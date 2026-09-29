import {test} from "node:test";
import assert from "node:assert/strict";
import {randomBytes,randomUUID,createHash} from "node:crypto";
import {readFileSync} from "node:fs";
import {Miniflare,convertV4MiniflareOptions} from "miniflare";
import {handleCoaching} from "../build/coaching-jobs.js";
import {handleCoachingHistory} from "../build/coaching-history.js";
import {sealKey} from "../build/account-key.js";
import {CoachingProviderError} from "../build/coaching-provider.js";
const sha=x=>createHash("sha256").update(x).digest("hex");
async function runtime(t,legacy=false) {
 const mf=new Miniflare(convertV4MiniflareOptions({modules:true,script:"export default {fetch(){return new Response('test')}}",compatibilityDate:"2026-02-17",d1Databases:["DB"]}));
 t.after(()=>mf.dispose());const db=await mf.getD1Database("DB");
 for(const name of ["0001_bootstrap.sql","0002_accounts.sql","0003_run_uploads.sql","0004_run_deletions.sql","0005_openai_keys.sql","0006_coaching_jobs.sql","0007_run_photos.sql","0008_run_locations.sql"]){
  const sql=readFileSync(new URL(`../migrations/${name}`,import.meta.url),"utf8").replace(/^--.*$/gm,"");
  const split=sql.indexOf("CREATE TRIGGER"),head=split<0?sql:sql.slice(0,split);
  await db.batch(head.split(";").map(x=>x.trim()).filter(Boolean).map(x=>db.prepare(x)));
  if(split>=0)await db.prepare(sql.slice(split)).run();
 }
 const migrate=async()=>{for(const name of ["0009_public_runs.sql","0010_run_weather.sql","0011_photo_weather.sql"]){
  const sql=readFileSync(new URL(`../migrations/${name}`,import.meta.url),"utf8").replace(/^--.*$/gm,"");
  await db.batch(sql.split(";").map(x=>x.trim()).filter(Boolean).map(x=>db.prepare(x)));}};
 if(!legacy)await migrate();
 const limiter={limit:async()=>({success:true})};
 const env={DB:db,GOOGLE_WEB_CLIENT_ID:"test.apps.googleusercontent.com",COACHING_KEYRING:JSON.stringify({active:"v1",keys:{v1:randomBytes(32).toString("base64")}}),RUN_RATE_LIMIT:limiter,RUN_TOTAL_LIMIT:limiter};
 const tokens={};
 for(const owner of ["alice","bob"]){
  tokens[owner]=randomBytes(32).toString("hex");
  await db.prepare("INSERT INTO accounts VALUES (?,?,NULL,NULL,1,1)").bind(owner,owner).run();
  await db.prepare("INSERT INTO auth_sessions (token_hash,owner_id,created_at,expires_at) VALUES (?,?,1,?)").bind(sha(tokens[owner]),owner,Math.floor(Date.now()/1000)+3600).run();
  const sealed=await sealKey(env,owner,`sk-${owner}-${"x".repeat(30)}`);
  await db.prepare("INSERT INTO openai_keys VALUES (?,?,'PUT',?,?,?,1,1)").bind(owner,randomUUID(),sealed.ciphertext,sealed.nonce,sealed.key_version).run();
 }
 async function add(owner="alice",ended=1000,mode="INDOOR"){
  const id=randomUUID(),operationId=randomUUID(),summary={state:"FINISHED",startedUtcMs:0,endedUtcMs:ended,activeDurationMs:500,distanceMeters:0,mode:"INDOOR",units:"MILES"};
  const archive={version:1,run:{id,cloudOwnerId:owner,state:"FINISHED",activeSlot:null,checkpoint:JSON.stringify({snapshot:{...summary,runId:id,settings:{mode:"INDOOR",units:"MILES"},segments:[],activeIntervals:[]}})},route:[],measurements:[],splits:[],intervals:[],segments:[]};
  if(mode==="OUTDOOR") {
   summary.mode=mode;
   const checkpoint=JSON.parse(archive.run.checkpoint);checkpoint.snapshot.settings.mode=mode;
   checkpoint.snapshot.segments=[{id:1,source:"GPS",startedMonotonicMs:0,endedMonotonicMs:500}];
   archive.run.checkpoint=JSON.stringify(checkpoint);
   archive.segments=[{runId:id,number:1,value:JSON.stringify(checkpoint.snapshot.segments[0])}];
   archive.route=[{runId:id,id:1,segmentId:1,monotonicMs:1,latitude:40.78123,longitude:-73.96876,accuracyMeters:5},
    {runId:id,id:2,segmentId:1,monotonicMs:500,latitude:40.78234,longitude:-73.96765,accuracyMeters:5}];
  }
  const bytes=Buffer.from(JSON.stringify(archive)),manifest=JSON.stringify({schemaVersion:1,runId:id,operationId,summary,chunks:[{bytes:bytes.length,sha256:sha(bytes)}]});
  await db.prepare("INSERT INTO run_uploads VALUES (?,?,?,?,?,1,1,10000,2)").bind(owner,id,operationId,manifest,sha(manifest)).run();
  await db.prepare("INSERT INTO run_chunks VALUES (?,?,0,?,?)").bind(owner,id,sha(bytes),Array.from(bytes)).run();return id;
 }
 const calls=[];const provider={countInput:async key=>{calls.push(["count",key]);return 20;},text:async key=>{calls.push(["text",key]);return "Good work.";},speech:async key=>{calls.push(["speech",key]);return new Uint8Array(150000).fill(7);}};
 const call=(id,{owner="alice",method="POST",body={operationId:randomUUID()},headers={},audio=false}={})=>handleCoaching(new Request(`https://test/api/coaching/${id}${audio?"/audio":""}`,{method,
  headers:{...(owner?{Authorization:`Bearer ${tokens[owner]}`} : {}),...(method==="POST"?{"Content-Type":"application/json"}:{}),...headers},...(method==="POST"?{body:JSON.stringify(body)}:{})}),env,provider);
 return {db,env,tokens,add,provider,calls,call,migrate};
}

import {handlePhotos,handlePublicPhoto} from "../build/photos.js";
import {handlePublication,handleShortRun} from "../build/publication.js";
import {parseWeather} from "../build/run-weather.js";

const photoWeather=()=>parseWeather({utc_offset_seconds:0,hourly_units:{time:"unixtime",temperature_2m:"\u00b0C"},
 hourly:{time:[0],temperature_2m:[20],weather_code:[2]}},{latitude:40.8,longitude:-74,observedUtcMs:0,endpoint:"archive"},3600000);
const weatherHeaders=weather=>({"X-Photo-Options":JSON.stringify({...options,weather:true}),"X-Photo-Weather":Buffer.from(JSON.stringify(weather)).toString("base64")});
async function seedWeather(r){const weather=photoWeather();await r.db.prepare("INSERT INTO run_weather VALUES ('alice',?,?,0,'fixture')").bind(r.id,JSON.stringify(weather)).run();return weather;}

const jpeg=new Uint8Array([255,216,255,217]);
const options={time:true,distance:true,pace:false,route:false};
async function photoRuntime(t,mode="INDOOR",legacy=false){
 const r=await runtime(t,legacy),id=await r.add("alice",1000,mode);
 const call=({owner="alice",method="GET",revision=randomUUID(),publicPhoto=true,body=jpeg,image=false,headers={}}={})=>handlePhotos(new Request(`https://test/api/photos/${id}${image?"/image":""}`,{method,headers:{...(owner?{Authorization:`Bearer ${r.tokens[owner]}`} :{}),...(method==="PUT"?{"Content-Type":"image/jpeg","X-Photo-Revision":revision,"X-Photo-Public":String(publicPhoto),"X-Photo-Options":JSON.stringify(options)}:{}),...headers},...(method==="PUT"?{body}: {})}),r.env);
 const publication=({owner="alice",method="GET",body,headers={}}={})=>handlePublication(new Request(`https://test/api/publications/${id}`,{
  method,headers:{...(owner?{Authorization:`Bearer ${r.tokens[owner]}`} :{}),...(body?{"Content-Type":"application/json"}:{}),...headers},...(body?{body:JSON.stringify(body)}:{})}),r.env);
 const mutate=async(action,photoVisible)=>{const state=(await (await publication()).json()).publication;
  const response=await publication({method:"PUT",body:{operationId:randomUUID(),expectedRevision:state.revision,action,...(action==="photo"?{photoVisible}:{})}});
  assert.equal(response.status,200);return (await response.json()).publication;};
 const publicCall=async(url,suffix="")=>{if(new URL(url).pathname.startsWith("/r/")){
  const redirect=await handleShortRun(new Request(url),r.env);if(redirect.status!==302)return redirect;
  url=new URL(redirect.headers.get("Location"),url).href;
 }return handlePublicPhoto(new Request(url+suffix),r.env);};
 return {...r,id,call,publicCall,publication,mutate};
}
test("private images require their owner; explicit publication exposes only one run and safe fields",async t=>{
 const {call,publicCall,mutate}=await photoRuntime(t);
 assert.equal((await call({owner:null})).status,401);assert.equal((await call({owner:"bob"})).status,404);
 assert.deepEqual(await (await call()).json(),{photo:null});
 let result=await call({method:"PUT",publicPhoto:false});assert.equal(result.status,200);
 let receipt=(await result.json()).photo;assert.equal(receipt.publicUrl,null);assert.equal(receipt.sha256,sha(jpeg));
 assert.equal((await call({owner:"bob",image:true})).status,404);
 assert.deepEqual(new Uint8Array(await (await call({image:true})).arrayBuffer()),jpeg);
 result=await call({method:"PUT"});assert.equal(result.status,200);receipt=(await result.json()).photo;
 assert.equal(receipt.publicUrl,null);
 const shared=await mutate("share");assert.equal((await publicCall(shared.publicUrl)).status,200);
 const data=await (await publicCall(shared.publicUrl,"/data")).json();
 assert.deepEqual(Object.keys(data).sort(),["geometry","location","photoVisible","run","settings","splits"]);
 assert.equal(data.settings.mode,"INDOOR");assert.deepEqual(data.geometry.parts,[]);assert.equal(data.location,null);
 assert.ok(!JSON.stringify(data).includes("alice"));assert.ok(!JSON.stringify(data).includes("cloudOwnerId"));
});
test("photo retry ignores legacy visibility; publication is independent and deletion cascades",async t=>{
 const {call,publicCall,db,id,mutate}=await photoRuntime(t),revision=randomUUID();
 const first=(await (await call({method:"PUT",revision})).json()).photo;
 assert.deepEqual((await (await call({method:"PUT",revision})).json()).photo,first);
 assert.equal((await call({method:"PUT",revision,publicPhoto:false})).status,200);
 const shared=await mutate("share");
 assert.equal((await call({method:"PUT",publicPhoto:false})).status,200);
 assert.equal((await publicCall(shared.publicUrl)).status,200);
 await mutate("unshare");assert.equal((await publicCall(shared.publicUrl)).status,404);
 await mutate("share");
 const second=(await (await call({method:"PUT"})).json()).photo;
 await db.prepare("DELETE FROM run_uploads WHERE owner_id='alice' AND run_id=?").bind(id).run();
 assert.equal((await publicCall(second.publicUrl,"/image")).status,404);
 assert.equal((await db.prepare("SELECT COUNT(*) n FROM run_photos").first()).n,0);
 assert.equal((await call({method:"PUT"})).status,404);
});

test("weather photo stores exact snapshot, retries consistently and strips public coordinates",async t=>{
 const r=await photoRuntime(t,"OUTDOOR"),weather=await seedWeather(r),revision=randomUUID();
 const reversed=Object.fromEntries(Object.entries(weather).reverse()),headers=weatherHeaders(reversed);
 const response=await r.call({method:"PUT",revision,headers});assert.equal(response.status,200);
 const receipt=(await response.json()).photo;assert.deepEqual(receipt.weather,weather);assert.equal(receipt.options.weather,true);assert.equal(receipt.publicUrl,null);
 assert.deepEqual((await (await r.call({method:"PUT",revision,headers})).json()).photo,receipt);
 const state=await r.mutate("share"),publicData=await (await r.publicCall(state.publicUrl,"/data")).json();
 assert.equal(publicData.weather.temperatureF,68);assert.equal(publicData.weather.latitude,undefined);assert.equal(publicData.weather.longitude,undefined);
 assert.ok(!JSON.stringify(publicData).includes("40.8"));assert.ok(!JSON.stringify(publicData).includes("-74"));
 await r.db.prepare("DELETE FROM run_weather WHERE run_id=?").bind(r.id).run();
 assert.deepEqual((await (await r.call()).json()).photo.weather,weather);
 assert.equal((await (await r.publicCall(state.publicUrl,"/data")).json()).weather.temperatureC,20);
 await r.mutate("photo",false);assert.equal((await (await r.publicCall(state.publicUrl,"/data")).json()).weather,undefined);
});

test("weather selection requires the owned cached snapshot and rejects altered or extra data",async t=>{
 const r=await photoRuntime(t,"OUTDOOR"),weather=photoWeather();
 assert.equal((await r.call({method:"PUT",headers:weatherHeaders(weather)})).status,400);
 await seedWeather(r);
 for(const invalid of [{...weather,temperatureC:19},{...weather,latitude:40.78123},{...weather,extra:"private"},{...weather,attribution:{...weather.attribution,url:"https://evil.test/"}}])
  assert.equal((await r.call({method:"PUT",headers:weatherHeaders(invalid)})).status,400);
 for(const raw of ["bad-base64!","x".repeat(4097),Buffer.from("not-json").toString("base64")])
  assert.equal((await r.call({method:"PUT",headers:{...weatherHeaders(weather),"X-Photo-Weather":raw}})).status,400);
 assert.equal((await r.call({method:"PUT",headers:{"X-Photo-Options":JSON.stringify({...options,weather:true})}})).status,400);
 assert.equal((await r.call({method:"PUT",headers:{"X-Photo-Weather":weatherHeaders(weather)["X-Photo-Weather"]}})).status,400);
 assert.equal((await r.call({owner:"bob",method:"PUT",headers:weatherHeaders(weather)})).status,404);
 assert.equal((await r.db.prepare("SELECT COUNT(*) n FROM run_photos").first()).n,0);
});

test("weather cache cannot alter a legacy photo; replacement can remove weather without changing publication",async t=>{
 const r=await photoRuntime(t,"OUTDOOR");await r.call({method:"PUT"});const legacy=(await (await r.call()).json()).photo;
 const weather=await seedWeather(r);assert.deepEqual((await (await r.call()).json()).photo,legacy);
 const revision=randomUUID();await r.call({method:"PUT",revision,headers:weatherHeaders(weather)});
 assert.equal((await r.call({method:"PUT",revision,headers:{"X-Photo-Options":JSON.stringify({...options,weather:false})}})).status,409);
 const state=await r.mutate("share");
 assert.equal((await r.call({method:"PUT",headers:{"X-Photo-Options":JSON.stringify({...options,weather:false})}})).status,200);
 const latest=(await (await r.call()).json()).photo;assert.equal(latest.weather,undefined);assert.equal(latest.publicUrl,state.publicUrl);
 assert.equal((await (await r.publicCall(state.publicUrl,"/data")).json()).weather,undefined);
});

test("public outdoor data exposes normalized route and cached city/state without exact coordinates",async t=>{
 const {call,publicCall,env,db,id,mutate}=await photoRuntime(t,"OUTDOOR");
 env.LOCATION_LOOKUP_URL="https://nominatim.openstreetmap.org/reverse";
 await db.prepare("INSERT INTO run_locations VALUES ('alice',?,'New York','New York','nominatim',0)").bind(id).run();
 await call({method:"PUT"});const receipt=await mutate("share");
 const response=await publicCall(receipt.publicUrl,"/data");assert.equal(response.status,200);
 const text=await response.text(),data=JSON.parse(text);
 assert.equal(data.location.label,"New York, New York");assert.equal(data.geometry.normalized,true);
 assert.equal(data.geometry.parts[0].length,2);
 for(const value of ["latitude","longitude","40.78123","73.96876","alice"])assert.ok(!text.includes(value));
 const page=await publicCall(receipt.publicUrl);assert.ok(!page.headers.get("Content-Security-Policy").includes("tile.openstreetmap.org"));
 assert.ok(!(await page.text()).includes("leaflet"));
});
test("invalid and oversized uploads cannot replace an existing image",async t=>{
 const {call}=await photoRuntime(t),revision=randomUUID();await call({method:"PUT",revision});
 for(const body of [new Uint8Array(3),new Uint8Array(1000001)])assert.ok([400,413].includes((await call({method:"PUT",body})).status));
 assert.equal((await call({method:"PUT",headers:{Origin:"https://evil.test"}})).status,403);
 assert.equal((await call({method:"PUT",headers:{"X-Photo-Options":'{"secret":true}'}})).status,400);
 assert.equal((await (await call()).json()).photo.revision,revision);
});
test("deletion tombstone suppresses public access even before physical cleanup",async t=>{
 const {call,publicCall,db,id,mutate,publication}=await photoRuntime(t);await call({method:"PUT"});const receipt=await mutate("share");
 await db.prepare("INSERT INTO run_deletions(owner_id,run_id,deleted_at) VALUES ('alice',?,?)").bind(id,Date.now()).run();
 assert.equal((await publicCall(receipt.publicUrl,"/image")).status,404);
 assert.equal((await call({method:"PUT"})).status,404);
 assert.equal((await publication({method:"PUT",body:{operationId:randomUUID(),expectedRevision:receipt.revision,action:"share"}})).status,404);
});


test("concurrent retries retain one public link and maximum photo bytes round-trip",async t=>{
 const {call}=await photoRuntime(t),revision=randomUUID();
 const results=await Promise.all([call({method:"PUT",revision}),call({method:"PUT",revision})]);
 assert.ok(results.every(r=>r.status===200));
 const receipts=await Promise.all(results.map(r=>r.json()));assert.equal(receipts[0].photo.publicUrl,receipts[1].photo.publicUrl);
 const body=new Uint8Array(1000000).fill(9);body[0]=255;body[1]=216;body[999998]=255;body[999999]=217;
 assert.equal((await call({method:"PUT",body})).status,200);
 assert.equal(sha(new Uint8Array(await (await call({image:true})).arrayBuffer())),sha(body));
});

test("photo-free sharing, hiding and unsharing protect every public endpoint",async t=>{
 const {mutate,publication,publicCall,call,env,db,id}=await photoRuntime(t);
 const initial=(await (await publication()).json()).publication;
 assert.deepEqual(initial,{shared:false,photoVisible:true,revision:0,publicUrl:null});
 const shared=await mutate("share");assert.match(shared.publicUrl,/\/r\/[0-9a-f]{32}$/);
 const redirect=await handleShortRun(new Request(shared.publicUrl),env);
 assert.equal(redirect.status,302);assert.equal(redirect.headers.get("Cache-Control"),"no-store");
 const long=new URL(redirect.headers.get("Location"),shared.publicUrl).href;
 assert.equal((await publicCall(long)).status,200);
 assert.equal((await (await publicCall(long,"/data")).json()).photoVisible,false);
 assert.equal((await publicCall(long,"/image")).status,404);
 await call({method:"PUT"});assert.equal((await publicCall(long,"/image")).status,200);
 await mutate("photo",false);
 assert.equal((await publicCall(long,"/image")).status,404);
 assert.equal((await (await publicCall(long,"/data")).json()).photoVisible,false);
 assert.equal((await call({image:true})).status,200);
 await call({method:"PUT",publicPhoto:true});assert.equal((await publicCall(long,"/image")).status,404);
 await mutate("unshare");
 for(const suffix of ["","/data","/image","/thumbnail"])assert.equal((await publicCall(long,suffix)).status,404);
 assert.equal((await handleShortRun(new Request(shared.publicUrl),env)).status,404);
 await mutate("photo",true);assert.equal((await publicCall(long)).status,404);
 assert.equal((await mutate("share")).publicUrl,shared.publicUrl);
 await db.prepare("DELETE FROM run_photos WHERE owner_id='alice' AND run_id=?").bind(id).run();
 assert.equal((await publicCall(long)).status,200);assert.equal((await publicCall(long,"/image")).status,404);
});

test("migration preserves legacy public tokens and leaves private photos unpublished",async t=>{
 const {db,id,migrate,publication,call,publicCall,add,env}=await photoRuntime(t,"INDOOR",true);
 const legacy="a".repeat(64),privateId=await add();
 for(const [run,publicToken] of [[id,legacy],[privateId,null]])await db.prepare("INSERT INTO run_photos VALUES ('alice',?,?,?,?,?,1)")
  .bind(run,randomUUID(),jpeg,JSON.stringify(options),publicToken).run();
 await migrate();
 assert.equal((await publicCall(`https://test/p/${legacy}`)).status,200);
 assert.equal((await db.prepare("SELECT COUNT(*) n FROM public_runs WHERE run_id=?").bind(privateId).first()).n,0);
 const state=(await (await publication()).json()).publication;assert.equal(state.shared,true);assert.match(state.publicUrl,/\/r\/[0-9a-f]{32}$/);
 const redirect=await handleShortRun(new Request(state.publicUrl),env);assert.equal(redirect.headers.get("Location"),`/p/${legacy}`);
 await call({method:"PUT",publicPhoto:false});assert.equal((await publicCall(`https://test/p/${legacy}/image`)).status,200);
});

test("mutation receipts survive retries, unshare wins over old share and stale revisions conflict",async t=>{
 const {publication,mutate}=await photoRuntime(t);
 const body={operationId:randomUUID(),expectedRevision:0,action:"share"};
 const pair=await Promise.all([publication({method:"PUT",body}),publication({method:"PUT",body})]);
 assert.ok(pair.every(r=>r.status===200));
 const first=(await pair[0].json()).publication;assert.equal(first.revision,1);
 const off=await mutate("unshare");assert.equal(off.revision,2);
 const replay=await publication({method:"PUT",body});assert.equal(replay.status,200);assert.equal((await replay.json()).publication.shared,false);
 assert.equal((await publication({method:"PUT",body:{...body,operationId:randomUUID()}})).status,409);
 assert.equal((await publication({method:"PUT",body:{...body,action:"unshare"}})).status,409);
 assert.equal((await (await publication()).json()).publication.revision,2);
});

test("publication boundary enforces owner, authentication, exact bodies and methods",async t=>{
 const {publication}=await photoRuntime(t);
 assert.equal((await publication({owner:null})).status,401);assert.equal((await publication({owner:"bob"})).status,404);
 const body={operationId:randomUUID(),expectedRevision:0,action:"share"};
 for(const headers of [{Origin:"https://test"},{Cookie:"x=y"}])assert.equal((await publication({method:"PUT",body,headers})).status,403);
 for(const invalid of [{...body,owner:"bob"},{...body,action:"unknown"},{...body,expectedRevision:-1},{...body,photoVisible:true},
  {...body,action:"photo",photoVisible:"true"}])assert.equal((await publication({method:"PUT",body:invalid})).status,400);
 assert.equal((await publication({method:"POST",body})).status,405);
});

test("short-token collision retries and publication deletion removes operation receipts",async t=>{
 const {db,id,add,publication,mutate}=await photoRuntime(t),other=await add();
 const collision="00".repeat(16);
 await db.prepare("INSERT INTO public_runs(owner_id,run_id,public_token,short_token) VALUES ('alice',?,?,?)").bind(other,"b".repeat(64),collision).run();
 const original=crypto.getRandomValues.bind(crypto);let calls=0;
 crypto.getRandomValues=array=>{if(array.length===16&&calls++===0){array.fill(0);return array;}return original(array);};
 try {assert.equal((await publication()).status,200);}finally{crypto.getRandomValues=original;}
 assert.ok(calls>=2);await mutate("share");
 await db.prepare("DELETE FROM run_uploads WHERE owner_id='alice' AND run_id=?").bind(id).run();
 assert.equal((await db.prepare("SELECT count(*) n FROM public_runs WHERE run_id=?").bind(id).first()).n,0);
 assert.equal((await db.prepare("SELECT count(*) n FROM publication_operations WHERE run_id=?").bind(id).first()).n,0);
});

test("unshare during a pending public locality lookup suppresses the final data response",async t=>{
 const {env,mutate,publicCall}=await photoRuntime(t,"OUTDOOR");
 env.LOCATION_LOOKUP_URL="https://fixture.test/reverse";
 const shared=await mutate("share"),original=globalThis.fetch;
 globalThis.fetch=async()=>{await mutate("unshare");return Response.json({address:{city:"Fixture",state:"Test"}});};
 try {assert.equal((await publicCall(shared.publicUrl,"/data")).status,404);}finally{globalThis.fetch=original;}
});

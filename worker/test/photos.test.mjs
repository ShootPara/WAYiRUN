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
async function runtime(t) {
 const mf=new Miniflare(convertV4MiniflareOptions({modules:true,script:"export default {fetch(){return new Response('test')}}",compatibilityDate:"2026-02-17",d1Databases:["DB"]}));
 t.after(()=>mf.dispose());const db=await mf.getD1Database("DB");
 for(const name of ["0001_bootstrap.sql","0002_accounts.sql","0003_run_uploads.sql","0004_run_deletions.sql","0005_openai_keys.sql","0006_coaching_jobs.sql","0007_run_photos.sql"]){
  const sql=readFileSync(new URL(`../migrations/${name}`,import.meta.url),"utf8").replace(/^--.*$/gm,"");
  const split=sql.indexOf("CREATE TRIGGER"),head=split<0?sql:sql.slice(0,split);
  await db.batch(head.split(";").map(x=>x.trim()).filter(Boolean).map(x=>db.prepare(x)));
  if(split>=0)await db.prepare(sql.slice(split)).run();
 }
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
 async function add(owner="alice",ended=1000){
  const id=randomUUID(),operationId=randomUUID(),summary={state:"FINISHED",startedUtcMs:0,endedUtcMs:ended,activeDurationMs:500,distanceMeters:0,mode:"INDOOR",units:"MILES"};
  const archive={version:1,run:{id,cloudOwnerId:owner,state:"FINISHED",activeSlot:null,checkpoint:JSON.stringify({snapshot:{...summary,runId:id,settings:{mode:"INDOOR",units:"MILES"},segments:[],activeIntervals:[]}})},route:[],measurements:[],splits:[],intervals:[],segments:[]};
  const bytes=Buffer.from(JSON.stringify(archive)),manifest=JSON.stringify({schemaVersion:1,runId:id,operationId,summary,chunks:[{bytes:bytes.length,sha256:sha(bytes)}]});
  await db.prepare("INSERT INTO run_uploads VALUES (?,?,?,?,?,1,1,10000,2)").bind(owner,id,operationId,manifest,sha(manifest)).run();
  await db.prepare("INSERT INTO run_chunks VALUES (?,?,0,?,?)").bind(owner,id,sha(bytes),Array.from(bytes)).run();return id;
 }
 const calls=[];const provider={countInput:async key=>{calls.push(["count",key]);return 20;},text:async key=>{calls.push(["text",key]);return "Good work.";},speech:async key=>{calls.push(["speech",key]);return new Uint8Array(150000).fill(7);}};
 const call=(id,{owner="alice",method="POST",body={operationId:randomUUID()},headers={},audio=false}={})=>handleCoaching(new Request(`https://test/api/coaching/${id}${audio?"/audio":""}`,{method,
  headers:{...(owner?{Authorization:`Bearer ${tokens[owner]}`} : {}),...(method==="POST"?{"Content-Type":"application/json"}:{}),...headers},...(method==="POST"?{body:JSON.stringify(body)}:{})}),env,provider);
 return {db,env,tokens,add,provider,calls,call};
}

import {handlePhotos,handlePublicPhoto} from "../build/photos.js";

const jpeg=new Uint8Array([255,216,255,217]);
const options={time:true,distance:true,pace:false,route:false};
async function photoRuntime(t){
 const r=await runtime(t),id=await r.add();
 const call=({owner="alice",method="GET",revision=randomUUID(),publicPhoto=true,body=jpeg,image=false,headers={}}={})=>handlePhotos(new Request(`https://test/api/photos/${id}${image?"/image":""}`,{method,headers:{...(owner?{Authorization:`Bearer ${r.tokens[owner]}`} :{}),...(method==="PUT"?{"Content-Type":"image/jpeg","X-Photo-Revision":revision,"X-Photo-Public":String(publicPhoto),"X-Photo-Options":JSON.stringify(options)}:{}),...headers},...(method==="PUT"?{body}: {})}),r.env);
 const publicCall=(url,suffix="")=>handlePublicPhoto(new Request(url+suffix),r.env);
 return {...r,id,call,publicCall};
}
test("private images require their owner; explicit publication exposes only one run and safe fields",async t=>{
 const {call,publicCall}=await photoRuntime(t);
 assert.equal((await call({owner:null})).status,401);assert.equal((await call({owner:"bob"})).status,404);
 assert.deepEqual(await (await call()).json(),{photo:null});
 let result=await call({method:"PUT",publicPhoto:false});assert.equal(result.status,200);
 let receipt=(await result.json()).photo;assert.equal(receipt.publicUrl,null);assert.equal(receipt.sha256,sha(jpeg));
 assert.equal((await call({owner:"bob",image:true})).status,404);
 assert.deepEqual(new Uint8Array(await (await call({image:true})).arrayBuffer()),jpeg);
 result=await call({method:"PUT"});assert.equal(result.status,200);receipt=(await result.json()).photo;
 assert.equal((await publicCall(receipt.publicUrl)).status,200);
 const data=await (await publicCall(receipt.publicUrl,"/data")).json();
 assert.deepEqual(Object.keys(data).sort(),["route","run","segments","settings","splits"]);
 assert.equal(data.settings.mode,"INDOOR");assert.deepEqual(data.route,[]);
 assert.ok(!JSON.stringify(data).includes("alice"));assert.ok(!JSON.stringify(data).includes("cloudOwnerId"));
});
test("retry is idempotent; changing private/public state revokes the old link; deletion cascades",async t=>{
 const {call,publicCall,db,id}=await photoRuntime(t),revision=randomUUID();
 const first=(await (await call({method:"PUT",revision})).json()).photo;
 assert.deepEqual((await (await call({method:"PUT",revision})).json()).photo,first);
 assert.equal((await call({method:"PUT",revision,publicPhoto:false})).status,409);
 assert.equal((await call({method:"PUT",publicPhoto:false})).status,200);
 assert.equal((await publicCall(first.publicUrl)).status,404);
 const second=(await (await call({method:"PUT"})).json()).photo;
 await db.prepare("DELETE FROM run_uploads WHERE owner_id='alice' AND run_id=?").bind(id).run();
 assert.equal((await publicCall(second.publicUrl,"/image")).status,404);
 assert.equal((await db.prepare("SELECT COUNT(*) n FROM run_photos").first()).n,0);
 assert.equal((await call({method:"PUT"})).status,404);
});
test("invalid and oversized uploads cannot replace an existing image",async t=>{
 const {call}=await photoRuntime(t),revision=randomUUID();await call({method:"PUT",revision});
 for(const body of [new Uint8Array(3),new Uint8Array(1000001)])assert.ok([400,413].includes((await call({method:"PUT",body})).status));
 assert.equal((await call({method:"PUT",headers:{Origin:"https://evil.test"}})).status,403);
 assert.equal((await call({method:"PUT",headers:{"X-Photo-Options":'{"secret":true}'}})).status,400);
 assert.equal((await (await call()).json()).photo.revision,revision);
});
test("deletion tombstone suppresses public access even before physical cleanup",async t=>{
 const {call,publicCall,db,id}=await photoRuntime(t);const receipt=(await (await call({method:"PUT"})).json()).photo;
 await db.prepare("INSERT INTO run_deletions(owner_id,run_id,deleted_at) VALUES ('alice',?,?)").bind(id,Date.now()).run();
 assert.equal((await publicCall(receipt.publicUrl,"/image")).status,404);
 assert.equal((await call({method:"PUT"})).status,404);
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

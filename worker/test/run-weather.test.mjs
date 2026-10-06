import {test} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {randomBytes,randomUUID,createHash} from "node:crypto";
import {Miniflare,convertV4MiniflareOptions} from "miniflare";
import {handleWeather,weatherQuery,weatherUrl,parseWeather,fetchWeather} from "../build/run-weather.js";

const now=Date.parse("2026-09-27T16:10:00Z"),start=Date.parse("2026-09-27T15:35:00Z"),hour=Date.parse("2026-09-27T15:00:00Z");
const sha=value=>createHash("sha256").update(value).digest("hex");
const archiveInput=(mode="OUTDOOR",startedUtcMs=start,route=[{latitude:40.78123,longitude:-73.96876}])=>
 ({run:{checkpoint:{snapshot:{settings:{mode},startedUtcMs}}},route});
const payload=(at=hour,temperature=20,code=2)=>({utc_offset_seconds:0,hourly_units:{time:"unixtime",temperature_2m:"\u00b0C"},
 hourly:{time:[at/1000],temperature_2m:[temperature],weather_code:[code]}});

async function runtime(t){
 const mf=new Miniflare(convertV4MiniflareOptions({modules:true,script:"export default {fetch(){return new Response('test')}}",compatibilityDate:"2026-02-17",d1Databases:["DB"]}));
 t.after(()=>mf.dispose());const DB=await mf.getD1Database("DB");
 for(const name of ["0001_bootstrap.sql","0002_accounts.sql","0003_run_uploads.sql","0004_run_deletions.sql","0007_run_photos.sql","0008_run_locations.sql","0009_public_runs.sql","0010_run_weather.sql"]){
  const sql=readFileSync(new URL(`../migrations/${name}`,import.meta.url),"utf8").replace(/^--.*$/gm,"");
  const split=sql.indexOf("CREATE TRIGGER"),head=split<0?sql:sql.slice(0,split);
  await DB.batch(head.split(";").map(x=>x.trim()).filter(Boolean).map(x=>DB.prepare(x)));
  if(split>=0)await DB.prepare(sql.slice(split)).run();
 }
 const limiter={limit:async()=>({success:true})},env={DB,GOOGLE_WEB_CLIENT_ID:"test.apps.googleusercontent.com",RUN_RATE_LIMIT:limiter,RUN_TOTAL_LIMIT:limiter};
 const tokens={};
 for(const owner of ["alice","bob"]){
  tokens[owner]=randomBytes(32).toString("hex");
  await DB.prepare("INSERT INTO accounts VALUES (?,?,NULL,NULL,1,1)").bind(owner,owner).run();
  await DB.prepare("INSERT INTO auth_sessions(token_hash,owner_id,created_at,expires_at) VALUES (?,?,1,?)")
   .bind(sha(tokens[owner]),owner,Math.floor(Date.now()/1000)+3600).run();
 }
 async function add({owner="alice",mode="OUTDOOR",startedUtcMs=start,gps=true}={}){
  const id=randomUUID(),op=randomUUID(),summary={state:"FINISHED",startedUtcMs,endedUtcMs:startedUtcMs+500,activeDurationMs:500,distanceMeters:0,mode,units:"MILES"};
  const segments=mode==="OUTDOOR"&&gps?[{id:1,source:"GPS",startedMonotonicMs:0,endedMonotonicMs:500}]:[];
  const archive={version:1,run:{id,cloudOwnerId:owner,state:"FINISHED",activeSlot:null,checkpoint:JSON.stringify({snapshot:{...summary,runId:id,settings:{mode,units:"MILES"},segments,activeIntervals:[]}})},
   route:segments.length?[{runId:id,id:1,segmentId:1,monotonicMs:1,latitude:40.78123,longitude:-73.96876,accuracyMeters:5}]:[],
   measurements:[],splits:[],intervals:[],segments:segments.map(value=>({runId:id,number:1,value:JSON.stringify(value)}))};
  const bytes=Buffer.from(JSON.stringify(archive)),manifest=JSON.stringify({schemaVersion:1,runId:id,operationId:op,summary,chunks:[{bytes:bytes.length,sha256:sha(bytes)}]});
  await DB.prepare("INSERT INTO run_uploads VALUES (?,?,?,?,?,1,1,10000,2)").bind(owner,id,op,manifest,sha(manifest)).run();
  await DB.prepare("INSERT INTO run_chunks VALUES (?,?,0,?,?)").bind(owner,id,sha(bytes),Array.from(bytes)).run();return id;
 }
 const call=(id,{owner="alice",method="GET",headers={},query="",fetcher=async()=>Response.json(payload()),time=now}={})=>
  handleWeather(new Request(`https://test/api/weather/${id}${query}`,{method,headers:{...(owner?{Authorization:`Bearer ${tokens[owner]}`} :{}),...headers}}),env,fetcher,time);
 return {DB,env,tokens,add,call};
}

test("weather query rounds coordinates, floors the run hour and selects recent/archive endpoints",()=>{
 const query=weatherQuery(archiveInput(),now);
 assert.deepEqual(query,{latitude:40.8,longitude:-74,observedUtcMs:hour,endpoint:"forecast"});
 const url=new URL(weatherUrl(query));assert.equal(url.host,"api.open-meteo.com");assert.equal(url.searchParams.get("start_date"),"2026-09-27");
 assert.equal(url.searchParams.get("end_date"),"2026-09-27");assert.equal(url.searchParams.get("timezone"),"GMT");
 assert.equal(url.searchParams.get("latitude"),"40.8");assert.ok(!url.href.includes("78123"));assert.equal(url.searchParams.get("current"),null);
 const old=weatherQuery(archiveInput("OUTDOOR",now-7*86400000),now);
 assert.equal(old.endpoint,"archive");assert.equal(new URL(weatherUrl(old)).pathname,"/v1/archive");
 assert.equal(new URL(weatherUrl(old)).host,"archive-api.open-meteo.com");
 assert.equal(weatherQuery(archiveInput("OUTDOOR",now-7*86400000+1),now).endpoint,"forecast");
});

test("weather never invents location/time for indoor, empty, invalid or future runs",()=>{
 for(const input of [archiveInput("INDOOR"),archiveInput("OUTDOOR",start,[]),archiveInput("OUTDOOR",now+1),archiveInput("OUTDOOR",-1),
  archiveInput("OUTDOOR",start,[{latitude:null,longitude:0}]),archiveInput("OUTDOOR",start,[{latitude:91,longitude:0}])])assert.equal(weatherQuery(input,now),null);
 const query=weatherQuery(archiveInput("OUTDOOR",Date.parse("2026-09-26T23:59:59Z")),now);
 assert.equal(query.observedUtcMs,Date.parse("2026-09-26T23:00:00Z"));
});

test("parser selects exact historical hour, validates units and preserves bounded provenance",()=>{
 const query=weatherQuery(archiveInput(),now),result=parseWeather(payload(),query,now);
 assert.equal(result.temperatureC,20);assert.equal(result.temperatureF,68);assert.equal(result.weatherCode,2);assert.ok(result.emoji);
 assert.equal(result.source,"open-meteo");assert.equal(result.attribution.licenseUrl,"https://creativecommons.org/licenses/by/4.0/");
 for(const code of [0,1,2,3,45,48,51,53,55,56,57,61,63,65,66,67,71,73,75,77,80,81,82,85,86,95,96,97,99])assert.ok(parseWeather(payload(hour,0,code),query,now));
 for(const value of [payload(hour-3600000),payload(hour,null),payload(hour,"20"),payload(hour,Infinity),payload(hour,71),payload(hour,20,4),
  {...payload(),utc_offset_seconds:3600},{...payload(),hourly_units:{time:"iso8601",temperature_2m:"\u00b0F"}},
  {...payload(),hourly:{time:[hour/1000,hour/1000],temperature_2m:[20,20],weather_code:[2,2]}}])assert.equal(parseWeather(value,query,now),null);
});

test("endpoint authenticates owner and rejects browser headers, arbitrary queries and methods before fetching",async t=>{
 const r=await runtime(t),id=await r.add();let calls=0;const fetcher=async()=>{calls++;return Response.json(payload());};
 for(const [options,status] of [[{owner:null},401],[{owner:"bob"},404],[{headers:{Origin:"https://test"}},403],
  [{headers:{Cookie:"a=b"}},403],[{query:"?latitude=1"},404],[{method:"POST"},405]])assert.equal((await r.call(id,{...options,fetcher})).status,status);
 assert.equal((await r.call(randomUUID(),{fetcher})).status,404);assert.equal(calls,0);
});

test("successful lookup is stable, private, bounded and independent of publication/photos",async t=>{
 const r=await runtime(t),id=await r.add();let calls=0;
 const fetcher=async(url,options)=>{calls++;assert.equal(options.redirect,"manual");assert.ok(options.signal);assert.equal(new URL(url).searchParams.get("longitude"),"-74");return Response.json({...payload(),latitude:40.78123,untrusted:"discard"});};
 const response=await r.call(id,{fetcher});assert.equal(response.status,200);assert.equal(response.headers.get("Cache-Control"),"no-store");
 const first=await response.json();assert.equal(first.weather.temperatureF,68);assert.equal(first.reason,null);
 assert.ok(!JSON.stringify(first).includes("40.78123"));assert.ok(!JSON.stringify(first).includes("untrusted"));
 assert.deepEqual(await (await r.call(id,{fetcher,time:now+90*86400000})).json(),first);assert.equal(calls,1);
 for(const table of ["public_runs","run_photos","publication_operations"])assert.equal((await r.DB.prepare(`SELECT COUNT(*) n FROM ${table}`).first()).n,0);
 await r.DB.prepare("DELETE FROM run_uploads WHERE owner_id='alice' AND run_id=?").bind(id).run();
 assert.equal((await r.DB.prepare("SELECT COUNT(*) n FROM run_weather").first()).n,0);
 assert.equal((await r.call(id,{fetcher})).status,404);
});

test("indoor, no-GPS and future runs skip the provider",async t=>{
 const r=await runtime(t);let calls=0;const fetcher=async()=>{calls++;throw Error("must not fetch");};
 for(const options of [{mode:"INDOOR"},{gps:false},{startedUtcMs:now+1}]){
  const id=await r.add(options),response=await r.call(id,{fetcher});assert.equal(response.status,200);assert.equal((await response.json()).weather,null);
 }assert.equal(calls,0);
});

test("archive-integrity failure cannot become a weather query",async t=>{
 const r=await runtime(t),id=await r.add();await r.DB.prepare("UPDATE run_uploads SET manifest_hash='bad' WHERE run_id=?").bind(id).run();
 let calls=0;const response=await r.call(id,{fetcher:async()=>{calls++;return Response.json(payload());}});
 assert.equal(response.status,503);assert.deepEqual(await response.json(),{error:"weather_unavailable"});assert.equal(calls,0);
});

test("historical runs request their recorded date and never fall back to today's conditions",async t=>{
 const r=await runtime(t),startedUtcMs=Date.parse("2020-01-02T23:45:00Z"),id=await r.add({startedUtcMs});let calls=0;
 const response=await r.call(id,{fetcher:async url=>{calls++;const parsed=new URL(url);assert.equal(parsed.host,"archive-api.open-meteo.com");
  assert.equal(parsed.searchParams.get("start_date"),"2020-01-02");return Response.json(payload());}});
 assert.equal((await response.json()).weather,null);assert.equal(calls,1);
});

test("failure retries are bounded and missing-hour responses do not produce fabricated weather",async t=>{
 const r=await runtime(t),id=await r.add();let calls=0;const fetcher=async()=>{calls++;return Response.json(payload(hour-3600000));};
 const first=await (await r.call(id,{fetcher})).json();
 assert.equal(first.reason,"provider_invalid_response");assert.equal(first.retryAfter,now+30000);
 assert.equal((await (await r.call(id,{fetcher,time:now+29999})).json()).reason,"retry_later");assert.equal(calls,1);
 const success=await (await r.call(id,{fetcher:async()=>{calls++;return Response.json(payload());},time:first.retryAfter})).json();
 assert.ok(success.weather);assert.equal(success.reason,null);assert.equal(calls,2);
});

test("provider status, malformed and oversized responses leave the optional flow available",async t=>{
 const r=await runtime(t);let time=now;
 for(const fetcher of [async()=>new Response("down",{status:503}),async()=>new Response("not-json"),async()=>new Response("x".repeat(32769)),async()=>{throw Error("offline");}]){
  const id=await r.add(),response=await r.call(id,{fetcher,time});time+=10000;
  assert.equal(response.status,200);assert.equal((await response.json()).weather,null);
 }
});

test("provider timeout aborts lookup and returns unavailable",async t=>{
 const r=await runtime(t),id=await r.add();let aborted=false;const started=performance.now();
 const response=await r.call(id,{fetcher:async(_url,{signal})=>new Promise((_resolve,reject)=>{
  signal.addEventListener("abort",()=>{aborted=true;reject(new DOMException("timeout","AbortError"));},{once:true});
 })});assert.equal(aborted,true);assert.equal(response.status,200);assert.equal((await response.json()).reason,"provider_timeout");
 assert.ok(performance.now()-started>=7900,"must not retain the old three-second deadline");
});

test("deadline also cancels a stalled response body",async t=>{
 const r=await runtime(t),id=await r.add();let cancelled=false;
 const response=await r.call(id,{fetcher:async()=>new Response(new ReadableStream({cancel(){cancelled=true;}}))});
 assert.equal(response.status,200);assert.equal((await response.json()).reason,"provider_timeout");assert.equal(cancelled,true);
});

test("a stale attempt cannot overwrite a newer successful snapshot",async t=>{
 const r=await runtime(t),id=await r.add();
 await r.call(id,{fetcher:async()=>{
  const newer=await r.call(id,{time:now+30001,fetcher:async()=>Response.json(payload(hour,10))});
  assert.equal((await newer.json()).weather.temperatureC,10);return Response.json(payload(hour,30));
 }});
 assert.equal((await (await r.call(id)).json()).weather.temperatureC,10);
});

test("concurrent lookups share one global provider gate and per-run reservation",async t=>{
 const r=await runtime(t),a=await r.add(),b=await r.add({owner:"bob"});let calls=0;
 const fetcher=async()=>{calls++;return Response.json(payload());};
 const results=await Promise.all([r.call(a,{fetcher}),r.call(a,{fetcher}),r.call(b,{owner:"bob",fetcher})]);
 assert.ok(results.every(response=>response.status===200));assert.equal(calls,1);
 assert.equal((await r.DB.prepare("SELECT COUNT(*) n FROM run_weather WHERE snapshot_json IS NOT NULL").first()).n,1);
});

test("logout during provider lookup cannot persist or deliver a snapshot",async t=>{
 const r=await runtime(t),id=await r.add();const response=await r.call(id,{fetcher:async()=>{
  await r.DB.prepare("UPDATE auth_sessions SET revoked_at=1 WHERE owner_id='alice'").run();return Response.json(payload());
 }});assert.equal(response.status,401);
 assert.equal((await r.DB.prepare("SELECT COUNT(*) n FROM run_weather WHERE snapshot_json IS NOT NULL").first()).n,0);
});

test("run tombstone during lookup prevents persistence/delivery even before archive deletion",async t=>{
 const r=await runtime(t),id=await r.add();const response=await r.call(id,{fetcher:async()=>{
  await r.DB.prepare("INSERT INTO run_deletions VALUES ('alice',?,1)").bind(id).run();return Response.json(payload());
 }});assert.equal(response.status,404);
 assert.equal((await r.DB.prepare("SELECT COUNT(*) n FROM run_weather WHERE snapshot_json IS NOT NULL").first()).n,0);
});

test("account deletion cascades in-flight weather reservations",async t=>{
 const r=await runtime(t),id=await r.add();const response=await r.call(id,{fetcher:async()=>{
  await r.DB.prepare("DELETE FROM accounts WHERE id='alice'").run();return Response.json(payload());
 }});assert.equal(response.status,401);assert.equal((await r.DB.prepare("SELECT COUNT(*) n FROM run_weather").first()).n,0);
});

test("native Workers fetch accepts the request and never follows provider redirects",async t=>{
 let mode="success",calls=0;
 const query=weatherQuery(archiveInput(),now);
 const mf=new Miniflare(convertV4MiniflareOptions({compatibilityDate:"2026-02-17",modules:[
  {type:"ESModule",path:"weather-runtime/entry.js",contents:`import {fetchWeather} from './run-weather.js';
   export default {async fetch(){return Response.json(await fetchWeather(${JSON.stringify(query)},fetch,${now}));}};`},
  {type:"ESModule",path:"weather-runtime/run-weather.js",contents:readFileSync(new URL("../build/run-weather.js",import.meta.url),"utf8")},
  // This helper-level harness does not invoke the handler's auth/archive dependencies.
  {type:"ESModule",path:"weather-runtime/auth.js",contents:"const unused=()=>{throw Error('not used')}; export {unused as accessGuard,unused as sessionAccount,unused as reply};"},
  {type:"ESModule",path:"weather-runtime/coaching-context.js",contents:"export const readVerifiedRun=()=>{throw Error('not used')}; export class CoachingDataError extends Error {}"},
 ],outboundService:async request=>{
  calls++;assert.equal(new URL(request.url).host,"api.open-meteo.com");
  return mode==="success"?Response.json(payload()):new Response(null,{status:302,headers:{Location:"https://redirect-destination.invalid/"}});
 }}));t.after(()=>mf.dispose());
 const success=await (await mf.dispatchFetch("https://test/")).json();assert.ok(success.weather);assert.equal(calls,1);
 mode="redirect";
 const rejected=await (await mf.dispatchFetch("https://test/")).json();
 assert.equal(rejected.weather,null);assert.equal(rejected.reason,"provider_unavailable");assert.equal(calls,2);
});

test("realistic full-day forecast and archive responses preserve exact-hour selection including code 97",async()=>{
 for(const [endpoint,day] of [["forecast","2026-10-05"],["archive","2026-09-27"]]) {
  const midnight=Date.parse(day+"T00:00:00Z"),observedUtcMs=midnight+18*3600000;
  const query={latitude:40.8,longitude:-74,observedUtcMs,endpoint};
  const value={latitude:40.795395,longitude:-74.00056,generationtime_ms:.329,utc_offset_seconds:0,
   timezone:"GMT",timezone_abbreviation:"GMT",elevation:60,
   hourly_units:{time:"unixtime",temperature_2m:"°C",weather_code:"wmo code"},
   hourly:{time:Array.from({length:24},(_,i)=>midnight/1000+i*3600),
    temperature_2m:Array.from({length:24},(_,i)=>i===18?17.8:14.1),weather_code:Array.from({length:24},(_,i)=>i===18?97:3)}};
  const result=await fetchWeather(query,async()=>Response.json(value),observedUtcMs+3600000);
  assert.equal(result.weather.temperatureC,17.8);assert.equal(result.weather.temperatureF,64);
  assert.equal(result.weather.weatherCode,97);assert.equal(result.weather.emoji,"⛈️");assert.equal(result.weather.observedUtcMs,observedUtcMs);
 }
});

test("provider deadline fires at eight seconds, not before",async t=>{
 t.mock.timers.enable({apis:["setTimeout"]});let aborted=false,settled=false;
 const pending=fetchWeather(weatherQuery(archiveInput(),now),async(_url,{signal})=>new Promise((_resolve,reject)=>{
  signal.addEventListener("abort",()=>{aborted=true;reject(Error("timeout"));});
 }),now).then(value=>{settled=true;return value;});
 t.mock.timers.tick(7999);await Promise.resolve();assert.equal(settled,false);assert.equal(aborted,false);
 t.mock.timers.tick(1);assert.equal((await pending).reason,"provider_timeout");assert.equal(aborted,true);
});

test("failure categories are bounded and provider Retry-After is respected",async()=>{
 const query=weatherQuery(archiveInput(),now);
 for(const [fetcher,reason] of [
  [async()=>{throw Error("secret provider diagnostics");},"provider_unavailable"],
  [async()=>new Response("private body",{status:503}),"provider_unavailable"],
  [async()=>new Response("not JSON"),"provider_invalid_response"],
  [async()=>new Response("x".repeat(32769)),"provider_invalid_response"],
 ]) {
  assert.deepEqual(await fetchWeather(query,fetcher,now),{weather:null,reason,retryAfter:now+30000});
 }
 for(const hint of ["120",new Date(now+120000).toUTCString()]) {
  assert.deepEqual(await fetchWeather(query,async()=>new Response(null,{status:429,headers:{"Retry-After":hint}}),now),
   {weather:null,reason:"provider_throttled",retryAfter:now+120000});
 }
 assert.equal((await fetchWeather(query,async()=>new Response(null,{status:429,headers:{"Retry-After":"999999999"}}),now)).retryAfter,now+86400000);
});

test("busy global gate releases a run to actual gate eligibility, not thirty seconds",async t=>{
 const r=await runtime(t),a=await r.add(),b=await r.add();let calls=0;
 const fetcher=async()=>{calls++;return Response.json(payload());};
 await r.call(a,{fetcher});
 const blocked=await (await r.call(b,{fetcher,time:now+1})).json();assert.equal(blocked.reason,"retry_later");
 assert.equal(blocked.retryAfter,now+10000);
 assert.equal((await r.DB.prepare("SELECT retry_after FROM run_weather WHERE run_id=?").bind(b).first()).retry_after,now+10000);
 await r.call(b,{fetcher,time:now+9999});assert.equal(calls,1);
 assert.ok((await (await r.call(b,{fetcher,time:now+10000})).json()).weather);assert.equal(calls,2);
});

test("same-run in-flight requests share reservation without extending retry eligibility",async t=>{
 const r=await runtime(t),id=await r.add();let release,entered;
 const enteredPromise=new Promise(resolve=>{entered=resolve;});const gate=new Promise(resolve=>{release=resolve;});let calls=0;
 const first=r.call(id,{fetcher:async()=>{calls++;entered();await gate;return Response.json(payload());}});
 await enteredPromise;
 try {
  const second=await (await r.call(id,{time:now+500,fetcher:async()=>{calls++;return Response.json(payload());}})).json();
  assert.equal(second.reason,"retry_later");assert.equal(second.retryAfter,now+30000);assert.equal(calls,1);
 } finally {release();}
 assert.ok((await (await first).json()).weather);
});

test("old one-hour failure entries expire naturally without rewrites",async t=>{
 const r=await runtime(t),id=await r.add();
 await r.DB.prepare("INSERT INTO run_weather VALUES ('alice',?,NULL,?,'old')").bind(id,now+3600000).run();
 let calls=0;const fetcher=async()=>{calls++;return Response.json(payload());};
 const result=await (await r.call(id,{fetcher})).json();assert.equal(result.retryAfter,now+3600000);assert.equal(calls,0);
 assert.ok((await (await r.call(id,{fetcher,time:now+3600000})).json()).weather);assert.equal(calls,1);
});

test("provider throttling delays other runs without extending their reservation beyond the hint",async t=>{
 const r=await runtime(t),a=await r.add(),b=await r.add();let calls=0;
 const limited=await (await r.call(a,{fetcher:async()=>{calls++;return new Response(null,{status:429,headers:{"Retry-After":"120"}});}})).json();
 assert.equal(limited.reason,"provider_throttled");assert.equal(limited.retryAfter,now+120000);
 const fetcher=async()=>{calls++;return Response.json(payload());};
 const blocked=await (await r.call(b,{fetcher,time:now+10000})).json();
 assert.equal(blocked.retryAfter,now+120000);assert.equal(calls,1);
 assert.ok((await (await r.call(b,{fetcher,time:now+120000})).json()).weather);assert.equal(calls,2);
});

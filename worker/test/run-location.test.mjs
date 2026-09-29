import {test} from "node:test";
import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {Miniflare,convertV4MiniflareOptions} from "miniflare";
import {parseLocality,resolveRunLocation} from "../build/run-location.js";
import {publicRoute} from "../build/public-route.js";

async function runtime(t) {
 const mf=new Miniflare(convertV4MiniflareOptions({modules:true,script:"export default {fetch(){return new Response('test')}}",compatibilityDate:"2026-02-17",d1Databases:["DB"]}));
 t.after(()=>mf.dispose());const DB=await mf.getD1Database("DB");
 for(const name of ["0001_bootstrap.sql","0002_accounts.sql","0003_run_uploads.sql","0004_run_deletions.sql","0008_run_locations.sql"]){
  const sql=readFileSync(new URL(`../migrations/${name}`,import.meta.url),"utf8").replace(/^--.*$/gm,"");
  const split=sql.indexOf("CREATE TRIGGER"),head=split<0?sql:sql.slice(0,split);
  await DB.batch(head.split(";").map(x=>x.trim()).filter(Boolean).map(x=>DB.prepare(x)));
  if(split>=0)await DB.prepare(sql.slice(split)).run();
 }
 for(const owner of ["alice","bob"]){
  await DB.prepare("INSERT INTO accounts VALUES (?,?,NULL,NULL,1,1)").bind(owner,owner).run();
  await DB.prepare("INSERT INTO run_uploads VALUES (?,?,'op','{}','hash',1,1,10000,2)").bind(owner,"run").run();
 }
 return {DB,LOCATION_LOOKUP_URL:"https://nominatim.openstreetmap.org/reverse"};
}
const point={latitude:40.78123,longitude:-73.96876};
test("locality uses city/town and optional region without inventing a city",()=>{
 assert.deepEqual(parseLocality({address:{city:"New York",state:"New York"}}),{city:"New York",region:"New York"});
 assert.deepEqual(parseLocality({address:{town:"Town"}}),{city:"Town",region:null});
 assert.equal(parseLocality({address:{state:"Region"}}),null);
 assert.equal(parseLocality({address:{city:42}}),null);
});
test("location lookup caches successful labels per owned run and limits requests globally",async t=>{
 const env=await runtime(t);let calls=0;
 const fetcher=async(url,options)=>{
  calls++;const q=new URL(url).searchParams;assert.equal(q.get("lat"),String(point.latitude));assert.equal(q.get("zoom"),"10");
  assert.ok(options.headers["User-Agent"].startsWith("WAYiRUN/"));assert.ok(options.signal);assert.equal(options.redirect,"error");
  return Response.json({address:{city:"New York",state:"New York"},lat:"private"});
 };
 const first=await resolveRunLocation(env,"alice","run",point,fetcher,1000);
 assert.equal(first.label,"New York, New York");assert.ok(!JSON.stringify(first).includes("latitude"));
 assert.deepEqual(await resolveRunLocation(env,"alice","run",point,fetcher,1001),first);
 assert.equal(await resolveRunLocation(env,"bob","run",point,fetcher,1001),null);
 assert.equal(calls,1);
 assert.equal((await resolveRunLocation(env,"bob","run",point,fetcher,11000)).label,first.label);assert.equal(calls,2);
 await env.DB.prepare("DELETE FROM run_uploads WHERE owner_id='alice'").run();
 assert.equal((await env.DB.prepare("SELECT COUNT(*) n FROM run_locations WHERE owner_id='alice'").first()).n,0);
});
test("concurrent different runs share one provider lease",async t=>{
 const env=await runtime(t);let calls=0;
 const fetcher=async()=>{calls++;return Response.json({address:{city:"Town"}});};
 const results=await Promise.all(["alice","bob"].map(owner=>resolveRunLocation(env,owner,"run",point,fetcher,1000)));
 assert.equal(calls,1);assert.equal(results.filter(Boolean).length,1);
});
test("provider failures are cached for a day; indoor and disabled lookups never fetch",async t=>{
 const env=await runtime(t);let calls=0;
 const fetcher=async()=>{calls++;throw new DOMException("Timed out","AbortError");};
 assert.equal(await resolveRunLocation(env,"alice","run",undefined,fetcher,1000),null);
 assert.equal(await resolveRunLocation({...env,LOCATION_LOOKUP_URL:""},"alice","run",point,fetcher,1000),null);
 assert.equal(calls,0);
 assert.equal(await resolveRunLocation(env,"alice","run",point,fetcher,1000),null);
 assert.equal(await resolveRunLocation(env,"alice","run",point,fetcher,11000),null);assert.equal(calls,1);
 await resolveRunLocation(env,"alice","run",point,fetcher,86401001);assert.equal(calls,2);
});
test("deletion during provider lookup cannot recreate location metadata",async t=>{
 const env=await runtime(t);
 await resolveRunLocation(env,"alice","run",point,async()=>{
  await env.DB.prepare("INSERT INTO run_deletions VALUES ('alice','run',1)").run();
  return Response.json({address:{city:"Town",state:"State"}});
 },1000);
 assert.equal((await env.DB.prepare("SELECT COUNT(*) n FROM run_locations").first()).n,0);
});
test("public geometry strips coordinates, unwraps date lines and preserves gaps",()=>{
 const segments=[{id:1,source:"GPS",startedMonotonicMs:0,endedMonotonicMs:20000},{id:2,source:"GPS",startedMonotonicMs:0,endedMonotonicMs:20}];
 const route=[{id:1,segmentId:1,monotonicMs:0,latitude:40,longitude:179.999},
  {id:2,segmentId:1,monotonicMs:1000,latitude:40.001,longitude:-179.999},
  {id:3,segmentId:1,monotonicMs:12000,latitude:40.002,longitude:-179.998},
  {id:4,segmentId:2,monotonicMs:1,latitude:40.003,longitude:-179.997}];
 const before=JSON.stringify(route),geometry=publicRoute("OUTDOOR",segments,route);
 assert.deepEqual(geometry.parts.map(p=>p.length),[2,1,1]);assert.equal(geometry.normalized,true);
 assert.ok(geometry.parts.flat(2).every(n=>Math.abs(n)<=0.5));assert.equal(JSON.stringify(route),before);
 assert.ok(!JSON.stringify(geometry).includes("latitude"));assert.ok(!JSON.stringify(geometry).includes("179.999"));
 assert.throws(()=>publicRoute("INDOOR",segments,route));
 assert.deepEqual(publicRoute("OUTDOOR",[segments[0]],[route[0]]).parts,[[[0,0]]]);
 assert.deepEqual(publicRoute("INDOOR",[],[]).parts,[]);
});

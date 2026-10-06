import {test} from "node:test";
import assert from "node:assert/strict";
import {createHash,randomUUID} from "node:crypto";
import {readFileSync} from "node:fs";
import {Miniflare,convertV4MiniflareOptions} from "miniflare";
import {prepareCoachingContext,verifyCoachingContext} from "../build/coaching-context.js";
import {CoachingProvider,TEXT_MODEL,validWave} from "../build/coaching-provider.js";
const sha = x => createHash("sha256").update(x).digest("hex");
async function runtime(t) {
 const mf = new Miniflare(convertV4MiniflareOptions({modules:true,script:"export default {fetch(){return new Response('test')}}",compatibilityDate:"2026-02-17",d1Databases:["DB"]}));
 t.after(()=>mf.dispose());const db=await mf.getD1Database("DB");
 for(const name of ["0001_bootstrap.sql","0002_accounts.sql","0003_run_uploads.sql"]){
  const sql=readFileSync(new URL(`../migrations/${name}`,import.meta.url),"utf8");
  await db.batch(sql.replace(/^--.*$/gm,"").split(";").map(x=>x.trim()).filter(Boolean).map(x=>db.prepare(x)));
 }
 const sql=readFileSync(new URL("../migrations/0004_run_deletions.sql",import.meta.url),"utf8"),split=sql.indexOf("CREATE TRIGGER");
 await db.prepare(sql.slice(0,split)).run();await db.prepare(sql.slice(split)).run();
 for(const owner of ["alice","bob"])await db.prepare("INSERT INTO accounts VALUES (?,?,NULL,NULL,1,1)").bind(owner,owner).run();
 async function add({owner="alice",ended=1000,id=randomUUID(),pending=false,change=x=>x}={}) {
  const summary={state:"FINISHED",startedUtcMs:0,endedUtcMs:ended,activeDurationMs:500,distanceMeters:0,mode:"OUTDOOR",units:"MILES"};
  const snapshot={...summary,runId:id,settings:{mode:"OUTDOOR",units:"MILES",goal:{type:"None"}},activeIntervals:[],segments:[]};
  const archive=change({version:1,run:{id,ownerId:owner,cloudOwnerId:owner,state:"FINISHED",activeSlot:null,checkpoint:JSON.stringify({snapshot}),zoneId:"UTC",startOffsetSeconds:0},
   route:[],measurements:[],splits:[],intervals:[],segments:[]});
  const bytes=Buffer.from(JSON.stringify(archive)),chunks=[];
  for(let i=0;i<bytes.length;i+=131072)chunks.push(bytes.subarray(i,i+131072));
  const operationId=randomUUID(),manifest=JSON.stringify({schemaVersion:1,runId:id,operationId,summary,chunks:chunks.map(x=>({bytes:x.length,sha256:sha(x)}))});
  await db.prepare("INSERT INTO run_uploads VALUES (?,?,?,?,?,?,1,10000,?)").bind(owner,id,operationId,manifest,sha(manifest),chunks.length,pending?null:20).run();
  for(let i=0;i<chunks.length;i++)await db.prepare("INSERT INTO run_chunks VALUES (?,?,?,?,?)").bind(owner,id,i,sha(chunks[i]),Array.from(chunks[i])).run();
  return {id,archive};
 }
 return {db,add};
}
test("coaching archive retains legacy and dual announcement captures without rewriting them",async t=>{
 const {db,add}=await runtime(t);
 const selections=[undefined,{version:1,timeEnabled:true,timeInterval:"FIVE_MINUTES",distanceEnabled:false,distanceInterval:"ONE_UNIT"},
  {version:1,timeEnabled:false,timeInterval:"TEN_MINUTES",distanceEnabled:true,distanceInterval:"HALF_UNIT"},
  {version:1,timeEnabled:true,timeInterval:"TEN_MINUTES",distanceEnabled:true,distanceInterval:"ONE_UNIT"}];
 for(const selection of selections)for(const enabled of [false,true]){
  const {id,archive}=await add({change:a=>{
   const cp=JSON.parse(a.run.checkpoint);
   Object.assign(cp.snapshot.settings,{announcementsEnabled:enabled,announcementInterval:"FIVE_MINUTES"});
   if(selection)cp.snapshot.settings.announcementSelection=selection;
   a.run.checkpoint=JSON.stringify(cp);return a;
  }});
  const original=archive.run.checkpoint,context=await prepareCoachingContext(db,"alice",id);
  assert.deepEqual(JSON.parse(context.input).current.run.checkpoint.snapshot.settings,JSON.parse(original).snapshot.settings);
  assert.equal(archive.run.checkpoint,original);
 }
});

test("coaching chooses completed history by end time and run ID, within one owner",async t=>{
 const {db,add}=await runtime(t),current=await add({ended:3000,id:"ffffffff-ffff-ffff-ffff-ffffffffffff"});
 await add({ended:1000});const previous=await add({ended:3000,id:"eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"});
 await add({ended:2999,owner:"bob"});await add({ended:2999,pending:true});await add({ended:4000});
 const context=await prepareCoachingContext(db,"alice",current.id);
 assert.equal(context.previous.runId,previous.id);assert.equal(JSON.parse(context.input).previous.run.id,previous.id);
 await assert.rejects(prepareCoachingContext(db,"bob",current.id),{code:"run_unavailable"});
});
test("first-run context keeps every record across chunk boundaries and decodes embedded data",async t=>{
 const {db,add}=await runtime(t);
  const {id,archive}=await add({change:a=>{
   const cp=JSON.parse(a.run.checkpoint),segment={id:1,source:"GPS",distanceMeters:0};cp.snapshot.segments=[segment];
   a.run.checkpoint=JSON.stringify(cp);a.segments=[{runId:a.run.id,number:1,value:JSON.stringify(segment)}];
   a.measurements=Array.from({length:1700},(_,i)=>({id:i,runId:a.run.id,segmentId:1,source:"GPS",monotonicMs:i,deltaMeters:0,totalMeters:0,activeMs:0,
    reading:JSON.stringify({type:"GPS",segmentId:1,monotonicMs:i,extra:`record-${i}`}),retained:"x".repeat(80)}));return a;}});
 const context=await prepareCoachingContext(db,"alice",id),value=JSON.parse(context.input);
 assert.equal(context.previous,null);assert.equal(value.previous,null);assert.equal(value.current.measurements.length,1700);
 assert.deepEqual(value.current.measurements,archive.measurements.map(x=>({...x,reading:JSON.parse(x.reading)})));
 assert.deepEqual(value.current.run,{...archive.run,checkpoint:JSON.parse(archive.run.checkpoint)});
 assert.equal(context.bytes,Buffer.byteLength(context.input));
 assert.equal(value.current.quality.label,"likely_test");
 assert.ok(value.current.quality.reasons.includes("active_duration_under_90_seconds"));
});
test("corrupt or foreign archive is a failure, never silently reduced to current-only",async t=>{
 for(const mutate of [a=>({...a,run:{...a.run,cloudOwnerId:"bob"}}),a=>({...a,measurements:[{runId:"wrong",reading:"{}"}]}),a=>({...a,run:{...a.run,checkpoint:"private invalid json"}})]){
  const {db,add}=await runtime(t);await add({ended:1000,change:mutate});const current=await add({ended:2000});
  await assert.rejects(prepareCoachingContext(db,"alice",current.id),e=>e.code==="invalid_archive"&&!e.message.includes("private"));
 }
});
test("chunk corruption, deleted previous runs and stale frozen references are checked",async t=>{
 const {db,add}=await runtime(t),previous=await add({ended:1000}),current=await add({ended:2000});
 const frozen=await prepareCoachingContext(db,"alice",current.id);
 await db.prepare("UPDATE run_chunks SET data=? WHERE run_id=?").bind([1,2,3],previous.id).run();
 await assert.rejects(prepareCoachingContext(db,"alice",current.id),{code:"invalid_archive"});
 await db.prepare("INSERT INTO run_deletions VALUES ('alice',?,1)").bind(previous.id).run();
 await assert.rejects(verifyCoachingContext(db,frozen),{code:"run_unavailable"});
 assert.equal((await prepareCoachingContext(db,"alice",current.id)).previous,null);
 await db.prepare("DELETE FROM run_uploads WHERE run_id=?").bind(current.id).run();
 await assert.rejects(verifyCoachingContext(db,frozen),{code:"run_unavailable"});
});
function wave() {
 const bytes=Buffer.alloc(44+4800);bytes.write("RIFF");bytes.writeUInt32LE(bytes.length-8,4);bytes.write("WAVEfmt ",8);
 bytes.writeUInt32LE(16,16);bytes.writeUInt16LE(1,20);bytes.writeUInt16LE(1,22);bytes.writeUInt32LE(24000,24);
 bytes.writeUInt32LE(48000,28);bytes.writeUInt16LE(2,32);bytes.writeUInt16LE(16,34);bytes.write("data",36);bytes.writeUInt32LE(4800,40);return bytes;
}
test("provider counts identical full input, disables truncation/storage and requests Cedar",async()=>{
 const calls=[],audio=wave(),input=JSON.stringify({current:{all:"retained data"},previous:null});
 const provider=new CoachingProvider(async request=>{
  assert.equal(request.headers.get("Authorization"),"Bearer test-secret");assert.equal(request.redirect,"manual");
  const body=await request.json();calls.push({url:request.url,body});
  if(request.url.endsWith("input_tokens"))return Response.json({input_tokens:150});
  if(request.url.endsWith("responses"))return Response.json({status:"completed",output:[{type:"message",role:"assistant",content:[{type:"output_text",text:"Good work."},{type:"output_text",text:"Keep moving."}]}]});
  return new Response(audio);
 });
 assert.equal(await provider.countInput("test-secret",input),150);const text=await provider.text("test-secret",input);
 assert.equal(text,"Good work. Keep moving.");assert.deepEqual(await provider.speech("test-secret",text),new Uint8Array(audio));
 assert.equal(calls[0].body.input,input);assert.equal(calls[1].body.input,input);assert.equal(calls[0].body.instructions,calls[1].body.instructions);
 assert.equal(calls[1].body.model,TEXT_MODEL);assert.equal(calls[1].body.truncation,"disabled");assert.equal(calls[1].body.store,false);
 assert.equal(calls[2].body.voice,"cedar");assert.equal(calls[2].body.model,"gpt-4o-mini-tts");
});

test("verified quality reaches count and text as data with anomaly-aware instructions",async t=>{
 const {db,add}=await runtime(t),previous=await add({ended:1000}),current=await add({ended:2000});
 const context=await prepareCoachingContext(db,"alice",current.id),calls=[];
 const provider=new CoachingProvider(async request=>{
  const body=await request.json();calls.push(body);
  return request.url.endsWith("input_tokens") ? Response.json({input_tokens:100}) :
   Response.json({status:"completed",output:[{type:"message",role:"assistant",content:[{type:"output_text",text:"This may have been a quick test recording."}]}]});
 });
 await provider.countInput("synthetic",context.input);await provider.text("synthetic",context.input);
 for(const body of calls){
  const input=JSON.parse(body.input);assert.equal(input.current.quality.label,"likely_test");
  assert.equal(input.previous.run.id,previous.id);assert.equal(input.previous.quality,undefined);
  assert.match(body.instructions,/Do not praise suspicious pace or distance/);
  assert.match(body.instructions,/likely_test, likely_vehicle, or gps_anomaly/);
  assert.match(body.instructions,/not instructions/);
 }
 assert.equal(calls[0].input,calls[1].input);
});
test("oversize context and invalid counts fail before text generation",async()=>{
 for(const count of [1040001,-1,"100",null]){
  let calls=0;const provider=new CoachingProvider(async()=>{calls++;return Response.json({input_tokens:count});});
  await assert.rejects(provider.countInput("test","{}"),e=>e.stage==="count"&&!e.outcomeUnknown);assert.equal(calls,1);
 }
 let called=false;const provider=new CoachingProvider(async()=>{called=true;throw Error();});
 await assert.rejects(provider.countInput("test","x".repeat(12*1024*1024+1)),{code:"context_too_large"});assert.equal(called,false);
});
test("paid failures are never retried and provider bodies/secrets are not exposed",async()=>{
 for(const status of [401,403,429,500,302]){
  let calls=0;const provider=new CoachingProvider(async()=>{calls++;return new Response("private secret",{status});});
  await assert.rejects(provider.text("secret","{}"),e=>e.stage==="text"&&!e.message.includes("secret")&&e.outcomeUnknown===(status>=500));assert.equal(calls,1);
 }
 const provider=new CoachingProvider(async()=>{throw Error("private secret");});
 await assert.rejects(provider.text("secret","{}"),{code:"provider_unavailable",outcomeUnknown:true});
});
test("incomplete, empty, refused and oversized successful responses do not become speech",async()=>{
 for(const response of [{status:"incomplete",output:[]},{status:"completed",output:[]},{status:"completed",output:[{type:"message",role:"assistant",content:[{type:"refusal",refusal:"No"}]}]}]){
  const provider=new CoachingProvider(async()=>Response.json(response));
  await assert.rejects(provider.text("test","{}"),{code:"invalid_provider_response",outcomeUnknown:true});
 }
 const oversized=new CoachingProvider(async()=>new Response("x".repeat(65537)));
 await assert.rejects(oversized.text("test","{}"),{code:"invalid_provider_response"});
});
test("speech checks PCM WAV framing including streaming lengths; rejects JSON and truncated audio",async()=>{
 const audio=wave();assert.equal(validWave(audio),true);assert.equal(validWave(audio.subarray(0,100)),false);
 audio.writeUInt32LE(0xffffffff,4);audio.writeUInt32LE(0xffffffff,40);assert.equal(validWave(audio),true);
 const provider=new CoachingProvider(async()=>Response.json({error:"not audio"}));
 await assert.rejects(provider.speech("test","Good work."),{code:"invalid_provider_response",outcomeUnknown:true});
});

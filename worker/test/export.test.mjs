import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createHash } from "node:crypto";
import {parseWeather} from "../build/run-weather.js";
const source=readFileSync(new URL("../web/export.browserjs",import.meta.url),"utf8");
const {createCsvExport,collectExportRuns,fetchWithBackoff,abortableDelay,verifyCoaching}=await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
test("throttled download retries the same chunk after Retry-After, with bounded attempts",async()=>{
 const controller=new AbortController(),waits=[];let calls=0;
 const result=await fetchWithBackoff(async()=>++calls<3?new Response("busy",{status:429,headers:{"Retry-After":"60"}}):new Response("chunk"),controller.signal,()=>{},async ms=>waits.push(ms));
 assert.equal(await result.text(),"chunk");assert.equal(calls,3);assert.deepEqual(waits,[60000,60000]);
 calls=0;const failed=await fetchWithBackoff(async()=>{calls++;return new Response(null,{status:429});},controller.signal,()=>{},async()=>{});assert.equal(failed.status,429);assert.equal(calls,4);
});
test("cancel or sign-out aborts a throttled wait without retrying",async()=>{
 const controller=new AbortController();let calls=0;
 await assert.rejects(fetchWithBackoff(async()=>{calls++;return new Response(null,{status:429});},controller.signal,()=>controller.abort()),{name:"AbortError"});assert.equal(calls,1);
 const active=new AbortController();const waiting=abortableDelay(60000,active.signal);active.abort();await assert.rejects(waiting,{name:"AbortError"});
});
// Independent character-level reader: accepts embedded quotes, commas and CRLF.
function parse(text){
  const rows=[];let row=[],field="",quoted=false;
  for(let i=text.charCodeAt(0)===0xfeff?1:0;i<text.length;i++){
    const c=text[i];
    if(c==='"'){if(quoted&&text[i+1]==='"'){field+='"';i++;}else quoted=!quoted;}
    else if(c===","&&!quoted){row.push(field);field="";}
    else if(c==="\r"&&text[i+1]==="\n"&&!quoted){row.push(field);rows.push(row);row=[];field="";i++;}
    else field+=c;
  }
  assert.equal(quoted,false);assert.equal(field,"");assert.equal(row.length,0);
  const header=rows.shift();return rows.map(values=>{assert.equal(values.length,header.length);return Object.fromEntries(header.map((key,i)=>[key,values[i]]));});
}
const id=n=>`00000000-0000-4000-8000-${String(n).padStart(12,"0")}`;
function fixture(){
  const runId=id(1),s={settings:{mode:"OUTDOOR",units:"MILES",countdownSeconds:3,strideLengthMeters:null,goal:{type:"Distance",meters:1609.344}},startedUtcMs:1700000000000,endedUtcMs:1700000001000,distanceMeters:0.123456789,activeDurationMs:1000};
  const value={version:1,run:{id:runId,ownerId:"private",cloudOwnerId:"owner",activeSlot:null,state:"FINISHED",checkpoint:JSON.stringify({snapshot:s,eventSequence:9}),zoneId:'=SUM(1,2)\r\n"日本語 🏃"',startOffsetSeconds:-18000,updatedUtcMs:1700000001000,interrupted:false},
    route:[{id:3,runId,segmentId:1,monotonicMs:42,latitude:-40.123456789,longitude:-73,accuracyMeters:0.25}],
    measurements:[{id:7,runId,segmentId:1,monotonicMs:42,source:"GPS",deltaMeters:0.123456789,totalMeters:0.123456789,activeMs:1000,reading:'{"note":"a,b\\n\\\"c\\\""}'}],
    splits:[{runId,number:1,meters:0.123456789,durationMs:1000,partial:true}],
    intervals:[{runId,number:1,value:JSON.stringify({number:1,epoch:0,startMonotonicMs:10,endMonotonicMs:1010,startUtcMs:1700000000000,endUtcMs:1700000001000,startActiveMs:0,endActiveMs:1000})}],
    segments:[{runId,number:1,value:JSON.stringify({id:1,source:"GPS",startedMonotonicMs:10,endedMonotonicMs:1010,startedActiveMs:0,distanceMeters:0.123456789})}]};
  return {value,s};
}
test("single CSV reconstructs complete archive and preserves precision, Unicode and multiline data",async()=>{
  const data=fixture(),writer=createCsvExport();await writer.add(data);const blob=writer.finish();
  assert.deepEqual([...new Uint8Array(await blob.arrayBuffer()).slice(0,3)],[239,187,191]);
  const rows=parse(await blob.text());assert.equal(rows.length,6);
  const rebuilt={version:Number(rows[0].archive_version),run:JSON.parse(rows[0].record_json)};
  for(const [group,type] of [["route","GPS_POINT"],["measurements","MEASUREMENT"],["splits","SPLIT"],["intervals","ACTIVE_INTERVAL"],["segments","SOURCE_SEGMENT"]])rebuilt[group]=rows.filter(r=>r.record_type===type).sort((a,b)=>a.record_index-b.record_index).map(r=>JSON.parse(r.record_json));
  assert.deepEqual(rebuilt,data.value);assert.equal(rows[1].latitude,"-40.123456789");
  assert.equal(rows[0].time_zone,"'"+data.value.run.zoneId);assert.equal(rows[0].distance_meters,"0.123456789");
  assert.equal(rows[1].start_utc,"");assert.equal(rows[0].stride_length_meters,"");
});
test("CSV reconstructs legacy and dual announcement captures without changing checkpoint bytes",async()=>{
 const selections=[undefined,{version:1,timeEnabled:true,timeInterval:"FIVE_MINUTES",distanceEnabled:false,distanceInterval:"ONE_UNIT"},
  {version:1,timeEnabled:false,timeInterval:"TEN_MINUTES",distanceEnabled:true,distanceInterval:"HALF_UNIT"},
  {version:1,timeEnabled:true,timeInterval:"TEN_MINUTES",distanceEnabled:true,distanceInterval:"ONE_UNIT"}];
 for(const selection of selections)for(const enabled of [false,true]){
  const data=fixture();Object.assign(data.s.settings,{announcementsEnabled:enabled,announcementInterval:"FIVE_MINUTES"});
  if(selection)data.s.settings.announcementSelection=selection;
  data.value.run.checkpoint=JSON.stringify({snapshot:data.s,eventSequence:9});
  const writer=createCsvExport();await writer.add(data);
  const row=parse(await writer.finish().text()).find(r=>r.record_type==="RUN");
  const restored=JSON.parse(row.record_json);
  assert.equal(restored.checkpoint,data.value.run.checkpoint);
  assert.deepEqual(JSON.parse(restored.checkpoint).snapshot.settings,data.s.settings);
 }
});

test("formula-like text is escaped without altering stored JSON",async()=>{
  for(const text of ["+1","-1","@SUM(A1)"," \t=1","\rhello","\nhello"]){const data=fixture();data.value.run.zoneId=text;const writer=createCsvExport();await writer.add(data);const row=parse(await writer.finish().text())[0];assert.equal(row.time_zone,"'"+text);assert.equal(JSON.parse(row.record_json).zoneId,text);}
});
test("empty history has header only; absent child rows and zero pace remain valid",async()=>{
  assert.deepEqual(parse(await createCsvExport().finish().text()),[]);
  const data=fixture();for(const key of ["route","measurements","splits","intervals","segments"])data.value[key]=[];data.s.distanceMeters=0;
  const writer=createCsvExport();await writer.add(data);const rows=parse(await writer.finish().text());assert.equal(rows.length,1);assert.equal(rows[0].pace_ms_per_km,"");
});
test("unknown version/fields, duplicate runs and foreign children cannot be silently exported",async()=>{
  for(const change of [d=>d.value.version=2,d=>d.value.future=[],d=>d.value.route[0].runId=id(2)]){const data=fixture();change(data);await assert.rejects(createCsvExport().add(data));}
  const writer=createCsvExport();await writer.add(fixture());await assert.rejects(writer.add(fixture()));
});
test("output budget and discarded builders cannot produce a partial file",async()=>{
  const writer=createCsvExport(1500);await assert.rejects(writer.add(fixture()),/128 MiB/);assert.throws(()=>writer.finish());
  const discarded=createCsvExport();discarded.discard();assert.throws(()=>discarded.finish());
});
const receipt=n=>({runId:id(n),operationId:id(n+100),manifestHash:"a".repeat(64),completedAt:n});
test("all pages are collected even beyond the initially visible twenty",async()=>{
  const calls=[];const runs=await collectExportRuns(async after=>{calls.push(after);return after?{runs:Array.from({length:5},(_,i)=>receipt(i+21)),next:null}:{runs:Array.from({length:20},(_,i)=>receipt(i+1)),next:"page2"};});
  assert.equal(runs.length,25);assert.deepEqual(calls,[null,"page2"]);
});
test("pagination rejects loops, duplicate IDs, invalid receipts and metadata budget overflow",async()=>{
  await assert.rejects(collectExportRuns(async()=>({runs:[],next:"loop"})));
  await assert.rejects(collectExportRuns(async()=>({runs:[receipt(1),receipt(1)],next:null})));
  await assert.rejects(collectExportRuns(async()=>({runs:[{...receipt(1),manifestHash:"bad"}],next:null})));
  await assert.rejects(collectExportRuns(async()=>({runs:[receipt(1)],next:null}),()=>true,10),/128 MiB/);
});
test("cancel after delayed list or during a large archive aborts",async()=>{
  let current=true;await assert.rejects(collectExportRuns(async()=>{current=false;return {runs:[],next:null};},()=>current),{name:"AbortError"});
  const data=fixture();data.value.route=Array.from({length:1001},()=>data.value.route[0]);const writer=createCsvExport();current=true;
  setTimeout(()=>current=false,0);await assert.rejects(writer.add(data,()=>current),{name:"AbortError"});writer.discard();assert.throws(()=>writer.finish());
});
function coachingFixture() {
 const hash=x=>createHash("sha256").update(x).digest("hex"),bytes=Buffer.alloc(40001);
 for(let i=0;i<bytes.length;i++)bytes[i]=i%256;
 const chunks=[];for(let offset=0;offset<bytes.length;offset+=16384){const b=bytes.subarray(offset,offset+16384);chunks.push({index:offset/16384,bytes:b.length,sha256:hash(b),base64:b.toString("base64")});}
 return {bytes,coaching:{version:1,runId:id(1),operationId:id(101),state:"ready",currentManifestHash:"a".repeat(64),previousRunId:null,previousManifestHash:null,
  message:'=SUM(1,2)\nA "good", run 🏃',error:null,createdAt:1,updatedAt:2,audio:{mediaType:"audio/wav",bytes:bytes.length,sha256:hash(bytes),chunks}}};
}
test("CSV v4 reconstructs saved recap and audio exactly with spreadsheet-safe text and bounded cells",async()=>{
 const {bytes,coaching}=coachingFixture(),writer=createCsvExport();await writer.add({...fixture(),coaching});const rows=parse(await writer.finish().text());
 assert.ok(rows.every(r=>r.export_version==="4"));const meta=rows.find(r=>r.record_type==="COACHING");assert.equal(meta.coaching_text,"'"+coaching.message);
 const rebuilt=JSON.parse(meta.record_json);const audio=rows.filter(r=>r.record_type==="COACHING_AUDIO").map(r=>JSON.parse(r.record_json));
 assert.equal(rebuilt.message,coaching.message);assert.equal(rebuilt.audio.chunkCount,audio.length);
 assert.deepEqual(Buffer.concat(audio.map(r=>Buffer.from(r.base64,"base64"))),bytes);
 assert.ok(rows.every(r=>r.record_json.length<32767));
});
test("coaching verification rejects wrong owner-run binding, corrupt/missing audio and credential fields",async()=>{
 for(const mutate of [c=>c.runId=id(2),c=>c.audio.chunks.pop(),c=>c.audio.chunks[0].base64="x".repeat(c.audio.chunks[0].base64.length),c=>c.audio.sha256="b".repeat(64),c=>c.token_hash="private",c=>c.audio.bytes=4194305]){
  const {coaching}=coachingFixture();mutate(coaching);await assert.rejects(verifyCoaching({runId:id(1),coaching},id(1)));
 }
 const {coaching}=coachingFixture();await assert.rejects(verifyCoaching({runId:id(1),coaching},id(1),()=>false),{name:"AbortError"});
});
test("achievement export preserves rule version and occurrence and rejects foreign runs",async()=>{
 const data=fixture(),writer=createCsvExport();await writer.add(data);
 const award={id:"distance-2",occurrence:"once",runId:data.value.run.id,name:"Five Alive",detail:"First 5K",date:"2026-09-18",version:1};
 assert.throws(()=>writer.addAchievements([{...award,runId:"foreign"}]));writer.addAchievements([award]);
 const row=parse(await writer.finish().text()).find(r=>r.record_type==="ACHIEVEMENT");assert.deepEqual(JSON.parse(row.record_json),award);assert.equal(row.export_version,"4");
});
test("failed speech preserves generated text in CSV without inventing an audio record",async()=>{
 const {coaching}=coachingFixture();coaching.state="unknown";coaching.audio=null;coaching.error="interrupted";
 const writer=createCsvExport();await writer.add({...fixture(),coaching});const rows=parse(await writer.finish().text());
 assert.equal(rows.filter(r=>r.record_type==="COACHING_AUDIO").length,0);assert.equal(rows.find(r=>r.record_type==="COACHING").coaching_state,"unknown");
});


test("CSV photo records reconstruct exact JPEG bytes and reject corrupt payloads",async()=>{
 const data=fixture(),bytes=new Uint8Array(20000).fill(7);bytes[0]=255;bytes[1]=216;bytes[19998]=255;bytes[19999]=217;
 const hash=async b=>[...new Uint8Array(await crypto.subtle.digest("SHA-256",b))].map(v=>v.toString(16).padStart(2,"0")).join("");
 const metadata={revision:id(3),options:{time:true,distance:true,pace:true,route:false},bytes:bytes.length,sha256:await hash(bytes),updatedAt:1,publicUrl:null};
 const writer=createCsvExport();await writer.add({...data,photo:{metadata,bytes}});
 const rows=parse(await writer.finish().text());assert.equal(rows.find(r=>r.record_type==="PHOTO").export_version,"4");
 const chunks=rows.filter(r=>r.record_type==="PHOTO_IMAGE").map(r=>JSON.parse(r.record_json));
 assert.deepEqual(Buffer.concat(chunks.map(c=>Buffer.from(c.base64,"base64"))),Buffer.from(bytes));
 const broken=createCsvExport();await assert.rejects(()=>broken.add({...data,photo:{metadata:{...metadata,sha256:"0".repeat(64)},bytes}}));
 for(const path of ["p/"+"a".repeat(64),"r/"+"b".repeat(32)]){
  const linked=createCsvExport();await linked.add({...data,photo:{metadata:{...metadata,publicUrl:`https://wayirun-dev.unopenedparachute.workers.dev/${path}`},bytes}});
  const production=createCsvExport();await production.add({...data,photo:{metadata:{...metadata,publicUrl:`https://wayirun.slopcopy.com/${path}`},bytes}});
 }
 for(const url of ["https://evil.test/r/"+"a".repeat(32),"https://wayirun-dev.unopenedparachute.workers.dev/r/short"]){
  await assert.rejects(()=>createCsvExport().add({...data,photo:{metadata:{...metadata,publicUrl:url},bytes}}));
 }
});

test("CSV retains selected photo weather exactly and rejects selection/metadata disagreement",async()=>{
 const data=fixture(),bytes=new Uint8Array([255,216,255,217]);
 const weather=parseWeather({utc_offset_seconds:0,hourly_units:{time:"unixtime",temperature_2m:"\u00b0C"},hourly:{time:[0],temperature_2m:[20],weather_code:[2]}},
  {latitude:40.8,longitude:-74,observedUtcMs:0,endpoint:"archive"},3600000);
 const metadata={revision:id(3),options:{time:true,distance:true,pace:true,route:false,weather:true},bytes:bytes.length,sha256:createHash("sha256").update(bytes).digest("hex"),updatedAt:1,publicUrl:null,weather};
 const writer=createCsvExport();await writer.add({...data,photo:{metadata,bytes}});
 const record=JSON.parse(parse(await writer.finish().text()).find(r=>r.record_type==="PHOTO").record_json);
 assert.deepEqual(record.weather,weather);
 for(const invalid of [{...metadata,weather:undefined},{...metadata,options:{...metadata.options,weather:false}},
  {...metadata,weather:{...weather,owner:"unexpected"}}])await assert.rejects(()=>createCsvExport().add({...data,photo:{metadata:invalid,bytes}}));
});

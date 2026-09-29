import {PUBLIC_PHOTO_PAGE} from "../build/photos.js";
import {publicRoute} from "../build/public-route.js";
import {parseWeather} from "../build/run-weather.js";
// Local browser QA only. Never imported by the Worker. No real accounts or runs.
import http from "node:http";
import { readFileSync } from "node:fs";
import { createHash, randomBytes } from "node:crypto";
const root=new URL("../web/",import.meta.url), sha=b=>createHash("sha256").update(b).digest("hex");
const photoBytes=readFileSync(new URL("../test/photo-fixture.jpg",import.meta.url));
const weatherFixture=parseWeather({utc_offset_seconds:0,hourly_units:{time:"unixtime",temperature_2m:"\u00b0C"},hourly:{time:[0],temperature_2m:[20],weather_code:[2]}},
 {latitude:40.8,longitude:-74,observedUtcMs:0,endpoint:"archive"},3600000);
const {latitude:_latitude,longitude:_longitude,...publicWeatherFixture}=weatherFixture;
const records=new Map();let throttlePending=true,deleteFailure=true;
const publications=new Map();
const audio=Buffer.alloc(44+24000*2*30);audio.write("RIFF");audio.writeUInt32LE(audio.length-8,4);audio.write("WAVEfmt ",8);
audio.writeUInt32LE(16,16);audio.writeUInt16LE(1,20);audio.writeUInt16LE(1,22);audio.writeUInt32LE(24000,24);audio.writeUInt32LE(48000,28);audio.writeUInt16LE(2,32);audio.writeUInt16LE(16,34);audio.write("data",36);audio.writeUInt32LE(audio.length-44,40);
const audioChunks=[];for(let offset=0;offset<audio.length;offset+=16384){const b=audio.subarray(offset,offset+16384);audioChunks.push({index:offset/16384,bytes:b.length,sha256:sha(b),base64:b.toString("base64")});}
function fixture(number,kind) {
 const id=`00000000-0000-4000-8000-${String(number).padStart(12,"0")}`,op=`10000000-0000-4000-8000-${String(number).padStart(12,"0")}`;
 const segments=kind==="indoor"||kind==="empty"?[]:[{id:1,source:"GPS",startedMonotonicMs:0,startedActiveMs:0,endedMonotonicMs:10000,distanceMeters:50},{id:2,source:"GPS",startedMonotonicMs:20000,startedActiveMs:10000,endedMonotonicMs:30000,distanceMeters:75}];
 const points=[[40.781,-73.969],[40.7815,-73.9685],[40.782,-73.968],[40.783,-73.967],[40.7835,-73.9675],[40.784,-73.968]];
 const route=segments.length?points.slice(0,kind==="single"?1:6).map(([latitude,longitude],i)=>({id:i+1,runId:id,segmentId:i<3?1:2,monotonicMs:i<3?1000+i*3000:21000+(i-3)*3000,latitude,longitude,accuracyMeters:5})):[];
 const settings={mode:kind==="indoor"?"INDOOR":"OUTDOOR",units:"KILOMETERS",countdownSeconds:0,goal:{type:"None"},strideLengthMeters:null};
 const snapshot={runId:id,state:"FINISHED",settings,startedUtcMs:1700000000000-number*86400000,endedUtcMs:1700000030000-number*86400000,activeDurationMs:30000,distanceMeters:125,segments,activeIntervals:[],splits:[]};
 const summary={state:"FINISHED",startedUtcMs:snapshot.startedUtcMs,endedUtcMs:snapshot.endedUtcMs,activeDurationMs:30000,distanceMeters:125,mode:settings.mode,units:settings.units};
 const archive={version:1,run:{id,cloudOwnerId:"fixture",state:"FINISHED",activeSlot:null,checkpoint:JSON.stringify({snapshot}),zoneId:"UTC"},route,measurements:[],segments:segments.map(s=>({runId:id,number:s.id,value:JSON.stringify(s)})),intervals:[],splits:[{runId:id,number:1,meters:125,durationMs:30000,partial:true}]};
 const bytes=Buffer.from(JSON.stringify(archive));
 const manifest={schemaVersion:1,runId:id,operationId:op,summary,chunks:[{bytes:bytes.length,sha256:sha(bytes)}]};
 const manifestJson=JSON.stringify(manifest), receipt={runId:id,operationId:op,manifestHash:sha(manifestJson),completedAt:number,summary};
 records.set(id,{receipt,manifest,manifestJson,bytes,kind});
 publications.set(id,{shared:false,photoVisible:true,revision:0,publicUrl:null});
}
["outdoor","indoor","empty","single","corrupt"].forEach((kind,i)=>fixture(i+1,kind));
const files={"/public-photo.js":"public-photo.browserjs","/achievements.js":"achievements.browserjs","/":"index.html","/app.js":"app.browserjs","/route.js":"route.browserjs","/map.js":"map.browserjs","/export.js":"export.browserjs","/style.css":"style.css"};
http.createServer((req,res)=>{
 const url=new URL(req.url,"http://127.0.0.1:8788"),path=url.pathname;
 const nonce=randomBytes(32).toString("hex");
 const policy=readFileSync(new URL("../src/browser.ts",import.meta.url),"utf8").match(/out.headers.set\("Content-Security-Policy", `([^`]+)`\)/)[1].replaceAll("${nonce}",nonce);
 res.setHeader("Content-Security-Policy",policy);res.setHeader("Referrer-Policy","strict-origin-when-cross-origin");res.setHeader("Cache-Control","no-store");
 const json=(value,status=200)=>{res.statusCode=status;res.setHeader("Content-Type","application/json");res.end(JSON.stringify(value));};
 if(path.startsWith("/r/")){
  const id=[...records.keys()].find(id=>id.replaceAll("-","")===path.slice(3));
  if(!id||!publications.get(id)?.shared)return json({error:"not_found"},404);
  res.statusCode=302;res.setHeader("Location","/p/"+id.replaceAll("-","").padEnd(64,"0"));return res.end();
 }
 if(path.startsWith("/p/")) {
   const publicToken=path.split("/")[2],id=[...records.keys()].find(id=>id.replaceAll("-","").padEnd(64,"0")===publicToken);
   const record=id?records.get(id):publicToken==="a".repeat(64)?[...records.values()][0]:null;
   const state=id?publications.get(id):{shared:true,photoVisible:true};
   if(!record||!state?.shared)return json({error:"not_found"},404);
   const photoVisible=state.photoVisible&&record.kind==="outdoor";
   if(path.endsWith("/image")){if(!photoVisible)return json({error:"not_found"},404);res.setHeader("Content-Type","image/jpeg");return res.end(photoBytes);}
   if(path.endsWith("/data")){const a=JSON.parse(record.bytes),s=JSON.parse(a.run.checkpoint).snapshot;return json({run:s,settings:s.settings,geometry:publicRoute(s.settings.mode,s.segments,a.route),location:{label:"New York, New York",attribution:{label:"Location data: OpenStreetMap contributors"}},splits:a.splits,photoVisible,...(photoVisible?{weather:publicWeatherFixture}:{})});}
   res.setHeader("Content-Type","text/html");res.setHeader("Content-Security-Policy","default-src 'self';script-src 'self';style-src 'self' 'unsafe-inline';img-src 'self';connect-src 'self'");return res.end(PUBLIC_PHOTO_PAGE);
 }
 if(files[path]){
   let body=readFileSync(new URL(files[path],root),"utf8").replaceAll("__CSP_NONCE__",nonce);
   if(path==="/"){body=body.replace('src="https://accounts.google.com/gsi/client"','src="/fixture-google.js"');res.setHeader("Set-Cookie",`fixture-case=${["tile-failure","export","empty","slow","throttle"].includes(url.searchParams.get("case"))?url.searchParams.get("case"):"normal"}; Path=/`);}
   res.setHeader("Content-Type",path==="/"?"text/html":path.endsWith(".css")?"text/css":"text/javascript");return res.end(body);
 }
 if(path==="/fixture-google.js"){res.setHeader("Content-Type","text/javascript");return res.end(`window.google={accounts:{id:{initialize(options){this.options=options},renderButton(element){const b=document.createElement('button');b.textContent='Fixture sign-in';b.onclick=()=>this.options.callback({credential:'fixture'});element.append(b)},disableAutoSelect(){}}}}`);}
 if(path==="/web-api/config")return json({clientId:"fixture"});
 if(path==="/web-api/challenge")return json({nonce:"a".repeat(64)});
 if(path==="/web-api/google"){res.setHeader("Set-Cookie","fixture-signed=yes; Path=/");return json({signedIn:true});}
 if(path==="/web-api/logout"){res.setHeader("Set-Cookie","fixture-signed=no; Path=/");return json({signedOut:true});}
 if(req.headers.cookie?.includes("fixture-signed=no"))return json({error:"unauthorized"},401);
 if(path==="/web-api/account")return json({account:{id:"fixture",displayName:"Local route verification"}});
 if(path==="/web-api/runs"){
   if(req.headers.cookie?.includes("fixture-case=empty"))return json({runs:[],next:null});
   const exporting=/fixture-case=(export|slow|throttle)/.test(req.headers.cookie||"");
   const rows=[...records.values()].filter(r=>!exporting||r.kind!=="corrupt").map(r=>r.receipt);
   const start=Number(url.searchParams.get("after")||0),end=exporting?start+2:rows.length;
   return json({runs:rows.slice(start,end),next:end<rows.length?String(end):null});
 }
 const record=records.get(path.split("/")[3]);
 if(path.startsWith("/web-api/publications/")&&record){
  const id=record.receipt.runId,state=publications.get(id);
  if(req.method==="GET")return json({publication:state});
  if(req.method!=="PUT")return json({error:"method_not_allowed"},405);
  let text="";req.on("data",part=>text+=part);req.on("end",()=>{
   const input=JSON.parse(text);if(input.expectedRevision!==state.revision)return json({error:"revision_conflict",publication:state},409);
   if(input.action==="photo")state.photoVisible=input.photoVisible;else state.shared=input.action==="share";
   state.revision++;state.publicUrl=state.shared?"http://127.0.0.1:"+(process.argv[2]||8788)+"/r/"+id.replaceAll("-",""):null;
   return json({publication:state});
  });return;
 }
 if(path.startsWith("/web-api/photos/")&&record){
   if(record.kind!=="outdoor")return json({photo:null});
   if(path.endsWith("/image")){res.setHeader("Content-Type","image/jpeg");return res.end(photoBytes);}
   return json({photo:{revision:record.receipt.operationId,options:{time:true,distance:true,pace:true,route:true},bytes:photoBytes.length,sha256:sha(photoBytes),updatedAt:1,publicUrl:"https://wayirun-dev.unopenedparachute.workers.dev/p/"+"a".repeat(64)}});
 }
 if(path.startsWith("/web-api/coaching-history/")&&record){
   if(req.method!=="GET")return json({error:"method_not_allowed"},405);
   if(record.kind==="empty"||record.kind==="indoor")return json({runId:record.receipt.runId,coaching:null});
   return json({runId:record.receipt.runId,coaching:{version:1,runId:record.receipt.runId,operationId:record.receipt.operationId,
    state:record.kind==="single"?"unknown":"ready",currentManifestHash:record.receipt.manifestHash,previousRunId:null,previousManifestHash:null,
    message:record.kind==="single"?"Saved recap; speech was interrupted.":"A steady run with a strong finish. You made time to move today — nice work! <script>not executable</script>",
    error:record.kind==="single"?"interrupted":null,createdAt:1700000100,updatedAt:1700000110,
    audio:record.kind==="single"?null:{mediaType:"audio/wav",bytes:audio.length,sha256:sha(audio),chunks:audioChunks}}});
 }
 if(req.method==="DELETE"&&record){
   if(record.kind==="indoor"&&deleteFailure){deleteFailure=false;return json({error:"fixture_failure"},503);}
   records.delete(record.receipt.runId);return json({runId:record.receipt.runId,deleted:true});
 }
 if(record&&path.endsWith("/chunks/0")){if(req.headers.cookie?.includes("fixture-case=throttle")&&throttlePending){throttlePending=false;res.setHeader("Retry-After","2");return json({error:"too_many_requests"},429);}res.setHeader("Content-Type","application/octet-stream");const send=()=>res.end(record.kind==="corrupt"?Buffer.from("corrupt"):record.bytes);if(req.headers.cookie?.includes("fixture-case=slow"))return setTimeout(send,3000);return send();}
 if(record)return json({...record.receipt,manifest:record.manifest,manifestJson:record.manifestJson});
 return json({error:"not_found"},404);
}).listen(Number(process.argv[2] || 8788),"127.0.0.1",()=>console.log("Local-only route fixture ready (five synthetic runs)"));

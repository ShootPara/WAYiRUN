import {accessGuard,sessionAccount,reply,type AuthEnv} from "./auth.js";
import {readVerifiedRun} from "./coaching-context.js";
const uuid="[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}";
type Photo={revision:string;jpeg:number[]|ArrayBuffer;options:string;public_token:string|null;updated_at:number;owner_id:string;run_id:string};
const headers={"Cache-Control":"no-store","X-Content-Type-Options":"nosniff","Referrer-Policy":"no-referrer"};
async function digest(row:Photo) {return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256",new Uint8Array(row.jpeg))),x=>x.toString(16).padStart(2,"0")).join("");}
function image(row:Photo) {return new Response(new Uint8Array(row.jpeg),{headers:{...headers,"Content-Type":"image/jpeg"}});}
export async function handlePhotos(request:Request,env:AuthEnv):Promise<Response> {
 try {
  const url=new URL(request.url),match=new RegExp(`^/api/photos/(${uuid})(/image)?$`).exec(url.pathname);
  if(!match || url.search)return reply({error:"not_found"},404);
  if(request.headers.has("Origin")||request.headers.has("Cookie"))return reply({error:"origin_not_allowed"},403);
  const denied=await accessGuard(request,env,true);if(denied)return denied;
  const session=await sessionAccount(request,env);if(!session)return reply({error:"unauthorized"},401);
  const owner=session.account.id,id=match[1]!;
  const exists=()=>env.DB.prepare("SELECT run_id FROM run_uploads WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL AND NOT EXISTS (SELECT 1 FROM run_deletions WHERE owner_id=? AND run_id=?)").bind(owner,id,owner,id).first();
  if(!await exists())return reply({error:"not_found"},404);
  const read=()=>env.DB.prepare("SELECT * FROM run_photos WHERE owner_id=? AND run_id=?").bind(owner,id).first<Photo>();
  let expected:{revision:string;jpeg:Uint8Array;options:string;public:boolean}|null=null;
  if(request.method==="PUT" && !match[2]) {
   if(request.headers.get("Content-Type")!=="image/jpeg")return reply({error:"invalid_photo"},400);
   const revision=request.headers.get("X-Photo-Revision")??"",visibility=request.headers.get("X-Photo-Public");
   if(!new RegExp(`^${uuid}$`).test(revision)||!["true","false"].includes(visibility??""))return reply({error:"invalid_photo"},400);
   let options:Record<string,unknown>;
   try {options=JSON.parse(request.headers.get("X-Photo-Options")??"");if(Object.keys(options).sort().join(",")!=="distance,pace,route,time"||Object.values(options).some(x=>typeof x!=="boolean"))throw Error();}catch{return reply({error:"invalid_photo"},400);}
   const reader=request.body?.getReader();if(!reader)return reply({error:"invalid_photo"},400);
   const parts:Uint8Array[]=[];let size=0;
   while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>1000000){await reader.cancel();return reply({error:"photo_too_large"},413);}parts.push(value);}
   const jpeg=new Uint8Array(size);let offset=0;for(const part of parts){jpeg.set(part,offset);offset+=part.length;}
   if(size<4||jpeg[0]!==255||jpeg[1]!==216||jpeg[size-2]!==255||jpeg[size-1]!==217)return reply({error:"invalid_photo"},400);
   expected={revision,jpeg,options:JSON.stringify(options),public:visibility==="true"};
   const prior=await read();
   if(prior?.revision===revision) {
    if(prior.options!==JSON.stringify(options)||Boolean(prior.public_token)!==(visibility==="true") || !equalBytes(new Uint8Array(prior.jpeg),jpeg))return reply({error:"revision_conflict"},409);
   } else {
    const token=visibility==="true"?Array.from(crypto.getRandomValues(new Uint8Array(32)),x=>x.toString(16).padStart(2,"0")).join(""):null;
    if(!await sessionAccount(request,env))return reply({error:"unauthorized"},401);
    await env.DB.prepare(`INSERT INTO run_photos(owner_id,run_id,revision,jpeg,options,public_token,updated_at)
      SELECT ?,?,?,?,?,?,? WHERE EXISTS(SELECT 1 FROM run_uploads WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL)
      AND NOT EXISTS(SELECT 1 FROM run_deletions WHERE owner_id=? AND run_id=?)
      ON CONFLICT(owner_id,run_id) DO UPDATE SET revision=excluded.revision,jpeg=excluded.jpeg,options=excluded.options,public_token=excluded.public_token,updated_at=excluded.updated_at WHERE run_photos.revision<>excluded.revision`)
      .bind(owner,id,revision,jpeg,JSON.stringify(options),token,Date.now(),owner,id,owner,id).run();
   }
  } else if(request.method!=="GET")return reply({error:"method_not_allowed"},405);
  const row=await read();if(!await exists())return reply({error:"not_found"},404);
  if(expected && (!row || row.revision!==expected.revision || row.options!==expected.options || Boolean(row.public_token)!==expected.public || !equalBytes(new Uint8Array(row.jpeg),expected.jpeg)))return reply({error:"revision_conflict"},409);
  if(match[2])return row?image(row):reply({error:"not_found"},404);
  return reply({photo:row?{revision:row.revision,options:JSON.parse(row.options),bytes:new Uint8Array(row.jpeg).length,sha256:await digest(row),updatedAt:row.updated_at,publicUrl:row.public_token?`https://wayirun-dev.unopenedparachute.workers.dev/p/${row.public_token}`:null}:null});
 }catch{return reply({error:"photo_unavailable"},503);}
}
function equalBytes(a:Uint8Array,b:Uint8Array){return a.length===b.length&&a.every((v,i)=>v===b[i]);}
function pick(value:Record<string,unknown>,keys:string[]){return Object.fromEntries(keys.filter(k=>k in value).map(k=>[k,value[k]]));}
export async function handlePublicPhoto(request:Request,env:AuthEnv):Promise<Response>{
 try {
  const url=new URL(request.url),match=/^\/p\/([0-9a-f]{64})(\/(image|data))?$/.exec(url.pathname);
  if(!match||url.search||request.method!=="GET")return reply({error:"not_found"},404);
  const denied=await accessGuard(request,env,true);if(denied)return denied;
  const row=await env.DB.prepare(`SELECT p.* FROM run_photos p JOIN run_uploads r ON r.owner_id=p.owner_id AND r.run_id=p.run_id
    WHERE p.public_token=? AND r.completed_at IS NOT NULL AND NOT EXISTS(SELECT 1 FROM run_deletions d WHERE d.owner_id=p.owner_id AND d.run_id=p.run_id)`).bind(match[1]).first<Photo>();
  if(!row)return reply({error:"not_found"},404);
  if(match[3]==="image")return image(row);
  if(match[3]==="data") {
   const archive=await readVerifiedRun(env.DB,row.owner_id,row.run_id);
   const run=archive.run as Record<string,unknown>,checkpoint=run.checkpoint as Record<string,unknown>,s=checkpoint.snapshot as Record<string,unknown>;
   const settings=s.settings as Record<string,unknown>;
   const route=(archive.route as Record<string,unknown>[]).map(p=>pick(p,["id","segmentId","monotonicMs","latitude","longitude","accuracyMeters"]));
   const splits=(archive.splits as Record<string,unknown>[]).map(p=>pick(p,["number","meters","durationMs","partial"]));
   const segments=(s.segments as Record<string,unknown>[]).map(p=>pick(p,["id","source","startedMonotonicMs","endedMonotonicMs"]));
   if(!await env.DB.prepare("SELECT 1 FROM run_photos WHERE public_token=?").bind(match[1]).first())return reply({error:"not_found"},404);
   return reply({run:pick(s,["startedUtcMs","endedUtcMs","activeDurationMs","distanceMeters","averagePaceMsPerUnit"]),
    settings:{...pick(settings,["mode","units","strideLengthMeters","countdownSeconds"]),goal:settings.goal && typeof settings.goal==="object"?pick(settings.goal as Record<string,unknown>,["type","meters","durationMs"]):null},route,splits,segments});
  }
  return new Response(PUBLIC_PHOTO_PAGE,{headers:{...headers,"Content-Type":"text/html;charset=utf-8","Content-Security-Policy":"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https://tile.openstreetmap.org; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; object-src 'none'"}});
 }catch{return reply({error:"public_run_unavailable"},503);}
}

export const PUBLIC_PHOTO_PAGE=`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>WAYiRUN · Shared run</title><link rel="stylesheet" href="/style.css"><link rel="stylesheet" href="/leaflet.css"><main style="max-width:1000px;margin:auto;padding:24px"><h1>WAYiRUN</h1><p>A shared run</p><img id="photo" alt="Run photo" style="max-width:100%;max-height:85vh"><div id="stats"></div><p id="location"></p><button id="fit">Fit route</button><div id="map" style="height:400px"></div><p id="map-status"></p><h2>Splits</h2><div id="splits"></div><h2>Run settings</h2><pre id="settings"></pre><p id="error" role="status"></p></main><script type="module" src="/public-photo.js"></script></html>`;

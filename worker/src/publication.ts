import {accessGuard,sessionAccount,reply,smallJson,type AuthEnv} from "./auth.js";
import {environmentOrigins} from "./environment.js";

const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
export type Publication = {owner_id:string;run_id:string;public_token:string;short_token:string|null;shared:number;photo_visible:number;revision:number};
const token = (bytes:number) => Array.from(crypto.getRandomValues(new Uint8Array(bytes)),v=>v.toString(16).padStart(2,"0")).join("");
const available = `EXISTS(SELECT 1 FROM run_uploads r WHERE r.owner_id=public_runs.owner_id AND r.run_id=public_runs.run_id AND r.completed_at IS NOT NULL)
 AND NOT EXISTS(SELECT 1 FROM run_deletions d WHERE d.owner_id=public_runs.owner_id AND d.run_id=public_runs.run_id)`;

export async function readPublication(env:AuthEnv,owner:string,id:string) {
 return env.DB.prepare(`SELECT * FROM public_runs WHERE owner_id=? AND run_id=? AND ${available}`).bind(owner,id).first<Publication>();
}
export function publicationUrl(row:Publication|null,origin:string=environmentOrigins.development) {
 return row?.shared ? `${origin}/${row.short_token ? "r/"+row.short_token : "p/"+row.public_token}` : null;
}
function view(row:Publication,origin:string) {
 return {shared:!!row.shared,photoVisible:!!row.photo_visible,revision:row.revision,publicUrl:publicationUrl(row,origin)};
}
async function ensure(env:AuthEnv,owner:string,id:string) {
 for(let attempt=0;attempt<5;attempt++) {
  const current=await readPublication(env,owner,id);
  if(current?.short_token)return current;
  try {
   if(current) {
    await env.DB.prepare(`UPDATE public_runs SET short_token=? WHERE owner_id=? AND run_id=? AND short_token IS NULL AND ${available}`)
      .bind(token(16),owner,id).run();
   } else {
    await env.DB.prepare(`INSERT INTO public_runs(owner_id,run_id,public_token,short_token)
      SELECT ?,?,?,? WHERE EXISTS(SELECT 1 FROM run_uploads WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL)
      AND NOT EXISTS(SELECT 1 FROM run_deletions WHERE owner_id=? AND run_id=?) ON CONFLICT(owner_id,run_id) DO NOTHING`)
      .bind(owner,id,token(32),token(16),owner,id,owner,id).run();
   }
  } catch(error) {
   // Only a token uniqueness collision is retryable; do not hide database failures.
   if(!String(error).includes("UNIQUE constraint failed: public_runs."))throw error;
   continue;
  }
  const row=await readPublication(env,owner,id);
  if(!row)return null;
  if(row.short_token)return row;
 }
 throw Error("publication_token_unavailable");
}

export async function handlePublication(request:Request,env:AuthEnv):Promise<Response> {
 try {
  const url=new URL(request.url),id=url.pathname.replace(/^\/api\/publications\//,"");
  if(!url.pathname.startsWith("/api/publications/")||!uuid.test(id)||url.search)return reply({error:"not_found"},404);
  if(request.headers.has("Origin")||request.headers.has("Cookie"))return reply({error:"origin_not_allowed"},403);
  const denied=await accessGuard(request,env,true);if(denied)return denied;
  const session=await sessionAccount(request,env);if(!session)return reply({error:"unauthorized"},401);
  if(!["GET","PUT"].includes(request.method))return reply({error:"method_not_allowed"},405);
  let input:Record<string,unknown>|null=null;
  if(request.method==="PUT") {
   try {input=await smallJson(request);}catch{return reply({error:"invalid_request"},400);}
   const keys=input.action==="photo"?"action,expectedRevision,operationId,photoVisible":"action,expectedRevision,operationId";
   if(Object.keys(input).sort().join(",")!==keys || !uuid.test(String(input.operationId)) ||
      !Number.isSafeInteger(input.expectedRevision)||Number(input.expectedRevision)<0 ||
      !["share","unshare","photo"].includes(String(input.action)) ||
      (input.action==="photo" && typeof input.photoVisible!=="boolean"))return reply({error:"invalid_request"},400);
  }
  const owner=session.account.id,row=await ensure(env,owner,id);
  if(!row)return reply({error:"not_found"},404);
  if(input) {
   const body=JSON.stringify({action:input.action,expectedRevision:input.expectedRevision,
    ...(input.action==="photo"?{photoVisible:input.photoVisible}:{})});
   const receipt=()=>env.DB.prepare("SELECT request_json FROM publication_operations WHERE owner_id=? AND run_id=? AND operation_id=?")
    .bind(owner,id,input!.operationId).first<{request_json:string}>();
   const prior=await receipt();
   if(prior && prior.request_json!==body)return reply({error:"operation_conflict"},409);
   if(!prior) {
    if(!await sessionAccount(request,env))return reply({error:"unauthorized"},401);
    // Batch serialization makes the CAS and its receipt atomic, including concurrent retries.
    const column=input.action==="photo"?"photo_visible":"shared";
    const value=input.action==="photo"?Number(input.photoVisible):Number(input.action==="share");
    await env.DB.batch([
     env.DB.prepare(`UPDATE public_runs SET ${column}=?,revision=revision+1,last_operation=?
      WHERE owner_id=? AND run_id=? AND revision=? AND ${available}
      AND NOT EXISTS(SELECT 1 FROM publication_operations WHERE owner_id=? AND run_id=? AND operation_id=?)`)
      .bind(value,input.operationId,owner,id,input.expectedRevision,owner,id,input.operationId),
     env.DB.prepare(`INSERT INTO publication_operations(owner_id,run_id,operation_id,request_json)
      SELECT owner_id,run_id,?,? FROM public_runs WHERE owner_id=? AND run_id=? AND last_operation=? AND revision=? AND ${available}
      ON CONFLICT(owner_id,run_id,operation_id) DO NOTHING`)
      .bind(input.operationId,body,owner,id,input.operationId,Number(input.expectedRevision)+1),
    ]);
    const applied=await receipt();
    const latest=await readPublication(env,owner,id);
    if(!latest)return reply({error:"not_found"},404);
    if(!applied || applied.request_json!==body)return reply({error:"revision_conflict",publication:view(latest,env.PUBLIC_ORIGIN??environmentOrigins.development)},409);
   }
  }
  const latest=await readPublication(env,owner,id);
  if(!latest)return reply({error:"not_found"},404);
  if(!await sessionAccount(request,env))return reply({error:"unauthorized"},401);
  return reply({publication:view(latest,env.PUBLIC_ORIGIN??environmentOrigins.development)});
 }catch{return reply({error:"publication_unavailable"},503);}
}

export async function publicAccess(env:AuthEnv,value:string,short=false) {
 return env.DB.prepare(`SELECT * FROM public_runs WHERE ${short?"short_token":"public_token"}=? AND shared=1 AND ${available}`)
  .bind(value).first<Publication>();
}
export async function handleShortRun(request:Request,env:AuthEnv) {
 const url=new URL(request.url),match=/^\/r\/([0-9a-f]{32})$/.exec(url.pathname);
 if(!match||url.search||request.method!=="GET")return reply({error:"not_found"},404);
 try {
  const denied=await accessGuard(request,env,true);if(denied)return denied;
  const row=await publicAccess(env,match[1]!,true);
  if(!row)return reply({error:"not_found"},404);
  return new Response(null,{status:302,headers:{Location:`/p/${row.public_token}`,"Cache-Control":"no-store","Referrer-Policy":"no-referrer"}});
 }catch{return reply({error:"public_run_unavailable"},503);}
}

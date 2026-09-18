import { accessGuard, reply, sessionAccount, smallJson, type AuthEnv } from "./auth.js";

export interface KeyEnv extends AuthEnv { COACHING_KEYRING?: string }
type Ring = { active: string; keys: Record<string,string> };
type Sealed = { ciphertext: string; nonce: string; key_version: string };
type Stored = Sealed & { revision: string; action: string; checked_at: number | null };
const uuid=/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const encode=(bytes: Uint8Array)=>btoa(String.fromCharCode(...bytes));
const decode=(text: string)=>Uint8Array.from(atob(text),c=>c.charCodeAt(0));
const utf8=new TextEncoder();
function ring(env: KeyEnv): Ring {
  const value=JSON.parse(env.COACHING_KEYRING ?? "null") as Ring;
  if(!value || !/^[a-zA-Z0-9_-]{1,32}$/.test(value.active) || !value.keys ||
    !Object.hasOwn(value.keys,value.active) || Object.keys(value.keys).length>4)throw new Error("keyring");
  for(const [version,key] of Object.entries(value.keys))if(!/^[a-zA-Z0-9_-]{1,32}$/.test(version)||typeof key!=="string"||decode(key).length!==32)throw new Error("keyring");
  return value;
}
const aad=(owner:string,version:string)=>utf8.encode(JSON.stringify(["wayirun-openai-key",1,owner,version]));
export async function sealKey(env:KeyEnv,owner:string,plaintext:string):Promise<Sealed> {
  const r=ring(env),nonce=crypto.getRandomValues(new Uint8Array(12));
  const key=await crypto.subtle.importKey("raw",decode(r.keys[r.active]!),"AES-GCM",false,["encrypt"]);
  const encrypted=await crypto.subtle.encrypt({name:"AES-GCM",iv:nonce,additionalData:aad(owner,r.active)},key,utf8.encode(plaintext));
  return {ciphertext:encode(new Uint8Array(encrypted)),nonce:encode(nonce),key_version:r.active};
}
// Internal only. No HTTP route ever exposes plaintext or the encryption envelope.
export async function unsealKey(env:KeyEnv,owner:string,value:Sealed):Promise<string> {
  const r=ring(env);if(!Object.hasOwn(r.keys,value.key_version))throw new Error("key_version");
  const key=await crypto.subtle.importKey("raw",decode(r.keys[value.key_version]!),"AES-GCM",false,["decrypt"]);
  const bytes=await crypto.subtle.decrypt({name:"AES-GCM",iv:decode(value.nonce),additionalData:aad(owner,value.key_version)},key,decode(value.ciphertext));
  return new TextDecoder("utf-8",{fatal:true,ignoreBOM:false}).decode(bytes);
}
function metadata(env:KeyEnv,row:Stored|null) {
  let available=false;
  try{const r=ring(env);available=!row?.ciphertext||Object.hasOwn(r.keys,row.key_version);}catch{}
  return {configured:!!row?.ciphertext,revision:row?.revision??"none",available,
    validation:row?.ciphertext?"credentials_checked":"not_configured",checkedAt:row?.checked_at??null};
}
async function providerCheck(key:string):Promise<Response|null> {
  let response:Response;
  try{response=await fetch("https://api.openai.com/v1/models",{headers:{Authorization:`Bearer ${key}`},redirect:"manual",signal:AbortSignal.timeout(10000)});}
  catch{return reply({error:"key_check_unavailable"},503);}
  if(response.status===429){
    let quota=false;
    const reader=response.body?.getReader();let text="",size=0;
    try{if(reader)while(true){const {done,value}=await reader.read();if(done)break;size+=value.length;if(size>8192)break;text+=new TextDecoder().decode(value);}
      quota=JSON.parse(text)?.error?.code==="insufficient_quota";
    }catch{}finally{await reader?.cancel();reader?.releaseLock();}
    return reply({error:quota?"key_quota_exceeded":"key_rate_limited"},429);
  }
  await response.body?.cancel();
  if(response.status===200)return null;
  return reply({error:response.status===401?"key_invalid":response.status===403?"key_permission_denied":"key_check_unavailable"},response.status===401||response.status===403?400:503);
}
export async function handleAccountKey(request:Request,env:KeyEnv):Promise<Response> {
  try {
    if(request.headers.has("Origin")||request.headers.has("Cookie"))return reply({error:"origin_not_allowed"},403);
    const url=new URL(request.url);
    if(url.search)return reply({error:"invalid_request"},400);
    if(!["GET","PUT","DELETE"].includes(request.method))return reply({error:"method_not_allowed"},405);
    const denied=await accessGuard(request,env);if(denied)return denied;
    const session=await sessionAccount(request,env);if(!session)return reply({error:"unauthorized"},401);
    const owner=session.account.id;
    const read=()=>env.DB.prepare("SELECT revision,action,ciphertext,nonce,key_version,checked_at FROM openai_keys WHERE owner_id=?").bind(owner).first<Stored>();
    const before=await read();
    if(request.method==="GET")return reply(metadata(env,before));
    let body:Record<string,unknown>;
    try{body=await smallJson(request);}catch{return reply({error:"invalid_request"},400);}
    const {revision,operationId,key}=body;
    if(typeof revision!=="string"||(revision!=="none"&&!uuid.test(revision))||typeof operationId!=="string"||!uuid.test(operationId)||
      Object.keys(body).sort().join(",")!==(request.method==="PUT"?"key,operationId,revision":"operationId,revision"))return reply({error:"invalid_request"},400);
    if(before?.revision===operationId)return before.action===request.method?reply(metadata(env,before)):reply({error:"key_changed"},409);
    if((before?.revision??"none")!==revision)return reply({error:"key_changed"},409);
    let sealed:Sealed|null=null;
    if(request.method==="PUT"){
      if(typeof key!=="string"||!/^sk-[A-Za-z0-9_-]{16,509}$/.test(key))return reply({error:"key_format"},400);
      try{sealed=await sealKey(env,owner,key);}catch{return reply({error:"key_storage_unavailable"},503);}
      const failed=await providerCheck(key);if(failed)return failed;
    }
    const now=Math.floor(Date.now()/1000);
    const validSession="EXISTS (SELECT 1 FROM auth_sessions WHERE token_hash=? AND expires_at>? AND revoked_at IS NULL)";
    const result=await env.DB.prepare(`INSERT INTO openai_keys (owner_id,revision,action,ciphertext,nonce,key_version,checked_at,updated_at)
      SELECT ?,?,?,?,?,?,?,? WHERE (?='none' OR EXISTS (SELECT 1 FROM openai_keys WHERE owner_id=? AND revision=?)) AND ${validSession}
      ON CONFLICT(owner_id) DO UPDATE SET revision=excluded.revision,action=excluded.action,ciphertext=excluded.ciphertext,
      nonce=excluded.nonce,key_version=excluded.key_version,checked_at=excluded.checked_at,updated_at=excluded.updated_at
      WHERE openai_keys.revision=? AND ${validSession}`)
      .bind(owner,operationId,request.method,sealed?.ciphertext??null,sealed?.nonce??null,sealed?.key_version??null,sealed?now:null,now,
        revision,owner,revision,session.tokenHash,now,revision,session.tokenHash,now).run();
    if(result.meta.changes!==1){
      const current=await read();
      if(current?.revision===operationId&&current.action===request.method)return reply(metadata(env,current));
      return reply({error:"key_changed"},409);
    }
    return reply(metadata(env,await read()));
  } catch { return reply({error:"key_storage_unavailable"},503); }
}

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
 for(const name of ["0001_bootstrap.sql","0002_accounts.sql","0003_run_uploads.sql","0004_run_deletions.sql","0005_openai_keys.sql","0006_coaching_jobs.sql"]){
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
test("durable job is owner scoped, single attempt, with chunked audio and current-run deletion",async t=>{
 const {db,add,call,calls}=await runtime(t),id=await add();
 const result=await call(id);assert.equal(result.status,200);assert.equal((await result.json()).state,"ready");
 assert.deepEqual(calls.map(x=>x[0]),["count","text","speech"]);assert.ok(calls.every(x=>x[1].startsWith("sk-alice-")));
 assert.equal((await (await call(id)).json()).state,"ready");assert.equal(calls.length,3);
 assert.equal((await call(id,{owner:"bob",method:"GET"})).status,404);
 const audio=await call(id,{method:"GET",audio:true});assert.equal(audio.headers.get("Content-Type"),"audio/wav");assert.equal((await audio.arrayBuffer()).byteLength,150000);
 await db.prepare("DELETE FROM run_uploads WHERE owner_id='alice' AND run_id=?").bind(id).run();
 assert.equal((await db.prepare("SELECT count(*) AS n FROM coaching_audio").first()).n,0);assert.equal((await call(id,{method:"GET"})).status,404);
});

test("suspicious run still completes the existing coaching job with identical classified input",async t=>{
 const {add,call,provider}=await runtime(t),id=await add(),inputs=[];
 provider.countInput=async (key,input)=>{inputs.push(input);return 100;};
 provider.text=async (key,input)=>{inputs.push(input);return "This may have been a test recording.";};
 assert.equal((await (await call(id)).json()).state,"ready");
 assert.equal(inputs.length,2);assert.equal(inputs[0],inputs[1]);
 assert.equal(JSON.parse(inputs[0]).current.quality.label,"likely_test");
});
test("simultaneous POSTs reserve once, repeated operation IDs cannot create a second run job",async t=>{
 const {add,call,calls,provider}=await runtime(t),id=await add();let entered,release;
 const ready=new Promise(r=>entered=r),held=new Promise(r=>release=r),original=provider.text;
 provider.text=async key=>{entered();await held;return original(key);};
 const operationId=randomUUID(),first=call(id,{body:{operationId}});await ready;
 assert.equal((await (await call(id)).json()).state,"text_pending");release();assert.equal((await (await first).json()).state,"ready");
 const second=await add("alice",2000);assert.equal((await call(second,{body:{operationId}})).status,409);assert.equal(calls.length,3);
});
test("previous-run deletion erases dependent audio but preserves a receipt preventing regeneration",async t=>{
 const {db,add,call,calls}=await runtime(t),previous=await add(),current=await add("alice",2000);await call(current);
 await db.prepare("DELETE FROM run_uploads WHERE owner_id='alice' AND run_id=?").bind(previous).run();
 const result=await (await call(current)).json();assert.equal(result.state,"failed");assert.equal(result.error,"context_deleted");assert.equal(result.message,null);
 assert.equal(calls.length,3);assert.equal((await db.prepare("SELECT count(*) AS n FROM coaching_audio").first()).n,0);
});
test("key removal and logout during text prevent speech and late ready results",async t=>{
 for(const logout of [false,true]){
  const {db,add,call,provider,calls}=await runtime(t),id=await add();
  provider.text=async()=>{await db.prepare(logout?"UPDATE auth_sessions SET revoked_at=1 WHERE owner_id='alice'":"UPDATE openai_keys SET ciphertext=NULL,nonce=NULL,key_version=NULL,checked_at=NULL,revision='removed' WHERE owner_id='alice'").run();return "Done.";};
  const result=await call(id);assert.equal(result.status,logout?401:200);assert.ok(!calls.some(x=>x[0]==="speech"));
  assert.notEqual((await db.prepare("SELECT state FROM coaching_jobs WHERE run_id=?").bind(id).first()).state,"ready");
 }
});
test("unknown paid failures and abandoned jobs cannot replay paid requests",async t=>{
 const {db,add,call,provider,calls}=await runtime(t),id=await add();
 provider.text=async()=>{throw new CoachingProviderError("provider_unavailable","text",true);};
 assert.equal((await (await call(id)).json()).state,"unknown");await call(id);assert.equal(calls.length,1);
 await db.prepare("UPDATE coaching_jobs SET state='speech_pending',created_at=1 WHERE run_id=?").bind(id).run();
 const result=await (await call(id,{method:"GET"})).json();assert.equal(result.state,"unknown");assert.equal(result.error,"interrupted");
 await call(id);assert.equal(calls.length,1);
});
test("native coaching boundary rejects unauthorized, origin, cookie and owner injection",async t=>{
 const {add,call,calls}=await runtime(t),id=await add();
 assert.equal((await call(id,{owner:null})).status,401);
 for(const headers of [{Origin:"https://test"},{Cookie:"x=y"}])assert.equal((await call(id,{headers})).status,403);
 assert.equal((await call(id,{body:{operationId:randomUUID(),owner:"bob"}})).status,400);
 assert.equal((await call(id,{method:"DELETE"})).status,405);assert.equal(calls.length,0);
});

test("coaching history reconstructs every audio byte without exposing credentials or generating again",async t=>{
 const {env,tokens,add,call,calls,db}=await runtime(t),id=await add();await call(id);
 const history=owner=>handleCoachingHistory(new Request(`https://test/api/coaching-history/${id}`,{headers:{Authorization:`Bearer ${tokens[owner]}`}}),env);
 const response=await history("alice");assert.equal(response.status,200);const result=await response.json(),c=result.coaching;
 const bytes=Buffer.concat(c.audio.chunks.map((chunk,index)=>{assert.equal(chunk.index,index);const b=Buffer.from(chunk.base64,"base64");assert.equal(b.length,chunk.bytes);assert.equal(sha(b),chunk.sha256);return b;}));
 assert.equal(bytes.length,150000);assert.equal(sha(bytes),c.audio.sha256);assert.deepEqual(bytes,Buffer.alloc(150000,7));
 const serialized=JSON.stringify(result);for(const secret of [tokens.alice,"token_hash","key_revision","ciphertext","nonce","sk-alice-"])assert.ok(!serialized.includes(secret));
 assert.equal((await history("bob")).status,404);assert.equal(calls.length,3);
 await db.prepare("DELETE FROM coaching_audio WHERE run_id=? AND chunk_index=1").bind(id).run();assert.equal((await history("alice")).status,503);
});
test("history distinguishes an existing uncoached run from deleted or unauthorized runs",async t=>{
 const {env,tokens,add,db}=await runtime(t),id=await add();
 const request=()=>new Request(`https://test/api/coaching-history/${id}`,{headers:{Authorization:`Bearer ${tokens.alice}`}});
 assert.deepEqual(await (await handleCoachingHistory(request(),env)).json(),{runId:id,coaching:null});
 await db.prepare("DELETE FROM run_uploads WHERE run_id=?").bind(id).run();assert.equal((await handleCoachingHistory(request(),env)).status,404);
});

import {test} from "node:test";
import assert from "node:assert/strict";
import {randomBytes,randomUUID,createHash} from "node:crypto";
import {readFileSync} from "node:fs";
import {fileURLToPath} from "node:url";
import {Miniflare,convertV4MiniflareOptions} from "miniflare";
import {workerModules} from "./worker-modules.mjs";
import {sealKey,unsealKey} from "../build/account-key.js";
const keyring=JSON.stringify({active:"v1",keys:{v1:randomBytes(32).toString("base64")}});
const sample="sk-test-"+"x".repeat(50), replacement="sk-test-"+"y".repeat(50);
async function runtime(t,options={}) {
 let checks=0,provider=async()=>Response.json({data:[]});
 const mf=new Miniflare(convertV4MiniflareOptions({name:"keys-test",modules:workerModules(),
   scriptPath:fileURLToPath(new URL("../build/deploy/index.js",import.meta.url)),compatibilityDate:"2026-02-17",
   bindings:{APP_ENV:"development",PUBLIC_ORIGIN:"https://wayirun-dev.unopenedparachute.workers.dev",WRITE_MODE:"normal",GOOGLE_WEB_CLIENT_ID:"test.apps.googleusercontent.com",GOOGLE_ANDROID_CLIENT_ID:"test-android.apps.googleusercontent.com",LOCATION_LOOKUP_URL:"https://nominatim.openstreetmap.org/reverse",WEATHER_FORECAST_URL:"https://api.open-meteo.com/v1/forecast",WEATHER_ARCHIVE_URL:"https://archive-api.open-meteo.com/v1/archive",...(options.noSecret?{}:{COACHING_KEYRING:options.secret??keyring})},
   ratelimits:{AUTH_RATE_LIMIT:{namespace_id:"1",simple:{limit:1000,period:60}},AUTH_TOTAL_LIMIT:{namespace_id:"2",simple:{limit:2000,period:60}}},
   d1Databases:["DB"],outboundService:async request=>{
     checks++;assert.equal(request.url,"https://api.openai.com/v1/models");assert.equal(request.method,"GET");
     assert.ok([`Bearer ${sample}`,`Bearer ${replacement}`].includes(request.headers.get("Authorization")));
     return provider(request);
   }}));
 t.after(()=>mf.dispose());const db=await mf.getD1Database("DB");
 for(const name of ["0001_bootstrap.sql","0002_accounts.sql","0005_openai_keys.sql"]){const sql=readFileSync(new URL(`../migrations/${name}`,import.meta.url),"utf8");await db.batch(sql.replace(/^--.*$/gm,"").split(";").map(x=>x.trim()).filter(Boolean).map(x=>db.prepare(x)));}
 const now=Math.floor(Date.now()/1000),tokens={};
 for(const owner of ["alice","bob"]){tokens[owner]=randomBytes(32).toString("hex");await db.prepare("INSERT INTO accounts VALUES (?,?,NULL,NULL,?,?)").bind(owner,owner,now,now).run();await db.prepare("INSERT INTO auth_sessions (token_hash,owner_id,created_at,expires_at) VALUES (?,?,?,?)").bind(createHash("sha256").update(tokens[owner]).digest("hex"),owner,now,now+3600).run();}
 const call=(owner,method="GET",body,headers={},path="/api/account/openai-key")=>mf.dispatchFetch("https://test"+path,{method,headers:{...(owner?{Authorization:`Bearer ${tokens[owner]}`} : {}),...(body?{"Content-Type":"application/json"}:{}),...headers},...(body?{body:JSON.stringify(body)}:{})});
 return {db,call,setProvider:fn=>provider=fn,checks:()=>checks};
}
const put=(revision="none",key=sample)=>({revision,key,operationId:randomUUID()});
test("key encryption uses distinct nonces and authenticates owner, version and ciphertext",async()=>{
 const env={COACHING_KEYRING:keyring},a=await sealKey(env,"alice",sample),b=await sealKey(env,"alice",sample);
 assert.notEqual(a.nonce,b.nonce);assert.notEqual(a.ciphertext,b.ciphertext);assert.equal(await unsealKey(env,"alice",a),sample);
 await assert.rejects(unsealKey(env,"bob",a));await assert.rejects(unsealKey({COACHING_KEYRING:JSON.stringify({active:"v1",keys:{v1:randomBytes(32).toString("base64")}})},"alice",a));
 await assert.rejects(unsealKey(env,"alice",{...a,ciphertext:a.ciphertext.slice(4)}));await assert.rejects(unsealKey(env,"alice",{...a,key_version:"missing"}));
 const old=JSON.parse(keyring),rotated={COACHING_KEYRING:JSON.stringify({active:"v2",keys:{...old.keys,v2:randomBytes(32).toString("base64")}})};
 assert.equal(await unsealKey(rotated,"alice",a),sample);assert.equal((await sealKey(rotated,"alice",sample)).key_version,"v2");
});
test("keys stay owner isolated and encrypted; status never exposes secret material",async t=>{
 const {call,db,checks}=await runtime(t);assert.equal((await (await call("alice")).json()).configured,false);
 const request=put();const saved=await call("alice","PUT",request);assert.equal(saved.status,200,await saved.clone().text());const text=await saved.text();assert.ok(!text.includes(sample)&&!text.includes("ciphertext")&&!text.includes("nonce"));
 const status=JSON.parse(text);assert.equal(status.configured,true);assert.equal(status.revision,request.operationId);assert.equal(checks(),1);
 const row=await db.prepare("SELECT * FROM openai_keys WHERE owner_id='alice'").first();assert.ok(!JSON.stringify(row).includes(sample));assert.equal(await unsealKey({COACHING_KEYRING:keyring},"alice",row),sample);
 assert.equal((await (await call("bob")).json()).configured,false);
 const changed=await call("alice","PUT",put(status.revision,replacement));assert.equal(changed.status,200);
 const after=await changed.json();const removed=await call("alice","DELETE",{revision:after.revision,operationId:randomUUID()});assert.equal(removed.status,200);assert.equal((await removed.json()).configured,false);
 const empty=await db.prepare("SELECT * FROM openai_keys WHERE owner_id='alice'").first();for(const field of ["ciphertext","nonce","key_version","checked_at"])assert.equal(empty[field],null);
});
test("missing or malformed master secret fails before checking or storing a key",async t=>{
 for(const options of [{noSecret:true},{secret:"invalid"},{secret:JSON.stringify({active:"v1",keys:{v1:"short"}})}]){
   const {call,db,checks}=await runtime(t,options);assert.equal((await (await call("alice")).json()).available,false);
   assert.equal((await call("alice","PUT",put())).status,503);assert.equal(checks(),0);assert.equal((await db.prepare("SELECT count(*) AS n FROM openai_keys").first()).n,0);
 }
});
test("provider invalid, permission, quota and unavailable states preserve saved key",async t=>{
 const {call,db,setProvider}=await runtime(t);const saved=await (await call("alice","PUT",put())).json();
 for(const [status,code,expected] of [[401,"x","key_invalid"],[403,"x","key_permission_denied"],[429,"insufficient_quota","key_quota_exceeded"],[429,"rate_limit_exceeded","key_rate_limited"],[503,"x","key_check_unavailable"]]){
   setProvider(async()=>Response.json({error:{code,message:sample}},{status}));const response=await call("alice","PUT",put(saved.revision,replacement));const body=await response.json();assert.equal(body.error,expected);assert.ok(!JSON.stringify(body).includes(sample));
   assert.equal((await db.prepare("SELECT revision FROM openai_keys WHERE owner_id='alice'").first()).revision,saved.revision);
 }
});
test("native boundary rejects unauthenticated, browser, malformed and owner-injected changes",async t=>{
 const {call,checks}=await runtime(t);assert.equal((await call(null)).status,401);
 for(const headers of [{Origin:"https://test"},{Cookie:"session=x"}])assert.equal((await call("alice","PUT",put(),headers)).status,403);
 for(const body of [{...put(),ownerId:"bob"},put("none","bad"),{...put(),operationId:"bad"}])assert.equal((await call("alice","PUT",body)).status,400);
 assert.equal((await call("alice","POST",put())).status,405);assert.equal((await call("alice","GET",undefined,{},"/api/account/openai-key?owner=bob")).status,400);
 assert.equal((await call("alice","PUT",{...put(),key:"x".repeat(17000)})).status,400);assert.equal(checks(),0);
});
test("idempotent operation receipts and revisions prevent stale saves from reviving removed keys",async t=>{
 const {call,checks}=await runtime(t);const first=put();await call("alice","PUT",first);assert.equal((await call("alice","PUT",first)).status,200);assert.equal(checks(),1);
 const deletion={revision:first.operationId,operationId:randomUUID()};assert.equal((await call("alice","DELETE",deletion)).status,200);assert.equal((await call("alice","DELETE",deletion)).status,200);
 assert.equal((await call("alice","PUT",first)).status,409);assert.equal((await call("alice","PUT",put(first.operationId))).status,409);assert.equal((await (await call("alice")).json()).configured,false);
});
test("deletion or logout during key validation prevents a late save",async t=>{
 const {call,db,setProvider}=await runtime(t);const first=await (await call("alice","PUT",put())).json();
 for(const logout of [false,true]){
   const baseline=await (await call("alice")).json();let release,entered;
   const ready=new Promise(resolve=>entered=resolve),hold=new Promise(resolve=>release=resolve);
   setProvider(async()=>{entered();await hold;return Response.json({data:[]});});
   const pending=call("alice","PUT",put(baseline.revision,replacement));await ready;
   if(logout)await db.prepare("UPDATE auth_sessions SET revoked_at=1 WHERE owner_id='alice'").run();
   else assert.equal((await call("alice","DELETE",{revision:baseline.revision,operationId:randomUUID()})).status,200);
   release();assert.equal((await pending).status,409);
 }
 assert.equal((await db.prepare("SELECT ciphertext FROM openai_keys WHERE owner_id='alice'").first()).ciphertext,null);
});

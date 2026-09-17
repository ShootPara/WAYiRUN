import { workerModules } from "./worker-modules.mjs";
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { Miniflare, convertV4MiniflareOptions } from "miniflare";
import { SignJWT, generateKeyPair, exportJWK } from "jose";


const audience = "test-web.apps.googleusercontent.com";
const pair = await generateKeyPair("RS256", { extractable: true });
const jwk = { ...await exportJWK(pair.publicKey), kid: "test-key", alg: "RS256", use: "sig" };
async function runtime(t, configured = true, rateLimit = 1000) {
  const mf = new Miniflare(convertV4MiniflareOptions({
    name: "auth-test", modules: workerModules(),
    scriptPath: fileURLToPath(new URL("../build/deploy/index.js", import.meta.url)),
    compatibilityDate: "2026-02-17",
    bindings: { APP_ENV: "development", ...(configured ? { GOOGLE_WEB_CLIENT_ID: audience, GOOGLE_ANDROID_CLIENT_ID: "test-android.apps.googleusercontent.com" } : {}) },
    ratelimits: { RUN_RATE_LIMIT: { namespace_id: "3", simple: { limit: 300, period: 60 } }, RUN_TOTAL_LIMIT: { namespace_id: "4", simple: { limit: 3000, period: 60 } },
      AUTH_RATE_LIMIT: { namespace_id: "1", simple: { limit: rateLimit, period: 60 } },
      AUTH_TOTAL_LIMIT: { namespace_id: "2", simple: { limit: 2000, period: 60 } },
    },
    d1Databases: ["DB"],
    // Only the test harness intercepts Google. The shipped Worker has no key/identity override.
    outboundService: async request => {
      assert.equal(request.url, "https://www.googleapis.com/oauth2/v3/certs");
      return Response.json({ keys: [jwk] });
    },
  }));
  t.after(() => mf.dispose());
  const db = await mf.getD1Database("DB");
  for (const name of ["0001_bootstrap.sql", "0002_accounts.sql", "0003_run_uploads.sql"]) {
    const sql = readFileSync(new URL(`../migrations/${name}`, import.meta.url), "utf8");
    await db.batch(sql.replace(/^--.*$/gm, "").split(";").map(s => s.trim()).filter(Boolean).map(s => db.prepare(s)));
  }
  const deletion=readFileSync(new URL("../migrations/0004_run_deletions.sql",import.meta.url),"utf8");
  const split=deletion.indexOf("CREATE TRIGGER");
  await db.prepare(deletion.slice(0,split)).run();await db.prepare(deletion.slice(split)).run();
  return { mf, db };
}
function call(mf, path, method = "GET", body, headers = {}) {
  return mf.dispatchFetch(`https://wayirun-dev.unopenedparachute.workers.dev${path}`, { method, headers: { ...(body === undefined ? {} : { "Content-Type": "application/json" }), ...headers },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }) });
}
async function challenge(mf) { return (await (await call(mf, "/api/auth/challenge", "POST")).json()).nonce; }
async function token(nonce, subject = "google-alice", patch = {}, key = pair.privateKey, algorithm = "RS256") {
  const now = Math.floor(Date.now() / 1000);
  return new SignJWT({ iss: "https://accounts.google.com", aud: audience, sub: subject, iat: now, exp: now + 300,
    nonce, name: "Alice", picture: "https://example.com/profile.png", ...patch })
    .setProtectedHeader({ alg: algorithm, kid: "test-key" }).sign(key);
}
async function login(mf, subject, patch = {}) {
  const nonce = await challenge(mf);
  const response = await call(mf, "/api/auth/google", "POST", { nonce, idToken: await token(nonce, subject, patch) });
  assert.equal(response.status, 200);
  return (await response.json()).accessToken;
}
const bearer = value => ({ Authorization: `Bearer ${value}` });

test("map assets are served locally with correct types and only the tile image host allowed", async t => {
  const {mf}=await runtime(t);
  for(const path of ["/export.js","/route.js","/map.js","/leaflet.js","/leaflet.css"]){
    const response=await call(mf,path);assert.equal(response.status,200);
    assert.match(response.headers.get("Content-Type"),path.endsWith(".css")?/text\/css/:/text\/javascript/);
    const csp=response.headers.get("Content-Security-Policy");
    assert.match(csp,/img-src[^;]*https:\/\/tile.openstreetmap.org/);
    assert.ok(!csp.includes("unsafe-inline"));
    assert.ok((await response.text()).length>100);
    assert.equal((await call(mf,path,"POST")).status,405);
    assert.equal(await (await call(mf,path,"HEAD")).text(),"");
  }
});

test("browser shell supplies a fresh style nonce for Google's widget and an origin referrer", async t => {
  const { mf } = await runtime(t);
  const nonces = [];
  for (let i = 0; i < 2; i++) {
    const response = await call(mf, "/");
    const html = await response.text();
    const nonce = html.match(/nonce="([0-9a-f]{64})" src="https:\/\/accounts.google.com\/gsi\/client"/)?.[1];
    assert.ok(nonce);
    assert.ok(!html.includes("__CSP_NONCE__"));
    const csp = response.headers.get("Content-Security-Policy");
    assert.ok(csp.includes(`'nonce-${nonce}'`));
    assert.ok(!csp.includes("unsafe-inline"));
    assert.equal(response.headers.get("Referrer-Policy"), "strict-origin-when-cross-origin");
    nonces.push(nonce);
  }
  assert.notEqual(nonces[0], nonces[1]);
});


const origin = "https://wayirun-dev.unopenedparachute.workers.dev";
const browserHeaders = {Origin: origin, "X-WAYIRUN-Request":"1", "Sec-Fetch-Site":"same-origin"};
async function webLogin(mf, subject = "alice") {
 const start = await call(mf, "/web-api/challenge", "POST", undefined, browserHeaders);
 assert.equal(start.status,200); const nonce = (await start.json()).nonce;
 const cookie = start.headers.get("Set-Cookie").split(";")[0];
 const response = await call(mf,"/web-api/google","POST",{nonce,idToken:await token(nonce,subject)},{...browserHeaders,Cookie:cookie});
 assert.equal(response.status,200); assert.deepEqual(await response.json(),{signedIn:true});
 const cookies=response.headers.getSetCookie();
 const session=cookies.find(c=>c.startsWith("__Host-wayirun="));
 assert.match(session,/Max-Age=7776000/);
 assert.ok(session.includes("Secure")&&session.includes("HttpOnly")&&session.includes("SameSite=Strict")&&session.includes("Path=/"));
 return {Cookie:session.split(";")[0],"Sec-Fetch-Site":"same-origin"};
}
test("browser login hides tokens, protects cookies, isolates accounts, and revokes logout",async t=>{
 const {mf,db}=await runtime(t);
 const alice=await webLogin(mf,"alice"),bob=await webLogin(mf,"bob");
 const a=await (await call(mf,"/web-api/account","GET",undefined,alice)).json();
 const b=await (await call(mf,"/web-api/account","GET",undefined,bob)).json();assert.notEqual(a.account.id,b.account.id);
 const runId=crypto.randomUUID();
 const m={schemaVersion:1,runId,operationId:crypto.randomUUID(),summary:{state:"FINISHED",startedUtcMs:1,endedUtcMs:2,activeDurationMs:1,distanceMeters:3,mode:"INDOOR",units:"MILES"},chunks:[{sha256:"a".repeat(64),bytes:1}]};
 await db.prepare("INSERT INTO run_uploads VALUES (?, ?, ?, ?, ?, 1, 1, 100, 2)").bind(a.account.id,runId,m.operationId,JSON.stringify(m),"b".repeat(64)).run();
 assert.equal((await (await call(mf,"/web-api/runs","GET",undefined,alice)).json()).runs.length,1);
 assert.equal((await (await call(mf,"/web-api/runs","GET",undefined,bob)).json()).runs.length,0);
 assert.equal((await call(mf,`/web-api/runs/${runId}`,"GET",undefined,bob)).status,404);
 assert.equal((await call(mf,`/web-api/runs/${runId}`,"DELETE",undefined,alice)).status,403);
 assert.equal((await call(mf,"/api/account","GET",undefined,alice)).status,401);
 const logout=await call(mf,"/web-api/logout","POST",undefined,{...alice,...browserHeaders});assert.equal(logout.status,200);assert.match(logout.headers.get("Set-Cookie"),/Max-Age=0/);
 assert.equal((await call(mf,"/web-api/account","GET",undefined,alice)).status,401);
 assert.equal((await call(mf,"/web-api/account","GET",undefined,bob)).status,200);
});

test("real chunk downloads exceed thirty requests without consuming the sign-in budget",async t=>{
 const {mf,db}=await runtime(t,true,3);
 const session=await webLogin(mf);
 const account=await (await call(mf,"/web-api/account","GET",undefined,session)).json();
 const runId=crypto.randomUUID(),operationId=crypto.randomUUID();
 const manifest={schemaVersion:1,runId,operationId,summary:{state:"FINISHED",startedUtcMs:1,endedUtcMs:2,activeDurationMs:1,distanceMeters:3,mode:"OUTDOOR",units:"MILES"},chunks:Array.from({length:40},()=>({sha256:"a".repeat(64),bytes:1}))};
 await db.prepare("INSERT INTO run_uploads VALUES (?, ?, ?, ?, ?, 40, 1, 100, 2)").bind(account.account.id,runId,operationId,JSON.stringify(manifest),"b".repeat(64)).run();
 await db.batch(Array.from({length:40},(_,i)=>db.prepare("INSERT INTO run_chunks (owner_id,run_id,chunk_index,sha256,data) VALUES (?, ?, ?, ?, ?)").bind(account.account.id,runId,i,"a".repeat(64),new Uint8Array([i]))));
 for(let i=0;i<40;i++){
   const response=await call(mf,`/web-api/runs/${runId}/chunks/${i}`,"GET",undefined,session);
   assert.equal(response.status,200);assert.deepEqual([...new Uint8Array(await response.arrayBuffer())],[i]);
 }
 assert.equal((await call(mf,"/web-api/runs","GET",undefined,session)).status,200);
 assert.equal((await call(mf,"/web-api/challenge","POST",undefined,browserHeaders)).status,429);
});

test("browser renews a valid short session to ninety days but never revives expired or revoked sessions",async t=>{
 const {mf,db}=await runtime(t);const session=await webLogin(mf);
 const now=Math.floor(Date.now()/1000);
 await db.prepare("UPDATE auth_sessions SET expires_at = ?").bind(now+60).run();
 const response=await call(mf,"/web-api/account","GET",undefined,session);
 assert.equal(response.status,200);assert.match(response.headers.get("Set-Cookie"),/Max-Age=7776000/);
 const stored=await db.prepare("SELECT expires_at FROM auth_sessions").first();assert.ok(stored.expires_at>=now+7776000);
 await db.prepare("UPDATE auth_sessions SET revoked_at = ?").bind(now).run();
 assert.equal((await call(mf,"/web-api/account","GET",undefined,session)).status,401);
 await db.prepare("UPDATE auth_sessions SET revoked_at = NULL, created_at = 0, expires_at = 1").run();
 assert.equal((await call(mf,"/web-api/account","GET",undefined,session)).status,401);
});
test("browser boundary rejects foreign origins, missing CSRF header, bearer injection and unbound login",async t=>{
 const {mf}=await runtime(t);const session=await webLogin(mf);
 for(const headers of [{},{Origin:origin},{...browserHeaders,Origin:"https://evil.example"}])assert.equal((await call(mf,"/web-api/challenge","POST",undefined,headers)).status,403);
 for(const headers of [{...session,Origin:"https://evil.example"},{...session,"Sec-Fetch-Site":"cross-site"},{...session,Authorization:`Bearer ${"a".repeat(64)}`}])assert.equal((await call(mf,"/web-api/account","GET",undefined,headers)).status,403);
 const nonce=await challenge(mf);
 assert.equal((await call(mf,"/web-api/google","POST",{nonce,idToken:await token(nonce)},browserHeaders)).status,401);
 assert.equal((await call(mf,"/api/auth/challenge","POST",undefined,browserHeaders)).status,403);
});
test("browser challenge replay, expired session and static response policies fail safely",async t=>{
 const {mf,db}=await runtime(t);const session=await webLogin(mf);
 await db.prepare("UPDATE auth_sessions SET created_at=0, expires_at=1").run();assert.equal((await call(mf,"/web-api/runs","GET",undefined,session)).status,401);
 const start=await call(mf,"/web-api/challenge","POST",undefined,browserHeaders),nonce=(await start.json()).nonce;
 const headers={...browserHeaders,Cookie:start.headers.get("Set-Cookie").split(";")[0]};const body={nonce,idToken:await token(nonce)};
 assert.equal((await call(mf,"/web-api/google","POST",body,headers)).status,200);assert.equal((await call(mf,"/web-api/google","POST",body,headers)).status,401);
 for(const path of ["/","/app.js","/style.css"]){const response=await call(mf,path);assert.equal(response.status,200);assert.equal(response.headers.get("Cache-Control"),"no-store");assert.match(response.headers.get("Content-Security-Policy"),/frame-ancestors 'none'/);assert.equal(response.headers.get("Cross-Origin-Opener-Policy"),"same-origin-allow-popups");assert.ok((await response.text()).length>100);}
});

test("browser deletion requires exact route, empty body and CSRF; removes only owner data and retries safely",async t=>{
 const {mf,db}=await runtime(t);const alice=await webLogin(mf,"alice"),bob=await webLogin(mf,"bob");
 const owner=(await (await call(mf,"/web-api/account","GET",undefined,alice)).json()).account.id;
 const id=crypto.randomUUID(),op=crypto.randomUUID(),path=`/web-api/runs/${id}`;
 const summary={state:"FINISHED",startedUtcMs:1,endedUtcMs:2,activeDurationMs:1,distanceMeters:1,mode:"OUTDOOR",units:"MILES"};
 const manifest={schemaVersion:1,runId:id,operationId:op,summary,chunks:[{sha256:"a".repeat(64),bytes:1}]};
 await db.prepare("INSERT INTO run_uploads VALUES (?, ?, ?, ?, ?, 1, 1, 100, 2)").bind(owner,id,op,JSON.stringify(manifest),"b".repeat(64)).run();
 await db.prepare("INSERT INTO run_chunks VALUES (?, ?, 0, ?, ?)").bind(owner,id,"a".repeat(64),new Uint8Array([42])).run();
 for(const headers of [alice,{...alice,Origin:origin},{...alice,...browserHeaders,Origin:"https://evil.example"}])assert.equal((await call(mf,path,"DELETE",undefined,headers)).status,403);
 const authorized={...alice,...browserHeaders};
 assert.equal((await call(mf,path,"DELETE",{},authorized)).status,400);
 for(const invalid of ["/web-api/runs",path+"/chunks/0","/web-api/runs/abc"])assert.equal((await call(mf,invalid,"DELETE",undefined,authorized)).status,405);
 assert.equal((await call(mf,path+"?owner=other","DELETE",undefined,authorized)).status,400);
 assert.equal((await call(mf,path,"POST",undefined,authorized)).status,405);
 assert.equal((await call(mf,path,"DELETE",undefined,{...bob,...browserHeaders})).status,200);
 assert.equal((await call(mf,path,"GET",undefined,alice)).status,200);
 assert.equal((await call(mf,path+"/chunks/0","GET",undefined,alice)).status,200);
 for(let i=0;i<2;i++){const response=await call(mf,path,"DELETE",undefined,authorized);assert.equal(response.status,200);assert.deepEqual(await response.json(),{runId:id,deleted:true});}
 assert.equal((await call(mf,path,"GET",undefined,alice)).status,404);
 assert.equal((await call(mf,path+"/chunks/0","GET",undefined,alice)).status,404);
 assert.equal((await db.prepare("SELECT count(*) AS n FROM run_chunks WHERE owner_id=? AND run_id=?").bind(owner,id).first()).n,0);
 assert.equal((await db.prepare("SELECT count(*) AS n FROM run_deletions WHERE owner_id=? AND run_id=?").bind(owner,id).first()).n,1);
});

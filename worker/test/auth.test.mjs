import { workerModules } from "./worker-modules.mjs";
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { Miniflare, convertV4MiniflareOptions } from "miniflare";
import { SignJWT, generateKeyPair, exportJWK } from "jose";
import { handleAuth } from "../build/auth.js";

const audience = "test-web.apps.googleusercontent.com";
const pair = await generateKeyPair("RS256", { extractable: true });
const jwk = { ...await exportJWK(pair.publicKey), kid: "test-key", alg: "RS256", use: "sig" };
async function runtime(t, configured = true, rateLimit = 1000) {
  const mf = new Miniflare(convertV4MiniflareOptions({
    name: "auth-test", modules: workerModules(),
    scriptPath: fileURLToPath(new URL("../build/deploy/index.js", import.meta.url)),
    compatibilityDate: "2026-02-17",
    bindings: { APP_ENV: "development", ...(configured ? { GOOGLE_WEB_CLIENT_ID: audience, GOOGLE_ANDROID_CLIENT_ID: "test-android.apps.googleusercontent.com" } : {}) },
    ratelimits: {
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
  for (const name of ["0001_bootstrap.sql", "0002_accounts.sql"]) {
    const sql = readFileSync(new URL(`../migrations/${name}`, import.meta.url), "utf8");
    await db.batch(sql.replace(/^--.*$/gm, "").split(";").map(s => s.trim()).filter(Boolean).map(s => db.prepare(s)));
  }
  return { mf, db };
}
function call(mf, path, method = "GET", body, headers = {}) {
  return mf.dispatchFetch(`https://test${path}`, { method, headers: { ...(body === undefined ? {} : { "Content-Type": "application/json" }), ...headers },
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

test("missing/failed/global-exhausted limiters fail closed before touching D1", async () => {
  const allow = { limit: async () => ({ success: true }) };
  const db = { prepare: () => { throw new Error("D1 must not be touched"); } };
  for (const [limits, status] of [
    [{}, 503],
    [{ AUTH_RATE_LIMIT: { limit: async () => { throw new Error("unavailable"); } }, AUTH_TOTAL_LIMIT: allow }, 503],
    [{ AUTH_RATE_LIMIT: allow, AUTH_TOTAL_LIMIT: { limit: async () => ({ success: false }) } }, 429],
  ]) {
    const response = await handleAuth(new Request("https://test/api/auth/challenge", { method: "POST" }),
      { DB: db, GOOGLE_WEB_CLIENT_ID: audience, ...limits });
    assert.equal(response.status, status);
  }
});

test("sign-in fails closed until a real audience is configured", async t => {
  const { mf, db } = await runtime(t, false);
  assert.equal((await call(mf, "/api/auth/challenge", "POST")).status, 503);
  assert.equal((await call(mf, "/api/account")).status, 503);
  assert.equal((await db.prepare("SELECT count(*) AS n FROM accounts").first()).n, 0);
});

test("real signature verification rejects forged, expired, misdirected and incomplete identities", async t => {
  const { mf, db } = await runtime(t);
  const nonce = await challenge(mf);
  const now = Math.floor(Date.now() / 1000);
  for (const patch of [{ iss: "https://attacker.example" }, { aud: "another.apps.googleusercontent.com" },
    { aud: [audience, "other"] }, { azp: "other" }, { exp: now - 1 }, { iat: now + 120 },
    { iat: now - 600 }, { exp: undefined }, { sub: "" }, { nonce: "wrong" }]) {
    const response = await call(mf, "/api/auth/google", "POST", { nonce, idToken: await token(nonce, undefined, patch) });
    assert.equal(response.status, 401, JSON.stringify(patch));
  }
  const other = await generateKeyPair("RS256");
  for (const idToken of ["garbage", await token(nonce, undefined, {}, other.privateKey),
    await token(nonce, undefined, {}, new Uint8Array(32), "HS256")]) {
    assert.equal((await call(mf, "/api/auth/google", "POST", { nonce, idToken })).status, 401);
  }
  assert.equal((await db.prepare("SELECT count(*) AS n FROM accounts").first()).n, 0);
});

test("two accounts stay isolated; repeated login uses stable subject rather than display name", async t => {
  const { mf, db } = await runtime(t);
  const alice = await login(mf, "alice");
  const bob = await login(mf, "bob", { azp: "test-android.apps.googleusercontent.com" }); // Same display names, approved Android party.
  const get = async value => (await (await call(mf, "/api/account", "GET", undefined,
    { ...bearer(value), "X-User-Id": "another-owner" })).json()).account;
  const a = await get(alice), b = await get(bob);
  assert.notEqual(a.id, b.id);
  assert.deepEqual(Object.keys(a).sort(), ["displayName", "id", "pictureUrl"]);
  const aliceAgain = await login(mf, "alice", { name: "Updated", picture: "javascript:alert(1)" });
  assert.deepEqual(await get(aliceAgain), { id: a.id, displayName: "Updated", pictureUrl: null });
  assert.equal((await db.prepare("SELECT count(*) AS n FROM accounts").first()).n, 2);
  const sessions = await db.prepare("SELECT token_hash FROM auth_sessions").all();
  assert.equal(sessions.results.length, 3);
  assert.ok(sessions.results.every(row => /^[0-9a-f]{64}$/.test(row.token_hash) && ![alice, bob, aliceAgain].includes(row.token_hash)));
  assert.equal((await call(mf, "/api/account", "GET", undefined, { Cookie: `session=${alice}` })).status, 401);
  assert.equal((await call(mf, "/api/account", "GET", undefined, bearer("a".repeat(64)))).status, 401);
});

test("rate-limited requests stop before database writes and cannot evade limits with forwarded headers", async t => {
  const { mf, db } = await runtime(t, true, 2);
  for (let i = 0; i < 2; i++) assert.equal((await call(mf, "/api/auth/challenge", "POST")).status, 200);
  const response = await call(mf, "/api/auth/challenge", "POST", undefined, { "X-Forwarded-For": "new-client" });
  assert.equal(response.status, 429); assert.equal(response.headers.get("Retry-After"), "60");
  assert.equal((await db.prepare("SELECT count(*) AS n FROM login_challenges").first()).n, 2);
});

test("one-time challenge rejects replay, concurrent exchange, and expiry", async t => {
  const { mf, db } = await runtime(t);
  const nonce = await challenge(mf), idToken = await token(nonce);
  const responses = await Promise.all([1, 2].map(() => call(mf, "/api/auth/google", "POST", { nonce, idToken })));
  assert.deepEqual(responses.map(r => r.status).sort(), [200, 401]);
  assert.equal((await call(mf, "/api/auth/google", "POST", { nonce, idToken })).status, 401);
  assert.equal((await db.prepare("SELECT count(*) AS n FROM auth_sessions").first()).n, 1);
  const expired = await challenge(mf);
  await db.prepare("UPDATE login_challenges SET expires_at = 0").run();
  assert.equal((await call(mf, "/api/auth/google", "POST", { nonce: expired, idToken: await token(expired) })).status, 401);
});

test("logout revokes only its session, expiry blocks access, and database failure stays closed", async t => {
  const { mf, db } = await runtime(t);
  const alice = await login(mf, "alice"), bob = await login(mf, "bob");
  assert.equal((await call(mf, "/api/auth/logout", "POST", undefined, bearer(alice))).status, 200);
  assert.equal((await call(mf, "/api/account", "GET", undefined, bearer(alice))).status, 401);
  assert.equal((await call(mf, "/api/account", "GET", undefined, bearer(bob))).status, 200);
  await db.prepare("UPDATE auth_sessions SET created_at = 0, expires_at = 1 WHERE revoked_at IS NULL").run();
  assert.equal((await call(mf, "/api/account", "GET", undefined, bearer(bob))).status, 401);
  await db.prepare("DROP TABLE auth_sessions").run();
  const failed = await call(mf, "/api/account", "GET", undefined, bearer(bob));
  assert.equal(failed.status, 503);
  assert.deepEqual(await failed.json(), { error: "authentication_unavailable" });
});

test("HTTP boundary rejects browser origins, wrong methods, owner injection and oversized bodies", async t => {
  const { mf } = await runtime(t);
  assert.equal((await call(mf, "/api/auth/challenge", "POST", undefined, { Origin: "https://attacker.example" })).status, 403);
  const method = await call(mf, "/api/auth/logout");
  assert.equal(method.status, 405); assert.equal(method.headers.get("Allow"), "POST");
  const nonce = await challenge(mf), idToken = await token(nonce);
  for (const body of [{ nonce, idToken, ownerId: "chosen-owner" }, { nonce, idToken: "x".repeat(20000) }, null, []]) {
    const response = await call(mf, "/api/auth/google", "POST", body);
    assert.equal(response.status, 400);
    assert.equal(response.headers.get("Cache-Control"), "no-store");
    assert.equal(response.headers.get("Access-Control-Allow-Origin"), null);
  }
});

import { workerModules } from "./worker-modules.mjs";
import { test } from "node:test";
import assert from "node:assert/strict";
import { createHash, randomUUID } from "node:crypto";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { Miniflare, convertV4MiniflareOptions } from "miniflare";
import { SignJWT, generateKeyPair, exportJWK } from "jose";
import { handleRuns } from "../build/runs.js";

const pair = await generateKeyPair("RS256", { extractable: true });
const jwk = { ...await exportJWK(pair.publicKey), kid: "run-test", alg: "RS256", use: "sig" };
const audience = "test-web.apps.googleusercontent.com";
const sha = value => createHash("sha256").update(value).digest("hex");
async function applyDeletionMigration(db) {
  const sql = readFileSync(new URL("../migrations/0004_run_deletions.sql", import.meta.url), "utf8");
  const split = sql.indexOf("CREATE TRIGGER");
  await db.prepare(sql.slice(0, split)).run();
  await db.prepare(sql.slice(split)).run();
}
async function runtime(t, migrateRuns = true) {
  const mf = new Miniflare(convertV4MiniflareOptions({
    name: "runs-test", modules: workerModules(), scriptPath: fileURLToPath(new URL("../build/deploy/index.js", import.meta.url)),
    compatibilityDate: "2026-02-17", bindings: { APP_ENV: "development", GOOGLE_WEB_CLIENT_ID: audience },
    ratelimits: { AUTH_RATE_LIMIT: { namespace_id: "1", simple: { limit: 1000, period: 60 } },
      AUTH_TOTAL_LIMIT: { namespace_id: "2", simple: { limit: 2000, period: 60 } } },
    d1Databases: ["DB"], outboundService: async request => {
      assert.equal(request.url, "https://www.googleapis.com/oauth2/v3/certs");
      return Response.json({ keys: [jwk] });
    },
  }));
  t.after(() => mf.dispose());
  const db = await mf.getD1Database("DB");
  for (const name of ["0001_bootstrap.sql", "0002_accounts.sql", "0003_run_uploads.sql"]) {
    if (name === "0003_run_uploads.sql" && !migrateRuns) continue;
    const sql = readFileSync(new URL(`../migrations/${name}`, import.meta.url), "utf8");
    await db.batch(sql.replace(/^--.*$/gm, "").split(";").map(s => s.trim()).filter(Boolean).map(s => db.prepare(s)));
  }
  if (migrateRuns) await applyDeletionMigration(db);
  const call = (path, method = "GET", body, token, headers = {}) => mf.dispatchFetch(`https://test${path}`, {
    method, headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body === undefined ? {} : { "Content-Type": "application/json" }), ...headers },
    ...(body === undefined ? {} : { body: body instanceof Uint8Array ? body : JSON.stringify(body) }),
  });
  async function login(subject = "alice") {
    const nonce = (await (await call("/api/auth/challenge", "POST")).json()).nonce;
    const now = Math.floor(Date.now() / 1000);
    const idToken = await new SignJWT({ sub: subject, aud: audience, iss: "https://accounts.google.com", iat: now, exp: now + 300, nonce })
      .setProtectedHeader({ alg: "RS256", kid: "run-test" }).sign(pair.privateKey);
    const response = await call("/api/auth/google", "POST", { nonce, idToken });
    assert.equal(response.status, 200);
    return (await response.json()).accessToken;
  }
  const token = await login();
  async function begin(m, as = token) {
    const response = await call("/api/run-uploads", "POST", m, as);
    assert.equal(response.status, 200);
    return response.json();
  }
  const put = (m, receipt, index, data, as = token) => call(`/api/run-uploads/${m.runId}/chunks/${index}`, "PUT", data, as,
    { "Content-Type": "application/octet-stream", "If-Match": `"${receipt.manifestHash}"` });
  const complete = (m, receipt, as = token) => call(`/api/run-uploads/${m.runId}/complete`, "POST", undefined, as,
    { "If-Match": `"${receipt.manifestHash}"` });
  return { mf, db, call, login, token, begin, put, complete };
}
function fixture(chunks = [Buffer.from('{"checkpoint":{"state":"FINISHED"},'), Buffer.from('"measurements":[],"routePoints":[]}')]) {
  return { chunks, m: { schemaVersion: 1, runId: randomUUID(), operationId: randomUUID(), summary: {
    state: "FINISHED", startedUtcMs: 1000, endedUtcMs: 61000, activeDurationMs: 60000,
    distanceMeters: 125.5, mode: "OUTDOOR", units: "KILOMETERS",
  }, chunks: chunks.map(c => ({ sha256: sha(c), bytes: c.length })) } };
}

test("interrupted upload resumes; only complete runs are visible; bytes download exactly", async t => {
  const r = await runtime(t), { m, chunks } = fixture();
  const receipt = await r.begin(m);
  assert.equal((await r.put(m, receipt, 1, chunks[1])).status, 200);
  assert.deepEqual((await (await r.call("/api/runs", "GET", undefined, r.token)).json()).runs, []);
  assert.equal((await r.call(`/api/runs/${m.runId}`, "GET", undefined, r.token)).status, 404);
  assert.equal((await r.complete(m, receipt)).status, 409);
  const status = await (await r.call(`/api/run-uploads/${m.runId}`, "GET", undefined, r.token)).json();
  assert.deepEqual(status.received, [1]);
  assert.deepEqual(await r.begin(m), receipt); // Lost begin response is safe to repeat.
  assert.equal((await r.put(m, receipt, 0, chunks[0])).status, 200);
  const done = await (await r.complete(m, receipt)).json();
  assert.ok(done.completedAt);
  assert.deepEqual(await (await r.complete(m, receipt)).json(), done); // Lost completion response.
  const detail = await (await r.call(`/api/runs/${m.runId}`, "GET", undefined, r.token)).json();
  assert.deepEqual(detail.manifest, m);
  assert.deepEqual(JSON.parse(detail.manifestJson), m);
  assert.equal(sha(detail.manifestJson), detail.manifestHash);
  const downloaded = [];
  for (let i = 0; i < chunks.length; i++) {
    const response = await r.call(`/api/runs/${m.runId}/chunks/${i}`, "GET", undefined, r.token);
    assert.equal(response.headers.get("Content-Type"), "application/octet-stream");
    assert.equal(response.headers.get("Cache-Control"), "no-store");
    downloaded.push(Buffer.from(await response.arrayBuffer()));
  }
  assert.deepEqual(Buffer.concat(downloaded), Buffer.concat(chunks));
});

test("concurrent retries create one receipt; conflicting operations and immutable payloads fail", async t => {
  const r = await runtime(t), { m, chunks } = fixture();
  const receipts = await Promise.all([r.begin(m), r.begin(m)]);
  assert.deepEqual(receipts[0], receipts[1]);
  for (let i = 0; i < chunks.length; i++) {
    assert.deepEqual((await Promise.all([r.put(m, receipts[0], i, chunks[i]), r.put(m, receipts[0], i, chunks[i])])).map(x => x.status), [200, 200]);
  }
  const done = await Promise.all([r.complete(m, receipts[0]), r.complete(m, receipts[0])]);
  assert.equal(done[0].status, 200); assert.equal(done[1].status, 200);
  assert.deepEqual(await done[0].json(), await done[1].json());
  for (const patch of [{ operationId: randomUUID() }, { runId: randomUUID() }, { summary: { ...m.summary, distanceMeters: 1 } }]) {
    assert.equal((await r.call("/api/run-uploads", "POST", { ...m, ...patch }, r.token)).status, 409);
  }
  assert.equal((await r.put(m, receipts[0], 0, chunks[0])).status, 200);
  assert.equal((await r.put(m, receipts[0], 0, Buffer.alloc(chunks[0].length))).status, 400);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_uploads").first()).n, 1);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_chunks").first()).n, chunks.length);
});

test("two accounts cannot read, complete, or append to each other's runs; matching IDs remain isolated", async t => {
  const r = await runtime(t), { m, chunks } = fixture(), bob = await r.login("bob");
  const receipt = await r.begin(m);
  for (const path of [`/api/run-uploads/${m.runId}`, `/api/runs/${m.runId}`, `/api/runs/${m.runId}/chunks/0`]) {
    assert.equal((await r.call(path, "GET", undefined, bob, { "X-User-Id": "alice" })).status, 404);
  }
  assert.equal((await r.put(m, receipt, 0, chunks[0], bob)).status, 404);
  assert.equal((await r.complete(m, receipt, bob)).status, 404);
  const second = await r.begin(m, bob);
  for (let i = 0; i < chunks.length; i++) await r.put(m, second, i, chunks[i], bob);
  assert.equal((await r.complete(m, second, bob)).status, 200);
  assert.equal((await (await r.call("/api/runs", "GET", undefined, r.token)).json()).runs.length, 0);
  assert.equal((await (await r.call("/api/runs", "GET", undefined, bob)).json()).runs.length, 1);
  assert.equal((await r.call(`/api/runs/${m.runId}/chunks/0`, "GET", undefined, r.token)).status, 404);
});

test("expired/revoked sessions block uploads; fresh same-account sign-in resumes the existing draft", async t => {
  const r = await runtime(t), { m, chunks } = fixture(), receipt = await r.begin(m);
  await r.db.prepare("UPDATE auth_sessions SET created_at = 0, expires_at = 1").run();
  assert.equal((await r.put(m, receipt, 0, chunks[0])).status, 401);
  const fresh = await r.login();
  assert.deepEqual(await r.begin(m, fresh), receipt);
  assert.equal((await r.put(m, receipt, 0, chunks[0], fresh)).status, 200);
  await r.call("/api/auth/logout", "POST", undefined, fresh);
  assert.equal((await r.complete(m, receipt, fresh)).status, 401);
});

test("manifest and chunk bounds reject malformed data without partial storage", async t => {
  const r = await runtime(t), { m, chunks } = fixture();
  for (const patch of [{ ownerId: "injected" }, { schemaVersion: 2 }, { chunks: [] }, { chunks: Array(129).fill(m.chunks[0]) },
    { chunks: [{ sha256: "bad", bytes: 1 }] }, { chunks: [{ ...m.chunks[0], bytes: 131073 }] },
    { summary: { ...m.summary, activeDurationMs: -1 } }, { summary: { ...m.summary, state: "RUNNING" } },
    { summary: { ...m.summary, distanceMeters: null } }, { summary: { ...m.summary, mode: "OTHER" } }]) {
    assert.equal((await r.call("/api/run-uploads", "POST", { ...m, ...patch }, r.token)).status, 400);
  }
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_uploads").first()).n, 0);
  const receipt = await r.begin(m);
  for (const bad of [Buffer.alloc(0), Buffer.alloc(131073), Buffer.alloc(chunks[0].length)]) {
    assert.equal((await r.put(m, receipt, 0, bad)).status, 400);
  }
  assert.equal((await r.put(m, receipt, 99, chunks[0])).status, 404);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_chunks").first()).n, 0);
  assert.equal((await r.call(`/api/run-uploads/${m.runId}/chunks/0`, "PUT", chunks[0], r.token,
    { "Content-Type": "application/octet-stream" })).status, 409); // Manifest identity required.
});

test("expired drafts clear their chunks; completion cannot be applied to a replacement manifest", async t => {
  const r = await runtime(t), { m, chunks } = fixture(), receipt = await r.begin(m);
  await r.put(m, receipt, 0, chunks[0]);
  await r.db.prepare("UPDATE run_uploads SET created_at = 0, expires_at = 1").run();
  assert.equal((await r.complete(m, receipt)).status, 410);
  const replacement = { ...m, operationId: randomUUID() };
  const second = await r.begin(replacement);
  assert.notEqual(second.manifestHash, receipt.manifestHash);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_chunks").first()).n, 0);
  assert.equal((await r.complete(m, receipt)).status, 409);
  assert.equal((await r.put(m, receipt, 0, chunks[0])).status, 409);
});

test("at most four incomplete uploads are reserved even concurrently; completed rows survive draft cleanup", async t => {
  const r = await runtime(t);
  const rows = Array.from({ length: 6 }, () => fixture());
  const responses = await Promise.all(rows.map(({ m }) => r.call("/api/run-uploads", "POST", m, r.token)));
  assert.equal(responses.filter(r => r.status === 200).length, 4);
  assert.equal(responses.filter(r => r.status === 429).length, 2);
  const selected = rows[responses.findIndex(r => r.status === 200)];
  const receipt = await r.begin(selected.m);
  for (let i = 0; i < selected.chunks.length; i++) await r.put(selected.m, receipt, i, selected.chunks[i]);
  await r.complete(selected.m, receipt);
  await r.db.prepare("UPDATE run_uploads SET created_at = 0, expires_at = 1").run();
  await r.begin(fixture().m);
  assert.equal((await r.call(`/api/runs/${selected.m.runId}`, "GET", undefined, r.token)).status, 200);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_uploads").first()).n, 2);
});

test("history pagination has no duplicates for same-second completions and ignores foreign owners", async t => {
  const r = await runtime(t);
  for (let i = 0; i < 23; i++) {
    const { m, chunks } = fixture([Buffer.from(`run-${i}`)]), receipt = await r.begin(m);
    await r.put(m, receipt, 0, chunks[0]);
    assert.equal((await r.complete(m, receipt)).status, 200);
  }
  const one = await (await r.call("/api/runs", "GET", undefined, r.token)).json();
  assert.equal(one.runs.length, 20); assert.ok(one.next);
  const two = await (await r.call(`/api/runs?after=${encodeURIComponent(one.next)}`, "GET", undefined, r.token)).json();
  assert.equal(two.runs.length, 3); assert.equal(two.next, null);
  assert.equal(new Set([...one.runs, ...two.runs].map(r => r.runId)).size, 23);
  for (const query of ["after=bad", "owner=alice", "after=1:a&after=2:b"]) {
    assert.equal((await r.call(`/api/runs?${query}`, "GET", undefined, r.token)).status, 400);
  }
});

test("binary chunk limit round-trips without truncation and database outages never acknowledge success", async t => {
  const r = await runtime(t), { m, chunks } = fixture([Buffer.alloc(131072, 42)]), receipt = await r.begin(m);
  assert.equal((await r.put(m, receipt, 0, chunks[0])).status, 200);
  await r.complete(m, receipt);
  assert.deepEqual(Buffer.from(await (await r.call(`/api/runs/${m.runId}/chunks/0`, "GET", undefined, r.token)).arrayBuffer()), chunks[0]);
  await r.db.prepare("DROP TABLE run_chunks").run();
  const response = await r.call(`/api/runs/${m.runId}/chunks/0`, "GET", undefined, r.token);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: "storage_unavailable" });
});

test("native boundary denies origins/cookies, unknown methods, and unavailable rate limits", async t => {
  const r = await runtime(t), { m } = fixture();
  assert.equal((await r.call("/api/run-uploads", "POST", m, r.token, { Origin: "https://attacker.example" })).status, 403);
  assert.equal((await r.call("/api/runs", "GET", undefined, undefined, { Cookie: `session=${r.token}` })).status, 401);
  assert.equal((await r.call("/api/runs", "DELETE", undefined, r.token)).status, 405);
  assert.equal((await r.call("/api/runs/not-an-id", "GET", undefined, r.token)).status, 404);
  const response = await handleRuns(new Request("https://test/api/runs"), {
    GOOGLE_WEB_CLIENT_ID: audience, DB: { prepare: () => { throw Error("must not access DB"); } },
  });
  assert.equal(response.status, 503);
});

test("additive migration preserves existing accounts/sessions and compound ownership foreign keys", async t => {
  const r = await runtime(t, false);
  const before = await r.db.prepare("SELECT * FROM accounts").all();
  const sql = readFileSync(new URL("../migrations/0003_run_uploads.sql", import.meta.url), "utf8");
  await r.db.batch(sql.split(";").map(s => s.trim()).filter(Boolean).map(s => r.db.prepare(s)));
  await applyDeletionMigration(r.db);
  assert.deepEqual((await r.db.prepare("SELECT * FROM accounts").all()).results, before.results);
  assert.equal((await r.call("/api/account", "GET", undefined, r.token)).status, 200);
  const { m, chunks } = fixture(), receipt = await r.begin(m);
  await assert.rejects(r.db.prepare("INSERT INTO run_chunks VALUES (?, ?, ?, ?, ?)")
    .bind("wrong-owner", m.runId, 0, m.chunks[0].sha256, chunks[0]).run());
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_chunks").first()).n, 0);
  await r.db.prepare("CREATE TRIGGER refuse_chunk BEFORE INSERT ON run_chunks BEGIN SELECT RAISE(ABORT, 'test write failure'); END").run();
  assert.equal((await r.put(m, receipt, 0, chunks[0])).status, 503);
  assert.equal((await r.complete(m, receipt)).status, 409);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_chunks").first()).n, 0);
});

test("discard removes every cloud byte and stale uploads cannot resurrect it", async t => {
  const r = await runtime(t), { m, chunks } = fixture(), receipt = await r.begin(m);
  for (let i = 0; i < chunks.length; i++) await r.put(m, receipt, i, chunks[i]);
  await r.complete(m, receipt);
  for (let i = 0; i < 2; i++) {
    const deleted = await r.call(`/api/runs/${m.runId}`, "DELETE", undefined, r.token);
    assert.equal(deleted.status, 200); assert.deepEqual(await deleted.json(), { runId: m.runId, deleted: true });
  }
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_chunks").first()).n, 0);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_uploads").first()).n, 0);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_deletions").first()).n, 1);
  assert.equal((await r.call("/api/run-uploads", "POST", m, r.token)).status, 410);
  assert.equal((await r.put(m, receipt, 0, chunks[0])).status, 404);
  assert.equal((await r.call(`/api/runs/${m.runId}`, "GET", undefined, r.token)).status, 404);
});

test("deletion racing begin or completion always wins; foreign-account deletion stays isolated", async t => {
  const r = await runtime(t), { m, chunks } = fixture();
  await Promise.all([r.call("/api/run-uploads", "POST", m, r.token), r.call(`/api/runs/${m.runId}`, "DELETE", undefined, r.token)]);
  assert.equal((await r.call("/api/run-uploads", "POST", m, r.token)).status, 410);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_uploads").first()).n, 0);
  const second = fixture(), receipt = await r.begin(second.m), bob = await r.login("bob");
  for (let i = 0; i < second.chunks.length; i++) await r.put(second.m, receipt, i, second.chunks[i]);
  await r.call(`/api/runs/${second.m.runId}`, "DELETE", undefined, bob);
  assert.equal((await r.complete(second.m, receipt)).status, 200);
  await Promise.all([r.complete(second.m, receipt), r.call(`/api/runs/${second.m.runId}`, "DELETE", undefined, r.token)]);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_uploads").first()).n, 0);
  assert.equal((await r.db.prepare("SELECT count(*) n FROM run_chunks").first()).n, 0);
});

test("deletion feed is private, paginated, repeatable and validates cursors", async t => {
  const r = await runtime(t), bob = await r.login("bob");
  const ids = Array.from({length: 23}, () => randomUUID()).sort();
  for (const id of ids) assert.equal((await r.call(`/api/runs/${id}`, "DELETE", undefined, r.token)).status, 200);
  const page = await (await r.call("/api/run-deletions", "GET", undefined, r.token)).json();
  assert.deepEqual(page.deleted, ids.slice(0, 20)); assert.equal(page.next, ids[19]);
  const last = await (await r.call(`/api/run-deletions?after=${page.next}`, "GET", undefined, r.token)).json();
  assert.deepEqual(last, {deleted: ids.slice(20), next: null});
  assert.deepEqual(await (await r.call("/api/run-deletions", "GET", undefined, bob)).json(), {deleted: [], next: null});
  assert.equal((await r.call("/api/run-deletions")).status, 401);
  for (const query of ["?after=bad", "?owner=alice", `?after=${ids[0]}&after=${ids[1]}`])
    assert.equal((await r.call(`/api/run-deletions${query}`, "GET", undefined, r.token)).status, 400);
  assert.equal((await r.call("/api/run-deletions", "POST", undefined, r.token)).status, 405);
});

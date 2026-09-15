import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { Miniflare, convertV4MiniflareOptions } from "miniflare";
import { assertDevelopmentTarget } from "../scripts/deploy-guard.mjs";

const migration = readFileSync(new URL("../migrations/0001_bootstrap.sql", import.meta.url), "utf8");
async function applyMigration(db) {
  const statements = migration.replace(/^--.*$/gm, "").split(";").map(sql => sql.trim()).filter(Boolean);
  await db.batch(statements.map(sql => db.prepare(sql)));
}
async function runtime(t, environment = "development") {
  const mf = new Miniflare(convertV4MiniflareOptions({
    name: "test-worker",
    modules: true, scriptPath: fileURLToPath(new URL("../build/deploy/index.js", import.meta.url)),
    compatibilityDate: "2026-02-17", bindings: { APP_ENV: environment }, d1Databases: ["DB"],
  }));
  t.after(() => mf.dispose());
  return mf;
}

test("health is live before migration; readiness requires the real D1 schema", async t => {
  const mf = await runtime(t);
  assert.equal((await mf.dispatchFetch("https://test/healthz")).status, 200);
  let response = await mf.dispatchFetch("https://test/readyz");
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { status: "not_ready" });
  const db = await mf.getD1Database("DB");
  await applyMigration(db);
  response = await mf.dispatchFetch("https://test/readyz");
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { service: "WAYiRUN", status: "ready" });
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  await db.prepare("UPDATE service_metadata SET schema_version = 2").run();
  assert.equal((await mf.dispatchFetch("https://test/readyz")).status, 503);
});

test("schema rejects a second singleton and invalid application/version", async t => {
  const db = await (await runtime(t)).getD1Database("DB");
  await applyMigration(db);
  for (const sql of [
    "INSERT INTO service_metadata VALUES (2, 'WAYiRUN', 1)",
    "UPDATE service_metadata SET application = 'other'",
    "UPDATE service_metadata SET schema_version = 0",
  ]) await assert.rejects(db.prepare(sql).run());
  assert.equal((await db.prepare("SELECT count(*) AS count FROM service_metadata").first()).count, 1);
});

test("private API stays closed for all methods, even with purported credentials", async t => {
  const mf = await runtime(t);
  for (const method of ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"]) {
    const response = await mf.dispatchFetch("https://test/api/runs", {
      method, headers: { Authorization: "Bearer not-a-real-token", "X-User-Id": "someone-else" },
    });
    assert.equal(response.status, 503);
    assert.equal(response.headers.get("Access-Control-Allow-Origin"), null);
    if (method !== "HEAD") assert.deepEqual(await response.json(), { error: "api_not_available" });
  }
});

test("unsupported routes/methods and HEAD are handled without database mutation", async t => {
  const mf = await runtime(t);
  assert.equal((await mf.dispatchFetch("https://test/anything")).status, 404);
  const rejected = await mf.dispatchFetch("https://test/healthz", { method: "POST" });
  assert.equal(rejected.status, 405);
  assert.equal(rejected.headers.get("Allow"), "GET, HEAD");
  const head = await mf.dispatchFetch("https://test/healthz", { method: "HEAD" });
  assert.equal(head.status, 200);
  assert.equal(await head.text(), "");
});

test("an unintended environment is unavailable", async t => {
  const mf = await runtime(t, "production");
  assert.equal((await mf.dispatchFetch("https://test/healthz")).status, 503);
});

test("deploy guard rejects other accounts, databases, worker names and routes", () => {
  const config = JSON.parse(readFileSync(new URL("../wrangler.jsonc", import.meta.url), "utf8"));
  assert.doesNotThrow(() => assertDevelopmentTarget(config));
  for (const patch of [{ name: "production" }, { account_id: "another" }, { routes: [] },
    { vars: { APP_ENV: "production" } }, { d1_databases: [] }, { env: { production: {} } }]) {
    assert.throws(() => assertDevelopmentTarget({ ...config, ...patch }));
  }
  const altered = structuredClone(config);
  altered.d1_databases[0].database_id = "another";
  assert.throws(() => assertDevelopmentTarget(altered));
});

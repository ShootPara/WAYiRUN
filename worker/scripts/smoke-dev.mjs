import assert from "node:assert/strict";
import { verifyRollout } from "./verify-rollout.mjs";

const origin = "https://wayirun-dev.unopenedparachute.workers.dev";
await verifyRollout(async () => {
const passed = [];
for (const [path, method, status, expected] of [
  ["/healthz", "GET", 200, { service: "WAYiRUN", status: "ok" }],
  ["/readyz", "GET", 200, { service: "WAYiRUN", status: "ready" }],
  ["/readyz", "HEAD", 200, null],
  ["/missing", "GET", 404, { error: "not_found" }],
  ["/healthz", "POST", 405, { error: "method_not_allowed" }],
  ["/api/runs", "GET", 503, { error: "api_not_available" }],
  ["/api/runs", "POST", 503, { error: "api_not_available" }],
  ["/api/auth/google", "POST", 400, { error: "invalid_request" }],
  ["/api/account", "GET", 401, { error: "unauthorized" }],
]) {
  const response = await fetch(origin + path, { method, signal: AbortSignal.timeout(15_000), redirect: "error" });
  assert.equal(response.status, status, `${method} ${path}`);
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  if (expected) assert.deepEqual(await response.json(), expected);
  else assert.equal(await response.text(), "");
  passed.push(`PASS ${method} ${path} (${status})`);
}
for (const message of passed) console.log(message);
});

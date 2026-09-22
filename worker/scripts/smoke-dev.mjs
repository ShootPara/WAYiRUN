import assert from "node:assert/strict";
import { verifyRollout } from "./verify-rollout.mjs";

const origin = "https://wayirun-dev.unopenedparachute.workers.dev";
await verifyRollout(async () => {
const passed = [];
for (const [path, method, status, expected] of [
  ["/api/photos/00000000-0000-0000-0000-000000000001", "GET", 401, {error:"unauthorized"}],
  ["/web-api/photos/00000000-0000-0000-0000-000000000001", "GET", 401, {error:"unauthorized"}],
  ["/p/"+"0".repeat(64), "GET", 404, {error:"not_found"}],
  ["/healthz", "GET", 200, { service: "WAYiRUN", status: "ok" }],
  ["/readyz", "GET", 200, { service: "WAYiRUN", status: "ready" }],
  ["/readyz", "HEAD", 200, null],
  ["/missing", "GET", 404, { error: "not_found" }],
  ["/healthz", "POST", 405, { error: "method_not_allowed" }],
  ["/api/runs", "GET", 401, { error: "unauthorized" }],
  ["/api/run-uploads", "POST", 401, { error: "unauthorized" }],
  ["/api/auth/google", "POST", 400, { error: "invalid_request" }],
  ["/api/account", "GET", 401, { error: "unauthorized" }],
  ["/api/account/openai-key", "GET", 401, { error: "unauthorized" }],
  ["/api/coaching/00000000-0000-0000-0000-000000000001", "GET", 401, { error: "unauthorized" }],
  ["/api/coaching/00000000-0000-0000-0000-000000000001/audio", "GET", 401, { error: "unauthorized" }],
  ["/web-api/account", "GET", 401, { error: "unauthorized" }],
  ["/web-api/runs", "GET", 401, { error: "unauthorized" }],
  ["/api/coaching-history/00000000-0000-0000-0000-000000000001", "GET", 401, { error: "unauthorized" }],
  ["/web-api/coaching-history/00000000-0000-0000-0000-000000000001", "GET", 401, { error: "unauthorized" }],
]) {
  const response = await fetch(origin + path, { method, signal: AbortSignal.timeout(15_000), redirect: "error" });
  assert.equal(response.status, status, `${method} ${path}`);
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  if (expected) assert.deepEqual(await response.json(), expected);
  else assert.equal(await response.text(), "");
  passed.push(`PASS ${method} ${path} (${status})`);
}
for (const [path, type, marker] of [["/public-photo.js", "text/javascript", "Start location"], ["/achievements.js", "text/javascript", "evaluateAchievements"], ["/export.js", "text/javascript", "createCsvExport"], ["/", "text/html", "WAYiRUN"], ["/app.js", "text/javascript", "setupSignIn"], ["/style.css", "text/css", ".totals"], ["/route.js", "text/javascript", "prepareRoute"], ["/map.js", "text/javascript", "showRouteMap"], ["/leaflet.js", "text/javascript", "1.9.4"], ["/leaflet.css", "text/css", ".leaflet-container"]]) {
  const response = await fetch(origin + path, { signal: AbortSignal.timeout(15000), redirect: "error" });
  assert.equal(response.status, 200); assert.ok(response.headers.get("Content-Type")?.startsWith(type));
  assert.equal(response.headers.get("Cache-Control"), "no-store");
  assert.ok(response.headers.get("Content-Security-Policy")?.includes("frame-ancestors 'none'"));
  assert.ok((await response.text()).includes(marker)); passed.push(`PASS GET ${path} (200)`);
}
for (const message of passed) console.log(message);
});

import { test } from "node:test";
import assert from "node:assert/strict";
import { verifyRollout } from "../scripts/verify-rollout.mjs";

test("rollout tolerates a transient old response and reruns the complete verification", async () => {
  let calls = 0; const delays = [];
  await verifyRollout(async () => { calls++; if (calls < 3) throw new Error("old deployment"); },
    { attempts: 3, delayMs: 5, pause: async delay => { delays.push(delay); } });
  assert.equal(calls, 3); assert.deepEqual(delays, [5, 5]);
});

test("persistent rollout failure fails the deployment after the bounded attempts", async () => {
  let calls = 0;
  const failure = new Error("not ready");
  await assert.rejects(verifyRollout(async () => { calls++; throw failure; },
    { attempts: 3, pause: async () => {} }), error => error === failure);
  assert.equal(calls, 3);
});

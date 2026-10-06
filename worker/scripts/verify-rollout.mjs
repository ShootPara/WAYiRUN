import { setTimeout } from "node:timers/promises";

// A newly uploaded Worker can take time to reach the region executing this check.
// Retry the complete check set, but never turn a persistent failure into success.
export async function verifyRollout(check, { attempts = 6, delayMs = 5000, pause = setTimeout } = {}) {
  for (let attempt = 1; ; attempt++) {
    try { await check(); return; }
    catch (error) {
      if (attempt >= attempts) throw error;
      await pause(delayMs);
    }
  }
}

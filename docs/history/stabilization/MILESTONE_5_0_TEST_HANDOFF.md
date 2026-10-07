# 1 Milestone 5.0 test handoff

## 1.1 Status and scope

Milestone 5.0 is complete. Phase 1 prepared 17 new synthetic test cases; Phase 2 passed compilation, Cloudflare deployment dry-run and the full Worker suite. Preserve the dirty tree from earlier milestones. Android/photo/public-page rendering remains Milestone 5.1. Read REQUIREMENTS.md, TASKS.md, the then-current working guide and worker/WEATHER_CONTRACT.md before that work.

Review worker/src/run-weather.ts, index.ts route, migrations/0010_run_weather.sql and test/run-weather.test.mjs. Provider terms were checked September 27. Attribution alongside data is recorded as a 5.1 integration constraint; do not lose it during the model switch.

## 1.2 Execute locally

From the repo root, run `git diff --check`. From `<repository-root>\worker`, run:

```powershell
npm.cmd test
```

This performs TypeScript compilation, Wrangler deployment dry-run (not deployment) and all Node tests. Baseline before this milestone: 125 tests. Record actual new count, failures/skips/cancellations and exit code. Fix bounded implementation/test defects and rerun the full gate. Existing photo/publication, routing, privacy, deletion, export and coaching tests must stay green. Do not run live smoke/deploy scripts or remote D1 commands.

## 1.3 Assertions to inspect

- Rounded query coordinates only, UTC start-hour floor, recent versus historical endpoint/date, no current-weather fallback.
- Exact matching hour/units, malformed/missing/null/unknown/out-of-range weather rejection, bounded response fields and WMO mapping.
- Owner/session isolation, browser-header rejection, GET-only/query rejection, no-GPS/indoor/future data, corrupted archive hash never reaching provider.
- Stable cache, failure retry, provider HTTP/network/malformed/oversize failures, timeout and stalled body cancellation.
- Same-run/different-owner concurrent requests limited by reservation/global gate; stale attempt cannot overwrite newer success.
- Session revocation, run tombstone and account deletion during provider response prevent delivery/persistence; cache cascades with archive deletion.
- No photo rows, publication rows or receipts created by weather lookup; public payloads stay unchanged in this milestone.

Inspect the built router or use a local bundled Worker fixture to confirm /api/weather/{UUID} reaches the authenticated handler, not the generic unavailable branch. No browser/Android UI changed, so no screenshots, APK build or emulator install is needed for 5.0. Tests must not use real user coordinates or contact Open-Meteo; all provider fetches are injected synthetic responses. A live provider smoke is not necessary to pass this implementation gate.

## 1.4 Finish and stop

September 28 result: `npm.cmd test` passed TypeScript compilation, Cloudflare deployment dry-run and all 142 tests with zero failures, skips or cancellations. All 17 new weather cases passed. The generated deployment bundle contains the weather handler and `/api/weather/` dispatch. Final `git diff --check` passed. No live provider request was made.

Milestone 5.0 is complete, leaving 5.1 and final stabilization pending. Do not claim phone weather/rendering passed. No commit/push, deployment, remote migration or phone installation. The next implementation phase is 5.1: route overlay placement, optional weather, stable preview/JPEG/upload metadata and unobtrusive compliant attribution.

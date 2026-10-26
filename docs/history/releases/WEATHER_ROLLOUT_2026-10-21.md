# 1 October 21, 2026 weather rollout evidence

Status: Historical deployment evidence only; non-authoritative. Current behavior belongs in `REQUIREMENTS.md`, `CURRENT_STATE.md` and `DATA_MODEL.md`.

## 1.1 Authorization and target

The owner explicitly authorized migration 0012, production Worker deployment and documentation updates. The exact-target production guard passed for `wayirun-prod`, `wayirun-prod-db` and `https://wayirun.slopcopy.com`. Development remained unchanged and write-frozen. No commit, push or phone installation was performed.

## 1.2 Backup and rollout

Before migration, a production D1 SQL export was saved to ignored `private-signing/production/weather-2026-10-21/pre-weather.sql`. It contains private records and must remain outside Git. No credentials or private record contents are included here.

`npm run deploy:prod` completed the guarded production dry-run, applied only `0012_early_weather.sql`, and deployed Worker version `0c8c3e4d-cf9e-43ef-b7a8-350b559efb0d`. The migration created `run_weather_early` and its `delete_early_weather` tombstone cleanup trigger; applied migrations 0001–0011 were unchanged.

## 1.3 Verification

Before rollout, 176 Worker tests, 127 Android JVM tests and 34 focused emulator tests passed. Debug and signed release assembly, lint (zero errors, 21 warnings), production-readiness checks and APK signature verification passed.

After rollout, `npm run smoke:prod` confirmed `/healthz` and `/readyz`. The deployed early-weather endpoint returned 401 for an unsigned POST, 405 for GET, and 403 for a browser Origin. These rejected probes created no test accounts or run data and made no weather-provider request. D1 recorded migration 0012 and both new schema objects.

| Retained records | Before | After |
| --- | ---: | ---: |
| Accounts | 1 | 1 |
| Completed runs | 30 | 30 |
| Archive chunks | 176 | 176 |
| Photos | 21 | 21 |
| Completed weather cache entries | 3 | 3 |
| Deletion tombstones | 15 | 15 |

The new early-weather table contained zero entries after deployment and probes. These checks establish deployment, schema, availability and access boundaries; they do not establish authenticated live-provider retrieval or physical-phone behavior.

## 1.4 Android artifact and remaining evidence

Signed production candidate: `android/app/build/outputs/apk/release/WAYiRUN-2026-10-21-weather.apk` (ignored generated artifact).

SHA-256: `905B4B9E87ED9931AFB520B6A15DFAEACDD7B7B244C5C74B8D1022F01DEA43EC`.

It includes run-start weather for both modes, cached photo weather, the translucent white icon circle, and the preceding camera-return/session reliability changes. It has not been installed on the owner's phone. Home-Wi-Fi timing, indoor network location, connectivity changes, finish/photo behavior and the original camera/login incident require separate physical verification.

# 1 Health Connect export

## 1.1 Scope and consent

Implement REQUIREMENTS 12 and TASKS 9.6 using stable AndroidX Health Connect 1.1.0. Settings explicitly connects the current account (or local-only mode), requests only exercise and distance write permissions, and explains export of retained completed runs and future runs. Account changes pause new exports until explicitly connected again. No health imports, GPS routes, photos, calories, steps, heart rate, or coaching are added to Health Connect.

## 1.2 Records and timing

One running/treadmill exercise session and one distance record per run, stable client IDs derived from the immutable run ID, version 1. Use actual start/end instants and frozen time zone offsets. Represent recorded pauses as pause segments; do not invent wall times for legacy missing intervals. Zero-duration or inconsistent clock records remain safely unexported with a visible explanation; WAYiRUN records remain intact. Original active time is preserved in session notes where historical wall time lacks complete pause evidence.

## 1.3 Durability and cleanup

Room v6 keeps an export ledger without a run foreign-key cascade. Enqueue before external insertion. A run deletion atomically changes any existing ledger row to DELETE; this survives local/cloud source deletion and permissions loss. Cleanup deletes only WAYiRUN session/distance IDs, never broad time ranges or another app's data. Stable IDs prevent duplicates after interrupted inserts. A guarded completion update cannot overwrite a pending deletion. Background work is independent of internet availability, bounded per pass, and retries recoverable errors. Revoked permissions require user action; no repeated prompts.

## 1.4 Verification and next phase

Verify projection, pauses, stable IDs, partial-write retries, account scope, deletion races, migration preservation and permission states. Build/lint/JVM plus focused emulator checks; real Health Connect consent/display remains phone acceptance. Then author a separate comprehensive test plan and issue ledger for bugs, quibbles and requested refinements. No production deployment or Git commit/push in this milestone.

## 1.5 References

- https://developer.android.com/jetpack/androidx/releases/health-connect
- https://developer.android.com/health-and-fitness/health-connect/get-started
- https://developer.android.com/health-and-fitness/health-connect/write-data
- https://developer.android.com/health-and-fitness/health-connect/sync-data

# WAYiRUN sync2 verification

## 1 Existing-phone update

Install the dated APK identified in TASKS.md over the existing app. Do not uninstall or clear your two runs. Your sync1 checks are already accepted; this build adds restore and deletion reconciliation while preserving the main screen, splash and permissions behavior.

Open the gear and confirm version 0.1.0-dev-sync2. Sign in again if requested for an expired session, then Retry sync. The existing synced count should remain stable without duplicate runs. Cloud checks run in background batches and may take several scheduling cycles; tracking remains available throughout.

## 2 Restore check on a separate installation

Only if a spare device or separate test installation is available: sign into the same Google account there and let sync finish. Gear sync count should reflect the two cloud runs. Reopen the app after restoration to load the latest saved summary. Its time, distance and pace should match the original. A second sync must not create duplicates. This build intentionally does not add a phone history browser.

Do not erase the working phone to perform this test. Automated emulator tests already cover restoration, interrupted downloads, account switches and deletion races. Real Google cross-device restore remains a separate device check.

## 3 Deletion check

Use a disposable new test run only. After it synchronizes to both installations, discard it on one. When the other reconnects and completes its deletion sweep, the local copy must disappear and never re-upload. Refresh/reopen the summary if it was already visible. Deletion cannot reach a disconnected device until it reconnects with valid authentication.

## 4 Boundaries

A conflicting or corrupt cloud archive is left uncommitted and reports a gear-only error; existing local runs remain intact. Staged chunks resume after connection loss and are checked again before reuse. Expired sessions need sign-in; they never stop recording. Music verification remains deferred, and maps/photos/coaching/Health Connect are not part of this build.

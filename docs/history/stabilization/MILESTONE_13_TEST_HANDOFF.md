# 1 Milestone 13: independent announcements

## 1.1 Status and guardrails

Code/test preparation and verification complete September 28, 2026. the owner paused and switched models before test execution as requested. No commit/push, deployment, physical-phone installation, paid provider request or database migration occurred. Milestone 14 remains separate.

Read REQUIREMENTS.md, TASKS.md, the then-current working guide and AUTO_PAUSE_AND_MILESTONES_PLAN.md before continuing. Preserve all existing uncommitted work. No commit/push, deployment, physical-phone installation, paid provider requests or database migration. Keep goal-audio priority/coalescing in Milestone 14. Do not ask the owner to install an intermediate APK.

## 1.2 Changes prepared

- RunModels.kt: optional AnnouncementSelection with version 1 (omission within the object means version 1), independently enabled time/distance channels, category validation and one effective resolver. The existing outer announcementsEnabled remains the master. Missing selection uses the legacy interval; explicit selection overrides that interval. Master off disables both effective channels without erasing their stored selections.
- RunController.kt: both threshold paths use the resolver, including time-index recovery. Distance progress remains derived from accumulated distance, preserving existing split interpolation and pause/gap boundaries. No event or cue format change.
- AnnouncementPreferences.kt: one-time migration from the old interval to its matching channel, retaining the master preference. An atomic editor transaction stores the marker and both channels. New defaults are five-minute time only, with one selected distance unit stored but disabled. No future-read migration can overwrite a dual choice.
- AnnouncementSettings.kt and WayirunApp.kt: master switch, independent channel switches and existing interval radio choices, in settings only. Preferences persist each edit; a new run captures the immutable selection. Disabled master/channel retains its selected radio. Global units label the distance choices; running captures do not change with later preference edits.
- DualAnnouncementsTest, AnnouncementPreferencesTest, AnnouncementSettingsTest and RunDatabaseTest: independent/master combinations, both units, legacy/new serialization, invalid settings, batching/splits, no-distance time cues, goal continuation, pause/recovery/source reset, migration once, reopen, settings-only controls, captured units/selections and byte-preserving archive round trips.
- Worker coaching.test.mjs and export.test.mjs: additive fixtures for legacy, time-only, distance-only, both and master-off captures; assert full settings/checkpoint retention. No production Worker/browser changes.

## 1.3 Compatibility review

Room stores checkpoint JSON as text; no table/schema change is needed. RunArchive validation decodes the updated domain settings and retains the original checkpoint string. Archive round-trip fixtures explicitly check unchanged bytes. Existing stored captures are not rewritten during migration.

Worker runs.ts stores opaque chunk bytes and manifests. coaching-context.ts preserves complete checkpoint/settings objects while validating existing tracking fields. app.browserjs validates core mode/units/countdown/stride and renders only its existing settings summary; it has no exclusive announcement enum renderer to update. export.browserjs retains the complete stored run/checkpoint in record_json, so reconstruction preserves the additive selection without a CSV version change. The new fixtures exercise coaching and CSV preservation. Old app binaries are not promised forward decoding of new fields; new binaries must decode old captures.

## 1.4 Android gate

From android/:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Inspect XML counts and actual lint severity; do not infer success or totals from prior milestones. Five new JVM cases were prepared. If generated Windows folders again fail deletion, inspect the exact build path and ReadOnly attributes before bounded repair; never clean source or unrelated artifacts.

Explicitly select the emulator after checking adb devices. Install fresh app/test packages from output metadata on the emulator only. Run AnnouncementPreferencesTest, AnnouncementSettingsTest, RunDatabaseTest, RunAnnouncementsTest's JVM coverage, plus emulator RunScreenTest and NewRunControlsTest for settings placement regressions. Run existing SyncMigrationTest to protect archive restore behavior. Refuse any unfinished user run; do not clear app data as a shortcut. Resolve failures narrowly and report changed tests honestly.

## 1.5 Worker and visual gate

From worker/, build the existing test imports, then run targeted compatibility tests:

```powershell
npm.cmd run build
node --test --test-timeout=60000 test/coaching.test.mjs test/export.test.mjs
```

No real provider or remote deployment is needed. Broaden only if a failure reveals an actual shared-contract change.

Inspect settings in light/dark and at normal/200-percent font scale on the emulator. Scroll through both channels: labels and radio choices must wrap without overlap, switches remain reachable, miles/km follow global units, master off preserves choices, neither channel is valid, and reopens retain both. Verify milestone controls are absent from New Run, running and summary screens. Capture/inspect screenshots in ignored android/app/build/verification/milestone-13 and restore emulator font/theme afterward. No screenshots were produced in the code phase.

## 1.6 Exit and next milestone

Record actual gate outcomes, artifact path/hash, visual checks and any remaining risks here and in TASKS.md. Do not claim physical speech acceptance. The current controller can emit both coincident channels; one recap and goal priority are deliberately Milestone 14, not failures to patch speculatively in this milestone. After M13 passes, plan/code M14 separately and honor the owner's model-switch preference unless he explicitly waives it again.

## 1.7 Final results

The Android gate passed: 107 JVM tests, zero failures/errors/skips; debug app and instrumentation APK assembly succeeded; lint reported zero errors and 21 warnings. The additional visual fixture compiled and lint passed again. Git diff whitespace checks passed.

Worker TypeScript compilation and 28 targeted coaching/export tests passed. These include exact retention of legacy, time-only, distance-only, both-channel and master-off settings inside coaching context and CSV reconstruction. No production Worker source changed, so no broader deployment or remote test was needed.

Fresh app and test APKs installed successfully on emulator-5554 only. A 27-case batch passed in 134.357 seconds: preference migration, functional announcement settings, full RunDatabaseTest and SyncMigrationTest classes, RunScreenTest and RunEntryTest. The batch verifies one-time migration, settings-only placement, immutable active-run captures, archive bytes, schema migrations, run UI and lifecycle regressions. A final AnnouncementSettingsTest rerun passed both cases in 39.827 seconds after adding visual qualification.

Four ignored screenshots under android/app/build/verification/milestone-13 were inspected: light/dark at font scale 1.0/2.0. Both channel switches and all radio options remained readable and reachable without overlap; selected/unselected contrast was clear. The fixture changes font scale through Compose-local density, so no emulator-wide theme/font setting needed restoration.

Internal verification artifact only: android/app/build/outputs/apk/debug/WAYiRUN-2026-09-28_16-54-07_EDT.apk, 40,240,876 bytes, SHA256 6338D8A38B14B89CD98DC7454CBFB3FBD29E28D20044EDEEB68A2446C17BCD10. the owner does not need to install it; preserve the combined phone handoff for Milestone 14.

Milestone 13 is locally complete. Physical announcement speech remains part of the combined acceptance build. Next bounded milestone: goal-audio priority and time/distance coalescing. Switch back to the implementation model before beginning Milestone 14, then pause again before its test phase unless the owner waives that split.

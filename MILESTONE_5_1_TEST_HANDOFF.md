# 1 Milestone 5.1 test handoff

## 1.1 Status and boundary

September 28: Milestone 5.1 is locally complete. Worker, Android, schema, emulator, browser and visual gates ran after the requested model switch. No deployment, remote migration, real provider query, physical-phone install, commit or push occurred.

Read REQUIREMENTS.md, TASKS.md, the then-current working guide, issue-plan Section 7 and worker/WEATHER_CONTRACT.md. Milestone 5.0 passed 142 Worker tests. Its weather lookup remains the source of truth. Current scope adds Android rendering/editor/persistence and the matching photo upload/public/export contract.

## 1.2 Implementation to review

- PhotoWeather.kt parses the bounded version-1 snapshot, retains its original JSON, prepares ASCII base64 for upload and uses the existing SyncApi. Owned finished runs wait for confirmed sync; logout, deletion and photo replacement invalidate responses. Weather lookup exceptions are optional failures.
- PhotoFlow.kt keeps weather JSON, options, editor generation and session identity across recreation. A prepared JPEG captures its source bitmap, editor generation, exact options and selected weather. Keep uses those captured values. Pending weather never sets the photo busy flag. Late results are discarded after Keep/exit/replacement/account change. Image decoding has a separate busy flag. Weather defaults selected when available, can be disabled, and retries manually or after pending run sync.
- The renderer keeps the stats band, removes the route rectangle, draws a thin stroke outline, preserves segment/time gaps, places emoji plus F/C at upper right and retains unobtrusive credit in the stats band. Landscape layout reserves space between weather and the route. Previously kept JPEGs are untouched.
- Room v8 adds nullable RunPhoto.weather through MIGRATION_7_8. Atomic keep checks owner, prior revision and deletion. PhotoSync sends the captured snapshot only when selected and validates revision, JPEG digest/size, options and returned weather before acknowledgement.
- Worker migration 0011 adds nullable run_photos.weather_json. The upload accepts legacy four-option photos or five booleans including weather. Selected weather requires a bounded X-Photo-Weather header matching the owner's run_weather snapshot; altered values/provenance/extra fields are rejected. Public data omits query coordinates and excludes weather with hidden photos. CSV preserves the full private snapshot. Public-page credit uses fixed provider/license links.

## 1.3 Build gates

From the repo root run `git diff --check`. From worker/ run:

```powershell
npm.cmd test
```

This compiles TypeScript, performs a Cloudflare dry-run and runs all Worker tests. Expected baseline plus four new cases: 146, but record actual counts. Photos/browser fixtures now apply 0010 and 0011. Check legacy uploads/migration, changed/reordered snapshots, absent cache, same-revision conflicts, private/public/hidden photo weather, and CSV reconstruction. All provider results are synthetic.

From android/ run:

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

KSP must generate and retain android/app/schemas/com.example.runningapp.storage.RunDatabase/8.json. Inspect nullable weather TEXT and unchanged photo/run foreign key against MIGRATION_7_8; do not hand-author the schema. Check the composable test injection, state-restoration test imports and coroutine cancellation paths. Record actual JVM count, lint errors/warnings and packaged APK paths/hash. If generated Windows output is locked, stop Gradle and move aside only the verified generated target within android/app/build; preserve sources and existing artifacts.

## 1.4 Emulator gate

Select an emulator explicitly; do not install to a physical phone. Confirm no real account session/key is active. Install the verified debug and test APKs there and use com.example.runningapp.debug.test/androidx.test.runner.AndroidJUnitRunner for the following classes:

- com.example.runningapp.photos.PhotoWeatherTest: five cases cover parsing/headers/receipt integrity, disabled/unavailable weather, pending sync and ownership, delayed logout/deletion/replacement, and atomic storage/replacement.
- com.example.runningapp.photos.PhotoEditorTest: five cases cover Keep while response is pending, restored editor snapshot without refetch, pending run sync then weather off, account change, and exit during response. It uses an isolated Room database, synthetic SyncApi and session reader; the Keep callback closes the injected dialog without background sync. Do not replace these with real network calls.
- com.example.runningapp.photos.PhotoRenderTest: three cases, including existing size/overlay checks, exact unchanged JPEG for disconnected route points (no rectangle or false connecting line), and stable weather on/off rendering at 600x1000, 1000x600 and 1000x1000.
- com.example.runningapp.storage.SyncMigrationTest and RunDatabaseTest: include new 7-to-8 migration preserving old JPEG/revision/sync flags with null weather, plus existing ownership, deletion and queue tests.
- com.example.runningapp.ui.RunScreenTest, com.example.runningapp.tracking.RunEntryTest, com.example.runningapp.ui.SettingsExperienceTest, com.example.runningapp.sharing.PublicationSyncTest and com.example.runningapp.sync.SyncEngineTest for relevant regressions.

Record executed totals and distinguish reruns from distinct cases. The existing long-suite RunEntry timeout has passed in isolation in prior milestones; investigate any new failure and do not silently suppress it.

## 1.5 Visual and interaction gate

PhotoRenderTest and PhotoEditorTest write evidence beneath the app's external files/milestone-5-1 directory. Pull it into android/app/build/verification/milestone-5-1/ and inspect with view_image. Inspect portrait, landscape and square JPEGs: route lower-right with no rectangle, gap preservation, clear emoji/F/C, intact stats, legible small attribution, no overlaps or clipping. Check both bright and dark photo backgrounds if the initial synthetic blue background leaves contrast uncertain.

Rerun the restored-editor screenshot case in light/dark themes and at 200-percent font. Its test theme follows the emulator appearance and filenames include UI mode/font scale. Inspect preview and lower controls, scrolling and Keep availability while weather is pending. Also inspect the actual settings attribution links at large font and restore emulator font/theme afterward.

Using synthetic callbacks only, verify choosing/replacing an image while a prior render is pending cannot Keep the previous bitmap; Weather toggling rapidly followed by Keep saves the final visible preview's exact snapshot; replacement/deletion during lookup closes or invalidates the editor. The prepared tests cover portions of these races; add a focused assertion if needed rather than claiming unexecuted behavior passed. Existing camera/picker/share-target behavior on the physical phone remains user acceptance.

For browser checks, start the existing local fixture on an unused port, for example `node scripts/serve-map-fixture.mjs 8798`, after the Worker build. Locate Playwright through workspace dependencies. Run `node scripts/verify-weather-ui.mjs <playwright-module-path> http://127.0.0.1:8798`, then the existing verify-route-ui.mjs and verify-publication-ui.mjs on that fixture. Inspect worker/build/verification/weather-public-390.png and weather-public-1365.png. Confirm loaded image, readable credit/license, no overflow, no external requests and no weather credit with hidden photo. Stop the fixture server when done.

## 1.6 Completion

After all gates pass, update this handoff, TASKS.md, REMAINING_WORK.md, issue-plan Section 7.9, FINAL_PASS_ISSUES.md and worker/WEATHER_CONTRACT.md with actual results and limits. Retain generated Room schema 8. Mark 5.1 complete only then. The next milestone is 11, final stabilization/handoff; deployment and physical-phone acceptance remain distinct. Keep the user's working phone APK in place.

## 1.7 Verified outcome

M11 resolved the qualification below: picker return was a test-window synchronization defect and passed repeatedly after correction; production code was unchanged. The Compose wrong-thread exception did not recur in the complete suite or six focused light/dark normal/200-percent restoration runs. Missing bottom-editor visual coverage was added and passed. See MILESTONE_11_TEST_HANDOFF.md.

Worker compilation and Cloudflare dry-run passed; all 146 tests passed with no failures, skips or cancellations. The Android gate passed 95 JVM tests, debug/test APK assembly and lint with zero errors and 20 warnings. Generated Room schema 8 contains nullable `run_photos.weather` and the unchanged cascading run foreign key. APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-28_07-45-03_EDT.apk`; SHA-256 `573EEA17F7BA1793A87358FF6A3D562BE0D18E684691E8ABCA217E612B8DBA47`.

The focused emulator run executed 61 cases: all 13 new photo weather/editor/render cases and 46 relevant regressions passed. The migration test initially exposed disabled foreign keys in `MigrationTestHelper`; enabling them in the test made the 7-to-8 preservation/cascade case pass. The existing `RunEntryTest.finishedRunSurvivesRecreationButNotWarmOrColdReopen` failed after the long combined run and again alone while waiting for the photo editor; this is not introduced by the weather path and remains a stabilization item. Dark 200-percent restored-editor and settings attribution checks passed after replacing an oversized full-scroll screenshot helper with viewport capture. Evidence under `android/app/build/verification/milestone-5-1/` shows readable portrait, landscape, square, light and dark output without route rectangles, false gap connections, clipping or overlap.

Weather, route and publication Playwright checks passed at 390 and 1365 pixels with no overflow, page errors or external requests. Visible public photos show location/provider/license credit; hidden photos omit weather and weather credit. `git diff --check` passed. Milestone 11 is next; phone camera/picker/share and real-network acceptance remain deferred.

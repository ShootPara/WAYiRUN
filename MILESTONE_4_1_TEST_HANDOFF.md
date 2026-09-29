# 1 Milestone 4.1 verification handoff

## 1.1 Status and boundary

Milestone 4.1 is complete. Android publication persistence, sync and sharing controls are locally verified. Milestone 4.0 previously passed 125 Worker tests and browser verification, so Milestone 4 is complete. No deployment, remote migration, paid calls, physical-phone install, commit or push occurred. The user's working phone build remains in place.

Read REQUIREMENTS.md, TASKS.md, the then-current working guide and worker/PUBLICATION_CONTRACT.md. Implement only bounded fixes found during this gate. Weather is Milestone 5 and remains outside this work.

## 1.2 Implementation to review

- RunPublication.kt and RunDatabase.kt: Room v7, separate observed server state and pending shared/photo preferences, exact durable request JSON, owner checks, atomic stale-response rejection, deletion cascade and explicit-import ownership transfer.
- Migration 6 to 7 preserves JPEGs, photo revisions and sync receipts; clears obsolete photo-public flags/cached URLs without inferring sharing intent from old Keep Photo choices. Server migration 0009 remains responsible for preserving previously public links. Android fetches actual publication state from the server.
- PublicationSync.kt: uses existing SyncApi and current account, waits for confirmed run upload, freezes operation ID/revision/body before sending, retries the same uncertain request, handles current-state replay, rejects malformed links/revisions, and clears conflicted sharing intent. Revocation can rebase and retry; a stale share cannot automatically republish after a conflict. A pending photo preference is applied before sharing. Superseding local intent invalidates acknowledgements from older requests.
- SyncWorker.kt: publication work runs after run upload/restore and before photo upload, with existing recovery scheduling. No background path opens a chooser or writes the clipboard.
- RunSharing.kt: summary and kept-photo controls for share/copy/unshare/photo visibility, pending status, and confirmed short-link delivery. Local runs retain intent until explicit import. Composition is keyed by run; leaving the foreground invalidates delivery. Account, intent and run existence are checked before delivery.
- PhotoFlow.kt/PhotoSync.kt: Keep Photo stays private; Save remains local; Share photo records intent before launching the local image chooser. Legacy cached photo links are no longer used and uploads always send X-Photo-Public=false. Photo sharing checks account, intent, lifecycle and deletion before launching.

## 1.3 Build and schema gate

From the repository root run `git diff --check`. From android/ run `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest --console=plain`.

The first build must generate `android/app/schemas/com.example.runningapp.storage.RunDatabase/7.json` through KSP. Inspect and retain that source-controlled schema. Do not hand-author it or omit it: migration tests require it. Verify the new table, nullable owner/preferences/request fields, types and run deletion foreign key against MIGRATION_6_7. Check compilation of the lifecycle Compose import and the new suspend/transaction methods. Report actual results/counts and resolve failures before marking complete.

If Windows locks generated build output, follow the established narrow generated-directory recovery workflow in the prior milestone handoffs. Do not delete or move unchecked paths or user source. APKs from this gate are internal verification artifacts.

## 1.4 Focused emulator gate

Use an explicitly selected emulator only. Confirm test accounts are synthetic and no real session/key is present before UI work; do not send tests to the deployed Worker. Run these classes after installing the verified debug/test APKs on that emulator:

- com.example.runningapp.sharing.PublicationSyncTest: 13 prepared cases covering private/photo-free behavior, independent visibility, unsynced share then unshare, exact retry receipts, remote unshare before replay, in-flight supersession, stale conflict, pending photo conflict, account change, deletion, local explicit import, failed fetch, expired session and database reopen (some cases cover multiple conditions).
- com.example.runningapp.sharing.SharingControlsTest: three prepared cases for commands, honest pending/conflict status and narrow double-font layout.
- com.example.runningapp.storage.SyncMigrationTest, RunDatabaseTest, and com.example.runningapp.sync.SyncEngineTest: schema preservation, existing data, owner isolation, import and deletion regressions.
- com.example.runningapp.ui.RunScreenTest and com.example.runningapp.tracking.RunEntryTest: private Keep Photo, preserved Save/Share actions, summary and external-action return behavior.
- com.example.runningapp.photos.PhotoRenderTest: existing JPEG/overlay behavior.

All new engine tests use a synthetic SyncApi. Record actual executed totals rather than adding prior milestone totals. Check the new database-reopen test cleanup and the existing migration tests for historical schemas as well as the new 6-to-7 case.

## 1.5 Interaction and visual gate

Inspect actual summary and kept-photo sharing controls in light/dark themes and 200-percent font, including scrolling and the wrapped photo-display label. Capture and inspect screenshots under android/app/build/verification/milestone-4-1/. Restore emulator appearance/font settings afterward.

Using synthetic API responses or focused test injection, verify real delivery wiring: Share and Copy persist intent before delivery; a confirmed short URL reaches the chooser/clipboard; cancelling the chooser retains intent; an unsynced/offline link remains pending with no invented URL or automatic later chooser. Local image sharing must persist intent and still launch offline. Save and Keep must never enqueue share intent. Verify a delayed response followed by New Run, foreground loss, account change, deletion or a newer Unshare cannot launch an old chooser or overwrite newer state. The prepared stateless control tests alone do not establish platform chooser behavior; add focused instrumentation if needed.

Review scheduler cancellation/races with foreground sync, lost responses followed by unshare, multiple pending runs, photo preference followed by sharing, and return from external activities. Server revocation of public endpoints was covered in 4.0; no live publication is needed. Physical chooser targets and offline-to-online behavior on the user's phone remain separate acceptance.

## 1.6 Completion

Room schema 7.json was generated by KSP and matches migration 6-to-7, including nullable intent fields and run deletion cascade. `git diff --check` passed with line-ending notices only. All 95 JVM tests passed; debug APK, instrumentation APK and lint passed with zero errors and 20 existing warnings. Windows repeatedly locked two generated Gradle directories; after stopping Gradle and moving only the verified generated test-results/package caches aside, the tasks passed.

The focused emulator gate covered 49 distinct cases. The initial 48-case run passed 47 and hit the previously documented long-suite timeout in `RunEntryTest.finishedRunSurvivesRecreationButNotWarmOrColdReopen`; both RunEntry cases then passed in isolation. A fourth sharing-control case was added and passed with the other three, validating clipboard content and exact Android chooser payload for a confirmed short URL. All 13 publication engine tests passed, along with migration, storage, sync, photo, summary and external-return regressions. Tests used synthetic accounts/API responses and made no deployed Worker calls.

Light and dark 200-percent-font screenshots in `android/app/build/verification/milestone-4-1/` were inspected: buttons fit, status is readable, and the photo-display label wraps without clipping. The initial dark screenshot lacked a themed test surface; the harness was corrected and regenerated without changing app UI. Internal APK: `android/app/build/outputs/apk/debug/WAYiRUN-2026-09-27_22-10-04_EDT.apk`, 40,126,188 bytes, SHA-256 `962C7D8B4EF6AABC6A28B246EDA33625B404B49024661B0FBFDDA8054B6A5D4E`. Physical chooser targets and real offline-to-online behavior remain phone acceptance. Milestone 5.0 is next; combined deployment and phone handoff remain deferred.

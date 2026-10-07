# 1 Milestone 4.0 test handoff

## 1.1 Status and boundary

Milestone 4.0 is complete. Worker publication and web controls are locally verified; Android durable sharing remains Milestone 4.1. No paid calls, deployment, remote migrations, Android build/change, phone install, commit or push occurred.

Read REQUIREMENTS.md, TASKS.md, the then-current working guide and worker/PUBLICATION_CONTRACT.md. Review publication.ts, migration 0009, photos.ts, browser.ts/index.ts, app/public-photo/export scripts and changed tests before execution. Normal photo saves/sync must remain private; no migration may revoke old public links. Old X-Photo-Public uploads must never change publication state.

## 1.2 Worker gate

Run `git diff --check` at the root. From worker/, run `npm.cmd test` (TypeScript, local deployment dry-run, all tests). Inspect individual failures and actual counts. Prior milestone baseline was 118 tests. Updated photos tests now publish explicitly. New cases cover no-photo publication, preserved legacy public/private migration, direct image hiding, unshare across short/long/data/image paths, stable reshare tokens, photo deletion independence, stale/replayed/concurrent operations, exact owner/auth/body boundaries, collision retry, deletion cascade and unshare during a pending public location request. Browser tests cover the real cookie bridge and CSRF; export tests accept only exact approved short/long URLs.

Run only local fixtures. No real key or provider lookup is required: the delayed location test replaces fetch. Existing account isolation, photo byte/hash, route privacy, deletion, export, coaching and API tests must remain green. If a fixture requires migration 0009, update that local fixture explicitly. Do not run live smoke/deploy scripts to address local failures.

## 1.3 Browser gate

After the build, use the existing local fixture server on an unused port, for example `node scripts/serve-map-fixture.mjs 8798`. Find the available Playwright module using the existing workspace tooling. Run `node scripts/verify-publication-ui.mjs <playwright-module-path> http://127.0.0.1:8798`, then the existing verify-route-ui.mjs against the fixture. Stop the fixture server when finished.

Inspect screenshots from worker/build/verification/publication-*.png using view_image. Verify desktop 1365px and mobile 390px: sharing status/actions, no text overlap or horizontal overflow, checkbox independent of sharing, correct visible short URL, chooser cancellation retains shared state, clipboard contains that URL, hidden image makes no visible broken image, and no-photo indoor run still shares. Public hidden-photo page must retain route/stats/locality. Direct long/short access after unshare must fail. Existing route checks protect segmented geometry and public location display.

Additionally inspect conflict and network-error UI with browser request interception: 409 refreshes current state and requires a new explicit action; failed requests do not claim success; navigating away or signing out during a delayed response must not update the next run/account or open a chooser. Synthetic fixture responses are UI evidence, not a substitute for the Worker security tests. Use bounded fixes and appropriate reruns, not assertion suppression.

## 1.4 Completion

`git diff --check` passed with line-ending warnings only. `npm.cmd test` passed TypeScript compilation, the local Cloudflare deployment dry-run and all 125 tests with zero failures/skips/cancellations. The existing route verifier and the publication verifier passed at 1365px and 390px. Inspected screenshots showed fitting controls, wrapped links without horizontal overflow, and hidden-photo pages retaining route, stats, locality and unobtrusive attribution. Browser interception verified 409 refresh without automatic replay, honest network-failure state, and no chooser or stale detail update after navigation during a delayed response.

Verification found and fixed one app defect: rendering the busy state overwrote a just-changed photo visibility checkbox before its request read could remain visible. The renderer now preserves the user's checked state during the in-flight mutation and reconciles after completion. The verifier was also corrected to use an explicit multi-page Playwright context and deterministic response waits. Milestone 4 remains incomplete until 4.1 Android sharing/offline-intent integration and its verification. Retain the current phone build and do not deploy this backend transition independently of the planned Android client update.

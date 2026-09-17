# WAYiRUN desktop history

## 1 Scope

The first Section 9.1 slice implements private Google sign-in, paginated history, loaded-run distance/time/weighted pace, unit conversion, and validated details with full/partial splits and captured settings. Route-map rendering now uses locally served Leaflet and OpenStreetMap tiles; see MAP_CONTRACT.md for gap handling, privacy and provider limits. Android, export, deletion UI, photos, coaching and production remain outside this slice.

## 2 Browser sessions

The development Worker serves the public shell and assets. Browser APIs use /web-api; native /api endpoints retain their bearer-only, Origin-rejecting behavior. Browser origin is pinned to the development URL. Foreign origins, cross-site requests and incoming Authorization headers are refused; no CORS headers are emitted. Browser mutations require exact Origin and X-WAYIRUN-Request: 1.

Challenge creation binds the one-use Google nonce to a five-minute __Host-wayirun-login cookie. Exchange requires that cookie and the matching body nonce, then invokes existing real Google verification. The one-hour session is set in __Host-wayirun with Secure, HttpOnly, SameSite=Strict and Path=/; no bearer token is returned to JavaScript. Duplicate cookies are rejected. Logout revokes the current session and expires its cookie. Browser run access is read-only and uses existing owner-filtered services internally.

## 3 Presentation and privacy

HTML, scripts, styles and API responses are no-store. CSP restricts scripts/connections and forbids framing, objects and base overrides; popup-compatible COOP supports Google sign-in. No private run data or tokens are put in localStorage, URLs or logs. Text uses textContent, not HTML interpretation. Only Google-hosted HTTPS profile pictures are rendered. In-memory private data is cleared on sign-out.

Google widget styling uses a fresh response-specific CSP nonce, attached to the Google client script and allowed only by style-src. Referrer-Policy is strict-origin-when-cross-origin, allowing Google's origin validation without sending private paths cross-origin. The sign-in container is capped at 260px (or the available narrow-screen width), with a 20px icon. Do not replace these checks with merely verifying that an iframe exists: inspect the real rendered button and verify that clicking it reaches Google's sign-in screen. Fixture sign-in tests do not exercise Google's actual styling or origin validation.

Detail retrieval verifies exact canonical manifest and chunk hashes, sizes, archive version, run/account identity, checkpoint-summary agreement, child ownership, routes and full/partial splits. A final detail read checks availability. Interrupted loads retain verified chunks only in tab memory. List dates use the viewer's zone; details use the run's stored zone. Totals describe the loaded set until pagination is exhausted. Indoor and missing-GPS records never gain fabricated routes.

## 4 Google setup and checks

In Google project wayirun-development, open Google Auth Platform → Clients → the existing Web application client. Add https://wayirun-dev.unopenedparachute.workers.dev under Authorized JavaScript origins and save. Leave the Android client unchanged. This button/callback flow needs no client secret or redirect URI. The user confirmed origin setup on September 16, 2026. The user confirmed successful real Google browser sign-in and supplied a screenshot showing the two saved runs on September 17, 2026.

Backend tests use bundled workerd/D1 and real test-generated signatures. Browser checks in scripts/verify-browser.mjs intercept fixture APIs; no deployed authentication bypass exists. Run with an installed Playwright module, optionally through PLAYWRIGHT_MODULE_PATH, and optional CHROME_PATH. Tests cover sign-in flow, history/totals/units, literal user text, corrupt chunks, details, narrow layout and logout. Screenshots remain under ignored build/verification. No test accounts or runs are created remotely.

## 5 References

[Google Web client setup](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid) and [Google button API](https://developers.google.com/identity/gsi/web/reference/js-reference).

## 8 Complete CSV export

Export all runs reads every history page and verifies each complete owner-scoped archive before downloading one UTF-8 CSV. Six record types and record_json preserve summaries and every retained detail. Cancel and sign-out prevent late downloads; any failed archive aborts the whole export. No new mutation endpoint or persistent browser cache is added. See EXPORT_DELETION_PLAN.md for format, size limits and separate deletion scope.

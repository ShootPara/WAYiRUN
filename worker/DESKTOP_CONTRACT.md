# WAYiRUN desktop history

## 1 Scope

The first Section 9.1 slice implements private Google sign-in, paginated history, loaded-run distance/time/weighted pace, unit conversion, and validated details with full/partial splits and captured settings. Map-provider selection and rendering are next. Android, export, deletion UI, photos, coaching and production remain outside this slice.

## 2 Browser sessions

The development Worker serves the public shell and assets. Browser APIs use /web-api; native /api endpoints retain their bearer-only, Origin-rejecting behavior. Browser origin is pinned to the development URL. Foreign origins, cross-site requests and incoming Authorization headers are refused; no CORS headers are emitted. Browser mutations require exact Origin and X-WAYIRUN-Request: 1.

Challenge creation binds the one-use Google nonce to a five-minute __Host-wayirun-login cookie. Exchange requires that cookie and the matching body nonce, then invokes existing real Google verification. The one-hour session is set in __Host-wayirun with Secure, HttpOnly, SameSite=Strict and Path=/; no bearer token is returned to JavaScript. Duplicate cookies are rejected. Logout revokes the current session and expires its cookie. Browser run access is read-only and uses existing owner-filtered services internally.

## 3 Presentation and privacy

HTML, scripts, styles and API responses are no-store. CSP restricts scripts/connections and forbids framing, objects and base overrides; popup-compatible COOP supports Google sign-in. No private run data or tokens are put in localStorage, URLs or logs. Text uses textContent, not HTML interpretation. Only Google-hosted HTTPS profile pictures are rendered. In-memory private data is cleared on sign-out.

Detail retrieval verifies exact canonical manifest and chunk hashes, sizes, archive version, run/account identity, checkpoint-summary agreement, child ownership, routes and full/partial splits. A final detail read checks availability. Interrupted loads retain verified chunks only in tab memory. List dates use the viewer's zone; details use the run's stored zone. Totals describe the loaded set until pagination is exhausted. Indoor and missing-GPS records never gain fabricated routes.

## 4 Google setup and checks

In Google project wayirun-development, open Google Auth Platform → Clients → the existing Web application client. Add https://wayirun-dev.unopenedparachute.workers.dev under Authorized JavaScript origins and save. Leave the Android client unchanged. This button/callback flow needs no client secret or redirect URI. Setup was explained to the user; completion and real browser sign-in are not yet confirmed.

Backend tests use bundled workerd/D1 and real test-generated signatures. Browser checks in scripts/verify-browser.mjs intercept fixture APIs; no deployed authentication bypass exists. Run with an installed Playwright module, optionally through PLAYWRIGHT_MODULE_PATH, and optional CHROME_PATH. Tests cover sign-in flow, history/totals/units, literal user text, corrupt chunks, details, narrow layout and logout. Screenshots remain under ignored build/verification. No test accounts or runs are created remotely.

## 5 References

[Google Web client setup](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid) and [Google button API](https://developers.google.com/identity/gsi/web/reference/js-reference).

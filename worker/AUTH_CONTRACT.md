# WAYiRUN — Account-session implementation contract

## 1 Bounded Milestone 5 slice

Implemented backend Google identity verification and D1 account/session storage. Android account UI and Google phone sign-in are implemented and user-verified. The server run transport is in RUN_STORAGE_CONTRACT.md; Android run synchronization and browser authentication remain subsequent work. The Worker accepts only the configured Web audience. There is no runtime verification bypass or configurable key endpoint.

## 2 Native HTTP contract

All responses are JSON and no-store. Requests with an Origin header are refused; cookies and client-supplied owner headers never authorize access. The later browser flow needs its own CSRF and session-storage design.

| Endpoint | Input | Success |
| --- | --- | --- |
| POST /api/auth/challenge | No body needed | 256-bit random nonce, expiresIn 300 |
| POST /api/auth/google | JSON containing only idToken and nonce | accessToken, tokenType Bearer, expiresIn 3600 |
| GET /api/account | Authorization: Bearer session token | account with id, displayName, pictureUrl |
| POST /api/auth/logout | Authorization: Bearer session token | signedOut true |

Use the nonce verbatim in Google Credential Manager's nonce option and in the exchange request. A token must be RS256-signed by Google's published keys, have an accepted Google issuer, the exact Web audience, a stable nonempty subject, matching nonce, expiry, and issue time no more than five minutes ago. Tokens with an unauthorized party or multiple audiences are rejected. The configured development Android client is explicitly allowed; do not relax signature/audience verification to make a test pass.

Only a valid, unexpired challenge can create a session. Session insertion and challenge consumption use an atomic D1 batch; a duplicate/concurrent exchange cannot mint another session. Lost successful exchange responses require a fresh Google sign-in challenge rather than replaying one. Exchange bodies are capped at 16 KiB; token strings at 12,000 characters. Unsupported methods return 405, malformed bodies 400, invalid identity/session 401, prohibited browser origins 403, and missing configuration or storage failure 503. Provider verification failures currently return generic invalid_identity, including key-service failure; automatic retry/UI messaging remains Android integration work.

## 3 Data and session lifecycle

Migration 0002 adds accounts, login_challenges, and auth_sessions. Google subject is unique and immutable; display name, picture, and email never determine ownership. Accounts expose an internal UUID and basic profile only. No email, raw Google token, or raw session token is stored. Optional picture URLs must be HTTPS; they are references, not fetched by this Worker.

Sessions contain only SHA-256 token hashes, owner, creation/expiry times, and optional revocation time. Random session tokens contain 256 bits of entropy and expire after one hour without sliding renewal. Logout revokes only the presented session. Expired rows are removed at successful login; expired challenge rows are removed when requesting a challenge. With no traffic, expired rows may remain but cannot authorize anything. No refresh token or account-wide logout is implemented yet.

The bootstrap schema version remains 1 so the earlier health-only Worker remains compatible with this additive migration. Health readiness checks the foundation; authentication failures independently fail closed if account tables/configuration are missing.

## 4 Verification and activation limits

Tests execute the actual bundled Worker in Miniflare/workerd with real local D1 and generated RSA signatures. Only the harness intercepts Google's fixed key URL. Cases cover signature/claim rejection, account separation and profile updates, session hashing, concurrent replay, challenge expiry, session expiry/revocation, input bounds, origin rejection, and storage failure. Tests never write remote account data.

Development Google Web and Android client IDs are now configured from the user's project wayirun-development. Tokens still require the exact Web audience; azp, when supplied, must equal that Web client or the explicitly configured Android client. An unrelated Android client remains rejected. Real Google/phone interoperability is separate from generated-key tests. The setup guide is ../GOOGLE_SIGN_IN_SETUP.md.

Every account/auth/run-storage request passes two Cloudflare rate-limit bindings before D1 access: 30 requests/minute per hashed CF-Connecting-IP, and 300/minute combined. Missing or failed limiters fail closed; exhaustion returns 429 with Retry-After 60. Forwarded/user-owner headers cannot choose the key. These limits operate per Cloudflare location and are approximate, not a global billing cap. No raw IP is written to D1 or logs. [Cloudflare rate limits](https://developers.cloudflare.com/workers/runtime-apis/bindings/rate-limit/).

## 5 Next slice

Server storage transport is implemented in RUN_STORAGE_CONTRACT.md. Next connect Android's validated archive, start-time ownership and durable queue, preserving pre-account records until the user explicitly imports them from settings. First-time offline runs remain local and eligible for deliberate later import. Integrate discard reconciliation before enabling uploads. Announcement selection and phone music testing remain deferred.

# WAYiRUN — Account-session implementation contract

## 1 Bounded Milestone 5 slice

Implemented backend Google identity verification and D1 account/session storage. Android account UI, run upload, account switching, and browser authentication remain subsequent work. The Worker accepts only the configured Web audience. There is no runtime verification bypass or configurable key endpoint.

## 2 Native HTTP contract

All responses are JSON and no-store. Requests with an Origin header are refused; cookies and client-supplied owner headers never authorize access. The later browser flow needs its own CSRF and session-storage design.

| Endpoint | Input | Success |
| --- | --- | --- |
| POST /api/auth/challenge | No body needed | 256-bit random nonce, expiresIn 300 |
| POST /api/auth/google | JSON containing only idToken and nonce | accessToken, tokenType Bearer, expiresIn 3600 |
| GET /api/account | Authorization: Bearer session token | account with id, displayName, pictureUrl |
| POST /api/auth/logout | Authorization: Bearer session token | signedOut true |

Use the nonce verbatim in Google Credential Manager's nonce option and in the exchange request. A token must be RS256-signed by Google's published keys, have an accepted Google issuer, the exact Web audience, a stable nonempty subject, matching nonce, expiry, and issue time no more than five minutes ago. Tokens with another authorized party or multiple audiences are currently rejected. Android client-party allowlisting must be added with real IDs during integration; do not relax signature/audience verification to make a test pass.

Only a valid, unexpired challenge can create a session. Session insertion and challenge consumption use an atomic D1 batch; a duplicate/concurrent exchange cannot mint another session. Lost successful exchange responses require a fresh Google sign-in challenge rather than replaying one. Exchange bodies are capped at 16 KiB; token strings at 12,000 characters. Unsupported methods return 405, malformed bodies 400, invalid identity/session 401, prohibited browser origins 403, and missing configuration or storage failure 503. Provider verification failures currently return generic invalid_identity, including key-service failure; automatic retry/UI messaging remains Android integration work.

## 3 Data and session lifecycle

Migration 0002 adds accounts, login_challenges, and auth_sessions. Google subject is unique and immutable; display name, picture, and email never determine ownership. Accounts expose an internal UUID and basic profile only. No email, raw Google token, or raw session token is stored. Optional picture URLs must be HTTPS; they are references, not fetched by this Worker.

Sessions contain only SHA-256 token hashes, owner, creation/expiry times, and optional revocation time. Random session tokens contain 256 bits of entropy and expire after one hour without sliding renewal. Logout revokes only the presented session. Expired rows are removed at successful login; expired challenge rows are removed when requesting a challenge. With no traffic, expired rows may remain but cannot authorize anything. No refresh token or account-wide logout is implemented yet.

The bootstrap schema version remains 1 so the earlier health-only Worker remains compatible with this additive migration. Health readiness checks the foundation; authentication failures independently fail closed if account tables/configuration are missing.

## 4 Verification and activation limits

Tests execute the actual bundled Worker in Miniflare/workerd with real local D1 and generated RSA signatures. Only the harness intercepts Google's fixed key URL. Cases cover signature/claim rejection, account separation and profile updates, session hashing, concurrent replay, challenge expiry, session expiry/revocation, input bounds, origin rejection, and storage failure. Tests never write remote account data.

The development deployment intentionally lacks GOOGLE_WEB_CLIENT_ID, so all auth endpoints remain unavailable. Before setting it, finish login request-rate controls, configure actual Google/Android client identities, and test on the phone. Do not claim Google service/phone interoperability from generated-key tests. The setup guide is ../GOOGLE_SIGN_IN_SETUP.md.

## 5 Next slice

Add retry-safe completed-run synchronization and owner-filtered queries under verified sessions, retaining original offline owners and preserving pre-account local runs. Resolve first-ever offline sign-in and deliberate migration of existing debug runs before connecting Android. Announcement-selector and phone music testing remain deferred.

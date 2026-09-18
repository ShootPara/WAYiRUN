# 1 Per-account OpenAI key storage

## 1.1 Scope and API

The native authenticated endpoint is `/api/account/openai-key`. GET returns only configured state, revision, service availability, credential-check state and last check time. PUT accepts exactly key, revision and operationId; DELETE accepts exactly revision and operationId. Mutation requests are bounded JSON. Both identifiers are UUIDs except the initial revision `none`. Browser cookies/origins, query strings, owner injection, unsupported methods and missing/expired sessions are rejected. There is no browser key-management endpoint or plaintext retrieval route.

PUT validates the submitted key with a bounded, ten-second GET to `https://api.openai.com/v1/models`, without redirect following. A success proves credential/model-list access only, not permission, quota or successful generation for a specific text or speech model. Restricted keys require Models read access for this check. Invalid credentials, permission denial, quota, rate limiting and provider/network failure receive distinct sanitized messages. Failed validation preserves the existing key. No text or speech generation is requested on Save. Reference: [OpenAI List models](https://developers.openai.com/api/reference/resources/models/methods/list).

## 1.2 Encryption and races

Migration 0005 stores AES-256-GCM ciphertext, a fresh 96-bit nonce, encryption version, last-check time and operation revision per owner. Account ID, format version and encryption-key version are authenticated additional data. The master keyring is a Worker secret named COACHING_KEYRING, with an active version and a maximum of four 256-bit base64 keys. Missing/invalid secrets prevent writes. No plaintext appears in database rows, metadata responses, logs or run archives/exports.

Revision-conditional writes and authenticated-session predicates run atomically in D1. A stale save cannot restore a removed/replaced credential; a revoked session cannot complete an in-flight save. Operation IDs allow recognition of a just-completed request without another provider call. After an unknown outcome, the Android UI requires refreshing metadata before a deliberate retry. Removal clears ciphertext, nonce, encryption version and validation timestamp; a minimal owner/revision/action/update marker remains to reject stale writes. Account removal cascades that marker too. Removal here does not revoke the external OpenAI key.

## 1.3 Android behavior

The AI coaching panel sits behind the existing gear, under the account/sync controls. Entry occurs in a password-masked secure dialog. Plaintext uses non-saveable transient Compose state; it is cleared on submission, dismissal, settings exit and account change. No key is persisted in phone preferences or saved instance state, and no background plaintext retry queue exists. A stored key is represented only by masked status. A different signed-in account receives its own metadata. Failed or uncertain mutations require a status refresh before retry. Confirmation precedes removal.

This slice does not generate coaching. Cedar/gpt-4o-mini-tts and current-plus-previous run context remain fixed for the upcoming generation integration. A successful key check does not claim that integration is already active.

## 1.4 Development secret operations

Initial development encryption version v1 was generated locally and uploaded to the development Worker using Wrangler's versioned secret command, then the guarded deployment script applied the additive migration and deployed the code. No secret was committed, printed or embedded in the APK. No Git commit/push was made for this deployment; local changes must remain available until the user requests backup.

A Windows DPAPI-protected recovery copy is stored outside the repository at `%LOCALAPPDATA%/WAYiRUN/secrets/wayirun-dev-keyring.dpapi`. It can be decrypted only under the original Windows user's protection context; it is not a portable cross-machine backup. Cloudflare retains the deployed secret. If both recovery sources are lost, users must re-enter their OpenAI keys; run data remains intact. Do not upload a plaintext keyring to GitHub or Drive.

Recovery must restore the existing protected keyring, not generate a replacement while encrypted rows exist. Never output the plaintext during recovery; pipe the decrypted value directly to the exact development Worker's secret command. Wrangler may require `versions secret put` when its latest version is not deployed. A normal code deployment must preserve that secret; verify its name after deployment.

Rotation procedure: add a fresh version while retaining old keys, switch active for new writes, re-encrypt existing rows using revision-conditional updates and fresh nonces, verify no rows reference the old version, then remove the old version. Keep a protected recovery copy through this process. Rotation is documented but no bulk rotation task is implemented in this slice. Database backups contain ciphertext and must be treated as sensitive; deleting a current row is not a promise to erase historical infrastructure backups.

## 1.5 Evidence

65 Worker tests pass, including nonce uniqueness, owner/version/ciphertext authentication, retained-key rotation reads, isolation, missing secrets, sanitized provider failures, body limits, revisions, idempotency and removal/logout during delayed validation. Android debug assembly and lint pass. Two emulator UI tests pass for masking, save/replace/remove, cancellation and account changes while a request is pending. The emulator initially slept through activity setup; tests now explicitly keep their own test activity visible. No phone was installed or launched and no personal OpenAI key was used during testing. Real-key validation remains a user check in the app.

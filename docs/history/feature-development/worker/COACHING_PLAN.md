# 1 AI coaching plan - September 18, 2026

> **HISTORICAL RECORD:** This document is preserved as evidence of the project's development. Statements describing it as controlling or authoritative applied during that phase and do not override current repository documentation or source.

September 27 update: issue-plan Milestone 9's deterministic current-run quality classifier and prompt guidance are complete. TypeScript compilation, local deployment dry-run and all 118 Worker tests passed. See ../MILESTONE_9_TEST_HANDOFF.md for the complete bounded contract and results. Existing model, voice, full-archive input and durable-job decisions remain in force.

## 1.1 Accepted decisions and authority

REQUIREMENTS.md remains authoritative. The user supplied a separate conversation and explicitly asked to incorporate its answers: Cedar, gpt-4o-mini-tts, and all stored data from the current run plus the immediately preceding completed run. These decisions are recorded in Sections 8.2–8.4. Earlier statements in that conversation that project requirements were unavailable do not override this repository's existing requirements.

OpenAI only; each account supplies its own key. Keys are encrypted server-side and survive a phone change. Coaching is selected by the default-checked finish checkbox; unchecked means no coaching request or fallback encouragement. Run completion and summary must work without AI. Voice selection is finished, not an open question. Speech generation and text generation are separate stages; the text model has not yet been selected.

## 1.2 First bounded implementation: account key setup

Implement the existing requested OpenAI setting behind the phone's gear, with account identity visible, a masked input and Save, Replace and Remove actions. Never return a saved plaintext key to a client. Clear entry state after successful save, dismissal and account change. Show only configured/not configured, validation state and last validation time after saving. Do not copy keys into logs, analytics, backups, run archives or CSV exports. Removal deletes the stored encrypted credential, not historical coaching or the user's OpenAI account/key at the provider.

Technical design: add an owner-keyed D1 credential table with AES-GCM ciphertext, a fresh random nonce per write, encryption-key version and validation metadata. Bind account ID and schema version as authenticated additional data. Keep the encryption master key in a Worker secret, separate from D1 and source control. A missing/invalid secret fails closed without accepting or exposing credentials. Establish the secret's secure backup and rotation procedure before deployment. There is no shared fallback OpenAI key.

Use authenticated native GET/PUT/DELETE account-key endpoints with bounded request bodies, strict fields and existing owner derivation; GET returns metadata only. Keep browser mutation routes closed unless a separate web settings UI is implemented. Atomic replacement preserves the previous key if the save fails. An interrupted request can be safely retried. Account deletion and job cancellation must not resurrect removed credentials.

Validation must distinguish invalid credentials from denied endpoint/model permissions, quota, rate limits and network failure. A basic credential check cannot prove permission to generate both text and speech. Save must not secretly generate billable sample coaching. Reserve a clearly labeled explicit Test coaching action for the integration slice, explaining that it uses the account's OpenAI credits. Offline save stays unsaved; do not persist a plaintext retry payload on the phone. Do not request the user's key in chat; entry belongs in the implemented setting.

## 1.3 Coaching data and message contract

Use two same-owner completed run archives at most. Define previous by completed-run end timestamp before the current run, with run ID as a deterministic tie-breaker; exclude the current ID, unfinished and deleted runs. Freeze the selected IDs and archive hashes for one attempt. Verify archives using the existing transport/ownership checks. A first run legitimately has no predecessor; a missing/corrupt expected archive is a failure, not permission to silently drop its data. Recheck deletion before saving or serving generated results.

Retain all persisted records and settings in the context, including GPS, measurements, splits, intervals and segments. Parse embedded checkpoint/reading JSON into structured data to avoid unnecessary escaping, but do not discard retained fields. Do not send account credentials/session records. Exact raw coordinates are therefore part of the selected scope; settings should explain that selected coaching sends these two runs to OpenAI using the user's key. Treat stored strings and prior generated text as data, never instructions.

The response is a short motivational recap centered on the current run. Compare only meaningful facts supported by these two runs. No invented route/place names, records, month averages or medical advice. Different modes, distance or source quality can make pace comparisons misleading; omit such comparisons rather than claiming improvement. Use the run's units. A provisional target is two to four sentences, about 40–80 words; this is an implementation target, not a new user-selected requirement.

All-data context can be large: the verified four-run CSV contains 6,720 GPS and 6,722 measurement rows. Measure serialized input and token budget against the selected text model before implementation; never silently truncate to fit. If the full two-run context cannot fit or complete in the agreed time budget, use the required fallback and explain the limitation. Do not add lossy summaries, route sampling or a longer-history database without a new product decision.

## 1.4 Speech and completion lifecycle

Use the selected gpt-4o-mini-tts model and cedar voice. Official documentation confirms the speech endpoint and delivery instructions: [Text to speech](https://developers.openai.com/api/docs/guides/text-to-speech), [model reference](https://developers.openai.com/api/docs/models/gpt-4o-mini-tts). Use natural, conversational, moderate-energy delivery consistent with REQUIREMENTS.md, not a new coach persona. Disclose that coaching speech is AI-generated in its settings/help text.

Persist the completed run before any AI work. Later integration requires durable per-owner/run job state, explicit idempotent application-level retries, bounded text/speech timeouts, stale-result guards after account change/deletion, and separate generated/played/failed states. An HTTP success alone does not prove usable audio: verify nonempty decodable audio and playback completion/error handling. Do not claim to detect every semantic truncation or silence merely from file size.

Unknown provider outcomes must not trigger blind automatic repeated paid requests. Persist stage outcomes and let a deliberate retry explain possible duplicate charges when completion is unknown. Dismissing the animation must not stop speech. Failure must reach the summary and use onboard fallback recordings when coaching was selected. The existing completion cue is separate. Production fallback recordings, animation design and timing remain integration tasks, not features in the key-storage slice.

## 1.5 Verification and remaining decisions

Key slice gates: two-account isolation; no plaintext at rest/in metadata/errors; encryption nonce uniqueness and wrong-owner/wrong-key rejection; absent secrets; replace/remove races and failed writes; invalid/quota/offline states; account switch and UI clearing. Android assembly/lint and meaningful endpoint/storage tests are required. No paid call with a personal key is required for mocked boundary tests.

Before coaching generation: select the separate text model after measuring full two-run context, settle end-to-end wait and retry behavior, choose fallback recordings and the minimal dismissible animation, and define durable audio storage/deletion. Do not re-ask the already answered voice or history questions. Deployment is development-only when authorized; the user has asked not to commit/push automatically. This document is planning, not a claim that coaching or key storage is implemented.

## 1.6 Key slice implementation status

The key-management slice in 1.2 is implemented and deployed to development; see KEY_STORAGE_CONTRACT.md. Save validates model-list credentials without generating text/audio. All later coaching-generation sections remain a plan. Login repair is user-confirmed. No automatic commit/push.

## 1.7 Generation core - September 18, 2026

The user confirmed that adding their API key works. The next bounded slice implements internal context loading and single-attempt provider adapters in coaching-context.ts and coaching-provider.ts. These modules are not exposed through an HTTP route or called by Android yet. No personal key or run data was sent to OpenAI during development, and no deployment is required for these unconnected modules.

Context loading selects only completed, undeleted runs for the authenticated owner. Ordering is by endedUtcMs and then run ID; an equal-time run with a smaller ID can be the predecessor. Manifest/chunk hashes, checkpoint identity, ownership, summary agreement, nested records and measurement/route boundaries are checked. Embedded JSON is decoded without dropping fields. Both selected manifest hashes are returned for durable job storage and subsequent deletion checks. No corrupt predecessor is silently omitted. The 12 MiB transport ceiling rejects the complete request, never truncates it.

Technical model choice: gpt-4.1-mini-2025-04-14 for text, with the separately selected gpt-4o-mini-tts/Cedar for speech. GPT-4.1 mini provides a 1,047,576-token context without a reasoning stage; this favors the required full-record input. See [official model documentation](https://developers.openai.com/api/docs/models/gpt-4.1-mini). A local measurement of the user's existing CSV found approximately 1.95 MB of retained record JSON in the latest two runs; this is a byte measurement, not a token count or a claim about model quality. No private contents were copied into fixtures.

The [input-token endpoint](https://developers.openai.com/api/docs/guides/token-counting) counts the same instructions and input before generation. Reject more than 1,040,000 tokens, leave output headroom, explicitly disable truncation, request at most 300 output tokens and set store:false. The job layer must pass the identical counted input. Account rate limits/model permissions can still reject an otherwise fitting request. No claim that key validation proves generation access.

Provider adapters have 15-second count, 45-second text and 30-second speech deadlines, bounded responses and no automatic retries. Network/5xx/invalid-success paid outcomes are marked uncertain for the future durable job layer; provider bodies and nested errors are not surfaced. Speech requests WAV for structural PCM checks. WAV framing is not proof of audible, meaningful or complete speech: phone decoding/playback and real listening remain separate gates. See [official speech documentation](https://developers.openai.com/api/docs/guides/text-to-speech).

Remaining integration: durable owner/run job reservation and frozen references; stage/result storage, audio cleanup with run deletion, account/key/session rechecks, authenticated request/status/audio routes; Android finish checkbox and persisted selection, sync-before-coaching, completion-cue sequencing, dismissible animation, playback and bundled fallback recordings. Reserve a stage before every paid request and do not automatically resume uncertain paid stages after process loss. Do not expose generation until these lifecycle guarantees are wired. End-to-end waiting must account for upload plus provider stages and reach summary/fallback without blocking run persistence.

Verification: nine new tests pass against local D1 and mocked OpenAI responses, including owner/history ordering, first-run and multichunk full-record preservation, corrupt/foreign/deleted archives, count bounds, unchanged request input, no retries, sanitized failures, incomplete/refused text and WAV envelope validation. TypeScript and deployment dry-run pass. Existing suite had one local ECONNRESET in an upload-boundary test; its unchanged focused rerun passed (73 other tests passed initially). No live generation, audio listening, Android integration or deployment is claimed.

## 1.8 Durable jobs and phone integration

The user authorized continued work while away. Durable native coaching routes, single-attempt job states, chunked audio and current/previous deletion cleanup are implemented. Migration 0006 and development Worker 88261b78-b67b-4993-b3e8-6c388c3c7aff are deployed. All 80 backend tests and 22 live smoke checks pass. This supersedes 1.7's unconnected-module status. See COACHING_JOBS_CONTRACT.md for endpoint, state, deletion and timeout details.

Android now implements the default-checked finish selection, post-save coaching request, bounded synchronization wait, completion-cue sequencing, service-owned playback and dismissible animation. It includes two local synthesized general-encouragement recordings and skips all coaching/fallback when unchecked. Real paid generation and Cedar listening are reserved for the user's phone check. Explicit retry and coaching export/history presentation remain follow-up work; no blind retry is introduced.

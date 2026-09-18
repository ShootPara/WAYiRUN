# 1 AI coaching plan - September 18, 2026

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

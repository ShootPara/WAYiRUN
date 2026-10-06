# 1 Development coaching jobs

## 1.1 Request and ownership

Native bearer-token routes are POST/GET /api/coaching/{runId} and GET /api/coaching/{runId}/audio. POST accepts only {operationId}, a UUID. The authenticated account owns the run and supplies its saved encrypted OpenAI key. Origin, Cookie, query parameters and owner injection are rejected. Completed uploads are required; the phone waits for its current run and pending account uploads/deletions to synchronize before requesting coaching.

One durable job exists per owner/run. The first request reserves it before any provider call. Concurrent or later POSTs return its existing state, even with another operation ID. Reusing one operation ID for another run is rejected. No endpoint currently retries a failed/unknown job; deliberate retry with possible duplicate-charge disclosure remains a later addition, not an automatic recovery path.

## 1.2 Stages and interruption

Stages are preparing, text_pending, speech_pending, ready, failed and unknown. Context references/hashes are frozen, token count happens before text, and each paid stage is durably marked pending before its single call. Text is retained before speech starts. Account session, key revision and run deletions are checked between stages and before committing audio. A key replacement/removal or logout does not allow an in-flight old request to advance to another paid stage.

The original HTTP request runs the bounded stages synchronously; the Android request uses a long read timeout and can cancel its connection. Lost connections never authorize replay. A pending receipt older than 180 seconds becomes failed (preparing) or unknown (paid stage). A late provider result cannot overwrite an expired receipt. Network failures, unexpected persistence failures after a paid stage starts, and uncertain provider outcomes are conservative failures, never hidden automatic retries. Requests already sent to OpenAI cannot be recalled by a subsequent deletion or logout.

## 1.3 Storage and deletion

Migration 0006 stores owner/run job metadata and audio in bounded 128 KiB D1 chunks, up to 4 MiB. It stores neither API plaintext nor an archive copy. Authenticated audio reads reconstruct individual chunks to avoid D1's aggregate response-size ceiling, then recheck job/session availability. Responses are private and no-store.

Deleting the current run cascades its job/audio. Deleting the previous run removes audio and message from dependent jobs, marks them context_deleted and retains their minimal receipt so another POST cannot regenerate and recharge. The existing run-deletion tombstone prevents recreation of a deleted current run. No shared key is available.

## 1.4 Phone completion behavior

The finish checkbox defaults checked and retains its value through pause/resume and UI recreation. Unchecked finish keeps the completion cue and makes no coaching request or fallback playback. Run persistence precedes coaching. The selected attempt is recorded locally before requesting, and a reopened completed run does not replay it after process loss. Upload waiting is bounded to 20 seconds; preparation including provider/audio download is bounded to 110 seconds. Slow/offline/failed work goes to onboard encouragement and summary. No automatic later coaching is queued for an offline finish.

The tracking service owns playback; tapping the animation only dismisses its visual. Audio waits for the normal completion cue. Playback requests temporary ducking focus, releases it on completion/error/cancellation, and has a 60-second bound. Invalid/failed generated playback can use a bundled recording. Account changes, run removal, new-run action and service teardown cancel the attempt. A process crash may omit speech; it must not repeat paid work or affect the saved run.

Two general encouragement WAV files are bundled under android/app/src/debug/res/raw. They were synthesized locally with the installed Windows System.Speech default voice; they are fallback recordings, not Cedar or an OpenAI call. Cedar remains the generated coaching voice. Settings disclose AI speech and sending complete current/previous run data, including GPS, using the account's API credits.

## 1.5 Verification boundaries

Backend tests use local D1 and synthetic provider responses. They cover simultaneous requests, operation reuse, owner isolation, chunked audio, deletion of current/previous runs, key removal/logout during text, stale stages, unknown outcomes and native request boundaries. No personal key is retrieved or billed in automated tests. The user reported the phone update tests OK on September 18; this is separate from the automated evidence.

## 1.6 Saved desktop coaching and export

GET /api/coaching-history/{runId} and its cookie-authenticated /web-api/coaching-history/{runId} bridge return a read-only owner-scoped snapshot. A completed run with no job returns coaching:null; missing, deleted or foreign runs return 404. The endpoint never generates, retries or expires jobs. Session/run/job state is rechecked after audio assembly. It exposes only the explicit result fields, never key revisions, session hashes or credentials.

Run details display retained recap text safely as text and offer manual replay of verified WAV bytes. Navigation and sign-out pause playback and revoke its Blob URL. Incomplete speech can still display its retained recap. Missing coaching does not prevent viewing the run. CSV version 2 includes metadata and all saved audio; see EXPORT_DELETION_PLAN.md 1.6. No new OpenAI call or charge occurs for replay or export.

## 1.7 Current-run quality evidence

Milestone 9 is complete. Verified current archives gain a derived quality object in coaching input only, with version, label, reasons and metrics. Stored archives and existing job results remain unchanged. The previous archive remains complete and is not the classification subject. The augmented input follows the same size/count and single-attempt job flow; suspicious recordings still receive requested coaching.

Test/incomplete thresholds are strictly under 90 seconds or 0.05 miles. Vehicle flags require 25 seconds of contiguous fast measured pace or GPS movement; gaps over 10 seconds and segment/source changes reset evidence. Isolated implausible GPS jumps are separate evidence and take label precedence. See ../MILESTONE_9_TEST_HANDOFF.md for numeric boundaries and Phase 2 checks. Provider instructions require plain, tentative acknowledgment, avoid suspicious performance praise and preserve supportive tone. No classification endpoint, schema migration or paid retry is introduced.

Verification passed TypeScript compilation, the local deployment dry-run and all 118 Worker tests. Provider calls were synthetic stubs; real generated tone and thresholds remain acceptance with user recordings.

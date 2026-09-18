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

Backend tests use local D1 and synthetic provider responses. They cover simultaneous requests, operation reuse, owner isolation, chunked audio, deletion of current/previous runs, key removal/logout during text, stale stages, unknown outcomes and native request boundaries. No personal key is retrieved or billed in automated tests. Real model access, meaningful recap quality, Cedar delivery and speaker/headphone behavior require a selected finish on the user's phone. Existing CSV export still describes the run archives; newly stored coaching results/audio are not part of that archive export and need a follow-up export contract.

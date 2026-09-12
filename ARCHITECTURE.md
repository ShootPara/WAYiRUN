# Running App — Architecture

Version: 0.1  
Status: Technical baseline; Android bootstrap implemented, verification tracked in TASKS.md  
FILE: <repository-root>\ARCHITECTURE.md (NEW)

## 1 Authority and scope

[REQUIREMENTS.md](REQUIREMENTS.md) defines product behavior. This document selects a small technical structure for implementing it. It does not resolve the remaining product choices in requirements Section 15. [DATA_MODEL.md](DATA_MODEL.md) defines persistence concepts; [TASKS.md](TASKS.md) defines implementation boundaries and verification.

The current working directory is `<repository-root>`. Keep the existing documents at its root. Create application directories only when their implementation milestone begins. No production service, credentials, remote repository, or deployment is created by this plan.

## 2 System structure

### 2.1 Android application

Use native Kotlin with Jetpack Compose, one application module, and a single run controller. The controller owns run state and calculations; screens display that state and send commands. Keep time, distance, and split calculations independent of Android APIs so they can be tested deterministically.

Use Room for durable run records and pending operations. Read the running screen and summaries from local state; persist run transitions before triggering cloud work. Room provides SQLite-backed persistence, and Android's offline architecture guidance supports local data as the UI's source. These are technical choices, not additional user-facing features. [Room](https://developer.android.com/training/data-storage/room), [offline architecture](https://developer.android.com/topic/architecture/data-layer/offline-first).

Start with Android API 28 as the proposed minimum to align with Health Connect availability. Pin compatible build dependencies during bootstrap; do not assume that installed SDK versions are the latest supported toolchain. [Health Connect availability](https://developer.android.com/health-and-fitness/health-connect/availability).

### 2.2 Cloud service and desktop web application

Use one TypeScript Cloudflare Worker for authentication, the run API, public pages, coaching requests, and access to stored images. Serve the desktop web application's static assets with the same Worker. Use D1 for account/run metadata and R2 for photographs and generated audio. Keep R2 private; the Worker checks ownership or publication state before returning an object. Cloudflare provides bindings for these resources and supports static assets alongside Worker logic. [Bindings](https://developers.cloudflare.com/workers/runtime-apis/bindings/), [static assets](https://developers.cloudflare.com/workers/static-assets/binding/).

Start the web interface with TypeScript and a small client application; select its UI library when the web milestone starts. Do not add a second backend, microservices, a message broker, or a generalized AI-provider framework.

### 2.3 Responsibility boundaries

| Component | Responsibility | Must not control |
| --- | --- | --- |
| Android run controller | Run state, active duration, distance, splits, goal events | Network availability |
| Android platform adapters | GPS, steps, media, camera, speech, Health Connect | Independent copies of run state |
| Local persistence | Durable run data and queued synchronization | Authorization for another account |
| Worker | Account authorization, cloud history, public access, AI invocation | Whether a local run can start or finish |
| D1 and private R2 | Durable account history and associated assets | Public access without Worker checks |
| Desktop web UI | History inspection, export, publication display, deletion | Direct database or secret access |

## 3 Run execution and recovery

### 3.1 State and time

Use the transitions `Ready → Countdown → Running ↔ Paused → Finished`; a zero countdown moves directly to Running. Persist the new run when tracking begins. Finish is valid from Running or Paused and is terminal. Duplicate pause, resume, and finish events are no-ops once their target state has been reached.

Measure duration with a monotonic clock rather than subtracting wall-clock timestamps. Store wall-clock start/end values separately for history. Accumulate time and distance only while Running. Reset step and GPS baselines when resuming so paused movement is not included. Snapshot units, stride length, and the selected goal at run start.

### 3.2 GPS and step distance

Use one ordered stream of accepted measurements. Each time interval has one distance source: GPS or steps, never both. A source change closes the previous interval and initializes the next baseline. The first accepted GPS point after a gap starts a new route segment; do not draw a line across an unrecorded gap or calculate a distance jump across it.

Indoor mode never registers a location listener. Outdoor tracking monitors GPS independently of network reachability. Record internal source metadata for correctness and testing; do not display estimated labels. Sensor quality thresholds and exact transition timing require measured tests before the tracking milestone is accepted.

### 3.3 Android service and permission behavior

Run tracking must outlive screen navigation. Use a foreground tracking service started from the visible app and request only the permissions required by the active capability. A location foreground service requires enabled location and granted location permission; fitness tracking can use the health service type with activity-recognition permission. Do not unconditionally start a location service when its prerequisites are missing. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types).

Step counters are not guaranteed on every device. Missing sensors and denied permissions must be detected explicitly. The product decision for a run with neither usable location nor steps remains open; software cannot supply measured distance in that condition. Lack of GPS or internet alone must still not prevent starting. [Step counting](https://developer.android.com/health-and-fitness/fitness/basic-app/read-step-count-data).

### 3.4 Persistence and restart

Commit state transitions and measurement checkpoints locally. On activity recreation, reconnect to the existing controller rather than start a second run. On process restart, load the persisted session; do not invent measurements for the missing interval. Recovery presentation and reboot behavior must be settled before claiming interrupted-run recovery is complete. A deliberate Android force-stop cannot be treated as uninterrupted tracking.

## 4 Media and spoken feedback

### 4.1 Shared pause state

The run controller receives both UI commands and linked-player playback changes. Any pause produces the same Paused state; do not attempt to identify its origin. Send pause to the linked player only when needed and emit a pause cue only on a real state transition. Ignore notifications that merely confirm the controller's own command. Detach run-resume handling after finishing.

Android offers access to active media sessions through an enabled notification listener. Playback callbacks report the new state. The selected YouTube Music session and its available transport controls must be tested on an actual phone. [Media sessions](https://developer.android.com/reference/android/media/session/MediaSessionManager), [playback callbacks](https://developer.android.com/reference/android/media/session/MediaController.Callback).

### 4.2 Music launch and speech

Control supported Android media transport actions. Do not use undocumented YouTube Music APIs. Opening a playlist and resuming an already active playlist are separate capabilities; verify both rather than assuming one implies the other. Playlist setup and absent-player behavior remain product decisions.

Generate milestone and state speech locally so it works offline. Package the requested fallback encouragement recordings. Prefer audio mixing/ducking for short cues, subject to device testing. If a cue or interruption actually pauses the music, the user's linked-pause rule still applies; do not introduce a hidden exception. Avoid repeated speech/media callbacks causing an event loop.

## 5 Accounts and cloud synchronization

### 5.1 Identity

Use Sign in with Google through Android Credential Manager. Verify Google identity tokens on the Worker, including signature, issuer, audience, and expiry; identify the account using the verified subject, not a client-supplied user ID or email. Android now directs Google sign-in integration through Credential Manager. [Android sign-in](https://developer.android.com/identity/sign-in/credential-manager-siwg), [backend verification](https://developers.google.com/identity/sign-in/android/backend-auth).

Use authenticated server sessions; protect web mutations against cross-site requests. Every private query, export, asset request, and mutation checks the authenticated owner. Public endpoints use an explicit field allowlist and never return account settings or credentials.

### 5.2 Offline ownership and synchronization

After an account is established, retain its local identity so an expired network session does not interrupt tracking. Queue completed runs under their original owner; require matching authentication when uploading. Account changes must not silently reassign unsynced runs. First-ever sign-in while offline is distinct from returning-user offline tracking and must be addressed before the account milestone ships.

Assign run IDs on the phone. Upload immutable completed run data with a retry-safe operation ID and payload hash. Repeating the same operation returns the existing result; conflicting data for an existing finalized run is rejected rather than silently overwritten. Schedule pending network work through WorkManager with bounded retries and backoff. WorkManager is for synchronization, not the live tracking clock.

### 5.3 Deletion and publication

Cloud deletion immediately disables public access and then removes run data and associated objects through retryable cleanup. Prevent an offline phone from re-uploading a deleted run. Use a minimal deletion marker containing only identity/version information, not retained run content; reconcile and remove local copies when the device reconnects. Do not claim immediate deletion from a disconnected device.

Publish only after the requested photo and run data are available and the user has selected publication. Recheck publication state on every public data/image response. Avoid caching private run data or public responses in a way that leaves a deleted run accessible. Offline publication completion and handling existing externally shared downloads remain distinct from server publication.

## 6 Coaching, achievements, and Health Connect

### 6.1 Coaching

The Worker retrieves only the authenticated user's encrypted OpenAI key. Use authenticated encryption with a unique nonce for each stored key and keep the encryption key in Worker secrets, outside D1. Record a key version for future rotation. Never log request authorization, decrypted keys, or full provider payloads.

Save the completed run before requesting coaching. Use its stable ID to avoid duplicate requests caused by retries; a timeout after a provider call is not proof that the call did not run. Store a coaching operation state and do not blindly retry an ambiguous billable request. Model, voice, historical selection limits, and timeout policy belong to the coaching milestone. No OpenAI calls are required for the local prototype.

### 6.2 Achievements

Keep definitions separate from presentation assets. Evaluate against run events and retained history; award idempotently and reveal at finish. The catalog and deletion/recalculation rules must be settled before this component is implemented. Offline awards based on stale history need reconciliation; do not assume an online-only award process satisfies immediate awards.

### 6.3 Health Connect

Write completed runs only with granted permissions and available Health Connect support. Use a stable client record identifier so retries do not duplicate exports. Keep export status separate from run completion and cloud synchronization. Route export requires the relevant route permission. [Exercise data](https://developer.android.com/health-and-fitness/health-connect/data-types).

## 7 Repository and verification boundaries

### 7.1 Planned layout

Keep the four planning documents and the existing working guide at the root. The bootstrap has created `android/`, root project instructions, and ignore configuration. Create `worker/` for the Worker and desktop assets when its milestone begins. Do not create empty future application scaffolds now.

### 7.2 Local environment observed

On 2026-09-11, Git and the Android Studio bundled Java 21 runtime were verified available. Android SDK platforms 35 and 36 and build tools are installed. `adb.exe` exists under the SDK's `platform-tools` directory. On 2026-09-12, the existing repository metadata was moved from the extra `RunningApp` subfolder into the project root, preserving its history and origin; the empty nested folder was removed. The implemented toolchain is pinned in `android/README.md`; its verification result is recorded in TASKS.md. No connected phone, cloud configuration, or remote network access has been verified.

### 7.3 Verification strategy

Use deterministic controller tests for pause exclusion, unit conversion, source transitions, goals, and duplicate events. Use Room integration tests for atomic finish and restart state. Use actual-device checks for screen-off tracking, GPS/steps, YouTube Music, headphones, permission changes, and Health Connect. Network and provider failures must be injected during the appropriate milestones. A successful local build alone does not verify a real run.

### 7.4 Deliberately deferred choices

Choose a map provider before map implementation, with both Android and desktop support and explicit account/cost requirements. Likewise defer the web UI library, exact dependency versions, full SQL migrations, API payload schemas, and deployment values to their bounded milestones. These choices do not block the independent run-controller work.

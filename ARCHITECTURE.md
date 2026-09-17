# Running App — Architecture

Version: 0.2
Status: Local debug tracking prototype implemented; actual-device verification tracked in TASKS.md
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

Indoor mode never registers a location listener. Outdoor tracking monitors GPS independently of network reachability and prefers usable GPS even when stride is configured. Record internal source metadata for correctness and testing; do not display estimated labels. Sensor quality thresholds and exact transition timing require measured tests before tracking accuracy is claimed.

### 3.3 Android service and permission behavior

Run tracking must outlive screen navigation. Use a foreground tracking service started from the visible app and request only the permissions required by the active capability. A location foreground service requires enabled location and granted location permission; fitness tracking can use the health service type with activity-recognition permission. Do not unconditionally start a location service when its prerequisites are missing. [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types).

Step counters are not guaranteed on every device. Missing sensors and denied permissions must be detected explicitly. REQUIREMENTS Section 5.10 specifies time-only operation with Distance unavailable and no invented distance when neither source is usable. The September 14 time-estimation proposal has not replaced that contract. Lack of GPS or internet alone must still not prevent starting. [Step counting](https://developer.android.com/health-and-fitness/fitness/basic-app/read-step-count-data).

### 3.4 Persistence and restart

The signin-settings1 activity preserves the original Android launcher splash and requests missing runtime permissions once at startup after loading local tracking state. Active/restored paused runs bypass that startup prompt. Activity state prevents repeated requests on recreation; onResume only refreshes tracking capabilities and never presents setup/settings. No setup overlay or Continue button is used. A fixed top-right gear toggles settings; editable values save immediately to local preferences, and active runs retain their captured settings. Pre-run mode and goal selectors/target remain on the main screen alongside the playlist-launch button and mode/Fallback indicator. Playlist entry and all additional settings stay behind the gear. Fallback reflects the current local-only run service; Google sign-in does not imply run synchronization is available. Music access opens the component-specific notification-listener settings on API 30+, with a general-page fallback. Android owns special-access approval and installation restrictions. Manual permission requests and system settings shortcuts live behind the gear.

Commit state transitions and measurement checkpoints locally. On activity recreation, reconnect to the existing controller rather than start a second run. On process restart, load the persisted session; do not invent measurements for the missing interval. Follow the paused recovery behavior below. A deliberate Android force-stop cannot be treated as uninterrupted tracking.

Milestone 3 implements the accepted recovery decision: restore an unfinished run as paused from its last durable checkpoint, close open intervals at that checkpoint, and reset measurement baselines. New intervals use a new clock epoch, so monotonic values from a previous boot are never subtracted from current values. The screen explains an interrupted active run and offers Resume or Finish. Completed runs remain terminal.

### 3.5 Local prototype implementation

The platform implementation is confined to `android/app/src/debug/`: Compose screens, a started foreground `TrackingService`, GPS and step-counter adapters, offline TTS/tone cues, and Room storage. `src/release/` retains the name-only shell. One channel serializes commands, sensor callbacks, and clock ticks; completed database transactions precede state publication and cues. UI recreation observes the existing service instead of creating another controller. Reopening the app refreshes sensor registrations only when capabilities changed.

GPS samples must have valid coordinates, reported accuracy at most 30 meters, age at most 10 seconds, ordered timestamps, and no implied speed above 12 meters/second. These are initial engineering filters, not device-validated accuracy claims. A GPS gap closes the source/route segment. Each new source needs a fresh baseline. A corrupt single-measurement distance above 100 km is ignored to bound split processing. Real-device step batching, drift, and GPS handoff checks remain required.

Location tracking is requested only outdoors. On API 34+, service types match available permissions: location for usable location access, health for activity-recognition access, and a declared special-use timer fallback when neither applies. The special-use declaration exists only in the debug manifest; production suitability must be reviewed in release preparation. No background-location permission or network permission is added. See [Android foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types).

The service holds a bounded, renewed partial wake lock during countdown/running and releases it on pause, finish, save failure, and destruction. Checkpoints are committed on each processed running tick (nominally once per second), accepted measurement, and state transition. Recovery can lose work after the last successful commit; it never fills the gap. Event sequence/goal state is committed before audio: a crash may omit a cue, but recovery does not replay it. If no installed offline TTS voice is available, a short tone supplies the state/goal cue; spoken output and mixing still require phone verification.

### 3.6 Phone feedback review — 2026-09-14

The tester reports functional phone passes, but quantitative accuracy and actual source handoff remain unverified. Basic state/goal speech and tone fallback exist in source; reported silence needs investigation. The current service keeps its ongoing notification on ordinary pause and releases sensors/wake lock; investigate the installed build before changing that lifecycle. See PHONE_TEST_REVIEW_2026-09-14.md for the proposed reliability plan. UI status indicators, estimation, and the revised photo sequence are proposals, not implemented architecture. Cloud remains absent; future status UI must not equate Internet access with server availability.

## 4 Media and spoken feedback

### 4.0 Implemented reliability follow-up — 2026-09-14

Debug `RunCueQueue` serializes each state/goal cue through speech or a timed tone; Android progress callbacks run on the main handler and handle asynchronous errors, stops, and a 15-second missing-completion timeout. Speech uses media audio attributes, matching tones and the activity's volume-button stream. No music focus/linkage policy is introduced. Offline voice selection excludes advertised uninstalled voices. Audio failure does not escape into the service's persistence-error path. Completed events remain persisted before playback; process destruction can still interrupt audio, and recovery does not replay events.

The foreground service requests immediate notification display and restores a notification when a recovered paused run is opened. Paused sensors and wake-lock release remain unchanged. These paths passed emulator regression tests; audible Moto G output remains unverified. See TASKS.md Section 6.8.

### 4.1 Shared pause state

The run controller receives UI commands and linked-player playback changes through the same service channel. The latest explicit user decision exempts all music pauses during cues, even intentional headphone pauses, and never auto-resumes that music. Direct run pauses still pause linked playback. `MusicLinkPolicy` arms only after playback while Running, ignores initial paused/unavailable playback, suppresses command echoes, and detaches on session replacement, permission loss, recovery, or finish. Media actions carry session generation and run ID so stale commands cannot affect a later run. A 2-second acknowledgement deadline detaches an unresponsive link instead of reversing the user's run command. The cue exemption includes a 600 ms callback-settling period after queued audio drains.

Android offers access to active media sessions through an enabled notification listener. Playback callbacks report the new state. The selected YouTube Music session and its available transport controls must be tested on an actual phone. [Media sessions](https://developer.android.com/reference/android/media/session/MediaSessionManager), [playback callbacks](https://developer.android.com/reference/android/media/session/MediaController.Callback).

### 4.2 Music launch and speech

Playlist setup uses a saved shared link in the existing local settings, with an explicit pre-run Open playlist action. Validate HTTPS playlist URLs on YouTube Music/YouTube hosts, retain only the playlist identifier, and dispatch ACTION_VIEW specifically to YouTube Music. No network fetch, undocumented API, browser fallback, or new permission is needed. Catch missing/blocked activity launches and keep run startup independent. Opening a playlist does not establish playback or arm linkage; the existing adapter still waits for observed playback during a run. The user's manual-open choice supersedes automatic playlist launch at run start.

Control supported Android media transport actions. The debug `MusicSessionAdapter` observes only YouTube Music sessions through user-granted notification-listener access; it does not read or retain notification contents. No player/access means standalone tracking, not a failed start. No undocumented YouTube API is used. Opening the saved playlist is distinct from controlling an active session and does not require notification-listener access.

State/goal speech uses the offline voice or tone fallback. The current debug build requests transient ducking focus, releases it across success/failure/cancellation/teardown, and does not change system volume. Focus denial skips the cue; focus loss cancels queued audio. The longer goal/completion speech has a bounded 60-second completion timeout. The foreground service remains eligible while terminal audio drains, with a completed-summary notification that is then removed. Notification prominence uses a DEFAULT channel without a competing sound; only an untouched prototype LOW channel is migrated, preserving explicit user settings. Onboard coaching recordings remain later work. Actual player ducking and notification ordering require phone verification.

## 5 Accounts and cloud synchronization

### 5.1 Identity

Use Sign in with Google through Android Credential Manager. Verify Google identity tokens on the Worker, including signature, issuer, audience, and expiry; identify the account using the verified subject, not a client-supplied user ID or email. Android now directs Google sign-in integration through Credential Manager. [Android sign-in](https://developer.android.com/identity/sign-in/credential-manager-siwg), [backend verification](https://developers.google.com/identity/sign-in/android/backend-auth).

Use authenticated server sessions; protect web mutations against cross-site requests. Every private query, export, asset request, and mutation checks the authenticated owner. Public endpoints use an explicit field allowlist and never return account settings or credentials.

The September 15 backend implements `worker/AUTH_CONTRACT.md`: Google RS256 verification, single-use challenges, hashed one-hour sessions, account profile/logout, and request-rate controls. Android Credential Manager sign-in uses configured development Web/Android clients; the user confirmed phone sign-in succeeded. Production identity remains unregistered. The completed-run transport is described in `worker/RUN_STORAGE_CONTRACT.md`; Android upload/import/discard synchronization is connected and sync1 phone checks are user-confirmed passed.

### 5.2 Offline ownership and synchronization

After an account is established, retain its local identity so an expired network session does not interrupt tracking. Queue completed runs under their original owner; require matching authentication when uploading. Account changes must not silently reassign unsynced runs. The user approved deliberate import of existing local records from gear settings. First-time offline runs remain local and eligible for later explicit import; previously established identity supports offline ownership. These Android ownership/import changes are implemented in sync1.

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

## 8 Implemented Android synchronization - sync1

Room v2 is the durable source of upload/deletion intent. WorkManager runs connected batches of at most four operations with per-operation retry deadlines, bounded consecutive failures and periodic recovery. A global mutex serializes engines; new changes replace scheduled immediate work while database intent survives cancellation. Every network request checks current account, session validity and current queue action. No bearer token is stored in WorkManager input or the queue.

Completed runs use deterministic versioned archives and resumable hashed chunks against the fixed development HTTPS endpoint. Tracking never waits for cloud availability. Explicit import is transactional and account-specific. Account changes/import are blocked during active runs; expired sessions preserve known ownership but cannot upload. Discard removes local metrics immediately, retains minimal deletion intent, and reconciles through server tombstones. Download/restore remains the next bounded slice.

### 8.1 Download and deletion reconciliation - sync2

Room v3 adds a per-account pull phase, cursor and bounded retry state. The worker runs uploads independently before restore so a malformed cloud archive cannot block new local uploads. Restore sweeps owner-filtered deletion pages before completed-run pages. Full sweeps repeat after 15 minutes; delayed Android scheduling may extend that interval. Fresh sign-in and Retry sync wake work. Pages and batches are bounded and cursors survive process death.

Binary staging uses the app-private no-backup directory, at most one archive per account. Cached chunks are rehashed on reuse. Exact canonical manifest bytes, completion receipt, chunk sizes/hashes, archive structure, run ID, account owner and summary must agree. Only then does one Room transaction insert all tables and the synced receipt; device-local autogenerated child IDs are regenerated. Existing/active local records and tombstones win. A final server detail request detects deletion during transfer; subsequent sweeps reconcile deletions racing that final request. No network result drives a tracking clock or settings overlay.

## 9 Desktop history foundation

The same development Worker serves a small HTML/CSS/JavaScript app without a new framework or service. Separate /web-api routes authenticate secure HttpOnly cookies, bind Google nonce exchange to a browser cookie and require exact-origin/custom-header mutations. Native bearer endpoints retain their original rules. The browser is read-only in this slice. Existing server owner filters protect list/detail/chunk access; client verification binds archive bytes to the receipt, owner and summary before presentation. History and verified chunks live only in tab memory and clear on sign-out. Desktop route details use locally served Leaflet 1.9.4 and OpenStreetMap Standard tiles for the small development site. Verified GPS segments remain separate across pauses/gaps; indoor/no-GPS records stay map-free. No paid plan, Android map UI or production provider capacity is established. See worker/MAP_CONTRACT.md.

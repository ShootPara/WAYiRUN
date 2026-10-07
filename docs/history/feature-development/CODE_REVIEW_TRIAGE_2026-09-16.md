# 1 External review disposition

Reviewed the supplied Gemini/Jules report against the current repository on September 16, 2026. It is advisory input, not a new product specification. No proposed Android refactor was applied. Several claims describe an older project state; the remaining risks warrant focused measurement or fault injection rather than an unrelated rewrite during desktop history delivery.

## 1.1 Findings and decisions

| Review claim | Evidence and disposition |
| --- | --- |
| Unlimited tracking queue inevitably causes OOM | `TrackingService.kt` does use an unlimited mixed command/sensor queue. Backpressure risk is real, but OOM has not been reproduced or measured. Conflating this queue could lose pause/finish commands and route samples. Retain behavior; measure queue growth and storage delays before choosing a command-preserving limit. |
| Room SQL blocks the main dispatcher | DAO operations are suspend functions. Generated `RunDao_Impl.kt` uses `performSuspending` and `performInTransactionSuspending`. The claimed synchronous SQL path is unsupported. Snapshot copying and JSON serialization before the DAO can still consume main-thread CPU and deserve profiling. |
| Recovery is broken because lastSaved remains set | The service advances lastSaved only after a successful save. Retaining that checkpoint is intentional. Sensor generation checks reject old callbacks. Secondary exceptions inside recovery/cleanup could still terminate the consumer, and queued user-command semantics after failure merit fault-injection tests. Do not blanket-clear commands or discard the recovery checkpoint. |
| GPS registration failure leaks the step listener | The retained step listener provides intentional outdoor fallback when GPS is unavailable. Its reference remains available to stop(), which unregisters it. Unregistering steps whenever GPS fails would remove required fallback. Cleanup exceptions are a separate hardening concern. |
| Reboot recovery creates negative intervals | `RunController.recover()` closes old intervals at the saved monotonic timestamp, advances the epoch, and restores unfinished runs paused. `checkpointRoundTripRecoversPausedAfterRebootWithoutCountingGap` covers a new boot clock starting below the old clock, interval closure and gap exclusion. Corrupt checkpoint handling is distinct from a demonstrated normal-recovery bug. |
| JSON checkpoint duplicates all child storage and doubles I/O | The checkpoint does not contain raw measurements or the GPS route. It supports deterministic controller recovery; relational rows support history/archive data. There is overlap in splits/intervals/segments and potential write amplification, but the claimed factor is unmeasured. Any schema change needs preservation/migration evidence first. |
| Release cannot compile because implementation is in debug | Release intentionally contains a separate minimal shell. `:app:assembleRelease` passed during this audit. Moving development integrations into production is not a compile fix and would expand the release boundary. The release shell is not a feature-complete release product. |
| OAuth credentials need plaintext-preference migration | `account/SessionStore.kt` already stores sessions using Android Keystore-backed AES-GCM in no-backup storage. Ordinary preferences and local owner IDs are not bearer credentials. Keystore does not promise immunity on a compromised/rooted device. Future per-user OpenAI keys belong to the server-side feature boundary in requirements. |
| Compose writes preferences during recomposition | Settings writes are in user callbacks; initial reads are remembered. Startup reads and pending preference flushes may merit profiling, but no jank measurement supports a ViewModel/DataStore rewrite here. |
| Centralize every unit conversion/formatter | Domain unit calculations and presentation formatting have different responsibilities. No concrete inconsistent output was demonstrated. Consolidate only duplicated presentation behavior supported by regression cases. |

## 1.2 Retained reliability follow-up

Before release preparation, run a bounded tracking reliability pass: inject delayed/failed saves and secondary cleanup/recovery failures; assert ordered pause/resume/finish handling, stale-sensor rejection and safe recovery; measure queue depth and checkpoint serialization/write cost over long synthetic runs. If evidence warrants code changes, preserve commands and route semantics, and cover data migration before changing persistence. This follow-up does not restart completed milestones or block the next desktop maps slice on speculative severity labels.

## 1.3 Verification and limits

Audit command `:app:assembleRelease :app:testDebugUnitTest --console=plain` passed. Existing JVM test tasks were up-to-date; this was not a fresh device test. The unsigned release artifact is archived outside the repository, leaving the existing dated sync2 phone APK as the sole handoff. No tracking implementation, phone data, permissions, or production configuration changed for this review.

The separate desktop slice passed 33 bundled Worker/D1 tests, browser fixture checks and 14 live smoke checks. Its development deployment is commit `83fbf98`. Google origin setup is user-confirmed; actual browser account sign-in remains a user verification boundary.

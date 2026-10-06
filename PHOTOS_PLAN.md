# 1 End-of-run photos

Historical implementation contract for TASKS 9.5. September 22 decisions in REQUIREMENTS.md Sections 10-11 and ISSUES_AND_QUIBBLES_IMPLEMENTATION_PLAN.md supersede the publication, route, and overlay behavior below. Keep no longer implies publication; new runs default private, any sharing attempt publishes, and photo visibility/unsharing are independent. Public runs need no photo. Legacy contracts below explain the existing migration input, not the target behavior.

September 28: PHOTO_SYNC_REPAIR.md supersedes the historical one-pending-photo queue description below. The matching backend/schema are deployed. Android Room v9 preserves images while adding failure status and fair bounded batches of five eligible run IDs, loading only one JPEG at a time. Current handoff evidence and the deployed-compatibility gate are in that repair record.

## 1.1 Authorized scope

Implement TASKS 9.5: optional camera/picker/skip, selectable time/distance/pace and actual GPS route, preview/retake/keep, save/share, Cloudflare storage and individual public pages. Existing runs and authentication remain intact. No commit, production deployment, or real-user publication during verification.

## 1.2 Lifecycle and privacy

Keep saves a rendered JPEG, strips source metadata and records the explicit publication choice (initially checked). Offline saves remain queued until the owning run is uploaded. Cancelling or retaking a draft leaves an existing kept photo unchanged. A replacement changes the image and publication setting together. No publication without a kept photo. Public output contains only the selected run's photo, actual route/location, splits, timing, distance and run settings; no account identifiers, coaching, credentials or other runs. Run deletion revokes public access and removes associated cloud records.

## 1.3 Implementation and verification

Use Android's system camera and image picker, bounded orientation-aware decoding, an app-private JPEG and FileProvider shares. Save through the system document picker without broad storage permissions. Keep image work off the main thread. Verify cancellation/error handling, route gaps, owner boundaries, deletion and upgrade migrations. Build/lint/unit tests and emulator checks supplement tomorrow's real-camera and running checks; they do not replace them.

## 1.4 Storage contract

One JPEG per run, bounded to 1,000,000 bytes and 1600 pixels on its longest side. Room v5 stores the kept image and pending publication choice in one row with a cascading run foreign key. The existing development D1 database stores the bounded JPEG in a corresponding cascading row; no new paid service or bucket is enabled. PUT is atomic and identical revision retries reuse the same public link. Each replacement revokes the old link. Incomplete uploads never replace the old photo. Only one pending image is loaded per sync pass; subsequent passes drain the queue. Images wait for the owning run's successful upload. CSV v4 includes PHOTO metadata and hashed, reconstructable PHOTO_IMAGE chunks. Original camera/gallery images are not uploaded.

## 1.5 Acceptance

System-camera front/rear switching and gallery providers require real-phone checks. Saved photos are available offline on the creating phone and through the authenticated website after sync. A restored run on another phone does not automatically download its photo in this version; the website retains it. Sharing a downloaded image produces an independent copy, just like CSV export; deleting the run revokes its hosted page, not copies already saved elsewhere.

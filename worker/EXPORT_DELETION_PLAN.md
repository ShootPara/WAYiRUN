# 1 Export and deletion plan - September 17, 2026

## 1.1 Authority and bounded sequence

The user accepted desktop maps and authorized continuing. The user explicitly chose one CSV file containing summary and detailed records. Deliver CSV export first, then desktop selection/deletion as a separate bounded implementation. Neither slice adds charts, period filters, achievements, photos, coaching, Health Connect integration, Android UI, production resources or new user accounts. Root/Android commits remain user-owned; scoped development Worker deployment is delegated.

## 1.2 Single-file export contract

One Export all runs action gathers every page of completed cloud runs for the signed-in owner, regardless of which history pages are loaded. It freezes the collected run-ID/receipt list before downloading archives. This is a collected export set, not an atomic database-wide time snapshot: uploads during collection may be included; uploads afterward require another export. Pending phone-only runs cannot be exported from the website.

Reuse authenticated archive retrieval and exact hash/owner/summary validation. Process archives sequentially. A missing/deleted/corrupt archive, expired session, cancellation or transport failure aborts the whole download with an actionable message; never silently omit a run or label a partial file complete. Navigation/refresh controls are disabled during export; Cancel and Sign out remain available. Sign-out cancels pending work, clears data and prevents a late download. No mutation or new server export cache is needed.

CSV v1 is UTF-8 with a BOM, comma delimiters, quoted cells and CRLF records. A fixed union header identifies export_version, archive_version, record_type, run_id and record_index, followed by readable summary/route/measurement/split/interval columns. Record types are RUN, GPS_POINT, MEASUREMENT, SPLIT, ACTIVE_INTERVAL and SOURCE_SEGMENT. One RUN row preserves the full stored run including checkpoint/settings; child rows retain every array entry in original order. record_json preserves each complete stored record, including null/boolean/nested values and original IDs. Reconstruction groups by run_id and record_type, orders by record_index, and parses record_json; readable columns are convenience projections, not substitutes for retained data. Empty arrays are represented by absence of child rows. Unknown archive versions/root fields fail rather than silently losing data.

Store distances in meters, durations/monotonic timestamps in milliseconds, absolute dates as UTC ISO strings, and the recorded timezone/units explicitly. Do not invent absolute GPS timestamps from monotonic clocks or join GPS gaps. Preserve full numeric precision; unavailable pace is blank. CSV text cells with formula-leading characters or leading control/whitespace before such characters are prefixed with an apostrophe; trusted finite numeric values remain numeric. Exact text remains available in record_json, whose object encoding cannot begin with a formula. CSV quoting alone is not formula protection; see https://community.owasp.org/attacks/CSV_Injection.

Exclude credentials, sessions, account tables and local sync retry bookkeeping. All persisted run/archive data is included, including internal record/ownership IDs. No private data is uploaded elsewhere or stored in browser persistent storage. Separate 128 MiB limits on encoded CSV output and collected receipt metadata (actual browser memory use is higher) fails clearly without a partial download; never truncate. A future streaming implementation can raise this limit without changing CSV v1. Filename: WAYiRUN-all-runs-UTC-timestamp.csv.

## 1.3 Deletion policy and later implementation

Reuse the existing atomic owner-scoped DELETE and cascading archive/chunk removal. Retain only owner ID, run ID and deletion timestamp indefinitely while the account exists: offline devices have no maximum reconnect delay, so time-based purging would permit resurrection. These markers contain no run metrics, GPS, settings, photos or coaching. Account-wide deletion/backup-retention policy is a separate future scope; do not promise physical erasure from infrastructure backups via a row DELETE.

Desktop UI will offer explicit row selection and Delete selected, followed by an app-styled confirmation naming/counting selected runs, Cancel and a destructive confirmation button. Selection applies only to explicitly selected loaded rows, never hidden pages. Forward DELETE only for exact run-ID routes, with authenticated browser cookies, exact Origin and the custom CSRF header. Reject other mutations and nonempty bodies. Perform sequential idempotent deletions; remove only acknowledged successes from UI/totals and retain failed selections with honest partial-result reporting. Unknown outcomes can be safely retried. Testing must use disposable fixtures; never delete the user's existing runs to verify the UI.

Phones reconcile via the existing deletion feed after reconnecting with valid authentication. Do not claim an offline phone is already cleared. Downloaded CSVs and user-shared external files cannot be recalled. Future photos/audio/publications must revoke public access immediately and queue durable retrying object cleanup; these objects do not exist today.

## 1.4 Future Health Connect policy

When Health Connect export is implemented, WAYiRUN deletion also queues deletion of its own exported records by stable client record IDs. Do not remove records belonging to other apps. Reconcile every participating installation; unavailable devices or revoked permissions remain pending until they can perform cleanup, with an honest status. Do not recreate a tombstoned run to make cleanup easier. Preserve only required external cleanup identifiers until acknowledged. No Health Connect code is added in the current slice. Android guidance supports deletion by record/client record ID and removing app-deleted data from Health Connect: https://developer.android.com/health-and-fitness/health-connect/delete-data and https://developer.android.com/health-and-fitness/health-connect/sync-data.

## 1.5 Verification gates

Export: independent CSV parsing/reconstruction of all six record types; commas, quotes, multiline text, Unicode, formula-like text, nulls and precise numeric values; empty history; more than one history page; corrupt/missing archive; cancellation/sign-out with delayed work; memory-budget failure; no partial or foreign-account download. Inspect the real browser download, not merely a success message. Existing owner-isolation and browser-session tests must remain green.

Deletion slice: owner isolation, CSRF, forbidden methods/bodies, repeated and concurrent deletion, stale upload/restore rejection, failed/unknown/partial results, selection scope, accessible confirmation/cancel and recomputed totals. Existing phone deletion tests remain evidence for the protocol; real multi-device timing remains separate.

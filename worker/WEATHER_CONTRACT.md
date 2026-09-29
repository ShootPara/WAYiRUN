# 1 Run weather contract

## 1.1 Milestone and status

Issue-plan Milestones 5.0 and 5.1 are locally complete. Compilation, Cloudflare deployment dry-run and all 146 Worker tests passed September 28, along with Android build/lint, Room schema 8, focused emulator and visual/browser gates. Migrations 0010 and 0011 are local source only, not applied remotely. The weather lookup endpoint never mutates publication or photos.

## 1.2 Provider decision

Checked September 27, 2026 against primary sources:

- [Terms](https://open-meteo.com/en/terms): free API is non-commercial, with fewer than 10,000 daily, 5,000 hourly and 600 minute requests. No availability guarantee; reassess before commercial distribution.
- [Forecast documentation](https://open-meteo.com/en/docs): explicit start/end dates, GMT UNIX hourly timestamps, Celsius temperature and WMO weather codes; recent past dates supported.
- [Historical documentation](https://open-meteo.com/en/docs/historical-weather-api): archive endpoint supports historical model/reanalysis data; ERA5 can lag five days.
- [Provider attribution instructions](https://github.com/open-meteo/open-meteo#data-license): CC BY 4.0 credit, license link and indication of changes, with attribution alongside displayed data.

No new key, paid account or dependency. This is modeled weather near the run start, not a claim of an on-site instrument observation. Forecast endpoint for starts less than seven days old; archive endpoint for seven days or older. One UTC day only, both dates identical. Missing historical hours do not fall back to current conditions or another provider. Model selection remains provider default and successful results are frozen.

## 1.3 Private API

`GET /api/weather/{run UUID}` uses the existing native bearer session and run rate limits. Reject Origin/Cookie, extra query parameters and other methods. Caller cannot supply coordinates, provider URL, time or owner. Require a completed, owned, non-deleted synced archive; verify its manifest/chunk hashes before first lookup.

Success or ordinary provider unavailability is HTTP 200:

```json
{"weather":null,"reason":"provider_unavailable","retryAfter":1790529000000}
```

When available, `weather` is the version-1 snapshot below; `reason` and `retryAfter` are null. `retryAfter` is an absolute UTC epoch millisecond timestamp, not seconds/duration. Reasons: `no_recorded_weather_location_or_time`, `retry_later`, `provider_unavailable`. Missing/foreign/unsynced/deleted run: 404; expired/revoked/missing session: 401; rate limits: 429; invalid archive or internal/configuration failure: 503 with no diagnostics. All responses are no-store. Android must treat any failure as optional weather unavailable, not a photo failure.

Snapshot fields:

| Field | Meaning |
| --- | --- |
| version, source | 1, open-meteo |
| latitude, longitude | Query coordinates rounded to 0.1 degree; private provenance only |
| observedUtcMs | Run-start UTC hour floored, exact provider hour required |
| endpoint | forecast or archive |
| weatherCode, emoji | Validated WMO code and fixed condition glyph mapping |
| temperatureC, temperatureF | Numeric temperatures rounded to one decimal, F converted from source C |
| retrievedUtcMs | Lookup request time in UTC epoch milliseconds |
| attribution | Fixed label, provider URL, CC BY license URL and transformation notice |

Use the first recorded route point of an OUTDOOR archive, not the viewer's location or a later convenient point. Indoor/no GPS/invalid/future start returns unavailable. Round before sending anything to the provider. Floor UTC time rather than interpreting the device timezone or substituting a nearby available hour. Accept only exact UNIX timestamp, Celsius units, known weather code and finite bounded temperature. Provider-returned coordinates, extra fields and strings never enter the snapshot.

## 1.4 Bounds, cache and lifecycle

The provider URL is hardcoded HTTPS, with redirects rejected. The whole lookup, including body consumption, has a three-second deadline and 32 KiB response cap. Request two hourly variables for one location and one day. Do not log URLs, coordinates, archives or provider bodies.

Migration 0010 adds owner/run-scoped run_weather with cascading deletion and a separate database-wide ten-second request gate. This caps this deployment at approximately 8,640 requests/day, 360/hour and six/minute. Multiple independent deployments or other users of a shared outbound IP are not coordinated by this gate; review combined usage before rollout.

Successful snapshots do not expire or refresh on viewing. Provider failures retry no sooner than one hour; an in-flight reservation or busy global gate returns a 30-second retry hint. No automatic background retry loop. Per-attempt tokens prevent late results overwriting newer results. Session and tombstone checks occur before provider access, atomically on result persistence, and before delivery. A logout may leave an empty reservation until retry, but cannot persist/deliver the in-flight snapshot. Deletion removes rows via foreign keys; tombstones block writes even before cleanup.

## 1.5 Milestone 5.1 integration guardrails

Request only from the open editor for a synced run. Keep/Save/Share must never await lookup. Hold one returned snapshot through editor recreation; preserve it with the exact kept photo revision and render preview/JPEG from the same values. Ignore responses after Keep, editor exit, account change or replacement. Weather off means no rendered weather; old kept photos stay unchanged. Unsynced/offline remains unavailable, with bounded retry after sync while the editor remains open.

Extend the photo option/header/metadata contract and persistence together in 5.1, accepting legacy four-option uploads. Validate incoming snapshots against the owned run's stored snapshot rather than trusting arbitrary client attribution/coordinates. Public metadata may include stable weather summary/provenance but must omit latitude/longitude and private owner/run archive details. Exports either deliberately include stable fields or document omission. No fetching on public views, image loads, photo sync or publication.

The provider's current display guidance does not clearly permit settings-only credit for a standalone JPEG. Conservative implementation: unobtrusive small credit in the existing photo stats band when weather is enabled, provider/license/changes details in settings, and linked small-print credit on public pages displaying weather. Carry provider and CC BY reference on standalone exported weather images so credit travels with the image. This uses the accepted requirement's provider-terms exception; do not silently claim settings-only attribution satisfies it. Final legibility/layout belongs to 5.1, not this backend milestone.

## 1.6 Photo snapshot transport

Milestone 5.1 adds nullable weather JSON to Android Room v8 and Worker migration 0011. Existing records keep null weather and unchanged JPEG/revision/sync flags. New photo options contain time, distance, pace, route and weather booleans. Legacy four-boolean uploads remain valid. Weather defaults on when available in a new editor; the user can switch it off independently.

When weather is selected, X-Photo-Weather carries base64-encoded UTF-8 snapshot JSON (maximum 4096 ASCII header characters). The Worker compares every field with the authenticated owner's stored run_weather snapshot, independent of JSON key order, then stores the server copy with the photo revision. Missing, altered, extra or unsolicited weather is rejected. When weather is off/unavailable, the header and snapshot are omitted. Photo sync never fetches weather or rerenders a JPEG; acknowledgement checks the image hash/size, options, revision and snapshot. Publication remains independent.

Private photo metadata and CSV photo records include the complete selected snapshot under weather. For legacy/weather-off photos, this field is absent. Public /data includes weather only when that photo is visible, with latitude/longitude omitted. Public viewers reuse the photo snapshot; they never call the weather provider. The image has small credit/license/transform text in the stats band, Android settings link provider/license, and public pages add out-of-the-way linked credit.

## 1.7 Verification boundary

See MILESTONE_5_0_TEST_HANDOFF.md for completed lookup evidence and MILESTONE_5_1_TEST_HANDOFF.md for the pending photo integration gate. Provider tests use synthetic coordinates/responses. No live personal-location request, phone install, deployment, remote migration, commit or push is part of this work. Real provider availability and physical rendering remain separate acceptance.

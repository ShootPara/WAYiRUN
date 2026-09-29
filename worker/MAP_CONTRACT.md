# 1 Route graphics and public location

## 1.1 Rendering contract

September 22 decisions replace basemaps with local SVG route graphics on private and public pages. No tile requests, map SDK, map library, or map attribution is needed for drawing the user's recorded geometry. The existing /map.js path remains for compatibility but serves only the route renderer. Routes are fitted automatically; the old Fit route control is hidden. Missing intervals, pauses, and step-only sections remain disconnected. Single GPS points remain visible; indoor/no-GPS runs show an honest empty state. Raw owner archives and CSV exports remain unchanged.

## 1.2 Public geometry and location

Public data contains normalized segmented geometry, with no raw latitude/longitude, geographic origin, or scale. City/state labels are separate metadata derived from the first valid recorded GPS location. A route can still be visually recognizable; normalization is not a claim of route anonymity. Public output excludes owner IDs, coaching, and other runs.

## 1.3 Reverse-geocoding provider

Selected service: Nominatim reverse API, configured by server variable LOCATION_LOOKUP_URL. Initial endpoint: https://nominatim.openstreetmap.org/reverse, format=jsonv2, zoom=10, addressdetails=1, accept-language=en. Remove/empty the variable to disable new lookups, or replace it with a compatible endpoint without changing the Android app.

Primary docs checked September 22, 2026: https://operations.osmfoundation.org/policies/nominatim/ and https://nominatim.org/release-docs/latest/api/Reverse/. Public-service policy requires application identification, attribution, caching, and at most one request per second across the application. This implementation uses one shared D1 gate allowing at most one attempt per ten seconds and a three-second request/body timeout, with an identifying User-Agent. No bulk job, autocomplete, background scanning, or client-side provider request is implemented. Recheck terms/capacity before release.

A published-run data request lazily resolves uncached location, including legacy public runs. Results belong to the run independently of its photo. Successful city/state values are retained; failures retry no sooner than 24 hours, on a later page request. A busy global gate immediately returns the generic label. Provider bodies are bounded to 32 KiB and discarded after extracting locality fields. Only the first coordinate is sent, without user identity, full route, or run timestamps. Locality is based on the provider's closest suitable administrative result, not a guarantee of surveyed municipal boundaries.

Provider data is licensed under ODbL; preserve its attribution and review share-alike obligations before expanding cached extracts or redistributing a location database. No location database export is added in this milestone.

## 1.4 Attribution and failure behavior

Place the linked text "Location data: OpenStreetMap contributors" in small print at the bottom of the public page when a resolved label is shown. The link is https://www.openstreetmap.org/copyright. No attribution overlays the route or running UI. Missing region uses the available city; missing city/provider failure uses "Outdoor route recorded". Indoor/no-GPS runs do not query the service.

Migration 0008_run_locations.sql adds owner/run-scoped cached labels and a shared request gate. Run deletion cascades cached labels; deletion markers prevent late lookup persistence. Public authorization is rechecked after a lookup before returning data. No remote migration or deployment is authorized by this milestone.

## 1.5 Verification

Use synthetic local fixtures and stubbed provider responses. Test geographic normalization, date-line handling, gaps, singleton/polar routes, cached/missing/failed locality, global throttling, and deletion races. Browser checks cover desktop and 390px mobile, private and public routes, and absence of external requests. Real user coordinates must not be used in automated provider checks.

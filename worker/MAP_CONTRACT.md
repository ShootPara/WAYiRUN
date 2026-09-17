# 1 Desktop route maps

## 1.1 Scope and provider

Run details render saved GPS sections with Leaflet 1.9.4 and OpenStreetMap Standard raster tiles. Leaflet is pinned and served locally with its BSD license; no third-party JavaScript CDN is required. The current development-only site uses https://tile.openstreetmap.org/{z}/{x}/{y}.png with visible attribution, browser caching, a valid origin Referer, requests for the current viewport only, and no offline download or prefetch feature. Do not drive automated tile scanning or pan/zoom tests against OSM; local browser fixtures substitute synthetic tiles.

Provider terms checked September 17, 2026: https://operations.osmfoundation.org/policies/tiles/ and https://leafletjs.com/download.html. OSM is best-effort community infrastructure, not a production capacity commitment. Reassess provider/capacity before public release. The same tile format is usable by an Android renderer if a later approved Android map surface needs it, but native identification/cache requirements must be implemented then. No Android map or SDK is added now.

## 1.2 Privacy and configuration

Only authenticated, hash-verified run archives reach geometry preparation. Owner filtering and browser session/CSRF rules remain unchanged. Routes stay in tab memory and are drawn locally; there is no route-upload, geocoding, analytics or static-map API request. Tile requests necessarily reveal the viewed map area, client IP and site origin to OSM, but carry no WAYiRUN account, run ID or private session cookie. No private coordinates appear in a share URL or server logs. The CSP adds only the exact tile image host. Tile URL/max zoom/attribution are centralized in map.browserjs and can be replaced by a web deployment without a phone update.

## 1.3 Geometry and lifecycle

Validate segment identities and source types, completed segment bounds, increasing stored row IDs, finite coordinates and ordered monotonic time within each segment. A new segment always starts a separate line. Gaps over ten seconds also split lines, matching the Android GPS freshness boundary. A reboot clock reset is allowed only in a later segment. No sorting, smoothing, map matching, estimated distance or invented joins are performed. Existing saved metrics never change.

Single points are markers without a fabricated line. Indoor and no-GPS records stay map-free. Date-line longitudes unwrap for local bounds; valid polar routes beyond Web Mercator's latitude range get an explicit map-unavailable note without changing saved data. First/last markers mean first/last recorded GPS point, not necessarily run start/finish.

The map offers zoom and Fit route, disables scroll-wheel zoom to preserve page scrolling, and resizes with its container. Tile/library failure preserves run details. Returning to history or signing out removes the map, geometry and handlers; a generation guard prevents a late library load from drawing a prior account's route.

## 1.4 Verification

Pure route tests cover pause/fallback separation, internal GPS gaps, reboot timing, indoor/empty/single-point routes, corrupt/out-of-order positions, date-line bounds and projection limits. Worker tests verify asset types, method handling and CSP. scripts/serve-map-fixture.mjs is a loopback-only browser fixture with synthetic archives/tiles; it is never imported into the deployed Worker. Use ?case=tile-failure for missing tile behavior. It cannot authenticate against the real service and has no production route.

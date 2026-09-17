# 1 Leaflet 1.9.4

Pinned upstream browser distribution, served from WAYiRUN rather than a third-party script CDN. BSD-2-Clause license is in LICENSE. Downloaded from https://unpkg.com/leaflet@1.9.4/ and verified against the official hashes at https://leafletjs.com/download.html on September 17, 2026.

- leaflet.browserjs (upstream dist/leaflet.js): SHA-256 base64 20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=
- leaflet.css: SHA-256 base64 p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=

The browserjs suffix permits Wrangler Text-module packaging. No upstream source is modified. Default image markers and layer controls are not used; route endpoints are canvas circle markers. Base tiles use the separate OpenStreetMap tile policy documented in MAP_CONTRACT.md.

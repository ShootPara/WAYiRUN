import type { AuthEnv } from "./auth.js";

type LocationRow = { city: string | null; region: string | null; source: string | null; retry_after: number };
const attribution = { label: "Location data: OpenStreetMap contributors", url: "https://www.openstreetmap.org/copyright" };
const clean = (value: unknown): string | null => typeof value === "string" && value.trim() && value.length <= 160 ? value.trim() : null;
export function parseLocality(value: unknown) {
  if (!value || typeof value !== "object") return null;
  const address = (value as { address?: Record<string, unknown> }).address;
  if (!address || typeof address !== "object") return null;
  const city = clean(address.city) ?? clean(address.town) ?? clean(address.village) ?? clean(address.municipality);
  const region = clean(address.state) ?? clean(address.region) ?? clean(address.state_district);
  return city ? { city, region } : null;
}
function display(row: LocationRow | null) {
  return row?.city ? { label: [row.city, row.region].filter(Boolean).join(", "), attribution } : null;
}

/** Called only for a verified, published run. No viewer coordinates or caller-selected query. */
export async function resolveRunLocation(env: AuthEnv, owner: string, runId: string,
  point: Record<string, unknown> | undefined, fetcher: typeof fetch = fetch, now = Date.now()) {
  if (!point || !env.LOCATION_LOOKUP_URL) return null;
  try {
    const cached = await env.DB.prepare("SELECT city,region,source,retry_after FROM run_locations WHERE owner_id=? AND run_id=?")
      .bind(owner, runId).first<LocationRow>();
    if (cached?.city || (cached && cached.retry_after > now)) return display(cached);
    const url = new URL(env.LOCATION_LOOKUP_URL);
    if (url.protocol !== "https:" || url.username || url.password) return null;
    const lat = Number(point.latitude), lon = Number(point.longitude);
    if (!Number.isFinite(lat) || Math.abs(lat) > 90 || !Number.isFinite(lon) || Math.abs(lon) > 180) return null;
    // Database-wide lease: at most one lookup per ten seconds across all Worker instances.
    const lease = await env.DB.prepare("UPDATE location_lookup_gate SET next_at=? WHERE id=1 AND next_at<=? RETURNING id")
      .bind(now + 10000, now).first();
    if (!lease) return null;
    url.search = new URLSearchParams({ lat: String(lat), lon: String(lon), format: "jsonv2", zoom: "10", addressdetails: "1", "accept-language": "en" }).toString();
    let locality: ReturnType<typeof parseLocality> = null;
    const abort = new AbortController(), timeout = setTimeout(() => abort.abort(), 3000);
    try {
      const response = await fetcher(url.toString(), { signal: abort.signal, redirect: "error",
        headers: { Accept: "application/json", "User-Agent": "WAYiRUN/1.0 (+https://wayirun-dev.unopenedparachute.workers.dev)" } });
      if (response.ok && response.body) {
        const reader = response.body.getReader();
        const chunks: Uint8Array[] = []; let size = 0;
        try {
          while (true) {
            const { value, done } = await reader.read(); if (done) break;
            size += value.length; if (size > 32768) { await reader.cancel(); throw new Error("Response too large"); }
            chunks.push(value);
          }
        } finally { reader.releaseLock(); }
        const bytes = new Uint8Array(size); let offset = 0;
        for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
        locality = parseLocality(JSON.parse(new TextDecoder().decode(bytes)));
      } else { await response.body?.cancel(); }
    } catch { /* A missing label must not prevent viewing the run. */ }
    finally { clearTimeout(timeout); }
    // Deletion wins even when it happens while the provider request is in flight.
    await env.DB.prepare(`INSERT INTO run_locations(owner_id,run_id,city,region,source,retry_after)
      SELECT ?,?,?,?,?,? WHERE EXISTS(SELECT 1 FROM run_uploads WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL)
      AND NOT EXISTS(SELECT 1 FROM run_deletions WHERE owner_id=? AND run_id=?)
      ON CONFLICT(owner_id,run_id) DO UPDATE SET city=excluded.city,region=excluded.region,source=excluded.source,retry_after=excluded.retry_after`)
      .bind(owner, runId, locality?.city ?? null, locality?.region ?? null, "nominatim", now + 86400000, owner, runId, owner, runId).run();
    return locality ? { label: [locality.city, locality.region].filter(Boolean).join(", "), attribution } : null;
  } catch { return null; }
}

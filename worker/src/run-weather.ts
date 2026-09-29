import { accessGuard, sessionAccount, reply, type AuthEnv } from "./auth.js";
import { readVerifiedRun, CoachingDataError } from "./coaching-context.js";

const HOUR = 3600000, DAY = 24 * HOUR;
const attribution = {
  label: "Weather data by Open-Meteo.com", url: "https://open-meteo.com/",
  licenseUrl: "https://creativecommons.org/licenses/by/4.0/",
  changes: "Hourly model estimate; coordinates and temperatures rounded; condition represented by emoji.",
};
export type WeatherQuery = { latitude: number; longitude: number; observedUtcMs: number; endpoint: "forecast" | "archive" };
export type WeatherSnapshot = WeatherQuery & {
  version: 1; source: "open-meteo"; weatherCode: number; emoji: string;
  temperatureC: number; temperatureF: number; retrievedUtcMs: number; attribution: typeof attribution;
};
type WeatherRow = { snapshot_json: string | null; retry_after: number };
const object = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};

/** Input is a verified archive, not caller-supplied location or time. */
export function weatherQuery(archive: Record<string, unknown>, now: number): WeatherQuery | null {
  const snapshot = object(object(object(archive.run).checkpoint).snapshot);
  if (object(snapshot.settings).mode !== "OUTDOOR") return null;
  const point = object(Array.isArray(archive.route) ? archive.route[0] : null);
  const latitude = point.latitude, longitude = point.longitude, started = snapshot.startedUtcMs;
  if (typeof latitude !== "number" || !Number.isFinite(latitude) || Math.abs(latitude) > 90 ||
      typeof longitude !== "number" || !Number.isFinite(longitude) || Math.abs(longitude) > 180 ||
      typeof started !== "number" || !Number.isSafeInteger(started) || started < 0 || started > now) return null;
  return { latitude: Math.round(latitude * 10) / 10, longitude: Math.round(longitude * 10) / 10,
    observedUtcMs: Math.floor(started / HOUR) * HOUR, endpoint: now - started < 7 * DAY ? "forecast" : "archive" };
}

export function weatherUrl(query: WeatherQuery): string {
  const date = new Date(query.observedUtcMs).toISOString().slice(0, 10);
  const url = new URL(query.endpoint === "forecast" ? "https://api.open-meteo.com/v1/forecast" : "https://archive-api.open-meteo.com/v1/archive");
  url.search = new URLSearchParams({ latitude: String(query.latitude), longitude: String(query.longitude),
    start_date: date, end_date: date, hourly: "temperature_2m,weather_code", temperature_unit: "celsius",
    timezone: "GMT", timeformat: "unixtime" }).toString();
  return url.toString();
}

function weatherEmoji(code: number): string | null {
  if (code === 0) return "\u2600\ufe0f";
  if (code === 1 || code === 2) return "\u26c5";
  if (code === 3) return "\u2601\ufe0f";
  if ([45, 48].includes(code)) return "\ud83c\udf2b\ufe0f";
  if ([51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82].includes(code)) return "\ud83c\udf27\ufe0f";
  if ([71, 73, 75, 77, 85, 86].includes(code)) return "\ud83c\udf28\ufe0f";
  if ([95, 96, 99].includes(code)) return "\u26c8\ufe0f";
  return null;
}

export function parseWeather(value: unknown, query: WeatherQuery, now: number): WeatherSnapshot | null {
  const root = object(value), hourly = object(root.hourly), units = object(root.hourly_units);
  const times = hourly.time, temperatures = hourly.temperature_2m, codes = hourly.weather_code;
  if (root.utc_offset_seconds !== 0 || units.time !== "unixtime" || units.temperature_2m !== "\u00b0C" ||
      !Array.isArray(times) || !Array.isArray(temperatures) || !Array.isArray(codes) ||
      times.length > 25 || times.length !== temperatures.length || times.length !== codes.length) return null;
  const target = query.observedUtcMs / 1000, index = times.indexOf(target);
  if (index < 0 || times.lastIndexOf(target) !== index) return null;
  const temperature = temperatures[index], code = codes[index];
  if (typeof temperature !== "number" || !Number.isFinite(temperature) || temperature < -100 || temperature > 70 ||
      typeof code !== "number" || !Number.isInteger(code)) return null;
  const emoji = weatherEmoji(code); if (!emoji) return null;
  return { version: 1, source: "open-meteo", ...query, weatherCode: code, emoji,
    temperatureC: Math.round(temperature * 10) / 10, temperatureF: Math.round((temperature * 1.8 + 32) * 10) / 10,
    retrievedUtcMs: now, attribution };
}

async function fetchWeather(query: WeatherQuery, fetcher: typeof fetch, now: number): Promise<WeatherSnapshot | null> {
  const abort = new AbortController();
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
  let timeout: ReturnType<typeof setTimeout>;
  const deadline = new Promise<null>(resolve => {
    timeout = setTimeout(() => {
      abort.abort();
      void reader?.cancel().catch(() => {});
      resolve(null);
    }, 3000);
  });
  const lookup = async (): Promise<WeatherSnapshot | null> => { try {
    const response = await fetcher(weatherUrl(query), { signal: abort.signal, redirect: "error", headers: { Accept: "application/json" } });
    if (abort.signal.aborted || !response.ok || !response.body) { await response.body?.cancel(); return null; }
    reader = response.body.getReader();
    const chunks: Uint8Array[] = []; let size = 0;
    try {
      while (true) {
        const { value, done } = await reader.read(); if (done) break;
        size += value.length;
        if (size > 32768) { await reader.cancel(); return null; }
        chunks.push(value);
      }
    } finally { reader.releaseLock(); reader = undefined; }
    if (abort.signal.aborted) return null;
    const bytes = new Uint8Array(size); let offset = 0;
    for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
    return parseWeather(JSON.parse(new TextDecoder().decode(bytes)), query, now);
  } catch { return null; } };
  try { return await Promise.race([lookup(), deadline]); }
  finally { clearTimeout(timeout!); }
}

export async function handleWeather(request: Request, env: AuthEnv, fetcher: typeof fetch = fetch, now = Date.now()): Promise<Response> {
  try {
    const url = new URL(request.url), match = /^\/api\/weather\/([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})$/.exec(url.pathname);
    if (!match || url.search) return reply({ error: "not_found" }, 404);
    if (request.headers.has("Origin") || request.headers.has("Cookie")) return reply({ error: "origin_not_allowed" }, 403);
    if (request.method !== "GET") return reply({ error: "method_not_allowed" }, 405, { Allow: "GET" });
    const denied = await accessGuard(request, env, true); if (denied) return denied;
    const session = await sessionAccount(request, env); if (!session) return reply({ error: "unauthorized" }, 401);
    const owner = session.account.id, id = match[1]!;
    const exists = () => env.DB.prepare(`SELECT 1 FROM run_uploads WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL
      AND NOT EXISTS(SELECT 1 FROM run_deletions WHERE owner_id=? AND run_id=?)`).bind(owner, id, owner, id).first();
    if (!await exists()) return reply({ error: "not_found" }, 404);
    const deliver = async (weather: WeatherSnapshot | null, reason: string | null, retryAfter: number | null = null) => {
      if ((await sessionAccount(request, env))?.account.id !== owner) return reply({ error: "unauthorized" }, 401);
      if (!await exists()) return reply({ error: "not_found" }, 404);
      return reply({ weather, reason, retryAfter });
    };
    const cached = await env.DB.prepare("SELECT snapshot_json,retry_after FROM run_weather WHERE owner_id=? AND run_id=?")
      .bind(owner, id).first<WeatherRow>();
    if (cached?.snapshot_json) return await deliver(JSON.parse(cached.snapshot_json) as WeatherSnapshot, null);
    if (cached && cached.retry_after > now) return await deliver(null, "retry_later", cached.retry_after);
    const archive = await readVerifiedRun(env.DB, owner, id), query = weatherQuery(archive, now);
    if (!query) return await deliver(null, "no_recorded_weather_location_or_time");
    const attempt = crypto.randomUUID();
    // A per-run reservation prevents late attempts from replacing a newer snapshot.
    const reserved = await env.DB.prepare(`INSERT INTO run_weather(owner_id,run_id,snapshot_json,retry_after,attempt)
      SELECT ?,?,NULL,?,? WHERE EXISTS(SELECT 1 FROM run_uploads WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL)
      AND NOT EXISTS(SELECT 1 FROM run_deletions WHERE owner_id=? AND run_id=?)
      ON CONFLICT(owner_id,run_id) DO UPDATE SET retry_after=excluded.retry_after,attempt=excluded.attempt
      WHERE run_weather.snapshot_json IS NULL AND run_weather.retry_after<=? RETURNING attempt`)
      .bind(owner, id, now + 30000, attempt, owner, id, owner, id, now).first();
    if (!reserved) return await deliver(null, "retry_later", now + 30000);
    // Ten-second global spacing stays below the free service's daily/hourly/minute limits.
    const gate = await env.DB.prepare("UPDATE weather_lookup_gate SET next_at=? WHERE id=1 AND next_at<=? RETURNING id")
      .bind(now + 10000, now).first();
    if (!gate) return await deliver(null, "retry_later", now + 30000);
    if ((await sessionAccount(request, env))?.account.id !== owner) return reply({ error: "unauthorized" }, 401);
    if (!await exists()) return reply({ error: "not_found" }, 404);
    const weather = await fetchWeather(query, fetcher, now);
    // Atomic persistence checks: a revoked session or deletion wins over the provider response.
    const stored = await env.DB.prepare(`UPDATE run_weather SET snapshot_json=?,retry_after=?
      WHERE owner_id=? AND run_id=? AND attempt=? AND snapshot_json IS NULL
      AND EXISTS(SELECT 1 FROM auth_sessions WHERE token_hash=? AND owner_id=? AND revoked_at IS NULL AND expires_at>?)
      AND EXISTS(SELECT 1 FROM run_uploads WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL)
      AND NOT EXISTS(SELECT 1 FROM run_deletions WHERE owner_id=? AND run_id=?) RETURNING run_id`)
      .bind(weather ? JSON.stringify(weather) : null, now + HOUR, owner, id, attempt, session.tokenHash, owner,
        Math.floor(Date.now() / 1000), owner, id, owner, id).first();
    return await deliver(stored ? weather : null, stored && weather ? null : "provider_unavailable", weather && stored ? null : now + HOUR);
  } catch (error) {
    if (error instanceof CoachingDataError && error.code === "run_unavailable") return reply({ error: "not_found" }, 404);
    return reply({ error: "weather_unavailable" }, 503);
  }
}

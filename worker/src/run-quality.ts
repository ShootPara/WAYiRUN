type Row = Record<string, unknown>;
type Sample = { segment: number; time: number; active?: number; total?: number; latitude?: number; longitude?: number; accuracy?: number };
const record = (x: unknown): Row => x !== null && typeof x === "object" && !Array.isArray(x) ? x as Row : {};
const rows = (x: unknown): Row[] => Array.isArray(x) ? x.map(record) : [];
const finite = (x: unknown): x is number => typeof x === "number" && Number.isFinite(x);
const PACE_SPEED = 1609.344 / 210;
const MIN_FAST_MS = 25_000;
const MAX_GAP_MS = 10_000;

/** Derived evidence only. Never changes retained records or decides whether coaching runs. */
export function classifyRunQuality(archive: Row) {
  const snapshot = record(record(archive.run).checkpoint).snapshot;
  const s = record(snapshot), duration = s.activeDurationMs, distance = s.distanceMeters;
  const reasons: string[] = [];
  if (finite(duration) && duration < 90_000) reasons.push("active_duration_under_90_seconds");
  if (finite(distance) && distance < 0.05 * 1609.344) reasons.push("distance_under_0_05_miles");
  let fastMeasurementMs = 0, fastGpsMs = 0, jumpCount = 0, evaluatedPairs = 0;

  function scan(samples: (Sample | null)[], threshold: number, gps: boolean, route: boolean) {
    let previous: Sample | null = null, fast = 0, longest = 0;
    for (const sample of samples) {
      if (!sample) { previous = null; fast = 0; continue; }
      const p = previous; previous = sample;
      if (!p || sample.segment !== p.segment) { fast = 0; continue; }
      const elapsed = sample.time - p.time;
      // Active snapshots can lag/batch sensor timestamps; use the larger elapsed interval.
      const active = sample.active !== undefined && p.active !== undefined ? sample.active - p.active : elapsed;
      const dt = Math.max(elapsed, active);
      const meters = route ? routeDistance(p, sample) : sample.total! - p.total!;
      if (elapsed < 0 || active < 0 || meters < 0) { fast = 0; continue; }
      if (gps && meters >= 100 && (dt === 0 || meters / (dt / 1000) > 20)) {
        jumpCount++; fast = 0; continue;
      }
      if (dt <= 0 || dt > MAX_GAP_MS) { fast = 0; continue; }
      evaluatedPairs++;
      fast = meters / (dt / 1000) > threshold ? fast + dt : 0;
      longest = Math.max(longest, fast);
    }
    return longest;
  }

  // Preserve acquisition order. Sorting could manufacture continuity across clock resets.
  const measurements = rows(archive.measurements).map(r => {
    if (!["GPS", "STEPS"].includes(String(r.source)) ||
        !finite(r.segmentId) || !finite(r.monotonicMs) || !finite(r.activeMs) || !finite(r.totalMeters)) return null;
    return { segment: r.segmentId, time: r.monotonicMs, active: r.activeMs, total: r.totalMeters };
  });
  // Scan sources separately, with null boundaries to prevent bridging source changes.
  const rawMeasurements = rows(archive.measurements);
  for (const kind of ["GPS", "STEPS"]) {
    fastMeasurementMs = Math.max(fastMeasurementMs, scan(measurements.map((r, i) => rawMeasurements[i]!.source === kind ? r : null), PACE_SPEED, kind === "GPS", false));
  }
  const route = rows(archive.route).map(r => {
    if (!finite(r.segmentId) || !finite(r.monotonicMs) || !finite(r.latitude) || !finite(r.longitude) ||
        !finite(r.accuracyMeters) || r.accuracyMeters < 0 || r.accuracyMeters > 25 ||
        Math.abs(r.latitude) > 90 || Math.abs(r.longitude) > 180) return null;
    return { segment: r.segmentId, time: r.monotonicMs, latitude: r.latitude, longitude: r.longitude, accuracy: r.accuracyMeters };
  });
  fastGpsMs = scan(route, 8, true, true);
  if (fastMeasurementMs >= MIN_FAST_MS) reasons.push("sustained_pace_faster_than_3_30_per_mile");
  if (fastGpsMs >= MIN_FAST_MS) reasons.push("sustained_gps_speed_above_8_mps");
  if (jumpCount) reasons.push("implausible_gps_distance_time_jump");
  const vehicle = fastMeasurementMs >= MIN_FAST_MS || fastGpsMs >= MIN_FAST_MS;
  const test = (finite(duration) && duration < 90_000) || (finite(distance) && distance < 0.05 * 1609.344);
  return {
    version: 1,
    label: jumpCount ? "gps_anomaly" : vehicle ? "likely_vehicle" : test ? "likely_test" : "normal",
    reasons,
    metrics: { activeDurationMs: duration, distanceMeters: distance, longestFastMeasurementMs: fastMeasurementMs,
      longestFastGpsMs: fastGpsMs, gpsJumpEvidenceCount: jumpCount, evaluatedPairs,
      sustainedWindowMs: MIN_FAST_MS, maxObservationGapMs: MAX_GAP_MS },
  };
}

function routeDistance(a: Sample, b: Sample): number {
  const radians = Math.PI / 180;
  const lat = (b.latitude! - a.latitude!) * radians, lon = (b.longitude! - a.longitude!) * radians;
  const h = Math.sin(lat / 2) ** 2 + Math.cos(a.latitude! * radians) * Math.cos(b.latitude! * radians) * Math.sin(lon / 2) ** 2;
  // Discount both accuracy radii before treating route displacement as speed evidence.
  return Math.max(0, 2 * 6371000 * Math.asin(Math.sqrt(Math.min(1, h))) - a.accuracy! - b.accuracy!);
}

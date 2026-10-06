import { hash } from "./auth.js";
import { classifyRunQuality } from "./run-quality.js";

type ObjectValue = Record<string, unknown>;
type RunRow = { run_id: string; operation_id: string; manifest_json: string; manifest_hash: string; chunk_count: number };
export type RunReference = { runId: string; manifestHash: string };
export type CoachingContext = { owner: string; current: RunReference; previous: RunReference | null; input: string; bytes: number };
export class CoachingDataError extends Error {
  constructor(readonly code: "run_unavailable" | "invalid_archive" | "context_too_large") { super(code); }
}
const MAX_ARCHIVE = 128 * 131072;
export const MAX_CONTEXT_BYTES = 12 * 1024 * 1024;
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
function check(condition: unknown): asserts condition { if (!condition) throw new CoachingDataError("invalid_archive"); }
function object(value: unknown): ObjectValue {
  check(value !== null && typeof value === "object" && !Array.isArray(value));
  return value as ObjectValue;
}
function embedded(value: unknown): ObjectValue { check(typeof value === "string"); return object(JSON.parse(value)); }
function array(value: unknown): ObjectValue[] { check(Array.isArray(value)); return value.map(object); }

async function currentRow(db: D1Database, owner: string, id: string): Promise<RunRow> {
  const row = await db.prepare(`SELECT * FROM run_uploads r WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL
    AND NOT EXISTS (SELECT 1 FROM run_deletions d WHERE d.owner_id=r.owner_id AND d.run_id=r.run_id)`)
    .bind(owner, id).first<RunRow>();
  if (!row) throw new CoachingDataError("run_unavailable");
  return row;
}
async function manifest(row: RunRow): Promise<ObjectValue> {
  check(await hash(row.manifest_json) === row.manifest_hash);
  const m = object(JSON.parse(row.manifest_json));
  check(m.schemaVersion === 1 && m.runId === row.run_id && m.operationId === row.operation_id);
  const s = object(m.summary);
  check(s.state === "FINISHED");
  for (const key of ["startedUtcMs", "endedUtcMs", "activeDurationMs"]) check(Number.isSafeInteger(s[key]) && Number(s[key]) >= 0);
  check(typeof s.distanceMeters === "number" && Number.isFinite(s.distanceMeters) && s.distanceMeters >= 0);
  check(["INDOOR", "OUTDOOR"].includes(String(s.mode)) && ["MILES", "KILOMETERS"].includes(String(s.units)));
  return m;
}
async function readArchive(db: D1Database, owner: string, row: RunRow): Promise<ObjectValue> {
  const m = await manifest(row), descriptors = array(m.chunks);
  check(descriptors.length >= 1 && descriptors.length <= 128 && descriptors.length === row.chunk_count);
  let size = 0;
  for (const d of descriptors) {
    check(Number.isSafeInteger(d.bytes) && Number(d.bytes) > 0 && Number(d.bytes) <= 131072);
    check(typeof d.sha256 === "string" && /^[0-9a-f]{64}$/.test(d.sha256)); size += Number(d.bytes);
  }
  check(size <= MAX_ARCHIVE);
  const joined = new Uint8Array(size); let offset = 0;
  // Read individually: a large archive must not exceed D1's result-size limit.
  for (let i = 0; i < descriptors.length; i++) {
    const chunk = await db.prepare("SELECT data FROM run_chunks WHERE owner_id=? AND run_id=? AND chunk_index=?")
      .bind(owner, row.run_id, i).first<{ data: number[] | ArrayBuffer }>();
    check(chunk);
    const bytes = new Uint8Array(chunk.data), descriptor = descriptors[i]!;
    check(bytes.byteLength === descriptor.bytes);
    const digest = Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)), b => b.toString(16).padStart(2, "0")).join("");
    check(digest === descriptor.sha256); joined.set(bytes, offset); offset += bytes.length;
  }
  const archive = object(JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(joined)));
  const run = object(archive.run), checkpoint = embedded(run.checkpoint), snapshot = object(checkpoint.snapshot);
  check(archive.version === 1 && run.id === row.run_id && run.cloudOwnerId === owner && run.state === "FINISHED" && run.activeSlot === null);
  check(snapshot.runId === row.run_id && snapshot.state === "FINISHED");
  const summary = object(m.summary), settings = object(snapshot.settings);
  for (const field of ["startedUtcMs", "endedUtcMs", "activeDurationMs", "distanceMeters"]) check(snapshot[field] === summary[field]);
  check(settings.mode === summary.mode && settings.units === summary.units);
  for (const name of ["route", "measurements", "splits", "intervals", "segments"]) {
    const rows = array(archive[name]); check(rows.every(r => r.runId === row.run_id));
    if (name === "intervals" || name === "segments") {
      const expected = array(snapshot[name === "intervals" ? "activeIntervals" : "segments"]);
      check(rows.length === expected.length);
      rows.forEach((r, i) => check(JSON.stringify(embedded(r.value)) === JSON.stringify(expected[i])));
    }
    // Decode nested JSON without discarding any retained fields or records.
    archive[name] = rows.map(r => name === "measurements" ? { ...r, reading: embedded(r.reading) }
      : name === "intervals" || name === "segments" ? { ...r, value: embedded(r.value) } : r);
  }
  const segments = array(snapshot.segments), route = array(archive.route), measurements = array(archive.measurements);
  check(new Set(route.map(r => r.id)).size === route.length && new Set(measurements.map(r => r.id)).size === measurements.length);
  for (const point of route) {
    check(settings.mode === "OUTDOOR" && typeof point.latitude === "number" && Number.isFinite(point.latitude) && Math.abs(point.latitude) <= 90);
    check(typeof point.longitude === "number" && Number.isFinite(point.longitude) && Math.abs(point.longitude) <= 180);
    check(typeof point.accuracyMeters === "number" && Number.isFinite(point.accuracyMeters) && point.accuracyMeters >= 0);
    check(segments.some(s => s.id === point.segmentId && s.source === "GPS"));
  }
  for (const measurement of measurements) {
    const reading = object(measurement.reading);
    check(typeof measurement.deltaMeters === "number" && Number.isFinite(measurement.deltaMeters) && measurement.deltaMeters >= 0);
    check(typeof measurement.totalMeters === "number" && Number.isFinite(measurement.totalMeters)
      && measurement.totalMeters >= 0 && measurement.totalMeters <= Number(snapshot.distanceMeters) + 0.000001);
    check(Number.isSafeInteger(measurement.activeMs) && Number(measurement.activeMs) >= 0 && Number(measurement.activeMs) <= Number(snapshot.activeDurationMs));
    check(reading.segmentId === measurement.segmentId && reading.monotonicMs === measurement.monotonicMs);
    check(segments.some(s => s.id === measurement.segmentId && s.source === measurement.source));
  }
  archive.run = { ...run, checkpoint };
  return archive;
}

/** Internal only: the caller must derive owner from authentication, never request JSON. */
export async function prepareCoachingContext(db: D1Database, owner: string, runId: string): Promise<CoachingContext> {
  if (!uuid.test(runId)) throw new CoachingDataError("run_unavailable");
  try {
    const current = await currentRow(db, owner, runId), m = await manifest(current);
    const ended = object(m.summary).endedUtcMs;
    const previous = await db.prepare(`SELECT * FROM run_uploads r WHERE owner_id=? AND completed_at IS NOT NULL
      AND (json_extract(manifest_json,'$.summary.endedUtcMs') < ? OR
        (json_extract(manifest_json,'$.summary.endedUtcMs') = ? AND run_id < ?))
      AND NOT EXISTS (SELECT 1 FROM run_deletions d WHERE d.owner_id=r.owner_id AND d.run_id=r.run_id)
      ORDER BY json_extract(manifest_json,'$.summary.endedUtcMs') DESC, run_id DESC LIMIT 1`)
      .bind(owner, ended, ended, runId).first<RunRow>();
    const currentArchive = await readArchive(db, owner, current);
    const input = JSON.stringify({ current: { ...currentArchive, quality: classifyRunQuality(currentArchive) },
      previous: previous ? await readArchive(db, owner, previous) : null });
    const bytes = new TextEncoder().encode(input).byteLength;
    if (bytes > MAX_CONTEXT_BYTES) throw new CoachingDataError("context_too_large");
    const result = { owner, current: { runId, manifestHash: current.manifest_hash },
      previous: previous ? { runId: previous.run_id, manifestHash: previous.manifest_hash } : null, input, bytes };
    await verifyCoachingContext(db, result);
    return result;
  } catch (error) {
    if (error instanceof CoachingDataError) throw error;
    // Never leak embedded data or database/parser diagnostics into error surfaces.
    throw new CoachingDataError("invalid_archive");
  }
}

/** Repeat before paid generation, result persistence and serving; does not reselect history. */
export async function verifyCoachingContext(db: D1Database, context: CoachingContext): Promise<void> {
  for (const reference of [context.current, context.previous]) {
    if (!reference) continue;
    const row = await currentRow(db, context.owner, reference.runId);
    if (row.manifest_hash !== reference.manifestHash) throw new CoachingDataError("run_unavailable");
  }
}

/** Internal verified archive access; caller must authenticate or resolve an existing publication. */
export async function readVerifiedRun(db: D1Database, owner: string, id: string) { return readArchive(db, owner, await currentRow(db,owner,id)); }

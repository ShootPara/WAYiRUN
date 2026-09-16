import { accessGuard, hash, reply, sessionAccount, smallJson, type AuthEnv } from "./auth.js";

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const digest = /^[0-9a-f]{64}$/;
const CHUNK_BYTES = 131072;
const UPLOAD_SECONDS = 86400;
type Summary = {
  state: "FINISHED"; startedUtcMs: number; endedUtcMs: number;
  activeDurationMs: number; distanceMeters: number; mode: "INDOOR" | "OUTDOOR"; units: "MILES" | "KILOMETERS";
};
type Manifest = {
  schemaVersion: 1; runId: string; operationId: string; summary: Summary;
  chunks: { sha256: string; bytes: number }[];
};
type Upload = {
  run_id: string; operation_id: string; manifest_json: string; manifest_hash: string;
  chunk_count: number; completed_at: number | null; expires_at: number;
};
function object(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error("object");
  return value as Record<string, unknown>;
}
function keys(value: Record<string, unknown>, expected: string[]) {
  if (Object.keys(value).sort().join(",") !== expected.sort().join(",")) throw new Error("keys");
}
function integer(value: unknown, min: number, max = Number.MAX_SAFE_INTEGER): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= min && value <= max;
}
function manifest(value: unknown): Manifest {
  const b = object(value);
  keys(b, ["schemaVersion", "runId", "operationId", "summary", "chunks"]);
  if (b.schemaVersion !== 1 || typeof b.runId !== "string" || !uuid.test(b.runId)
      || typeof b.operationId !== "string" || !uuid.test(b.operationId)) throw new Error("identity");
  const s = object(b.summary);
  keys(s, ["state", "startedUtcMs", "endedUtcMs", "activeDurationMs", "distanceMeters", "mode", "units"]);
  if (s.state !== "FINISHED" || !integer(s.startedUtcMs, 0) || !integer(s.endedUtcMs, 0)
      || !integer(s.activeDurationMs, 0) || typeof s.distanceMeters !== "number" || !Number.isFinite(s.distanceMeters)
      || s.distanceMeters < 0 || !["INDOOR", "OUTDOOR"].includes(String(s.mode))
      || !["MILES", "KILOMETERS"].includes(String(s.units))) throw new Error("summary");
  if (!Array.isArray(b.chunks) || b.chunks.length < 1 || b.chunks.length > 128) throw new Error("chunks");
  const chunks = b.chunks.map(value => {
    const c = object(value); keys(c, ["sha256", "bytes"]);
    if (typeof c.sha256 !== "string" || !digest.test(c.sha256) || !integer(c.bytes, 1, CHUNK_BYTES)) throw new Error("chunk");
    return { sha256: c.sha256, bytes: c.bytes };
  });
  // Construct a canonical property order, independent of a JSON client's key ordering.
  return { schemaVersion: 1, runId: b.runId, operationId: b.operationId, summary: {
    state: "FINISHED", startedUtcMs: s.startedUtcMs, endedUtcMs: s.endedUtcMs,
    activeDurationMs: s.activeDurationMs, distanceMeters: s.distanceMeters,
    mode: s.mode as Summary["mode"], units: s.units as Summary["units"],
  }, chunks };
}
function receipt(row: Upload) {
  return { runId: row.run_id, operationId: row.operation_id, manifestHash: row.manifest_hash, completedAt: row.completed_at };
}
async function upload(env: AuthEnv, owner: string, id: string): Promise<Upload | null> {
  return env.DB.prepare("SELECT * FROM run_uploads WHERE owner_id = ? AND run_id = ?").bind(owner, id).first<Upload>();
}
async function bytes(request: Request, expected: number, checkType = true): Promise<Uint8Array> {
  if (checkType && request.headers.get("Content-Type") !== "application/octet-stream") throw new Error("type");
  const reader = request.body?.getReader();
  if (!reader) { if (expected === 0) return new Uint8Array(); throw new Error("body"); }
  const result = new Uint8Array(expected); let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read(); if (done) break;
      if (size + value.length > expected) { await reader.cancel(); throw new Error("size"); }
      result.set(value, size); size += value.length;
    }
  } finally { reader.releaseLock(); }
  if (size !== expected) throw new Error("size");
  return result;
}
async function binaryHash(value: Uint8Array): Promise<string> {
  const result = await crypto.subtle.digest("SHA-256", value);
  return Array.from(new Uint8Array(result), b => b.toString(16).padStart(2, "0")).join("");
}

export async function handleRuns(request: Request, env: AuthEnv): Promise<Response> {
  try {
    const denied = await accessGuard(request, env); if (denied) return denied;
    const verified = await sessionAccount(request, env);
    if (!verified) return reply({ error: "unauthorized" }, 401);
    const owner = verified.account.id;
    const url = new URL(request.url);
    const path = url.pathname;
    const match = /^\/api\/(run-uploads|runs)\/([0-9a-f-]+)(?:\/(complete|chunks\/(0|[1-9][0-9]*)))?$/.exec(path);
    const root = path === "/api/run-uploads" || path === "/api/runs";
    if (!root && (!match || !uuid.test(match[2]!))) return reply({ error: "not_found" }, 404);
    const staged = path.startsWith("/api/run-uploads");
    const expected = root ? (staged ? "POST" : "GET") : (staged && match![3] ? (match![3] === "complete" ? "POST" : "PUT") : "GET");
    if (!staged && match?.[3] === "complete") return reply({ error: "not_found" }, 404);
    if (request.method !== expected) return reply({ error: "method_not_allowed" }, 405, { Allow: expected });
    if (url.search && path !== "/api/runs") return reply({ error: "invalid_request" }, 400);
    const now = Math.floor(Date.now() / 1000);

    if (path === "/api/run-uploads") {
      let m: Manifest;
      try { m = manifest(await smallJson(request)); } catch { return reply({ error: "invalid_manifest" }, 400); }
      const text = JSON.stringify(m), fingerprint = await hash(text);
      // Atomic cleanup + reservation. Abandoned drafts expire; completed receipts never do.
      await env.DB.batch([
        env.DB.prepare("DELETE FROM run_uploads WHERE owner_id = ? AND completed_at IS NULL AND expires_at <= ?").bind(owner, now),
        env.DB.prepare(`INSERT INTO run_uploads (owner_id, run_id, operation_id, manifest_json, manifest_hash, chunk_count, created_at, expires_at)
          SELECT ?, ?, ?, ?, ?, ?, ?, ? WHERE
          (SELECT count(*) FROM run_uploads WHERE owner_id = ? AND completed_at IS NULL) < 4
          ON CONFLICT DO NOTHING`).bind(owner, m.runId, m.operationId, text, fingerprint, m.chunks.length, now, now + UPLOAD_SECONDS, owner),
      ]);
      const row = await upload(env, owner, m.runId);
      if (row && row.manifest_hash === fingerprint) return reply({ ...receipt(row), expiresAt: row.completed_at ? null : row.expires_at });
      const collision = await env.DB.prepare("SELECT run_id FROM run_uploads WHERE owner_id = ? AND operation_id = ?").bind(owner, m.operationId).first();
      if (row || collision) return reply({ error: "upload_conflict" }, 409);
      return reply({ error: "pending_upload_limit" }, 429, { "Retry-After": "60" });
    }

    if (path === "/api/runs") {
      if ([...url.searchParams.keys()].some(k => k !== "after") || url.searchParams.getAll("after").length > 1) return reply({ error: "invalid_request" }, 400);
      const cursor = url.searchParams.get("after");
      const parts = cursor?.split(":");
      if (parts && (parts.length !== 2 || !/^\d{1,16}$/.test(parts[0]!) || !integer(Number(parts[0]), 0) || !uuid.test(parts[1]!))) return reply({ error: "invalid_request" }, 400);
      const result = await env.DB.prepare(`SELECT * FROM run_uploads WHERE owner_id = ? AND completed_at IS NOT NULL
        AND (completed_at > ? OR (completed_at = ? AND run_id > ?)) ORDER BY completed_at, run_id LIMIT 21`)
        .bind(owner, parts ? Number(parts[0]) : -1, parts ? Number(parts[0]) : -1, parts?.[1] ?? "").all<Upload>();
      const page = result.results.slice(0, 20), last = page.at(-1);
      return reply({ runs: page.map(row => ({ ...receipt(row), summary: (JSON.parse(row.manifest_json) as Manifest).summary })),
        next: result.results.length > 20 && last ? `${last.completed_at}:${last.run_id}` : null });
    }

    const id = match![2]!, row = await upload(env, owner, id);
    if (!row || (!staged && row.completed_at === null)) return reply({ error: "not_found" }, 404);
    if (row.completed_at === null && row.expires_at <= now) return reply({ error: "upload_expired" }, 410);
    if (request.method !== "GET" && request.headers.get("If-Match") !== `"${row.manifest_hash}"`) {
      return reply({ error: "upload_conflict" }, 409);
    }
    const m = JSON.parse(row.manifest_json) as Manifest;
    if (!match![3]) {
      if (!staged) return reply({ ...receipt(row), manifest: m });
      const chunks = await env.DB.prepare("SELECT chunk_index FROM run_chunks WHERE owner_id = ? AND run_id = ? ORDER BY chunk_index").bind(owner, id).all<{ chunk_index: number }>();
      return reply({ ...receipt(row), expiresAt: row.completed_at ? null : row.expires_at, received: chunks.results.map(c => c.chunk_index) });
    }
    if (match![3] === "complete") {
      try { await bytes(request, 0, false); } catch { return reply({ error: "invalid_request" }, 400); }
      await env.DB.prepare(`UPDATE run_uploads SET completed_at = ? WHERE owner_id = ? AND run_id = ?
        AND manifest_hash = ? AND completed_at IS NULL AND expires_at > ? AND chunk_count =
        (SELECT count(*) FROM run_chunks WHERE owner_id = ? AND run_id = ?)`)
        .bind(now, owner, id, row.manifest_hash, now, owner, id).run();
      const completed = (await upload(env, owner, id))!;
      return completed?.completed_at != null && completed.manifest_hash === row.manifest_hash ? reply(receipt(completed)) : reply({ error: "upload_incomplete" }, 409);
    }
    const index = Number(match![4]), descriptor = m.chunks[index];
    if (!Number.isSafeInteger(index) || !descriptor) return reply({ error: "not_found" }, 404);
    if (staged) {
      let data: Uint8Array;
      try {
        data = await bytes(request, descriptor.bytes);
        if (await binaryHash(data) !== descriptor.sha256) throw new Error("hash");
      } catch { return reply({ error: "invalid_chunk" }, 400); }
      await env.DB.prepare(`INSERT INTO run_chunks (owner_id, run_id, chunk_index, sha256, data)
        SELECT ?, ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM run_uploads
        WHERE owner_id = ? AND run_id = ? AND manifest_hash = ? AND completed_at IS NULL AND expires_at > ?)
        ON CONFLICT DO NOTHING`).bind(owner, id, index, descriptor.sha256, data.buffer, owner, id, row.manifest_hash, now).run();
      const stored = await env.DB.prepare("SELECT sha256 FROM run_chunks WHERE owner_id = ? AND run_id = ? AND chunk_index = ?").bind(owner, id, index).first<{ sha256: string }>();
      return stored?.sha256 === descriptor.sha256 ? reply({ index, sha256: descriptor.sha256 }) : reply({ error: "upload_conflict" }, 409);
    }
    const chunk = await env.DB.prepare("SELECT data FROM run_chunks WHERE owner_id = ? AND run_id = ? AND chunk_index = ?")
      .bind(owner, id, index).first<{ data: number[] }>();
    if (!chunk) return reply({ error: "storage_unavailable" }, 503);
    return new Response(new Uint8Array(chunk.data), { headers: {
      "Content-Type": "application/octet-stream", "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff",
    } });
  } catch {
    return reply({ error: "storage_unavailable" }, 503);
  }
}

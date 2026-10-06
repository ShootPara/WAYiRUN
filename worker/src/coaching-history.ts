import { accessGuard, reply, sessionAccount, type AuthEnv } from "./auth.js";

type Saved = { operation_id: string; state: string; current_hash: string | null; previous_run_id: string | null;
  previous_hash: string | null; message: string | null; error_code: string | null; audio_bytes: number | null;
  created_at: number; updated_at: number };
const columns = "operation_id,state,current_hash,previous_run_id,previous_hash,message,error_code,audio_bytes,created_at,updated_at";
async function digest(bytes: Uint8Array) {
  return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)), b => b.toString(16).padStart(2, "0")).join("");
}
function base64(bytes: Uint8Array) {
  let text = "";
  for (let offset = 0; offset < bytes.length; offset += 8192) text += String.fromCharCode(...bytes.subarray(offset, offset + 8192));
  return btoa(text);
}

/** Read-only, owner-scoped snapshot. No credential columns, generation or retry path. */
export async function handleCoachingHistory(request: Request, env: AuthEnv): Promise<Response> {
  try {
    const url = new URL(request.url), match = /^\/api\/coaching-history\/([0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12})$/.exec(url.pathname);
    if (!match) return reply({ error: "not_found" }, 404);
    if (request.method !== "GET") return reply({ error: "method_not_allowed" }, 405);
    if (url.search) return reply({ error: "invalid_request" }, 400);
    if (request.headers.has("Origin") || request.headers.has("Cookie")) return reply({ error: "origin_not_allowed" }, 403);
    const denied = await accessGuard(request, env, true); if (denied) return denied;
    const session = await sessionAccount(request, env); if (!session) return reply({ error: "unauthorized" }, 401);
    const owner = session.account.id, id = match[1]!;
    const runExists = () => env.DB.prepare(`SELECT run_id FROM run_uploads r WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM run_deletions d WHERE d.owner_id=r.owner_id AND d.run_id=r.run_id)`).bind(owner, id).first();
    if (!await runExists()) return reply({ error: "not_found" }, 404);
    const read = () => env.DB.prepare(`SELECT ${columns} FROM coaching_jobs WHERE owner_id=? AND run_id=?`).bind(owner, id).first<Saved>();
    const saved = await read();
    if (!saved) return reply({ runId: id, coaching: null });
    let audio = null;
    if (saved.state === "ready") {
      if (!saved.audio_bytes || saved.audio_bytes > 4194304) throw new Error("audio");
      const bytes = new Uint8Array(saved.audio_bytes);
      for (let offset = 0; offset < bytes.length; offset += 131072) {
        const row = await env.DB.prepare("SELECT data FROM coaching_audio WHERE owner_id=? AND run_id=? AND chunk_index=?")
          .bind(owner, id, offset / 131072).first<{ data: number[] | ArrayBuffer }>();
        if (!row) throw new Error("audio");
        const chunk = new Uint8Array(row.data);
        if (chunk.length !== Math.min(131072, bytes.length - offset)) throw new Error("audio");
        bytes.set(chunk, offset);
      }
      // Keep each CSV JSON cell below common spreadsheet cell-length limits.
      const chunks = [];
      for (let offset = 0; offset < bytes.length; offset += 16384) {
        const chunk = bytes.slice(offset, offset + 16384);
        chunks.push({ index: offset / 16384, bytes: chunk.length, sha256: await digest(chunk), base64: base64(chunk) });
      }
      audio = { mediaType: "audio/wav", bytes: bytes.length, sha256: await digest(bytes), chunks };
    }
    if (!await sessionAccount(request, env)) return reply({ error: "unauthorized" }, 401);
    if (!await runExists()) return reply({ error: "not_found" }, 404);
    if (JSON.stringify(await read()) !== JSON.stringify(saved)) return reply({ error: "coaching_changed" }, 409);
    return reply({ runId: id, coaching: { version: 1, runId: id, operationId: saved.operation_id, state: saved.state,
      currentManifestHash: saved.current_hash, previousRunId: saved.previous_run_id, previousManifestHash: saved.previous_hash,
      message: saved.message, error: saved.error_code, createdAt: saved.created_at, updatedAt: saved.updated_at, audio } });
  } catch { return reply({ error: "coaching_history_unavailable" }, 503); }
}

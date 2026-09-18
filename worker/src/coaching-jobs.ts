import { accessGuard, reply, sessionAccount, smallJson } from "./auth.js";
import { unsealKey, type KeyEnv } from "./account-key.js";
import { prepareCoachingContext, verifyCoachingContext, CoachingDataError } from "./coaching-context.js";
import { CoachingProvider, CoachingProviderError } from "./coaching-provider.js";

type KeyRow = { revision: string; ciphertext: string; nonce: string; key_version: string };
type Job = { owner_id: string; run_id: string; operation_id: string; state: string; current_hash: string | null;
  previous_run_id: string | null; previous_hash: string | null; key_revision: string; token_hash: string;
  message: string | null; error_code: string | null; audio_bytes: number | null; created_at: number; updated_at: number };
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/;
const now = () => Math.floor(Date.now() / 1000);
const live = `EXISTS (SELECT 1 FROM auth_sessions s WHERE s.token_hash=coaching_jobs.token_hash AND s.owner_id=coaching_jobs.owner_id AND s.revoked_at IS NULL AND s.expires_at>?)
 AND EXISTS (SELECT 1 FROM openai_keys k WHERE k.owner_id=coaching_jobs.owner_id AND k.revision=coaching_jobs.key_revision AND k.ciphertext IS NOT NULL)
 AND NOT EXISTS (SELECT 1 FROM run_deletions d WHERE d.owner_id=coaching_jobs.owner_id AND (d.run_id=coaching_jobs.run_id OR d.run_id=coaching_jobs.previous_run_id))`;
async function read(db: D1Database, owner: string, id: string) {
  return db.prepare("SELECT * FROM coaching_jobs WHERE owner_id=? AND run_id=?").bind(owner, id).first<Job>();
}
function status(job: Job) {
  return { runId: job.run_id, operationId: job.operation_id, state: job.state,
    message: job.state === "ready" ? job.message : null, error: job.error_code,
    audioBytes: job.state === "ready" ? job.audio_bytes : null };
}
async function expire(db: D1Database, owner: string, id: string) {
  // A dead worker is never permission to repeat a potentially paid request.
  await db.prepare(`UPDATE coaching_jobs SET state=CASE WHEN state='preparing' THEN 'failed' ELSE 'unknown' END,
    error_code='interrupted',updated_at=? WHERE owner_id=? AND run_id=? AND state IN ('preparing','text_pending','speech_pending') AND created_at<=?`)
    .bind(now(), owner, id, now() - 180).run();
}
async function advance(db: D1Database, owner: string, id: string, from: string, to: string, message: string | null = null) {
  const result = await db.prepare(`UPDATE coaching_jobs SET state=?,message=COALESCE(?,message),updated_at=?
    WHERE owner_id=? AND run_id=? AND state=? AND created_at>? AND ${live}`)
    .bind(to, message, now(), owner, id, from, now() - 180, now()).run();
  if (result.meta.changes !== 1) throw new CoachingDataError("run_unavailable");
}

/** Synchronous request, durable state. Repeated POSTs only read the existing receipt. */
export async function handleCoaching(request: Request, env: KeyEnv, provider = new CoachingProvider()): Promise<Response> {
  try {
    if (request.headers.has("Origin") || request.headers.has("Cookie")) return reply({ error: "origin_not_allowed" }, 403);
    const url = new URL(request.url), match = /^\/api\/coaching\/([0-9a-f-]+)(\/audio)?$/.exec(url.pathname);
    if (!match || !uuid.test(match[1]!)) return reply({ error: "not_found" }, 404);
    if (url.search) return reply({ error: "invalid_request" }, 400);
    const audio = !!match[2];
    if (!(["GET", ...(audio ? [] : ["POST"])].includes(request.method))) return reply({ error: "method_not_allowed" }, 405);
    const denied = await accessGuard(request, env, true); if (denied) return denied;
    const session = await sessionAccount(request, env); if (!session) return reply({ error: "unauthorized" }, 401);
    const owner = session.account.id, id = match[1]!;
    if (request.method === "POST") {
      let body; try { body = await smallJson(request); } catch { return reply({ error: "invalid_request" }, 400); }
      if (Object.keys(body).join(",") !== "operationId" || typeof body.operationId !== "string" || !uuid.test(body.operationId)) return reply({ error: "invalid_request" }, 400);
      await expire(env.DB, owner, id);
      const existing = await read(env.DB, owner, id); if (existing) return reply(status(existing));
      const key = await env.DB.prepare("SELECT revision,ciphertext,nonce,key_version FROM openai_keys WHERE owner_id=? AND ciphertext IS NOT NULL").bind(owner).first<KeyRow>();
      if (!key) return reply({ error: "key_missing" }, 409);
      const inserted = await env.DB.prepare(`INSERT INTO coaching_jobs (owner_id,run_id,operation_id,state,key_revision,token_hash,created_at,updated_at)
        SELECT ?,?,?,'preparing',?,?,?,? WHERE EXISTS (SELECT 1 FROM run_uploads WHERE owner_id=? AND run_id=? AND completed_at IS NOT NULL)
        AND NOT EXISTS (SELECT 1 FROM run_deletions WHERE owner_id=? AND run_id=?) ON CONFLICT DO NOTHING`)
        .bind(owner, id, body.operationId, key.revision, session.tokenHash, now(), now(), owner, id, owner, id).run();
      if (inserted.meta.changes !== 1) {
        const concurrent = await read(env.DB, owner, id);
        return concurrent ? reply(status(concurrent)) : reply({ error: "run_unavailable" }, 409);
      }
      try {
        const context = await prepareCoachingContext(env.DB, owner, id);
        const saved = await env.DB.prepare(`UPDATE coaching_jobs SET current_hash=?,previous_run_id=?,previous_hash=? WHERE owner_id=? AND run_id=? AND state='preparing' AND ${live}`)
          .bind(context.current.manifestHash, context.previous?.runId ?? null, context.previous?.manifestHash ?? null, owner, id, now()).run();
        if (saved.meta.changes !== 1) throw new CoachingDataError("run_unavailable");
        const plaintext = await unsealKey(env, owner, key);
        await verifyCoachingContext(env.DB, context);
        await provider.countInput(plaintext, context.input);
        await verifyCoachingContext(env.DB, context);
        await advance(env.DB, owner, id, "preparing", "text_pending");
        const message = await provider.text(plaintext, context.input);
        await verifyCoachingContext(env.DB, context);
        await advance(env.DB, owner, id, "text_pending", "speech_pending", message);
        const bytes = await provider.speech(plaintext, message);
        await verifyCoachingContext(env.DB, context);
        const statements: D1PreparedStatement[] = [];
        for (let offset = 0; offset < bytes.length; offset += 131072) {
          statements.push(env.DB.prepare(`INSERT INTO coaching_audio SELECT owner_id,run_id,?,? FROM coaching_jobs
            WHERE owner_id=? AND run_id=? AND state='speech_pending' AND created_at>? AND ${live}`)
            .bind(offset / 131072, bytes.slice(offset, offset + 131072), owner, id, now() - 180, now()));
        }
        statements.push(env.DB.prepare(`UPDATE coaching_jobs SET state='ready',audio_bytes=?,updated_at=? WHERE owner_id=? AND run_id=?
          AND state='speech_pending' AND created_at>? AND ${live}`)
          .bind(bytes.length, now(), owner, id, now() - 180, now()));
        const results = await env.DB.batch(statements);
        if (results.at(-1)?.meta.changes !== 1) throw new CoachingDataError("run_unavailable");
      } catch (error) {
        const uncertain = error instanceof CoachingProviderError && error.outcomeUnknown;
        const code = error instanceof CoachingProviderError || error instanceof CoachingDataError ? error.code : "coaching_unavailable";
        // Once a paid stage started, an unexpected failure is conservatively uncertain.
        await env.DB.prepare(`UPDATE coaching_jobs SET state=CASE WHEN ?=1 OR (state IN ('text_pending','speech_pending') AND ?=1) THEN 'unknown' ELSE 'failed' END,
          error_code=?,updated_at=? WHERE owner_id=? AND run_id=? AND state IN ('preparing','text_pending','speech_pending')`)
          .bind(uncertain ? 1 : 0, error instanceof CoachingProviderError ? 0 : 1, code, now(), owner, id).run();
      }
    } else await expire(env.DB, owner, id);
    // Logout or deletion while generating must not return a late private result.
    if (!await sessionAccount(request, env)) return reply({ error: "unauthorized" }, 401);
    const job = await read(env.DB, owner, id);
    if (!job) return reply({ error: "not_found" }, 404);
    if (!audio) return reply(status(job));
    if (job.state !== "ready") return reply({ error: "audio_unavailable" }, 409);
    if (!job.audio_bytes || job.audio_bytes > 4194304) return reply({ error: "audio_unavailable" }, 503);
    const bytes = new Uint8Array(job.audio_bytes); let offset = 0;
    for (let i = 0; i < Math.ceil(bytes.length / 131072); i++) {
      const chunk = await env.DB.prepare("SELECT data FROM coaching_audio WHERE owner_id=? AND run_id=? AND chunk_index=?")
        .bind(owner, id, i).first<{ data: number[] | ArrayBuffer }>();
      if (!chunk) return reply({ error: "audio_unavailable" }, 503);
      const part = new Uint8Array(chunk.data);
      if (part.length !== Math.min(131072, bytes.length - offset)) return reply({ error: "audio_unavailable" }, 503);
      bytes.set(part, offset); offset += part.length;
    }
    if (offset !== bytes.length || (await read(env.DB, owner, id))?.state !== "ready") return reply({ error: "audio_unavailable" }, 503);
    if (!await sessionAccount(request, env)) return reply({ error: "unauthorized" }, 401);
    return new Response(bytes, { headers: { "Content-Type": "audio/wav", "Content-Length": String(bytes.length), "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });
  } catch { return reply({ error: "coaching_unavailable" }, 503); }
}

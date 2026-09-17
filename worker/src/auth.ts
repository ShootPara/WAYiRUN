import { verifyGoogleToken } from "./google.js";

export interface AuthEnv {
  DB: D1Database; GOOGLE_WEB_CLIENT_ID?: string; GOOGLE_ANDROID_CLIENT_ID?: string;
  AUTH_RATE_LIMIT?: RateLimit; AUTH_TOTAL_LIMIT?: RateLimit;
  RUN_RATE_LIMIT?: RateLimit; RUN_TOTAL_LIMIT?: RateLimit;
}
export const SESSION_SECONDS = 90 * 24 * 60 * 60;
const CHALLENGE_SECONDS = 300;
const opaquePattern = /^[0-9a-f]{64}$/;
function opaque(): string {
  return Array.from(crypto.getRandomValues(new Uint8Array(32)), b => b.toString(16).padStart(2, "0")).join("");
}
export async function hash(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, "0")).join("");
}
export function reply(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: {
    "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff", ...headers,
  } });
}
export async function smallJson(request: Request): Promise<Record<string, unknown>> {
  if (request.headers.get("Content-Type")?.split(";")[0]?.trim() !== "application/json") throw new Error("body");
  const reader = request.body?.getReader();
  if (!reader) throw new Error("body");
  const chunks: Uint8Array[] = []; let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read(); if (done) break;
      size += value.byteLength;
      if (size > 16384) { await reader.cancel(); throw new Error("body"); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  const body: unknown = JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes));
  if (!body || typeof body !== "object" || Array.isArray(body)) throw new Error("body");
  return body as Record<string, unknown>;
}

export async function handleAuth(request: Request, env: AuthEnv): Promise<Response> {
  // Native bearer-token API only. Browser sign-in/cookies require a separate CSRF contract.
  if (request.headers.has("Origin")) return reply({ error: "origin_not_allowed" }, 403);
  const path = new URL(request.url).pathname;
  const methods: Record<string, string> = {
    "/api/auth/challenge": "POST", "/api/auth/google": "POST", "/api/account": "GET", "/api/auth/logout": "POST",
  };
  if (request.method !== methods[path]) return reply({ error: "method_not_allowed" }, 405, { Allow: methods[path]! });
  const now = Math.floor(Date.now() / 1000);
  try {
    const denied = await accessGuard(request, env);
    if (denied) return denied;
    if (path === "/api/auth/challenge") {
      const nonce = opaque();
      await env.DB.batch([
        env.DB.prepare("DELETE FROM login_challenges WHERE expires_at <= ?").bind(now),
        env.DB.prepare("INSERT INTO login_challenges VALUES (?, ?)").bind(await hash(nonce), now + CHALLENGE_SECONDS),
      ]);
      return reply({ nonce, expiresIn: CHALLENGE_SECONDS });
    }
    if (path === "/api/auth/google") {
      let body: Record<string, unknown>;
      try { body = await smallJson(request); } catch { return reply({ error: "invalid_request" }, 400); }
      const { idToken, nonce } = body;
      if (typeof idToken !== "string" || idToken.length > 12000 || typeof nonce !== "string" || !opaquePattern.test(nonce)
          || Object.keys(body).some(key => key !== "idToken" && key !== "nonce")) return reply({ error: "invalid_request" }, 400);
      const nonceHash = await hash(nonce);
      const challenge = await env.DB.prepare("SELECT nonce_hash FROM login_challenges WHERE nonce_hash = ? AND expires_at > ?")
        .bind(nonceHash, now).first();
      if (!challenge) return reply({ error: "invalid_identity" }, 401);
      let identity;
      try { identity = await verifyGoogleToken(idToken, env.GOOGLE_WEB_CLIENT_ID!, nonce, undefined, env.GOOGLE_ANDROID_CLIENT_ID); }
      catch { return reply({ error: "invalid_identity" }, 401); }
      const issuedAt = Math.floor(Date.now() / 1000);
      const token = opaque(); const tokenHash = await hash(token);
      // D1 batch is atomic. The conditional insert and challenge consumption permit just one session,
      // including concurrent exchanges of the same Google token/nonce.
      const results = await env.DB.batch([
        env.DB.prepare(`INSERT INTO accounts VALUES (?, ?, ?, ?, ?, ?)
          ON CONFLICT(google_subject) DO UPDATE SET display_name = excluded.display_name,
          picture_url = excluded.picture_url, updated_at = excluded.updated_at`)
          .bind(crypto.randomUUID(), identity.subject, identity.name, identity.picture, issuedAt, issuedAt),
        env.DB.prepare(`INSERT INTO auth_sessions (token_hash, owner_id, created_at, expires_at)
          SELECT ?, id, ?, ? FROM accounts WHERE google_subject = ?
          AND EXISTS (SELECT 1 FROM login_challenges WHERE nonce_hash = ? AND expires_at > ?)`)
          .bind(tokenHash, issuedAt, issuedAt + SESSION_SECONDS, identity.subject, nonceHash, issuedAt),
        env.DB.prepare("DELETE FROM login_challenges WHERE nonce_hash = ?").bind(nonceHash),
        env.DB.prepare("DELETE FROM auth_sessions WHERE expires_at <= ?").bind(now),
      ]);
      if (results[1]?.meta.changes !== 1) return reply({ error: "invalid_identity" }, 401);
      return reply({ accessToken: token, tokenType: "Bearer", expiresIn: SESSION_SECONDS });
    }
    const verified = await sessionAccount(request, env);
    if (!verified) return reply({ error: "unauthorized" }, 401);
    const { account, tokenHash } = verified;
    if (path === "/api/auth/logout") {
      await env.DB.prepare("UPDATE auth_sessions SET revoked_at = ? WHERE token_hash = ?").bind(now, tokenHash).run();
      return reply({ signedOut: true });
    }
    return reply({ account });
  } catch {
    // Fail closed without leaking a token, identity, SQL error, or provider response to logs.
    return reply({ error: "authentication_unavailable" }, 503);
  }
}

// Shared by native account and run-storage endpoints; all authorization comes from the session.
export async function accessGuard(request: Request, env: AuthEnv, runData = false): Promise<Response | null> {
  if (request.headers.has("Origin")) return reply({ error: "origin_not_allowed" }, 403);
  if (!env.GOOGLE_WEB_CLIENT_ID || !/^[a-zA-Z0-9-]+\.apps\.googleusercontent\.com$/.test(env.GOOGLE_WEB_CLIENT_ID)) {
    return reply({ error: "authentication_not_configured" }, 503);
  }
  const perClient = runData ? env.RUN_RATE_LIMIT : env.AUTH_RATE_LIMIT;
  const total = runData ? env.RUN_TOTAL_LIMIT : env.AUTH_TOTAL_LIMIT;
  if (!perClient || !total) return reply({ error: "authentication_unavailable" }, 503);
  const key = await hash(request.headers.get("CF-Connecting-IP") ?? "unknown-client");
  if (!(await perClient.limit({ key })).success || !(await total.limit({ key: runData ? "runs" : "auth" })).success) {
    return reply({ error: "too_many_requests" }, 429, { "Retry-After": "60" });
  }
  return null;
}
export async function sessionAccount(request: Request, env: AuthEnv) {
  const match = /^Bearer ([0-9a-f]{64})$/i.exec(request.headers.get("Authorization") ?? "");
  if (!match) return null;
  const tokenHash = await hash(match[1]!);
  const account = await env.DB.prepare(`SELECT a.id, a.display_name AS displayName, a.picture_url AS pictureUrl
    FROM accounts a JOIN auth_sessions s ON s.owner_id = a.id
    WHERE s.token_hash = ? AND s.expires_at > ? AND s.revoked_at IS NULL`)
    .bind(tokenHash, Math.floor(Date.now() / 1000)).first<{ id: string; displayName: string | null; pictureUrl: string | null }>();
  return account ? { account, tokenHash } : null;
}

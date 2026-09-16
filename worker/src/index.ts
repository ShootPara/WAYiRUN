import { handleRuns } from "./runs.js";
import { handleAuth, type AuthEnv } from "./auth.js";

export interface Env extends AuthEnv {
  DB: D1Database;
  APP_ENV: string;
  GOOGLE_WEB_CLIENT_ID?: string;
}

function json(body: unknown, status = 200, head = false, extra: Record<string, string> = {}): Response {
  return new Response(head ? null : JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
      ...extra,
    },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const path = new URL(request.url).pathname;
    const head = request.method === "HEAD";
    if (env.APP_ENV !== "development") return json({ error: "environment_not_configured" }, 503, head);

    if (["/api/auth/challenge", "/api/auth/google", "/api/auth/logout", "/api/account"].includes(path)) {
      return handleAuth(request, env);
    }
    if (path === "/api/run-deletions" || path === "/api/runs" || path.startsWith("/api/runs/") || path === "/api/run-uploads" || path.startsWith("/api/run-uploads/")) {
      return handleRuns(request, env);
    }
    if (path === "/api" || path.startsWith("/api/")) {
      return json({ error: "api_not_available" }, 503, head);
    }
    if (path !== "/healthz" && path !== "/readyz") return json({ error: "not_found" }, 404, head);
    if (request.method !== "GET" && !head) return json({ error: "method_not_allowed" }, 405, false, { Allow: "GET, HEAD" });
    if (path === "/healthz") return json({ service: "WAYiRUN", status: "ok" }, 200, head);

    try {
      const metadata = await env.DB.prepare(
        "SELECT application, schema_version FROM service_metadata WHERE singleton = 1",
      ).first<{ application: string; schema_version: number }>();
      if (metadata?.application !== "WAYiRUN" || metadata.schema_version !== 1) {
        return json({ status: "not_ready" }, 503, head);
      }
      return json({ service: "WAYiRUN", status: "ready" }, 200, head);
    } catch {
      // Do not return database details or log request data during failure handling.
      return json({ status: "not_ready" }, 503, head);
    }
  },
} satisfies ExportedHandler<Env>;

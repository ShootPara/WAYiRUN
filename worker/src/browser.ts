import { handleAuth, reply, smallJson, type AuthEnv } from "./auth.js";
import { handleRuns } from "./runs.js";
import page from "../web/index.html";
import script from "../web/app.browserjs";
import style from "../web/style.css";

export const WEB_ORIGIN = "https://wayirun-dev.unopenedparachute.workers.dev";
const sessionName = "__Host-wayirun";
const nonceName = "__Host-wayirun-login";
const opaque = /^[0-9a-f]{64}$/;
function cookie(request: Request, name: string): string | null {
  const values = (request.headers.get("Cookie") ?? "").split(";").map(v => v.trim()).filter(v => v.startsWith(`${name}=`));
  if (values.length !== 1) return null;
  const value = values[0]!.slice(name.length + 1);
  return opaque.test(value) ? value : null;
}
function setCookie(name: string, value: string, seconds: number) {
  return `${name}=${value}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=${seconds}`;
}
function protect(response: Response): Response {
  const out = new Response(response.body, response);
  out.headers.set("Cache-Control", "no-store");
  out.headers.set("X-Content-Type-Options", "nosniff");
  out.headers.set("Referrer-Policy", "no-referrer");
  out.headers.set("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
  out.headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self' https://accounts.google.com/gsi/client; style-src 'self' https://accounts.google.com/gsi/style; connect-src 'self' https://accounts.google.com/gsi/; frame-src https://accounts.google.com/gsi/; img-src 'self' https://*.googleusercontent.com data:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'; form-action 'self'");
  return out;
}
export async function handleBrowser(request: Request, env: AuthEnv): Promise<Response> {
  try { return protect(await route(request, env)); }
  catch { return protect(reply({ error: "web_unavailable" }, 503)); }
}
async function route(request: Request, env: AuthEnv): Promise<Response> {
  const url = new URL(request.url), path = url.pathname;
  if (["/", "/app.js", "/style.css"].includes(path)) {
    if (request.method !== "GET" && request.method !== "HEAD") return reply({ error: "method_not_allowed" }, 405);
    const body = path === "/" ? page : path === "/app.js" ? script : style;
    return new Response(request.method === "HEAD" ? null : body, { headers: { "Content-Type": path === "/" ? "text/html; charset=utf-8" : path === "/app.js" ? "text/javascript; charset=utf-8" : "text/css; charset=utf-8" } });
  }
  // Fixed deployment origin, no wildcard CORS, no bearer or caller-selected owner at this boundary.
  if (url.origin !== WEB_ORIGIN || request.headers.has("Authorization") ||
      request.headers.get("Sec-Fetch-Site") === "cross-site" ||
      (request.headers.has("Origin") && request.headers.get("Origin") !== WEB_ORIGIN)) return reply({ error: "origin_not_allowed" }, 403);
  const authPaths: Record<string, string> = { "/web-api/challenge": "/api/auth/challenge", "/web-api/google": "/api/auth/google",
    "/web-api/account": "/api/account", "/web-api/logout": "/api/auth/logout" };
  const auth = authPaths[path];
  const runs = /^\/web-api\/runs(?:\/[0-9a-f-]+(?:\/chunks\/(0|[1-9][0-9]*))?)?$/.test(path);
  if (!auth && !runs && path !== "/web-api/config") return reply({ error: "not_found" }, 404);
  const mutating = ["/web-api/challenge", "/web-api/google", "/web-api/logout"].includes(path);
  if (request.method !== (mutating ? "POST" : "GET")) return reply({ error: "method_not_allowed" }, 405);
  if (mutating && (request.headers.get("Origin") !== WEB_ORIGIN || request.headers.get("X-WAYIRUN-Request") !== "1")) return reply({ error: "csrf_rejected" }, 403);
  if (path === "/web-api/config") return reply({ clientId: env.GOOGLE_WEB_CLIENT_ID ?? null });
  const headers = new Headers();
  const ip = request.headers.get("CF-Connecting-IP"); if (ip) headers.set("CF-Connecting-IP", ip);
  let body: string | undefined;
  if (path === "/web-api/google") {
    let input: Record<string, unknown>;
    try { input = await smallJson(request); } catch { return reply({ error: "invalid_request" }, 400); }
    const nonce = cookie(request, nonceName);
    if (!nonce || input.nonce !== nonce) return reply({ error: "invalid_identity" }, 401);
    body = JSON.stringify(input); headers.set("Content-Type", "application/json");
  }
  if (path !== "/web-api/challenge" && path !== "/web-api/google") {
    const token = cookie(request, sessionName);
    if (!token) return reply({ error: "unauthorized" }, 401);
    headers.set("Authorization", `Bearer ${token}`);
  }
  const forwarded = new Request(`${WEB_ORIGIN}${auth ?? path.replace("/web-api/", "/api/")}${url.search}`, { method: request.method, headers, ...(body ? { body } : {}) });
  const response = auth ? await handleAuth(forwarded, env) : await handleRuns(forwarded, env);
  if (path === "/web-api/challenge" && response.ok) {
    const data = await response.json() as { nonce: string; expiresIn: number };
    return reply(data, 200, { "Set-Cookie": setCookie(nonceName, data.nonce, 300) });
  }
  if (path === "/web-api/google" && response.ok) {
    const data = await response.json() as { accessToken: string; expiresIn: number };
    const result = reply({ signedIn: true });
    result.headers.append("Set-Cookie", setCookie(sessionName, data.accessToken, data.expiresIn));
    result.headers.append("Set-Cookie", setCookie(nonceName, "", 0));
    return result;
  }
  if (path === "/web-api/logout" && (response.ok || response.status === 401)) {
    return reply({ signedOut: true }, 200, { "Set-Cookie": setCookie(sessionName, "", 0) });
  }
  return response;
}

import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";

// The key endpoint is fixed; neither request data nor runtime configuration can replace it.
const googleKeys = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
export interface GoogleIdentity { subject: string; name: string | null; picture: string | null }

export async function verifyGoogleToken(
  token: string, audience: string, nonce: string, keys: JWTVerifyGetKey = googleKeys,
  androidClientId?: string,
): Promise<GoogleIdentity> {
  const { payload } = await jwtVerify(token, keys, {
    algorithms: ["RS256"], issuer: ["https://accounts.google.com", "accounts.google.com"],
    audience, requiredClaims: ["sub", "iat", "exp", "nonce"], maxTokenAge: "5m",
  });
  if (payload.nonce !== nonce || typeof payload.sub !== "string" || !payload.sub || payload.sub.length > 255) {
    throw new Error("Invalid identity");
  }
  // This backend accepts one Web audience, not multi-audience tokens or another authorized party.
  if (payload.aud !== audience || (payload.azp !== undefined && payload.azp !== audience && payload.azp !== androidClientId)) {
    throw new Error("Invalid audience");
  }
  let picture: string | null = null;
  if (typeof payload.picture === "string" && payload.picture.length <= 2048) {
    try { const url = new URL(payload.picture); if (url.protocol === "https:" && !url.username && !url.password) picture = url.href; } catch { /* Optional profile field. */ }
  }
  return {
    subject: payload.sub,
    name: typeof payload.name === "string" && payload.name.length <= 200 ? payload.name : null,
    picture,
  };
}

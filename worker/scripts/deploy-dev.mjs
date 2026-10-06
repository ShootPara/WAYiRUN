import { readFileSync } from "node:fs";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { assertDevelopmentTarget } from "./deploy-guard.mjs";

const root = fileURLToPath(new URL("../", import.meta.url));
const config = JSON.parse(readFileSync(new URL("../wrangler.jsonc", import.meta.url), "utf8"));
assertDevelopmentTarget(config);
// Do not accept caller-provided routes, environments, or alternate databases.
if (process.argv.length !== 2) throw new Error("deploy:dev accepts no additional arguments.");
const wrangler = fileURLToPath(new URL("../node_modules/wrangler/bin/wrangler.js", import.meta.url));
for (const args of [
  ["deploy", "--dry-run", "--outdir", "build/deploy"],
  ["d1", "migrations", "apply", "DB", "--remote"],
  ["deploy"],
]) {
  const result = spawnSync(process.execPath, [wrangler, ...args], {
    cwd: root, stdio: "inherit", env: { ...process.env, CI: "true", CLOUDFLARE_ACCOUNT_ID: config.account_id },
  });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}

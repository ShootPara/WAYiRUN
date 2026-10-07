import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const wrangler = fileURLToPath(new URL("../node_modules/wrangler/bin/wrangler.js", import.meta.url));
export function d1(database, config, args, { json = false, quiet = false } = {}) {
  const result = spawnSync(process.execPath, [wrangler, "d1", ...args, database, "--remote", "--config", config, ...(json ? ["--json"] : [])], { encoding: "utf8", maxBuffer: 256 * 1024 * 1024 });
  if (result.status !== 0) throw Error((result.stderr || result.stdout || "D1 command failed").trim());
  if (!quiet && result.stderr) process.stderr.write(result.stderr);
  return json ? JSON.parse(result.stdout) : result.stdout;
}

export function query(database, config, sql) {
  const response = d1(database, config, ["execute", "--command", sql], { json: true, quiet: true });
  if (!Array.isArray(response) || response.some(x => !x.success)) throw Error("D1 query failed.");
  return response.flatMap(x => x.results ?? []);
}

export function executeFile(database, config, path) {
  return d1(database, config, ["execute", "--file", path, "--yes"], { quiet: true });
}

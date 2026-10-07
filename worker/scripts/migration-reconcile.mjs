import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { query } from "./migration-cli.mjs";
import { TABLES, tableHash, validateRows } from "./migration-core.mjs";

const [packageArg, database, config = "wrangler.jsonc"] = process.argv.slice(2);
if (!packageArg || !database) throw Error("Usage: migration-reconcile.mjs PACKAGE_DIRECTORY TARGET_DATABASE [CONFIG]");
if (["wayirun-dev-db", "wayirun-prod-db"].includes(database)) throw Error("Reconciliation target must be a disposable rehearsal database.");
const directory = resolve(packageArg), manifest = JSON.parse(readFileSync(join(directory, "manifest.json"), "utf8")), tables = {};
for (const [name, spec] of Object.entries(TABLES)) {
  const rows = [];
  for (let offset = 0;; offset += 25) {
    const page = query(database, config, `SELECT ${spec.columns.join(",")} FROM ${name}${spec.where ? ` WHERE ${spec.where}` : ""} ORDER BY ${spec.order} LIMIT 25 OFFSET ${offset}`);
    rows.push(...page); if (page.length < 25) break;
  }
  tables[name] = rows;
  if (rows.length !== manifest.files[name].rows || tableHash(rows) !== manifest.files[name].canonicalSha256) throw Error(`${name}: reconciliation mismatch`);
}
validateRows(tables);
const excluded = query(database, config, "SELECT (SELECT COUNT(*) FROM login_challenges) login_challenges,(SELECT COUNT(*) FROM auth_sessions) auth_sessions,(SELECT COUNT(*) FROM openai_keys) openai_keys,(SELECT COUNT(*) FROM publication_operations) publication_operations,(SELECT COUNT(*) FROM coaching_jobs WHERE state<>'ready') nonready_coaching,(SELECT COUNT(*) FROM run_weather WHERE snapshot_json IS NULL) unresolved_weather,(SELECT COUNT(*) FROM run_locations WHERE city IS NULL AND region IS NULL AND source IS NULL) unresolved_locations")[0];
if (Object.values(excluded).some(Number)) throw Error("Excluded data appeared in rehearsal target.");
console.log(JSON.stringify({ database, reconciled: true, files: manifest.files, excluded }));

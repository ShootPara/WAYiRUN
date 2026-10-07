import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { basename, join, resolve } from "node:path";
import { execFileSync } from "node:child_process";
import { query } from "./migration-cli.mjs";
import { EXCLUSIONS, SOURCE, TABLES, canonicalLine, fileSha256, sha256, tableHash, validateRows } from "./migration-core.mjs";

const [outputArg, database = SOURCE.name, config = "wrangler.jsonc"] = process.argv.slice(2);
if (!outputArg) throw Error("Usage: migration-export.mjs OUTPUT_DIRECTORY [DATABASE] [CONFIG]");
if (database !== SOURCE.name) throw Error("Export source must be the approved development D1.");
const output = resolve(outputArg);
mkdirSync(output, { recursive: true });
const tables = {};
const files = {};
for (const [name, spec] of Object.entries(TABLES)) {
  const rows = [];
  for (let offset = 0;; offset += 25) {
    const sql = `SELECT ${spec.columns.join(",")} FROM ${name}${spec.where ? ` WHERE ${spec.where}` : ""} ORDER BY ${spec.order} LIMIT 25 OFFSET ${offset}`;
    const page = query(database, config, sql);
    rows.push(...page);
    if (page.length < 25) break;
  }
  tables[name] = rows;
}
validateRows(tables);
for (const [name, rows] of Object.entries(tables)) {
  const path = join(output, `${name}.jsonl`);
  writeFileSync(path, rows.map(canonicalLine).join(""), { encoding: "utf8", flag: "wx", mode: 0o600 });
  files[name] = { file: basename(path), rows: rows.length, sha256: fileSha256(path), canonicalSha256: tableHash(rows) };
}
const ownerSummary = tables.accounts.map(account => ({
  ownerHash: tableHash([{ id: account.id }]),
  runs: tables.run_uploads.filter(x => x.owner_id === account.id).length,
  publications: tables.public_runs.filter(x => x.owner_id === account.id).length,
  photos: tables.run_photos.filter(x => x.owner_id === account.id).length,
  coachingResults: tables.coaching_jobs.filter(x => x.owner_id === account.id).length,
}));
const manifest = {
  format: "wayirun-d1-migration-v1",
  toolVersion: 1,
  source: SOURCE,
  exportedAt: new Date().toISOString(),
  toolCommit: execFileSync("git", ["rev-parse", "HEAD"], { encoding: "utf8", cwd: new URL("../../", import.meta.url) }).trim(),
  toolSourceSha256: sha256(["migration-core.mjs", "migration-cli.mjs", "migration-export.mjs", "migration-import.mjs", "migration-reconcile.mjs"].map(file => readFileSync(new URL(file, import.meta.url))).reduce((all, part) => Buffer.concat([all, part]), Buffer.alloc(0))),
  files,
  ownerSummary,
  totals: { owners: tables.accounts.length, runs: tables.run_uploads.length, chunks: tables.run_chunks.length, photos: tables.run_photos.length, photoBytes: tables.run_photos.reduce((n, x) => n + x.jpeg_hex.length / 2, 0), coachingResults: tables.coaching_jobs.length, coachingAudioChunks: tables.coaching_audio.length, coachingAudioBytes: tables.coaching_audio.reduce((n, x) => n + x.data_hex.length / 2, 0), publications: tables.public_runs.length, deletions: tables.run_deletions.length },
  exclusions: EXCLUSIONS,
};
const manifestPath = join(output, "manifest.json");
writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, { encoding: "utf8", flag: "wx", mode: 0o600 });
writeFileSync(join(output, "manifest.sha256"), `${fileSha256(manifestPath)}  manifest.json\n`, { encoding: "utf8", flag: "wx", mode: 0o600 });
console.log(JSON.stringify({ output, manifestSha256: fileSha256(manifestPath), totals: manifest.totals }));

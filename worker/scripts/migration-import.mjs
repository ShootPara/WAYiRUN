import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { executeFile, query } from "./migration-cli.mjs";
import { TABLES, assertImportTarget, fileSha256, insertSql, validateRows } from "./migration-core.mjs";

const [packageArg, database, config = "wrangler.jsonc", authorization] = process.argv.slice(2);
if (!packageArg || !database) throw Error("Usage: migration-import.mjs PACKAGE_DIRECTORY TARGET_DATABASE [CONFIG] [--production-cutover]");
assertImportTarget(database, JSON.parse(readFileSync(resolve(config), "utf8")), authorization);
const directory = resolve(packageArg);
const manifest = JSON.parse(readFileSync(join(directory, "manifest.json"), "utf8"));
if (manifest.format !== "wayirun-d1-migration-v1") throw Error("Unsupported migration package.");
const tables = {};
for (const name of Object.keys(TABLES)) {
  const path = join(directory, manifest.files[name].file);
  if (fileSha256(path) !== manifest.files[name].sha256) throw Error(`${name}: package hash mismatch`);
  tables[name] = readFileSync(path, "utf8").split(/\r?\n/).filter(Boolean).map(JSON.parse);
  if (tables[name].length !== manifest.files[name].rows) throw Error(`${name}: row-count mismatch`);
}
validateRows(tables);
const existing = query(database, config, `SELECT ${Object.keys(TABLES).map(name => `(SELECT COUNT(*) FROM ${name}) AS ${name}`).join(",")}`)[0];
if (Object.values(existing).some(Number)) throw Error("Rehearsal target is not empty.");
const sqlPath = join(directory, `import-${database}.sql`);
const statements = ["PRAGMA foreign_keys=ON;"];
for (const name of Object.keys(TABLES)) for (const row of tables[name]) statements.push(insertSql(name, row));
writeFileSync(sqlPath, `${statements.join("\n")}\n`, { encoding: "utf8", mode: 0o600 });
executeFile(database, config, sqlPath);
console.log(JSON.stringify({ database, imported: Object.fromEntries(Object.entries(tables).map(([name, rows]) => [name, rows.length])) }));

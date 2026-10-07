import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";

export const SOURCE = Object.freeze({
  name: "wayirun-dev-db",
  id: "04bf8339-386b-4a03-80d7-12b4d1f99ffb",
  migrations: Object.freeze(Array.from({ length: 11 }, (_, i) => String(i + 1).padStart(4, "0"))),
});

const spec = (columns, order, options = {}) => Object.freeze({ columns, order, ...options });
export const TABLES = Object.freeze({
  accounts: spec(["id", "google_subject", "display_name", "picture_url", "created_at", "updated_at"], "id", { primaryKey: ["id"] }),
  run_uploads: spec(["owner_id", "run_id", "operation_id", "manifest_json", "manifest_hash", "chunk_count", "created_at", "expires_at", "completed_at"], "owner_id,run_id", { primaryKey: ["owner_id", "run_id"], where: "completed_at IS NOT NULL" }),
  run_chunks: spec(["owner_id", "run_id", "chunk_index", "sha256", "hex(data) AS data_hex"], "owner_id,run_id,chunk_index", { primaryKey: ["owner_id", "run_id", "chunk_index"], where: "EXISTS(SELECT 1 FROM run_uploads r WHERE r.owner_id=run_chunks.owner_id AND r.run_id=run_chunks.run_id AND r.completed_at IS NOT NULL)", blobs: { data_hex: "data" } }),
  run_deletions: spec(["owner_id", "run_id", "deleted_at"], "owner_id,run_id", { primaryKey: ["owner_id", "run_id"] }),
  coaching_jobs: spec(["owner_id", "run_id", "operation_id", "state", "current_hash", "previous_run_id", "previous_hash", "key_revision", "token_hash", "message", "error_code", "audio_bytes", "created_at", "updated_at"], "owner_id,run_id", { primaryKey: ["owner_id", "run_id"], where: "state='ready'" }),
  coaching_audio: spec(["owner_id", "run_id", "chunk_index", "hex(data) AS data_hex"], "owner_id,run_id,chunk_index", { primaryKey: ["owner_id", "run_id", "chunk_index"], where: "EXISTS(SELECT 1 FROM coaching_jobs j WHERE j.owner_id=coaching_audio.owner_id AND j.run_id=coaching_audio.run_id AND j.state='ready')", blobs: { data_hex: "data" } }),
  run_photos: spec(["owner_id", "run_id", "revision", "hex(jpeg) AS jpeg_hex", "options", "public_token", "updated_at", "weather_json"], "owner_id,run_id", { primaryKey: ["owner_id", "run_id"], blobs: { jpeg_hex: "jpeg" } }),
  run_locations: spec(["owner_id", "run_id", "city", "region", "source", "retry_after"], "owner_id,run_id", { primaryKey: ["owner_id", "run_id"], where: "city IS NOT NULL OR region IS NOT NULL OR source IS NOT NULL" }),
  public_runs: spec(["owner_id", "run_id", "public_token", "short_token", "shared", "photo_visible", "revision", "last_operation"], "owner_id,run_id", { primaryKey: ["owner_id", "run_id"] }),
  run_weather: spec(["owner_id", "run_id", "snapshot_json", "retry_after", "attempt"], "owner_id,run_id", { primaryKey: ["owner_id", "run_id"], where: "snapshot_json IS NOT NULL" }),
});

export const EXCLUSIONS = Object.freeze({
  login_challenges: "ephemeral authentication challenge",
  auth_sessions: "operational session; production sign-in creates a new session",
  openai_keys: "owner decision: development encrypted envelopes never migrate",
  incomplete_run_uploads: "ambiguous staging; source currently has none",
  nonready_coaching_jobs: "no completed coaching result; source has one failed provider-limit attempt",
  nonready_coaching_audio: "audio without a completed coaching result; source currently has none",
  publication_operations: "operational idempotency receipt; stable public_runs state migrates instead",
  unresolved_run_locations: "retryable derived enrichment without a resolved snapshot",
  unresolved_run_weather: "retryable derived enrichment without a resolved snapshot",
  location_lookup_gate: "provider throttle singleton recreated by migrations",
  weather_lookup_gate: "provider throttle singleton recreated by migrations",
  service_metadata: "service singleton recreated by migrations",
});

export const sha256 = value => createHash("sha256").update(value).digest("hex");
export const canonicalLine = row => `${JSON.stringify(row)}\n`;
export const fileSha256 = path => sha256(readFileSync(path));

export function assertImportTarget(database, config, authorization) {
  if (database === SOURCE.name) throw Error("Import target cannot be the development source.");
  if (database !== "wayirun-prod-db") {
    if (authorization !== undefined) throw Error("Production authorization is invalid for a rehearsal target.");
    return;
  }
  const binding = config.d1_databases?.find(value => value.binding === "DB");
  if (authorization !== "--production-cutover" || config.name !== "wayirun-prod" ||
      binding?.database_name !== "wayirun-prod-db" || binding?.database_id !== "e4624be3-14f5-4cbc-939c-90009d377102") {
    throw Error("Production import requires the explicit cutover gate and exact approved target.");
  }
}

export function validateRows(tables) {
  const errors = [];
  const keys = (rows, fn) => new Set(rows.map(fn));
  const duplicate = (name, rows, fn) => { const seen = new Set(); for (const row of rows) { const key = fn(row); if (seen.has(key)) errors.push(`${name}:duplicate:${key}`); seen.add(key); } };
  const owners = keys(tables.accounts, r => r.id);
  const runs = keys(tables.run_uploads, r => `${r.owner_id}\0${r.run_id}`);
  duplicate("accounts", tables.accounts, r => r.id);
  duplicate("run_uploads", tables.run_uploads, r => `${r.owner_id}\0${r.run_id}`);
  for (const [name, rows] of Object.entries(tables)) {
    if (name === "accounts") continue;
    for (const row of rows) {
      if (row.owner_id != null && !owners.has(row.owner_id)) errors.push(`${name}:orphan-owner`);
      if (row.run_id != null && name !== "run_deletions" && !runs.has(`${row.owner_id}\0${row.run_id}`)) errors.push(`${name}:orphan-run`);
    }
  }
  for (const run of tables.run_uploads) {
    const chunks = tables.run_chunks.filter(r => r.owner_id === run.owner_id && r.run_id === run.run_id);
    if (chunks.length !== run.chunk_count) errors.push("run_uploads:missing-chunk");
    chunks.forEach((chunk, index) => { if (chunk.chunk_index !== index) errors.push("run_chunks:noncontiguous"); if (sha256(Buffer.from(chunk.data_hex, "hex")) !== chunk.sha256.toLowerCase()) errors.push("run_chunks:hash-mismatch"); });
  }
  for (const job of tables.coaching_jobs) {
    const audio = tables.coaching_audio.filter(r => r.owner_id === job.owner_id && r.run_id === job.run_id);
    const bytes = audio.reduce((n, row) => n + row.data_hex.length / 2, 0);
    if (job.audio_bytes !== bytes) errors.push("coaching_jobs:audio-size-mismatch");
    audio.forEach((row, index) => { if (row.chunk_index !== index) errors.push("coaching_audio:noncontiguous"); });
  }
  for (const row of tables.run_photos) if (row.jpeg_hex.length / 2 < 4 || row.jpeg_hex.length / 2 > 1_000_000) errors.push("run_photos:invalid-size");
  if (errors.length) throw Error([...new Set(errors)].join(","));
}

export function tableHash(rows) { return sha256(rows.map(canonicalLine).join("")); }

export function sqlLiteral(value, blob = false) {
  if (value === null || value === undefined) return "NULL";
  if (blob) {
    if (!/^(?:[0-9a-fA-F]{2})+$/.test(value)) throw Error("Invalid blob encoding.");
    return `X'${value}'`;
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) throw Error("Invalid numeric value.");
    return String(value);
  }
  return `'${String(value).replaceAll("'", "''")}'`;
}

export function insertSql(name, row) {
  const table = TABLES[name], blobs = table.blobs ?? {};
  const sourceColumns = TABLES[name].columns.map(x => x.includes(" AS ") ? x.split(" AS ")[1] : x);
  const targetColumns = sourceColumns.map(column => blobs[column] ?? column);
  const fragmentBytes = 24_000;
  const values = sourceColumns.map(column => sqlLiteral(Object.hasOwn(blobs, column) ? row[column].slice(0, fragmentBytes * 2) : row[column], Object.hasOwn(blobs, column)));
  const statements = [`INSERT INTO ${name}(${targetColumns.join(",")}) VALUES(${values.join(",")});`];
  for (const [source, target] of Object.entries(blobs)) {
    const where = table.primaryKey.map(column => `${column}=${sqlLiteral(row[column])}`).join(" AND ");
    for (let offset = fragmentBytes * 2; offset < row[source].length; offset += fragmentBytes * 2) statements.push(`UPDATE ${name} SET ${target}=CAST(${target}||X'${row[source].slice(offset, offset + fragmentBytes * 2)}' AS BLOB) WHERE ${where};`);
  }
  return statements.join("\n");
}

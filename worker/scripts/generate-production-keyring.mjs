import { randomBytes } from "node:crypto";
import { chmodSync, mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

if (process.argv.length !== 3) throw Error("Expected one recovery-file path.");
const output = resolve(process.argv[2]);
const keyring = JSON.stringify({ active: "v1", keys: { v1: randomBytes(32).toString("base64") } });
mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, `${keyring}\n`, { encoding: "utf8", flag: "wx", mode: 0o600 });
try { chmodSync(output, 0o600); } catch {}
console.log(`Created ignored production keyring recovery material at ${output}.`);

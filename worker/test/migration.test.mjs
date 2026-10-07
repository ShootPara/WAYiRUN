import test from "node:test";
import assert from "node:assert/strict";
import { EXCLUSIONS, TABLES, assertImportTarget, canonicalLine, insertSql, sha256, tableHash, validateRows } from "../scripts/migration-core.mjs";

const fixture = () => {
  const data = Buffer.from("archive").toString("hex"), audio = Buffer.from("wav").toString("hex"), jpeg = Buffer.from([0xff,0xd8,0xff,0xd9]).toString("hex");
  return {
    accounts:[{id:"a",google_subject:"g",display_name:null,picture_url:null,created_at:1,updated_at:1}],
    run_uploads:[{owner_id:"a",run_id:"r",operation_id:"o",manifest_json:"{}",manifest_hash:"h",chunk_count:1,created_at:1,expires_at:2,completed_at:2}],
    run_chunks:[{owner_id:"a",run_id:"r",chunk_index:0,sha256:sha256(Buffer.from(data,"hex")),data_hex:data}],
    run_deletions:[],
    coaching_jobs:[{owner_id:"a",run_id:"r",operation_id:"c",state:"ready",current_hash:"h",previous_run_id:null,previous_hash:null,key_revision:"k",token_hash:"t",message:"ok",error_code:null,audio_bytes:3,created_at:1,updated_at:2}],
    coaching_audio:[{owner_id:"a",run_id:"r",chunk_index:0,data_hex:audio}],
    run_photos:[{owner_id:"a",run_id:"r",revision:"p",jpeg_hex:jpeg,options:"{}",public_token:null,updated_at:2,weather_json:null}],
    run_locations:[],public_runs:[],run_weather:[],
  };
};
test("migration policy excludes secrets and transient state",()=>{assert.equal(Object.hasOwn(TABLES,"openai_keys"),false);assert.match(EXCLUSIONS.openai_keys,/never migrate/);assert.equal(Object.hasOwn(TABLES,"auth_sessions"),false);});
test("canonical JSONL and hashes are stable",()=>{const row={a:1,b:"x"};assert.equal(canonicalLine(row),canonicalLine(row));assert.equal(tableHash([row]),tableHash([row]));});
test("valid run graph preserves owner, chunk, media and coaching relationships",()=>assert.doesNotThrow(()=>validateRows(fixture())));
test("duplicate IDs and owner crossover fail closed",()=>{const x=fixture();x.accounts.push({...x.accounts[0]});assert.throws(()=>validateRows(x),/duplicate/);const y=fixture();y.run_photos[0].owner_id="other";assert.throws(()=>validateRows(y),/orphan-owner/);});
test("orphan and missing chunk/media fail closed",()=>{const x=fixture();x.run_photos[0].run_id="missing";assert.throws(()=>validateRows(x),/orphan-run/);const y=fixture();y.run_chunks=[];assert.throws(()=>validateRows(y),/missing-chunk/);const z=fixture();z.run_photos[0].jpeg_hex="00";assert.throws(()=>validateRows(z),/invalid-size/);});
test("SQL replay is deterministic and preserves blobs without envelope tables",()=>{const row=fixture().run_chunks[0];assert.equal(insertSql("run_chunks",row),insertSql("run_chunks",row));assert.match(insertSql("run_chunks",row),/X'[0-9a-f]+'/);});
test("large binary replay uses bounded statements",()=>{const x=fixture().run_photos[0];x.jpeg_hex=Buffer.alloc(459202,7).toString("hex");const statements=insertSql("run_photos",x).split("\n");assert.ok(statements.length>1);assert.ok(Math.max(...statements.map(value=>value.length))<50_000);});
test("production import requires the exact explicit cutover target",()=>{const production={name:"wayirun-prod",d1_databases:[{binding:"DB",database_name:"wayirun-prod-db",database_id:"e4624be3-14f5-4cbc-939c-90009d377102"}]};assert.throws(()=>assertImportTarget("wayirun-dev-db",{},undefined),/development/);assert.throws(()=>assertImportTarget("wayirun-prod-db",production,undefined),/explicit/);assert.throws(()=>assertImportTarget("wayirun-prod-db",{...production,name:"other"},"--production-cutover"),/exact/);assert.doesNotThrow(()=>assertImportTarget("wayirun-prod-db",production,"--production-cutover"));assert.doesNotThrow(()=>assertImportTarget("wayirun-m9-rehearsal",{},undefined));});

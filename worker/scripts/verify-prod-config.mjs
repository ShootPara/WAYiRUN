import{readFileSync}from"node:fs";import{spawnSync}from"node:child_process";import{fileURLToPath}from"node:url";import{assertProductionTarget}from"./deploy-prod-guard.mjs";
const root=fileURLToPath(new URL("../",import.meta.url)),real=JSON.parse(readFileSync(new URL("../wrangler.production.jsonc",import.meta.url),"utf8"));
assertProductionTarget(real);
const wrangler=fileURLToPath(new URL("../node_modules/wrangler/bin/wrangler.js",import.meta.url));
const result=spawnSync(process.execPath,[wrangler,"deploy","--dry-run","--outdir","build/deploy-prod-verify","--config","wrangler.production.jsonc"],{cwd:root,stdio:"inherit",env:{...process.env,CI:"true"}});
if(result.error)throw result.error;if(result.status!==0)process.exit(result.status??1);console.log("Resolved production guard and local dry-run passed.");

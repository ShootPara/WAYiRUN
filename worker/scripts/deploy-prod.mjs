import{readFileSync}from"node:fs";import{spawnSync}from"node:child_process";import{fileURLToPath}from"node:url";import{assertProductionTarget}from"./deploy-prod-guard.mjs";
if(process.argv.length!==2)throw Error("deploy:prod accepts no arguments.");
const root=fileURLToPath(new URL("../",import.meta.url)),configUrl=new URL("../wrangler.production.jsonc",import.meta.url),config=JSON.parse(readFileSync(configUrl,"utf8"));
assertProductionTarget(config);const wrangler=fileURLToPath(new URL("../node_modules/wrangler/bin/wrangler.js",import.meta.url));
for(const args of [["deploy","--dry-run","--outdir","build/deploy-prod","--config","wrangler.production.jsonc"],["d1","migrations","apply","DB","--remote","--config","wrangler.production.jsonc"],["deploy","--config","wrangler.production.jsonc"]]){
 const result=spawnSync(process.execPath,[wrangler,...args],{cwd:root,stdio:"inherit",env:{...process.env,CI:"true",CLOUDFLARE_ACCOUNT_ID:config.account_id}});if(result.error)throw result.error;if(result.status!==0)process.exit(result.status??1);
}

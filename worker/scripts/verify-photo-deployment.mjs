// Explicit, disposable development-only canary. Never uses a real user's session or run.
import assert from 'node:assert/strict';
import {randomBytes, randomUUID, createHash} from 'node:crypto';
import {readFileSync, writeFileSync, mkdtempSync, unlinkSync, rmdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
import {assertDevelopmentTarget} from './deploy-guard.mjs';

if(process.argv.slice(2).join(' ')!=='--confirm-development-write') throw Error('Requires --confirm-development-write; creates and removes synthetic development records.');
const root=fileURLToPath(new URL('../',import.meta.url));
const config=JSON.parse(readFileSync(new URL('../wrangler.jsonc',import.meta.url),'utf8'));
assertDevelopmentTarget(config);
const origin='https://wayirun-dev.unopenedparachute.workers.dev';
const owner='photo-canary-'+randomUUID(), id=randomUUID(), token=randomBytes(32).toString('hex');
const sha=x=>createHash('sha256').update(x).digest('hex');
const sql=x=>"'"+String(x).replaceAll("'","''")+"'";
const directory=mkdtempSync(join(tmpdir(),'wayirun-photo-canary-'));
const cleanup=`DELETE FROM accounts WHERE id=${sql(owner)};`;
writeFileSync(join(directory,'cleanup.sql'),cleanup);
function query(statement) {
 const reading=statement.startsWith('SELECT ');
 const file=join(directory,'query.sql');if(!reading)writeFileSync(file,statement);
 const result=spawnSync(process.execPath,[join(root,'node_modules/wrangler/bin/wrangler.js'),'d1','execute','DB','--remote',...(reading?['--command',statement]:['--file',file]),'--json'],
  {cwd:root,encoding:'utf8',env:{...process.env,CI:'true',CLOUDFLARE_ACCOUNT_ID:config.account_id}});
 // SQL contains synthetic records only. Do not print CLI output or bearer credentials.
 if(result.status!==0)throw Error('Development D1 canary query failed; retry with the existing CLI login.');
 return reading?JSON.parse(result.stdout):null;
}
async function call(path,{method='GET',body,headers={},authenticated=true,status=200}={}) {
 const response=await fetch(origin+path,{method,body,headers:{...(authenticated?{Authorization:`Bearer ${token}`} :{}),...headers},
  signal:AbortSignal.timeout(20000),redirect:'manual'});
 assert.equal(response.status,status,`${method} ${path.split('/').slice(0,3).join('/')}: unexpected status`);
 return response;
}
let clean=false;
try {
 const time=Math.floor(Date.now()/1000);
 const summary={state:'FINISHED',startedUtcMs:0,endedUtcMs:1000,activeDurationMs:500,distanceMeters:0,mode:'INDOOR',units:'MILES'};
 const archive={version:1,run:{id,cloudOwnerId:owner,state:'FINISHED',activeSlot:null,checkpoint:JSON.stringify({snapshot:{...summary,runId:id,settings:{mode:'INDOOR',units:'MILES'},segments:[],activeIntervals:[]}})},route:[],measurements:[],splits:[],intervals:[],segments:[]};
 const bytes=Buffer.from(JSON.stringify(archive));
 const operation=randomUUID(),manifest=JSON.stringify({schemaVersion:1,runId:id,operationId:operation,summary,chunks:[{bytes:bytes.length,sha256:sha(bytes)}]});
 query(`INSERT INTO accounts VALUES (${sql(owner)},${sql(owner)},'Synthetic photo deployment check',NULL,${time},${time});
 INSERT INTO auth_sessions(token_hash,owner_id,created_at,expires_at) VALUES (${sql(sha(token))},${sql(owner)},${time},${time+600});
 INSERT INTO run_uploads VALUES (${sql(owner)},${sql(id)},${sql(operation)},${sql(manifest)},${sql(sha(manifest))},1,${time},${time+600},${time});
 INSERT INTO run_chunks VALUES (${sql(owner)},${sql(id)},0,${sql(sha(bytes))},X'${bytes.toString('hex')}');`);
 const jpeg=readFileSync(new URL('../test/photo-fixture.jpg',import.meta.url));
 const base={time:true,distance:true,pace:false,route:false};
 const photoPath=`/api/photos/${id}`,publicationPath=`/api/publications/${id}`;
 let revision=randomUUID();
 async function upload(options) {
  const response=await call(photoPath,{method:'PUT',body:jpeg,headers:{'Content-Type':'image/jpeg','X-Photo-Revision':revision,'X-Photo-Public':'false','X-Photo-Options':JSON.stringify(options)}});
  const photo=(await response.json()).photo;
  assert.equal(photo.sha256,sha(jpeg));assert.equal(photo.bytes,jpeg.length);assert.equal(photo.revision,revision);assert.deepEqual(photo.options,options);
  return photo;
 }
 assert.equal((await upload(base)).publicUrl,null);
 revision=randomUUID();const current={...base,weather:false};
 assert.equal((await upload(current)).publicUrl,null);
 assert.equal((await upload(current)).publicUrl,null);
 assert.equal(sha(Buffer.from(await (await call(photoPath+'/image')).arrayBuffer())),sha(jpeg));
 await call(photoPath+'/image',{authenticated:false,status:401});
 let publication=(await (await call(publicationPath)).json()).publication;
 async function mutate(action,photoVisible) {
  const body={operationId:randomUUID(),expectedRevision:publication.revision,action,...(action==='photo'?{photoVisible}:{})};
  publication=(await (await call(publicationPath,{method:'PUT',body:JSON.stringify(body),headers:{'Content-Type':'application/json'}})).json()).publication;
 }
 assert.equal(publication.shared,false);
 await mutate('share');const shortPath=new URL(publication.publicUrl).pathname;
 const redirect=await call(shortPath,{authenticated:false,status:302});
 const publicPath=new URL(redirect.headers.get('Location'),origin).pathname;
 assert.equal(sha(Buffer.from(await (await call(publicPath+'/image',{authenticated:false})).arrayBuffer())),sha(jpeg));
 await call(publicPath+'/data',{authenticated:false});
 await mutate('photo',false);
 await call(publicPath+'/image',{authenticated:false,status:404});
 await call(publicPath+'/data',{authenticated:false});
 await call(photoPath+'/image');
 await mutate('unshare');
 await call(shortPath,{authenticated:false,status:404});
 await call(publicPath+'/data',{authenticated:false,status:404});
 await call(publicPath+'/image',{authenticated:false,status:404});
 console.log('PASS deployed legacy/current photo uploads, duplicate receipt, exact image bytes, private access, sharing, hide and unshare.');
} finally {
 try {
  query(cleanup);
  const result=query(`SELECT (SELECT COUNT(*) FROM accounts WHERE id=${sql(owner)})+(SELECT COUNT(*) FROM auth_sessions WHERE owner_id=${sql(owner)})+(SELECT COUNT(*) FROM run_uploads WHERE owner_id=${sql(owner)})+(SELECT COUNT(*) FROM run_photos WHERE owner_id=${sql(owner)})+(SELECT COUNT(*) FROM public_runs WHERE owner_id=${sql(owner)}) AS remaining;`);
  assert.equal(result[0].results[0].remaining,0);clean=true;
  console.log('PASS synthetic account, session, run, photo and publication cleanup.');
 } finally {
  if(clean){unlinkSync(join(directory,'query.sql'));unlinkSync(join(directory,'cleanup.sql'));rmdirSync(directory);}
  else console.error(`Cleanup needs attention; exact synthetic-only recovery SQL: ${join(directory,'cleanup.sql')}`);
 }
}

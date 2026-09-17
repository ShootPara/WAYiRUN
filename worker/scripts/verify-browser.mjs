import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { readFileSync, mkdirSync } from "node:fs";
import { createHash, randomUUID } from "node:crypto";
import assert from "node:assert/strict";
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE_PATH || "playwright");
const root=new URL("../web/",import.meta.url),origin="https://wayirun-dev.unopenedparachute.workers.dev";
const sha=value=>createHash("sha256").update(value).digest("hex");
const id=randomUUID(),op=randomUUID();
const settings={mode:"INDOOR",units:"KILOMETERS",countdownSeconds:0,goal:{type:"None"},strideLengthMeters:null};
const snapshot={runId:id,state:"FINISHED",settings,startedUtcMs:1700000000000,endedUtcMs:1700000010000,activeDurationMs:10000,distanceMeters:125,segments:[],activeIntervals:[],splits:[]};
const summary={state:"FINISHED",startedUtcMs:snapshot.startedUtcMs,endedUtcMs:snapshot.endedUtcMs,activeDurationMs:10000,distanceMeters:125,mode:"INDOOR",units:"KILOMETERS"};
const archive={version:1,run:{id,cloudOwnerId:"alice",state:"FINISHED",activeSlot:null,checkpoint:JSON.stringify({snapshot}),zoneId:"UTC"},route:[],measurements:[],segments:[],intervals:[],splits:[{runId:id,number:1,meters:125,durationMs:10000,partial:true}]};
const bytes=Buffer.from(JSON.stringify(archive)),chunks=[bytes.subarray(0,300),bytes.subarray(300)];
const manifest={schemaVersion:1,runId:id,operationId:op,summary,chunks:chunks.map(b=>({bytes:b.length,sha256:sha(b)}))};
const manifestJson=JSON.stringify(manifest),receipt={runId:id,operationId:op,manifestHash:sha(manifestJson),completedAt:1};
let signed=false,corrupt=true;
const browser=await chromium.launch({headless:true,...(process.env.CHROME_PATH?{executablePath:process.env.CHROME_PATH}:{})});
try {
 const page=await browser.newPage({viewport:{width:1365,height:900}});const errors=[];page.on("pageerror",error=>errors.push(error.message));
 await page.route("**/*",async route=>{
  const url=new URL(route.request().url()),path=url.pathname;
  if(url.hostname==="accounts.google.com")return route.fulfill({contentType:"text/javascript",body:`window.google={accounts:{id:{initialize(options){this.options=options},renderButton(element){const b=document.createElement('button');b.textContent='Sign in with Google';b.onclick=()=>this.options.callback({credential:'fixture-token'});element.append(b)},disableAutoSelect(){}}}}`});
  const send=(data,status=200)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(data)});
  if(path==="/")return route.fulfill({contentType:"text/html",body:readFileSync(new URL("index.html",root),"utf8")});
  if(path==="/app.js"||path==="/style.css")return route.fulfill({contentType:path.endsWith(".js")?"text/javascript":"text/css",body:readFileSync(new URL(path.endsWith(".js")?"app.browserjs":"style.css",root),"utf8")});
  const extraAssets={"/route.js":"route.browserjs","/map.js":"map.browserjs","/leaflet.js":"vendor/leaflet.browserjs","/leaflet.css":"vendor/leaflet.css"};
  if(extraAssets[path])return route.fulfill({contentType:path.endsWith(".css")?"text/css":"text/javascript",body:readFileSync(new URL(extraAssets[path],root),"utf8")});
  if(path==="/web-api/config")return send({clientId:"fixture"});
  if(path==="/web-api/challenge")return send({nonce:"a".repeat(64)});
  if(path==="/web-api/google"){signed=true;return send({signedIn:true});}
  if(path==="/web-api/logout"){signed=false;return send({signedOut:true});}
  if(!signed)return send({error:"unauthorized"},401);
  if(path==="/web-api/account")return send({account:{id:"alice",displayName:"Test Runner <script>"}});
  if(path==="/web-api/runs")return send({runs:[{...receipt,summary},{...receipt,runId:randomUUID(),summary:{...summary,distanceMeters:250}}],next:null});
  if(path===`/web-api/runs/${id}`)return send({...receipt,manifest,manifestJson});
  if(path.includes("/chunks/")){const data=chunks[Number(path.split("/").at(-1))];return route.fulfill({contentType:"application/octet-stream",body:corrupt?Buffer.concat([data,Buffer.from('x')]):data});}
  return send({},404);
 });
 await page.goto(origin);await page.getByRole("button",{name:"Sign in with Google",exact:true}).waitFor();
 mkdirSync(new URL("../build/verification/",import.meta.url),{recursive:true});
 await page.screenshot({path:fileURLToPath(new URL("../build/verification/desktop-signin.png",import.meta.url)),fullPage:true});
 await page.getByRole("button",{name:"Sign in with Google",exact:true}).click();await page.locator("#runs tr").nth(1).waitFor();
 assert.equal(await page.locator("#name").textContent(),"Test Runner <script>");assert.equal(await page.locator("#scope").textContent(),"2 runs");
 await page.selectOption("#units","KILOMETERS");assert.match(await page.locator("#totals").textContent(),/0.38 km/);
 await page.screenshot({path:fileURLToPath(new URL("../build/verification/desktop-history.png",import.meta.url)),fullPage:true});
 await page.locator("#runs button").first().click();await page.getByText("This run could not be verified. Its saved data is unchanged.").waitFor();assert.equal(await page.locator("#detail").isVisible(),false);
 corrupt=false;await page.locator("#runs button").first().click();await page.locator("#detail").waitFor();assert.match(await page.locator("#splits").textContent(),/Partial/);
 await page.screenshot({path:fileURLToPath(new URL("../build/verification/desktop-detail.png",import.meta.url)),fullPage:true});
 await page.setViewportSize({width:390,height:844});await page.screenshot({path:fileURLToPath(new URL("../build/verification/desktop-mobile.png",import.meta.url)),fullPage:true});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.getByRole("button",{name:"Sign out",exact:true}).click();await page.locator("#welcome").waitFor();assert.equal(await page.locator("#runs tr").count(),0);assert.equal(await page.locator("#detail").isVisible(),false);
 assert.deepEqual(errors,[]);console.log("PASS browser sign-in, history, totals/units, text safety, corrupt archive rejection, run detail, narrow layout and logout clearing (fixture API only)");
} finally { await browser.close(); }

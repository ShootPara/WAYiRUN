import {createRequire} from "node:module";
import {mkdirSync} from "node:fs";
import {fileURLToPath} from "node:url";
import assert from "node:assert/strict";
const {chromium}=createRequire(import.meta.url)(process.argv[2]||"playwright");
const origin=process.argv[3]||"http://127.0.0.1:8798";
const browser=await chromium.launch({headless:true,channel:"msedge"});
const output=new URL("../build/verification/",import.meta.url);mkdirSync(output,{recursive:true});
try {
 for(const width of [1365,390]){
  const context=await browser.newContext({viewport:{width,height:900}});
  const page=await context.newPage(),errors=[];
  page.on("pageerror",e=>errors.push(e.message));
  await page.addInitScript(()=>{
   Object.defineProperty(navigator,"share",{value:async()=>{throw new DOMException("Cancelled","AbortError");}});
   Object.defineProperty(navigator,"clipboard",{value:{writeText:async text=>{window.copiedLink=text;}}});
  });
  await page.goto(origin);await page.locator("#runs tr").first().getByRole("button").click();
  await page.locator("#publication-state").filter({hasText:/^(Private|Shared)$/}).waitFor();
  // Normalize the fixture for the second viewport without relying on its previous state.
  await page.locator("#unshare-run").click();await page.locator("#publication-state").filter({hasText:/^Private$/}).waitFor();
  await page.waitForFunction(()=>!document.getElementById("photo-visible").disabled);
  if(!await page.locator("#photo-visible").isChecked()){
   const resetPhoto=page.waitForResponse(response=>response.request().method()==="PUT"&&response.url().includes("/web-api/publications/"));
   await page.locator("#photo-visible").check();assert.equal((await resetPhoto).status(),200);
   await page.locator("#publication-state").filter({hasText:/^Private$/}).waitFor();
  }
  await page.locator("#share-run").click();await page.locator("#publication-state").filter({hasText:/^Shared$/}).waitFor();
  const link=await page.locator("#public-run").getAttribute("href");assert.match(link,/\/r\/[0-9a-f]{32}$/);
  await page.locator("#copy-run").click();await page.locator("#publication-state").filter({hasText:/Link copied/}).waitFor();
  assert.equal(await page.evaluate(()=>window.copiedLink),link);
  await page.waitForFunction(()=>!document.getElementById("photo-visible").disabled);
  assert.equal(await page.locator("#photo-visible").isChecked(),true);
  const photoMutation=page.waitForResponse(response=>response.request().method()==="PUT"&&response.url().includes("/web-api/publications/"));
  await page.locator("#photo-visible").uncheck();
  assert.equal((await photoMutation).status(),200);
  assert.equal(await page.locator("#publication-state").textContent(),"Shared");
  const publicPage=await context.newPage();await publicPage.goto(link);await publicPage.locator("#map svg").waitFor();
  assert.equal(await publicPage.locator("#photo").isVisible(),false);
  const long=publicPage.url();assert.equal((await page.request.get(long+"/image")).status(),404);
  await page.locator("#publication-panel").screenshot({path:fileURLToPath(new URL(`publication-owner-${width}.png`,output))});
  await publicPage.screenshot({path:fileURLToPath(new URL(`publication-hidden-photo-${width}.png`,output)),fullPage:true});
  await page.locator("#unshare-run").click();await page.locator("#publication-state").filter({hasText:/^Private$/}).waitFor();
  assert.equal((await page.request.get(long)).status(),404);assert.equal((await page.request.get(link)).status(),404);
  await page.locator("#back").click();await page.locator("#runs tr").nth(1).getByRole("button").click();
  await page.locator("#copy-run").waitFor();await page.waitForFunction(()=>!document.getElementById("copy-run").disabled);
  await page.locator("#copy-run").click();await page.locator("#publication-state").filter({hasText:/Link copied/}).waitFor();
  await publicPage.goto(await page.locator("#public-run").getAttribute("href"));await publicPage.locator("#stats").filter({hasText:/Indoor/}).waitFor();
  assert.equal(await publicPage.locator("#photo").isVisible(),false);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  assert.deepEqual(errors,[]);await context.close();
 }
 const context=await browser.newContext({viewport:{width:900,height:800}}),page=await context.newPage();
 await page.addInitScript(()=>{
  window.shareCalls=0;
  Object.defineProperty(navigator,"share",{value:async()=>{window.shareCalls++;}});
  Object.defineProperty(navigator,"clipboard",{value:{writeText:async()=>{}}});
 });
 await page.goto(origin);await page.locator("#runs tr").nth(2).getByRole("button").click();
 await page.locator("#publication-state").filter({hasText:/^Private$/}).waitFor();
 const endpoint=/\/web-api\/publications\//;
 const conflict=async route=>route.fulfill({status:409,contentType:"application/json",body:JSON.stringify({error:"revision_conflict",publication:{shared:false,photoVisible:true,revision:1,publicUrl:null}})});
 await page.route(endpoint,conflict,{times:1});await page.locator("#share-run").click();
 await page.locator("#publication-state").filter({hasText:/changed elsewhere/}).waitFor();
 assert.equal(await page.locator("#share-run").isDisabled(),false);
 const failed=async route=>route.abort("failed");
 await page.route(endpoint,failed,{times:1});await page.locator("#share-run").click();
 await page.locator("#publication-state").filter({hasText:/Change not confirmed/}).waitFor();
 assert.equal(await page.locator("#public-run").isVisible(),false);
 const delayed=async route=>{await new Promise(resolve=>setTimeout(resolve,250));await route.continue();};
 await page.route(endpoint,delayed,{times:1});await page.locator("#share-run").click();await page.locator("#back").click();
 await page.waitForTimeout(500);assert.equal(await page.evaluate(()=>window.shareCalls),0);
 assert.equal(await page.locator("#detail").isVisible(),false);await context.close();
 console.log("Publication UI checks passed: desktop/mobile share/copy/cancel, hidden image, revocation, photo-free run, conflict, failure and delayed navigation.");
} finally {await browser.close();}

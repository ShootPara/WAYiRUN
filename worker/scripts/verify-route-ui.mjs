import {createRequire} from "node:module";
import {mkdirSync} from "node:fs";
import {fileURLToPath} from "node:url";
import assert from "node:assert/strict";
const require=createRequire(import.meta.url);
const {chromium}=require(process.argv[2] || "playwright");
const origin=process.argv[3] || "http://127.0.0.1:8798";
const browser=await chromium.launch({headless:true,channel:"msedge"});
const output=new URL("../build/verification/",import.meta.url);mkdirSync(output,{recursive:true});
try {
 for(const width of [1365,390]) {
  const page=await browser.newPage({viewport:{width,height:900}}),errors=[],external=[];
  page.on("pageerror",e=>errors.push(e.message));
  page.on("request",r=>{if(new URL(r.url()).origin!==origin)external.push(r.url());});
  await page.goto(origin);
  await page.locator("#runs tr").first().waitFor();
  await page.locator("#runs tr").first().getByRole("button").click();
  await page.locator("#route-map svg").waitFor();
  assert.equal(await page.locator("#route-map polyline").count(),2);
  assert.equal(await page.locator("#fit-route").isVisible(),false);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.locator("#route-map").screenshot({path:fileURLToPath(new URL(`route-private-${width}.png`,output))});
  await page.locator("#back").click();
  assert.equal(await page.locator("#route-map svg").count(),0);
  await page.locator("#runs tr").nth(1).getByRole("button").click();
  await page.locator("#detail").waitFor({state:"visible"});
  assert.equal(await page.locator("#route-panel").isVisible(),false);
  assert.match(await page.locator("#route-note").textContent(),/Indoor/);
  await page.goto(`${origin}/p/${"a".repeat(64)}`);
  await page.locator("#map svg").waitFor();
  assert.equal(await page.locator("#map polyline").count(),2);
  assert.equal(await page.locator("#location").textContent(),"New York, New York");
  assert.equal(await page.locator('#attribution a[href="https://www.openstreetmap.org/copyright"]').count(),1);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:fileURLToPath(new URL(`route-public-${width}.png`,output)),fullPage:true});
  assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
  await page.close();
 }
 console.log("Route browser checks passed: desktop/mobile, private/public, preserved gaps, no-route, cleanup, locality and no external requests.");
} finally {await browser.close();}

import {createRequire} from "node:module";
import {mkdirSync} from "node:fs";
import {fileURLToPath} from "node:url";
import assert from "node:assert/strict";
const require=createRequire(import.meta.url),{chromium}=require(process.argv[2]||"playwright");
const origin=process.argv[3]||"http://127.0.0.1:8798";
const browser=await chromium.launch({headless:true,channel:"msedge"});
const output=new URL("../build/verification/",import.meta.url);mkdirSync(output,{recursive:true});
try {
 for(const width of [390,1365]) {
  const page=await browser.newPage({viewport:{width,height:900}}),errors=[],external=[];
  page.on("pageerror",e=>errors.push(e.message));page.on("request",r=>{if(new URL(r.url()).origin!==origin)external.push(r.url());});
  await page.goto(`${origin}/p/${"a".repeat(64)}`);
  await page.getByRole("link",{name:"Weather data by Open-Meteo.com",exact:true}).waitFor();
  await page.waitForFunction(()=>{const image=document.getElementById("photo");return image.complete&&image.naturalWidth>0;});
  assert.equal(await page.getByRole("link",{name:"CC BY 4.0",exact:true}).getAttribute("href"),"https://creativecommons.org/licenses/by/4.0/");
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
  await page.screenshot({path:fileURLToPath(new URL(`weather-public-${width}.png`,output)),fullPage:true});
  await page.route("**/data",async route=>{const response=await route.fetch(),data=await response.json();data.photoVisible=false;delete data.weather;
   await route.fulfill({response,json:data});});
  await page.reload();await page.locator("#location").filter({hasText:"New York"}).waitFor();
  assert.equal(await page.getByRole("link",{name:"Weather data by Open-Meteo.com",exact:true}).count(),0);
  assert.equal(await page.locator("#photo").isVisible(),false);
  assert.deepEqual(errors,[]);assert.deepEqual(external,[]);await page.close();
 }
 console.log("Weather attribution passed at 390px and 1365px; hidden photos omit credit and no external requests occurred.");
} finally {await browser.close();}

import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const source=readFileSync(new URL("../web/map.browserjs",import.meta.url),"utf8");
const moduleUrl=`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`;

test("late map library completion cannot render after leaving a run or signing out", async () => {
 let pendingScript;
 globalThis.window={};
 globalThis.document={createElement:()=>({}),head:{append:script=>{pendingScript=script;}}};
 try {
  const {showRouteMap}=await import(moduleUrl+"#late");
  let current=true;
  const pending=showRouteMap({}, {}, {}, {}, ()=>current);
  current=false;
  window.L={map:()=>assert.fail("private route must not be rendered")};
  pendingScript.onload();
  assert.equal(await pending,null);
 } finally { delete globalThis.window; delete globalThis.document; }
});
test("map initialization failure removes the partially created map", async () => {
 let removed=false;
 globalThis.window={L:{map:()=>({remove:()=>{removed=true;}}),latLngBounds:()=>{throw new Error("renderer failure");}}};
 try {
  const {showRouteMap}=await import(moduleUrl+"#failure");
  await assert.rejects(showRouteMap({}, {}, {}, {parts:[]}, ()=>true),/renderer failure/);
  assert.equal(removed,true);
 } finally { delete globalThis.window; }
});

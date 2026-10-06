import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const source=readFileSync(new URL("../web/map.browserjs",import.meta.url),"utf8");
const {showRouteMap}=await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
function element(tag) {
 return {tag,attrs:{},children:[],setAttribute(k,v){this.attrs[k]=v;},
  append(child){child.parent=this;this.children.push(child);},
  replaceChildren(...children){this.children=[];children.forEach(c=>this.append(c));},
  remove(){if(this.parent)this.parent.children=this.parent.children.filter(c=>c!==this);}};
}
test("leaving a run or signing out suppresses route rendering",async()=>{
 assert.equal(await showRouteMap(null,null,null,null,()=>false),null);
});
test("route rendering preserves separated paths and cleanup removes only its own graphic",async()=>{
 globalThis.document={createElementNS:(_,tag)=>element(tag)};
 try {
  const root=element("div"),status={},fit={};
  const geometry={parts:[[[40,-74],[40.001,-74]],[[40.002,-73.999],[40.003,-73.999]]],pointCount:4,note:"Recorded"};
  const cleanup=await showRouteMap(root,status,fit,geometry,()=>true);
  const svg=root.children[0];
  assert.equal(svg.children.filter(c=>c.tag==="polyline").length,2);
  assert.equal(svg.children.filter(c=>c.tag==="circle").length,2);
  assert.equal(status.textContent,"Recorded");assert.equal(fit.hidden,true);
  assert.ok(!source.includes("fetch("));assert.ok(!source.includes("tileLayer"));
  await showRouteMap(root,status,fit,geometry,()=>true);
  cleanup();assert.equal(root.children.length,1);
 } finally {delete globalThis.document;}
});
test("single-point normalized routes render a visible centered marker",async()=>{
 globalThis.document={createElementNS:(_,tag)=>element(tag)};
 try {
  const root=element("div");
  await showRouteMap(root,{}, {},{normalized:true,parts:[[[0,0]]],pointCount:1,note:"One point"},()=>true);
  const circles=root.children[0].children.filter(c=>c.tag==="circle");
  assert.ok(circles.length>0);
  assert.equal(circles.at(-1).attrs.cx,"50");assert.equal(circles.at(-1).attrs.cy,"50");
 } finally {delete globalThis.document;}
});

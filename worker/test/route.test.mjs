import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
const source = readFileSync(new URL("../web/route.browserjs", import.meta.url), "utf8");
const { prepareRoute } = await import(`data:text/javascript;base64,${Buffer.from(source).toString("base64")}`);
const segment = (id, start, end, source = "GPS") => ({ id, source, startedMonotonicMs: start, endedMonotonicMs: end });
const point = (id, segmentId, monotonicMs, latitude = 40, longitude = -74) => ({ id, segmentId, monotonicMs, latitude, longitude, accuracyMeters: 5 });

test("route preserves pauses, fallback segments and recording order without joining them", () => {
  const segments = [segment(1,0,10000),segment(2,10000,20000,"STEPS"),segment(3,20000,30000)];
  const points = [point(1,1,0),point(2,1,5000,40.001),point(3,3,20000,40.01),point(4,3,25000,40.011)];
  const before = JSON.stringify({segments,points});
  const route = prepareRoute("OUTDOOR",segments,points);
  assert.deepEqual(route.parts.map(part=>part.length),[2,2]);
  assert.equal(JSON.stringify({segments,points}),before);
});
test("a missing GPS interval over ten seconds is never connected even within one segment", () => {
  const route = prepareRoute("OUTDOOR",[segment(1,0,30000)],[point(1,1,0),point(2,1,10000),point(3,1,20001)]);
  assert.deepEqual(route.parts.map(part=>part.length),[2,1]);
});
test("reboot clock reset is valid in a later segment without joining old and new positions", () => {
  const route = prepareRoute("OUTDOOR",[segment(1,10000,20000),segment(2,0,2000)],[point(1,1,15000),point(2,2,1000)]);
  assert.equal(route.parts.length,2);
});
test("indoor, no GPS and a single point have honest map states", () => {
  assert.deepEqual(prepareRoute("INDOOR",[],[]).parts,[]);
  assert.match(prepareRoute("OUTDOOR",[],[]).note,/No GPS/);
  const single = prepareRoute("OUTDOOR",[segment(1,0,10)],[point(1,1,1)]);
  assert.equal(single.parts[0].length,1);assert.match(single.note,/One GPS point/);
  assert.throws(()=>prepareRoute("INDOOR",[],[point(1,1,1)]));
});
test("date-line route bounds stay local rather than spanning the world", () => {
  const route=prepareRoute("OUTDOOR",[segment(1,0,10)],[point(1,1,1,0,179.999),point(2,1,2,0,-179.999)]);
  assert.ok(Math.abs(route.parts[0][1][1]-route.parts[0][0][1])<0.01);
});
test("invalid, duplicate, reversed or out-of-segment points fail verification", () => {
  const segments=[segment(1,0,10),segment(2,20,30,"STEPS")];
  for(const points of [[point(1,9,1)],[point(1,2,25)],[point(1,1,11)],[point(1,1,1,NaN)],
    [point(1,1,1,91)],[point(1,1,1,0,181)],[point(1,1,-1)],[point(1,1,2),point(2,1,1)],
    [point(1,1,1),point(1,1,2)],[point(1,1,1),point(2,1,1)]]) assert.throws(()=>prepareRoute("OUTDOOR",segments,points));
  assert.throws(()=>prepareRoute("OUTDOOR",[segment(1,0,10),segment(1,20,30)],[]));
  assert.throws(()=>prepareRoute("OUTDOOR",[segment(1,10,0)],[]));
});
test("a route cannot return to an earlier segment in archive order", () => {
  assert.throws(()=>prepareRoute("OUTDOOR",[segment(1,0,10),segment(2,20,30)],
    [point(1,2,21),point(2,1,1)]));
});
test("polar coordinates remain unchanged and report map projection limits", () => {
  const p=point(1,1,1,89);
  const route=prepareRoute("OUTDOOR",[segment(1,0,10)],[p]);
  assert.equal(p.latitude,89);assert.deepEqual(route.parts,[]);assert.match(route.note,/latitude/);
});

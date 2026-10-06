import {test} from "node:test";
import assert from "node:assert/strict";
import {classifyRunQuality} from "../build/run-quality.js";

const run = (duration=300000, distance=1000) => ({run:{checkpoint:{snapshot:{activeDurationMs:duration,distanceMeters:distance}}},measurements:[],route:[]});
const samples = (speed, seconds=30, source="GPS") => Array.from({length:seconds+1},(_,i)=>({segmentId:1,source,monotonicMs:i*1000,activeMs:i*1000,totalMeters:i*speed}));
const points = (speed, seconds=30) => Array.from({length:seconds+1},(_,i)=>({segmentId:1,monotonicMs:i*1000,latitude:0,longitude:i*speed/6371000*180/Math.PI,accuracyMeters:0}));

test("test thresholds are strict and use the exact 0.05-mile boundary",()=>{
 for(const r of [run(60000,500),run(300000,0),run(89999,1000),run(300000,80.46)]) assert.equal(classifyRunQuality(r).label,"likely_test");
 assert.equal(classifyRunQuality(run(90000,0.05*1609.344)).label,"normal");
});
test("normal running and absent evidence do not manufacture vehicle movement",()=>{
 const r=run();r.measurements=samples(4);r.route=points(4);
 assert.equal(classifyRunQuality(r).label,"normal");
 const empty=classifyRunQuality(run());assert.equal(empty.label,"normal");assert.equal(empty.metrics.evaluatedPairs,0);
});
test("vehicle pace requires 25 seconds and works for either measured source",()=>{
 for(const source of ["GPS","STEPS"]){
  const r=run();r.measurements=samples(10,24,source);assert.equal(classifyRunQuality(r).label,"normal");
  r.measurements=samples(10,25,source);const q=classifyRunQuality(r);
  assert.equal(q.label,"likely_vehicle");assert.equal(q.metrics.longestFastMeasurementMs,25000);
 }
 const r=run();r.measurements=samples(1609.344/210,30);assert.equal(classifyRunQuality(r).label,"normal");
});
test("GPS-only vehicle evidence requires accurate contiguous route observations",()=>{
 const r=run();r.route=points(10);assert.equal(classifyRunQuality(r).label,"likely_vehicle");
 r.route=r.route.map(x=>({...x,accuracyMeters:26}));assert.equal(classifyRunQuality(r).label,"normal");
 r.route=points(10).map(x=>({...x,accuracyMeters:5}));assert.equal(classifyRunQuality(r).label,"normal");
});
test("isolated GPS jumps outrank vehicle/test labels without changing retained data",()=>{
 const r=run(60000,5000);r.measurements=samples(4);r.measurements[10].totalMeters+=1000;
 const before=JSON.stringify(r),q=classifyRunQuality(r);assert.equal(q.label,"gps_anomaly");
 assert.ok(q.reasons.includes("active_duration_under_90_seconds"));assert.ok(q.reasons.includes("implausible_gps_distance_time_jump"));
 assert.equal(JSON.stringify(r),before);assert.equal(q.metrics.longestFastMeasurementMs,0);
 const route=run();route.route=points(4);route.route[10].longitude+=0.02;assert.equal(classifyRunQuality(route).label,"gps_anomaly");
});
test("pauses, source changes, missing fields and long gaps break sustained evidence",()=>{
 for(const mutate of [
  xs=>xs.map((x,i)=>({...x,segmentId:i<15?1:2})),
  xs=>xs.map((x,i)=>({...x,source:i<15?"GPS":"STEPS"})),
  xs=>xs.map((x,i)=>i===15?{...x,monotonicMs:null}:x),
  xs=>xs.map((x,i)=>({...x,monotonicMs:x.monotonicMs+(i<15?0:11000),activeMs:x.activeMs+(i<15?0:11000)})),
 ]){const r=run();r.measurements=mutate(samples(10));assert.equal(classifyRunQuality(r).label,"normal");}
});
test("timestamp resets and batched active snapshots cannot invent GPS jumps",()=>{
 const r=run();r.measurements=samples(4).map(x=>({...x,activeMs:0}));assert.equal(classifyRunQuality(r).label,"normal");
 r.measurements=[{segmentId:1,source:"GPS",monotonicMs:10000,activeMs:10000,totalMeters:0},
  {segmentId:2,source:"GPS",monotonicMs:0,activeMs:10000,totalMeters:1000}];
 assert.equal(classifyRunQuality(r).label,"normal");
});
test("long unobserved intervals and one fast observation do not prove sustained movement",()=>{
 const r=run();r.measurements=[samples(10)[0],samples(10)[30]];assert.equal(classifyRunQuality(r).label,"normal");
 r.measurements=samples(10,1);assert.equal(classifyRunQuality(r).label,"normal");
});

import {readFileSync} from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';
const {evaluateAchievements:evaluate,holidays,achievementInput}=await import('data:text/javascript;base64,'+readFileSync(new URL('../web/achievements.browserjs',import.meta.url)).toString('base64'));
const run=(id,date,meters=5000)=>({id,endedMs:Date.parse(date),meters,activeMs:1500000,movement:{[date]:meters},points:[]});
test('missing calendar evidence does not discard verified distance awards',()=>{
 const awards=evaluate([{...run('a','2026-01-01'),movement:{},endDate:'2026-01-01'}]);
 assert.ok(awards.some(a=>a.id==='distance-2'));assert.ok(!awards.some(a=>a.id.startsWith('holiday-')));
});
test('year of history matches the shared Android/browser contract',()=>{
 const fixture=JSON.parse(readFileSync(new URL('../../testdata/achievement-parity.json',import.meta.url),'utf8'));
 assert.deepEqual(evaluate(fixture.runs).map(({id,occurrence,runId,name,date})=>({id,occurrence,runId,name,date})),fixture.expected);
});
test('holiday calendar covers Easter, nth weekdays, overlapping days and actual rather than observed dates',()=>{
 for(const d of ['2024-03-31','2026-04-05','2038-04-25'])assert.equal(holidays(d)[0].id,'easter');
 assert.equal(holidays('2026-11-26')[0].id,'thanksgiving');assert.equal(holidays('2026-05-25')[0].id,'memorial');assert.equal(holidays('2026-07-03').length,0);
 assert.equal(holidays('2022-06-19').length,2);
});
test('thresholds, annual repeatability, duplicate prevention and stable ordering',()=>{
 const runs=[run('a','2026-12-25',50000),run('b','2026-12-25',1000)];const a=evaluate(runs);
 assert.equal(a.filter(x=>x.id.startsWith('distance-')).length,9);assert.equal(a.filter(x=>x.id==='holiday-christmas').length,1);assert.equal(a.filter(x=>x.id==='lifetime-50').length,1);
 assert.deepEqual(evaluate(runs.toReversed()),a);assert.throws(()=>evaluate([runs[0],runs[0]]));assert.equal(evaluate([run('z','2026-01-01',0)]).length,0);
});
test('weekly days are distinct, monthly boundaries are separate and deletion removes unsupported awards',()=>{
 const r=[run('a','2026-09-14'),run('b','2026-09-14'),run('c','2026-09-15'),run('d','2026-09-16')];
 assert.ok(evaluate(r).some(a=>a.id==='week-3'));assert.ok(!evaluate(r.slice(0,-1)).some(a=>a.id==='week-3'));
 const a=evaluate([run('a','2026-01-31',25000),run('b','2026-02-01',25000)]);assert.equal(a.filter(x=>x.id==='month-25').length,2);assert.ok(!a.some(x=>x.id==='month-50'));
});
test('weekly streaks allow rest and performance requires exact measured curves',()=>{
 assert.ok(evaluate([5,12,19,26].map((d,i)=>run(String(i),`2026-01-${String(d).padStart(2,'0')}`))).some(a=>a.id==='streak-4'));
 const a={...run('a','2026-09-14',3000),points:[{meters:0,activeMs:0},{meters:1000,activeMs:360000},{meters:2000,activeMs:660000},{meters:3000,activeMs:900000}]};
 const b={...a,id:'b',endedMs:a.endedMs+1},c={...b,id:'c',endedMs:b.endedMs+1,points:b.points.map(p=>({...p,activeMs:p.activeMs*.9}))};
 const awards=evaluate([a,b,c]);assert.equal(awards.filter(x=>x.id==='pr-0').length,1);assert.equal(awards.find(x=>x.id==='pr-0').runId,'c');assert.ok(awards.some(x=>x.id==='negative-split'));assert.ok(awards.some(x=>x.id==='progression'));
});
test('movement uses frozen time zone and excludes pauses crossing midnight',()=>{
 const start=Date.parse('2026-01-01T04:59:00Z');
 const s={runId:'a',startedUtcMs:start,endedUtcMs:start+180000,activeDurationMs:120000,distanceMeters:20,segments:[{}],activeIntervals:[{startUtcMs:start,startActiveMs:0,endActiveMs:60000},{startUtcMs:start+120000,startActiveMs:60000,endActiveMs:120000}]};
 const input=achievementInput({s,value:{run:{zoneId:'America/New_York'},measurements:[{deltaMeters:10,totalMeters:10,activeMs:30000},{deltaMeters:10,totalMeters:20,activeMs:90000}]}});
 assert.deepEqual(input.movement,{'2025-12-31':10,'2026-01-01':10});assert.equal(input.points.length,0);
 assert.equal(evaluate([input]).filter(a=>a.id.startsWith('holiday-')).length,2);
});

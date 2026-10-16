import test from 'node:test';
import assert from 'node:assert/strict';
import {compareNativeLatency} from './native-latency-summary.mjs';
const run=wallMs=>({scope:'native-task-wall',wallMs,status:'succeeded',verified:true,task:'read switch',device:'phone',network:'fixed',startState:'settings root',requestedModel:'fixed-model',responseModel:'fixed-model',reasoningEffort:'default',warmth:'warm'});
const data=()=>({baseline:[40,44,42].map(run),candidate:[19,21,20].map(run)});
test('uses repeated full-task medians for the half-latency target',()=>{
 const r=compareNativeLatency(data());assert.equal(r.baseline.medianMs,42);assert.equal(r.candidate.medianMs,20);assert.equal(r.halfLatencyReached,true);
});
test('failed or unverified runs cannot establish the performance target',()=>{
 for(const change of [{status:'failed'},{verified:false}]){const d=data();Object.assign(d.candidate[1],change);assert.equal(compareNativeLatency(d).halfLatencyReached,false);}
});
test('rejects mixed models, environments and timing scopes',()=>{
 for(const change of [{responseModel:'other'},{network:'other'},{scope:'tool-only'},{reasoningEffort:'low'},{warmth:'cold'}]){const d=data();Object.assign(d.candidate[0],change);assert.throws(()=>compareNativeLatency(d));}
 assert.throws(()=>compareNativeLatency({baseline:[run(40)],candidate:[run(20)]}));
});

import test from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import { settledFrame } from '../server/midscene/settled-frame.mjs';
async function image(background) {
  return 'data:image/png;base64,' + (await sharp({create:{width:64,height:96,channels:3,background}}).png().toBuffer()).toString('base64');
}
function clock() { let time=0; return {now:()=>time,sleep:async ms=>{time+=ms}}; }
async function marked(points) {
  const data=Buffer.alloc(64*96,255);
  for(const [x,y] of points) data[y*64+x]=0;
  return 'data:image/png;base64,'+(await sharp(data,{raw:{width:64,height:96,channels:1}}).png().toBuffer()).toString('base64');
}
test('small localized changes settle without consuming the unchanged budget',async()=>{
  const old=await image('white');
  const next=await marked([[48,24],[49,24],[50,24],[49,25],[49,26],[49,27]]);
  const result=await settledFrame(async()=>next,old,clock());
  assert.equal(result.observation.state,'stable');
  assert.equal(result.observation.samples,2);
  assert.ok(result.observation.elapsedMs<500);
});
test('status bar changes and isolated image noise do not fake a transition',async()=>{
  const old=await image('white');
  const next=await marked([...Array.from({length:64},(_,x)=>[x,2]),[10,20],[30,40],[50,60]]);
  const result=await settledFrame(async()=>next,old,clock());
  assert.equal(result.observation.state,'unchanged');
});
test('small localized animation must stop before returning stable',async()=>{
  const old=await image('white');
  const next=await marked([[48,24],[49,24],[50,24],[49,25],[49,26],[49,27]]);let n=0;
  const result=await settledFrame(async()=>++n%2?next:old,old,clock());
  assert.equal(result.observation.state,'timeout');
});
test('does not accept repeated old frames before a delayed page transition', async()=>{
  const old=await image('white'), next=await image('black');
  const frames=[old,old,next,next];
  const result=await settledFrame(async()=>frames.shift(),old,clock());
  assert.equal(result.screenshot,next);
  assert.equal(result.observation.state,'stable');
  assert.equal(result.observation.samples,4);
});
test('unchanged screen is bounded and explicitly inconclusive', async()=>{
  const old=await image('white');
  const result=await settledFrame(async()=>old,old,clock());
  assert.equal(result.observation.state,'unchanged');
  assert.ok(result.observation.elapsedMs<=1650);
});
test('continuing animation returns latest frame with timeout, not stable', async()=>{
  const old=await image('white'), next=await image('black');let n=0;
  const result=await settledFrame(async()=>++n%2?next:old,old,clock());
  assert.equal(result.observation.state,'timeout');
  assert.ok(result.observation.samples>1);
});
test('capture failure propagates without replaying a mutation', async()=>{
  const old=await image('white');let calls=0;
  await assert.rejects(settledFrame(async()=>{calls++;throw Error('disconnected')},old,clock()),/disconnected/);
  assert.equal(calls,1);
});

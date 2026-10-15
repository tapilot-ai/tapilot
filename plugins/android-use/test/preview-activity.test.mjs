import test from 'node:test';
import assert from 'node:assert/strict';
import {PreviewActivity} from '../ui/preview-activity.mjs';
function fixture() {
  let callback, delay, suspended = 0, resumed = 0;
  const activity = new PreviewActivity({onSuspend:()=>suspended++, onResume:()=>resumed++,
    setTimer:(fn, ms)=>{callback=fn;delay=ms;return 1;}, clearTimer:()=>{callback=undefined;}});
  return {activity, expire:()=>callback?.(), get delay(){return delay;}, get suspended(){return suspended;}, get resumed(){return resumed;}};
}
test('background preview stops after ten seconds and foreground refreshes immediately',()=>{
  const f=fixture(); f.activity.update(false);
  assert.equal(f.delay,10000); assert.equal(f.activity.canPoll,true);
  f.expire(); assert.equal(f.activity.canPoll,false); assert.equal(f.suspended,1);
  f.activity.update(true); assert.equal(f.activity.canPoll,true); assert.equal(f.resumed,1);
});
test('brief background switch cancels suspension but still requests a fresh frame',()=>{
  const f=fixture(); f.activity.update(false); f.activity.update(false); f.activity.update(true); f.expire();
  assert.equal(f.suspended,0); assert.equal(f.resumed,1); assert.equal(f.activity.canPoll,true);
});
test('closing the preview cancels background timers without resuming it',()=>{
  const f=fixture(); f.activity.update(false); f.activity.close(); f.expire(); f.activity.update(true);
  assert.equal(f.activity.canPoll,false); assert.equal(f.suspended,0); assert.equal(f.resumed,0);
});

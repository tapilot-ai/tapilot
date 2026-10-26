import test from 'node:test';
import assert from 'node:assert/strict';
import { Preview, connectionState, connectionMessage } from '../server/preview.mjs';
import { control, imageSize } from '../server/panel-controls.mjs';
import { imagePoint } from '../ui/geometry.mjs';
const png = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=';
function device() {
  const calls = [];
  return { calls, connected: true, open: async () => {}, request: async (method, params) => {
    calls.push({ method, params });
    if (method === 'device.preview') return { dataUrl: png, capturedAt: Date.now() };
    if (method === 'device.capture') return { geometry: { frameId: 'native-frame', imageWidth: 1, imageHeight: 1 } };
    return {};
  } };
}
async function ready(phone) { const preview = new Preview(); preview.observe(); const frame = (await preview.frame(phone)).structuredContent; return { preview, frame }; }
test('letterboxing and scaling map visible image pixels, excluding margins', () => {
  const rect = { left: 10, top: 20, width: 400, height: 400 };
  assert.deepEqual(imagePoint(rect, 100, 200, 210, 220), { x: 50, y: 100 });
  assert.equal(imagePoint(rect, 100, 200, 109, 220), null);
  assert.equal(imagePoint(rect, 100, 200, 310, 220), null);
  assert.deepEqual(imagePoint(rect, 200, 100, 210, 220), { x: 100, y: 50 });
  assert.equal(imagePoint(rect, 200, 100, 210, 119), null);
  assert.equal(imagePoint({ ...rect, width: 0 }, 200, 100, 210, 220), null);
});
test('manual tap captures once then executes once using the native frame, consuming the panel frame', async () => {
  const phone = device(), { preview, frame } = await ready(phone);
  await preview.control(phone, { frame_id: frame.frame_id, action: 'tap', x: .5, y: .5 });
  assert.deepEqual(phone.calls.map(c => c.method), ['device.preview', 'device.capture', 'device.execute', 'device.preview']);
  assert.deepEqual(phone.calls[2].params, { frameId: 'native-frame', action: { type: 'tap', x: .5, y: .5 } });
  await assert.rejects(preview.control(phone, { frame_id: frame.frame_id, action: 'home' }), /expired/);
  assert.equal(phone.calls.length, 4);
});
test('old, invalidated, paused and expired panel frames cannot operate the phone', async () => {
  const phone = device(), { preview, frame } = await ready(phone);
  await preview.frame(phone);
  await preview.frame(phone);
  await assert.rejects(preview.control(phone, { frame_id: frame.frame_id, action: 'home' }), /expired/);
  await preview.frame(phone); preview.lastFrame.receivedAt -= 11000;
  await assert.rejects(preview.control(phone, { frame_id: preview.lastFrame.id, action: 'home' }), /expired/);
  await preview.frame(phone); preview.invalidate();
  await assert.rejects(preview.control(phone, { frame_id: frame.frame_id, action: 'home' }), /expired/);
  await preview.frame(phone); preview.pause();
  await assert.rejects(preview.control(phone, { frame_id: frame.frame_id, action: 'home' }), /paused/);
  assert.ok(phone.calls.every(c => c.method === 'device.preview'));
});
test('invalid coordinates and malformed actions fail before capture', async () => {
  const phone = device();
  const frame = { id: 'f', width: 100, height: 200, receivedAt: performance.now() };
  for (const args of [{ action: 'tap', x: -1, y: 0 }, { action: 'tap', x: 100, y: 0 }, { action: 'tap', x: 0, y: 200 }, { action: 'shell' }, { action: 'home', unexpected: true }]) {
    await assert.rejects(control(phone, frame, { frame_id: 'f', ...args }));
  }
  assert.equal(phone.calls.length, 0);
});
test('orientation changes stop input after capture; disconnected controls never reconnect', async () => {
  const phone = device();
  const frame = { id: 'f', width: 100, height: 200, receivedAt: performance.now() };
  await assert.rejects(control(phone, frame, { frame_id: 'f', action: 'home' }), /orientation/);
  assert.deepEqual(phone.calls.map(c => c.method), ['device.capture']);
  phone.connected = false;
  phone.open = () => { throw new Error('Must not reconnect'); };
  await assert.rejects(control(phone, frame, { frame_id: 'f', action: 'home' }), /disconnected/);
  assert.equal(phone.calls.length, 1);
});
test('uncertain execution consumes its frame and is never replayed', async () => {
  const phone = device(), original = phone.request;
  let attempts = 0;
  phone.request = async (method, params) => { if (method === 'device.execute') { attempts++; throw new Error('Phone timed out'); } return original(method, params); };
  const { preview, frame } = await ready(phone);
  const args = { frame_id: frame.frame_id, action: 'input', text: '无线输入测试', replace: true };
  await assert.rejects(preview.control(phone, args), /timed out/);
  await assert.rejects(preview.control(phone, args), /expired/);
  assert.equal(attempts, 1);
});
test('swipe and Chinese input preserve user intent; text input never submits', async () => {
  const phone = device(), { preview } = await ready(phone);
  await preview.control(phone, { frame_id: preview.lastFrame.id, action: 'swipe', start_x: .5, start_y: .8, end_x: .5, end_y: .2, duration_ms: 500 });
  await preview.frame(phone);
  await preview.control(phone, { frame_id: preview.lastFrame.id, action: 'input', text: '无线输入测试', replace: false });
  const actions = phone.calls.filter(c => c.method === 'device.execute').map(c => c.params.action);
  assert.deepEqual(actions, [{ type: 'swipe', startX: .5, startY: .8, endX: .5, endY: .2, duration: 500 }, { type: 'input', text: '无线输入测试', replace: false, autoDismissKeyboard: false }]);
});
test('occupied, unpaired and network failures remain distinguishable', async () => {
  for (const [message, state] of [['Another task is already registered', 'occupied'], ['Phone certificate changed', 'pairing_required'], ['Not paired', 'pairing_required'], ['Pair this computer first', 'pairing_required'], ['Phone timed out', 'unavailable']]) {
    assert.equal(connectionState(new Error(message)), state);
    const preview = new Preview(); preview.observe();
    const result = await preview.frame({ open: async () => { throw new Error(message); } });
    assert.equal(result.structuredContent.state, state);
    assert.equal(result.structuredContent.dataUrl, undefined);
  }
});

test('click racing one preview refresh executes once and returns its next frame inline', async () => {
  const phone=device(),{preview,frame}=await ready(phone);
  await preview.frame(phone); // In-flight poll finished after the user's pointerdown.
  const response=await preview.control(phone,{frame_id:frame.frame_id,action:'tap',x:.5,y:.5});
  assert.equal(response.structuredContent.action_completed,true);
  assert.ok(response.structuredContent.dataUrl);
  assert.ok(response.structuredContent.frame_id);
  await assert.rejects(preview.control(phone,{frame_id:frame.frame_id,action:'tap',x:.5,y:.5}),/expired/);
  assert.equal(phone.calls.filter(c=>c.method==='device.execute').length,1);
});
test('post-action preview failure does not turn a completed mutation into a retryable failure', async () => {
  const phone=device(),{preview,frame}=await ready(phone),original=phone.request;
  phone.request=async(method,params)=>{if(method==='device.preview')throw new Error('Phone timed out');return original(method,params);};
  const r=await preview.control(phone,{frame_id:frame.frame_id,action:'home'});
  assert.equal(r.structuredContent.action_completed,true);
  assert.equal(r.structuredContent.state,'unavailable');
  assert.equal(phone.calls.filter(c=>c.method==='device.execute').length,1);
});

// Minimal SOF header exercises metadata parsing without a decoder dependency.
const jpeg = 'data:image/jpeg;base64,' + Buffer.from([255,216,255,192,0,11,8,0,2,0,3,1,1,17,0,255,217]).toString('base64');
test('JPEG preview dimensions are parsed and malformed segments rejected', () => {
  assert.deepEqual(imageSize(jpeg), { width: 3, height: 2 });
  assert.throws(() => imageSize('data:image/jpeg;base64,/9j/wA=='));
  assert.throws(() => imageSize('data:image/jpeg;base64,'));
});
test('negotiated preview uses JPEG and prepares a compact native action frame', async () => {
  const calls = [];
  const phone = { connected: true, features: { jpegPreview: true, compactActionFrame: true }, open: async () => {},
    request: async (method, params) => {
      calls.push({ method, params });
      if (method === 'device.preview') return { dataUrl: jpeg, capturedAt: Date.now() };
      if (method === 'device.prepareAction') return { geometry: { frameId: 'compact-frame', imageWidth: 3, imageHeight: 2 } };
      if (method === 'device.capture') throw new Error('Full screenshot must not be transferred');
      return {};
    }
  };
  const { preview, frame } = await ready(phone);
  const result = await preview.control(phone, { frame_id: frame.frame_id, action: 'tap', x: 1, y: 1 });
  assert.deepEqual(calls.map(call => call.method), ['device.preview', 'device.prepareAction', 'device.execute', 'device.preview']);
  assert.deepEqual(calls[0].params, { format: 'jpeg' });
  assert.equal(calls[2].params.frameId, 'compact-frame');
  assert.equal(result.structuredContent.dataUrl, jpeg);
});

test('drag endpoints outside the image clamp to its edge while outside starts remain invalid', () => {
  const rect = { left: 10, top: 20, width: 100, height: 200 };
  assert.equal(imagePoint(rect, 100, 200, 60, 0), null);
  assert.deepEqual(imagePoint(rect, 100, 200, 60, 0, true), { x: 50, y: 0 });
  assert.deepEqual(imagePoint(rect, 100, 200, 500, 500, true), { x: 99, y: 199 });
});

test('missing local pairing is distinct from revoked pairing and network failure', () => {
  assert.equal(connectionState(Object.assign(new Error('Pair this computer first'), {code:'setup_required'})), 'setup_required');
  assert.equal(connectionState(new Error('certificate changed')), 'pairing_required');
  assert.equal(connectionState(new Error('connect ECONNREFUSED')), 'unavailable');
});

test('privacy pause wins over an in-flight preview, failed preview, and resume', async () => {
  for (const operation of ['frame', 'resume', 'failure']) {
    const preview=new Preview();preview.observe();
    let resolve,reject,started;
    const ready=new Promise(r=>started=r);
    const phone={open:async()=>{},request:()=>{started();return new Promise((a,b)=>{resolve=a;reject=b;});}};
    const pending=operation==='resume'?preview.resume(phone):preview.frame(phone);
    await ready;preview.pause('manual');
    if(operation==='failure')reject(new Error('Unlock the phone first'));
    else resolve({dataUrl:png});
    const value=await pending;
    assert.equal(value.structuredContent.state,'manual');
    assert.equal(value.structuredContent.dataUrl,undefined);
    assert.equal(preview.lastFrame,undefined);
    assert.equal(preview.reason,'manual');
  }
});

test('privacy pause during action preparation prevents the gesture from dispatching', async () => {
  const phone=device(),{preview,frame}=await ready(phone);
  let finish,started;const preparing=new Promise(r=>started=r);
  phone.request=async method=>{
    if(method==='device.capture'){started();return new Promise(r=>finish=r);}
    throw new Error('Gesture must not dispatch');
  };
  const pending=preview.control(phone,{frame_id:frame.frame_id,action:'home'});
  await preparing;preview.pause('manual');
  finish({geometry:{frameId:'new',imageWidth:1,imageHeight:1}});
  await assert.rejects(pending,/paused/);
});

test('missing plugin modules explain host recovery without exposing local paths', async () => {
  const error = Object.assign(new Error("Cannot find module '/private/cache/old/server/discovery.mjs'"), {code:'ERR_MODULE_NOT_FOUND'});
  assert.equal(connectionState(error), 'host_reload_required');
  assert.match(connectionMessage(error), /Reload/);
  assert.doesNotMatch(connectionMessage(error), /private\/cache/);
  const phone=device(); phone.open=async()=>{throw error;};
  const preview=new Preview();preview.observe();
  const response=await preview.frame(phone);
  assert.equal(response.structuredContent.state,'host_reload_required');
  assert.equal(response.structuredContent.dataUrl,undefined);
});

test('independent preview windows retain their own frames and consume all frames on mutation', async () => {
  const phone=device(), preview=new Preview();
  const a=(await preview.frame(phone,'window-a')).structuredContent;
  const b=(await preview.frame(phone,'window-b')).structuredContent;
  await preview.frame(phone,'window-b');
  await preview.frame(phone,'window-b');
  await preview.control(phone,{viewer_id:'window-a',frame_id:a.frame_id,action:'home'});
  assert.equal(phone.calls.filter(c=>c.method==='device.execute').length,1);
  await assert.rejects(preview.control(phone,{viewer_id:'window-b',frame_id:b.frame_id,action:'home'}),/expired/);
});

test('suspending one preview window does not discard another window frame', async () => {
  const phone=device(), preview=new Preview();
  const a=(await preview.frame(phone,'window-a')).structuredContent;
  await preview.frame(phone,'window-b');
  preview.invalidateViewer('window-b');
  await preview.control(phone,{viewer_id:'window-a',frame_id:a.frame_id,action:'back'});
  assert.equal(phone.calls.filter(c=>c.method==='device.execute').length,1);
});

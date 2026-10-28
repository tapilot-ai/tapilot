import { afterEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { DeviceFixtures } from '../device-fixtures.js';
const originalFaults = process.env.TAPILOT_E2E_FAULTS;
afterEach(() => {
  if (originalFaults === undefined) delete process.env.TAPILOT_E2E_FAULTS;
  else process.env.TAPILOT_E2E_FAULTS = originalFaults;
});

function radios() {
  let wifi = true, data = false, airplane = false, failRestore = false;
  const commands: string[] = [];
  const run = (args: string[]) => {
    const command = args.join(' '); commands.push(command);
    if (command === 'shell cmd connectivity airplane-mode') return airplane ? 'enabled' : 'disabled';
    if (command.startsWith('shell cmd connectivity airplane-mode ')) airplane = command.endsWith('enable');
    if (command === 'shell cmd wifi status') return `Wifi is ${wifi ? 'enabled' : 'disabled'}`;
    if (command === 'shell settings get global mobile_data') return data ? '1' : '0';
    if (command === 'shell svc wifi enable' && failRestore) throw new Error('Wi-Fi restore unavailable');
    if (command.startsWith('shell svc wifi ')) wifi = command.endsWith('enable');
    if (command.startsWith('shell svc data ')) data = command.endsWith('enable');
    return '';
  };
  return { run, commands, state: () => ({ wifi, data, airplane }), failRestore: (value: boolean) => { failRestore = value; } };
}

test('radio fault cannot run in the ordinary live profile', async () => {
  delete process.env.TAPILOT_E2E_FAULTS;
  const state = radios(); const fixture = new DeviceFixtures('test', 'lifeos.md.tapilot', state.run);
  await assert.rejects(fixture.offline(), /opt into device faults/);
  assert.equal(state.commands.length, 0);
});

test('cleanup restores original radio choices and is safe to repeat', async () => {
  process.env.TAPILOT_E2E_FAULTS = '1';
  const state = radios(); const fixture = new DeviceFixtures('test', 'lifeos.md.tapilot', state.run);
  await fixture.offline(); assert.deepEqual(state.state(), { wifi: false, data: false, airplane: true });
  await fixture.restore(); assert.deepEqual(state.state(), { wifi: true, data: false, airplane: false });
  const count = state.commands.length; await fixture.restore(); assert.equal(state.commands.length, count);
});

test('one failed radio restoration does not prevent restoring the other and can be retried', async () => {
  process.env.TAPILOT_E2E_FAULTS = '1';
  const state = radios(); const fixture = new DeviceFixtures('test', 'lifeos.md.tapilot', state.run);
  await fixture.offline(); state.failRestore(true);
  await assert.rejects(fixture.restore(), /cleanup failed/);
  assert.equal(state.commands.at(-1), 'shell svc data disable');
  state.failRestore(false); await fixture.restore(); assert.deepEqual(state.state(), { wifi: true, data: false, airplane: false });
});

test('offline fixture accepts airplane mode when an OEM retains mobile_data=1', async () => {
  process.env.TAPILOT_E2E_FAULTS = '1';
  let wifi = true, airplane = false;
  const fixture = new DeviceFixtures('test', 'lifeos.md.tapilot', args => {
    const command = args.join(' ');
    if (command === 'shell cmd wifi status') return `Wifi is ${wifi ? 'enabled' : 'disabled'}`;
    if (command === 'shell settings get global mobile_data') return '1';
    if (command === 'shell cmd connectivity airplane-mode') return airplane ? 'enabled' : 'disabled';
    if (command.startsWith('shell cmd connectivity airplane-mode ')) airplane = command.endsWith('enable');
    if (command.startsWith('shell svc wifi ')) wifi = command.endsWith('enable');
    return '';
  });
  await fixture.offline();
  assert.equal(airplane, true); assert.equal(wifi, false);
  await fixture.restore();
  assert.equal(airplane, false); assert.equal(wifi, true);
});

test('auth fixture refuses the personal main-app package even when opted in', () => {
  const previous = process.env.TAPILOT_E2E_AUTH_FIXTURE;
  process.env.TAPILOT_E2E_AUTH_FIXTURE = '1';
  try {
    for (const pkg of ['lifeos.md.tapilot', 'lifeos.md.tapilot.siwcverify']) {
      assert.throws(() => new DeviceFixtures('test', pkg).requireRejectedAuth(), /isolated/);
    }
    assert.doesNotThrow(() => new DeviceFixtures('test', 'lifeos.md.tapilot.e2everify').requireRejectedAuth());
  }
  finally {
    if (previous === undefined) delete process.env.TAPILOT_E2E_AUTH_FIXTURE;
    else process.env.TAPILOT_E2E_AUTH_FIXTURE = previous;
  }
});


test('task observation waits for the native service rather than a changing screenshot', async () => {
  let polls = 0;
  const fixture = new DeviceFixtures('test', 'lifeos.md.tapilot', () => ++polls === 1
    ? '  * ServiceRecord{123 u0 lifeos.md.tapilot/.TaskRuntimeService}' : 'no matching services');
  const result = await fixture.waitForTaskIdle(new AbortController().signal);
  assert.equal(result.polls, 2);
  assert.equal(result.status, 'native-service-idle');
});

test('task observation aborts without issuing a device command', async () => {
  const controller = new AbortController(); controller.abort(new Error('case cancelled'));
  let polls = 0;
  const fixture = new DeviceFixtures('test', 'lifeos.md.tapilot', () => { polls++; return ''; });
  await assert.rejects(fixture.waitForTaskIdle(controller.signal), /case cancelled/);
  assert.equal(polls, 0);
});

test('task observation times out honestly while the native service is active', async () => {
  const fixture = new DeviceFixtures('test', 'lifeos.md.tapilot', () => '* ServiceRecord{123 u0 lifeos.md.tapilot/.TaskRuntimeService}');
  await assert.rejects(fixture.waitForTaskIdle(new AbortController().signal, 0), /still running/);
});

test('attachment fixture cleanup removes only its own unique document and can be retried', async () => {
  const commands: string[][] = [];
  let failCleanup = true;
  const fixture = new DeviceFixtures('test', 'lifeos.md.tapilot', args => {
    commands.push(args);
    if (args[1] === 'rm' && failCleanup) throw new Error('device unavailable');
    return '';
  });
  fixture.stageAttachment();
  const path = commands[0][2];
  assert.match(path, /^\/sdcard\/Download\/tapilot-attachment-qa-[a-f0-9-]+\.txt$/);
  assert.throws(() => fixture.stageAttachment(), /already active/);
  await assert.rejects(fixture.restore(), /cleanup failed/);
  failCleanup = false;
  await fixture.restore();
  assert.deepEqual(commands.at(-1), ['shell', 'rm', '-f', path]);
  const count = commands.length;
  await fixture.restore();
  assert.equal(commands.length, count);
});

test('text sharing uses the Android share entry of the selected app without submitting a task', () => {
  const commands: string[][] = [];
  new DeviceFixtures('test', 'lifeos.md.tapilot.e2everify', args => { commands.push(args); return ''; }).shareText();
  assert.deepEqual(commands, [['shell', 'am', 'start', '-W', '-n', 'lifeos.md.tapilot.e2everify/lifeos.md.tapilot.MainActivity',
    '-a', 'android.intent.action.SEND', '-t', 'text/plain', '--es', 'android.intent.extra.TEXT', 'SHARED-TEXT-QA']]);
});

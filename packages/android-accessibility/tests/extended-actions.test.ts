import { describe, expect, it } from '@rstest/core';
import { extendedActions } from '../src/extended-actions';
import { AndroidAccessibilityDevice } from '../src/device';
import type { NativeAction, NativeBridge, NativeCapabilities } from '../src/protocol';

const capabilities: NativeCapabilities = {
  protocolVersion: 1, canCapture: true, canPerformGestures: true, canInputText: true,
  systemActions: ['notifications', 'quickSettings', 'saveScreenshot', 'splitScreen', 'lockScreen', 'powerDialog'],
  apps: [{ label: 'Calendar', packageName: 'example.calendar', component: 'example.calendar/.Main' }],
};
function fixture(caps = capabilities) {
  const calls: NativeAction[] = [];
  const actions = extendedActions(async action => { calls.push(action); }, caps);
  return { calls, action: (name: string) => actions.find(a => a.name === name)! };
}
describe('extended device actions', () => {
  it('dispatches every supported system operation', async () => {
    const { calls, action } = fixture();
    for (const name of capabilities.systemActions!) {
      await action(`Android${name[0].toUpperCase()}${name.slice(1)}`).call({});
    }
    expect(calls).toEqual(capabilities.systemActions!.map(action => ({ type: 'system', action })));
  });
  it('omits unavailable system operations', () => {
    const { action } = fixture({ ...capabilities, systemActions: [] });
    expect(action('AndroidSplitScreen')).toBeUndefined();
    expect(action('EditText')).toBeDefined();
  });
  it('keeps a long press and multi-segment drag in one native operation', async () => {
    const { calls, action } = fixture();
    const fingers = [[{ x: 10, y: 20 }, { x: 100, y: 200 }, { x: 150, y: 300 }]];
    await action('MultiTouchGesture').call({ fingers, holdMs: 800, duration: 900 });
    expect(calls).toEqual([{ type: 'gesture', fingers, holdMs: 800, duration: 900 }]);
  });
  it('uses screen width for paging without converting it into a short center swipe', async () => {
    const calls: NativeAction[] = [];
    const device = new AndroidAccessibilityDevice({ getSize: async () => ({ width: 1000, height: 2000 }), execute: async action => { calls.push(action); } } as NativeBridge, capabilities);
    const action = device.actionSpace().find(a => a.name === 'SwipeAcrossScreen')!;
    await action.call({ direction: 'right', y: 1200 });
    expect(calls).toEqual([{ type: 'swipe', startX: 100, endX: 900, startY: 1200, endY: 1200, duration: 350 }]);
    await expect(action.call({ direction: 'left', y: 2000 })).rejects.toThrow('outside screen');
  });
  it('preserves endpoint dwell for page turns and rejects an unbounded hold', async () => {
    const { calls, action } = fixture();
    const fingers = [[{ x: 100, y: 200 }, { x: 390, y: 200 }]];
    await action('MultiTouchGesture').call({ fingers, holdMs: 800, duration: 600, endHoldMs: 1000 });
    expect(calls[0]).toEqual({ type: 'gesture', fingers, holdMs: 800, duration: 600, endHoldMs: 1000 });
    await expect(action('MultiTouchGesture').call({ fingers, endHoldMs: 3001 })).rejects.toThrow();
    expect(calls).toHaveLength(1);
  });
  it('opens app details through a dedicated visible-settings action', async () => {
    const { calls, action } = fixture();
    await action('AndroidOpenAppDetails').call({ packageOrName: 'Calendar' });
    expect(calls).toEqual([{ type: 'appDetails', packageOrName: 'Calendar' }]);
  });
  it('rotates two opposite fingers through an arc', async () => {
    const { calls, action } = fixture();
    await action('Rotate').call({ center: { x: 200, y: 400 }, radius: 50, degrees: 90 });
    const result = calls[0];
    expect(result.type).toBe('gesture');
    if (result.type !== 'gesture') throw new Error('Wrong action');
    expect(result.fingers[0][0]).toEqual({ x: 250, y: 400 });
    expect(result.fingers[0].at(-1)!.y).toBeCloseTo(450);
    expect(result.fingers[1].at(-1)!.y).toBeCloseTo(350);
  });
  it('pans without changing finger separation', async () => {
    const { calls, action } = fixture();
    await action('TwoFingerPan').call({ start: { x: 100, y: 200 }, end: { x: 200, y: 300 }, separation: 40 });
    expect(calls).toEqual([{ type: 'gesture', holdMs: 0, duration: 600, fingers: [
      [{ x: 80, y: 200 }, { x: 180, y: 300 }],
      [{ x: 120, y: 200 }, { x: 220, y: 300 }],
    ] }]);
  });
  it('forwards edit and IME submit operations', async () => {
    const { calls, action } = fixture();
    for (const operation of ['selectAll', 'copy', 'cut', 'paste', 'submit']) await action('EditText').call({ operation });
    await action('EditText').call({ operation: 'select', start: 0, end: 2 });
    await action('EditText').call({ operation: 'replaceSelection', text: '你好' });
    expect(calls.at(-1)).toEqual({ type: 'editText', operation: 'replaceSelection', text: '你好' });
    expect(calls).toHaveLength(7);
  });
  it('rejects invalid gestures before sending to Android', async () => {
    const { calls, action } = fixture();
    await expect(action('MultiTouchGesture').call({ fingers: [], duration: -1 })).rejects.toThrow();
    expect(calls).toHaveLength(0);
  });
  it('loads launcher candidates only on demand and preserves ambiguous matches', async () => {
    const apps = [
      { label: 'Calendar', packageName: 'example.calendar', component: 'example.calendar/.Main' },
      { label: 'Calendar', packageName: 'example.work', component: 'example.work/.Main' },
      { label: 'Other', packageName: 'example.other', component: 'example.other/.Main' },
    ];
    const device = new AndroidAccessibilityDevice({} as NativeBridge, { ...capabilities, apps });
    const actions = device.actionSpace();
    const launch = actions.find(a => a.name === 'Launch')!;
    expect(launch.description).not.toContain('example.calendar');
    expect(launch.description).toContain('SearchApps');
    const search = actions.find(a => a.name === 'SearchApps')!;
    expect(await search.call({ query: 'calendar' })).toEqual(apps.slice(0, 2).map(({label, component}) => ({label, component})));
    expect(await search.call({ query: 'example.other' })).toEqual([{ label: 'Other', component: 'example.other/.Main' }]);
    expect(await search.call({ query: '' })).toHaveLength(3);
    expect(await search.call({ query: 'missing' })).toEqual([]);
  });
});

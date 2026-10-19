import { describe, expect, it } from '@rstest/core';
import { AndroidAccessibilityDevice } from '../src/device';
import type { NativeAction, NativeBridge } from '../src/protocol';

function createBridge(): {
  bridge: NativeBridge;
  actions: Array<{ action: NativeAction; frameId?: string }>;
} {
  const actions: Array<{ action: NativeAction; frameId?: string }> = [];
  const bridge: NativeBridge = {
    getCapabilities: async () => ({
      protocolVersion: 1,
      canCapture: true,
      canPerformGestures: true,
      canInputText: true,
    }),
    capture: async () => ({
      dataUrl: 'data:image/png;base64,AAAA',
      geometry: {
        frameId: 'frame-1',
        capturedAt: 1,
        displayId: 0,
        rotation: 0,
        screenWidth: 400,
        screenHeight: 800,
        imageWidth: 400,
        imageHeight: 800,
      },
    }),
    getSize: async () => ({ width: 400, height: 800 }),
    execute: async (action, frameId) => {
      actions.push({ action, frameId });
    },
    cancelSession: async () => {},
    completeSession: async () => {},
  };
  return { bridge, actions };
}

describe('AndroidAccessibilityDevice', () => {
  it('keeps a long press and drag in one continuous native gesture', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);
    await device.screenshotBase64();
    const drag = device.actionSpace().find(action => action.name === 'DragAndDrop');
    expect(drag).toBeDefined();
    await drag!.call({
      from: { center: [100, 200] },
      to: { center: [300, 400] },
    });
    expect(actions).toEqual([{
      action: { type: 'gesture', fingers: [[{ x: 100, y: 200 }, { x: 300, y: 400 }]], holdMs: 800, duration: 800 },
      frameId: 'frame-1',
    }]);
  });
  it('maps Pinch out and in to simultaneous-finger native parameters', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);
    const pinch = device.actionSpace().find(action => action.name === 'Pinch');
    expect(pinch).toBeDefined();
    await pinch!.call({ direction: 'out', distance: 80, duration: 600 });
    await pinch!.call({ direction: 'in', distance: 80, duration: 600 });
    expect(actions.map(({ action }) => action)).toEqual([
      { type: 'pinch', centerX: 200, centerY: 400, startDistance: 100, endDistance: 180, duration: 600 },
      { type: 'pinch', centerX: 200, centerY: 400, startDistance: 100, endDistance: 20, duration: 600 },
    ]);
  });
  it('still stops repeated drag loops after switching drags to continuous gestures', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);
    const drag = () => device.inputPrimitives.pointer.dragAndDrop!(
      { x: 100, y: 200 }, { x: 300, y: 400 },
    );
    for (let attempt = 0; attempt < 3; attempt += 1) await drag();
    expect(() => drag()).toThrow('repeated Android gesture loop');
    expect(actions).toHaveLength(3);
  });
  it('opens recent apps through a system action rather than a swipe', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);
    const recent = device.actionSpace().find(action => action.name === 'AndroidRecentAppsButton');
    expect(recent).toBeDefined();
    await device.inputPrimitives.system?.recentAppsButton?.();
    expect(actions).toEqual([{ action: { type: 'recentApps' }, frameId: undefined }]);
  });
  it('passes the captured frame to device actions', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);

    await device.screenshotBase64();
    await device.inputPrimitives.pointer.tap({ x: 10, y: 20 });

    expect(actions).toEqual([
      {
        action: { type: 'tap', x: 10, y: 20, duration: undefined },
        frameId: 'frame-1',
      },
    ]);
    expect(await device.size()).toEqual({ width: 400, height: 800 });
  });

  it('stops an identical swipe loop without waiting for the overall replan limit', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);
    const swipe = () =>
      device.inputPrimitives.touch?.swipe(
        { x: 300, y: 400 },
        { x: 80, y: 400 },
      );

    for (let attempt = 0; attempt < 3; attempt += 1) await swipe();
    expect(() => swipe()).toThrow('repeated Android gesture loop');
    expect(actions).toHaveLength(3);

    await device.inputPrimitives.pointer.tap({ x: 20, y: 20 });
    await swipe();
    expect(actions).toHaveLength(5);
  });

  it('stops repeated taps around the same incorrect target', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);

    await device.inputPrimitives.pointer.tap({ x: 620, y: 980 });
    await device.inputPrimitives.pointer.tap({ x: 628, y: 988 });
    await device.inputPrimitives.pointer.tap({ x: 630, y: 994 });

    expect(() =>
      device.inputPrimitives.pointer.tap({ x: 633, y: 997 }),
    ).toThrow('repeated Android gesture loop');
    expect(actions).toHaveLength(3);
  });

  it('exposes mobile actions without the app WebView host', () => {
    const device = new AndroidAccessibilityDevice(createBridge().bridge);
    expect(device.actionSpace().map((action) => action.name)).toEqual(
      expect.arrayContaining(['Tap', 'Input', 'Scroll', 'Launch']),
    );
  });
});

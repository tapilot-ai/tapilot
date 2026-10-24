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
  it('allows changing edge dwell after repeated gestures without dwell', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);
    const gesture = device.actionSpace().find(action => action.name === 'MultiTouchGesture')!;
    const param = { fingers: [[{ x: 100, y: 200 }, { x: 390, y: 200 }]], holdMs: 800, duration: 600 };
    for (let attempt = 0; attempt < 3; attempt += 1) await gesture.call(param);
    await gesture.call({ ...param, endHoldMs: 1000 });
    expect(actions).toHaveLength(4);
  });
  it('stops alternating page swipes and resets after a different action', async () => {
    const { bridge, actions } = createBridge();
    const device = new AndroidAccessibilityDevice(bridge);
    const swipe = device.actionSpace().find(action => action.name === 'SwipeAcrossScreen')!;
    for (let i = 0; i < 6; i += 1) await swipe.call({ direction: i % 2 ? 'right' : 'left', y: 600 });
    await expect(swipe.call({ direction: 'left', y: 600 })).rejects.toThrow('alternating Android swipe loop');
    expect(actions).toHaveLength(6);
    await device.inputPrimitives.pointer.tap({ x: 20, y: 20 });
    await swipe.call({ direction: 'left', y: 600 });
    expect(actions).toHaveLength(8);
  });
  it('blocks a drag that would repeat the same two visual states even as the targets swap', async () => {
    const { bridge, actions } = createBridge();
    const capture = bridge.capture;
    let revision = 0;
    bridge.capture = async () => {
      const frame = await capture();
      const pixels = Buffer.alloc(64 * 96, 40);
      pixels.fill(revision % 2 ? 160 : 80, 64 * 24, 64 * 40);
      pixels.fill(++revision * 20, 0, 64 * 6); // Clock changes must not hide the cycle.
      const frameId = `frame-${revision}`;
      return { ...frame, geometry: { ...frame.geometry!, frameId }, fingerprint: {
        algorithm: 'rgb-luma-64x96-v1', frameId, data: pixels.toString('base64'),
      } };
    };
    const device = new AndroidAccessibilityDevice(bridge);
    const left = { x: 100, y: 200 }, right = { x: 300, y: 200 };
    for (let i = 0; i < 4; i++) {
      await device.screenshotBase64();
      await device.inputPrimitives.pointer.dragAndDrop!(i % 2 ? right : left, i % 2 ? left : right);
    }
    await device.screenshotBase64();
    expect(() => device.inputPrimitives.pointer.dragAndDrop!(left, right)).toThrow('alternating Android drag loop');
    expect(actions).toHaveLength(4);
    await device.inputPrimitives.system!.backButton!();
    await device.inputPrimitives.pointer.dragAndDrop!(left, right);
    expect(actions).toHaveLength(6);
  });
  it('allows repeated opposite drags when the observed content actually progresses', async () => {
    const { bridge, actions } = createBridge();
    const capture = bridge.capture;
    let revision = 0;
    bridge.capture = async () => ({ ...(await capture()), fingerprint: {
      algorithm: 'rgb-luma-64x96-v1', frameId: 'frame-1',
      data: Buffer.alloc(64 * 96, revision++ * 30).toString('base64'),
    } });
    const device = new AndroidAccessibilityDevice(bridge);
    const left = { x: 100, y: 200 }, right = { x: 300, y: 200 };
    for (let i = 0; i < 6; i++) {
      await device.screenshotBase64();
      await device.inputPrimitives.pointer.dragAndDrop!(i % 2 ? right : left, i % 2 ? left : right);
    }
    expect(actions).toHaveLength(6);
  });
  it('allows consecutive page swipes when each observed page changes', async () => {
    const { bridge, actions } = createBridge();
    const capture = bridge.capture;
    let page = 0;
    bridge.capture = async () => {
      const frame = await capture();
      return { ...frame, fingerprint: { algorithm: 'rgb-luma-64x96-v1', frameId: 'frame-1',
        data: Buffer.alloc(64 * 96, page++ * 30).toString('base64') } };
    };
    const device = new AndroidAccessibilityDevice(bridge);
    const swipe = device.actionSpace().find(action => action.name === 'SwipeAcrossScreen')!;
    for (let i = 0; i < 6; i++) {
      await device.screenshotBase64();
      await swipe.call({ direction: 'left', y: 600 });
    }
    expect(actions).toHaveLength(6);
  });
  it('does not mistake clock changes or new frame IDs for navigation progress', async () => {
    const { bridge, actions } = createBridge();
    const capture = bridge.capture;
    let revision = 0;
    bridge.capture = async () => {
      const frame = await capture();
      const pixels = Buffer.alloc(64 * 96, 80);
      pixels.fill(++revision * 30, 0, 64 * 6);
      const frameId = `frame-${revision}`;
      return { ...frame, geometry: { ...frame.geometry!, frameId },
        fingerprint: { algorithm: 'rgb-luma-64x96-v1', frameId, data: pixels.toString('base64') } };
    };
    const device = new AndroidAccessibilityDevice(bridge);
    const swipe = device.actionSpace().find(action => action.name === 'SwipeAcrossScreen')!;
    for (let i = 0; i < 3; i++) {
      await device.screenshotBase64();
      await swipe.call({ direction: 'left', y: 600 });
    }
    await device.screenshotBase64();
    await expect(swipe.call({ direction: 'left', y: 600 })).rejects.toThrow('repeated Android gesture loop');
    expect(actions).toHaveLength(3);
  });
  it('blocks returning through the same two observed pages repeatedly', async () => {
    const { bridge, actions } = createBridge();
    const capture = bridge.capture;
    let page = 0;
    bridge.capture = async () => ({ ...(await capture()), fingerprint: {
      algorithm: 'rgb-luma-64x96-v1', frameId: 'frame-1',
      data: Buffer.alloc(64 * 96, (page++ % 2) * 80).toString('base64'),
    } });
    const device = new AndroidAccessibilityDevice(bridge);
    const swipe = device.actionSpace().find(action => action.name === 'SwipeAcrossScreen')!;
    for (let i = 0; i < 6; i++) {
      await device.screenshotBase64();
      await swipe.call({ direction: i % 2 ? 'right' : 'left', y: 600 });
    }
    await device.screenshotBase64();
    await expect(swipe.call({ direction: 'left', y: 600 })).rejects.toThrow('alternating Android swipe loop');
    expect(actions).toHaveLength(6);
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

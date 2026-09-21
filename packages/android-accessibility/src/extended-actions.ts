import { defineAction, type DeviceAction } from '@midscene/core/device';
import { z } from '@midscene/core';
import type { NativeAction, NativeCapabilities } from './protocol';

const point = z.object({ x: z.number().finite().nonnegative(), y: z.number().finite().nonnegative() });
const duration = z.number().int().min(1).max(10000).default(600);
const gesture = z.object({
  fingers: z.array(z.array(point).min(2).max(128)).min(1).max(10),
  duration,
  holdMs: z.number().int().min(0).max(5000).default(0),
});
const rotate = z.object({ center: point, radius: z.number().positive(), degrees: z.number().min(-360).max(360), duration });
const pan = z.object({ start: point, end: point, separation: z.number().positive().default(80), duration });
const edit = z.object({
  operation: z.enum(['selectAll', 'select', 'replaceSelection', 'copy', 'cut', 'paste', 'submit']),
  start: z.number().int().nonnegative().optional(),
  end: z.number().int().nonnegative().optional(),
  text: z.string().optional(),
});
const appDetails = z.object({
  packageOrName: z.string().min(1).describe('Installed app label or Android package name'),
});
const systemDescriptions: Record<string, string> = {
  notifications: 'Open system notification shade',
  quickSettings: 'Open system Quick Settings panel (not app pull-to-refresh)',
  splitScreen: 'Toggle system split screen. Only for apps and devices supporting it',
  lockScreen: 'Lock screen, only when explicitly requested. Cannot unlock or bypass authentication',
  powerDialog: 'Open power menu; does not itself power off or restart',
  saveScreenshot: 'Capture and save a PNG screenshot to the gallery under Pictures/Tapilot, hiding assistant overlays. Returns after the image is saved; secure screens may reject capture. Unlike internal model screenshots, this creates a user-visible file',
};

export function extendedActions(execute: (action: NativeAction) => Promise<void>, capabilities?: NativeCapabilities): DeviceAction[] {
  return [
    ...Object.entries(systemDescriptions)
      .filter(([name]) => !capabilities?.systemActions || capabilities.systemActions.includes(name))
      .map(([name, description]) => defineAction({
        name: `Android${name[0].toUpperCase()}${name.slice(1)}`,
        description,
        paramSchema: z.object({}),
        call: async () => execute({ type: 'system', action: name }),
      })),
    defineAction({
      name: 'AndroidOpenAppDetails',
      description: 'Open the Android system app-details page for an installed app. Prefer this for force-stop or uninstall requests instead of visually hunting for an app icon. After opening, visually locate the requested Force stop / 强行停止 or Uninstall / 卸载 control and confirm it. This action only opens Settings and never stops or uninstalls an app by itself.',
      paramSchema: appDetails,
      call: async (param) => execute({ type: 'appDetails', ...appDetails.parse(param) }),
    }),
    defineAction({
      name: 'MultiTouchGesture',
      description: 'Continuous screen-coordinate paths, one path per finger, all simultaneous. For long-press then drag (icons/reordering), use one finger and holdMs=800; contact stays down between hold and movement. For curved/multi-segment drags add intermediate points. Coordinates must stay on screen. Inspect result afterwards.',
      paramSchema: gesture,
      call: async (param) => execute({ type: 'gesture', ...gesture.parse(param) }),
    }),
    defineAction({
      name: 'Rotate',
      description: 'Rotate a map/image with two fingers around center; positive degrees clockwise. Radius and full circle must fit within target and screen. Verify target supports rotation.',
      paramSchema: rotate,
      call: async (param) => {
        const { center, radius, degrees, duration } = rotate.parse(param);
        const fingers = [0, Math.PI].map(offset => Array.from({ length: 37 }, (_, i) => {
          const angle = offset + degrees * Math.PI / 180 * i / 36;
          return { x: center.x + radius * Math.cos(angle), y: center.y + radius * Math.sin(angle) };
        }));
        await execute({ type: 'gesture', fingers, duration, holdMs: 0 });
      },
    }),
    defineAction({
      name: 'TwoFingerPan',
      description: 'Move two fingers together from start to end, maintaining horizontal separation. For canvas/map two-finger panning, not zoom. Both paths must remain within screen and target.',
      paramSchema: pan,
      call: async (param) => {
        const { start, end, separation, duration } = pan.parse(param);
        await execute({ type: 'gesture', duration, holdMs: 0, fingers: [-1, 1].map(sign => [start, end].map(p => ({ x: p.x + sign * separation / 2, y: p.y }))) });
      },
    }),
    defineAction({
      name: 'EditText',
      description: 'Edit currently focused text: selectAll, select a UTF-16 start/end range, replaceSelection with text, copy/cut/paste, or submit the current keyboard Search/Send/Done action. Focus field first. submit may send data, so only use for user-requested submission. Unsupported fields report errors.',
      paramSchema: edit,
      call: async (param) => execute({ type: 'editText', ...edit.parse(param) }),
    }),
  ];
}

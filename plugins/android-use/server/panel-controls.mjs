import { AjvJsonSchemaValidator } from '@modelcontextprotocol/sdk/validation/ajv';
import { callTool } from './tools.mjs';

const coordinate = { type: 'number', minimum: 0 };
const base = { frame_id: { type: 'string', minLength: 1 } };
const variant = (action, properties = {}, required = Object.keys(properties)) => ({
  type: 'object', properties: { ...base, action: { const: action }, ...properties },
  required: ['frame_id', 'action', ...required], additionalProperties: false,
});
export const CONTROL_SCHEMA = { type: 'object', oneOf: [
  variant('tap', { x: coordinate, y: coordinate }),
  variant('swipe', { start_x: coordinate, start_y: coordinate, end_x: coordinate, end_y: coordinate, duration_ms: { type: 'integer', minimum: 100, maximum: 2000 } }),
  variant('input', { text: { type: 'string', minLength: 1, maxLength: 10000 }, replace: { type: 'boolean' } }, ['text']),
  ...['home', 'back', 'recentApps', 'enter'].map(action => variant(action)),
] };
const validate = new AjvJsonSchemaValidator().getValidator(CONTROL_SCHEMA);

export function imageSize(dataUrl) {
  if (typeof dataUrl !== 'string' || !dataUrl.startsWith('data:image/png;base64,')) throw new Error('Invalid preview frame');
  const png = Buffer.from(dataUrl.slice(22), 'base64');
  if (png.length < 24 || !png.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) || png.toString('ascii', 12, 16) !== 'IHDR') throw new Error('Invalid preview PNG');
  const width = png.readUInt32BE(16), height = png.readUInt32BE(20);
  if (!width || !height || width > 16384 || height > 16384) throw new Error('Invalid preview dimensions');
  return { width, height };
}

export async function control(phone, frame, args) {
  const validation = validate(args);
  if (!validation.valid) throw new Error(`Invalid screen control: ${validation.errorMessage}`);
  if (!frame || args.frame_id !== frame.id || performance.now() - frame.receivedAt > 10000) throw new Error('Screen changed or expired. Wait for a fresh frame and try again.');
  if (!phone.connected) throw new Error('Phone disconnected. Reconnect and inspect before trying another action.');
  for (const field of ['x', 'start_x', 'end_x']) if (args[field] !== undefined && args[field] >= frame.width) throw new Error('Coordinate outside screenshot');
  for (const field of ['y', 'start_y', 'end_y']) if (args[field] !== undefined && args[field] >= frame.height) throw new Error('Coordinate outside screenshot');
  // Manual input invalidates previous model coordinates. Obtain the phone's
  // action-frame token immediately before sending one user-requested mutation.
  // Neither capture nor mutation is retried after a lost connection.
  const { geometry } = await phone.request('device.capture');
  if (geometry.imageWidth !== frame.width || geometry.imageHeight !== frame.height) throw new Error('Screen orientation or size changed. Wait for a fresh frame.');
  const common = { frame_id: geometry.frameId };
  if (args.action === 'tap') return callTool(phone, 'tapilot_tap', { ...common, x: args.x, y: args.y });
  if (args.action === 'swipe') return callTool(phone, 'tapilot_swipe', { ...common, start_x: args.start_x, start_y: args.start_y, end_x: args.end_x, end_y: args.end_y, duration_ms: args.duration_ms });
  if (args.action === 'input') return callTool(phone, 'tapilot_type_text', { ...common, text: args.text, replace: args.replace ?? true });
  return callTool(phone, 'tapilot_press', { button: args.action });
}

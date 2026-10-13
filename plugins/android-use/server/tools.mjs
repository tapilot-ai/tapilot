import { AjvJsonSchemaValidator } from '@modelcontextprotocol/sdk/validation/ajv';
const text = value => ({ content: [{ type: 'text', text: JSON.stringify(value) }] });
const number = { type: 'number', minimum: 0 };
const string = { type: 'string', minLength: 1 };
const frame = { frame_id: { ...string, description: 'frameId from the most recent tapilot_screen. Use coordinates from that exact image.' } };
const schema = (properties = {}, required = Object.keys(properties)) => ({ type: 'object', properties, required, additionalProperties: false });
const tool = (name, description, properties, readOnlyHint = false, required) => ({ name, description, inputSchema: schema(properties, required), annotations: { readOnlyHint, destructiveHint: !readOnlyHint, idempotentHint: false, openWorldHint: true } });
export const tools = [
  tool('tapilot_status', 'Connect to the paired Android phone and list its capabilities and launchable apps. Reserves the phone until disconnect or 2 minutes idle. No model API key or ADB is used.', {}, true),
  tool('tapilot_screen', 'Capture the unlocked phone screen. Returns the actual image and frameId; inspect this image before operating. Screenshots are shared with ChatGPT.', {}, true),
  tool('tapilot_tap', 'Tap once at image pixel coordinates. Requires the latest screenshot. Observe the result before retrying.', { ...frame, x: number, y: number }),
  tool('tapilot_swipe', 'Swipe from start to end in image pixels. Finger up reveals lower content. Capture again after the gesture.', { ...frame, start_x: number, start_y: number, end_x: number, end_y: number, duration_ms: { type: 'integer', minimum: 100, maximum: 2000, default: 500 } }, false, ['frame_id', 'start_x', 'start_y', 'end_x', 'end_y']),
  tool('tapilot_type_text', 'Enter text into the currently focused editable field. Supports Chinese. Does not press Send. Capture first; replace defaults to true.', { ...frame, text: { type: 'string', maxLength: 10000 }, replace: { type: 'boolean', default: true } }, false, ['frame_id', 'text']),
  tool('tapilot_press', 'Press a navigation button, or submit the focused keyboard action with enter. Only submit when the user requested it.', { button: { type: 'string', enum: ['home', 'back', 'recentApps', 'enter'] } }),
  tool('tapilot_launch_app', 'Launch an app using its exact package name from tapilot_status. Capture afterward to verify the app opened.', { package_name: { type: 'string', pattern: '^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$' } }),
  tool('tapilot_disconnect', 'Release the phone only when the user explicitly asks to disconnect or release it. Do not call merely because a task is complete; keep the live screen available. Wireless mode remains enabled on the phone.', {}, false),
];

const validator = new AjvJsonSchemaValidator();
const validators = new Map(tools.map(tool => [tool.name, validator.getValidator(tool.inputSchema)]));
export async function callTool(phone, name, args) {
  const validate = validators.get(name);
  if (!validate) throw new Error('Unknown Tapilot tool');
  const validation = validate(args);
  if (!validation.valid) throw new Error(`Invalid tool arguments: ${validation.errorMessage}`);
  if (name === 'tapilot_disconnect') {
    try { if (phone.connected) await phone.request('disconnect'); } finally { phone.close(); }
    return text({ disconnected: true });
  }
  // Only observation can reconnect. Never retry a mutation, or reuse an old screen across reconnects.
  if (name === 'tapilot_status' || name === 'tapilot_screen') await phone.open();
  if (name === 'tapilot_status') return text(await phone.request('device.getCapabilities'));
  if (name === 'tapilot_screen') {
    const { dataUrl, geometry } = await phone.request('device.capture');
    if (typeof dataUrl !== 'string' || !dataUrl.startsWith('data:image/png;base64,')) throw new Error('Phone returned an invalid screenshot');
    return { content: [{ type: 'text', text: JSON.stringify(geometry) }, { type: 'image', mimeType: 'image/png', data: dataUrl.slice('data:image/png;base64,'.length) }] };
  }
  let action;
  switch (name) {
    case 'tapilot_tap': action = { type: 'tap', x: args.x, y: args.y }; break;
    case 'tapilot_swipe': action = { type: 'swipe', startX: args.start_x, startY: args.start_y, endX: args.end_x, endY: args.end_y, duration: args.duration_ms ?? 500 }; break;
    case 'tapilot_type_text': action = { type: 'input', text: args.text, replace: args.replace ?? true, autoDismissKeyboard: false }; break;
    case 'tapilot_press': action = args.button === 'enter' ? { type: 'key', keyName: 'ENTER' } : { type: args.button }; break;
    case 'tapilot_launch_app': action = { type: 'launch', uri: args.package_name }; break;
    default: throw new Error('Unknown Tapilot tool');
  }
  await phone.request('device.execute', { action, ...(args.frame_id ? { frameId: args.frame_id } : {}) });
  return text({ action_completed: true, verified: false, next: 'Call tapilot_screen to verify the visible result.' });
}

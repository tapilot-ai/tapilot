/** OEM resolution overrides need wm-based input dimensions; foldables may need physical lookup. */
export function displayOptions(display = '0', mode = 'physical') {
  const displayId = Number(display);
  if (!Number.isSafeInteger(displayId) || displayId < 0) throw new Error('Invalid logical Android display ID');
  if (!['physical', 'wm'].includes(mode)) throw new Error('Screen info mode must be physical or wm');
  if (mode === 'wm' && displayId !== 0) throw new Error('wm screen info supports only the default display 0');
  return {
    ...(mode === 'physical' ? { displayId, usePhysicalDisplayIdForDisplayLookup: true } : {}),
    usePhysicalDisplayIdForScreenshot: true,
    exposeRunAdbShellAction: false,
  };
}

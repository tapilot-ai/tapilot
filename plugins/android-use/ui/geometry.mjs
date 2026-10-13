/** object-fit:contain leaves letterboxing; those margins never map to touches. */
export function imagePoint(rect, width, height, clientX, clientY, clamp = false) {
  if (!width || !height || !rect.width || !rect.height) return null;
  const scale = Math.min(rect.width / width, rect.height / height);
  const left = rect.left + (rect.width - width * scale) / 2;
  const top = rect.top + (rect.height - height * scale) / 2;
  const x = (clientX - left) / scale, y = (clientY - top) / scale;
  if (clamp) return { x: Math.max(0, Math.min(width - 1, x)), y: Math.max(0, Math.min(height - 1, y)) };
  return x >= 0 && y >= 0 && x < width && y < height ? { x, y } : null;
}

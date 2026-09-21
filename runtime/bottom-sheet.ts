export function shouldDismissBottomSheet(
  distance: number,
  elapsedMs: number,
  sheetHeight: number,
): boolean {
  const velocity = distance / Math.max(1, elapsedMs);
  const closeThreshold = Math.min(120, sheetHeight * 0.18);
  return distance >= closeThreshold || (distance >= 44 && velocity >= 0.55);
}

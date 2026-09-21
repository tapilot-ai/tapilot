export const MIN_EDITOR_HEIGHT = 128;
export const MAX_EDITOR_HEIGHT = 320;

export function editorHeightForContent(scrollHeight: number): number {
  return Math.min(
    MAX_EDITOR_HEIGHT,
    Math.max(MIN_EDITOR_HEIGHT, Math.ceil(scrollHeight)),
  );
}

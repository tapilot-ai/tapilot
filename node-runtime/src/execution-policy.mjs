export function withAndroidAiActDefaults(options) {
  return {
    ...options,
    // Planner coordinates are intentionally treated as a rough proposal on a
    // tall phone screenshot. A dedicated locate pass is more reliable and also
    // gives Midscene a chance to crop/search before sending the native gesture.
    deepLocate: options?.deepLocate ?? true,
  };
}

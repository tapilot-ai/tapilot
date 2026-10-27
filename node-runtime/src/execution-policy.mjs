export function withAndroidAiActDefaults(options) {
  const effort = options?.effort ?? 'fast';
  return {
    ...options,
    // Deep planning alone can repeat a mistaken target location. Recheck visual
    // targets too; short routes still reuse ordinary planner coordinates.
    ...(effort === 'deepThink' ? { deepLocate: options?.deepLocate ?? true } : {}),
    effort,
  };
}

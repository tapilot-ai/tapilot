// Setup searches a single system setting. Missing OEM entries must not consume
// the ordinary app-task budget by repeatedly scanning the same list.
export function androidReplanningCycleLimit(environment) {
  return environment.MIDSCENE_ANDROID_SETUP_NAVIGATION === '1' ? 12 : 100;
}

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

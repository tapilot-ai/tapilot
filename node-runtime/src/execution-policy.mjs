export function withAndroidAiActDefaults(options) {
  return {
    ...options,
    // Reuse visual coordinates supplied by the planner. Leaving deepLocate
    // unspecified preserves the SDK's fallback when it must locate separately.
    // Callers can still explicitly request a dedicated crop/search pass.
    effort: options?.effort ?? 'fast',
  };
}

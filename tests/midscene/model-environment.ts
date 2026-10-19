/** A selected model profile is a complete boundary, not an overlay on another provider. */
export function applyModelEnvironment(parsed: Record<string, string> | undefined,
  environment: Record<string, string | undefined> = process.env): void {
  const modelKey = /^MIDSCENE_(?:PLANNING_|INSIGHT_)?MODEL_/;
  if (!parsed || !Object.keys(parsed).some(key => modelKey.test(key))) return;
  for (const key of Object.keys(environment)) if (modelKey.test(key)) delete environment[key];
  for (const [key, value] of Object.entries(parsed)) {
    if (modelKey.test(key) && value.trim()) environment[key] = value;
  }
}

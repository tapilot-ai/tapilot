export function controlledEvidence(html) {
  const tasks = new Map();
  function walk(value) {
    if (Array.isArray(value)) { value.forEach(walk); return; }
    if (!value || typeof value !== 'object') return;
    if (value.taskId && value.timing) tasks.set(value.taskId, value);
    for (const [key, child] of Object.entries(value)) if (!['recorder', 'uiContext', 'log'].includes(key)) walk(child);
  }
  for (const match of html.matchAll(/<script[^>]*type=["']midscene_web_dump["'][^>]*>([\s\S]*?)<\/script>/g)) {
    if (match[1].trimStart().startsWith('{')) walk(JSON.parse(match[1]));
  }
  const all = [...tasks.values()];
  const inference = all.filter(t => t.usage);
  if (!inference.length || inference.some(t => t.usage.model_name !== 'gpt-6-luna' || t.usage.response_model_name !== 'gpt-6-luna')) throw Error('Actual model identity mismatch or missing');
  const queries = all.filter(t => t.subType === 'Query');
  if (queries.length !== 1 || queries[0].output?.success !== true || !queries[0].output?.observation?.trim()) throw Error('Independent final query failed or missing');
  const captures = all.filter(t => t.uiContext?.shotSize);
  if (!captures.length || captures.some(t => t.uiContext.shotSize.width !== 988 || t.uiContext.shotSize.height !== 1972 || t.uiContext.shrunkShotToLogicalRatio !== 1)) throw Error('Controlled OPPO screenshot dimensions mismatch');
  return { model: 'gpt-6-luna', modelCalls: inference.length, retries: inference.reduce((n,t) => n + (t.usage.retry_count ?? 0), 0),
    screenshot: { width: 988, height: 1972 }, observation: queries[0].output.observation };
}

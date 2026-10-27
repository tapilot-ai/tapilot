/** Every inference must use the requested screenshot-to-logical coordinate ratio. */
export function hasExpectedScreenshotScale(tasks, factor) {
  if (![1, 1.5].includes(factor)) return false;
  const inference = tasks.filter(task => task.usage);
  return inference.length > 0 && inference.every(task => {
    const context = task.uiContext;
    const dpr = context?.deprecatedDpr, ratio = context?.shrunkShotToLogicalRatio;
    return Number.isFinite(dpr) && dpr > 0 && Number.isFinite(ratio) && ratio > 0 &&
      Math.abs(ratio - dpr / factor) < 1e-6;
  });
}

export function controlledEvidence(html, screenshot = { width: 988, height: 1972 }) {
  if (![screenshot.width, screenshot.height].every(v => Number.isSafeInteger(v) && v > 0)) throw Error('Invalid expected screenshot dimensions');
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
  const queries = all.filter(t => t.subType === 'Query').sort((a, b) => (a.timing.start ?? 0) - (b.timing.start ?? 0));
  const validVerdict = task => typeof task?.output?.success === 'boolean' &&
    typeof task.output.observation === 'string' && task.output.observation.trim() &&
    !Object.values(task.param?.dataDemand ?? {}).some(value => typeof value === 'string' && value.trim() === task.output.observation.trim());
  // The optional third query repairs only malformed output, never a valid false verdict.
  const formatRepair = queries.length === 3 && !validVerdict(queries[1]);
  const readThenCheck = (queries.length === 2 || formatRepair) &&
    (typeof queries[0].output === 'string' ? queries[0].output.trim() : typeof queries[0].output?.observation === 'string' && queries[0].output.observation.trim()) &&
    !Object.hasOwn(queries[0].output, 'success') &&
    queries.every((q, i) => Number.isFinite(q.timing.start) && (!i || q.timing.start > queries[i - 1].timing.start));
  const finalQuery = queries.at(-1);
  if ((!readThenCheck && queries.length !== 1) || !validVerdict(finalQuery) || finalQuery.output.success !== true) throw Error('Independent final query failed or missing');
  const captures = all.filter(t => t.uiContext?.shotSize);
  if (!captures.length || captures.some(t => t.uiContext.shotSize.width !== screenshot.width || t.uiContext.shotSize.height !== screenshot.height || t.uiContext.shrunkShotToLogicalRatio !== 1)) throw Error('Controlled screenshot dimensions mismatch');
  return { model: 'gpt-6-luna', modelCalls: inference.length, retries: inference.reduce((n,t) => n + (t.usage.retry_count ?? 0), 0),
    screenshot: { ...screenshot }, observation: finalQuery.output.observation };
}

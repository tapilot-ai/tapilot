#!/usr/bin/env node
/** Inspect private benchmark artifacts; never turn a negative visual judgment into success. */
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { createHash } from 'node:crypto';
import { controlledEvidence } from './lib/controlled-evidence.mjs';

const dir = process.argv[2];
if (!dir) throw Error('Usage: node scripts/inspect-native-benchmark.mjs ARTIFACT_DIRECTORY');
const sample = JSON.parse(readFileSync(join(dir, 'sample.json')));
const reports = [];
for (const file of readdirSync(dir).filter(name => /^report-\d+\.html$/.test(name))) {
  const html = readFileSync(join(dir, file), 'utf8');
  const tasks = new Map();
  function walk(value) {
    if (!value || typeof value !== 'object') return;
    if (value.taskId && value.timing) tasks.set(value.taskId, value);
    for (const [key, child] of Object.entries(value)) {
      if (!['recorder', 'uiContext', 'log'].includes(key)) walk(child);
    }
  }
  for (const match of html.matchAll(/<script[^>]*type=["']midscene_web_dump["'][^>]*>([\s\S]*?)<\/script>/g)) {
    if (match[1].trimStart().startsWith('{')) walk(JSON.parse(match[1]));
  }
  const all = [...tasks.values()];
  const queries = all.filter(task => task.subType === 'Query').sort((a, b) => a.timing.start - b.timing.start);
  const last = queries.at(-1);
  if (last) {
    const shot = last.uiContext.screenshot;
    const data = typeof shot === 'string' ? shot : [...html.matchAll(/<script[^>]*type="midscene-image"[^>]*data-id="([^"]+)"[^>]*>([\s\S]*?)<\/script>/g)]
      .find(match => match[1] === shot.id)?.[2].trim();
    if (!data?.startsWith('data:')) throw Error(`Missing screenshot: ${file}`);
    writeFileSync(join(dir, file.replace('.html', '-query.jpg')), Buffer.from(data.split(',')[1], 'base64'));
  }
  let checks;
  try { checks = controlledEvidence(html, process.env.TAPILOT_BENCH_SCREENSHOT_SIZE ? JSON.parse(process.env.TAPILOT_BENCH_SCREENSHOT_SIZE) : undefined); }
  catch (error) { checks = { error: error.message }; }
  reports.push({ file, sha256: createHash('sha256').update(html).digest('hex'),
    startedAt: Math.min(...all.map(task => task.timing?.start).filter(Number.isFinite)),
    checks, queryCount: queries.length, query: last?.output });
}
reports.sort((a, b) => a.startedAt - b.startedAt);
const events = readFileSync(join(dir, 'logcat.txt'), 'utf8').split('\n')
  .filter(line => line.includes('TapilotPerformance: {'))
  .map(line => JSON.parse(line.split('TapilotPerformance: ')[1]));
const outer = events.filter(event => event.phase === 'model.complete');
const vision = events.filter(event => event.phase === 'midscene.model.http');
const audit = { ...sample, reports, outerCalls: outer.length,
  outerMs: outer.reduce((sum, event) => sum + event.durationMs, 0),
  visionCalls: vision.length, visionMs: vision.reduce((sum, event) => sum + event.totalMs, 0),
  models: [...new Set(events.filter(event => event.phase === 'model.response' && event.role === 'outer').map(event => event.model))],
  manualReview: 'pending' };
writeFileSync(join(dir, 'audit.json'), JSON.stringify(audit, null, 2) + '\n');
console.log(JSON.stringify({ status: sample.task.status, wallMs: sample.task.durationMs,
  outerCalls: audit.outerCalls, visionCalls: audit.visionCalls,
  reports: reports.map(({ file, checks }) => ({ file, valid: !checks.error, error: checks.error })) }));

#!/usr/bin/env node
// Read-only host evidence sampler. Never emits environment values, process
// arguments, session IDs, credentials or user paths. Does not contact a phone.
import { execFileSync } from 'node:child_process';
import { basename, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { writeFile } from 'node:fs/promises';

export function hostFromPath(value = '') {
  if (/DoubaoWork|com\.work\.pc\.doubao/i.test(value)) return 'doubao';
  if (/Cherry[ -]?Studio|com\.kangfenmao\.cherry/i.test(value)) return 'cherry';
  if (/WorkBuddy|com\.tencent\.workbuddy/i.test(value)) return 'workbuddy';
  if (/Visual Studio Code|Code\.app|[\\/]\.vscode[\\/]/i.test(value)) return 'vscode';
  if (/Cursor\.app|[\\/]\.cursor[\\/]/i.test(value)) return 'cursor';
  return null;
}

export function summarize({ env, executable = '', ancestors = [] }) {
  const markers = Object.keys(env).filter(key =>
    /^(CODEX_|CURSOR_|VSCODE_|WORKBUDDY_|DOUBAO_|DSH_|DEEPSEEK_|COPILOT_)/.test(key) ||
    ['TERM_PROGRAM', 'MCP_VM_CONVERSATION_ID', 'TAPILOT_HOST'].includes(key)
  ).sort();
  const terminal = ['vscode', 'Cursor', 'Apple_Terminal', 'iTerm.app', 'WezTerm', 'ghostty'].includes(env.TERM_PROGRAM)
    ? env.TERM_PROGRAM : env.TERM_PROGRAM ? 'other' : null;
  const runtimeHost = hostFromPath(executable);
  const dshSession = !!env.DSH_SESSION_ID;
  const codexProcess = ancestors.some(item => item.name === 'codex');
  const container = ancestors.find(item => item.host)?.host ?? runtimeHost ?? (dshSession ? 'dsh' : null) ??
    (terminal === 'vscode' ? 'vscode' : terminal === 'Cursor' ? 'cursor' : null);
  const codex = ['CODEX_SESSION_ID', 'CODEX_THREAD_ID'].some(key => !!env[key]);
  const doubao = ['DOUBAO_OFFICE_LOG_ID', 'DOUBAO_OFFICE_PLATFORM_APP_ID'].every(key => !!env[key]);
  // Marker-only identifications remain provisional until sampled in that host.
  const candidates = [...new Set([
    ...(container ? [container] : []), ...(codex ? ['codex'] : []), ...(doubao ? ['doubao'] : []),
  ])];
  const host = ['doubao', 'workbuddy', 'cherry'].includes(container) ? container
    : codexProcess ? 'codex' : container === 'dsh' ? 'dsh'
    : candidates.length === 1 ? candidates[0] : 'unknown';
  return {
    host, containerHost: container ?? 'unknown', surface: 'unknown',
    confidence: codexProcess || ['doubao', 'workbuddy', 'cherry'].includes(container) ? 'process-evidence'
      : host === 'dsh' ? 'session-evidence' : host === 'unknown' ? 'insufficient-or-conflicting' : 'environment-evidence',
    candidates, markers, terminal,
    runtimeHost, ancestors,
    adapterHint: ['codex', 'doubao', 'workbuddy', 'cursor', 'vscode', 'dsh', 'cherry', 'standard'].includes(env.TAPILOT_HOST) ? env.TAPILOT_HOST : null,
    panelSupport: 'unverified',
  };
}

export function collect() {
  const ancestors = [];
  let pid = process.ppid;
  const safeNames = new Set(['node', 'bash', 'zsh', 'sh', 'fish', 'codex', 'dsh', 'launchd', 'login', 'Code', 'Cursor', 'WorkBuddy', 'DoubaoWork', 'nodejs']);
  if (process.platform !== 'win32') for (let depth = 0; depth < 8 && pid > 1; depth++) {
    try {
      // comm only: argv can contain exported credentials in shell wrappers.
      const text = execFileSync('ps', ['-p', String(pid), '-o', 'ppid=', '-o', 'comm='], { encoding: 'utf8', timeout: 1000, stdio: ['ignore', 'pipe', 'ignore'] }).trim();
      const match = /^(\d+)\s+(.+)$/.exec(text);
      if (!match) break;
      const host = hostFromPath(match[2]);
      const name = basename(match[2]);
      ancestors.push({ depth, name: safeNames.has(name) ? name : host ? `${host}-process` : 'other', host });
      const next = Number(match[1]); if (next === pid) break; pid = next;
    } catch { break; }
  }
  return { schemaVersion: 1, sampledAt: new Date().toISOString(), platform: process.platform, ...summarize({ env: process.env, executable: process.execPath, ancestors }) };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const args = process.argv.slice(2);
  if (args.length && (args.length !== 2 || args[0] !== '--output')) throw Error('Usage: node probe-client-host.mjs [--output FILE]');
  const text = JSON.stringify(collect(), null, 2) + '\n';
  if (args.length) await writeFile(resolve(args[1]), text, { mode: 0o600 });
  process.stdout.write(text);
}

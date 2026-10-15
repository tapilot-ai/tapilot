#!/usr/bin/env node

import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { nativeRpcTimings } from './stdio-native-bridge.mjs';
import {
  collectFailureMessage,
  collectFinalAiResponse,
} from './run-result.mjs';

const EVENT_PREFIX = 'MIDSCENE_EVENT ';

function emit(type, detail = {}) {
  process.stdout.write(
    `${EVENT_PREFIX}${JSON.stringify({ type, timestamp: new Date().toISOString(), ...detail })}\n`,
  );
}

function parseArguments(argv) {
  const [command, ...rest] = argv;
  const values = { command };
  for (let index = 0; index < rest.length; index += 1) {
    const argument = rest[index];
    if (!argument.startsWith('--')) {
      throw new Error(`Unexpected argument: ${argument}`);
    }
    const name = argument.slice(2);
    const value = rest[index + 1];
    if (!value || value.startsWith('--')) {
      throw new Error(`${argument} requires a value`);
    }
    if (name === 'project') {
      values.projects ??= [];
      values.projects.push(value);
    } else {
      values[name] = value;
    }
    index += 1;
  }
  return values;
}

function requireAbsolutePath(value, name) {
  if (!value) throw new Error(`--${name} is required`);
  const absolute = resolve(value);
  if (absolute !== value) {
    throw new Error(`--${name} must be an absolute path`);
  }
  return absolute;
}

function collectAgentReports(runResult) {
  const reports = [];
  const seen = new Set();
  for (const project of runResult.projects ?? []) {
    for (const testCase of project.cases ?? []) {
      for (const [attemptIndex, attempt] of (
        testCase.attempts ?? []
      ).entries()) {
        for (const reportPath of attempt.reportPaths ?? []) {
          if (
            typeof reportPath !== 'string' ||
            !reportPath ||
            seen.has(reportPath)
          )
            continue;
          seen.add(reportPath);
          reports.push({
            path: reportPath,
            projectName: project.name || project.projectId || 'Android',
            caseName: testCase.name || 'Case',
            attempt: attemptIndex + 1,
          });
        }
      }
    }
  }

  // Custom nodes can expose report paths outside an attempt. Keep them visible
  // even when the Test Runner result shape gains another nesting level.
  const visit = (value) => {
    if (Array.isArray(value)) {
      for (const item of value) visit(item);
      return;
    }
    if (!value || typeof value !== 'object') return;
    for (const [key, child] of Object.entries(value)) {
      if (key === 'reportPaths' && Array.isArray(child)) {
        for (const reportPath of child) {
          if (
            typeof reportPath === 'string' &&
            reportPath &&
            !seen.has(reportPath)
          ) {
            seen.add(reportPath);
            reports.push({ path: reportPath, caseName: 'Agent report' });
          }
        }
      } else visit(child);
    }
  };
  visit(runResult);
  return reports;
}

async function doctor() {
  const { runTestProject } = await import('@midscene/test/config');
  if (typeof runTestProject !== 'function') {
    throw new Error('@midscene/test/config did not export runTestProject');
  }
  const result = {
    node: process.version,
    platform: process.platform,
    arch: process.arch,
    runtimeVersion:
      process.env.MIDSCENE_ANDROID_RUNTIME_VERSION ?? 'development',
    testRunnerLoaded: true,
  };
  emit('doctor.completed', result);
  process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
}

async function test(options) {
  const projectRoot = requireAbsolutePath(
    options['project-root'],
    'project-root',
  );
  const resultDir = requireAbsolutePath(options['result-dir'], 'result-dir');
  const summaryPath = resolve(resultDir, 'android-runner-result.json');
  await mkdir(resultDir, { recursive: true });
  emit('test.started', { projectRoot, resultDir });

  const { runTestProject } = await import('@midscene/test/config');
  const output = [];
  const runResult = await runTestProject({
    cwd: projectRoot,
    projectRoot,
    resultDir,
    ...(options.config ? { configPath: options.config } : {}),
    ...(options.projects ? { projectNames: options.projects } : {}),
    onProgress(message) {
      const line = String(message);
      output.push(line);
      process.stdout.write(`${line}\n`);
    },
  });
  const result = { ...runResult, output, nativeRpcTimings: nativeRpcTimings() };
  await writeFile(summaryPath, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
  const agentReports = collectAgentReports(runResult);
  const finalResponse = collectFinalAiResponse(runResult);
  const failure = collectFailureMessage(runResult);
  emit('test.completed', {
    nativeRpcTimings: result.nativeRpcTimings,
    exitCode: runResult.exitCode,
    resultDir: runResult.resultDir,
    summaryPath: runResult.summaryPath,
    reportPath: agentReports.at(-1)?.path ?? runResult.reportPath,
    agentReports,
    testRunReportPath: runResult.reportPath,
    ...(finalResponse ? { finalResponse } : {}),
    ...(failure ? { failure } : {}),
  });
  process.exitCode = runResult.exitCode;
}

let interrupted = false;
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.once(signal, () => {
    if (interrupted) return;
    interrupted = true;
    emit('run.interrupted', { signal });
    process.exitCode = 130;
  });
}

try {
  const options = parseArguments(process.argv.slice(2));
  if (options.command === 'doctor') {
    await doctor();
  } else if (options.command === 'test') {
    await test(options);
  } else {
    throw new Error('Usage: runner.mjs <doctor|test> [options]');
  }
} catch (error) {
  const message = error instanceof Error ? error.message : String(error);
  emit('run.failed', { message });
  process.stderr.write(`${message}\n`);
  process.exitCode = 1;
}

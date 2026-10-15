import { describe, expect, it } from '@rstest/core';
import { classifyExecutionFailure } from './execution-failure';

describe('classifyExecutionFailure', () => {
  it('recognizes a DNS lookup failure and extracts its hostname', () => {
    expect(
      classifyExecutionFailure('Node Test Runner exited with code 1', [
        'Cause: Error [ENOTFOUND]: getaddrinfo ENOTFOUND api.example.com',
      ]),
    ).toEqual({
      category: 'network',
      hostname: 'api.example.com',
    });
  });

  it('recognizes an offline device without a hostname', () => {
    expect(
      classifyExecutionFailure('fetch failed', ['Network is unreachable']),
    ).toEqual({ category: 'network', hostname: undefined });
  });

  it('leaves unrelated runner failures unchanged', () => {
    expect(
      classifyExecutionFailure('YAML syntax is invalid', ['step 1 failed']),
    ).toEqual({ category: 'unknown' });
  });

  it('distinguishes a stalled model request from a task step timeout', () => {
    expect(
      classifyExecutionFailure('Node Test Runner exited with code 1', [
        'AI call hard timeout after 45000ms',
      ]),
    ).toEqual({ category: 'modelTimeout' });
    expect(
      classifyExecutionFailure('Node Test Runner exited with code 1', [
        'Node "aiAct" timed out after 600000ms.',
      ]),
    ).toEqual({ category: 'stepTimeout' });
  });
});

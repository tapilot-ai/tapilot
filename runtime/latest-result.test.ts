import { describe, expect, it } from '@rstest/core';
import {
  parseLatestTaskResult,
  serializeLatestTaskResult,
} from './latest-result';

describe('latest task result', () => {
  it('round-trips a failed result', () => {
    const value = { message: 'model timed out', status: 'failed' } as const;
    expect(parseLatestTaskResult(serializeLatestTaskResult(value), null)).toEqual(
      value,
    );
  });

  it('migrates the legacy response as a successful result', () => {
    expect(parseLatestTaskResult(null, 'done')).toEqual({
      message: 'done',
      status: 'succeeded',
    });
  });

  it('uses the newest history status instead of a stale legacy success', () => {
    expect(
      parseLatestTaskResult(null, 'an older task succeeded', {
        detail: 'the newest task timed out',
        status: 'failed',
      }),
    ).toEqual({
      message: 'the newest task timed out',
      status: 'failed',
    });
  });

  it('does not reuse an older success message for a failed history entry', () => {
    expect(
      parseLatestTaskResult(null, 'an older task succeeded', {
        status: 'failed',
      }),
    ).toEqual({ message: '', status: 'failed' });
  });

  it('rejects malformed and unknown states', () => {
    expect(parseLatestTaskResult('{broken', null)).toBeUndefined();
    expect(
      parseLatestTaskResult('{"message":"done","status":"unknown"}', null),
    ).toBeUndefined();
  });
});

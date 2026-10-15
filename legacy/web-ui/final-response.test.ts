import { describe, expect, it } from '@rstest/core';
import { resolveRunResultMessage } from './final-response';

describe('final task response', () => {
  it('prefers the final AI response over runner logs', () => {
    expect(
      resolveRunResultMessage(
        '  最近聊天主要讨论了发布计划。  ',
        ['runner log'],
        'done',
      ),
    ).toBe('最近聊天主要讨论了发布计划。');
  });

  it('falls back to runner output and then the success message', () => {
    expect(resolveRunResultMessage(undefined, ['first', 'last'], 'done')).toBe(
      'first\nlast',
    );
    expect(resolveRunResultMessage(' ', [], 'done')).toBe('done');
  });
});

import { describe, expect, it } from '@rstest/core';
import { resolveChatAgentOutcome } from './chat-agent-outcome';

describe('chat agent outcome', () => {
  it('keeps a direct answer successful', () => {
    expect(resolveChatAgentOutcome({ reply: '你好' }, 'zh'))
      .toEqual({ exitCode: 0, finalResponse: '你好' });
  });

  it('does not report success when the model claims a failed operation succeeded', () => {
    expect(resolveChatAgentOutcome({
      reply: '已经打开了。',
      lastPhoneResult: { success: false, result: '屏幕上仍是桌面' },
    }, 'zh')).toEqual({
      exitCode: 1,
      failure: '手机操作未通过最终画面验证：屏幕上仍是桌面',
    });
  });

  it('accepts a successful retry as the final phone result', () => {
    expect(resolveChatAgentOutcome({
      reply: '设置首页已打开',
      lastPhoneResult: { success: true, result: '设置首页可见' },
    }, 'zh')).toEqual({ exitCode: 0, finalResponse: '设置首页已打开' });
  });
});

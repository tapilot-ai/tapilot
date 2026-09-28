import { describe, expect, it } from '@rstest/core';
import { verifiedPhoneTask } from './verified-phone-task';

describe('verified phone task', () => {
  it('runs a screen assertion after the phone operation', () => {
    const instruction = '打开设置：不要点击“清除数据”';
    const condition = '设置首页显示“WLAN”';
    expect(JSON.parse(verifiedPhoneTask(instruction, condition))).toEqual({
      cases: [{
        name: 'Tapilot verified phone operation',
        steps: [
          { aiAct: { prompt: instruction } },
          { aiAssert: { prompt: condition } },
        ],
      }],
    });
  });
});

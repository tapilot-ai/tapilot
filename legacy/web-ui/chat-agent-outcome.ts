import type { ChatAgentResult } from './chat-agent';

export function resolveChatAgentOutcome(
  result: ChatAgentResult,
  language: 'zh' | 'en',
): { exitCode: number; finalResponse?: string; failure?: string } {
  if (result.lastPhoneResult?.success === false) {
    const prefix = language === 'zh'
      ? '手机操作未通过最终画面验证'
      : 'The phone operation did not pass final-screen verification';
    return { exitCode: 1, failure: `${prefix}：${result.lastPhoneResult.result}` };
  }
  return { exitCode: 0, finalResponse: result.reply };
}

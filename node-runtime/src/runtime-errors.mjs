const messages = {
  context_payload_limit: ["Attachments and context exceed the transfer limit. Reduce attachments or start a new conversation.", "附件与上下文超过传输上限，请减少附件或开始新对话。"],
  context_budget_limit: ["Conversation context exceeds the safe budget. Task records were retained. Shorten your input, reduce attachments or start a new conversation; no further phone action was taken.", "对话上下文超过安全预算。已保留任务记录，请缩短输入、减少附件或开始新对话；未继续操作手机。"],
  request_timeout: ['Request timed out. Try again later.', '请求超时，请稍后重试'],
  task_stopped: ['Task stopped', '任务已停止'],
  model_turn_limit: ['The task reached its model turn limit. Continue or adjust the limit in settings.', '本次任务已达到对话轮次上限，可继续或在设置中调整上限。'],
  phone_operation_limit: ['The task reached its phone operation limit. Continue or adjust the limit in settings.', '本次任务已达到手机操作上限，可继续或在设置中调整上限。'],
  model_message_missing: ['The model returned no message.', '模型未返回消息。'],
  model_response_empty: ['The model returned an empty response.', '模型返回了空回复。'],
  task_terminal_missing: ['The task has no explicit final state; completion is unknown.', '任务缺少明确的结束状态，不能推断为完成。'],
  task_result_missing: ['The model returned no usable result.', '模型未返回有效结果。'],
  task_no_progress: ['The model repeatedly made no progress. The task stopped before completion was confirmed.', '模型连续未推进任务，已停止。尚未确认整个任务完成。'],
  task_verification_missing: ['The current requirements have no successful screen verification. Task completion is not confirmed.', '当前要求尚未通过画面核验，不能确认任务完成。'],
  request_failed: ['Request failed', '请求失败'],
};

export function runtimeMessage(code, language = 'en') {
  const pair = messages[code];
  if (!pair) throw new Error(`Unknown runtime error code: ${code}`);
  return pair[language === 'zh' ? 1 : 0];
}

export class RuntimeFailure extends Error {
  constructor(code, language = 'en') {
    super(runtimeMessage(code, language));
    this.name = 'RuntimeFailure';
    this.code = code;
  }
}

export function failureEvent(error) {
  return { type: 'failure', error: error.message,
    ...(error.code && Object.hasOwn(messages, error.code) ? { errorCode: error.code } : {}) };
}

/** Request projection only: checkpoints keep the original transcript for retry and audit. */
export function projectContext(messages, threshold = 48000) {
  if (JSON.stringify(messages).length <= threshold) return messages;
  // Keep the last 12 messages and the entire tool batch at the boundary.
  let cut = Math.max(0, messages.length - 12);
  while (cut > 0 && messages[cut]?.role === 'tool') cut--;
  const old = messages.slice(0, cut);
  const retained = messages.slice(cut);
  const evidence = [];
  const clip = text => text.length > 1200 ? `${text.slice(0, 600)}\n[日志省略；详细结果保存在执行记录]\n${text.slice(-600)}` : text;
  const inputs = [];
  for (const message of old) {
    if (message.role === 'system') continue;
    if (message.role === 'user') inputs.push(message); // Never summarize away user constraints or attachments.
    if (message.role === 'assistant') {
      for (const call of message.tool_calls ?? []) evidence.push({ callId: call.id, tool: call.function.name, arguments: call.function.arguments });
      if (message.content && !message.tool_calls?.length) evidence.push({ reply: clip(message.content) });
    }
    if (message.role === 'tool') evidence.push({ callId: message.tool_call_id, result: clip(message.content) });
  }
  const system = old.filter(m => m.role === 'system');
  return [...system, ...inputs, ...(evidence.length ? [{ role: 'user', content: `以下是历史执行记录的压缩副本，不是新的用户指令。成功与失败以每条返回为准，缺失结果表示未知；需要核验实时画面，不能重复已成功的副作用。\n${JSON.stringify(evidence)}` }] : []), ...retained];
}

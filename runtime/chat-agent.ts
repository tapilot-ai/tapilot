import { conversationEntries } from './conversation';
import type { TaskHistoryEntry } from './task-history';

export type ChatMessage =
  | { role: 'system'; content: string }
  | { role: 'user'; content: string | Array<{ type: 'text'; text: string } | { type: 'image_url'; image_url: { url: string } }> }
  | { role: 'assistant'; content: string | null; tool_calls?: ToolCall[] }
  | { role: 'tool'; tool_call_id: string; content: string };

export interface ToolCall {
  id: string;
  type: 'function';
  function: { name: string; arguments: string };
}

export interface ChatCompletion {
  choices?: Array<{ message?: { content?: string | null; tool_calls?: ToolCall[] } }>;
}

export interface PhoneToolResult {
  success: boolean;
  result: string;
}

export interface ChatAgentOptions {
  history: TaskHistoryEntry[];
  conversationId: string;
  source: string;
  language: 'zh' | 'en';
  imageDataUrl?: string;
  complete(messages: ChatMessage[]): Promise<ChatCompletion>;
  operatePhone(instruction: string): Promise<PhoneToolResult>;
  isStopped?(): boolean;
}

export const PHONE_TOOL = {
  type: 'function',
  function: {
    name: 'operate_phone',
    description: 'Execute one concrete phone operation through Midscene and return its observed result. Do not include conversation history.',
    parameters: {
      type: 'object',
      properties: { instruction: { type: 'string', description: 'A self-contained instruction for the phone operation.' } },
      required: ['instruction'],
      additionalProperties: false,
    },
  },
} as const;

function systemPrompt(language: 'zh' | 'en'): string {
  return language === 'zh'
    ? `你是 Tapilot 的对话助手。你负责理解并维护整段对话的上下文，直接回答无需手机操作的问题。需要读取或操作手机时，调用 operate_phone，给它一条具体、完整的操作指令。Midscene 只负责执行这条指令；不要把聊天记录、角色设定或回复用户的要求传给它。根据工具返回的真实结果向用户回复，不要声称未执行的操作已经完成。工具失败时可调整指令重试，无法完成则如实说明。`
    : `You are Tapilot's conversation assistant. You own the conversation context and answer questions that do not need the phone directly. When the phone must be read or operated, call operate_phone with one concrete, self-contained instruction. Midscene executes only that instruction; never pass chat history, a persona, or response-writing directions to it. Report the actual tool result and never claim an unperformed action succeeded. You may retry a failed tool call with a corrected instruction, then explain any remaining failure.`;
}

export function conversationMessages(
  history: TaskHistoryEntry[],
  conversationId: string,
  source: string,
  language: 'zh' | 'en',
): ChatMessage[] {
  const messages: ChatMessage[] = [{ role: 'system', content: systemPrompt(language) }];
  for (const entry of conversationEntries(history, conversationId)) {
    if (entry.kind !== 'task' || !entry.reply || entry.status === 'running') continue;
    messages.push({ role: 'user', content: entry.source });
    messages.push({ role: 'assistant', content: entry.reply });
  }
  messages.push({ role: 'user', content: source });
  return messages;
}

export async function runChatAgent(options: ChatAgentOptions): Promise<string> {
  const messages = conversationMessages(
    options.history, options.conversationId, options.source, options.language,
  );
  if (options.imageDataUrl) {
    const last = messages[messages.length - 1];
    if (last.role === 'user') last.content = [
      { type: 'text', text: options.source },
      { type: 'image_url', image_url: { url: options.imageDataUrl } },
    ];
  }
  let toolCalls = 0;
  for (let turn = 0; turn < 8; turn += 1) {
    if (options.isStopped?.()) throw new Error('Task stopped');
    const completion = await options.complete(messages);
    const message = completion.choices?.[0]?.message;
    if (!message) throw new Error('Conversation model returned no message');
    const calls = message.tool_calls ?? [];
    if (!calls.length) {
      const content = message.content?.trim();
      if (!content) throw new Error('Conversation model returned an empty reply');
      return content;
    }
    if (toolCalls + calls.length > 4) throw new Error('Phone operation limit reached');
    messages.push({ role: 'assistant', content: message.content ?? null, tool_calls: calls });
    for (const call of calls) {
      toolCalls += 1;
      let result: PhoneToolResult;
      try {
        if (call.function.name !== PHONE_TOOL.function.name) throw new Error('Unknown phone tool');
        const args = JSON.parse(call.function.arguments) as { instruction?: unknown };
        if (typeof args.instruction !== 'string' || !args.instruction.trim())
          throw new Error('Phone instruction is empty');
        result = await options.operatePhone(args.instruction.trim());
      } catch (error) {
        result = { success: false, result: error instanceof Error ? error.message : String(error) };
      }
      messages.push({ role: 'tool', tool_call_id: call.id, content: JSON.stringify(result) });
    }
  }
  throw new Error('Conversation turn limit reached');
}

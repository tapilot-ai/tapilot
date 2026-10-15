import type { NativeChatCommand } from './native-chat-state';

declare global {
  interface Window {
    TapilotChat?: { update(payload: string): void; requestFocus(): void };
    __tapilotNativeChatCommand?: (command: NativeChatCommand) => void;
    __tapilotNativeChatFocus?: () => void;
  }
}

let publish: (() => void) | undefined;
let queued = false;

export function publishNativeChat(): void {
  if (!publish || queued) return;
  queued = true;
  queueMicrotask(() => { queued = false; publish?.(); });
}

export function installNativeChat(
  snapshot: () => object,
  command: (command: NativeChatCommand) => Promise<void>,
  failure: (error: unknown) => void,
): void {
  if (!window.TapilotChat) return;
  document.body.classList.add('native-chat-host');
  window.__tapilotNativeChatFocus = () => window.TapilotChat?.requestFocus();
  publish = () => window.TapilotChat?.update(JSON.stringify(snapshot()));
  window.__tapilotNativeChatCommand = value => {
    void command(value).catch(failure).finally(() => {
      // Native typing already updates its local draft; avoid retransmitting image previews per keystroke.
      if (value.action !== 'draft') publishNativeChat();
    });
  };
  publishNativeChat();
}

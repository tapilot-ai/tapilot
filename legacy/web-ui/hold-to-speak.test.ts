import { expect, it } from '@rstest/core';
import { bindHoldToSpeak } from './hold-to-speak';

it('keeps focused and whitespace drafts with native editing and defers cold tap focus until release', () => {
  class Input extends EventTarget {
    value = ''; placeholder = ''; focused = false; style = { touchAction: '' };
    setPointerCapture() {}
    focus() { this.focused = true; }
  }
  const input = new Input();
  const previousDocument = Object.getOwnPropertyDescriptor(globalThis, 'document');
  const previousWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const doc = { activeElement: null as unknown };
  Object.defineProperty(globalThis, 'document', { configurable: true, value: doc });
  Object.defineProperty(globalThis, 'window', { configurable: true, value: globalThis });
  const down = () => Object.assign(new Event('pointerdown', { cancelable: true }), { pointerType: 'touch', pointerId: 1, clientX: 1, clientY: 1 });
  try {
    bindHoldToSpeak(input as unknown as HTMLTextAreaElement, {
      enabled: () => true, placeholder: hold => hold ? '按住说话' : '输入消息',
      begin: () => { throw new Error('Unexpected recording'); }, move() {}, finish() {}, cancel() {},
    });
    expect(input.placeholder).toBe('按住说话'); expect(input.style.touchAction).toBe('none');
    doc.activeElement = input; input.dispatchEvent(new Event('focus'));
    const focused = down(); input.dispatchEvent(focused);
    expect(input.style.touchAction).toBe('auto'); expect(focused.defaultPrevented).toBe(false); expect(input.placeholder).toBe('输入消息');
    doc.activeElement = null; input.value = ' '; input.dispatchEvent(new Event('input'));
    expect(input.style.touchAction).toBe('auto'); const populated = down(); input.dispatchEvent(populated); expect(populated.defaultPrevented).toBe(false);
    input.value = ''; input.dispatchEvent(new Event('input'));
    const cold = down(); input.dispatchEvent(cold); expect(cold.defaultPrevented).toBe(true); expect(input.focused).toBe(false);
    input.dispatchEvent(Object.assign(new Event('pointerup'), { pointerId: 1 })); expect(input.focused).toBe(true);
  } finally {
    if (previousDocument) Object.defineProperty(globalThis, 'document', previousDocument); else delete (globalThis as any).document;
    if (previousWindow) Object.defineProperty(globalThis, 'window', previousWindow); else delete (globalThis as any).window;
  }
});

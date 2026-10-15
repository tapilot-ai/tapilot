/** Keep native selection on focused/populated fields; cold holds never focus the keyboard. */
export function bindHoldToSpeak(input: HTMLTextAreaElement, options: {
  enabled: () => boolean;
  placeholder: (holding: boolean) => string;
  begin: (event: PointerEvent) => void;
  move: (event: PointerEvent) => void;
  finish: () => void;
  cancel: () => void;
}): void {
  let pointer: number | undefined;
  let timer: number | undefined;
  let held = false;
  let moved = false;
  let x = 0, y = 0;
  const allowed = () => options.enabled() && document.activeElement !== input && input.value.length === 0;
  const refresh = () => { input.style.touchAction = held || allowed() ? 'none' : 'auto'; const hint = options.placeholder(allowed()); if (input.placeholder !== hint) input.placeholder = hint; };
  const clear = () => { window.clearTimeout(timer); timer = undefined; };
  for (const name of ['focus', 'blur', 'input'] as const) input.addEventListener(name, () => {
    if (!held && !allowed()) clear();
    refresh();
  });
  input.addEventListener('pointerdown', event => {
    if (event.pointerType === 'mouse' || !allowed()) return;
    event.preventDefault();
    pointer = event.pointerId; x = event.clientX; y = event.clientY; moved = false; held = false;
    input.setPointerCapture(event.pointerId);
    timer = window.setTimeout(() => {
      timer = undefined;
      if (pointer === event.pointerId && allowed() && !moved) { held = true; options.begin(event); }
    }, 480);
  });
  input.addEventListener('pointermove', event => {
    if (pointer !== event.pointerId) return;
    if (Math.hypot(event.clientX - x, event.clientY - y) > 12) { moved = true; clear(); }
    if (held) options.move(event);
  });
  for (const name of ['pointerup', 'pointercancel', 'lostpointercapture'] as const) input.addEventListener(name, event => {
    if (pointer !== event.pointerId) return;
    clear(); pointer = undefined;
    if (held) { held = false; if (name === 'pointerup') options.finish(); else options.cancel(); }
    else if (name === 'pointerup' && !moved) input.focus();
    refresh();
  });
  input.addEventListener('contextmenu', event => { if (held || timer !== undefined) event.preventDefault(); });
  if (typeof MutationObserver !== 'undefined') new MutationObserver(refresh).observe(input, { attributes: true, attributeFilter: ['placeholder', 'disabled'] });
  refresh();
}

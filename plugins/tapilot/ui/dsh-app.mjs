// Served under DSH's authenticated /api channel. No local runtime capability
// or loopback address is sent to a remote browser or DSH Go Web view.
export class DshApp {
  async connect() {
    try {
      const parent = frameElement?.ownerDocument.documentElement;
      if (parent) {
        const observer = new MutationObserver(() => this.onhostcontextchanged?.(this.getHostContext()));
        observer.observe(parent, { attributes: true, attributeFilter: ['data-ds-dark-theme', 'lang'] });
        addEventListener('pagehide', () => observer.disconnect(), { once: true });
      }
    } catch {} // A host may sandbox the page; retain the OS theme/locale fallback.
  }
  getHostContext() {
    try {
      const parent = frameElement?.ownerDocument.documentElement;
      return { locale: parent?.lang || navigator.language, theme: parent?.hasAttribute('data-ds-dark-theme') ? 'dark' : 'light' };
    } catch { return { locale: navigator.language }; }
  }
  async callServerTool(request) {
    const response = await fetch('/api/tapilot/call', {
      method: 'POST', credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'X-Tapilot-Panel': '1' },
      body: JSON.stringify(request),
    });
    const value = await response.json();
    if (!response.ok) throw new Error(value.error || 'DSH panel request failed');
    return value;
  }
  async openLink({ url }) {
    if (!/^https?:/.test(url)) throw new Error('Unsupported link');
    window.open(url, '_blank', 'noopener,noreferrer');
    return {};
  }
}

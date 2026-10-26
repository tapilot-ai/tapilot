// Browser adapter for the same screen UI. The capability stays in this tab.
export class BrowserApp {
  constructor() {
    this.token =
      location.hash.slice(1) || sessionStorage.getItem("android-use-panel");
    if (location.hash) {
      sessionStorage.setItem("android-use-panel", this.token);
      history.replaceState(null, "", location.pathname);
    }
  }
  async connect() {
    if (!this.token)
      throw new Error("Open the preview link returned by Android Use CLI");
  }
  getHostContext() {
    return { locale: navigator.language };
  }
  async callServerTool(request) {
    const response = await fetch("/panel", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${this.token}`,
      },
      body: JSON.stringify(request),
    });
    const value = await response.json();
    if (!response.ok) throw new Error(value.error);
    return value;
  }
  async openLink({ url }) {
    window.open(url, "_blank", "noopener,noreferrer");
    return {};
  }
}

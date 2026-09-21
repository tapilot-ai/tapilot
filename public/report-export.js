(() => {
  if (window.__tapilotVideoExportInstalled) return;
  const host = window.TapilotVideo;
  if (!host) return;
  window.__tapilotVideoExportInstalled = true;
  let sequence = 0;
  let busy = false;
  const requests = new Map();
  host.onmessage = (event) => {
    const response = JSON.parse(event.data);
    const pending = requests.get(response.id);
    if (!pending) return;
    requests.delete(response.id);
    clearTimeout(pending.timer);
    if (response.error) pending.reject(new Error(response.error));
    else pending.resolve();
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++sequence;
    const timer = setTimeout(() => {
      requests.delete(id);
      reject(new Error('Video save timed out'));
    }, 30000);
    requests.set(id, { resolve, reject, timer });
    host.postMessage(JSON.stringify({ id, method, ...params }));
  });
  const save = async (url) => {
    if (busy) return;
    busy = true;
    try {
      // Fetch immediately: the report revokes this URL shortly after anchor.click().
      const blob = await (await fetch(url)).blob();
      await send('begin', { size: blob.size, mime: blob.type.split(';')[0] });
      for (let offset = 0; offset < blob.size; offset += 65536) {
        const bytes = new Uint8Array(await blob.slice(offset, offset + 65536).arrayBuffer());
        let binary = '';
        for (const byte of bytes) binary += String.fromCharCode(byte);
        await send('append', { data: btoa(binary) });
      }
      await send('finish');
    } catch (error) {
      await send('abort', { error: String(error.message || error) }).catch(() => {});
    } finally {
      busy = false;
    }
  };
  const isVideo = (anchor) => anchor.href.startsWith('blob:') && /\.(webm|mp4)$/i.test(anchor.download);
  const originalClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function (...args) {
    if (isVideo(this)) { void save(this.href); return; }
    return originalClick.apply(this, args);
  };
  document.addEventListener('click', (event) => {
    const anchor = event.target instanceof Element ? event.target.closest('a') : null;
    if (!anchor || !isVideo(anchor)) return;
    event.preventDefault();
    void save(anchor.href);
  }, true);
})();

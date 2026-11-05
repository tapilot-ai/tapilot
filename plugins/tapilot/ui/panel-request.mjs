// Stop waiting in the UI without retrying or claiming that the server cancelled.
// A late mutation may still finish; only a fresh frame can establish its outcome.
export async function panelRequest(call, request, { timeoutMs = 45000, setTimer = setTimeout, clearTimer = clearTimeout } = {}) {
  let timer;
  const deadline = new Promise((_, reject) => {
    timer = setTimer(() => {
      const error = new Error('Tapilot panel request timed out');
      error.code = 'panel_timeout';
      reject(error);
    }, timeoutMs);
  });
  try { return await Promise.race([call(request), deadline]); }
  finally { clearTimer(timer); }
}

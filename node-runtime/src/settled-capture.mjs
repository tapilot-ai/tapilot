import sharp from 'sharp';

// Negotiated once per agent. Do not mix native and Sharp fingerprints mid-run:
// their resampling/luma algorithms differ and could turn an old page into a change.
export function nativeFingerprint(frame) {
  const hint = frame.fingerprint;
  if (hint?.algorithm !== 'rgb-luma-64x96-v1' || !frame.geometry?.frameId ||
      hint.frameId !== frame.geometry.frameId || typeof hint.data !== 'string' ||
      !/^[A-Za-z0-9+/]{8192}$/.test(hint.data)) {
    throw new Error('Missing or invalid native frame fingerprint');
  }
  return Buffer.from(hint.data, 'base64');
}

async function pixels(frame) {
  return sharp(Buffer.from(frame.dataUrl.split(',').at(-1), 'base64'))
    .resize(64, 96, { fit: 'fill' }).greyscale().raw().toBuffer();
}
function changed(a, b) {
  if (a.length !== b.length) return true;
  const tiles = new Uint16Array(96);
  for (let i = 64 * 6; i < a.length - 64 * 5; i++) {
    if (Math.abs(a[i] - b[i]) > 12 && ++tiles[Math.floor(i / 512) * 8 + Math.floor(i % 64 / 8)] >= 5) return true;
  }
  return false;
}

// Used only by the on-device agent. Never replays actions or claims task success.
// Wait for a changed, stable pair; an unchanged old page must not settle early.
export function withSettledScreenshots(bridge, {
  now = () => performance.now(), sleep = ms => new Promise(r => setTimeout(r, ms)),
  budgetMs = 1500, intervalMs = 100, fingerprint = pixels, record = () => {},
} = {}) {
  let last, pending = false, revision = 0;
  return {
    getCapabilities: bridge.getCapabilities.bind(bridge),
    getSize: bridge.getSize.bind(bridge),
    cancelSession: bridge.cancelSession.bind(bridge),
    completeSession: bridge.completeSession.bind(bridge),
    async execute(action, frameId) {
      try { return await bridge.execute(action, frameId); }
      finally { pending = true; revision++; }
    },
    async capture() {
      if (!pending) { last = await bridge.capture(); return last; }
      const version = revision, start = now();
      const baseline = last ? await fingerprint(last) : undefined;
      let previous, latest, observedChange = false, samples = 0, state = 'unchanged';
      do {
        latest = await bridge.capture(); samples++;
        const current = await fingerprint(latest);
        observedChange ||= baseline ? changed(baseline, current) : previous ? changed(previous, current) : false;
        if (previous && observedChange && (!baseline || changed(baseline, current)) && !changed(previous, current)) { state = 'stable'; break; }
        previous = current;
        if (now() - start >= budgetMs) break;
        await sleep(intervalMs);
      } while (now() - start < budgetMs);
      if (state !== 'stable' && observedChange) state = 'timeout';
      if (version === revision) pending = false;
      last = latest;
      try { record({ state, samples, durationMs: Math.round(now() - start) }); } catch { /* diagnostics only */ }
      return latest;
    },
  };
}

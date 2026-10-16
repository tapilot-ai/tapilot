import sharp from "sharp";
import { performance } from "node:perf_hooks";

async function pixels(dataUrl) {
  return sharp(Buffer.from(dataUrl.split(",").at(-1), "base64"))
    .resize(64, 96, { fit: "fill" }).greyscale().raw().toBuffer();
}

function changed(a, b) {
  if (a.length !== b.length) return true;
  let count = 0;
  const tiles = new Uint16Array(8 * 12);
  // Ignore status/navigation bars; the clock must not prevent settling.
  for (let i = 64 * 6; i < a.length - 64 * 5; i++) {
    if (Math.abs(a[i] - b[i]) > 12) {
      count++;
      // A digit or toggle may occupy less than 1% of the screen. Detect a
      // concentrated local change without treating isolated noise as motion.
      const tile = Math.floor(i / (64 * 8)) * 8 + Math.floor((i % 64) / 8);
      if (++tiles[tile] >= 5) return true;
    }
  }
  return count > (a.length - 64 * 11) * 0.01;
}

// Only observes: never repeats an action. Stable pixels are not proof that the
// requested operation succeeded or that asynchronous content finished loading.
export async function settledFrame(capture, before, {
  now = () => performance.now(),
  sleep = ms => new Promise(resolve => setTimeout(resolve, ms)),
  budgetMs = 1500,
  initialWaitMs = 100,
  intervalMs = 75,
} = {}) {
  const start = now();
  const baseline = await pixels(before);
  let previous, latest, observedChange = false, samples = 0;
  await sleep(initialWaitMs);
  do {
    latest = await capture();
    samples++;
    const current = await pixels(latest);
    observedChange ||= changed(baseline, current);
    if (previous && !changed(previous, current) && observedChange) {
      return { screenshot: latest, observation: { state: "stable", samples, elapsedMs: Math.round(now() - start) } };
    }
    previous = current;
    if (now() - start >= budgetMs) break;
    await sleep(intervalMs);
  } while (now() - start < budgetMs);
  return { screenshot: latest, observation: { state: observedChange ? "timeout" : "unchanged", samples, elapsedMs: Math.round(now() - start) } };
}

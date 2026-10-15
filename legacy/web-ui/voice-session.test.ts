import { expect, it } from '@rstest/core';
import { VoiceSession, type VoiceDriver } from './voice-session';
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>(done => { resolve = done; });
  return { promise, resolve };
}
function fixture() {
  const ready = deferred<void>(), transcript = deferred<string>();
  const phases: string[] = [], inserted: string[] = [], sent: string[] = [];
  let finishes = 0, cancels = 0;
  const driver: VoiceDriver = { start: () => ready.promise, finish: () => { finishes++; return transcript.promise; },
    cancel: async () => { cancels++; }, destroy() {} };
  const session = new VoiceSession({ driver: () => driver, render: phase => phases.push(phase),
    insert: text => inserted.push(text), send: async text => { sent.push(text); } });
  return { session, ready, transcript, inserted, sent, phases, finishes: () => finishes, cancels: () => cancels };
}
it('release while starting waits for readiness, inserts once, and never sends by default', async () => {
  const f = fixture(); f.session.begin(); const done = f.session.release(); const duplicate = f.session.release();
  expect(done).toBe(duplicate); expect(f.session.phase).toBe('transcribing'); expect(f.finishes()).toBe(0);
  f.ready.resolve(); await Promise.resolve(); await Promise.resolve(); f.transcript.resolve('测试文字'); await done;
  expect(f.inserted).toEqual(['测试文字']); expect(f.sent).toEqual([]); expect(f.finishes()).toBe(1);
});
it('moving out of an action target restores insertion; explicit send is the only sending path', async () => {
  const f = fixture(); f.session.begin(); f.session.move('send'); f.session.move('insert');
  f.ready.resolve(); const done = f.session.release(); f.transcript.resolve('回填'); await done;
  expect(f.inserted).toEqual(['回填']); expect(f.sent).toEqual([]);
  f.session.begin(); const send = f.session.release('send'); await send;
  expect(f.sent).toEqual(['回填']);
});
it('cancel while transcribing hides immediately and rejects late transcription', async () => {
  const f = fixture(); f.session.begin(); f.ready.resolve(); const done = f.session.release('send');
  await Promise.resolve(); await Promise.resolve(); const cancelled = f.session.cancel();
  expect(f.session.phase).toBe('idle'); await cancelled;
  f.transcript.resolve('迟到文字'); await done;
  expect(f.sent).toEqual([]); expect(f.inserted).toEqual([]); expect(f.session.busy).toBe(false);
});
it('cancel before startup finishes cannot leave an overlay or allow a late result', async () => {
  const f = fixture(); f.session.begin(); const cancelled = f.session.cancel();
  expect(f.session.phase).toBe('idle'); f.ready.resolve(); await cancelled;
  expect(f.finishes()).toBe(0); expect(f.cancels()).toBeGreaterThan(0); expect(f.session.busy).toBe(false);
});

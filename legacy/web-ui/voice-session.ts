export type VoiceAction = 'insert' | 'cancel' | 'send';
export type VoicePhase = 'idle' | 'recording' | 'transcribing';
export interface VoiceDriver {
  start(alive: () => boolean): Promise<void>;
  finish(): Promise<string>;
  cancel(): Promise<void>;
  destroy(): void;
}
interface Session {
  driver: VoiceDriver;
  start: Promise<void>;
  cancelled: boolean;
  ending?: Promise<void>;
}

/** Cancellation invalidates results immediately, including results arriving after release. */
export class VoiceSession {
  private active?: Session;
  phase: VoicePhase = 'idle';
  action: VoiceAction = 'insert';
  constructor(private callbacks: {
    driver: () => VoiceDriver;
    render: (phase: VoicePhase, action: VoiceAction, error?: string) => void;
    insert: (text: string) => void;
    send: (text: string) => Promise<void>;
  }) {}
  get busy(): boolean { return !!this.active; }
  private render(error?: string) { this.callbacks.render(this.phase, this.action, error); }
  refresh(): void { if (this.active && this.phase !== 'idle') this.render(); }
  begin(): boolean {
    if (this.active) return false;
    let driver: VoiceDriver;
    try { driver = this.callbacks.driver(); }
    catch (error) { this.render(String(error instanceof Error ? error.message : error)); return false; }
    const session: Session = { driver, start: Promise.resolve(), cancelled: false };
    this.active = session;
    this.phase = 'recording'; this.action = 'insert'; this.render();
    session.start = Promise.resolve().then(() => session.driver.start(() => this.active === session && !session.cancelled));
    void session.start.catch(error => {
      if (this.active !== session || session.cancelled) return;
      this.phase = 'idle'; this.render(String(error instanceof Error ? error.message : error));
      void session.driver.cancel().catch(() => {}).finally(() => {
        session.driver.destroy(); if (this.active === session) this.active = undefined;
      });
    });
    return true;
  }
  move(action: VoiceAction): void {
    if (this.phase !== 'recording') return;
    this.action = action; this.render();
  }
  release(action = this.action): Promise<void> {
    if (action === 'cancel') return this.cancel();
    const session = this.active;
    if (!session || session.cancelled) return Promise.resolve();
    if (session.ending) return session.ending;
    this.action = action; this.phase = 'transcribing'; this.render();
    session.ending = (async () => {
      try {
        await session.start;
        if (session.cancelled || this.active !== session) return;
        const text = await session.driver.finish();
        if (session.cancelled || this.active !== session) return;
        this.phase = 'idle'; this.render();
        if (action === 'send') await this.callbacks.send(text); else this.callbacks.insert(text);
      } catch (error) {
        if (this.active === session && !session.cancelled) {
          this.phase = 'idle'; this.render(String(error instanceof Error ? error.message : error));
        }
      } finally {
        session.driver.destroy(); if (this.active === session) this.active = undefined;
      }
    })();
    return session.ending;
  }
  async cancel(): Promise<void> {
    const session = this.active;
    if (!session || session.cancelled) return;
    session.cancelled = true; this.phase = 'idle'; this.action = 'insert'; this.render();
    await session.driver.cancel().catch(() => {});
    await session.start.catch(() => {});
    await session.driver.cancel().catch(() => {});
    session.driver.destroy(); if (this.active === session) this.active = undefined;
  }
}

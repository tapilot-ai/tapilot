// Only preview polling is gated; phone ownership and model tasks are unaffected.
export class PreviewActivity {
  constructor({onSuspend, onResume, graceMs = 10000, setTimer = setTimeout, clearTimer = clearTimeout}) {
    Object.assign(this, {onSuspend, onResume, graceMs, setTimer, clearTimer});
    this.foreground = true;
    this.suspended = false;
  }
  update(foreground) {
    if (this.closed || foreground === this.foreground) return;
    this.foreground = foreground;
    if (foreground) {
      this.clearTimer(this.timer); this.timer = undefined;
      this.suspended = false;
      this.onResume(); // Refresh even after a brief switch; stale frames cannot be tapped.
    } else {
      this.timer = this.setTimer(() => {
        this.timer = undefined;
        if (this.closed || this.foreground) return;
        this.suspended = true; this.onSuspend();
      }, this.graceMs);
    }
  }
  get canPoll() { return !this.closed && !this.suspended; }
  close() { this.closed = true; this.clearTimer(this.timer); }
}

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync, existsSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { networkWatchdogScript } from '../network-watchdog.js';

for (const cancelled of [false, true]) {
  test(`phone recovery script ${cancelled ? 'does nothing after cancellation' : 'restores all original radios without a host'}`, () => {
    const dir = mkdtempSync(join(tmpdir(), 'tapilot-watchdog-'));
    try {
      for (const name of ['cmd', 'svc']) {
        writeFileSync(join(dir, name), '#!/bin/sh\nprintf "%s\\n" "$*" >> "$RADIO_TEST_LOG"\n', { mode: 0o700 });
      }
      if (!cancelled) writeFileSync(join(dir, 'pending'), '');
      const script = join(dir, 'restore.sh');
      writeFileSync(script, networkWatchdogScript(dir, { airplane: false, wifi: true, data: false }, 1));
      execFileSync('sh', [script], { env: { ...process.env, PATH: `${dir}:${process.env.PATH}`, RADIO_TEST_LOG: join(dir, 'calls') } });
      assert.equal(existsSync(join(dir, 'armed')), true);
      assert.equal(existsSync(join(dir, 'fired')), !cancelled);
      if (cancelled) assert.equal(existsSync(join(dir, 'calls')), false);
      else assert.equal(readFileSync(join(dir, 'calls'), 'utf8'), 'connectivity airplane-mode disable\nwifi enable\ndata disable\n');
    } finally { rmSync(dir, { recursive: true, force: true }); }
  });
}

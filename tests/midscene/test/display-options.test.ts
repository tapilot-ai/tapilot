import test from 'node:test';
import assert from 'node:assert/strict';
import { displayOptions } from '../display-options.js';

test('wm dimensions bypass physical lookup while retaining physical screenshot selection', () => {
  assert.equal(displayOptions().displayId, 0);
  const options = displayOptions('0', 'wm');
  assert.equal('displayId' in options, false);
  assert.equal('usePhysicalDisplayIdForDisplayLookup' in options, false);
  assert.equal(options.usePhysicalDisplayIdForScreenshot, true);
  assert.equal(options.exposeRunAdbShellAction, false);
  assert.throws(() => displayOptions('1', 'wm'), /default display/);
  assert.throws(() => displayOptions('-1'), /Invalid/);
  assert.throws(() => displayOptions('0', 'guess'), /mode/);
});

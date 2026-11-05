import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm, chmod } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { assertPrivatePath, protectPrivatePath } from '../server/private-path.mjs';
import { ChatGPTAuth } from '../server/midscene/chatgpt-auth.mjs';

test('private paths reject broad access rather than silently repairing existing credentials', async t => {
  const root = await mkdtemp(join(tmpdir(), 'tapilot-acl-'));
  t.after(() => rm(root, { recursive: true, force: true }));
  const auth = new ChatGPTAuth(join(root, 'auth'));
  await auth.init();
  await auth.write('account.json', { fixture: true });
  assert.deepEqual(await auth.read('account.json'), { fixture: true });
  const file = join(root, 'auth/account.json');
  if (process.platform === 'win32') {
    // SID avoids localized group names. Grant only synthetic fixture bytes.
    await promisify(execFile)('icacls.exe', [file, '/grant', '*S-1-1-0:(R)']);
  } else await chmod(file, 0o644);
  await assert.rejects(assertPrivatePath(file));
  await assert.rejects(auth.read('account.json'));
  await protectPrivatePath(file);
  assert.deepEqual(await auth.read('account.json'), { fixture: true });
  const empty = join(root, 'empty');
  await writeFile(empty, '');
  await protectPrivatePath(empty);
  await assertPrivatePath(empty);
});


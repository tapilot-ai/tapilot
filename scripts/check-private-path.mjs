// Synthetic files only: expose native permission failures before costly packaging.
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { assertPrivatePath, protectPrivatePath } from '../plugins/tapilot/server/private-path.mjs';
const root = await mkdtemp(join(tmpdir(), 'tapilot-private-check-'));
try {
  const directory = join(root, 'auth');
  await mkdir(directory);
  await protectPrivatePath(directory, true);
  await assertPrivatePath(directory, true);
  const file = join(directory, 'synthetic.json');
  await writeFile(file, '');
  await protectPrivatePath(file);
  await writeFile(file, '{"fixture":true}');
  await assertPrivatePath(file);
  console.log('Private directory and file checks passed');
} finally { await rm(root, { recursive: true, force: true }); }

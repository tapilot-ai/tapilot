import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { publishEntries, entryMatches } from '../server/clients/entries.mjs';

test('stable entries support first install, update, rollback and reject unmanaged files', async () => {
  const root = await mkdtemp(join(tmpdir(), 'tapilot-entries-'));
  try {
    const directory = join(root, 'entries');
    await mkdir(directory);
    const releases = [];
    for (const version of ['one', 'two']) {
      const folder = join(root, version);
      await mkdir(folder);
      const node = join(folder, 'node');
      await writeFile(node, version);
      releases.push({ root: folder, node });
    }
    const first = await publishEntries(directory, releases[0]);
    for (const [name, target] of Object.entries(first.targets)) assert.ok(await entryMatches(join(directory, name), target));
    const second = await publishEntries(directory, releases[1], first.targets);
    for (const [name, target] of Object.entries(second.targets)) assert.ok(await entryMatches(join(directory, name), target));
    await second.rollback();
    for (const [name, target] of Object.entries(first.targets)) assert.ok(await entryMatches(join(directory, name), target));
    await first.rollback();
    await writeFile(join(directory, 'current'), 'user content');
    await assert.rejects(publishEntries(directory, releases[0]), /edited or is unmanaged/);
  } finally { await rm(root, { recursive: true, force: true }); }
});

import { lstat, readlink, symlink, rename, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { randomUUID } from 'node:crypto';

// Stable host paths; releases remain immutable for already-running sessions.
export async function publishEntries(directory, runtime, previous) {
  const targets = { current: runtime.root, node: runtime.node };
  const old = {};
  for (const name of Object.keys(targets)) {
    const file = join(directory, name);
    try {
      const stat = await lstat(file);
      if (!stat.isSymbolicLink() || !previous || await readlink(file) !== previous[name])
        throw new Error(`Stable entry was edited or is unmanaged: ${file}`);
      old[name] = await readlink(file);
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  const changed = [];
  const replace = async (name, target) => {
    const file = join(directory, name), stage = `${file}.${randomUUID()}.tmp`;
    try { await symlink(target, stage, name === 'current' ? 'dir' : 'file'); await rename(stage, file); }
    finally { await rm(stage, { force: true }); }
  };
  const rollback = async () => {
    for (const name of [...changed].reverse()) {
      if (old[name]) await replace(name, old[name]);
      else await rm(join(directory, name), { force: true });
    }
  };
  try {
    for (const [name, target] of Object.entries(targets)) { await replace(name, target); changed.push(name); }
    return { targets, rollback };
  } catch (error) { await rollback(); throw error; }
}

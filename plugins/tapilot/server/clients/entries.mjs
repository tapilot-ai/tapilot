import { lstat, stat, link, readlink, symlink, rename, rm } from 'node:fs/promises';
import { join, normalize } from 'node:path';
import { randomUUID } from 'node:crypto';

export const sameLink = (a, b) => typeof b === 'string' && normalize(a).replace(/[\\/]$/, '') === normalize(b).replace(/[\\/]$/, '');

export async function entryMatches(file, target) {
  if (typeof target !== 'string') return false;
  const info = await lstat(file);
  if (info.isSymbolicLink()) return sameLink(await readlink(file), target);
  if (process.platform !== 'win32' || !info.isFile()) return false;
  try {
    const source = await stat(target);
    return source.dev === info.dev && source.ino === info.ino;
  } catch (error) {
    if (error.code === 'ENOENT') return false;
    throw error;
  }
}

// Stable host paths; releases remain immutable for already-running sessions.
export async function publishEntries(directory, runtime, previous) {
  const targets = { current: runtime.root, [process.platform === 'win32' ? 'node.exe' : 'node']: runtime.node };
  const old = {};
  for (const name of Object.keys(targets)) {
    const file = join(directory, name);
    try {
      await lstat(file);
      if (!previous || !await entryMatches(file, previous[name]))
        throw new Error(`Stable entry was edited or is unmanaged: ${file}`);
      old[name] = previous[name];
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
  }
  const changed = [];
  const replace = async (name, target) => {
    const file = join(directory, name), stage = `${file}.${randomUUID()}.tmp`;
    const backup = `${file}.${randomUUID()}.old`;
    let saved = false;
    try {
      if (process.platform === 'win32' && name !== 'current') await link(target, stage);
      else await symlink(target, stage, process.platform === 'win32' ? 'junction' : name === 'current' ? 'dir' : 'file');
      if (process.platform === 'win32' && old[name]) { await rename(file, backup); saved = true; }
      try { await rename(stage, file); }
      catch (error) { if (saved) await rename(backup, file); throw error; }
      if (saved) await rm(backup, { force: true });
    }
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

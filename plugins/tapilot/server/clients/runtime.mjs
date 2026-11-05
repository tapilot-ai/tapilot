import { cp, mkdir, mkdtemp, readFile, rm, chmod, writeFile } from 'node:fs/promises';
import { dirname, join, resolve, sep } from 'node:path';
import { execFileSync } from 'node:child_process';
import { constants } from 'node:fs';

export function runtimeInUse(directory) {
  // Darwin comm includes executable paths; Linux comm exposes only a process
  // name, so absence of a path cannot prove that managed code is unused.
  // Retain the runtime on platforms without a reliable path inventory.
  if (process.platform !== 'darwin') return true;
  try {
    // Executable names only. Never inspect argv, which may contain credentials.
    return execFileSync('ps', ['-axww', '-o', 'comm='], { encoding: 'utf8', timeout: 5000 })
      .split('\n').some(command => command.trim().startsWith(join(directory, 'releases') + sep));
  } catch { return true; } // Preserve code if activity cannot be established.
}

// An installation owns its code, dependencies and Node. No symlinks back to a
// checkout or an npm cache survive the copy. Phone state is deliberately absent.
export async function prepareRuntime({ root, node = process.execPath, directory }) {
  root = resolve(root);
  const releases = join(directory, 'releases');
  if (resolve(releases).startsWith(root + sep)) throw new Error('Runtime destination must be outside the source package');
  const version = execFileSync(node, ['--version'], { encoding: 'utf8' }).trim();
  const [major, minor] = version.slice(1).split('.').map(Number);
  if (!(major > 22 || major === 22 && minor >= 19)) throw new Error('Installation requires Node.js >=22.19');
  await mkdir(releases, { recursive: true, mode: 0o700 });
  const installed = await mkdtemp(join(releases, 'runtime-'));
  const dispose = () => rm(installed, { recursive: true, force: true });
  try {
    const manifest = JSON.parse(await readFile(join(root, 'package.json'), 'utf8'));
    for (const name of ['server', 'ui', 'assets', 'integrations', 'skills', 'scripts', 'node_modules', 'package.json', 'LICENSE']) {
      await cp(join(root, name), join(installed, name), { recursive: true, dereference: true, errorOnExist: true, force: false, mode: constants.COPYFILE_FICLONE });
    }
    const binary = join(installed, 'bin', process.platform === 'win32' ? 'node.exe' : 'node');
    await mkdir(dirname(binary));
    await cp(node, binary, { dereference: true, mode: constants.COPYFILE_FICLONE });
    await chmod(binary, 0o700);
    const runtime = { root: installed, node: binary, version: manifest.version, nodeVersion: version, platform: process.platform, arch: process.arch };
    await writeFile(join(installed, 'runtime.json'), JSON.stringify(runtime, null, 2) + '\n', { mode: 0o600 });
    return { ...runtime, dispose };
  } catch (error) { await dispose(); throw error; }
}

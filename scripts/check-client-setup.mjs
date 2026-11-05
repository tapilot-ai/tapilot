import { mkdtemp, mkdir, readFile, writeFile, rm } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { execFile, spawn } from 'node:child_process';
import { promisify } from 'node:util';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const exec = promisify(execFile);
if (process.platform !== 'darwin') throw new Error('Native setup archive verification currently requires macOS');
if (!process.argv[2]) throw new Error('Use node scripts/check-client-setup.mjs /path/to/distribution');
const distribution = resolve(process.argv[2]);
const status = JSON.parse(await readFile(join(distribution, 'status.json'), 'utf8'));
const archive = status.desktopArchive;
assert.equal(archive, `tapilot-setup-${process.platform}-${process.arch}-${status.version}.tar.gz`);
const bytes = await readFile(join(distribution, archive));
const digest = createHash('sha256').update(bytes).digest('hex');
assert.ok((await readFile(join(distribution, 'SHA256SUMS'), 'utf8')).split('\n').includes(`${digest}  ${archive}`));
const root = fileURLToPath(new URL('../', import.meta.url));
const work = await mkdtemp(join(root, 'build/setup-validation-'));
await exec('tar', ['-xzf', join(distribution, archive), '-C', work]);
const extracted = join(work, archive.replace(/\.tar\.gz$/, ''));
const cleanEnvironment = home => ({
  HOME: home, TMPDIR: join(work, 'tmp'), PATH: '/usr/bin:/bin:/usr/sbin:/sbin',
  LANG: 'en_US.UTF-8', LC_ALL: 'en_US.UTF-8',
});
await mkdir(join(work, 'tmp'));
async function menuInstall(entry, client, env) {
  const number = { doubao: 1, workbuddy: 2, dsh: 3, cherry: 7 }[client];
  return new Promise((resolveResult, reject) => {
    const child = spawn(entry, [], { env, stdio: ['pipe', 'pipe', 'pipe'] });
    let output = '', errors = '', selected = false, finished = false;
    const timeout = setTimeout(() => { child.kill(); reject(new Error('Interactive installer timed out for ' + client)); }, 120000);
    child.stdout.setEncoding('utf8');
    child.stdout.on('data', chunk => {
      output += chunk;
      if (!selected && output.includes('编号 / Number:')) { selected = true; child.stdin.write(number + '\n'); }
      if (!finished && output.includes('Press Enter to finish:')) { finished = true; child.stdin.end('\n'); }
    });
    child.stderr.setEncoding('utf8'); child.stderr.on('data', chunk => { errors += chunk; });
    child.on('error', error => { clearTimeout(timeout); reject(error); });
    child.on('close', code => {
      clearTimeout(timeout);
      if (code !== 0) return reject(new Error(`Interactive setup failed (${code}): ${errors}`));
      assert.ok(selected && finished, 'actual menu and completion prompt were exercised');
      resolveResult(output);
    });
  });
}
const results = [];
for (const client of ['doubao', 'workbuddy', 'dsh', 'cherry']) {
  const home = join(work, client, 'home');
  await mkdir(home, { recursive: true });
  const env = cleanEnvironment(home);
  await assert.rejects(exec('/usr/bin/env', ['node', '--version'], { env }), 'no external Node on the clean PATH');
  const launch = () => exec(join(extracted, 'Install Tapilot.command'), [client], { env, timeout: 120000, maxBuffer: 2e6 });
  const firstOutput = await menuInstall(join(extracted, 'Install Tapilot.command'), client, env);
  const recordPath = join(home, '.local/share/tapilot/clients', client, 'installation.json');
  const before = JSON.parse(await readFile(recordPath, 'utf8'));
  const configBefore = before.configFile ? await readFile(before.configFile, 'utf8') : null;
  const { stdout } = await launch();
  const after = JSON.parse(await readFile(recordPath, 'utf8'));
  assert.notEqual(before.root, after.root, 'upgrade creates an immutable release');
  assert.equal(before.entryNode, after.entryNode);
  assert.equal(before.entryRoot, after.entryRoot);
  assert.ok(after.skill.startsWith(home + '/'), 'canonical Skill stays inside the isolated home');
  if (before.configFile) assert.equal(await readFile(before.configFile, 'utf8'), configBefore);
  assert.ok(stdout.includes('not_tested'), 'wizard leaves host/device acceptance unclaimed');
  await writeFile(join(work, client, 'FIRST-INSTALL.txt'), firstOutput);
  await writeFile(join(work, client, 'INSTALLATION.md'), stdout);
  results.push({ client, home, recordPath, interactiveFirstInstall: 'passed', environment: 'allowlisted_without_inherited_provider_or_tapilot_config', firstInstall: 'passed', update: 'passed', stableHostPaths: 'passed' });
}
// Installer source and its bundled Node can be deleted without breaking hosts.
await rm(extracted, { recursive: true });
for (const result of results) {
  const record = JSON.parse(await readFile(result.recordPath, 'utf8'));
  const { stdout } = await exec(record.entryNode, [join(record.skill, 'scripts/tapilot.mjs'), '--help'], { env: cleanEnvironment(result.home), timeout: 15000 });
  assert.ok(stdout.includes('Tapilot CLI'));
  if (record.connector) {
    const probe = pathToFileURL(join(record.root, 'server/clients/probe.mjs')).href;
    await exec(record.entryNode, ['--input-type=module', '-e', `const {probeConnector} = await import(${JSON.stringify(probe)}); const result = await probeConnector(${JSON.stringify(record.connector)}); if(result.protocol !== 'passed') process.exit(1);`], { env: cleanEnvironment(result.home), timeout: 15000 });
  }
  else if (result.client === 'dsh') await exec(record.entryNode, ['--input-type=module', '-e', `await import(${JSON.stringify(pathToFileURL(join(record.entryRoot, 'integrations/dsh/index.mjs')).href)});`], { env: cleanEnvironment(result.home), timeout: 15000 });
  result.restartAfterSourceRemoval = 'passed';
}
await writeFile(join(work, 'results.json'), JSON.stringify({ archive, sha256: digest, results, osUserSession: 'not_tested_isolated_home_only', nativeHostRendering: 'not_tested', phone: 'not_tested' }, null, 2) + '\n');
console.log(`Setup archive install/update/restart checks passed: ${join(work, 'results.json')}`);

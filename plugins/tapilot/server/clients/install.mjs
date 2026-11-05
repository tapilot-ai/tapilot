import { readFile, writeFile, mkdir, rename, rm, lstat, readlink, symlink, mkdtemp, readdir } from 'node:fs/promises';
import { dirname, join, resolve, isAbsolute } from 'node:path';
import { homedir } from 'node:os';
import { createHash, randomUUID } from 'node:crypto';
import lockfile from 'proper-lockfile';
import { publishEntries, sameLink, entryMatches } from './entries.mjs';
import { parse, modify, applyEdits } from 'jsonc-parser';
import { clientConfiguration, packageRoot } from './catalog.mjs';
import { clientSkill, skillLauncher, connectorLauncher } from './skills.mjs';
import { probeConnector, probeDshPlugin } from './probe.mjs';
import { prepareRuntime, runtimeInUse } from './runtime.mjs';

const hash = text => createHash('sha256').update(text).digest('hex');
async function optional(path) {
  try { return await readFile(path, 'utf8'); } catch (error) { if (error.code === 'ENOENT') return null; throw error; }
}
async function info(path) {
  try { return await lstat(path); } catch (error) { if (error.code === 'ENOENT') return null; throw error; }
}
function document(text) {
  const errors = [];
  const value = parse(text, errors, { allowTrailingComma: true });
  if (errors.length || !value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Client configuration is not a JSON/JSONC object; it was not changed');
  return value;
}
async function atomic(path, text) {
  await mkdir(dirname(path), { recursive: true });
  const temporary = `${path}.${randomUUID()}.tmp`;
  try {
    const existing = await info(path);
    if (existing?.isSymbolicLink()) throw new Error(`Refusing to replace a symlink: ${path}`);
    await writeFile(temporary, text, { mode: existing ? existing.mode & 0o777 : 0o600, flag: 'wx' });
    await rename(temporary, path);
  } finally { await rm(temporary, { force: true }); }
}
function locations(client, options) {
  const home = options.home ?? homedir();
  const directory = join(options.dataRoot ?? join(home, '.local/share/tapilot/clients'), client);
  const skillsRoot = options.skillsRoot ?? process.env.TAPILOT_SKILLS_DIR ?? join(home, process.platform === 'darwin' ? 'Library/Mobile Documents/com~apple~CloudDocs/Agents/skills' : '.local/share/tapilot/skills');
  if (!isAbsolute(skillsRoot)) throw new Error('Skill directory must be an absolute path');
  return { directory, record: join(directory, 'installation.json'), skill: join(skillsRoot, 'tapilot-clients', client, 'tapilot'), launcher: join(directory, 'mcp.mjs') };
}
export function integrationPlan(client, options = {}) {
  const configuration = clientConfiguration(client, options);
  return { ...configuration, ...locations(client, options) };
}
async function verifyOwnedSkill(skill, record) {
  if (!await info(skill)) return;
  if ((await lstat(skill)).isSymbolicLink()) throw new Error(`Skill source must be a directory: ${skill}`);
  if (!record?.files) throw new Error(`Existing skill is not managed by this installer: ${skill}`);
  const paths = (await readdir(skill, { recursive: true, withFileTypes: true })).filter(entry => !entry.isDirectory());
  if (paths.length !== Object.keys(record.files).length) throw new Error(`Skill contains extra files; preserve it before updating: ${skill}`);
  for (const [path, digest] of Object.entries(record.files)) {
    if ((await info(join(skill, path)))?.isSymbolicLink()) throw new Error(`Skill contains a symlink: ${skill}`);
    const content = await optional(join(skill, path));
    if (content === null || hash(content) !== digest) throw new Error(`Skill was edited; preserve it before updating: ${skill}`);
  }
}
export async function installClient(client, options = {}) {
  let initial = integrationPlan(client, options);
  if (client === 'dsh' && options.dshHome === undefined && !process.env.DSH_HOME?.trim()) {
    const saved = await optional(initial.record);
    if (saved && JSON.parse(saved).dshHome) {
      options = { ...options, dshHome: JSON.parse(saved).dshHome };
      initial = integrationPlan(client, options);
    }
  }
  if (client === 'codex') return { ...initial, installed: false, next: 'Use the native Codex plugin commands in instructions. No second connector or skill was installed.' };
  const runtime = await (options.prepareRuntime ?? prepareRuntime)({ root: options.root ?? packageRoot, node: options.node ?? process.execPath, directory: initial.directory });
  try {
    return await installPreparedClient(client, { ...options, root: runtime.root, node: runtime.node, runtime });
  } catch (error) { await runtime.dispose(); throw error; }
}
async function installPreparedClient(client, options) {
  const plan = integrationPlan(client, options);
  const root = resolve(options.root ?? packageRoot);
  // Protocol validation precedes every write to a client configuration.
  const probe = !plan.connector && !plan.pluginDirectory ? { protocol: 'not_applicable', hostRendering: client === 'cherry' ? 'not_tested' : 'not_applicable', phone: 'not_tested' }
    : await (options.probe ?? (plan.connector ? probeConnector : probeDshPlugin))(plan.connector ?? plan.pluginDirectory);
  const entryRoot = join(plan.directory, 'current');
  const entryNode = join(plan.directory, process.platform === 'win32' ? 'node.exe' : 'node');
  if (client === 'dsh') {
    plan.pluginDirectory = join(entryRoot, 'integrations/dsh');
    plan.instructions = plan.instructions.map(text => text.replaceAll(root, entryRoot));
  }
  if (plan.connector) {
    plan.connector.command = entryNode;
    plan.connector.env.PATH = [plan.directory, process.env.PATH].filter(Boolean).join((options.platform ?? process.platform) === 'win32' ? ';' : ':');
    plan.connector.args = [plan.launcher];
    plan.config = { [plan.configKey]: { tapilot: plan.connector } };
  }
  await mkdir(plan.directory, { recursive: true, mode: 0o700 });
  const release = await lockfile.lock(plan.directory, { retries: 0 });
  let staged;
  try {
    const recordText = await optional(plan.record);
    const record = recordText ? JSON.parse(recordText) : null;
    if (record && (record.skill !== plan.skill || record.configFile !== plan.configFile || record.skillLink !== plan.skillLink))
      throw new Error('Installation paths changed. Uninstall the previous client integration first.');
    await verifyOwnedSkill(plan.skill, record);
    if (record?.launcherHash && hash(await optional(plan.launcher) ?? '') !== record.launcherHash)
      throw new Error('Connector launcher was edited; preserve it before updating');
    let configText, updatedConfig;
    if (plan.configFile) {
      configText = await optional(plan.configFile);
      if ((await info(plan.configFile))?.isSymbolicLink()) throw new Error('Client configuration is a symlink; merge config manually');
      const value = document(configText ?? '{}');
      const section = value[plan.configKey];
      if (section !== undefined && (!section || typeof section !== 'object' || Array.isArray(section))) throw new Error('Invalid MCP server section');
      const current = section?.tapilot;
      if (record ? JSON.stringify(current) !== JSON.stringify(record.connector) : current !== undefined)
        throw new Error('Existing tapilot connector is unmanaged or edited; merge config manually');
      updatedConfig = applyEdits(configText ?? '{}\n', modify(configText ?? '{}\n', [plan.configKey, 'tapilot'], plan.connector, { formattingOptions: { insertSpaces: true, tabSize: 2 } }));
    }
    if (plan.skillLink && await info(plan.skillLink)) {
      if (!(await lstat(plan.skillLink)).isSymbolicLink() || resolve(dirname(plan.skillLink), await readlink(plan.skillLink)) !== plan.skill)
        throw new Error('Existing tapilot skill link is unmanaged; it was not changed');
    }
    const files = { 'SKILL.md': await clientSkill(root, client), 'scripts/tapilot.mjs': skillLauncher(plan.record), 'references/client-setup.md': await readFile(join(root, 'skills/tapilot/references/client-setup.md'), 'utf8') };
    await mkdir(dirname(plan.skill), { recursive: true });
    staged = await mkdtemp(join(dirname(plan.skill), '.tapilot-stage-'));
    await mkdir(join(staged, 'scripts'));
    await mkdir(join(staged, 'references'));
    for (const [path, content] of Object.entries(files)) await writeFile(join(staged, path), content, { mode: 0o600 });
    // Stage everything before changing the host. On failure the previous config
    // is restored; user-owned files are never overwritten by force flags.
    const oldSkill = `${plan.skill}.previous-${randomUUID()}`;
    const hadSkill = !!await info(plan.skill);
    let linked = false, swapped = false, configWritten = false, recordWritten = false, entries;
    const oldLauncher = await optional(plan.launcher);
    try {
      if (plan.connector) await atomic(plan.launcher, connectorLauncher(plan.record, client));
      if (hadSkill) await rename(plan.skill, oldSkill);
      await rename(staged, plan.skill); staged = null; swapped = true;
      if (plan.skillLink && !await info(plan.skillLink)) {
        await mkdir(dirname(plan.skillLink), { recursive: true });
        await symlink(plan.skill, plan.skillLink, process.platform === 'win32' ? 'junction' : 'dir'); linked = true;
      }
      const { dispose, ...runtime } = options.runtime;
      entries = await publishEntries(plan.directory, runtime, record?.entries);
      const next = { version: 3, client, root, runtime, entryRoot, entryNode, entries: entries.targets, ...(plan.dshHome ? { dshHome: plan.dshHome } : {}), skill: plan.skill, skillLink: plan.skillLink, configFile: plan.configFile, connector: plan.connector, ...(plan.connector ? { launcherHash: hash(connectorLauncher(plan.record, client)) } : {}), files: Object.fromEntries(Object.entries(files).map(([path, content]) => [path, hash(content)])) };
      await atomic(plan.record, JSON.stringify(next, null, 2) + '\n');
      recordWritten = true;
      // Publish the host config last: a host may start its connector immediately
      // after noticing the change, so the launcher and record must already work.
      if (plan.configFile) {
        if (await optional(plan.configFile) !== configText) throw new Error('Client configuration changed during installation; retry after saving it');
        await atomic(plan.configFile, updatedConfig); configWritten = true;
      }
      await rm(oldSkill, { recursive: true, force: true });
      return { ...plan, runtime, installed: true, registration: plan.configFile ? 'configured' : client === 'dsh' ? 'pending_native' : client === 'skill' ? 'skill_exported' : 'pending_client_ui', probe, launcher: plan.connector ? { command: entryNode, args: [plan.launcher] } : null, skillFolder: plan.skill, nativeRegistration: client === 'dsh' ? 'pending' : undefined, next: plan.instructions };
    } catch (error) {
      if (entries) await entries.rollback();
      if (recordWritten) { if (recordText === null) await rm(plan.record, { force: true }); else await atomic(plan.record, recordText); }
      if (configWritten) { if (configText === null) await rm(plan.configFile, { force: true }); else await atomic(plan.configFile, configText); }
      if (linked) await rm(plan.skillLink, { force: true });
      if (swapped) await rm(plan.skill, { recursive: true, force: true });
      if (hadSkill && await info(oldSkill)) await rename(oldSkill, plan.skill);
      if (oldLauncher === null) await rm(plan.launcher, { force: true }); else await atomic(plan.launcher, oldLauncher);
      throw error;
    }
  } finally {
    if (staged) await rm(staged, { recursive: true, force: true });
    await release();
  }
}
export async function uninstallClient(client, options = {}) {
  let plan = integrationPlan(client, options);
  if (client === 'codex') return { client, removed: false, instructions: ['Remove Tapilot through the native Codex plugin manager. The shared phone pairing is preserved.'] };
  if (!await info(plan.record)) return { client, removed: false, instructions: client === 'codex' ? plan.instructions : ['No managed installation found.'] };
  const release = await lockfile.lock(plan.directory, { retries: 0 });
  try {
    const record = JSON.parse(await readFile(plan.record, 'utf8'));
    if (client === 'dsh' && record.dshHome && options.dshHome === undefined && !process.env.DSH_HOME?.trim())
      plan = integrationPlan(client, { ...options, dshHome: record.dshHome });
    if (record.skill !== plan.skill || record.configFile !== plan.configFile || record.skillLink !== plan.skillLink)
      throw new Error('Installation paths changed. Use the original installation paths.');
    await verifyOwnedSkill(record.skill, record);
    if (record.launcherHash && hash(await optional(plan.launcher) ?? '') !== record.launcherHash)
      throw new Error('Connector launcher was edited; it was not removed');
    let configText, updatedConfig;
    if (record.configFile) {
      configText = await readFile(record.configFile, 'utf8');
      const value = document(configText);
      if (JSON.stringify(value[plan.configKey]?.tapilot) !== JSON.stringify(record.connector)) throw new Error('Connector was edited; it was not removed');
      updatedConfig = applyEdits(configText, modify(configText, [plan.configKey, 'tapilot'], undefined, {}));
    }
    if (record.skillLink && await info(record.skillLink)) {
      if (!(await lstat(record.skillLink)).isSymbolicLink() || resolve(dirname(record.skillLink), await readlink(record.skillLink)) !== record.skill)
        throw new Error('Skill link was edited; it was not removed');
    }
    if (record.configFile) await atomic(record.configFile, updatedConfig);
    if (record.skillLink) await rm(record.skillLink, { force: true });
    await rm(record.skill, { recursive: true, force: true });
    await rm(plan.launcher, { force: true });
    await rm(plan.record);
    for (const [name, target] of Object.entries(record.entries ?? {})) {
      const file = join(plan.directory, name);
      if (await info(file) && await entryMatches(file, target)) await rm(file);
    }
    // A shared phone daemon may have been started by this client and still serve
    // another client. DSH may also have this package loaded under its own Node.
    const runtimeRetained = client === 'dsh' || runtimeInUse(plan.directory);
    if (!runtimeRetained) await rm(join(plan.directory, 'releases'), { recursive: true, force: true });
    return { client, removed: true, pairingPreserved: true, runtimeRetained, next: client === 'cherry' ? ['Remove the imported Tapilot Skill and its Agent bindings through Cherry Studio settings.'] : client === 'doubao' ? ['Delete the connector and uploaded skill in Doubao Work.'] : client === 'dsh' ? ['Remove the native plugin separately: dsh plugin --profile web remove @tapilot/dsh'] : [] };
  } finally { await release(); }
}

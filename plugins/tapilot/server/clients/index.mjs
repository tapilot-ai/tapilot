import { readFile } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { join } from 'node:path';
import { parse } from 'jsonc-parser';
import { integrationPlan, installClient, uninstallClient } from './install.mjs';
import { onboarding } from './onboarding.mjs';
import { CLIENTS } from './catalog.mjs';
import { probeConnector, probeDshPlugin } from './probe.mjs';
import { collect } from '../../scripts/probe-client-host.mjs';

export function detection(client, evidence = collect()) {
  if (client && !CLIENTS.includes(client)) throw new Error(`Unknown client: ${client}. Choose ${CLIENTS.join(', ')}`);
  return {
    ...evidence, selectedClient: client ?? null, recommendation: client ?? (CLIENTS.includes(evidence.host) ? evidence.host : null),
    selection: client ? 'explicit' : 'confirmation_required', choices: CLIENTS,
    next: client ? `Run config --client ${client}; install only for the requested target.` : 'Use the user’s stated target. Otherwise offer the recommendation and ask once. Detection never installs or selects an adapter automatically.',
  };
}

export async function clientCommand(command, args, options = {}) {
  if (Object.keys(args).some(key => !['client', 'skills_dir'].includes(key))) throw new Error('Use --client CLIENT [--skills-dir ABSOLUTE_PATH]');
  if (command === 'detect') return detection(args.client);
  if (!args.client) {
    if (['config', 'install', 'onboard'].includes(command)) return { ...detection(), installed: false };
    throw new Error('Choose --client CLIENT explicitly');
  }
  options = { ...options, ...(args.skills_dir ? { skillsRoot: args.skills_dir } : {}) };
  if (command === 'onboard') {
    const installation = await installClient(args.client, options);
    if (!installation.installed) return { ...installation, onboarding: onboarding(installation) };
    const diagnostics = await clientCommand('doctor', args, options);
    return { ...installation, diagnostics, onboarding: onboarding(installation, diagnostics) };
  }
  if (command === 'install') return installClient(args.client, options);
  if (command === 'uninstall') return uninstallClient(args.client, options);
  let plan = integrationPlan(args.client, options);
  let record;
  try { record = JSON.parse(await readFile(plan.record, 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; }
  if (record) {
    plan = { ...integrationPlan(args.client, { ...options, ...(record.dshHome ? { dshHome: record.dshHome } : {}), root: record.entryRoot ?? record.root, node: record.entryNode ?? record.runtime?.node ?? record.connector?.command }), connector: record.connector, runtime: record.runtime };
    if (record.connector) plan.config = { [plan.configKey]: { tapilot: record.connector } };
  }
  if (command === 'config') return { ...plan, installed: !!record };
  if (!record) return { client: args.client, installed: false, protocol: 'not_tested', hostRendering: 'not_tested', phone: 'not_tested', next: plan.instructions };
  let registration = 'pending_client_ui';
  if (plan.configFile) {
    let text = null;
    try { text = await readFile(plan.configFile, 'utf8'); } catch (error) { if (error.code !== 'ENOENT') throw error; }
    registration = JSON.stringify(text && parse(text)?.[plan.configKey]?.tapilot) === JSON.stringify(record.connector) ? 'configured' : 'missing_or_edited';
  }
  const { stdout } = await promisify(execFile)(record.runtime?.node ?? process.execPath, [join(plan.skill, 'scripts/tapilot.mjs'), '--help'], { timeout: 15000 });
  if (!stdout.includes('Tapilot CLI')) throw new Error('Installed skill launcher did not start the CLI');
  const probe = !plan.connector && !plan.pluginDirectory ? { protocol: 'not_applicable', hostRendering: args.client === 'cherry' ? 'not_tested' : 'not_applicable', phone: 'not_tested' }
    : plan.connector ? await probeConnector(plan.connector) : await probeDshPlugin(plan.pluginDirectory);
  return { client: args.client, installed: true, isError: registration === 'missing_or_edited', runtime: record.runtime, skillLauncher: 'passed', registration: args.client === 'dsh' ? 'pending_native_verification' : args.client === 'skill' ? 'skill_exported' : registration, ...probe, configFile: plan.configFile, panel: plan.panel,
    connector: plan.connector, launcher: plan.connector ? { command: record.entryNode ?? record.runtime?.node ?? record.connector.command, args: [plan.launcher] } : null,
    skillFolder: plan.skill, pluginDirectory: plan.pluginDirectory, next: plan.instructions };
}

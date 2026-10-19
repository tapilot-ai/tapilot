import { applyModelEnvironment } from '../model-environment.ts';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { config as loadEnv } from 'dotenv';
const root = fileURLToPath(new URL('../', import.meta.url));
const configured = loadEnv({ path: process.env.TAPILOT_E2E_ENV_FILE || `${root}/.env` });
// An explicit missing file must not fall back to another account/provider.
if (process.env.TAPILOT_E2E_ENV_FILE && configured.error) throw configured.error;
applyModelEnvironment(configured.parsed);
const args = process.argv.slice(2);
const profile = ['--live', '--resilience', '--auth-fixture'].includes(args[0]) ? args.shift() : '';
const live = !!profile;
// File selection otherwise makes the CLI discover config relative to cases/fixtures.
const configArgs = args.includes('--config') || ['model', 'create'].includes(args[0])
  ? [] : ['--config', `${root}/midscene.config.ts`];
const result = spawnSync(process.execPath, [`${root}/node_modules/@midscene/test/bin/midscene-test`, ...args, ...configArgs], {
  cwd: root, stdio: 'inherit',
  env: {
    ...process.env,
    TAPILOT_E2E_LIVE: live ? '1' : '0',
    TAPILOT_E2E_FAULTS: profile === '--resilience' ? '1' : '0',
    TAPILOT_E2E_AUTH_FIXTURE: profile === '--auth-fixture' ? '1' : '0',
    ...(live ? { MIDSCENE_INCLUDE_TAGS: process.env.MIDSCENE_INCLUDE_TAGS || (profile === '--resilience' ? 'resilience' : profile === '--auth-fixture' ? 'auth-fixture' : 'live') } : {}),
  },
});
if (result.error) throw result.error;
process.exit(result.status ?? 1);

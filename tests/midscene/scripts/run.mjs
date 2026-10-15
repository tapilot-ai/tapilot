import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { config as loadEnv } from 'dotenv';
const root = fileURLToPath(new URL('../', import.meta.url));
const configured = loadEnv({ path: process.env.TAPILOT_E2E_ENV_FILE || `${root}/.env` });
// A selected local model config must not silently use another shell's model/token.
for (const [key, value] of Object.entries(configured.parsed || {})) {
  if (/^MIDSCENE_(?:PLANNING_|INSIGHT_)?MODEL_/.test(key) && value.trim()) process.env[key] = value;
}
const args = process.argv.slice(2);
const profile = ['--live', '--resilience', '--auth-fixture'].includes(args[0]) ? args.shift() : '';
const live = !!profile;
const result = spawnSync(process.execPath, [`${root}/node_modules/@midscene/test/bin/midscene-test`, ...args], {
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

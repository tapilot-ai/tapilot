import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import { execFileSync } from 'node:child_process';
import { parsePairingLink, saveConfig } from './config.mjs';
import { PhoneConnection } from './transport.mjs';

const reader = createInterface({ input: stdin, output: stdout });
let phone;
try {
  const link = process.argv.includes('--clipboard') && process.platform === 'darwin'
    ? execFileSync('pbpaste', { encoding: 'utf8' }).trim()
    : (await reader.question('Paste the pairing link copied from Tapilot (not into a chat): ')).trim();
  const config = parsePairingLink(link);
  phone = new PhoneConnection(config);
  const { token } = await phone.open(true);
  await saveConfig({ ...config, token });
  console.log('Paired. You can now use the Tapilot MCP plugin. No USB connection is required.');
} catch (error) { console.error(error.message); process.exitCode = 1; }
finally { phone?.close(); reader.close(); }

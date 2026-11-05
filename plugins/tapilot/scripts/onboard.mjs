import { createInterface } from 'node:readline/promises';
import { stdin, stdout } from 'node:process';
import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { clientCommand } from '../server/clients/index.mjs';
import { installationGuide } from '../server/clients/onboarding.mjs';

const choices = ['doubao', 'workbuddy', 'dsh', 'codex', 'vscode', 'skill', 'cherry'];
const labels = ['豆包工作 / Doubao Work', 'WorkBuddy', 'DSH Web', 'Codex', 'VS Code Copilot', '其他客户端：仅 Skill / Skill only', 'Cherry Studio（桌面 Work Agent）'];
const terminal = createInterface({ input: stdin, output: stdout });
try {
  let client = process.argv[2];
  if (!client) {
    console.log('Tapilot 接入 / Client setup\n请选择要安装或升级的客户端 / Choose a client:\n');
    choices.forEach((value, index) => console.log(`${index + 1}. ${labels[index]}`));
    const answer = (await terminal.question('\n编号 / Number: ')).trim();
    client = choices[Number(answer) - 1];
  }
  if (!choices.includes(client)) throw new Error('请选择列表中的客户端 / Choose a listed client');
  console.log(`\n正在安装并检查 / Installing and checking: ${client}…`);
  const result = await clientCommand('onboard', { client });
  const guide = installationGuide(result);
  console.log('\n' + guide);
  if (result.installed) {
    const file = join(result.directory, 'INSTALLATION.md');
    await writeFile(file, guide, { mode: 0o600 });
    console.log(`\n接入说明已保存 / Saved instructions: ${file}`);
  }
  if (!process.argv[2]) await terminal.question('\n按回车结束；请保留客户端窗口 / Press Enter to finish: ');
} catch (error) { console.error(error.message); process.exitCode = 1; }
finally { terminal.close(); }

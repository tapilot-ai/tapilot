export function onboarding(installation, diagnostics) {
  const { client } = installation;
  if (client === 'codex') return {
    complete: false, checks: [{ id: 'native_plugin', status: 'manual', steps: installation.instructions }],
  };
  const checks = [{ id: 'local_installation', status: installation.installed ? 'passed' : 'pending' },
    { id: 'protocol', status: diagnostics?.protocol ?? 'not_tested' }];
  if (client === 'doubao') checks.push({ id: 'host_registration', status: 'manual',
    steps: ['Add/refresh the custom STDIO connector using launcher.command and launcher.args.',
      'Import skillFolder, enable the connector and Skill, then start a Local computer task.',
      'On Skill updates, re-import the exported folder; local installation does not update account-side uploads.'],
    launcher: installation.launcher, skillFolder: installation.skillFolder });
  else if (client === 'dsh') checks.push({ id: 'native_registration', status: 'manual',
    command: { command: 'dsh', args: ['plugin', '--profile', 'web', 'add', installation.pluginDirectory], env: { DSH_HOME: installation.dshHome } },
    steps: ['Register this stable plugin path once, then reload/restart DSH Web after updates.',
      'In the conversation header, select Tapilot to open its right sidebar. DSH Go requires separate acceptance.'] });
  else if (client === 'standard' || client === 'skill' || client === 'cherry') checks.push({ id: 'host_registration', status: 'manual', steps: installation.instructions, skillFolder: installation.skillFolder });
  else checks.push({ id: 'host_enable', status: 'manual', steps: ['Enable/trust Tapilot in the selected client and start a new Agent conversation.', 'After updates, restart the connector/client to load the new runtime.'] });
  if (client !== 'skill') checks.push({ id: 'native_panel', status: 'not_tested', steps: [
    client === 'cherry' ? 'Open the preview in the Work Agent’s built-in browser pane using the imported Tapilot Skill; this is not an MCP App.' : 'Ask Tapilot to open the phone panel in this conversation; observe actual phone pixels.',
    ...(client === 'doubao' || client === 'workbuddy' ? ['If MCP rendering fails after one recovery, open CLI preview in the client’s built-in browser sidebar.'] : []),
    'A successful config or protocol probe does not verify a native panel.' ] });
  checks.push({ id: 'phone_task', status: 'not_tested', steps: ['After the user requests phone testing, read a fresh screenshot and verify a harmless task.', 'Confirm reopen, host restart and disconnect recovery in the actual client; never resume a private pause automatically.'] });
  return { complete: false, checks, update: 'Run onboard again from the new verified package, then reload the selected client. Stable host paths follow the managed installation record.' };
}

const shellQuote = value => "'" + String(value).replaceAll("'", "'\\''") + "'";
export function installationGuide(result) {
  const steps = result.onboarding?.checks ?? [];
  const labels = { native_plugin: '安装原生插件 / Native plugin', local_installation: '本地安装 / Local installation', protocol: '连接检查 / Connection check', host_registration: '客户端导入 / Client import', native_registration: 'DSH 插件注册 / DSH registration', host_enable: '客户端启用 / Enable in client', native_panel: '会话内画面 / In-session panel', phone_task: '实际任务与恢复 / Task and recovery' };
  return `# Tapilot · ${result.client}\n\n${result.installed ? '本地安装已完成；宿主启用和实际画面需继续确认。' : '请按客户端原生安装说明继续。'}\n\n` +
    steps.map(check => `## ${labels[check.id] ?? check.id} · ${check.status}\n\n` +
      (check.steps ?? []).map(step => `- ${step}`).join('\n') +
      (check.launcher ? `\n\nSTDIO command:\n\n\`\`\`text\n${check.launcher.command}\n\`\`\`\n\nSTDIO arguments (JSON array):\n\n\`\`\`json\n${JSON.stringify(check.launcher.args)}\n\`\`\`\n\nSkill folder:\n\n\`\`\`text\n${check.skillFolder}\n\`\`\`` : '') +
      (check.skillFolder && !check.launcher ? `\n\nSkill folder:\n\n\`\`\`text\n${check.skillFolder}\n\`\`\`` : '') +
      (check.command ? `\n\n在 DSH 所在电脑复制执行 / Run on the DSH host:\n\n\`\`\`json\n${'DSH_HOME=' + shellQuote(check.command.env.DSH_HOME) + ' ' + [check.command.command, ...check.command.args].map(shellQuote).join(' ')}\n\`\`\`` : '') + '\n').join('\n') +
    `\n${result.onboarding?.update ?? ''}\n`;
}

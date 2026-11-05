import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { installerName, writeInstaller, nodeLicense, zipDirectory } from './client-distribution-platform.mjs';

// Consume the audited, bundled artifact; never resolve a fresh dependency tree.
const root = fileURLToPath(new URL('../', import.meta.url));
const verified = JSON.parse(fs.readFileSync(path.join(root, 'build/cli-package-validation/latest.json')));
if (!verified.passed || !fs.existsSync(verified.artifact)) throw new Error('Run check:cli-package first');
const out = fs.mkdtempSync(path.join(root, 'build/client-distribution-'));
const json = (file, value) => {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n');
};
const unpack = target => {
  fs.mkdirSync(target, { recursive: true });
  execFileSync('tar', ['-xzf', verified.artifact, '-C', target, '--strip-components=1']);
};
const workbuddy = path.join(out, 'workbuddy/plugins/tapilot');
unpack(workbuddy);
const { clientSkill } = await import(pathToFileURL(path.join(workbuddy, 'server/clients/skills.mjs')));
const hostSkills = {};
for (const client of ['doubao', 'workbuddy', 'dsh', 'cherry']) hostSkills[client] = await clientSkill(workbuddy, client);
fs.writeFileSync(path.join(workbuddy, 'skills/tapilot/SKILL.md'), hostSkills.workbuddy);
const pkg = JSON.parse(fs.readFileSync(path.join(workbuddy, 'package.json')));
// One platform-specific offline installer; dependencies and Node travel together.
const desktopName = `tapilot-setup-${process.platform}-${process.arch}-${pkg.version}`;
const desktop = path.join(out, desktopName);
unpack(path.join(desktop, 'runtime'));
fs.mkdirSync(path.join(desktop, 'bin'), { recursive: true });
const bundledNode = path.join(desktop, 'bin', process.platform === 'win32' ? 'node.exe' : 'node');
fs.copyFileSync(process.execPath, bundledNode);
fs.chmodSync(bundledNode, 0o755);
// Redistribute the exact Node binary with its upstream notices.
fs.copyFileSync(nodeLicense(), path.join(desktop, 'NODE-LICENSE.txt'));
json(path.join(desktop, 'runtime-version.json'), { node: process.version, platform: process.platform, arch: process.arch, tapilot: pkg.version });
writeInstaller(desktop);
fs.writeFileSync(path.join(desktop, 'README.txt'), `Tapilot setup / 接入安装\n\n运行 ${installerName}，选择客户端。再次运行新版本接入包即可升级。\nRun ${installerName} and select a client. Run a new verified bundle to update.\n\n本包自带当前平台的 Node 和完整依赖，无需源码目录。账号侧导入、信任和重载操作请按安装结果完成。\nIncludes Node and dependencies for this platform. Follow the output for host import, trust and reload.\n`);
const desktopArchive = `${desktopName}.tar.gz`;
execFileSync('tar', ['-czf', path.join(out, desktopArchive), '-C', out, desktopName]);

json(path.join(workbuddy, '.codebuddy-plugin/plugin.json'), {
  name: 'tapilot', version: pkg.version, description: pkg.description,
  author: { name: 'quanru' }, license: 'MIT',
});
json(path.join(workbuddy, '.mcp.json'), { mcpServers: { tapilot: {
  type: 'stdio', command: 'node', args: ['${CODEBUDDY_PLUGIN_ROOT}/server/index.mjs'],
  cwd: '${CODEBUDDY_PLUGIN_ROOT}', env: { TAPILOT_HOST: 'workbuddy' },
} } });
json(path.join(out, 'workbuddy/.codebuddy-plugin/marketplace.json'), {
  name: 'tapilot', owner: { name: 'quanru' },
  plugins: [{ name: 'tapilot', source: './plugins/tapilot', version: pkg.version,
    description: 'Live Android phone panel and screenshot-based control', category: 'productivity' }],
});

// Put the entire runtime inside the DSH package, retaining internal relative paths.
const dsh = path.join(out, 'dsh');
unpack(dsh);
fs.writeFileSync(path.join(dsh, 'skills/tapilot/SKILL.md'), hostSkills.dsh);
json(path.join(dsh, 'package.json'), { ...pkg, name: '@tapilot/dsh',
  main: './integrations/dsh/index.mjs',
  exports: { '.': './integrations/dsh/index.mjs', './client': './integrations/dsh/client.js' },
  engines: { ...pkg.engines, dsh: '>=0.2.1-rc.5 <0.3.0-0' },
  dsh: { bundle: { patch: './integrations/dsh/cordis.patch.yml' }, client: { platform: 'web' } },
});
// The lockfile identifies the original runtime package, not this distribution.
fs.rmSync(path.join(dsh, 'package-lock.json'), { force: true });
const pack = (directory, filename) => execFileSync('tar', ['-czf', path.join(out, filename), '-C', directory, '.']);
pack(dsh, `tapilot-dsh-${pkg.version}.tar.gz`);
pack(path.join(out, 'workbuddy'), `tapilot-workbuddy-${pkg.version}.tar.gz`);

const doubao = path.join(out, 'doubao/tapilot');
fs.cpSync(path.join(workbuddy, 'skills/tapilot'), doubao, { recursive: true });
fs.mkdirSync(path.join(doubao, 'assets'), { recursive: true });
fs.copyFileSync(path.join(workbuddy, 'assets/icon.png'), path.join(doubao, 'assets/icon.png'));
fs.copyFileSync(path.join(workbuddy, 'LICENSE'), path.join(doubao, 'LICENSE'));
const skillPath = path.join(doubao, 'SKILL.md');
const skill = hostSkills.doubao;
fs.writeFileSync(skillPath, skill.replace('\n---\n', `\nmetadata:\n  display_name: Tapilot\n  display_description: 在会话中查看和操作已配对的 Android 手机，交付带截图的执行报告。\n  icon: ./assets/icon.png\n  version: "${pkg.version}"\n---\n`));
const zip = path.join(out, `tapilot-doubao-${pkg.version}.zip`);
zipDirectory(doubao, zip);
// Cherry Studio uses the desktop Agent's Skill + native browser pane.
const cherry = path.join(out, 'cherry/tapilot');
fs.cpSync(path.join(workbuddy, 'skills/tapilot'), cherry, { recursive: true });
fs.writeFileSync(path.join(cherry, 'SKILL.md'), hostSkills.cherry);
const cherryZip = `tapilot-cherry-${pkg.version}.zip`;
zipDirectory(cherry, path.join(out, cherryZip));
// A personal Skill import must have a usable bootstrap artifact beside it.
fs.copyFileSync(verified.artifact, path.join(out, `tapilot-runtime-${pkg.version}.tgz`));
fs.writeFileSync(path.join(out, 'INSTALL.zh.md'), `# Tapilot 个人接入包\n\n${process.platform} ${process.arch} 用户解压 ${desktopArchive}，运行 ${installerName} 并选择客户端；内含 Node 和依赖。以下为手动接入方式，需要 Node.js >=22.19。\n\n## 豆包工作\n\n解压 tapilot-runtime-${pkg.version}.tgz，在 package 目录执行：\n\n\`\`\`sh\nnode server/cli.mjs install --client doubao\n\`\`\`\n\n用返回的 launcher.command 和 launcher.args 添加自定义 STDIO 连接器；导入返回的 skillFolder，启用连接器和 Skill，开始本地电脑任务。支持 ZIP 导入时也可导入 tapilot-doubao-${pkg.version}.zip。无需企业认证。\n\n## Cherry Studio\n\n在统一菜单选择 Cherry Studio，导入返回的 Skill 文件夹，或在本机运行时安装后导入 tapilot-cherry-${pkg.version}.zip。绑定到桌面 Work Agent，并按宿主提示启用 Shell、读图和 Browser 工具。使用会话内浏览器侧栏；不注册 MCP 连接器。\n\n## WorkBuddy\n\n将 workbuddy 目录作为本地插件市场添加，再安装 tapilot。目录包含完整运行时，不需要开发者源码路径。首次信任以及原生面板仍需在客户端完成。\n\n## DSH Web\n\n在 DSH 所在电脑运行 dsh plugin --profile web add 加上解压后的 dsh 目录路径，刷新页面，在会话标题处打开 Tapilot。此包尚未公开发布，不能使用 npm 包名安装。DSH Go 另需验收。\n`);
const checksums = fs.readdirSync(out).filter(name => /\.(tgz|zip|tar\.gz)$/.test(name)).map(name => createHash('sha256').update(fs.readFileSync(path.join(out, name))).digest('hex') + '  ' + name);
fs.writeFileSync(path.join(out, 'SHA256SUMS'), checksums.join('\n') + '\n');
json(path.join(out, 'status.json'), { desktopArchive, installerName, platform: process.platform, arch: process.arch, cherryZip, version: pkg.version, sourceArtifact: verified.artifact,
  workbuddy, dsh, doubaoZip: zip, published: false,
  requirements: ['Node >=22.19', 'Doubao: separately installed Tapilot runtime and connector',
    'Doubao personal import: no enterprise store submission; native acceptance pending',
    'DSH index: immutable published source commit or npm version required'] });
json(path.join(root, 'build/client-distribution-path.json'), { directory: out });
console.log(out);

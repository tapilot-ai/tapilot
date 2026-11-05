import { homedir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const packageRoot = fileURLToPath(new URL('../../', import.meta.url));
export const CLIENTS = Object.freeze(['codex', 'doubao', 'workbuddy', 'cursor', 'vscode', 'standard', 'dsh', 'cherry', 'skill']);
export function clientConfiguration(client, { home = homedir(), platform = process.platform, root = packageRoot, node = process.execPath, dshHome = process.env.DSH_HOME } = {}) {
  if (!CLIENTS.includes(client)) throw new Error(`Unknown client: ${client}. Choose ${CLIENTS.join(', ')}`);
  if (client === 'cherry') return {
    client, panel: 'browser-sidebar', configFile: null, configKey: null, connector: null, config: null, skillLink: null,
    instructions: ['In Cherry Studio Settings → Skills → Add Skill → Local import, import skillFolder.',
      'Enable the Skill globally and bind it to a desktop Work Agent with Shell, File and Browser tools.',
      'Use the CLI for phone actions and the conversation’s built-in browser pane for live preview. This does not register an MCP server.',
      'Native browser rendering and real-phone acceptance require verification; ordinary chat/mobile without local shell cannot run this Skill.'],
    acceptance: 'Skill import, desktop Agent tools and actual in-conversation browser pixels require native acceptance. MCP Apps support is not claimed.',
  };
  if (client === 'skill') return {
    client, panel: 'none', configFile: null, configKey: null, connector: null, config: null, skillLink: null,
    instructions: ['Install the exported skillFolder using your agent’s skill mechanism.', 'Use the CLI for phone actions. This integration does not register an embedded panel or MCP server.'],
    acceptance: 'Skill discovery and real-phone actions require verification in the selected agent.',
  };
  if (client === 'dsh') {
    const configured = dshHome?.trim() ? dshHome : join(home, '.dsh');
    const dshDirectory = resolve(configured === '~' ? home : /^~[\\/]/.test(configured) ? join(home, configured.slice(2)) : configured);
    return {
    client, panel: 'interactive', configFile: null, configKey: null, connector: null, config: null,
    dshHome: dshDirectory, skillLink: join(dshDirectory, 'skills/tapilot'), pluginDirectory: join(root, 'integrations/dsh'),
    instructions: ['Register the native Web panel on the DSH host:', `dsh plugin --profile web add ${JSON.stringify(join(root, 'integrations/dsh'))}`, 'Reload the DSH Web page and select Tapilot in the sidebar. Keep the installed Tapilot package directory.', 'DSH Go SSH/Web-view support requires host acceptance. Relay connections are not supported for embedded panels.'],
    acceptance: 'Native plugin registration, host rendering and real-phone acceptance require verification in DSH.',
    };
  }
  const entry = { command: node, args: [join(root, 'server/index.mjs')], env: { TAPILOT_HOST: client, PATH: [dirname(node), process.env.PATH].filter(Boolean).join(platform === 'win32' ? ';' : ':'), NO_PROXY: '127.0.0.1,localhost,::1' } };
  const files = {
    workbuddy: join(home, '.workbuddy/mcp.json'),
    cursor: join(home, '.cursor/mcp.json'),
    vscode: platform === 'darwin' ? join(home, 'Library/Application Support/Code/User/mcp.json') : platform === 'win32' ? join(process.env.APPDATA || join(home, 'AppData/Roaming'), 'Code/User/mcp.json') : join(home, '.config/Code/User/mcp.json'),
  };
  const skills = { workbuddy: join(home, '.workbuddy/skills/tapilot'), cursor: join(home, '.cursor/skills/tapilot'), vscode: join(home, '.copilot/skills/tapilot') };
  const instructions = {
    codex: ['Use the existing Codex plugin marketplace installation. This bundles the MCP panel, CLI and skill.', 'From the repository: codex plugin marketplace add .', 'codex plugin add tapilot@tapilot-local'],
    doubao: ['In Plugins · Skills · Partners, add a custom STDIO connector named tapilot.', 'Use connector.command and connector.args below. If the form has no environment fields, use launcher instead.', 'Upload the exported tapilot skill folder. Enable the connector and skill, then start a new Local computer task.'],
    workbuddy: ['Trust and enable tapilot in Connector management, then start a new conversation.', 'The embedded preview is read-only. Use the Tapilot skill for pairing, selecting devices, private pause/resume and phone actions.'],
    cursor: ['Enable the tapilot MCP server and skill, then open a new Agent conversation.'],
    vscode: ['Trust/start the tapilot MCP server and enable the skill in Copilot Agent mode.'],
    standard: ['Merge the configuration into your MCP Apps client. Install the exported skill using its supported mechanism.'],
  };
  const configKey = client === 'vscode' ? 'servers' : 'mcpServers';
  const connector = client === 'vscode' ? { type: 'stdio', ...entry } : entry;
  return { client, panel: client === 'workbuddy' ? 'read-only' : 'interactive', configFile: files[client] ?? null, skillLink: skills[client] ?? null, connector, configKey, config: { [configKey]: { tapilot: connector } }, instructions: instructions[client], acceptance: 'Host rendering and real-phone acceptance have not been verified by configuration generation.' };
}

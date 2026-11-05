import { readFile } from 'node:fs/promises';
import { join } from 'node:path';

export async function clientSkill(root, client) {
  const source = await readFile(join(root, 'skills/tapilot/SKILL.md'), 'utf8');
  const note = client === 'cherry' ? 'This desktop Work Agent uses Skill + CLI and its built-in browser pane. No Tapilot MCP connector is registered. Shell and image-reading File tools are required; use Browser tools for the visible panel. Ordinary chat/mobile without local shell is not supported by this integration.' : client === 'dsh' ? 'Open the Tapilot panel inside DSH Web using its sidebar entry. For agent actions use the CLI on the DSH host, where Tapilot and the phone connection run. This host uses a native panel plugin, not the MCP panel opener. Do not call tapilot_preview_open here. DSH Go relay connections do not provide this panel.' : client === 'workbuddy'
    ? 'The MCP panel is read-only and updates through read-only resources. Do not ask it to call write tools. Use CLI pair and its local confirmation page for pairing; use CLI connect, pause, resume and phone commands for all agent changes. The separate built-in browser sidebar can display the interactive local preview.'
    : 'Open tapilot_preview_open once at the start of a phone task. Use the embedded MCP App for live preview and the CLI for agent phone actions.';
  let text = source;
  if (client === 'doubao' || client === 'workbuddy') {
    const start = text.indexOf('## Open the panel first');
    const end = text.indexOf('## Locate the CLI');
    if (start < 0 || end < start) throw new Error('Skill panel instructions changed; review the native browser adaptation');
    text = text.slice(0, start) + `## Open the panel first

At the start of each phone-related user turn, call tapilot_preview_open with
recover: true once if that tool is exposed, then reuse the panel. Recovery never
resumes a privacy pause, switches devices, resets pairing, or replays an action.

If the MCP opener is unavailable or its view remains blank after one recovery,
run CLI preview without --open. Open the returned URL in this client's built-in
browser sidebar, alongside the conversation. Preserve its URL fragment for
authorization, but never print the fragment in chat. Do not open the system
browser or ask for a development checkout path. Report this route as a native
browser sidebar, separately from MCP App validation. Observe actual pixels
before claiming that either panel works.

For an open-only request, stop after opening the panel. Do not run phone actions.

` + text.slice(end);
  }
  if (client === 'workbuddy') {
    const pairing = 'Pair through the panel button when embedded. Otherwise run';
    if (!text.includes(pairing)) throw new Error('Skill pairing instructions changed; review the WorkBuddy adaptation');
    text = text.replace(pairing, 'Run');
  }
  if (client === 'dsh' || client === 'skill' || client === 'cherry') {
    const start = text.indexOf('## Open the panel first');
    const end = text.indexOf('## Locate the CLI');
    if (start < 0 || end < start) throw new Error('Skill panel instructions changed; review the DSH adaptation');
    const opening = client === 'cherry' ? 'Do not call tapilot_preview_open. Run CLI preview without --open. Use Cherry Studio’s built-in cherry-browser Skill and its live browser automation tool to navigate this conversation’s visible browser pane to the returned URL. Read the live browser API documentation before navigating. Preserve the capability fragment for authorization, but never print it in chat. Do not use the system browser. If Browser tools are unavailable, explain the missing capability and ask the user to open the URL in this conversation’s browser pane; do not expand permissions yourself. Verify actual phone pixels beside the conversation before claiming the panel is open. For an open-only request, stop after opening; do not execute phone actions.' : client === 'dsh' ? 'Open Tapilot from the DSH Web conversation header to keep the panel beside messages. If no UI-control tool is available, ask the user to click that header action; do not inspect host source or claim you opened it. Agent actions use CLI below.' : 'This agent uses Skill + CLI. Do not call tapilot_preview_open. Open CLI preview --open only if the user wants a browser preview.';
    text = text.slice(0, start) + `## Open the panel first\n\n${opening} ${client === 'cherry' ? 'A separate system browser does not count as an embedded panel.' : 'A browser preview does not count as embedded-panel acceptance.'}\n\n` + text.slice(end);
  }
  return text.replace('# Tapilot over Wi-Fi', `# Tapilot over Wi-Fi\n\n## ${client} installation\n\n${client === 'skill' ? 'Use CLI for agent phone actions; no embedded panel is registered.' : note}\n\nThe launcher in this exported skill resolves the managed installation record. For integration repair or a different host, read [client setup](references/client-setup.md).\n`);
}
export function skillLauncher(record) {
  return managedLauncher(record, 'server/cli.mjs');
}
export function connectorLauncher(record, client) {
  return managedLauncher(record, 'server/index.mjs', client);
}
function managedLauncher(record, entry, client) {
  // JSON literals are JavaScript, never interpolated into shell commands.
  return `#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { dirname, delimiter, join } from 'node:path';
import { spawn } from 'node:child_process';
const installation = JSON.parse(readFileSync(${JSON.stringify(record)}, 'utf8'));
const node = installation.runtime.node;
${client ? `process.env.TAPILOT_HOST=${JSON.stringify(client)};` : ''}
process.env.PATH=[dirname(node),process.env.PATH].filter(Boolean).join(delimiter);
process.env.NO_PROXY=[process.env.NO_PROXY,'127.0.0.1,localhost,::1'].filter(Boolean).join(',');
const child = spawn(node, [join(installation.root, ${JSON.stringify(entry)}), ...process.argv.slice(2)], { stdio: 'inherit', env: process.env });
for (const signal of ['SIGINT','SIGTERM']) process.on(signal, () => child.kill(signal));
child.on('error', error => { process.stderr.write(error.message+'\\n'); process.exitCode=1; });
child.on('exit', (code, signal) => { process.exitCode=code ?? (signal ? 1 : 0); });
`;
}

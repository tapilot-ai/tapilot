# Embedded client panels

Tapilot uses one phone runtime and one screen UI. Agents operate through the CLI
and skill; MCP Apps and the DSH Web plugin embed the live panel in the client.
The existing Codex plugin and standalone browser retain their interactive controls.

An external browser preview is useful but does not count as embedded-client support.
Configuration generation and STDIO checks do not prove native-host rendering.

See [the native acceptance log](NATIVE-ACCEPTANCE.md) for actual client attempts,
remaining blockers and the current test scope. Cursor is excluded from further
native testing at the user's request.

## Unified desktop setup

Use the verified `tapilot-setup-PLATFORM-ARCH-VERSION.tar.gz` archive. It includes
Node and dependencies. Match the execution OS and architecture using the official
release manifest. Extract it and run `Install Tapilot.command` on macOS,
`Install Tapilot.cmd` on Windows, or `sh install-tapilot.sh` on Linux, then
choose Doubao Work, WorkBuddy, DSH Web, Cherry Studio, Codex, VS Code or Skill only. The installer
runs `onboard --client CLIENT`, installs into managed storage, checks the installed
launcher/protocol and saves `INSTALLATION.md` with remaining host steps. Codex
continues to use its native plugin; the wizard displays its instructions.

Run the new verified setup archive to update. Host entries use stable `node`,
`mcp.mjs` and (for DSH) `current/integrations/dsh` paths. The installer switches
managed entries after validation and preserves old releases for open sessions.
Migrating a legacy Doubao connector or DSH registration requires replacing its
old release path with the stable path once. Subsequent updates need a host reload;
Doubao account-side Skill uploads must still be re-imported when content changes.

The archive is platform-specific. macOS gatekeeping may require normal user
approval; the installer does not bypass it. Six-platform CI covers macOS, Windows
and Linux on x64/ARM64. Native client rendering, physical phones and fresh standard-user
sessions require separate acceptance. No public release is implied by
building a local archive.

## Client status

| Client | Delivery | Panel policy | Validation in this change |
| --- | --- | --- | --- |
| Codex | Existing native plugin | Interactive; retains widget session recovery | Original regression suite and SDK iframe fixture |
| Doubao Work | STDIO connector + uploaded skill | Interactive MCP Apps | Actual native MCP panel recovery and English conversation with live browser-sidebar pixels; MCP stability and complete lifecycle acceptance remain pending |
| WorkBuddy | Local connector + skill link | Read-only resource-based live preview | Chinese/English real conversations and live emulator pixels in the native browser sidebar; MCP profile remains pending |
| Cursor | User MCP config + skill link | Interactive MCP Apps | Excluded from further native tests by user; previous fixture results only |
| VS Code Copilot | User MCP config + skill link | Interactive MCP Apps | STDIO and SDK iframe fixture; native host/device pending |
| DSH Web | Native sidebar/main panel plugin + skill link | Interactive, authenticated DSH routes | Actual DSH 0.2.1-rc.5 Chinese/English conversations with live Android 15 emulator and native plugin sidebar; full lifecycle acceptance pending |
| DSH Go | DSH host's Web plugin | Experimental | No Android client acceptance; relay panels unsupported |
| Cherry Studio desktop Work Agent | Imported Skill + managed CLI | Conversation browser pane; no MCP server | Actual native 2.1.10 Skill import and Chinese/English conversations with live Android 15 emulator pixels; full lifecycle pending |
| Other agents | Generic skill / standard MCP Apps config | Depends on actual host capabilities | Must be accepted separately |

WorkBuddy's policy is deliberately scoped to its connector. The reference host
integration reports that app-origin tool calls are rejected. This adapter reads
only frames and the saved device list through resources. Pairing, device changes,
pause/resume and phone actions remain available through the CLI skill. Resource
URLs cannot encode writes. A future interactive WorkBuddy adapter needs actual
host verification; do not silently retry a failed write over another channel.

## Prerequisites

Bootstrap from a verified local package/repository with installed dependencies
and Node.js 22.19 or newer. `install` copies code, dependencies and Node into
`~/.local/share/tapilot/clients/<client>/releases/` before changing host config.
Moving the original checkout or clearing its Node cache afterward is safe.
Stable launchers read `installation.json` on each invocation; GUI clients receive
an absolute managed Node path. Prior releases remain available for open sessions
and saved GUI launch commands. Uninstall removes idle releases; it preserves code
with a running managed process, a DSH registration, or unknown process activity,
so another client's shared phone daemon is not broken.

Installation, update and restart after removing the installer source are verified
on native six-platform CI runners. This does not establish native client rendering
on every OS. Public archives are listed at https://tapilot.lifeos.md/install;
only use a published matching entry with a verified SHA-256.

## Inspect and diagnose

```sh
tapilot detect
tapilot config --client doubao
tapilot doctor --client doubao
```

Clients: `codex`, `doubao`, `workbuddy`, `cursor`, `vscode`, `standard`, `dsh`, `cherry`, `skill`.

`detect` writes nothing and only recommends a host. An explicit `--client` wins;
an install without a target returns a selection request without installing.
`config` writes nothing and reads the installed record when present. `doctor`
validates the installed Skill launcher, configuration and actual installed STDIO
connector, including the panel opener and bundled HTML. It
does not inspect the client's active session or contact a phone. DSH diagnostics
validate its native package entry, not native registration or browser rendering.
Results explicitly distinguish `hostRendering` and `phone` from protocol checks.

## Codex

Continue to use the native plugin from the repository root:

```sh
codex plugin marketplace add .
codex plugin add tapilot@tapilot-local
```

Reload the plugin host after updating. `tapilot install --client codex` returns
these native installation instructions without creating a duplicate connector.

## WorkBuddy, Cursor and VS Code

```sh
tapilot install --client workbuddy
tapilot install --client cursor
tapilot install --client vscode
```

The installer probes the connector before changing client files, preserves JSONC
comments and unrelated settings, and refuses unmanaged/edited `tapilot` entries.
The default user config paths are:

| Client | macOS config | Skill link |
| --- | --- | --- |
| WorkBuddy | `~/.workbuddy/mcp.json` | `~/.workbuddy/skills/tapilot` |
| Cursor | `~/.cursor/mcp.json` | `~/.cursor/skills/tapilot` |
| VS Code | `~/Library/Application Support/Code/User/mcp.json` | `~/.copilot/skills/tapilot` |

Enable/trust the connector in the client and open a new Agent conversation. VS
Code uses the `servers` configuration schema; other MCP clients use `mcpServers`.

User-managed exported skills have their canonical source under:

```text
~/Library/Mobile Documents/com~apple~CloudDocs/Agents/skills/tapilot-clients/<client>/tapilot/
```

Host skill folders contain symlinks to this source. Set `TAPILOT_SKILLS_DIR` or
pass `--skills-dir /absolute/canonical/skills` to select an existing canonical
root. The installer refuses to remove or replace an edited skill or extra files.
These exports are generated from the product's bundled skill, with a small host
introduction, client-setup guidance and a record-backed CLI launcher. Use
`install --client skill` to export a Skill without registering an MCP connector
or promising an embedded panel. The bundled Skill guides client selection,
installation, host-specific manual steps and separate native acceptance.

## Doubao Work

```sh
tapilot install --client doubao
```

The result includes `launcher.command`, `launcher.args` and `skillFolder`.
In **Plugins · Skills · Partners**, add a custom **STDIO** connector named
`tapilot`. Use the returned launcher command and arguments; no environment fields
are required. Upload `skillFolder`, enable the connector and skill, then create a
new **Local computer** task. Refresh an existing connector by disabling/enabling it.

Doubao opens the panel in its right workbench using the MCP App display-mode
capabilities. Closing that tab returns it inline; use **Open in sidebar** to expand
it again. Availability still depends on the installed native host version.

The installer prepares local files; account-side connector and skill registration
remain separate steps. When updated skill content changes, upload it again.

## DSH Web and DSH Go

```sh
tapilot install --client dsh
dsh plugin --profile web add /absolute/tapilot/integrations/dsh
```

Use the `pluginDirectory` from the install result; the installed CLI package root
contains `integrations/dsh`. Installation exports/links the skill and reports
native registration as **pending** until the `dsh plugin` command is executed.
The returned directory is a managed copy; the original checkout may be moved.
Legacy registrations require one migration to the stable returned path. After updates, reload/restart DSH Web. In a conversation, select
**Tapilot** in the session header to open the native right sidebar alongside the
messages. The global **Tapilot** entry still opens a dedicated panel page.
The plugin targets DSH `>=0.2.1-rc.5 <0.3.0-0` and requires
`connection.fetch.register`; it fails explicitly if the authenticated API is absent.

The host registers `/api/tapilot/panel` and `/api/tapilot/call` through DSH
Connection. DSH performs authentication, origin checks and owner admission. The
plugin forwards only validated panel requests to the shared runtime. It exposes
neither the runtime admin capability nor a public loopback proxy. Remote pages
use same-origin DSH routes, so `localhost` never refers to the mobile browser.

DSH Go is a separate acceptance target. SSH/Web views may render the Web plugin;
relay connections do not expose the required panels. Phone-local DSH Go runtime
compatibility has not been verified. A tool card or external browser launch does
not count as DSH Go embedded-panel support.

## Update and remove

Update the stable Tapilot package, rerun the same install command, then reload
the client. This changes only that client's managed entry and skill. All clients
continue to share phone pairing and the existing runtime-upgrade behavior.

```sh
tapilot uninstall --client cursor
```

Uninstall removes only managed connector entries and skill links/exports. It
preserves unrelated servers, pairing, reports and other client integrations.
Doubao connectors/uploads must also be removed in its UI. Remove the DSH native
plugin separately with `dsh plugin --profile web remove @tapilot/dsh`.

## Validation

```sh
npm run check --prefix plugins/tapilot
npm run check:panels --prefix plugins/tapilot
pnpm check:quick
pnpm check:cli-package
```

`check:panels` uses a real Chromium MCP Apps SDK host, production STDIO servers and
shared runtime, and an isolated TLS phone fixture. It verifies embedded frame
loading, manual Home, pause/resume and WorkBuddy resource-only preview. It creates
screenshots and `results.json` in `build/client-validation/panels`. On macOS it
uses installed Google Chrome; set `TAPILOT_CHROME_PATH` elsewhere. This requires
development dependencies, not the production-only npm artifact.

For an isolated DSH Web host with this plugin installed, run:

```sh
TAPILOT_DSH_LOG=/private/path/to/dsh-launch.log npm run check:dsh-panel --prefix plugins/tapilot
```

The host should have its provider onboarding completed and no paired phone in
its isolated Tapilot state. The smoke checks the actual sidebar/iframe, rejects
an unauthenticated panel request and a non-panel tool, and captures `dsh-web.png`
and `dsh-native.json`. The launch token is not printed. The local acceptance run
used a dummy provider key without running model inference; it is not a provider
authentication or Android hardware test.

Before marking a native client supported, record client/OS/model versions,
actual panel placement, live phone pixels, pairing/device changes, available
manual controls, CLI screenshot reading and a verified phone task. Also check
host restart, history reopening, disconnect/lock recovery and two viewers.
Record transport, native-host and hardware evidence separately. Never claim a
30-fps stream or latency improvement based on another project's WDA implementation.

DSH installations honor `DSH_HOME` (default `~/.dsh`), placing the host Skill link
under its `skills/` directory. Use the same host environment for native plugin
registration. The installation record retains a custom DSH home for diagnostics
and default uninstall. A different explicit target home requires removing the
previous managed integration first; it is never silently moved.


## Cherry Studio desktop Work Agent

```sh
tapilot onboard --client cherry
```

Or choose **Cherry Studio** in the unified desktop setup menu. The installer copies
the runtime into managed storage and exports the Cherry-specific Skill into the
canonical Skills directory. It does not write Cherry's internal application state,
register an MCP connector, or promise MCP Apps support.

In **Settings → Skills → Add Skill → Local import**, import the returned
`skillFolder`. Enable it globally and bind it to a desktop **Work Agent** with
Shell, image-reading File and Browser tools. Run `CLI preview` without `--open`
and use the conversation's built-in browser pane for the authenticated live view.
The exported Skill routes the Agent through the host's `cherry-browser` instructions.
If Browser tools are absent, finish the host capability setup; never substitute a
system-browser window for embedded acceptance. Ordinary chat/mobile without a
local shell is outside this integration's scope. Re-import the Skill after its
contents change. Removing the managed integration does not remove the account's
imported Skill; remove that import through Cherry settings separately.

Sources checked 2026-11-05:
[Skill local import and Agent binding](https://cherryai.com/docs/en/advanced-basic/extensions/skills/),
[official native browser Skill](https://github.com/CherryHQ/cherry-studio/blob/main/resources/skills/cherry-browser/SKILL.md),
[desktop MCP Apps feature request](https://github.com/CherryHQ/cherry-studio/issues/16159).
The open issue and inspected source motivate the Skill/browser route; actual
native rendering is tracked independently in NATIVE-ACCEPTANCE.md.

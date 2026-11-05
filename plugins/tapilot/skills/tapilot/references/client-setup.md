# Install or repair client integration

Use this only for client installation, migration or a missing opener/runtime.
Phone pairing and control remain in SKILL.md. Installing a connector does not
pair a phone, resume a private pause or authorize internal Midscene AI.

## Select a target

Use the user's explicit client choice. An exported skill's installation heading
identifies where it was installed, not necessarily the current execution host.
If no target is stated, run `CLI detect` for a read-only recommendation and ask
once. `CLI detect --client NAME` keeps an explicit choice even when evidence
points elsewhere. Never silently install into a detected host. Host detection
cannot prove panel support, Web versus Go, or native rendering.

Targets: codex, doubao, workbuddy, vscode, cursor, dsh, standard, cherry, skill.
Use `standard` only for a client known to support MCP Apps. Use `skill` for
agents with shell and image-reading tools that have no embedded panel support.
A client with only MCP and no local shell cannot use this Skill's phone actions.

## Prepare and install

1. Run `CLI onboard --client NAME` for the requested target. This installs or
   updates its managed runtime, then runs installed-launcher and protocol checks.
   Follow the returned `onboarding.checks` in order. Preserve `manual` and
   `not_tested` states until actually completed. Never ask for confirmation again
   when the user already requested installation into this named client.
   `CLI config --client NAME` remains a read-only plan for inspection.
2. The installation returns the actual `launcher` and `skillFolder`. Use those
   exact values, never invent a developer path. Exported user skills live in the
   canonical skills root; host directories link to it. `--skills-dir PATH` selects
   a different canonical root; use the same option for diagnosis/uninstall.
3. Finish only the selected host's steps:
   - **Codex:** use the existing native plugin instructions returned by install.
     Do not create a second MCP server or replace the plugin-managed Skill.
   - **WorkBuddy / VS Code / Cursor:** the installer merges the MCP configuration
     and links the Skill. Guide the user through first trust/enable and a new
     conversation; VS Code requires a signed-in Copilot Agent.
   - **Doubao Work:** add a custom STDIO connector using `launcher.command` and
     `launcher.args`, upload `skillFolder`, enable both, and start a new Local
     computer task. These UI steps are not completed by writing local files.
     Upload the Skill again when its content changes.
   - **DSH:** execute the returned `dsh plugin --profile web add ...` command on
     the DSH host, reload Web and open Tapilot in the conversation header. Native
     plugin registration is a separate step, not an MCP connector. Do not infer
     DSH Go acceptance from Web or from a server-side shell.
   - **Cherry Studio:** import the exported skillFolder through Settings → Skills
     → Local import. Enable it globally and bind it to a desktop Work Agent with
     Shell, image-reading File and Browser tools. No MCP server or private settings
     database is changed. Use the conversation's built-in browser pane for preview;
     do not claim MCP Apps support or use the system browser as embedded acceptance.
     Chat/mobile without local shell is outside this integration's scope.
   - **Skill only:** install `skillFolder` using the agent's Skill mechanism.
     No MCP connector or embedded panel is registered.
4. Run `CLI doctor --client NAME`. It checks the installed Skill launcher and
   actual installed connector/package, not just the source package. Report
   configuration, protocol, native panel rendering and real-phone actions
   separately. Only an observed native session proves the last two. Do not start
   phone control merely to validate an installation unless requested.

Existing edited connectors or Skills are preserved. Explain the conflict and
inspect the user's change; do not delete it, force reinstall or uninstall it as
an automatic recovery. Other clients and saved phone pairing are shared/preserved.
Updates retain prior runtime releases so already-open host processes and saved
GUI launch commands can finish; new launches resolve the current installation
record. Uninstall preserves runtime code still used by a managed process, DSH,
or when process activity cannot be determined. Restart/reload the host after updating. Existing DSH registrations using a release path need one migration to the returned stable pluginDirectory. Subsequent updates keep this path; reload/restart the host. Uninstall removes only that client's managed files; Doubao's
account-side connector/Skill and DSH's plugin registration need host-side removal.

Managed background startup uses retained code and Node even when imported by DSH
or a native plugin. Native plugins may reuse a matching managed installation;
without one they still require the host's supported Node. Daemon handoff preserves
privacy pause. Legacy daemons cannot export that state, so their first migration
starts paused; wait for the user to be ready and explicitly resume before capture.

## No runtime available

If the Skill launcher reports a missing runtime, do not ask for a developer's
checkout path and do not fall back to ADB. Read https://tapilot.lifeos.md/install/agent.en.md
and https://tapilot.lifeos.md/releases/clients/latest.json. Select a published archive
whose platform and arch match the machine running the client, and verify its size
and SHA-256 before extraction. Never run an archive for a different OS or architecture.
The archive includes Node and dependencies. On macOS run Install Tapilot.command;
on Windows run Install Tapilot.cmd; on Linux run sh install-tapilot.sh. Choose only
the requested client. A missing matching archive means automatic setup must stop. If using
a verified Tapilot package or the user's existing repository instead: install its locked dependencies with the supported
Node, then run `node <package>/server/cli.mjs install --client NAME`. This initial
bootstrap needs a package containing server, UI/assets, integrations, scripts,
Skills and dependencies; a standalone SKILL.md is insufficient. Do not invent an
npm installation command: the tapilot package has not yet been published there.
A network downloader/release updater is not part of this installer.

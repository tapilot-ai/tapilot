# Tapilot Client Setup 0.6.19 Preview

Bring a paired Android phone into your AI conversation with guided setup from the Tapilot website.

## What's included

- English and Chinese setup pages with a copyable agent prompt and public Markdown instructions.
- Managed installation for the selected client, preserving existing configuration and pairing. Use the installer's actual returned paths for host setup.
- A macOS Apple Silicon offline archive containing Node and audited dependencies; no source checkout or separately installed Node is required.
- Client-specific guidance for WorkBuddy, Doubao Work, Cherry Studio desktop Work Agent, DSH Web, Codex and VS Code Copilot.
- Separate reporting for installation/protocol checks, host rendering and phone reading. The first phone verification is read-only.
- Midscene core updated to 1.17.6, matching the merged main branch.

## Start or update

Visit https://tapilot.lifeos.md/install (English) or https://tapilot.lifeos.md/zh/install (Chinese).

Download the archive for your platform and compare its SHA-256 with SHA256SUMS before extracting it. Run `Install Tapilot.command` and select only your client. For updates, select the same client, preserve configuration and pairing, then restart the connector/client. Re-import account-side Skills where required.

## Scope and acceptance

This is a client setup preview, not a new Android APK or an npm/marketplace publication. The Android application remains at 0.1.9. Windows, Linux and Intel Mac are not validated for the unified setup archive.

Historical native host examples include in-app browser sidebars for Cherry Studio, WorkBuddy and Doubao Work, and the native DSH Web sidebar. Browser sidebar success does not establish MCP panel acceptance. Codex's existing native plugin route remains separate. VS Code native sessions, DSH Go, full Chinese Doubao acceptance and Codex's full native flow in this release remain unverified. Cursor native tests are excluded.

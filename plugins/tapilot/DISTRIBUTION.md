# Client distribution

Verified against the vendors' documentation on 2026-11-04.

| Client | Supported distribution | Current delivery |
| --- | --- | --- |
| Doubao Work | Personal Skill import plus a custom STDIO connector | ZIP available; no enterprise qualification needed for this local route |
| WorkBuddy | Third-party plugin marketplace, containing Skills and MCP configuration | Local marketplace with a bundled runtime; native installation pending |
| Cherry Studio desktop | Local Skill folder/ZIP import into a Work Agent | Managed CLI + native browser pane; no MCP connector |
| DSH Web | Native bundle installation and experimental custom plugin indexes | Standalone bundle registered with the official DSH CLI; no public index yet |

These are different from inclusion in a vendor-curated store. DSH ships no index
source. DSH Go is a separate client; Web panel acceptance does not establish Go
native panel support.

## Unified macOS setup

The builder also emits `tapilot-setup-darwin-ARCH-VERSION.tar.gz` with a supported
Node binary, the complete audited runtime and `Install Tapilot.command`. Users
extract, double-click, choose a client and follow only its remaining host steps.
No checkout, npm installation or external Node is needed. `SHA256SUMS` covers all
archives. The archive supports the builder's macOS architecture only.

The wizard saves a local `INSTALLATION.md` and separates installation/protocol
checks from native panel and phone-task acceptance. Run a new verified archive to
update; this is an explicit offline update, not a network auto-updater. Distribution
through a public download or vendor marketplace remains a publication step.

## Build

Run `pnpm check:cli-package` with Node >=22.19 first. Then run:

```sh
node scripts/build-client-distribution.mjs
```

The builder consumes the verified, audited CLI artifact rather than resolving
dependencies again. It prints a new output directory with:

- `workbuddy/`: `.codebuddy-plugin/marketplace.json`, the plugin manifest, Skill,
  complete runtime and MCP configuration using `${CODEBUDDY_PLUGIN_ROOT}`.
- `tapilot-cherry-VERSION.zip`: host-specific Skill for a desktop Work Agent;
  install the managed runtime first. It does not install an MCP connector.
- `dsh/`: complete runtime with DSH package entrypoints. Internal imports remain
  within this package, rather than escaping into a developer's checkout.
- `tapilot-doubao-VERSION.zip`: `SKILL.md` at ZIP root, display metadata, 512px
  icon, license and scripts. Requires the separately installed runtime and
  Doubao STDIO connector; the Skill alone does not provide a panel.

The WorkBuddy and DSH archives include dependencies but not Node itself. They
require supported Node on the host. Local directory installation is for native
acceptance; publishing requires hosting the complete distribution, not copying a
local absolute path into a public listing.

For an npm artifact, run `npm pack --ignore-scripts` in the generated `dsh/`
directory. It already contains the built UI and audited bundled dependencies.
Do not publish the source `integrations/dsh` subdirectory on its own.

After publishing the DSH bundle, generate its official-format index:

```sh
node scripts/build-dsh-market-index.mjs --version VERSION --output index.json
```

This verifies that the exact version is present on npm and is a DSH bundle before
writing the index. Host the result over HTTPS, enable Custom plugin indexes in
DSH, and add that URL under Discover. No package or index has been published yet.

## Verify the setup archive

```sh
node scripts/check-client-setup.mjs /path/to/client-distribution
```

This uses isolated home/Skill directories for Doubao, WorkBuddy and DSH. It runs
the actual `.command` entry with no external Node on PATH, installs twice, checks
stable host configuration, deletes the installer source, and starts fresh managed
CLI/connector/plugin processes. Results remain under `build/setup-validation-*`.
These are installation lifecycle checks, not native UI or device acceptance.
The manual `Verify desktop client setup` GitHub workflow builds and verifies the
same archives and uploads downloadable CI artifacts; it does not publish a release.

## Doubao personal installation

Use the personal installation route. Enterprise store submission is out of scope
because the maintainer does not have enterprise qualification.

1. Extract the verified CLI runtime package into a directory of your choice.
2. With Node >=22.19, run `node server/cli.mjs install --client doubao` from that
   directory. The installer copies the runtime to managed storage and returns
   `launcher` and `skillFolder`.
3. In Doubao Work, add a custom STDIO connector using those returned launcher
   values. Import the returned Skill folder (or the distributed Skill ZIP where
   ZIP import is supported), then enable the Skill and connector.
4. Start a Local computer task and ask Tapilot to open its phone panel.

The runtime is included beside the Skill ZIP in the personal distribution. There
is no dependency on the developer's checkout path and no unpublished `npx`
command. The actual connector path belongs to the user's managed installation.

### Historical store draft

The official console accepted the 0.6.18 ZIP and saved a draft. Six sample prompts
and runtime prerequisites are included in the draft. It will not be submitted.
Store submission would need
enterprise qualification, category, and a Feishu evaluation document readable by
the platform reviewers. Complete actual native acceptance before submitting;
static ZIP validation is not task acceptance. Do not invent a report URL or
represent an unfinished client test as passing.

## Sources

- [Doubao Skill submission](https://open.doubao.com/document/doubao-developer-guide/skill-integration-guide)
- [WorkBuddy plugin system](https://www.codebuddy.cn/docs/workbuddy/Plugins)
- [CodeBuddy marketplace format](https://cloud.tencent.com/document/product/1831/137031)
- [Official DSH plugin indexes](https://github.com/deepseek-ai/deepseek-harness/blob/master/docs/user/guide/plugin-market.md)

## Native screenshot acceptance

Use English Android system UI and separate Chinese/English conversations. Only
captures of completed native host tasks are website examples. The 2026-11-04 DSH
emulator panel capture proves live rendering, but the chat still has an invalid
model key; it is diagnostic evidence, not a completed showcase. WorkBuddy retains
the user's unsent Skill-editing draft. Doubao's Chinese task started and opened
the right-side MCP panel; capture completion is blocked by native automation
`cgWindowNotFound`. Do not substitute a browser mockup or synthetic chat.


## Native browser sidebar recovery

Doubao Work and WorkBuddy were both observed rendering the actual emulator in
an in-app browser sidebar on 2026-11-05. This provides a fallback when the MCP
opener is absent or its iframe remains blank after one recovery. Run `preview`
without `--open` and open the returned URL, including its capability fragment,
in the host's own browser sidebar. Never print the fragment in conversation text.
The exported host Skills include this route. It is distinct from MCP App/profile
acceptance; a separate system-browser window is not embedded-panel acceptance.

Managed runtime updates do not replace already running connector children or
account-side uploaded Skills automatically. Restart the client/connector, re-import
the exported Skill when needed, and verify the version and actual pixels inside
a new native conversation. Do not treat `doctor` protocol checks as that proof.

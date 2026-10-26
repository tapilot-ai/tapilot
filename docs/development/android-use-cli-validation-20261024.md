# Android Use 0.6.0: CLI and shared runtime validation

## Delivered architecture

- Agent operations: `plugins/android-use/server/cli.mjs`, also available through the
  `android-use` package bin and `android-use-cli` skill launcher.
- Persistent local state: `server/runtime.mjs`; singleton startup, authenticated
  loopback HTTP and process ownership: `runtime-client.mjs` / `runtime-server.mjs`.
- Embedded panel: `server/index.mjs` exposes the opener and app-only panel tools.
  Removed model-operation MCP tools are rejected by dispatch as well as absent
  from the tool list. Panel pairing has a separate, narrowly validated tool.
- Standalone preview: `ui/browser-app.mjs` adapts the same screen component to a
  restricted browser capability. Browser and CLI use the same phone connection.
- No ADB integration and no changes to the Midscene or midscene-skills repositories.

Existing phone-side edits in `ComputerPairing.kt` and `WirelessControlService.kt`
were already present before this work; they are not part of this CLI migration.

## Automated evidence

`npm run check --prefix plugins/android-use`: **77/77 tests pass**, including
bundled UI consistency. Skill Creator's `quick_validate.py` also passes.

The new cross-process TLS-fixture integration checks concurrent CLI startup,
screenshot files with mode 0600, retaining a single authenticated phone connection
between separate CLI invocations and preview, panel suspension without invalidating
the agent frame, rejecting stale taps without sending another mutation, privacy
pause, report generation, unauthenticated access rejection, cross-origin rejection,
and preventing a browser panel capability from invoking the CLI action endpoint.

Existing pairing, TLS pinning, reports, cancellation, manual-input recovery and
frame-stability tests remain in the suite. The obsolete model-tool MCP integration
now routes model operations through the shared runtime; the panel still uses a
real MCP stdio client. Unlock behavior is tested as explicit pause/resume rather
than a model-tool MCP elicitation dialog.

## Host and browser evidence

Installed Codex plugin version **0.6.0** from the local marketplace. Both actual
host clients passed real-phone navigation after the old connection owner was
released (details below). Codex used the installed skill and CLI, with MCP only
for `tapilot_preview_open`. Claude Code used a standalone copy of the skill with
`ANDROID_USE_ROOT` pointing to this package and all MCP servers disabled.

## Initial blocker and recovery

Both actual host runs returned:

```json
{"state":"occupied","messages":["Another task is already registered"],"isError":true}
```

The blocker was traced to a local **0.5.12 MCP server process** with an established
TCP connection to the phone. After the user explicitly authorized ending it,
SIGTERM was sent to that exact old process. The shared 0.6.0 runtime then obtained
capabilities and a real screenshot successfully. Pairing was not reset and ADB
was not used. A fresh embedded-panel call returned `state: live`.

Claude Code then passed the real-device workflow: screenshot and image read,
launch `com.android.settings`, inspect, tap the Bluetooth category, inspect its
page, back, inspect Settings again, and record the observed verdict. Four images
were read through Claude Code's Read tool. Only navigation actions were issued;
no settings toggle action was sent. Report ID:
`73aeddb6-8dcf-4336-97b2-f8bcbb41d796`.

Ego Browser displayed the actual 988 × 1972 phone stream and subsequent Settings
screen with advancing timestamps. This establishes real preview refresh across
CLI actions, not just the initial connection-state rendering.

Codex independently passed screenshot/image inspection, launching Settings,
tapping Connection & sharing, inspecting the destination, returning to Settings,
and recording its verdict. Report ID:
`6bc491da-f033-4150-82fb-d6b642e39402`.

Final browser manual-control verification exposed a multi-viewer frame-cache
conflict. Two regression tests reproduced the failure before the fix. Frames are
now retained per viewer; suspending one viewer preserves other viewers' frames,
while a phone mutation still invalidates all outstanding control frames. The full
77-test check passes after the fix and UI rebuild; installed `preview.mjs` matches
source. The host navigation passes preceded this preview-only fix.

After restarting with the fix, the real browser Home button navigated to the
launcher. Pause hid the image and a CLI screenshot exited 1 with `state: manual`;
explicit Resume restored the live stream. Root observation report:
`1a605aee-7cf0-43c9-809b-e7c4a49bfb06`.

The embedded MCP opener returned `state: live`; native app pixel inspection was
unavailable because computer-use access to the Codex app was denied. Browser
rendering and manual controls were visually verified. Fresh QR scanning was not
repeated on the live phone: existing pairing was reused, and pairing protocol
coverage remains in the automated suite. No ADB was used.

Final validation: `npm run check --prefix plugins/android-use` (77/77),
`git diff --check`, and standalone skill validation passed. The browser live
preview is left available for the user.

Raw local validation transcripts are in `/tmp/android-use-acceptance/` (private
directory); they are intentionally not committed because tool output can contain
local browser capability links or private phone observations.

## Additional standalone regression check

The user requested explicit standalone-phone coverage after the initial desktop
acceptance. Phone agent sources and `node-runtime/` were not changed by the CLI
migration; the two pre-existing wireless Kotlin edits are unrelated to that
migration. Phone-native execution and computer control intentionally share an
exclusive task registry, so the computer must release its connection first.

Full project checks were run. The JavaScript/type/adapter stages, 120 phone-runtime
tests, 22 script tests and model-free benchmark passed. The default shell lacked
Java; rerunning the remaining Gradle stages with the installed Homebrew JDK 17
passed: 350 Kotlin cases, 349 passed and 1 skipped, no failures; Beta lint and APK
build passed. APK verification passed with the repository Android SDK explicitly
set. This is a local-check signing certificate, not a distribution build, and the
new APK was not installed over the existing phone app.

A read-only task was entered in the real phone's Tapilot draft: open Settings,
open About device, report Android version, change no settings. CLI `disconnect`
returned success and `lsof` found no TCP connection to the phone afterward.
The user was asked to submit on the phone and report its result, because using
computer control to press Send holds the same exclusive task lock. This actual
standalone run is PENDING, not counted as passed. The native Codex panel opener
again returned live; its rendered image still awaits user confirmation because
automated access to the native host app is unavailable.

Preparation report: `77e3c124-6b8a-4151-aff5-1d3fee7b3351`.


## Autonomous phone follow-up (22:50–22:59)

The user requested no manual participation. The already-authorized USB debug
connection to PEUM00 was used solely as an external test harness to press Send
and collect screenshots/logs. Product CLI/skill/plugin paths still have no ADB
dependency. During each task, no host navigation actions or Wi-Fi controller
connection were used; the phone's own agent executed the task lifecycle.

Actual results were failures, not a standalone pass:

- Task `8cc31b71-f013-4ccd-8531-39cdf4c81c67`: failed in 33,308 ms at the
  first model request, before any Settings action. UI: ChatGPT HTTP 403.
- Phone authorization browser displayed `unsupported_country_region_territory`.
  The existing Clash MC profile was stopped. It was started without changing
  its rules, selected proxy, or account/model settings.
- Task `fd622f89-9a04-45ca-9858-20c9623ce4ac`: after the network change,
  failed in 1,596 ms, again at the first model request with HTTP 403.
- The separate connection check also returned HTTP 403. Reauthorization reached
  the normal consent page for the existing account but did not complete its
  callback after Continue. The pending login was cancelled; credentials were
  not exported, replaced, or cleared. Clash remains running on the existing
  configuration. The computer Wi-Fi controller remains disconnected.

Evidence is in the private `/tmp/android-use-acceptance/` directory:
`standalone-phone.log`, `phone-failed.png`, `phone-retry-result.png`,
`phone-auth-result.png`, and `auth-check-result.png`.

This supersedes the earlier PENDING standalone status: **tested, blocked by
phone ChatGPT access/authentication, not passed**. It does not prove a CLI
regression or guarantee full standalone compatibility. The remaining native
Codex panel pixel verification is also not claimed as passed; access to the
native host app was unavailable. Desktop client navigation, browser controls,
and automated test results above remain valid within their stated scope.


## 2026-10-25 installation correction and revalidation

The previous assumption that the installed APK certificate differed from the
local Beta was incorrect: it had compared against the repository's distribution
certificate, not the actual installed APK. The installed `base.apk` was pulled
and verified directly. Both installed and built APK certificates are
`45155f7c18ac8a6e6252b7a51d50f200d89e00c5d450f9d3b54cc08ac3aea166`.
Both phone-runtime source fingerprints are
`ff33b5a048cfd2fbd07ad1d845f5cbd388e5d044f88346a25b32538c1986bf0a`.
The local Beta built on October 24 at 22:22 was successfully installed with
`adb install -r`, preserving app data. Package Manager confirms version 0.1.7,
lastUpdateTime **2026-10-25 18:53:23**. No uninstall or credential reset occurred.

Before installation, a user-started standalone task already had successful
outer-model requests and multiple completed HTTP 200 internal-model requests.
Thus HTTP 403 was no longer reproducing before this upgrade; this upgrade cannot
be claimed as its cause of recovery. An earlier request timed out at 120 seconds,
a separate event from the previous HTTP 403. The task was allowed to leave its
running state before upgrade. A new read-only Android-version task was submitted
on the newly installed APK, with no desktop Wi-Fi controller connection.


The newly installed APK's standalone task **passed** at 18:55:00:
`f6281535-f281-48da-953c-5d83a96ce842`, `status: succeeded`, 60,319 ms.
The final phone screenshot visibly shows About device → Android version **14**;
Tapilot's completion message agrees. Model requests completed with HTTP 200 and
this task had no HTTP 403. Submission and observation used the external USB test
harness, but all Settings navigation, model requests and result evaluation were
performed by the phone itself; no desktop Wi-Fi controller was connected.

Evidence is retained locally under the ignored directory
`artifacts/controlled-execution/android-use-403-20261025/`: `result.json`,
`live.log`, `new-draft.png`, `new-task-observation.png`, and `succeeded.png`.
No phone agent code change was needed for this recovery. The prior explicit
region error was real, but the exact intervening authorization/network change
cannot be attributed from available evidence. The conclusion is recovery verified
on the newly installed APK, not a claim that installation fixed HTTP 403.


## Latest standalone voice validation (2026-10-25)

Rebuilt, reinstalled and read back the actual phone APK; hashes match. A real microphone voice arithmetic task completed on the phone with the host Wi-Fi controller disconnected. Found and corrected the model’s false claim that local spoken answers are unsupported. See [voice validation](voice-standalone-20261025.zh.md) for task IDs, audio playback evidence, exact installation hash and unresolved ASR / old-conversation status limitations.

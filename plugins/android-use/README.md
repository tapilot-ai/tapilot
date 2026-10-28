# Tapilot — wireless Android control

The npm package and primary command are `tapilot`; `android-use` remains a compatibility alias.

```sh
git clone https://github.com/quanru/tapilot.git
cd tapilot
npm ci --prefix plugins/android-use
node plugins/android-use/scripts/patch-midscene-zod.mjs plugins/android-use
npm link --prefix plugins/android-use
tapilot setup
tapilot pair
tapilot preview --open
```

Control a paired Android phone from Codex, Claude Code, or another local agent
with shell execution and image reading. Agent actions use the **Tapilot CLI**;
the MCP server provides only the embedded screen panel. Without a plugin, use the
same live screen in a local browser. All surfaces share one runtime and phone
connection. No ADB, USB, public server, or tunnel is used.

The default `steps` mode lets the host agent inspect screenshots and decide each
action; Midscene records the execution report. Optional `ai` mode retains separate
OpenAI authorization. Installing this skill does not require that authorization.

## Saved devices

The embedded panel and browser preview include a **Paired devices** list. One
computer user shares these pairings across agents. Existing single-phone pairing
files migrate automatically when first updated; update all local plugin copies
before using the new registry format.

```sh
tapilot devices
tapilot connect --device-id DEVICE_ID
tapilot disconnect --device-id DEVICE_ID
tapilot forget --device-id DEVICE_ID
```

`devices` lists saved devices without making a phone connection and never includes
credentials. `disconnect` keeps pairing and persists the user's choice across
runtime restarts. Background previews and agent observations cannot reconnect it;
use `connect` or the panel's Reconnect button explicitly. Only one saved device is
selected for control at a time. `forget` removes this computer's local credential;
it does not remotely revoke other computers or disable wireless control on the phone.
The panel asks for a second click before removal. A disconnected device is not
necessarily offline; the list does not claim availability without a connection.

## Install

Requires Node.js 22.19+, an unlocked phone running a Tapilot build with wireless
control, accessibility enabled, and a Wi-Fi network reachable from the computer.

```sh
npm ci --prefix plugins/android-use
node plugins/android-use/scripts/patch-midscene-zod.mjs plugins/android-use
node plugins/android-use/server/cli.mjs --help
```

For the Codex embedded panel, install the plugin from the repository root:

```sh
codex plugin marketplace add .
codex plugin add android-use@android-use-local
```

For standalone Claude Code or another agent, install/copy the
[`android-use-cli` skill](skills/android-use-cli/SKILL.md) using that agent's skill
installation mechanism. Set `ANDROID_USE_ROOT` to the absolute installed
`plugins/android-use` directory if the skill is copied independently. Its bundled
launcher works directly without that variable when the whole plugin is installed.
The runtime is currently distributed from this repository, not a published npm package.
The repository marketplace uses `.claude-plugin/marketplace.json`, supported by Codex;
no root `.agents` directory is required.

## Pair and operate

Download Tapilot: [Feishu APK](https://www.feishu.cn/file/Ig0KbUKlJoADozxRbOocm5iqnXf)
· [GitHub Releases](https://github.com/quanru/tapilot/releases).
Use a build supporting wireless control; `setup` returns the installation guide.

```sh
node plugins/android-use/server/cli.mjs setup
node plugins/android-use/server/cli.mjs pair
node plugins/android-use/server/cli.mjs pair-status
node plugins/android-use/server/cli.mjs preview --open
node plugins/android-use/server/cli.mjs capabilities
node plugins/android-use/server/cli.mjs screenshot
```

Open the returned pairing URL, scan its computer QR using Tapilot, and confirm the
phone in that page. In the plugin, the panel provides the same pairing flow inline.
Existing pairing remains in `~/.config/tapilot/wireless.json`; do not paste secrets
into chat. The terminal `npm run pair --prefix plugins/android-use` remains an older
phone-build fallback. Pairing a different computer replaces the previous authorization.

The CLI prints JSON, with `images[].path`, `geometry.frameId`, `report_id`, and
`report_link` for observations. Read the saved image before acting. Pass the latest
frame and reuse the report ID, for example:

```sh
node plugins/android-use/server/cli.mjs tap --x 120 --y 300 --frame-id FRAME --report-id REPORT
node plugins/android-use/server/cli.mjs record --text 'Observed expected result' --passed true --report-id REPORT
```

Replace placeholders with actual returned values. Failure exits nonzero. An action
timeout is an uncertain outcome, never an instruction to replay. `pause` blocks
capture/control for private login; `resume` requires user completion first.
`disconnect` releases the phone; `runtime-stop` shuts down the shared local service.
Neither resets pairing. Keep preview open after tasks unless the user requests release.

## Runtime and panel boundary

`server/runtime.mjs` owns the shared phone/controller/preview state. The local
service starts on demand, binds only to loopback, and stores its capability in a
private state directory. Concurrent CLI invocations share a singleton; serialized
mutations and frame checks prevent stale coordinate replay. Closing or hiding a
preview stops its polling but does not disconnect an active CLI task.

The MCP exposes only `tapilot_preview_open` to the model. Preview, manual controls,
private pause/resume, disconnect, and pairing calls are app-only. Both listing and
server dispatch reject the removed model action tools. Agent operations use CLI.

The browser gets a separate panel-only capability in the preview URL fragment,
removed from the address bar after loading. It cannot invoke the CLI action route.
HTTP requests enforce loopback Host and same-origin checks; no CORS is enabled.
Screenshots and reports stay local. Browser and embedded previews share privacy
pause and phone ownership; neither creates an independent phone connection.

## Compatibility notes for earlier releases

The sections below document the underlying transport, reports and release history.
References to `tapilot_midscene` and raw model MCP tools describe the pre-0.6 API;
those operations now run through CLI (`screenshot`, `tap`, `settings`, etc.).
The MCP resource and manual preview tools remain for the embedded panel.

## Preview latency (0.4.3)

With an updated phone app, the TLS handshake advertises JPEG preview and compact
manual-action frames. Preview images retain native dimensions at JPEG quality 75;
model screenshots remain PNG. Before a manual action, the phone still captures
and validates a fresh frame, skips image encoding, and sends only its geometry
and frame token. Older
phone builds automatically retain the PNG/full-capture path.

Preview polling does not disable controls. A click racing one pending refresh can
use the preceding displayed frame; an action consumes both tokens and is never
replayed automatically. Its response includes the next preview, avoiding another
host round trip. Live polling waits 250 ms after each completed capture.

The pre-update SM-N9860 Wi-Fi measurement found 28–61 ms action execution but
2.6–3.1 seconds per complex home-screen PNG transfer (about 5.7 MB base64 payload).
An initial three-pair home-screen Wi-Fi comparison reduced median action-to-frame
latency from 4592 ms to 2014 ms and preview transfer from 2234 ms to 257 ms.
That measurement still encoded a discarded PNG during action preparation. The
subsequent metadata-only capture build skips that encoding and passes Android
build/unit tests. Three real-device Wi-Fi pairs then measured action preparation
at 156–164 ms, execution at 31–45 ms, and action-to-frame at 1046–1162 ms.
The alternating legacy PNG runs took 9556–11903 ms in that same network session;
network throughput varied substantially from the earlier run. These direct
Wi-Fi measurements exclude ChatGPT host overhead and are not sidebar acceptance.

## Daily use

Enable wireless control on the phone when needed. The paired credential survives
app restarts. The listener binds only to the phone's Wi-Fi IPv4 address, preferring port 39877
and falling back to an OS-assigned port if occupied. Pairing and discovery publish
the actual port; pair again if a previously saved endpoint has changed. Stop from
the phone settings or the persistent notification; the listener and active
controller close immediately. Revoke pairing removes the computer's access.
The service does not restart at boot or after being killed. If the Wi-Fi address
changes, enable again and pair again to update the computer's endpoint.

Only one controller owns the phone. Local phone tools cannot run during that
session. Keep the connection and live panel open after completing a task. Use
`tapilot_disconnect` only when the user explicitly requests disconnect/release. Two minutes without a request closes
the controller connection. Observation tools may reconnect; mutation tools never
reconnect or retry automatically. If an operation times out or the connection
breaks, its outcome may be unknown: capture and inspect before another action.

The phone must remain unlocked. Secure windows may reject screenshots, and some
custom input fields do not support accessibility text input. OEM battery policy
and Doze can interrupt wireless availability. This version does not promise
always-on locked-screen automation or high-frame-rate video streaming.

## Unlock and live side panel

The phone must be unlocked. When it is locked, supported MCP hosts show an
interactive unlock form. Unlock **on the phone**, then choose `已完成继续`.
Hosts without form elicitation receive a structured `phone_locked` response
instructing the agent to use its interactive question tool and wait for the reply.
Never enter the phone passcode in chat. Only a fresh observation may be retried
once after confirmation; a blocked mutation is never automatically replayed.

`tapilot_preview_open` is the only MCP App panel opener. Automatically call it at
the start of a phone viewing/control task if no panel is open; do not require a
separate user request to display the screen. Call it once per open panel; `tapilot_screen` only returns a model screenshot. Pause/resume and captures
update the existing panel without declaring another UI entry point.
The panel polls a separate `device.preview` endpoint every 250 ms
plus capture latency, over the existing pinned Wi-Fi connection. It does not
invalidate the model's last action frame. Requires the Android APK built with
this plugin's 0.2.0 preview support; upgrade older APKs first.

Locked/disconnected/paused states clear the displayed image. The panel offers
`已完成继续` for unlock/authentication and a pause button. Before entering app
passwords or other private authentication, pause through the panel or
`tapilot_preview(action=pause)`. This also blocks model phone operations until
explicit resume. Hidden or closed panels stop polling. Task completion does not stop collection. Explicit
`tapilot_disconnect` stops collection; merely opening an existing panel does not
clear a manual or lock pause. Live frames go only to the App response, not model
observations. A supporting desktop host chooses the right-side/fullscreen layout;
clients without MCP Apps continue to receive normal screenshot tools.

Rebuild the bundled, offline UI after source edits:

```sh
npm ci --prefix plugins/android-use
node plugins/android-use/scripts/patch-midscene-zod.mjs plugins/android-use
npm run build:ui --prefix plugins/android-use
```

## Transport and credentials

Phone traffic uses TLS 1.2 with a P-256 Keystore identity. The pairing link includes
its SHA-256 certificate fingerprint and a random 256-bit bootstrap secret. The
client verifies the exact certificate **before** sending any credential. It does
not rely on a public CA or silently accept a changed certificate. Successful
pairing exchanges the one-time secret for a random device credential.

The phone stores only the credential hash in private preferences. The computer
stores its credential in `~/.config/tapilot/wireless.json` (directory mode 0700,
file mode 0600). `TAPILOT_CONFIG` overrides this path. Neither credentials nor
screenshots are logged. Screenshots returned by MCP are sent to the ChatGPT host
and model for the requested task.

The bounded, newline-delimited wire protocol is versioned separately from MCP.
It allows at most 64 KiB per request, authenticates before device access, and has
connection and action deadlines. Device actions consume a session-bound frame
(maximum age 60 seconds); coordinates are converted from the original PNG's
pixels to Android logical coordinates. Reusing a frame after an action fails.
A frame ID checks capture identity/geometry; it does not prove UI content stayed
unchanged between capture and execution.

## Verification

```sh
npm test --prefix plugins/android-use
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The plugin tests run a real MCP stdio client against a TLS fixture and check image
content, certificate pinning, authentication rejection, argument validation,
private config permissions, and no mutation retry on disconnect.

The optional `androidTest` APK contains a bounded pairing bootstrap harness. It
writes the pairing link only into the target app's private cache, never test logs.
It is not shipped in the normal APK. Instrumentation restarts the target process
and may temporarily disconnect its accessibility service; normal UI operation
must also be tested outside instrumentation. Final acceptance must include real
MCP calls against the phone over Wi-Fi and a physically unplugged USB cable.

## Interactive screen (0.3.0)

Click the preview to tap, drag to swipe, or use the navigation and scroll buttons.
Focus a field on the phone before submitting text. Text entry and Enter are separate
actions. Letterboxed margins never produce phone touches. Each manual action
requires a fresh preview frame and consumes it; rotation, stale frames, and lost
connections require a new observation. Failed actions are never replayed.

Use **Disconnect and release phone** when switching to another conversation or
controller. The phone allows one controller at a time: an active preview in another
host is reported as **occupied**, not as a Wi-Fi failure. Pause hides the preview
for private authentication; it is distinct from releasing the controller.

The transport enables TCP keepalive and disables Nagle buffering. These settings
do not prevent Android Doze, network changes, or the phone service stopping. Keep
the phone unlocked and wireless control enabled. Network errors, expired pairing,
lock, privacy pause, and controller occupancy have distinct UI messages.


## Midscene (0.4.0)

This implementation follows `../iphone-use` branch `feat/optional-midscene`, source
commit `dcbb057`. Its OAuth, Responses adapter, compact planning and native report
modules are adapted locally; there is no runtime dependency on that checkout.
The Responses adapter additionally implements the pinned SDK's `stream()` and
`finalChatCompletion()` interface, covered by an actual SDK fixture test.

Call `tapilot_midscene` with `action=settings` to read the persistent mode:

- `steps` (default): the chat host decides, the wireless device adapter executes
  explicit actions and Midscene produces local HTML reports. No model inference.
- `ai`: after user consent and separate ChatGPT sign-in, `act`, `assert`, and `wait`
  use real Midscene AI APIs. Existing steps remain available.
- `off`: use the original `tapilot_*` controls without Midscene reports.

Set a mode with `{"action":"settings","mode":"steps"}`. Mode changes do not
sign in or initiate a task. Reports/settings live in the private directory
`~/.local/share/android-use` (`ANDROID_USE_STATE_DIR` overrides it).

| Action | Required additional fields |
| --- | --- |
| screenshot | none |
| tap | x, y, frame_id |
| swipe | x, y, end_x, end_y, frame_id |
| input | text, frame_id (append, single line, no submit) |
| home / back / recentApps | none |
| launch | text (exact installed package name) |
| record | text, passed (host verdict, not an AI assertion) |
| act / assert | text; AI authorization required |
| wait | text, optional timeout_ms (1000–60000, default 15000) |

Omit `report_id` on the first call and reuse the returned ID for that task.
Use `geometry.frameId` from the latest Midscene screenshot/result for coordinate
or text actions. Other model captures and manual panel actions invalidate that
observation. Coordinates are screenshot pixels. Errors never replay actions.
Reports contain screenshots and task text; keep them local and link the returned
report. A dispatched action is not proof of its visible outcome.

For AI mode, obtain explicit consent to use ChatGPT plan inference, select `ai`,
then call `auth_status` and, if needed, `auth_login`. The user completes official
login/consent themselves; do not inspect credentials or auth pages. Check status
again after completion. `auth_cancel`, `auth_logout`, and `models` are available.
Android Use has its own registration; it never reads iPhone Use or Codex tokens.
Auth/settings do not require a paired or unlocked phone. AI planning uses the
account catalog's first visible GPT model, not the chat's current model/history.
Pass the necessary task context explicitly. No API-key or provider fallback.

AI uses at most 24 planning cycles and a 300-second act deadline, with a 330-second
worker limit; assert and wait also have deadlines. Repeated identical taps stop.
Only bounded touch/navigation/input/package-launch actions are exposed. Pausing
immediately cancels the worker, blocks subsequent actions and keeps the TLS
connection. An already dispatched gesture may finish; inspect before continuing.
The live preview remains available during AI execution, while manual controls
reject concurrent input. Finishing a task never disconnects the phone.

### Validation boundary

Local tests execute the published Midscene SDK, native HTML report generation,
wireless RPC fixtures and OAuth Responses fixtures (including false assertions).
They are not real-provider or real-phone AI acceptance. Run the repository security checks for current dependency audit results;
fixture tests do not establish provider or host acceptance.

### VPN routing (0.4.1)

The TLS client binds its source address to an unambiguous physical IPv4 interface
(prefer a matching subnet, otherwise the sole physical interface). This avoids a
VPN capturing the phone's LAN route on the tested Mac. Ambiguous interfaces retain
the operating system default; no system routes or VPN settings are modified.
Pinned certificate verification and pairing credentials remain unchanged.


### Panel lifecycle (0.4.2)

Reconnect/resume shares the pause button's toolbar position. The phone image has
no CSS border or outline shadow. Only `tapilot_preview_open` declares the screen
resource, with a fixed widget session ID. Captures and pause/resume no longer open
panels; the iframe never requests fullscreen on load. Existing host tabs are not
closed by the plugin: MCP Apps provides no host-tab closing API here. Native
ChatGPT deduplication remains a host validation item; one opener is not a promise
of global tab exclusivity across conversations or old plugin versions.

Midscene results include `report`, `report_id`, and a clickable Markdown
`report_link`, plus a `text/html` resource link to the local report. The chat host
must show that link when reporting the task outcome, including failed verdicts.
Reports stay on this computer; they are not uploaded. Reuse `report_id` across
steps of the same task so the link opens the accumulated execution history.

## Guided Midscene AI sign-in

First use continues in the default steps mode without a mandatory mode-selection
question. AI remains an explicit optional choice. For AI, the skill saves mode `ai`, checks `auth_status`, opens `auth_login` if needed,
shows the returned authorization link, waits for the user's browser confirmation,
and verifies `authorized=true` before proceeding. This is a separate OpenAI
sign-in for the local plugin; no API key or developer account is required.

Settings return `onboarding_required` for an unconfigured user, together with
`onboarding_blocking=false`. `status` returns mode, authorization guidance and
active-task state without acquiring the phone, including during an ongoing task. Missing AI consent
or authorization returns structured `code`, `next_action`, `message`, and
`host_instruction` before touching the phone. Pending login is reused; an existing
authorization is reused without opening a browser. Cancelled/failed/expired login
requires a new user choice before retrying. AI tasks are not silently downgraded
to host-directed steps. The browser sign-in and model-backed AI result still need
end-user acceptance; fixture tests only verify this routing contract.

## Discover and pair by scanning the computer

For a fresh phone, call `tapilot_midscene(action="pair")`. A temporary local
browser window shows a computer QR code and nearby `_tapilot._tcp` services.
In the phone's wireless-control settings choose **Scan computer QR to pair**, scan
that code, then confirm the phone in the computer window. This needs the updated
Tapilot phone build; older builds retain local terminal/link pairing.

The QR pins the temporary computer receiver's TLS certificate. The phone uses its
default network route, including any VPN, to submit a one-time pairing offer over
HTTPS. The VPN must allow access to the computer's LAN address; Tapilot does not
bypass VPN routing. The phone still advertises its physical Wi-Fi address for
the computer to connect back. Only explicit local
browser confirmation redeems it and saves the pinned phone credential. The window
expires after five minutes. No phone pairing secret is returned to chat or placed
in DNS-SD. The browser controls bind to loopback; only the capability-protected TLS
offer endpoint accepts LAN requests. Camera access is used only on the phone.

`tapilot_midscene(action="discover")` lists nearby phones without connecting or
authorizing them. After a paired address fails, reconnection may use discovery to
find the same pinned identity at a new IPv4 address; the TLS pin and existing
credential must still validate. Blocked multicast does not prevent QR pairing,
although Wi-Fi client isolation or a computer firewall can prevent LAN traffic.

Validation: protocol tests cover TLS certificate identity, explicit confirmation,
cross-origin rejection, one-use offers and credential-free discovery metadata.
Browser fixture rendering and native build/unit tests are separate from actual
new-phone camera, DNS-SD and browser OAuth acceptance.

The live panel stops requesting preview frames after 10 seconds in the background and refreshes immediately when visible or focused again. This does not release the phone or pause model tasks. Closing the panel stops preview polling. An in-flight frame may finish after suspension and is discarded.

## Experience updates (0.5.2)

- An unpaired panel offers a local QR pairing window directly. Recovery messages
  distinguish first setup, revoked pairing, occupied phones, locking and network
  failure. Existing pairing and authorization remain reusable.
- Manual controls accept one action at a time. Coordinates from an older screen
  are never silently attached to a later frame. Failed or uncertain input keeps
  the draft; confirmed input clears only that draft, preserving newly typed text.
- Late action responses cannot restore a hidden screen after backgrounding or
  closing the panel. Pausing still cancels AI execution and preserves pairing.
- AI status can be read while a task runs; use it instead of submitting again.
  Pending sign-in links are reused. Android execution reports identify the
  correct device type and retain the existing clickable report-link contract.

Reload the plugin host after updating to load the new server and panel resource.
Local regression tests cover these transitions; native ChatGPT/Codex panel
rendering and real-provider sign-in still require host acceptance.

## Recovery and privacy fixes (0.5.10)

- Updating a running plugin can leave the host using removed files. Missing-module
  errors now ask for a host reload instead of suggesting phone reconnection.
  Existing pairing and authorization do not need to be reset.
- Pause or disconnect takes precedence over pending preview, resume, capture and
  unlock responses. A manual action still waiting for capture is not dispatched
  after a pause; an already dispatched action is never automatically replayed.
- Backgrounded or closed panels discard late host tool results as well as late
  manual-action responses.

These paths are covered by local regression tests. Real ChatGPT/Codex host
acceptance remains pending a host reload.

## Faster step observations (0.5.11)

Screenshot-only requests no longer capture twice. After a host-directed step,
the plugin briefly observes the screen and returns a changed, stable frame when
possible, reducing extra screenshot/model round trips. `observation.state` reports
`stable`, `unchanged` or `timeout`; inspect the image to verify the actual outcome.
The observer never replays an action. `timing` includes aggregate worker and RPC
durations without screenshots, coordinates or credentials in timing fields.

Measure latency on the current device and build; historical measurements do not establish current performance.

### Small screen changes (0.5.12)

The observer also detects concentrated local changes, such as calculator digits
and small controls, rather than requiring 1% of the entire screen to change.
Initial and inter-frame waits are shorter; a changed frame still needs a second
stable observation. Unchanged screens and continuing animations retain the bounded
wait and an inconclusive result. This affects host-directed steps, not the phone's
independent AI planner. Pixel stability never replaces visual task verification.

### Verify a standalone CLI package

From the repository root, run `pnpm check:cli-package`. This builds a tarball
with locked production dependencies, installs it into an independent consumer,
audits it, runs the plugin tests and exercises portable WASM image processing.
The artifact path is in `build/cli-package-validation/latest.json`; install that
local file with `npm install -g /absolute/path/to/tapilot-0.6.1.tgz`.
The registry package is not published by this command. Plain `npm pack` from
this directory is blocked because it would omit the dependency overrides.
Binary redistribution review remains a separate release requirement.

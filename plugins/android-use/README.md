# Android Use

Control a paired Android phone from a **local** ChatGPT desktop / Codex session.
By default ChatGPT decides from screenshots and Midscene records explicit wireless
actions. Optional AI mode runs Midscene planning with separate ChatGPT authorization.
Neither mode calls the phone's model provider or needs an API key. The computer still needs internet for
ChatGPT. No ADB, USB, public server, or tunnel is used by the plugin. The live preview
runs as a bundled MCP App inside the supporting desktop host.

## Install and pair

Requires Node.js 22.19+ (Node.js 24 recommended), an Android phone running this Tapilot build, and a Wi-Fi
network that allows the computer to reach the phone. Corporate/guest networks
may isolate devices. IPv4 is supported in this first version.

From the repository root:

```sh
npm ci --prefix plugins/android-use
codex plugin marketplace add .
codex plugin add android-use@android-use-local
```

On the phone, grant Tapilot accessibility access, keep it unlocked, then open
Settings → ChatGPT wireless control → Enable wireless control → Copy pairing link.
Transfer that link privately to the computer and run:

```sh
npm run pair --prefix plugins/android-use
```

Paste the link into the local terminal prompt, **not into ChatGPT**. On macOS,
`npm run pair --prefix plugins/android-use -- --clipboard` reads the clipboard without
printing the link. The link expires after five minutes and can be redeemed once.
Pairing a new computer replaces the previous computer's authorization.

Restart/reload the local plugin host after installation. Ask ChatGPT to use
Android Use to inspect the phone. The plugin exposes status, screen, tap, swipe,
text input, navigation/Enter, app launch, and disconnect tools. One action is
followed by a fresh screenshot. It does not expose shell, arbitrary URLs, model
credentials, app-private files, or a generic internal bridge proxy.

If installing from a local plugin source is unavailable in your client, register
the same MCP server directly instead (do not install both):

```sh
codex mcp add android_use -- node /absolute/path/to/tapilot/plugins/android-use/server/index.mjs
```

Cloud-hosted ChatGPT conversations cannot use this local process automatically.
This is a desktop/local plugin, not a published cloud connector.

The phone app remains Tapilot. Existing pairing credentials and the `tapilot_*`
tool names are retained for compatibility; the plugin is named **Android Use**.

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
app restarts. Port 39877 is bound only to the phone's Wi-Fi IPv4 address. Stop from
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
They are not real-provider or real-phone AI acceptance. The added dependencies
currently report npm audit findings (js-yaml, sharp and uuid transitive paths);
no forced downgrade is applied to the pinned SDK used by iPhone Use.

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

The bundled Android Use skill guides each new user through the steps/AI choice.
For AI, it saves mode `ai`, checks `auth_status`, opens `auth_login` if needed,
shows the returned authorization link, waits for the user's browser confirmation,
and verifies `authorized=true` before proceeding. This is a separate OpenAI
sign-in for the local plugin; no API key or developer account is required.

Settings return `onboarding_required` for an unconfigured user. Missing AI consent
or authorization returns structured `code`, `next_action`, `message`, and
`host_instruction` before touching the phone. Pending login is reused; an existing
authorization is reused without opening a browser. Cancelled/failed/expired login
requires a new user choice before retrying. AI tasks are not silently downgraded
to host-directed steps. The browser sign-in and model-backed AI result still need
end-user acceptance; fixture tests only verify this routing contract.

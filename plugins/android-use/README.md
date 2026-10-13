# Android Use

Control a paired Android phone from a **local** ChatGPT desktop / Codex session.
ChatGPT sees screenshots and calls individual actions; this path does not call the
phone's model provider or need an API key. The computer still needs internet for
ChatGPT. No ADB, USB, public server, or tunnel is used by the plugin. The live preview
runs as a bundled MCP App inside the supporting desktop host.

## Install and pair

Requires Node.js 20+, an Android phone running this Tapilot build, and a Wi-Fi
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

## Daily use

Enable wireless control on the phone when needed. The paired credential survives
app restarts. Port 39877 is bound only to the phone's Wi-Fi IPv4 address. Stop from
the phone settings or the persistent notification; the listener and active
controller close immediately. Revoke pairing removes the computer's access.
The service does not restart at boot or after being killed. If the Wi-Fi address
changes, enable again and pair again to update the computer's endpoint.

Only one controller owns the phone. Local phone tools cannot run during that
session; finish with `tapilot_disconnect`. Two minutes without a request closes
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

`tapilot_screen` opens/reuses an MCP App side panel while returning an actual
screenshot to the model. `tapilot_preview(action=open)` reopens the panel.
The panel polls a separate `device.preview` endpoint about once per second
plus capture latency, over the existing pinned Wi-Fi connection. It does not
invalidate the model's last action frame. Requires the Android APK built with
this plugin's 0.2.0 preview support; upgrade older APKs first.

Locked/disconnected/paused states clear the displayed image. The panel offers
`已完成继续` for unlock/authentication and a pause button. Before entering app
passwords or other private authentication, pause through the panel or
`tapilot_preview(action=pause)`. This also blocks model phone operations until
explicit resume. Hidden or closed panels stop polling. Finishing a task with
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

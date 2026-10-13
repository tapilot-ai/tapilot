# Embedding iPhone Mirroring in a local MCP plugin

Research date: 2026-10-13. This includes a partial real-device test; a standalone plugin bridge is not implemented.

## Conclusion

A local plugin can plausibly display a captured iPhone Mirroring window and send
user input back through a macOS helper. No public Apple embedding or remote-control
SDK was found in the reviewed documentation. An MCP App is a sandboxed HTML iframe;
it cannot directly host another process's native macOS window. The proposed route
is capture and input forwarding, not reparenting the native window.

## Proposed boundary

1. The user sets up Apple's iPhone Mirroring app normally. The iPhone remains locked
   and nearby; both devices use the same Apple Account with two-factor authentication
   and have Wi-Fi and Bluetooth enabled. Unlocking the phone ends mirroring.
2. A local macOS helper selects the exact Mirroring process/window and captures only
   that window through ScreenCaptureKit. It needs Screen Recording permission.
3. A local MCP server supplies frames to an MCP App. Start with bounded snapshot
   polling as Android Use does. Higher frame rates would require a separately scoped
   streaming transport and host CSP support; MCP polling is not a video codec.
4. Panel coordinates map to the phone content rectangle, excluding native chrome.
   macOS input forwarding requires Accessibility permission. CGEvent provides event
   posting, but whether Mirroring accepts targeted background events is unverified.
5. If foreground activation is required, actions may steal focus from ChatGPT.
   This is a product limitation to measure, not something to hide behind a success
   response. Geometry changes, authentication, stale frames, and connection loss
   must stop input and require a fresh observation. Never replay uncertain actions.

## Validation gates

- Capture shows actual phone pixels rather than black/protected content.
- Tap, swipe, long press, Chinese input, Home and app switcher visibly work.
- Occluded, minimized and background window behavior is measured separately.
- Scaling, window resize, rotation, and native toolbar offsets are tested.
- App pause, Mac lock, phone unlock, idle timeout, and reconnect clear stale frames.
- Authentication remains user-driven; no credentials enter plugin logs or frames.

## Current evidence and limitations

The Mac reports macOS 27.0.1. Attempting to inspect `com.apple.ScreenContinuity`
through Computer Use returned `Computer Use was not approved to use iPhone Mirroring`.
No alternate capture or input path was used to bypass that permission boundary.
That initial attempt did not validate window capture or input. A later authorized
Computer Use attempt succeeded, as recorded below; a standalone ScreenCaptureKit
helper and background event delivery remain unverified.

Apple documents wireless mouse/keyboard interaction, a locked nearby phone, one
Mac/phone session at a time, and automatic pause after inactivity. Camera/microphone
access (including Face ID) is unavailable in Mirroring. This is a local Mac bridge,
not a general LAN endpoint on iPhone and not a cloud-only ChatGPT connector.

## Primary sources

- [Apple: iPhone Mirroring requirements and behavior](https://support.apple.com/en-us/120421)
- [Apple: ScreenCaptureKit window filters](https://developer.apple.com/documentation/screencapturekit/sccontentfilter)
- [Apple: CGEvent event posting](https://developer.apple.com/documentation/coregraphics/cgevent)
- [MCP Apps architecture and iframe boundary](https://apps.extensions.modelcontextprotocol.io/api/documents/overview.html)

These APIs support the proposed architecture; they do not prove Mirroring-specific
capture or background-input compatibility.

## Real-device follow-up, 2026-10-13 13:56–13:58 Asia/Shanghai

Computer Use accessed `com.apple.ScreenContinuity` successfully and the app
connected to iPhone 15 Pro. Window screenshots showed actual iPhone Settings
pixels rather than a black surface. Clicking the Wi-Fi page's back button
visibly returned to the Settings list. Typing `display` in Settings search
visibly inserted the text and produced the corresponding no-results view.

Chinese input did not pass: direct `typeText` with `显示` left the field unchanged,
including after selecting the existing English text. Paste opened a Universal
Clipboard transfer progress dialog and did not complete during observation; it
was cancelled. Do not interpret successful English entry as Unicode support.

Before scroll acceptance, the app changed to “iPhone 使用中” and explicitly said
mirroring had ended because the iPhone was being used, asking to lock the phone.
The attempted scroll therefore did not establish phone scrolling. The user was
asked to lock the iPhone and keep it nearby. Home/app switcher, scrolling, window
occlusion/minimization, independent capture, background input, and plugin embedding
remain pending. Computer Use screenshots/control alone do not prove a reusable
ScreenCaptureKit/CGEvent plugin implementation.

### Further observation and Midscene route

After reconnecting, Command-1 visibly returned the real iPhone to its Home Screen;
clicking the Settings icon reopened Settings. Window dimensions then changed, and
a subsequent coordinate action failed with `windowNotFoundAtPosition`. Reacquiring
window geometry is required; this is not evidence of stable background input.

The user's preferred controller is now Midscene. The inspected local
`midscene/packages/computer/src/device.ts` exposes display selection (`displayId`),
not a dedicated iPhone Mirroring window target. An initial Midscene Computer PoC
can operate the visible mirror on a desktop. A window-only plugin should add a
device adapter for capture, geometry, input, and reconnect. No Midscene-driven
iPhone action has been validated in this session; previous successes used Computer
Use. Chinese input and background focus behavior remain open issues.

## Independent native helper and background requirement

The experimental `experiments/iphone-mirror/MirrorBridge.swift` helper compiled with
Swift 6.4 and captured actual phone pixels with ScreenCaptureKit while another
application was frontmost. It selects the main Mirroring window by process and
exact title, excluding its welcome window. Capture therefore passed independently
of Computer Use. The helper is a diagnostic prototype, not an installed MCP plugin.

Foreground CGEvent input visibly opened Settings and its Battery page. The user
subsequently made background operation a hard requirement; foreground activation
was removed from the prototype and is not an acceptable fallback.

Two background input variants did not visibly navigate back from Battery:
CGEvent posting to the Mirroring PID with window-target fields, and NSEvent-derived
CGEvents with a window number posted to that PID. Captures remained on Battery;
other applications were frontmost. A successful event-post call is not proof of
phone input. Background capture passed; background interaction, ChatGPT embedding,
and Midscene-driven control have not passed. No phone screenshots containing app
usage or identifying information are stored in this repository.

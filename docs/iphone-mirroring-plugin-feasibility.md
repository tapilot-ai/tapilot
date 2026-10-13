# Embedding iPhone Mirroring in a local MCP plugin

Research date: 2026-10-13. This is a feasibility assessment, not a validated device implementation.

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
Window capture, phone connection, and input forwarding have therefore NOT been
validated on this machine.

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

# Network recovery investigation — 2026-10-29

## Reproduction and isolation

Diagnostic build added redacted OkHttp transport milestones: DNS, connect, TLS,
request upload and failure type. It records no hosts, addresses, headers, bodies,
credentials or raw exception messages. Existing request deadlines, one reconnect,
explicit task retry and proxy routing are preserved.

Batch `20261029073900-7ea29191` reproduced the original FAIL-03 failure. Before
fault injection, the phone used its configured HTTP proxy and received HTTP 200;
TLS completed in 804ms and the full response in 4.713s. After airplane/radio
restoration and Android default-network validation, both attempts timed out after
30.033s / 30.028s, at proxy TCP connection, before TLS or request headers.
Independent device-shell IPv4 and IPv6-mapped connection probes to the same proxy
also timed out. The system still advertised the proxy and its port still appeared
in LISTEN state. This is broader than a Tapilot connection-pool failure. It does
not by itself distinguish an Android/VPN routing problem from a proxy-service bug.

Using the existing Clash UI, stopped and restarted its Running service, preserving
Rule Mode and the selected MC profile. No proxy bypass, credential change, model
change or Tapilot restart was performed. The same Tapilot process then received
HTTP 200 (TLS 931ms, first model response 5.155s). Explicit retry of the same failed
task passed with real Settings/WLAN replay evidence:
`20261029074538-ce5790c4`, 1/1, 78.676s. This establishes recovery after proxy
reconnection, not autonomous repair of another app's VPN service.

## Product change and validation

The app now distinguishes pre-TLS/pre-request failures to an explicitly configured
HTTP/SOCKS proxy from generic model failures. The message tells the user to check
or reconnect the phone VPN/proxy service and retry, retaining incomplete task
state. TLS errors, HTTP authentication errors and direct connections are not
classified solely from a timeout. No phone tools or whole task are automatically
replayed, and no direct-connection fallback bypasses the user's proxy.

Full Kotlin tests: 391 total, 390 passed, 1 skipped. Lint and beta build passed.
A real closed-port HTTP proxy test verifies the specific recovery error, bounded
attempt count and absence of address leakage. Keep-alive transport tests verify
connection-stage telemetry, reuse and redaction.

Installed beta SHA-256:
`f6ccddf0d3c651b740620231cd09038b140dc5a647e946c8035a002f6ba1c8cd`.
Data/account settings were preserved.

Wi-Fi-only cycle: disabled Wi-Fi for three seconds and re-enabled it; left mobile
data and the existing proxy service unchanged. After default-network validation,
a new read-only Settings/WLAN task passed with actual replay evidence:
`20261029074802-8d273ef4`, 1/1. The first model request returned HTTP 200 in 4.634s,
TLS completed in 975ms. This does not represent a complete internet outage because
mobile data was left enabled. It is distinct from the airplane-mode failure.

Local logs: `/tmp/tapilot-network-diagnostic-e2e.log`,
`/tmp/tapilot-network-live-timing.log`, `/tmp/tapilot-proxy-restart-retry.log`,
`/tmp/tapilot-wifi-recovery.log`, `/tmp/tapilot-proxy-fix-build.log`.

On the final beta, the injected proxy-unreachable UI case passed:
`20261029074956-162dc7c9`, 1/1. The task displayed the new system-proxy recovery
message, remained incomplete and retained Retry. No automatic retry occurred.
Screenshot inspected at `/tmp/tapilot-proxy-message.png`. Its separate automatic
speech-playback failure still displayed a raw socket error banner; this change
covers model/task error classification, not speech transport UI. The fault fixture
restored airplane off and Wi-Fi on. The existing Clash service was then reconnected
again, preserving the same profile/mode, for final task recovery verification.

The original full-offline automatic-recovery requirement remains unproven when
the external VPN proxy is left unusable. This patch does not restart third-party
VPN services, bypass the configured proxy, or claim to fix the upstream service.

Final recovery on the same installed f6ccddf0 beta passed:
`20261029075305-39bd5c5d`, 1/1, 70.573s. The failed task was explicitly retried
after proxy reconnection, completed, and its Player screenshot supported actual
Settings/WLAN. No Tapilot restart or account/model change was needed. Device APK
SHA-256 readback matched the local artifact exactly. Focused transport tests were
rerun successfully after the final test-name cleanup. Phone proxy service is left
Running with its original profile/mode; airplane mode is off and Wi-Fi enabled.

## Afternoon plugin acceptance continuation

Plugin UI build consistency and all 94 plugin tests passed. Installed plugin
screen.html SHA-256 matched the source file exactly:
`2b94974a290e0915ffeb6be4193d9c44c9b969149cd29fad125cc3264701c4c3`.
This is artifact consistency, not rendered UI acceptance. The browser inventory
still returned “The browser access host is unavailable”; the previously forbidden
Codex native app was not accessed through an alternative path.

The first wireless connection attempt failed. A wired setup inspection showed
Tapilot accessibility Connected and wireless control Off. After enabling the
existing wireless-control service through its visible UI, the installed plugin
CLI connected with the saved pairing, without QR scanning. All following acceptance
operations used Wi-Fi CLI: fresh capture, package discovery, launch Android Settings,
inspect the resulting screenshot with Wi-Fi/mobile network rows, and record pass.
Report id: `2bd55745-4cca-411c-b8f6-a7724ccb3434`. This proves real wireless action
and report generation on the latest phone beta, not embedded-panel rendering.

Secure-lock recovery remains pending the user's availability to unlock physically.
The phone was not locked, credentials were not requested, and neither mocked
recovery tests nor this unlocked wireless task count as secure-lock acceptance.

### Real secure-lock recovery — completed with user unlock

On the same installed phone beta and existing pairing, exercised the installed
plugin's `tapilot_preview_frame` API with one viewer id. At 08:27:05.944 UTC the
baseline was live with an image. The wired test harness sent sleep/wake key events;
Android trust state confirmed `deviceLocked=1`. At 08:27:14.613 UTC the frame API
returned `device_locked` with no image. No passcode was requested or entered.

After the user physically unlocked and confirmed completion, the very next frame
request at 08:36:39.006 UTC returned `live`, a new frame id and a new Settings
image (visually inspected). No resume, connect, refresh, pairing or task mutation
occurred between the locked and recovered probes. The long interval reflects
waiting for user confirmation, not recovery latency. This passes real-device
secure-lock recovery at the panel transport API layer; actual Codex window
rendering and its polling latency remain unverified due to the host restrictions.

Local evidence: `build/acceptance-lock-recovery/events.jsonl`, `before.jpg`,
`unlocked.jpg`, and the probe source in that directory. The existing Midscene
report `2bd55745-4cca-411c-b8f6-a7724ccb3434` includes the observed verdict and a
fresh post-unlock capture. Connection remains available; phone is unlocked.

## Physically unplugged continuous voice — real failure and repair

The user unplugged USB; `adb devices` listed no devices. After opening a fresh
voice conversation, disconnected the Wi-Fi controller while retaining pairing.
Played three Chinese utterances through the Mac speaker into the phone microphone,
with 18 seconds between utterances: remember 73; subtract three from that remembered
number; open Calculator and multiply the previous answer by two, checking its display.
No phone text injection, screenshots or controller actions occurred during these
turns. Mac speaker output was restored afterward. The phone rejected observer
reconnection while its native task ran; no task was stopped to obtain a screenshot.

After completion, reconnected with the existing pairing and visually inspected both
Calculator and the conversation. The first two replies were “记住了！” and “70”,
each marked completed. Calculator displayed `70×2` and `140`. The third task was
nevertheless marked incomplete: its verifier lacked the prior assistant reply and
reported that the previously answered number was absent. This proves continuous
acoustic input and actual cross-turn execution, but fails overall task acceptance.
Voice mode was ended after readback. Report:
`1f36197b-92fb-4509-a00b-439a1554877d` (recorded failed).

The repair carries the exact previous assistant reply as bounded reference data,
separate from authoritative user requirements and model-authored step proposals.
It cannot prove earlier phone state or override the current user request. Oversized
replies are omitted whole. An unresolved reference must remain unknown, rather than
being guessed from a plausible screenshot. Regression checks preserve negative
screen judgments and reject model-invented reference targets.

Local evidence is in `build/acceptance-standalone-voice/`. The first frozen-image
model replay passed the supplied-70 and wrong-71 cases, but incorrectly accepted
the missing-reference case. That failed experiment is retained in `replay-1/`;
it prompted the explicit missing-reference rule. Frozen-image checks are host-side
component evidence only. Installation and another unplugged acoustic run remain
required before claiming the native fix passes.

The final frozen-image replay (`replay-2/`, existing authorized account,
`gpt-6-luna`, current Midscene runtime) passed all six judgments: two each for
the actual prior reply 70, a conflicting prior reply 71, and missing reference
context. The original Calculator screenshot bytes were reused unchanged. The
129 runtime tests passed; Android unit tests reported 391 total, zero failures,
one skipped. These results do not substitute for reinstalling and repeating the
physical voice test. No phone account or model setting was changed.

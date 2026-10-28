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

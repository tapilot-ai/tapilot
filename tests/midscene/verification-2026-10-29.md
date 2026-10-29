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

### Installed repair and unplugged voice reruns

Installed the repaired beta over USB and compared the installed `base.apk` SHA-256
with the build: both were
`7314589d962941c9be75575e36418bd312f42933d0a30a09b5e3be6413d9d152`.
Wireless control was Off after replacement; enabled it through the visible settings.
The saved pairing reconnected without scanning. After the user unplugged again,
`adb devices` was empty for both following acoustic runs. Each began in a fresh
conversation with voice Ready, then the Wi-Fi controller was disconnected before
playback and remained disconnected throughout all three turns.

The first rerun retained the original 18-second gaps. The second utterance lost
its prefix, including the subtraction request, and its transcript contained only
“回答数字不要超过手机”. The assistant answered 73 and subsequently calculated
`73×2=146`, with the task marked completed. This establishes reference propagation
but FAILS the intended three-utterance acceptance. Missing audio at the transition
is not fixed by the verifier change. Reading `VoiceConversation.read` confirms
listening resumes after playback completion; the exact acoustic loss timing was
not instrumented, so overlap remains an explanation rather than proven causality.

The second rerun used the same utterances with 25-second gaps. Visually inspected
the transcript: first reply “记住了” (4s), second question included “减去三” and
reply 70 (6s), third request referenced the prior answer. Calculator displayed
`70×2` and 140; the third task was marked completed (54s, one operation). The
screen was in editable-expression/preview-result state, so this is evidence of
visible calculation and cross-turn checking, not pressing equals or committing
the expression. The original missing-prior-reply rejection did not recur.

This passes the paced, unplugged three-turn voice/reference scenario, not reliable
barge-in or arbitrary inter-utterance timing. Both attempts are preserved, rather
than replacing the unsuccessful run with the successful one. No phone text was
injected; speech came from the Mac speaker into the phone mic. No phone control or
captures occurred during either sequence. Observer reconnection during the first
native task was refused; the task was not interrupted. Voice was ended after
readback, and Mac audio restored to volume 17 / muted. Report:
`76de35be-8ea3-4c92-a83c-a1313d24aeb8`. Local acoustic logs:
`build/acceptance-standalone-voice/rerun-acoustic.log` and `rerun-paced.log`.

### Follow-up turn readiness repair — local checks complete, device pending

Code inspection found that the Volcengine playback reader accepted terminal
code `20000000` but continued reading until HTTP EOF. The provider documents this
code as successful synthesis completion:
https://docs.volcengine.com/docs/doubao-voice/http-chunked-sse-unidirectional-streaming-v3?lang=zh
The reader now stops on that event, preserves any final audio payload, and leaves
the existing AudioTrack drain in place before reopening the microphone. Early EOF
without a terminal event fails instead of silently treating truncated speech as
complete. This removes a concrete possible wait, but does not prove it caused the
earlier lost prefix; no timing trace was captured during that run.

A short local readiness cue now follows successful microphone start, respecting
mute and suppressing the cue during a held floating-input gesture. Duplicate,
failed and stale microphone callbacks do not emit it. Speaking UI explicitly asks
users to wait for Ready or tap Speak to interrupt. Phase-transition logs contain
only phase names and elapsed milliseconds, with no transcript or audio samples.
This change does not implement full-duplex voice interruption.

Regression tests include a response source that throws if read after its terminal
event (simulating a still-open HTTP stream), truncated/failed synthesis, null final
audio, cancellation, and readiness callbacks across playback. Android tests:
396 total, 395 passed, one skipped; Lint and beta build passed. APK local verification
passed. Log: `build/acceptance-standalone-voice/turn-ready-build.log`. Installation,
audible readiness validation and another unplugged voice run remain pending USB
reconnection. No new real-device success is claimed for this repair.

### Readiness repair installed; original 18-second-gap scenario passed

After USB reconnection, installed the readiness repair and confirmed installed
`base.apk` matches build SHA-256
`fe61dce92f18a09b17defa940d134d1e60ccd48e9dbc47060973cb887f37496f`.
Enabled wireless control after package replacement; existing pairing worked.
The user unplugged again and ADB listed no devices. Opened a fresh conversation,
confirmed Ready, and disconnected the wireless controller before acoustic playback.

Reused the original three utterances and 18-second gaps, rather than the easier
25-second pacing. Visually inspected after native execution: remembered 73 (12s);
the second transcript retained the full subtraction question and the assistant
answered 70 (3s); Calculator showed committed `70×2=140`, and the third task was
completed (51s, one operation). Ready appeared again after the task. No phone input
or captures occurred during the sequence; voice was ended only after readback.
The nonessential “不要操作手机” phrase was still transcribed as “不要超过手机”
in turn two, so this is not word-perfect ASR acceptance. No arithmetic prefix was
lost. Mac output restored to volume 17/muted; saved pairing remains connected.

The 18-second arithmetic/reference scenario passes on the installed repair. One
pass does not establish arbitrary timing or full-duplex reliability. The readiness
beep was not independently recorded/listened to, and phase logs were not retrieved
while USB remained unplugged. The cause of the earlier lost prefix is therefore
not conclusively established. Acoustic log:
`build/acceptance-standalone-voice/readiness-18s.log`.
Report: `76de35be-8ea3-4c92-a83c-a1313d24aeb8`.

### Pre-publication audit — repository remains private

Audited source baseline `157ed2b6ba9244bb59fe7a0feef6056fe48b5454`.
GitHub reported `isPrivate: true`; no visibility, release or package publication
change was made. This entry records preparation, not publication approval.

- Gitleaks 8.30.1 with `git . --log-opts=--all --redact` completed successfully:
  234 commits and 13,262,217 bytes scanned, no findings. A separate `dir` scan of
  a snapshot containing only `git ls-files` completed successfully:
  41,467,488 bytes scanned, no findings. These are detector results, not a
  guarantee that every type of sensitive content is absent. An earlier broad
  worktree scan was interrupted and is not counted as a successful check.
- `pnpm check:security` passed for the root, runtime bundle, plugin and Midscene
  test dependency trees. This is dependency vulnerability checking, separate
  from the secret scans.
- `pnpm release:materials` verified and assembled all 132 checksum-pinned
  materials. It intentionally retained `redistributionApproved: false`.
- `pnpm check:distribution` remains blocked by four entries: Sharp WASM source
  and rebuild provenance, proxy-agent-negotiate and secure-compare notices, and
  the overall native corresponding-source/rebuild review. See
  `third-party/README.md` for pinned upstream evidence. Passing material
  collection does not resolve these entries.

The two final promotional MP4s were sampled every four seconds and selected
WeChat/plugin frames inspected at full resolution. They contain visible
WeChat photo avatars and a local network address in the plugin interface. The
inspection does not establish the identity of anyone pictured.
Before public promotion, redact those details or explicitly approve their
disclosure. This was a sampled visual inspection, not exhaustive frame/audio
inspection or verification of music/voice asset rights. The original final
videos were left unchanged; extracted review images are ignored build artifacts.

Local scan logs, redacted JSON findings and sampled frames are under
`build/open-source-review/`; assembled materials are under
`build/distribution-materials/`. These generated files are not committed.
Existing acceptance limitations still apply: actual Codex embedded GUI acceptance
is outstanding, and GitHub CI could not run due to the account billing restriction.


### Publication follow-up — remove an unresolved runtime dependency

The Android bundle now patches `http-server@14.1.1` to use Node's
`crypto.timingSafeEqual` with UTF-16 buffers, and a scoped pnpm override removes
`secure-compare`. Its copyright/license gap is resolved by excluding its code
from this bundle, not by asserting new license terms. Desktop dependency trees
are unchanged. The replacement retains HTTP Basic authentication.

Validation on Node 24.21.0: all 130 runtime tests passed, including real HTTP
requests with valid Unicode credentials and rejected incorrect/missing
credentials. Both runtime smoke tests passed (the Android bridge is simulated,
not a fresh real-device acceptance). The deployment notice inventory contains
281 npm packages with two requiring review; `secure-compare` is absent.
`pnpm check:quick` and all four `pnpm check:security` audits passed. Beta assembly
and `verify-apk.mjs beta --local-check` passed with JDK 17. No APK was installed
or published in this follow-up. Logs are in `build/open-source-review/`.

`check:distribution` now reports three blockers: proxy-agent-negotiate's notice,
Sharp WASM provenance, and overall native corresponding-source/rebuild review.
These are still unresolved. This change does not claim the remaining upstream
material or Codex embedded GUI acceptance is complete. Videos are unchanged
pending the user's preference on the two photo avatars around 32 seconds.
Repository visibility remains private.

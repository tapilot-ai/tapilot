# Wireless control implementation evidence

Baseline: `e5dd7bf`. Implementation lives on `feat/wifi-mcp` in an isolated worktree
so concurrent ChatGPT account integration changes in the original checkout are
not overwritten. This record distinguishes protocol tests from physical-phone
acceptance. Cable-free control through the installed MCP artifact passed; native
ChatGPT tool discovery remains a separate host integration check.

## Automated checks

- `:app:testDebugUnitTest`: 172 tests, 171 passed, 1 pre-existing optional live
  speech-provider test skipped, no failures/errors.
- `:app:assembleDebug`: passed.
- `:app:assembleDebugAndroidTest`: passed, including pairing-fixture cleanup.
- `npm test --prefix plugins/tapilot`: six tests passed. These exercise a real
  stdio MCP SDK client and a TLS fixture, including screenshot image content,
  pin mismatch (no credential sent), bad credentials, disconnected mutations not
  retried, schema validation, and mode-0600 credential storage.
- `git diff --check`: passed.

## Physical device findings

Device: Samsung SM-N9860, Android 13. Debug APK installed with `adb install -r`;
no app-data reset and no ADB TCP mode or port forwarding was used.

- Earlier network probe: computer reached a temporary phone TCP listener through
  the phone's Wi-Fi interface and received an exact nonce echo. This was a shell
  process and is only network reachability evidence.
- Actual Tapilot TLS server: certificate-pinned one-time pairing succeeded over
  Wi-Fi. The permanent credential was saved privately on the computer and was
  not printed into logs or the conversation.
- Initial RSA Keystore identity failed on the Samsung Conscrypt path with
  `Incompatible padding mode` / `RSA/ECB/NoPadding`. The implementation uses a
  P-256 ECDSA Keystore identity instead, with `DIGEST_NONE` authorized because
  Conscrypt supplies an already-hashed TLS transcript. The ECDSA pairing was
  successful; TLS and fingerprint validation were retained.
- MCP status reached the phone and correctly reported that its accessibility
  service was disconnected. Instrumentation restarts the app process and can
  leave that service enabled but unbound on this device.
- Follow-up physical acceptance succeeded on October 13, 2026, 11:59–12:05
  Asia/Shanghai. The harness ran the **installed plugin cache artifact**, using
  the official MCP SDK client over stdio. The plugin connected directly to the
  phone's Wi-Fi TLS endpoint. No ADB input or screenshots were used for the
  functional actions below (ADB was used only for installation/setup).
- Verified by inspecting actual MCP PNG image responses:
  - Launch Android Settings and open its search field with a screenshot-based tap.
  - Enter Chinese `电池`; the screen shows the query and eight search results.
  - Swipe the Settings list; the visible entries change from Connection / Connected
    devices to Display / Wallpaper / Location services.
  - Tap Location services near the bottom of the screen; its detail page opens.
    No setting toggle was changed.
  - Explicitly disconnect, reconnect with the saved credential, press Back, and
    capture the returned Settings list.
- A second installed-plugin process was rejected with `Another task is already
  registered`. The original connection continued working. Reusing an old frame
  was rejected with `Stale frame. Capture a new screen.`
- Screenshots in the final run took 368–560 ms, taps 176–228 ms, Chinese input
  129 ms, and a 600 ms swipe took 771 ms. These are observed samples, not a
  latency guarantee.
- Capture metadata initially reported 1080×2210 logical size for a 1080×2316 PNG.
  The existing inverse gesture scaling compensated on the tested wireless path,
  but the metadata itself excluded system bars. Capture, frame validation,
  device size, and native gesture scaling now share full-display logical metrics.
  The final device reports 1080×2316 consistently, with successful top/bottom
  taps, swipe, Chinese input, and reconnect on the rebuilt APK. Android's
  [Display API](https://developer.android.com/reference/android/view/Display#getRealMetrics(android.util.DisplayMetrics))
  documents the full-display metrics used here.
- Final installed APK SHA-256:
  `2cb26c6f9827410bfe588e4b2247f1dccf03376a3075307fb44c8341f3c654d3`.
- **Physical USB removal passed**, October 13, 2026, 12:12–12:14 Asia/Shanghai:
  the user unplugged the phone and `adb devices -l` no longer listed Samsung
  serial `R5CN90V1AYP` (only an unrelated device and emulator remained).
  A new installed-plugin process authenticated using the existing pairing over
  Wi-Fi, then returned a fresh 1080×2316 screen.
- Without USB, changed the focused search field to Chinese `显示` (126 ms).
  The next screenshot shows `显示`, four popular results, and 90 search results.
  Tapped the visible screen-mode result (255 ms), and a screenshot confirmed
  the Display settings page opened. Swiped its list (765 ms), explicitly
  disconnected, then reconnected through `tapilot_screen` (727 ms including
  connection). The returned screenshot shows the lower Display settings list,
  including resolution, timeout, navigation bar, and accidental-touch protection.
  No setting toggles were changed. USB inventory was checked again at cleanup.
- Cable-free screenshots took 538–567 ms on this run. Testing used the actual
  installed stdio MCP plugin with an SDK host, not ChatGPT's native tool picker.

![Cable-free Chinese input](unplugged-chinese-search.png)
![Cable-free swipe and reconnect](unplugged-swipe-reconnect.png)

![Settings list after MCP swipe](settings-after-swipe.png)
![Chinese search result from MCP screenshot](chinese-search-result.png)

## Installed desktop artifact

The local `tapilot-local` marketplace and `tapilot@tapilot-local` plugin were
registered. CLI inventory reports installed=true, enabled=true, version=0.1.0.
An already-open ChatGPT/Codex conversation may need a new session or host reload
before its tool inventory includes this plugin. Installation does not prove
that this conversation has invoked the plugin through its native tool picker.

## Remaining acceptance

1. Reload the desktop host/open a fresh conversation and verify native plugin
   tool discovery. This run proves the installed stdio MCP artifact against a
   real phone, not invocation through ChatGPT's native tool picker.
2. Verify local-task exclusion, notification stop, and revocation on the device.
3. Verify Android 14+ connected-device service behavior and idle/network-change
   behavior on additional OS/device combinations. No always-on Doze guarantee.

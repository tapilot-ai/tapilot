# Wireless control implementation evidence

Baseline: `e5dd7bf`. Implementation lives on `feat/wifi-mcp` in an isolated worktree
so concurrent ChatGPT account integration changes in the original checkout are
not overwritten. This record distinguishes protocol tests from physical-phone
acceptance; it is not a claim of complete cable-free end-to-end validation.

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
- Full actions/screenshot acceptance remains pending. Device UI repeatedly
  switched to a localhost login browser page during setup; confirmation of
  exclusive use was requested before proceeding with more device interaction.
- The USB cable was still attached for setup. **Physical cable removal, complete
  screenshot/tap/Chinese input/swipe verification, and reconnect verification
  have not yet passed.**

## Installed desktop artifact

The local `tapilot-local` marketplace and `tapilot@tapilot-local` plugin were
registered. CLI inventory reports installed=true, enabled=true, version=0.1.0.
An already-open ChatGPT/Codex conversation may need a new session or host reload
before its tool inventory includes this plugin. Installation does not prove
that this conversation has invoked the plugin through its native tool picker.

## Remaining acceptance

1. Reserve the phone for this test and restore its normal accessibility binding.
2. Install the final APK and enable wireless mode through the normal settings UI.
3. Run the installed plugin's MCP tools against the real phone: capabilities,
   screenshot, app launch, screenshot-based tap, Chinese input, swipe, and visible
   result verification. Do not submit messages or modify accounts as a test.
4. Remove USB physically and repeat a visible action and screenshot.
5. Verify second-controller exclusion, stale-frame rejection, local-task
   exclusion, explicit disconnect/reconnect, notification stop, and revocation.
6. Verify Android 14+ connected-device service behavior and idle/network-change
   behavior on additional OS/device combinations. No always-on Doze guarantee.

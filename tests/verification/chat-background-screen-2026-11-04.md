# Chat-driven background-screen setup — 2026-11-04

Implementation: `feat/chat-background-screen-setup-20261104`, based on
`origin/main` at `59e3848`. This is an uncommitted local implementation and has
not been published. See [behavior and boundaries](../../docs/experiments/chat-background-screen-setup.md).

## Final build checks

- Node runtime: 144 tests passed; runtime bundle rebuilt from current source.
- JVM: 453 tests, 452 passed, one existing skipped, no failures or errors.
- `assembleDebug`, `assembleBeta`, and `lintBeta` passed with JDK 17.
- `verify-apk.mjs beta --local-check` passed: expected package, non-debuggable,
  current runtime. The certificate is for local verification, not distribution.
- Debug APK SHA-256: `1023ada263497f6955d58ec1da4b70316d1c38ae3073eabb60559e24c3778681`.
- Beta APK SHA-256: `5255e11063db59303a14a03001bd414c8eca0a000a08f1f81db4a1749c661493`.

## Final model-backed emulator checks

Device: isolated Android 15 / API 35 arm64 emulator, `emulator-5560`.
No physical phone was modified.

| Check | Observed result |
| --- | --- |
| Explicit setup request with existing authorization | Passed in 3,978 ms; live shell connected, overlay allowed, background enabled |
| Ordinary request to open Settings → Apps on the background screen | Passed in 61,921 ms, one phone operation, zero failed operations; screenshot verification described the actual Apps page |
| Display ownership during the ordinary task | Display ID 2, `virtual:com.android.shell,2000,Tapilot authorized shell display,0`, 1080 × 2400 |
| Primary screen during the ordinary task | Tapilot chat remained visible with background-operation progress |

Local evidence (ignored artifacts, retained in this worktree):

- [Final setup summary](../../artifacts/background-screen-setup/final-setup-passed/summary.json).
- [Ordinary background task summary](../../artifacts/background-screen-setup/background-task-passed/summary.json), with native and agent HTML reports alongside it.
- [Active display dump](../../artifacts/background-screen-setup/active-display.txt).
- [Primary-screen capture](../../artifacts/background-screen-setup/active-background.png).

An earlier candidate let the model route an ordinary background app task into
setup. Instrumentation rejected its missing phone report. The final build grants
the setup capability only for an explicit configuration intent, checks that
authorization again in the native handler, and passed both checks above.

## Staged first-setup evidence and limits

Earlier candidates were exercised through user consent, model-driven Developer
options/debugging navigation, system confirmation, phone-side notification input
for wireless-ADB pairing, and the overlay permission page. Pairing produced a
live shell helper and a paired Tapilot entry. Cancellation and an overlay-step
timeout preserved existing consent/pairing. A tester backed out of the overlay
page accidentally; permission was granted separately before the final readiness
and ordinary-task checks.

At this initial checkpoint there was no uninterrupted fresh-first-setup pass.
The follow-up UI acceptance below closes that emulator gap. Physical-device
and OEM-specific first-setup acceptance remain pending.

The emulator had DHCP/DNS failures. Its Wi-Fi was configured with a static
address, and real model requests used a temporary host forwarding proxy through
an emulator-scoped ADB reverse. This was a transport workaround, not a mock
model. ADB was also used for installation, instrumentation, accessibility test
bootstrap, and screenshot-guided simulation of user confirmations. Host
`adb pair` and host shell-helper bootstrap were not used; pairing used the
application's existing local-ADB flow. Pairing-code screenshots and credentials
are not included in committed evidence.

## Follow-up: actual UI entry acceptance

The unchanged final debug APK (SHA-256 above) was installed on a new dedicated
AVD, `Tapilot_Setup_UI_20261104`, also Android 15 / arm64. The test harness gained
`manual_entry=1`: it configures the real model, then waits for a task submitted
through the product UI. It does not dispatch that task itself. Optional `task`
text is copied to the Android clipboard for manual UI paste. Model credentials
are restored at teardown. The Chinese and ordinary-task runs also capture the
final UI before instrumentation terminates the target process.

The first attempt used a display-size override and navigation drifted; it was
aborted. Display size/density were restored to their defaults and app data was
cleared before the accepted first-setup run. No production code was changed
between these attempts.

- **Settings button, first setup: passed.** Navigated from Settings to Background
  screen and tapped **Set up automatically**. The conversation displayed the
  generated request and started it. The same task continued through shell
  consent, notification permission, model-driven Build number taps, Developer
  options and Wireless debugging navigation, user confirmation of wireless
  debugging, notification RemoteInput pairing, and user overlay permission.
  Native readiness was true for shell connection, overlay and background chat.
  There was no stop/restart of this accepted setup task. USB debugging was
  already enabled by the emulator, so its off-to-on path was not exercised here.
- **English conversation synonym: passed.** Typed and sent `Configure the virtual
  screen` in the actual composer. The real model called setup and native
  readiness passed using the existing pairing.
- **Chinese conversation synonym: passed.** Pasted `把虚拟屏打开` through Gboard's
  clipboard suggestion, then tapped Send. The real model called setup and native
  readiness passed. The final in-app screenshot shows that exact request and
  the completed response.
- **Ordinary task after first setup: passed.** Typed and sent a request to open
  Android Settings on the background screen and verify Apps. Actual Apps-page
  screenshot verification passed, with one phone operation and zero failed
  operations. The live display dump showed display ID 2 owned by shell UID 2000;
  the primary screen remained Tapilot chat. The in-app task duration was 1m 13s.

Evidence:

- [Button → conversation](../../artifacts/background-screen-setup/ui-entry/06-default-display-chat.png),
  [consent wait](../../artifacts/background-screen-setup/ui-entry/07-default-display-consent.png),
  [system confirmation](../../artifacts/background-screen-setup/ui-entry/09-wireless-system-confirmation.png),
  [pairing entry](../../artifacts/background-screen-setup/ui-entry/10-pairing-entry.png),
  [overlay wait](../../artifacts/background-screen-setup/ui-entry/11-waiting-overlay.png).
- [Uninterrupted button-run summary](../../artifacts/background-screen-setup/ui-button-fresh-passed/summary.json).
  Its 622,611 ms wall time includes manually browsing to the setup button before
  submitting; this is not an automation latency measurement.
- [English synonym summary](../../artifacts/background-screen-setup/ui-synonym-passed/summary.json)
  and [composer screenshot](../../artifacts/background-screen-setup/ui-entry/14-synonym-composer.png).
- [Chinese synonym summary](../../artifacts/background-screen-setup/ui-chinese-passed/summary.json)
  and [final in-app screenshot](../../artifacts/background-screen-setup/ui-chinese-passed/0-final.png).
- [Exact inputs and stored native outcomes](../../artifacts/background-screen-setup/ui-entry/task-inputs.json).
- [Ordinary task summary and HTML reports](../../artifacts/background-screen-setup/ui-background-task-passed/summary.json),
  [final in-app result](../../artifacts/background-screen-setup/ui-background-task-passed/0-final.png),
  [live display dump](../../artifacts/background-screen-setup/ui-entry/active-display.txt),
  and [primary screen during execution](../../artifacts/background-screen-setup/ui-entry/19-background-execution.png).

For this new AVD, normal DHCP Wi-Fi remained connected; no static address or
setup-wizard modification was needed. Real model traffic still used the
temporary host proxy / emulator-scoped ADB reverse. ADB accessibility rebind was
test bootstrap; shell consent, notification permission, system confirmation,
pairing-code entry and overlay permission were performed through visible UI.
Host ADB pairing and helper bootstrap were not used.

# Background-screen setup review fixes — 2026-11-04

Branch: `feat/chat-background-screen-setup-20261104`, based on `59e3848`.
All three findings from the serial review were fixed locally. No commit, push
or release was performed.

## Changes

1. Task history now owns a typed setup purpose. Actual typed continuation,
   localized Continue task actions and retry retain setup authorization. New
   ordinary requests cannot acquire it from a previous setup goal. Explicit
   fresh setup requests start separately from an ordinary waiting task.
   JVM and Pi reject answer-only completion of a setup attempt. Pi distinguishes
   prior phone work, setup and planning, so a saved setup plan does not prevent
   setup itself from resuming.
2. `BackgroundScreenSetupProgress` owns stage entry, waiting, timeout and
   revocation decisions. The session-wide attempted set was removed. Re-entry
   restores navigation; every pairing-stage entry verifies the pairing page.
3. The settings button observes coordinator availability and explains its busy
   state. The coordinator returns whether setup was accepted; a rejected request
   preserves the current task and the settings page.

The acceptance harness now requires an actual native setup RPC rather than only
checking that a previously configured device remains ready.

## Verification

- Extracted the pre-fix Pi runtime from the previously built Beta APK. Three new
  regression tests failed against it: resumed setup accepted answer-only
  completion, resumed setup skipped native verification, and a fresh setup
  request accepted answer-only completion. This was a controlled model-RPC test,
  not a claim of hardware reproduction.
- Rebuilt runtime: **148 tests passed**, zero failures. Includes those three
  regressions and resumption after a saved setup plan.
- JVM: **461 tests, 460 passed, one skipped**, zero failures/errors. New tests
  cover real task-history continuation selection, localized button-style
  requests, retry/handoff, native completion rejection, state re-entry,
  deadlines and revocation.
- Debug APK, Beta APK, test APK and `lintBeta` built successfully.
- Beta local verification passed: expected package, non-debuggable and current
  runtime. The local verification certificate is not a distribution approval.
- `git diff --check` passed.

## Android UI check

Dedicated Android 15 arm64 emulator `emulator-5560`, AVD
`Tapilot_Review_Fixes_20261104`; the connected physical phone was not operated.
The final APK used a real configured model through a temporary host proxy / ADB
reverse, with test-only accessibility and notification-permission bootstrap.

A real ordinary Settings task ran while instrumentation checked that setup was
rejected, no replacement task was created, and the actual setup button was
disabled with an explanation. After completion the actual button was enabled.
Native admission and UI button-state assertions passed.

The Settings task initially observed an empty loading page and failed its first
screen verification, then recovered and verified the actual main Settings page.
Its status succeeded with **one failed intermediate operation**. This is not a
zero-retry ordinary-task result.

- [Native summary and reports](../../artifacts/background-screen-setup/review-fixes/native/summary.json)
- [Busy button](../../artifacts/background-screen-setup/review-fixes/native/0-busy-entry.png)
- [Idle button](../../artifacts/background-screen-setup/review-fixes/native/idle-entry.png)

The full first-pairing flow was exercised before these review fixes, as recorded
in the earlier acceptance ledger. It was not rerun end-to-end on this final APK.
Wi-Fi re-entry is covered by the actual pure transition policy tests, not a
physical network-interruption replay. Physical/OEM acceptance remains pending.

## Final local APK hashes

- Debug SHA-256: `08f5fc7154dc12d7af7841ffe3f50f882583ce9b8bac573936664e573ea9b075`.
- Beta SHA-256: `1c5a8dfe8573b2e9c85836dbef220c699032a49a534ff4ecee2e8762ed2cb8e2`.

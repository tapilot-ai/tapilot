# Background chat acceptance — 2026-11-04

## Scope and environment

Physical OPPO Find N (PEUM00), Android 14, serial `e7ed3f17`, folded outer display 988 × 1972 at 440 dpi, font scale 1.0. Isolated Debug package `lifeos.md.tapilot.e2everify`; existing model authorization, `gpt-6.1-terra`. No app data was cleared and no distribution APK was published.

The phone ran the real conversation agent and Midscene phone operations, including final visual verification. Host screenshot-guided ADB actions submitted requests and tested manual takeover; they did not replace model actions in the unattended calculator case. Device operations were serialized through the device lock. Screenshots remain local because the artifact directory also contains account information; do not upload the directory or raw model reports.

## Regression and fixes

The initial chat integration reused the fixture geometry (480 × 800 at 160 dpi). OPPO Calculator rendered clipped key labels. The physical display was 988 × 1972 at 440 dpi. Conversation displays now preserve the phone's resolution, aspect ratio and density within a 2560-pixel edge / 4,194,304-pixel area budget. The preview scales pixels rather than changing app layout. Fixture geometry remains unchanged.

A density-only candidate fixed rendering, but the model refused to certify screen isolation from app pixels. A second attempt in the same conversation retained that constraint and also failed. These are failures, not successful retries. The host now provides explicit display context to both action and verification instructions; actual app content still requires visual verification.

## Verified context candidate

APK SHA-256: `4229bde1965319c1bf7e986f4a3a7694aa71ed3d21412c37c77fa35a73e83f6a`.

- A new chat requested Calculator, clear, 37 × 9 and read the result. The real agent completed with 333 in 82 seconds. All calculator key labels were visible.
- During that run, the host typed an unsent `draft-only` into the primary chat composer. The background operation continued and the draft remained unchanged. After completion, owned display count and embedded Node process count were both zero; the authorized helper remained connected.
- A subsequent 8 + 6 task was taken over after entering 8. After confirmed task shutdown, manual preview taps added +6= and displayed 14. Explicit Continue observed that result and completed with 14 in 31 seconds without repeating the calculation. The display and workers were released afterwards.

Evidence: local `artifacts/guided-shell-20261103/chat-conversations.png` (original clipping), `chat-density-calc.png` (fixed layout), `chat-density-result.png` (retained model failure), `chat-main-draft.png`, `chat-context-result.png`, `chat-takeover-paused.png`, `chat-takeover-manual.png`, and `chat-takeover-result.png`.

## Final candidate

APK SHA-256: `ad0c5e48676c950084c57c5bf92ae94b1d8eac7eb5269ccf1661e524920eafb3`.

This adds a smaller initial preview above task controls, background-specific progress text, coordinator-close cleanup, coordinate-aware ClearInput, and floor-rounded capture dimensions to keep the area budget strict.

The final candidate completed a Settings search for `Bluetooth` in 45 seconds with a successful native visual verification and results reading. The host observed the final task response (`chat-search-step.png`); it did not independently capture the short-lived Settings preview. This verifies the real model input path, not every Unicode editing case.

Closing the final candidate preview during a 125 × 8 task stopped the task and removed the display. The primary screen stayed in chat; owned displays and embedded Node workers returned to zero. Evidence: `chat-final-calc-preview.png`, `chat-preview-closed.png`. The interrupted calculation is not counted as completed.

During an explicit retry, the model hit network-request failures and had reached an observation operation; this is not a successful calculation. Terminating only the isolated package's shell helper then stopped the still-active task, removed the preview and left the primary screen in chat. Automatic helper reconnection succeeded, but the task did not resume; owned displays and Node workers remained zero. Evidence: `chat-disconnect-preview.png`, `chat-disconnect-result.png`.

## Automated validation

- `pnpm check`: passed plugin, E2E definitions, adapter, Node, scripts, Kotlin, Android lint, Beta build and APK verification. Run before the final two Debug-only input/geometry corrections.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Pe2eVerification --max-workers=2 --no-parallel --no-daemon`: passed after those corrections; 436 tests, 0 failures, 0 errors, 1 skipped.
- Added regressions for preserving OPPO geometry, larger and landscape displays, strict pixel area, rejection of global keys, background task navigation and host execution context. Existing pure-answer tests caught and prevented a premature phone-capability requirement during development.

## Limits

Debug only, off by default. These tests do not establish arbitrary app compatibility, protected-screen capture, screen-off execution, or all Android versions. OPPO automatic recovery permission remains denied as recorded in the prior setup verification; connection can be restored manually. Beta and Release exclude the backend.

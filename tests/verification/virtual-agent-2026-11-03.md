# Virtual-screen Agent acceptance — 2026-11-03

## Scope

This opt-in Debug experiment uses the embedded Midscene Agent against a built-in secondary-display fixture. It does not enable arbitrary background conversation tasks. Shell consent, an authenticated UID 2000 helper and overlay permission are required. Beta and Release exclude the experiment.

The test package is `lifeos.md.tapilot.e2everify`. Screenshot-guided ADB drives setup and fault injection; the Agent run itself uses the app's configured model, `aiAct`, and `aiAssert`. The host does not perform the successful baseline fixture click. Reports and model credentials remain in private app storage; no account identifiers, pairing codes or credentials are published here.

## Acceptance evidence

Results below distinguish the final candidate from earlier integration checks. An OPPO Find N / PEUM00 on Android 14 uses its folded outer screen. Emulator checks use Android 15 / API 35 and do not establish OEM compatibility.

The initial real-model run completed on OPPO: the native counter advanced from 0 to 1 and the model assertion passed. Three model HTTP requests returned 200. This exposed overlapping controls and repetitive RPC progress; the final candidate reduces the floating preview width, moves controls above the detail text and uses the existing execution-step event pipeline.

### OPPO core acceptance candidate

- Baseline: real model increments 0 → 1 and `aiAssert` passes; the native counter independently matches. The UI records Plan, Locate, Tap, Plan and Assert.
- Takeover: a manual preview tap while RUNNING is ignored (counter stays 0). Pause reaches manual control with no embedded Node process; a manual tap reaches 1. Explicit continuation performs Plan and Assert without Tap, succeeds and keeps the counter at 1.
- Closing the floating preview while planning releases the display and exits Node, retaining only the authorized helper.
- Revocation from the persistent notification after completion removes helper, display and Node.
- Killing only the isolated shell helper during planning stops the trial. Automatic helper reconnection leaves zero displays and zero Node processes; the task remains stopped.
- Force-stop during execution changes one display/helper/Node process each to zero of all three.
- Beta DEX contains neither shell-helper classes, `VirtualAgentTrialActivity` nor `VirtualScreenDevice`.

### Emulator checks

- At 1.3× system font scale and in system dark mode, setup and the manual fixture remained operable; the counter advanced 0 → 1. Rotation released the display without automatic recreation.
- The advanced form rejected an empty pairing port.
- Computer fallback: the visible Prepare action armed consent, the displayed repository command connected an authenticated helper, and the floating fixture received input. Forget pairing stopped that helper.
- Manual phone pairing: after forgetting the local key, Settings and Tapilot were placed in Android split screen. The current pairing port and code were entered in the advanced form while the system dialog remained open. Pairing succeeded and automatic connection established a helper. Neither workstation `adb pair` nor computer bootstrap was used for this path. The host only drove visible UI.
- The visual check exposed low-contrast dark checkboxes and an unfilled page background; both are corrected in the final source. A separate close-state correction settles unfinished execution steps instead of leaving them labeled Running.

## Safety boundaries

Each run captures and injects only into its owned nonzero display. A fresh frame identifier is required before action. Unsupported actions fail without a primary-display fallback. Pausing invalidates the run generation immediately, then waits for process shutdown and bounded in-flight input before enabling manual interaction. Resuming observes again with the same goal. Late callbacks from the previous run cannot finish or resume the replacement run.

Closing, revocation, disconnect and activity destruction release the display. Every model attempt has a three-minute deadline. Reconnection never recreates a display or resumes a task. The model receives only fixture images and the counter goal. Reports use the existing private repository.

## Release decision

Keep the feature experimental, opt-in and Debug-only. Passing a bounded fixture task does not establish general background-app automation, screen-off support, protected-screen capture, or universal OEM recovery. OPPO refuses the optional secure-settings grant; manual restoration of Wireless debugging remains required on that device. No APK release is authorized or performed by this work.

## Build verification

`pnpm check` passed with Node 24.22.2 and JDK 17, including plugin, E2E definition, adapter, embedded runtime, script, Kotlin, Beta lint/build and APK checks. Kotlin results: 430 tests, zero failures/errors, one existing skip. Separate Debug assembly and lint passed with `-Pe2eVerification`. Gradle used two workers, no parallel execution and no persistent daemon. Initial missing local plugin/E2E dependencies were resolved with their committed lockfiles; no dependency manifests were changed.

Core acceptance APK SHA-256: `c281a2528da77198b82f7511a9eac7f06858845cb3fd20b9d8027fb4fd8c0fbc`.
Beta APK SHA-256: `d0cb01433691a7ef6aa97bc29eabaf67a8563a385a98c370275e1e665a34864d`.

The preceding Agent candidate kept its OPPO display alive for over ten minutes (20:03–20:14 local). After model completion, manual taps advanced the fixture from 1 to 3. At 20:14, one helper and one owned display remained, with no embedded Node process. Closing the preview removed the display while retaining the authorized helper. This tests connection/display lifetime, not a ten-minute model task.

## UI regression candidates

After the acceptance run, the final UI fixes settle interrupted execution steps, tint dark-mode recovery checkboxes and fill short pages with the theme background. Debug assembly, all Kotlin tests and Debug lint passed again. This APK repeats the real-model 0 → 1 success on OPPO; closing during planning now shows Completion unknown instead of Running.

UI regression APK SHA-256: `2f50edb2f4537d65d9e15f90ac7278a5e2dd722c9c3486a4fca1c81708e2d8c0`.

A final system-bar-only correction uses light status/navigation icons on dark pages. Debug assembly, unit tests and lint passed again. Its setup screen was checked on the Android 15 emulator at 1.3× font scale; status icons, checkbox and full-page background are legible. This final APK was not rerun through the model; the preceding UI candidate supplies the OPPO model evidence.

Final APK SHA-256: `384823d960ef2d1b35d60869f33aa495d1250ff8cbbc455bfec58e7a1bad1343`.

English/Chinese resource parity: all 19 Agent strings and 61 setup strings have matching keys.

## Screenshots

- [Final OPPO Agent success](virtual-agent-2026-11-03/oppo-agent-success.png)
- [Manual takeover reaches the goal](virtual-agent-2026-11-03/oppo-manual-goal.png)
- [Resume verifies without repeating the click](virtual-agent-2026-11-03/oppo-resume-verified.png)
- [Final stopped-step state](virtual-agent-2026-11-03/oppo-stopped.png)
- [Connection after manual phone pairing](virtual-agent-2026-11-03/emulator-manual-pairing.png)
- [Final dark setup at 1.3× font scale](virtual-agent-2026-11-03/emulator-dark-setup.png)

Takeover and manual-pairing captures use the core acceptance candidate. Success and stopped-state captures use the UI regression candidate. The dark-setup capture uses the final system-bar candidate. Raw pairing dialogs, account settings and model reports are excluded.

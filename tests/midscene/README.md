# Tapilot × Midscene E2E

Android product-flow tests using the same standalone package, YAML case discovery,
tag selection and native reports as [Rome PR #974](https://github.com/rome-os/rome/pull/974),
reviewed at `d1b3183a0bbb92c1335d7bd3749d1aa4f2b299a1`.
The suite uses `@midscene/test` and `@midscene/android` 1.15.3. YAML in `cases/`
is the single source of truth. UI actions and visible checks use `aiAct`, split
at product-stage boundaries.

Latest fixed-APK coverage, first-run failures and performance measurements are
tracked in the [2026-10-27 verification ledger](verification-2026-10-27.md).
The chronological notes below include earlier APKs and must not be pooled as one
current-build pass.

## Setup

Use Node 24 and ADB. Install Tapilot on an unlocked test device. Keep its display
state fixed during a run. Set a sufficiently long screen timeout or enable the
Android developer option to stay awake while charging before running. Some OEM
devices deny ADB writes to this policy; the suite does not bypass that restriction. Connect the selected device before starting; the suite
never picks the first connected device, installs APKs, clears app data or performs login.

```sh
npm ci --prefix tests/midscene
cp tests/midscene/.env.example tests/midscene/.env
# Edit the ignored .env locally: host visual-model credentials and exact device serial.
pnpm check:e2e
npm --prefix tests/midscene run check:model
pnpm test:e2e
```

The host model drives the test observer; the model account configured inside the
phone's Tapilot app drives its native tasks. These are separate authorizations.
Smoke cases need only the host model. Live cases also require an already configured
Tapilot model and enabled Tapilot accessibility. Complete login on the phone;
never paste tokens or passwords into test cases or chat.

`TAPILOT_E2E_DEVICE_ID` is required. `TAPILOT_E2E_DISPLAY_ID` defaults to logical
display 0. Midscene maps it to the physical screenshot display, including folded
OPPO devices with inactive inner displays. `TAPILOT_E2E_PACKAGE` defaults to
`lifeos.md.tapilot`; select an explicit verification-package suffix if needed.
Use a dedicated test device/profile: live actions can visit other installed apps.

For a default-display phone with a resolution override (for example Samsung
physical 1440×3088, overridden to 1080×2316), set `TAPILOT_E2E_SCREEN_INFO=wm`.
This lets Midscene read the input dimensions from `wm` while selecting the physical
screenshot display. The default `physical` mode is retained for folded OPPO.
`wm` mode rejects nonzero display IDs. Do not change the user's screen resolution
to make a test pass. The wrapper pins the project config even when selecting one
YAML file from `cases/` or `fixtures/`.

To reuse a locally configured environment file without copying credentials:

```sh
TAPILOT_E2E_ENV_FILE=/absolute/path/to/.env \
TAPILOT_E2E_DEVICE_ID=your-serial pnpm test:e2e
```

The runner loads that file for model verification and execution. Exported environment
variables take precedence for device and tag selection. Nonempty model fields in
the selected local file take precedence over inherited model variables; set
`MIDSCENE_MODEL_PROTOCOL` explicitly for the provider (`openai-chat` for Seed).
`.env`, reports and dependencies are gitignored.

## Cases and selection

| Case | Goal | Mode |
| --- | --- | --- |
| UI-01 | Fresh conversation, draft entry and clearing | Smoke |
| UI-02 | Toggle automatic reply speech, verify persistence and leave it on | Smoke |
| UI-03 | Wireless/accessibility guidance and model-access choices | Smoke |
| CHAT-01 | Clarify a general question, then finish with an answer and completed status | Live |
| DRAFT-01 | Retry only the historical message and preserve the unrelated composer text/attachment | Live |
| DRAFT-02 | Restart with an unsent attachment, then preview the preserved content | Resilience |
| DRAFT-03 | Share text through Android while preserving the unsent draft attachment | Live |
| LONG-01 | Three Calculator results, precedence and retained results | Live |
| LONG-02 | Device facts agree with observed screens and reports | Live |
| LONG-03 | Two browser pages, source dates and Calculator comparison | Live |
| VOICE-01 | Low-volume TTS sample reaches playing and successful playback | Live |
| TASK-01 | Stop an active task and execute a new request | Live |
| FAIL-01 | Impossible Settings target produces an honest failure | Live |
| FAIL-02 | Rejected model credentials produce a visible failure | Isolated auth fixture |
| FAIL-03 | Network loss fails honestly; restored network permits explicit retry | Resilience |
| UPDATE-01 | Append a requirement while a task is running | Live |
| UPDATE-02 | Change a calculation requirement without losing completed work | Live |
| LIFE-01 | Background and foreground preserve task state | Live |
| LIFE-02 | Force-stop/relaunch retains paused state without automatic replay | Resilience |
| LIFE-03 | Lock/wake preserves state and avoids duplicate execution | Resilience |
| WIFI-01 | Occupied preferred port and repeated start recover without EADDRINUSE | Resilience |
| WIFI-02 | Pinned TLS disconnect/reconnect produces a fresh screenshot | Resilience |
| WIFI-03 | Wireless lease blocks local execution; releasing it permits explicit retry | Resilience |

```sh
# Default: only smoke cases; no native phone-model task is submitted.
pnpm test:e2e
MIDSCENE_INCLUDE_TAGS=speech pnpm test:e2e

# Explicit opt-in to native tasks. Tags are OR-combined, comma-separated.
pnpm test:e2e:live
MIDSCENE_INCLUDE_TAGS=evidence pnpm test:e2e:live
MIDSCENE_INCLUDE_TAGS=calculator,cancellation pnpm test:e2e:live
```

All cases run serially with a 30-minute case timeout and zero automatic retries.
A timed-out action must be inspected before rerunning; never replay an uncertain
mutation automatically. Opening a case creates a fresh device connection and observer agent but retains
phone data and model login. Chat cases explicitly create a new conversation.
UI-02 exercises both automatic reply speech states and leaves it on, matching the
current user preference. It does not start microphone capture or play an audio sample.
Other cases preserve the automatic reply speech selection.

ADB is the external developer test harness. Tapilot's native agent still executes
its own tasks through AccessibilityService. Only the explicit wireless resilience cases exercise the computer plugin transport,
using existing private pairing data; they do not perform initial pairing. Disconnect any wireless controller
holding the device lease before native tasks. Do not operate the phone from another
session while this suite is running.

The controller submits a goal to Tapilot, observes it without interacting while
running, then checks native execution records and final answers. It must never do
the requested calculations, browse pages or repair task results itself. A green
"completed" badge alone is insufficient. LONG-02 specifically catches mismatched
storage numbers and inverted automatic-brightness states found during manual testing.
Cancellation must leave the previous task stopped and permit a fresh Settings task.

## Fault and recovery profiles

Ordinary smoke/live runs exclude `resilience` and `auth-fixture` tags even when a
broader tag filter is supplied. Opt in separately:

```sh
pnpm test:e2e:resilience
MIDSCENE_INCLUDE_TAGS=wireless pnpm test:e2e:resilience
pnpm test:e2e:auth-fixture
```

Use a dedicated test device for resilience cases. The network fixture snapshots
airplane mode, Wi-Fi and mobile-data state and restores them after each case.
Offline injection enables airplane mode and disables Wi-Fi; keep USB connected
until restoration has completed and inspect cleanup failures before proceeding.
Before injection, a phone-side watchdog must arm successfully. It restores the
captured radio preferences after five minutes even if USB disconnects. Normal
cleanup cancels it only after restoring every radio. A watchdog expiration fails
the case, so automatic recovery cannot masquerade as a successful offline test.
Force-stop retains app data. Lock/wake requires a device without a secure screen
lock: the fixture never enters a PIN or bypasses authentication. Port-conflict
coverage requires `TAPILOT_E2E_WIFI_ADDRESS`; the test owns and removes only its
own listener. Wireless transport coverage requires `TAPILOT_E2E_PAIRING_FILE`,
a private existing Android Use pairing JSON targeting the selected phone.
Disconnect other controllers before the run.

The rejected-auth profile requires `TAPILOT_E2E_PACKAGE` to identify a separately
installed verification package already configured with rejected dummy credentials.
Build one with `./gradlew assembleDebug -Pe2eVerification`; its application ID is
`lifeos.md.tapilot.e2everify`, so its data and model credentials stay separate.
It refuses the personal main-app package and never edits real account credentials.
For a fresh `e2everify` installation, explicitly provision its synthetic credential:

```sh
TAPILOT_E2E_PACKAGE=lifeos.md.tapilot.e2everify \
  npm --prefix tests/midscene run test:auth-fixture -- fixtures/rejected-auth-setup.yaml
TAPILOT_E2E_PACKAGE=lifeos.md.tapilot.e2everify pnpm test:e2e:auth-fixture
```

The setup fixture lives outside case discovery, refuses the main package and only
saves a literal rejected test key in the separate app. No real login is needed.
Fault cleanup also runs at project teardown. Inspect reported cleanup failures
before starting another test.

## Verification and reports

`pnpm check:e2e` runs TypeScript, native Midscene node-schema normalization and
case checks, plus model-free fixture guard, recovery and native-task wait tests. It requires no model, Android SDK or phone. Ordinary CI runs this
check; it does not run device/model-backed cases. Root `pnpm check:security` also
audits this package. The lockfile pins compatible fixes for affected transitive
Android/Appium dependencies; keep overrides until upstream ranges include the fixes.

Model runs produce original framework artifacts:

- HTML replay: `midscene_run/report/`.
- Native result summary: `.midscene/test-results/<runId>/summary.json`.
- Native Markdown result list: `.midscene/test-results/<runId>/result.md`.

Keep the HTML report's adjacent assets when copying a run. Do not publish reports
from personal devices without reviewing captured screenshots for private content.
Record the tested APK/source identity separately from test-run status.

Initial device smoke attempt (2026-10-14): the YAML preflight and device launch
passed; the first model call failed with `401 token_expired` in the host's existing
Rome model credentials. No smoke case passed. Refresh the host model configuration,
run `check:model`, and rerun the suite. Phone ChatGPT login does not refresh this
separate host credential.

Seed model rerun (2026-10-14): text, image understanding and locate verification
passed (3/3). UI-01 passed on the physical device. The first suite run found and
fixed an Android fixture lifetime issue: destroying an agent also destroys its
device, so each case now creates a new connection. The subsequent run was manually
interrupted when an unrelated `lifeos.md.tapilot.test` installation screen appeared:
1 passed, 1 interrupted/failed and 1 not run. This does not establish a full smoke pass.

The expanded suite contains 18 cases (11 added recovery/failure/update cases).
Authoring checks and all four fixture tests passed on 2026-10-14. These new cases
have not yet been executed against a phone; no device-backed pass is claimed.

Continuation attempt (2026-10-14, 21:35): UI-02 failed before changing the switch
because the phone slept and screenshots became black. An attempted charging
screen-policy fixture was rejected by the OEM with WRITE_SECURE_SETTINGS denial;
it was removed. The phone policy remains 0 (unchanged). Setup now rejects a
sleeping phone before consuming model calls, and the observer receives the exact
installed package name. Manual unlock and a longer screen timeout are required
before continuing. Neither automatic speech enablement nor this case passed.

2026-10-15 follow-up: enabled the OPPO developer setting to stay awake while
charging through its visible UI (policy 15, mStayOn=true), leaving media volume
at 1/16. UI-02 then passed 1/1 in 235 seconds: automatic reply speech was toggled
and verified enabled after navigation. Run `20261015094127-8e93df35` includes
the native report and screenshots. This verifies switch persistence, not actual
TTS audio playback or the remaining 17 cases.

VOICE-01 exercises actual TTS sample playback with existing phone credentials.
Verify low media volume before running it. A successful UI status verifies the
app playback path; record AudioTrack/provider evidence separately. It does not
measure acoustic output from the physical speaker. The suite now has 19 cases.

Native completion uses `task.waitForIdle`, an abortable wait on the selected
app task service (up to 25 minutes), followed by aiAct checks of the final UI
and native execution records. A still screenshot during a model call is not a
pause verdict. app.open refuses to replace an active native task after a failed
case. Calculator device execution was observed to continue past the former
observer failure; case budgets include native execution and report inspection.

2026-10-15 verified continuation: the full smoke run
`20261015100136-5e35dcb2` passed 3/3. The strict missing-target case passed
in `20261015102632-323bf798`, requiring recorded Settings screens; an earlier
green result caused by an unrelated runtime error was invalidated.
VOICE-01 passed in `20261015111052-18a2951a` after the existing Volcengine
speech credential was configured privately. The phone showed playback success
with media volume 1/16, Seed TTS 2.0 and Vivi 2.0. This verifies the playback
path, without measuring physical speaker loudness. The tested voice APK SHA-256
was `6e1141ef0f4b0c9d942ee2742d25c8100ab56d59a712028a66195087ad9e656b`.
These results do not establish a pass for all 19 cases. Original reports remain
local and ignored because device screenshots can contain private information.

LONG-02 run `20261015111414-a31fdce1` failed its strict evidence check after
the overlay-input fix. Native execution completed without the former accidental
panel pause, and storage 53.4/256 GB plus enabled automatic brightness had recorded
support. The reply also claimed Android 14, but the record only showed ColorOS
14.0, which cannot verify Android version. This remains a product evidence gap;
the completed badge does not override the failed E2E verdict.

2026-10-15 follow-up implementation: each native operation now runs an independent
`aiAsk` screenshot reading after its assertion. Multi-page facts are requested as
separate operations; an empty reading cannot fall back to action summaries or logs.
Regression tests cover ColorOS/Android confusion and missing observations. The
original OPPO was disconnected during this follow-up, so LONG-02 remains awaiting
real-device revalidation; these changes do not turn its previous failure into a pass.

A selected model env file is now a complete model profile: undeclared protocol, planning/insight credentials and extra-body settings from other profiles are cleared. An explicitly missing file fails instead of falling back to another account.

2026-10-27 draft and amendment audit: DRAFT-03 reproduced loss of the unsent
attachment after Android text sharing in `20261027081336-836bdefa`. After the fix,
`20261027081725-62b55cfa` passed 1/1 on its first attempt. The final build then
passed DRAFT-02 and DRAFT-03, 2/2 with zero retries, in
`20261027083306-b3d46d09`, including restart recovery and attachment previews.
An earlier DRAFT-03 setup attempt accidentally tapped the keyboard Send key and
never reached sharing; it is not counted as a product reproduction.

At this stage, UPDATE-02 was still unverified end to end. `20261027080549-a6e29fcf` exposed a
late amendment being accepted as a separate goal and an incorrectly grouped
calculation reported as 4,850. Explicit amendments to failed/paused tasks now
retain the original goal, with old-fails/new-passes JVM coverage. Calculation
guidance and verification also preserve operation grouping. On the final build,
`20261027082149-c383087b` accepted the amendment while running but ended with an
explicit incomplete result after model connection failures. The native report
records `UND_ERR_CONNECT_TIMEOUT`; the expected 1,298 was not substituted for
phone evidence. Do not count this as a Calculator pass. The observer now follows
each step's stopping condition instead of always waiting for terminal state, and
UPDATE-02 explicitly rejects amendments submitted after the task already stopped.

2026-10-27 continued audit: after the user authorized starting the phone's existing
VPN, native model requests succeeded. UPDATE-02 `20261027090145-05294838` was a
false green: Calculator displayed 1,298 but the native task was incomplete. That
result is invalidated. The case now independently requires successful completion.
The run also reproduced native actions navigating away while the user typed an
amendment. Native conversation device access now pauses when the user returns to
chat or focuses input; continuing requires a fresh screenshot after the pause.

Per-operation verification now checks the current step after a plan, resume, or
earlier verified step. It does not require all historical expressions to remain on
one screenshot. The conversation still owns overall completion. Failed phone
actions use deeper planning for recovery, with the same configured model/account;
read-only observations and ordinary successful follow-ups retain their effort.
Intermediate builds still failed UPDATE-02 (`20261027092146-34ba9334` and
`20261027094152-1fdd5139`); these are retained as failures, not retry passes.

Recovery-planning APK SHA-256
`4bf8ed23512f195309991f35ee9cb2e577f19ed5acee51df7cab311868ae669d`
passed the full `pnpm check`: Kotlin 384 passed / 1 skipped, Node 125,
plugin 94, E2E fixture checks 12, script checks 22, plus adapter, Android lint,
beta build, embedded-runtime and signing checks. On this APK:

- UPDATE-02 `20261027101117-24ee42fd`: 1/1, zero retries, all six steps passed.
  Native screenshots show 128×37=4736, 4736+456=5192 and 5192÷4=1298;
  final completion passed the independent assertion. Native elapsed time was
  218.171 seconds including the input pause; the full case took 290.025 seconds.
- TASK-01 `20261027101812-5526d8a0`: 1/1, zero retries. The old task stayed
  stopped, and a new Settings task completed with actual Settings replay evidence.
- DRAFT-01 `20261027102538-474835ba` failed during setup when the observer used
  the keyboard Send key to dismiss it, before Retry was exercised. After changing
  the case to use Android Back, `20261027102921-8777891d` passed 1/1 without
  automatic retries: the original message was retried while unrelated draft text
  and its attachment remained unsent. Test artifacts were cleaned afterwards.
- LONG-02 `20261027103413-d8afb741`: 1/1, zero retries, 339.637 seconds
  including report inspection. Android 14, storage 55.7/256 GB and enabled
  automatic brightness all matched the native replay and separately captured
  read-only screen evidence. The original ColorOS/Android evidence gap is now
  revalidated on a physical phone; native execution took about 2m35s.

Observer guidance now returns via Launch/recent apps, inspects actual Player
screenshots instead of treating raw prompts as evidence, and uses Android Back
to dismiss a keyboard without submitting a draft. These selected device results
do not establish a live pass for every authored case.

An independent UPDATE-02 repeat, `20261027104022-e72e3c7b`, also passed all
six steps without observer retries on that APK, but native elapsed time rose to
699.507 seconds. Its native report exposed repeated visual mislocation: the
requested digit 2 was located over 5 four times before recovery. This operation
already used deepThink, so the delay cannot be attributed to fast planning alone.
Deep planning now also enables deepLocate by default, while preserving an
explicit override and the ordinary fast/balance behavior.

The resulting APK SHA-256 is
`6c76275844c4a1a1a699aa4a26a8abdac45abc5c3d4482375d24425ae81dcc99`.
Full `pnpm check` passed again, including 126 Node tests. Strict UPDATE-02
`20261027110005-12addf4f` passed 1/1, zero observer retries; native elapsed time
was 396.088 seconds and the complete case took 485.792 seconds. The comparison
is one run per condition, not a general latency guarantee. Cancellation, draft
retry and device-fact results above belong to the preceding APK; they were not
rerun immediately after this Node-only locating-policy change. See the current
verification ledger for the later fixed-APK regressions.

The final native report confirms deepLocate was active. Two ambiguous digit-2
locations failed and their taps were cancelled; a later location using the
neighboring 1/2/3 row succeeded. The earlier repeated taps on 5 did not recur in
this recorded operation. This is recovery within a passing case, not an entirely
error-free model run. The exported operation used 53 model calls / 302.482 seconds,
versus 84 calls / 582.931 seconds in the preceding slow operation; these operation
times exclude the remaining conversation and observer work.

The offline fault snapshots airplane mode, Wi-Fi and mobile-data preferences. It
enables airplane mode and disables Wi-Fi, waits up to 30 seconds for readback,
and restores all three choices in cleanup. This also covers OEMs where
`svc data disable` leaves `mobile_data=1` despite no usable cellular service.
A failed model preflight is not an offline-recovery pass.

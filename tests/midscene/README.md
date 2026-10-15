# Tapilot × Midscene E2E

Android product-flow tests using the same standalone package, YAML case discovery,
tag selection and native reports as [Rome PR #974](https://github.com/rome-os/rome/pull/974),
reviewed at `d1b3183a0bbb92c1335d7bd3749d1aa4f2b299a1`.
The suite uses `@midscene/test` and `@midscene/android` 1.15.3. YAML in `cases/`
is the single source of truth. UI actions and visible checks use `aiAct`, split
at product-stage boundaries.

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
Wi-Fi and mobile-data state and restores both independently after each case;
force-stop retains app data. Lock/wake requires a device without a secure screen
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
case checks, plus seven model-free fixture guard, recovery and native-task wait tests. It requires no model, Android SDK or phone. Ordinary CI runs this
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

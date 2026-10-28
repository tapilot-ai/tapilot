# Open-source readiness verification, 2026-10-28

This records observed results, not an approval to distribute binaries. GitHub
hosted CI is excluded at the owner's request because of the account billing block.

## Local release checks

- Full `pnpm check` passed, including Kotlin (388 passed, 1 skipped), plugin (94),
  runtime (127), scripts (34), adapter (28), E2E definitions (12), Android Lint,
  non-debuggable Beta build and APK integrity/signature verification.
- All four dependency audits reported zero known vulnerabilities.
- `pnpm check:cli-package` passed: frozen source install, source/consumer audits,
  independent tarball installation with lifecycle scripts disabled, 94 plugin
  tests against the installed artifact, and forced WASM encode/resize/decode.
- Global installation into a private temporary prefix and CLI invocation passed.
  No npm publication was performed. Normal source-directory pack is rejected.
- 132 checksum-pinned distribution materials assembled successfully. NDK r30
  LLVM notices now cover the libc++ notice gap; source/rebuild review is separate.
- Four distribution blockers remain: Sharp WASM corresponding source/relink
  evidence, proxy-agent-negotiate full notice, secure-compare full notice, and
  the remaining native/component corresponding-source review. The gate fails.

## Physical device, OPPO PEUM00 / Android 14

Both APKs were installed with `adb install -r`, preserving app data. Installed
base.apk hashes were read back and matched. These are different APKs; results
are not pooled into a claim of complete same-APK coverage.

| APK SHA-256 | Case | Result and evidence |
| --- | --- | --- |
| a7a3e321dd9b1761cc6ab3796ba10ff361c05b3a727bbfe4987fef35d7d798b5 | UI-01/02/03 | All passed, first attempts, batch 20261028142902-518c710c. |
| a7a3e321dd9b1761cc6ab3796ba10ff361c05b3a727bbfe4987fef35d7d798b5 | TASK-01 | Passed all six steps, batch 20261028143949-7cbd8a7a. New native Settings task completed in 45s; report inspection took 335s. |
| a7a3e321dd9b1761cc6ab3796ba10ff361c05b3a727bbfe4987fef35d7d798b5 | CHAT-01 | Interrupted by evaluator SIGINT during setup; retained as failed, not a product failure or pass. |
| ed26d1eb18e7cb818175e07b91d09376b23cf1faec1232ebb5d3b8cfcecf3b29 | CHAT-01 | Passed all seven steps, batch 20261028145022-22d857a1. First reply waits; second returns 100, completed, zero phone operations. |
| ed26d1eb18e7cb818175e07b91d09376b23cf1faec1232ebb5d3b8cfcecf3b29 | WIFI-01 | Passed preferred-port collision and repeated starts, batch 20261028145238-cb850898. |
| ed26d1eb18e7cb818175e07b91d09376b23cf1faec1232ebb5d3b8cfcecf3b29 | WIFI-02 | Fresh-frame reconnect fixture passed (881ms); full case failed at final UI check when USB device disappeared. |
| ed26d1eb18e7cb818175e07b91d09376b23cf1faec1232ebb5d3b8cfcecf3b29 | WIFI-03 | Failed prerequisite: adb device absent. Native-task contention was not exercised. |

No automatic case retries. USB remained absent at the follow-up check. The owner
was asked to reconnect it; secure unlock credentials were not requested.
Remaining cases (drafts, long tasks, lifecycle faults, auth rejection, offline
recovery, playback) have not passed on the final APK in this round.

## Independent packaged CLI over Wi-Fi

With USB absent, the source CLI captured the live two-turn conversation. Its
runtime was then stopped and the globally installed tarball started in an
isolated runtime state directory, reusing the existing pairing registry.
The first connection timed out; an independent screenshot retry succeeded.
A single tap using its returned frame ID opened Settings. The fresh readback
showed Accessibility Connected and Wireless control: Computer connected.
Report ID: b1511668-1725-401f-8fa4-a6144b80cc4c.
This verifies packaged CLI control and shared pairing without USB. It does not
prove uninterrupted connectivity, all-host-platform support, Codex GUI acceptance,
or the complete resilience suite.

Private screenshots, HTML and command logs remain local/ignored. Logs use the
`/tmp/tapilot-readiness-` and `/tmp/tapilot-package-` prefixes. Do not publish
those artifacts without a separate privacy review.

## Follow-up after USB reconnection

- Main branch through `f7e90df` was pushed to origin. Repository remains private.
- Full-history Gitleaks scanned 223 commits with no findings beyond the existing
  narrowly reviewed exclusions. Both final videos were additionally sampled at
  one-second intervals; no obvious credential exposure was observed in the
  contact sheets. This is not exhaustive frame/audio or rights clearance.
- Checks run 40649909473 and Secret scan run 40649909355 still failed before
  job steps. The Checks annotation explicitly reports account payment/spending
  restrictions; running Dependabot jobs do not prove these checks ran.
- USB reconnected and the installed APK hash again matched `ed26d1eb…`.
  Wireless reconnect's fresh-frame fixture and final waiting-state UI check
  passed on the independent follow-up. Both WIFI-02 and WIFI-03 passed all
  steps in batch `20261028152515-5fd57a53` (2/2, first attempts, no retries).
  WIFI-03 took 195.018s including observer setup: a connected controller blocked
  the native task; disconnect plus explicit retry completed a real Settings task.

### Network and speech follow-up

FAIL-03 initially failed before submission because the old radio fixture did
not reach its required state. A 30-second diagnostic retry showed Wi-Fi disabled
but `mobile_data=1` (batch `20261028153225-9abc20df`). Cleanup restored Wi-Fi.
The fixture now snapshots airplane mode and uses enabled airplane mode plus
disabled Wi-Fi as its offline precondition, preserving the original preferences.
Thirteen E2E definition/fixture tests pass, including an OEM retained-data-value
regression. Its next full case (`20261028153515-bab1e2c8`) failed model preflight,
before activating the new fault: ChatGPT connection check timed out.

VOICE-01 (`20261028153826-0aeb051f`) failed with a 15-second connection timeout
to the phone's local HTTP proxy. Read-only inspection found the configured
network proxy endpoint still listening; no causal diagnosis is claimed. No
account, VPN profile or credential was changed. Flight mode readback was disabled
and Wi-Fi enabled afterward. Neither offline recovery nor speech playback is
accepted on this evidence.

DRAFT-02 and DRAFT-03 passed in `20261028154312-eec9e85e` (2/2, no retries),
including retained draft/attachment after process restart, share-text preservation,
attachment preview and cleanup. After force-stop, Android removed Tapilot from
its enabled accessibility-service list; restoration is tracked separately and
must complete before another native-task case.

### Restored device control and network

After the draft force-stop, the expected Android permission UI re-enabled
Tapilot accessibility. The restoration helper failed its final assertion because
it had returned to the chat rather than the settings label. Separate system
binding readback and a fresh Settings screenshot both confirmed Connected.
This does not convert the helper's failed result into a pass.

Speech retry after app restart (`20261028155122-2b09fb65`) still failed at the
same local proxy. A phone-shell TCP request also timed out. The Wi-Fi network
owner was Feilian, but that fact did not establish ownership of the failing
proxy. Inspecting the installed network apps showed Feilian VPN off and Clash
stopped with the existing MC profile selected. Starting that unchanged Clash
profile produced Running and a new reachable local proxy endpoint. No profile
content or account credential was changed.

VOICE-01 then passed (`20261028155617-aefb02a8`, 1/1, 23.556s observer action),
requiring the actual Playback succeeded UI. This verifies the playback service
and UI outcome, not an independently recorded acoustic-quality measurement or
continuous microphone conversation. Earlier failures remain in the ledger.

### USB loss during the offline fault

Batch `20261028155730-bd81e55c` passed model preflight and entered the new
airplane-mode/Wi-Fi-off fixture in 501ms. USB disappeared during the following
submission step. Both afterEach and teardown restoration failed because adb
could no longer reach the device. This is a failed case with failed cleanup,
not accepted offline recovery. The captured original state was airplane mode
disabled, Wi-Fi enabled, mobile data enabled. The owner was notified immediately
and asked to reconnect USB (or restore those switches locally). A bounded
10-minute host watcher was started to apply exactly those restoration commands
to serial e7ed3f17 when it reconnects. Final restoration remains unverified
until a subsequent readback is recorded. No further device test runs while
this cleanup is outstanding.

### USB reconnected and fault cleanup completed

After the owner reconnected USB, serial `e7ed3f17` was available again.
The expired host watcher was no longer running. Explicit restoration commands
disabled airplane mode and enabled Wi-Fi and mobile data. Subsequent readbacks
confirmed airplane mode disabled, Wi-Fi connected, and mobile data enabled.
Tapilot remained in the enabled accessibility-service list. A fresh screenshot
of Clash showed Running with the unchanged MC profile activated; no VPN profile
or account was edited. This closes the failed cleanup, not the FAIL-03 test:
that interrupted offline-recovery case remains failed.

FAIL-01 passed on the subsequent connected-device run, batch
`20261028172733-461b9993` (1/1, first attempt, 182.104s). The native task
opened and inspected Settings, reported the missing target without claiming
completion, and the observer checked the execution screenshots. This also
provides a real model-and-device operation after network restoration.

The follow-up live suite also passed TASK-01 on this APK (first attempt,
390.239s): cancellation, a new Settings task, and final execution-record checks.
The remaining cases in that suite are still being evaluated; this entry does
not certify the entire batch.

The same follow-up suite passed DRAFT-01 (158.805s), LIFE-01 (228.817s), and
LONG-01 (552.493s), each on its first attempt. LONG-01's native-service wait
was 274.886s and its visual replay check was 218.395s; total test duration is
not the native task's execution time.

LONG-02 failed its final observer action after 701.082s and 80 replanning cycles.
The native service had settled after 99.190s, but the observer repeatedly
mistook the clickable, instruction-labelled Activity selector for a noninteractive
prompt dialog and never finished verifying storage evidence. This is not a
passing device-facts test. The observer also contradicted its own earlier reading
of the brightness reply, so its intermediate reasoning is not accepted as a
product defect. Preserve the original replay for investigation.

The observer guidance now explicitly distinguishes clickable numbered Activity
entries from noninteractive progress rows. LONG-02's final verification is split
into Android version, storage, and brightness checks, retaining actual Player
screenshots and all original truthfulness requirements. TypeScript/schema checks
and all 13 model-free E2E tests pass. Live verification of this adjustment remains
pending; it does not change the failed original result.

### Completed live batch and separate follow-ups

Batch `20261028173109-901c6907` finished 6/8 passed, 2 failed, without retries.
In addition to TASK-01, DRAFT-01, LIFE-01 and LONG-01 above, LONG-03 passed
(571.908s total; native-service wait 353.475s) and UPDATE-02 passed (417.751s),
including the independent successful-status assertion for the amended result 1298.
UPDATE-01 failed because its observer did not find a recorded storage screenshot.

Read-only follow-up `20261028183138-139a2d3d` inspected existing conversations
without submitting or retrying their tasks. UPDATE-01's original 256 GB capacity
was supported by an existing Player screenshot (READBACK-02 passed, 149.971s).
This supplies missing evidence independently; the original batch remains 6/8.
LONG-02's old Android-version replay returned `Report does not exist`, so its
readback failed. The repository automatically retains at most 20 reports / 200 MB
(always keeping the newest), but chat references had not reflected pruning.

LONG-02 was then fully rerun with split evidence checks in
`20261028183552-132571b6`: 1/1 passed, 348.444s. Android version, used/total
storage and brightness were individually verified from actual Player screenshots.
All preceding live results still apply to installed APK `ed26d1eb...`, not the
subsequent report-link fix.

### Report-link fix build

Unavailable report opens now produce a typed missing-report error. The chat
removes only that report reference and explains that the file was cleaned up or
deleted, with advice to export reports for retention. Other failures still follow
the normal error path. README now documents the existing retention limits.
Kotlin: 390 tests, 389 passed, 1 skipped; Lint and beta build passed. Added
repository coverage checks exact missing ids after pruning and explicit deletion,
and confirms retained reports remain readable. E2E schema/type/fixture checks pass.

APK `fd370537dd86c17adae7cde910a2df09d1f17f305a78c7ce1f9ff2c9f73b5fd0`
was installed with data preserved and its device hash matched the local artifact.
Local APK verification passed (non-debuggable, runtime current, local certificate;
not approved for distribution). The first verification invocation lacked
ANDROID_HOME and failed before validation; it was rerun with the SDK configured.
Targeted UI batch `20261028184305-46251902` selected the newer same-title
conversation, opened an existing replay, and correctly failed its expired-report
precondition. A separate attempt targets the older same-title conversation.

That attempt, `20261028184511-5b9983cd`, passed (1/1, 73.462s): the old
report showed the cleanup/deletion explanation, its particular unavailable entry
was removed, and other report entries remained. The final phone readbacks showed
airplane mode disabled, Wi-Fi enabled, mobile data enabled, and Tapilot's
accessibility service enabled. USB remained connected. This is targeted acceptance
of the report-link fix, not a complete regression of the new APK. Earlier failed
offline recovery and unexecuted acceptance cases remain outstanding; hosted CI and
the four distribution-material blockers are unchanged.

### Evening acceptance continuation (same fd370537 beta)

- Smoke: `20261028204906-6b57c5ec`, 3/3 passed on first attempts
  (UI-01 64.646s, UI-02 253.053s, UI-03 179.099s).
- Added a phone-side five-minute radio recovery watchdog to the opt-in offline
  fixture. It arms before fault injection and restores the original radio state
  even if USB is lost. Normal cleanup cancels it only after successful restoration;
  expiration invalidates the case. Fixture/schema/type checks: 17 passed.
  A real-device three-second probe returned from ADB in 60ms, then restored the
  original Wi-Fi on / data on / airplane off state on its own. This probe did not
  inject a radio outage. The first compound-shell startup attempt held the ADB
  pipe open and timed out before fault injection (`20261028205836-86792a4f`);
  startup now backgrounds only the fully redirected watchdog process.
- Offline case `20261028210250-2e6496d7` remains FAILED: offline failure UI and
  radio restoration passed, but the immediate explicit retry timed out at the
  model connection. No false completion was shown. USB remained connected.
  A later explicit retry of that same task, `20261028210904-24886474`, passed
  in 140.582s with real Settings/WLAN screenshot evidence. No account, model,
  proxy or VPN changes were made. This proves delayed recovery, not immediate
  or automatic recovery.
- Rejected-auth fixture used isolated `lifeos.md.tapilot.e2everify`, APK SHA-256
  `66b23eeaa9723d06c7b9ba0c27171048141cb22885fce2df8db986e6c7eb870a`,
  and a synthetic invalid key. Setup `20261028211329-61683c16` passed;
  FAIL-02 `20261028211927-ac1a388d` passed in 72.507s: explicit authentication
  failure, retryable state, no false completion or exposed credential.
  Earlier setup `20261028211221-9f4674dc` did not run because installation was
  awaiting the OEM prompt; a permission helper later failed at a residual
  installer screen. Neither is counted as a pass. The isolated app was then
  uninstalled and its accessibility entry disappeared. Main-app credentials
  were not modified.
- Real microphone continuous conversation passed: Mac speakers delivered acoustic
  speech to the phone microphone; no text was injected. Turn one asked to remember
  63 and received “新的数字已记住”; after automatic listening resumed, turn two
  asked to subtract three and received “60”, then listening resumed again.
  No phone tap occurred between utterances. Both cards showed completed tasks
  (6s and 4s). ASR misrecognized the second utterance's final “不要操作手机” as
  “不要超过手机”; the arithmetic request was correctly understood. This is not
  perfect-ASR evidence. Initial manual timing attempts missed the 30-second
  listening window and are not counted as continuous-conversation passes.
  Evidence: ignored `build/acceptance-voice/continuous/frame-009.png` and
  `frame-018.png`, local log `/tmp/tapilot-acceptance-continuous-voice.log`.
  Voice was ended and Mac audio restored to volume 17, muted. USB remained
  attached for observation; this is not a physically unplugged acceptance run.
- Force-stop LIFE-02 `20261028213012-4c70afa3`: 1/1 passed, 388.291s.
  Old task remained interrupted and was not replayed; a fresh task succeeded.
  The fixture now explicitly follows the system UI to restore accessibility
  after OEM force-stop revocation. This pass requires that permission recovery;
  it does not establish automatic recovery after force-stop.
- Afterwards the phone showed wireless control Off. Enabling it again showed
  Paired, and the installed plugin CLI connected using the saved computer pairing
  without scanning. A fresh Wi-Fi screenshot showed Computer connected, recorded
  in report `83ff8115-2e08-4afc-b671-a20f96e60e75`. The panel opener returned,
  but the current client's MCP Apps browser access host was unavailable, so actual
  embedded-panel rendering is NOT accepted from this run.

Secure-lock/user-unlock acceptance, hosted CI billing, and the four distribution
material blockers remain unresolved. No package or APK was published.

### Actual Claude Code Skill client check

A temporary project-local copy of the repository Skill was loaded by real Claude
Code, using the previously verified globally installed package through
ANDROID_USE_ROOT. Shared managed skills were not modified. The first run was
blocked by a scoped Bash allowlist mismatch (`/tmp` versus macOS canonical
`/private/tmp`), before any phone command ran. After correcting that exact command
path, the second run completed in 45.628s with no permission denials: devices,
status, wireless screenshot, image Read, then record in the same report. It reused
the connected saved phone without pairing or ADB. Report:
`a5a9b6e1-cce5-44d1-bbea-1e9d69906b0a`; local result:
`/tmp/tapilot-acceptance-claude-retry.json`. This is read-only screenshot/report
integration evidence, not a new multi-step control test. Claude Code retained its
existing configured provider/model (`auto_model/seed-evolving-auto`); it does not
establish an Anthropic-hosted model test.

A separate native Codex GUI inspection was explicitly rejected by Computer Use:
“not allowed to use the app 'com.openai.codex' for safety reasons”. No alternate
GUI-control route was attempted. Together with the unavailable MCP Apps browser
host, this leaves embedded-panel visual acceptance blocked, despite successful
plugin CLI screenshot/report evidence. No report was generated by either failed
GUI inspection.

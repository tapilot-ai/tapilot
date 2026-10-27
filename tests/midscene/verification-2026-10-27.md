# Physical-device verification, 2026-10-27

This ledger distinguishes the installed APK from earlier builds, case first
attempts from independent reruns, and observer failures from native task failures.
The authored suite has 23 cases; an authored or historically passing case is not
automatically a pass on this APK.

The subsequent [frozen screenshot replay](../verification/results-2026-10-27.md)
isolates requirement resolution, arithmetic checking and numeric locating. Those
host-side component results do not add physical-device passes to this ledger.

## Frozen comparison baseline

- Device: OPPO PEUM00, Android 14, USB serial `e7ed3f17`.
- APK SHA-256: `e480e3f0729dea93bb1349ef23cfb566bd42486ae5b2fac8462fc5fb09a0a686`.
  The installed `base.apk` hash was read back and matched the local beta APK.
- Native model: `gpt-6-luna`; existing phone account and VPN profile preserved.
  The host observer uses the existing Doubao Seed profile, so its elapsed time
  is not native model latency.
- Execution: serial physical-device cases, no automatic case retries.
- Local gate: full `pnpm check` passed after the text-completion follow-up: 388 Kotlin
  tests (387 passed, one skipped), 126 Node runtime tests, 94 plugin tests,
  12 E2E fixture tests, 22 script tests, adapter tests, Android lint and beta APK
  integrity/signing checks. Log: `/tmp/tapilot-bug-audit-round8/check-text-completion.log`.
  The observer-format guidance also passed `check:e2e` (12/12).
- Round-eight private artifacts: `/tmp/tapilot-bug-audit-round8/`.
  HTML and screenshots stay local and ignored; this ledger records bounded results.

## Earlier round-eight failure and repair

The initial APK was `6c76275844c4a1a1a699aa4a26a8abdac45abc5c3d4482375d24425ae81dcc99`.
Batch `20261027161749-c26c0813` passed 2/4: TASK-01 and DRAFT-03.
DRAFT-01 passed its product assertions, then failed cleanup on an observer XML
parse error. LONG-02 native execution succeeded in 368.564 seconds, but its
observer was interrupted after repeatedly searching the last replay for earlier
pages. The full LONG-02 case therefore failed; the reply alone was not accepted.

The app had saved three operation reports but only exposed the final report in
chat. Commit `67b1b34` adds an Activity selector for every agent report and names
new records using the corresponding confirmed operation instruction. Existing
records remain accessible. Three regression tests cover retained report choices,
runner-only fallback/deduplication, and operation-to-report attribution.

After installing the report-access APK `ae1f9f1b…`, all three existing replay records were opened
independently on the phone (`report-picker-verify.log` and
`android-2026-10-27_16-51-55-87d65453.html`). This verifies the repaired entry point;
it does not turn execution on the preceding APK into a fresh current-APK pass.
Fresh key regressions are recorded separately below.

On that APK, batch `20261027165456-fc9d89e2` passed TASK-01, DRAFT-01 and
DRAFT-03 (3/4, no automatic retries). LONG-02 stopped before submission because
the observer mistook the standalone wireless "Off" label for Accessibility off.
System readback confirmed the AccessibilityService was still bound and enabled.
Commit `9df77f8` labels the two states separately. The label APK physically
shows "Device control (Accessibility): Connected" and "Wireless control: Off"
together (`control-labels-after.png`), without changing either service.

## Follow-up after the label APK

On `391031b1…`, DRAFT-02 passed in `20261027171822-8463108e` (159.043s).
Batch `20261027172815-72d048b9` passed DRAFT-03 (121.930s) and LONG-02
(439.121s, native 276.268s). Android 14, 55.6/256 GB and auto-brightness on
matched passive frames 0040/0054/0075 and reply 0088 in `facts-labelled-frames/`.
Every relevant operation replay was accessible. TASK-01 failed before phone
operation on a credentials `connection closed` error (native 5.630s, observer
121.111s); the next credentials request recovered without account/network changes.
DRAFT-01 passed its product assertions but failed cleanup on observer XML action
`AndroidBackButton>` (168.333s).

Independent rerun `20261027174257-bf71c8e9` passed TASK-01 (348.851s, old task
stopped in 52.810s, new task succeeded in 83.843s). DRAFT-01 failed at its final
assertion (130.817s): the retry returned RETRY-BASELINE but was marked incomplete.
Draft text/attachment remained intact. Both model requests succeeded; there was no
phone operation. Performance logs do not reveal the exact terminal tool arguments,
so misrouting is an inference, not a captured trace. Commit `e1c4c07` clarifies that
`answer_question` covers all text-only requests, including exact-text replies, and
improves the correction when `finish_task` is used without phone evidence. It does
not relax any verification guard. Cleanup completed separately.

On the frozen `e480e3f0…` APK, draft batch `20261027175841-7b35b268` passed
DRAFT-03. DRAFT-01 and DRAFT-02 stopped during setup on the observer's malformed
`AndroidBackButton>` action (112.602s and 19.714s); neither target behavior was
exercised. Converted `observer-back-format/report.md` confirms the malformed XML
value. Commit `abc19b7` gives an exact action-format example to the observer;
no parser behavior, result or assertion was overridden. Independent rerun
`20261027180324-c067c3b0` then passed both complete cases, including cleanup.
These are recovered independent runs, not first-pass successes.

## Coverage by APK

The table separates the frozen e480 baseline from the 665a follow-up. `passed` requires
a complete case; independent reruns are identified and do not erase earlier
failures. `failed` includes observer/cleanup failures even when product assertions
passed. `blocked` identifies a checked missing prerequisite. `not_run` means no result on that build; historical evidence appears separately.

| Case | Frozen e480 | Follow-up 665a | Evidence and boundary |
| --- | --- | --- | --- |
| TASK-01 | passed | passed | `20261027180959-cdc35ced`: all six steps, first attempt, zero retries, 226.568s. Old task stopped in 55.135s; new Settings task succeeded in 21.349s with replay evidence. Follow-up `20261027201545-7edfda27`: all six steps, first attempt, 255.518s; old task stopped and new Settings task succeeded. |
| CHAT-01 | not_run | not_run | Earlier clarification run `20261026223446-133f6c3c` passed on an older APK. |
| DRAFT-01 | passed | not_run | `20261027180324-c067c3b0`: all ten steps, zero automatic retries, 169.523s. Independent rerun after the current-APK observer setup failure; retry completion and preserved unsent text/attachment passed. |
| DRAFT-02 | passed | not_run | `20261027180324-c067c3b0`: all seven steps, zero automatic retries, 114.878s. Independent rerun after the current-APK observer setup failure; draft/preview survived restart. Accessibility restored separately afterward. |
| DRAFT-03 | passed | not_run | `20261027175841-7b35b268`: all seven steps including cleanup, first attempt, zero retries, 131.600s. |
| FAIL-01 | not_run | not_run | Older strict verification-failure case passed in `20261015102632-323bf798`. |
| FAIL-02 | blocked | blocked | Isolated `lifeos.md.tapilot.e2everify` package is not installed (checked with `pm path`). Main-app credentials were not changed. |
| LIFE-01 | not_run | not_run | Background-task case has no result on this APK. |
| LIFE-02 | not_run | not_run | Task process-death recovery is separate from DRAFT-02 draft recovery. |
| LIFE-03 | not_run | not_run | Lock/recovery case has not been executed on this APK. |
| LONG-01 | not_run | not_run | Three-result calculation is separate from UPDATE-02. |
| LONG-02 | passed | passed | `20261027180959-cdc35ced`: all four steps including separate operation replays, first attempt, zero retries, 579.857s; native task 360.309s. Android 14, 55.7/256 GB, auto-brightness on. Independent frames 0027/0047/0066 and reply0086 in `facts-text-completion-frames/` agree. Follow-up `20261027201545-7edfda27`: all four steps, first attempt, 416.151s; native 210.391s; Android 14, 55.9/256 GB and auto-brightness on match independent frames. |
| LONG-03 | not_run | not_run | Browser-to-calculator case has no result on this APK. |
| FAIL-03 | not_run | not_run | Network-fault case has no result on this APK. |
| UI-01 | not_run | not_run | Older smoke suite `20261015100136-5e35dcb2` passed. |
| UI-02 | not_run | not_run | Older smoke suite passed; no current-baseline speech-switch result. |
| UI-03 | not_run | not_run | Older smoke suite passed; no current-baseline permissions result. |
| VOICE-01 | not_run | not_run | Older `20261015111052-18a2951a` passed the playback UI path, not acoustic verification. |
| UPDATE-01 | not_run | not_run | Added-requirement case is separate from UPDATE-02 replacement. |
| UPDATE-02 | failed | passed | Frozen-baseline independent run 3 `20261027185358-b0379a74` failed: actual 5192÷4=1298 was visible, but native verification demanded earlier addition history and task stayed incomplete. Run 2 passed; the three-run results below retain both failures. Follow-up `20261027195711-690e1fcc`: all six steps, first attempt, 823.781s; native 700.621s with internal recovery and one false-negative verification before a separate read-only check succeeded. |
| WIFI-01 | not_run | not_run | Port-conflict case has no result on this APK. |
| WIFI-02 | not_run | not_run | Wireless reconnect case has no result on this APK. |
| WIFI-03 | not_run | not_run | Competing-client case has no result on this APK. |

## Calculator stability and locating comparison

Three independent frozen-baseline runs are retained separately; automatic case retries
are disabled. Native task time includes user-input pauses and internal recovery.
Observer time also includes test setup and replay inspection.

| Run | Full case | Native task | Observer case | Internal evidence |
| --- | --- | --- | --- | --- |
| 1 (`20261027182343-c03fd2d9`) | failed | succeeded, 563.970s | 664.365s | Four failed digit-2 locations in the final operation; expression was corrected before 5192÷4=1298. Three native reports exported. |
| 2 (`20261027183853-d23d993a`) | passed | succeeded, 378.057s | 651.052s | First attempt, zero case retries; all three operation replays and final chat assertion passed. One failed digit-2 location recovered internally; 53 inner model calls / 275.713s cumulative model time. |
| 3 (`20261027185358-b0379a74`) | failed | failed, 627.044s | 738.785s | Native task remained incomplete despite actual 5192÷4=1298; final verification rejected absent earlier addition history. Observer correctly failed step 5. Four failed digit-2 locates, 108 inner model calls / 475.829s model time. All three reports exported. |

Run 1's last assertion saw Replay instead of the main chat, so it could not verify
the completion badge. The framework failure is retained. Returning to chat afterward
showed the amended goal, correct 1298 reply and Task completed; this is supplemental
verification, not a replacement first-attempt pass. Commit `4390976` explicitly
returns to chat before the independent assertion. APK/model remain unchanged.
The measurement helper initially raised an AttributeError after saving the correct
framework status and JSON; its exit handling was corrected for subsequent runs.

Run 1 reports: `calculator-1-native-1/report.md`, `calculator-1-native-2/report.md`,
`calculator-1-native-last/report.md`. They contain 86 inner model calls and 409.734s
of cumulative model-call time. The last operation alone took 473.826s / 83 calls /
388.467s model time, with four explicit failed digit-2 locations whose cropped
view contained digit 5. The earlier Tap/Plan errors were the user-input pause
guard rejecting an obsolete action, not a failed digit location. Passive frames
0060 show 128×37=4736, 0074 shows the 5192 intermediate preview before expression
repair, and 0121 shows the actually evaluated 5192÷4=1298. The chat completion
screenshot is `screenshot-1793097382193.png` in the system temporary directory.

Run 2 exported all three native reports as `calculator-2-native-{1,2,3}/report.md`.
The last operation took 301.822s / 50 calls / 259.790s model time, including one
failed digit-2 location. The initial operation was interrupted by the intended
user-input guard. Both model work and internal recovery remain part of native
wall time; observer replay inspection alone took 218.920s.

Run 3 reports `calculator-3-native-{1,2,3}/report.md` confirm the latest amendment
was present in the final screenshot query. That query observed 5192÷4=1298 but
returned `success:false`; the native reply cited missing earlier addition history.
This was not a lost amendment or an observer false failure. The last operation
took 536.152s / 104 calls / 448.060s model time, with four failed digit-2 locates.
All three runs are retained: one complete case pass, one observer failure and
one native verification failure. These are not three consecutive clean passes.

The locating comparison uses native YAML `aiLocate`, the same phone, APK, model,
account, digit-2 prompt and keypad view. Three pairs alternate order. One policy
always uses `deepLocate`; the other starts ordinary and upgrades only after a
reported failure. Returned coordinates are independently checked against the
visible key, so a successful API response at the wrong point still fails the
quality check. This read-only component comparison does not by itself establish
whole-task latency or justify weakening final screenshot verification.


Native locating comparison on the frozen e480 APK completed in task
`0f226b1e-bce9-482b-adb7-1f65253e550e` (53.815s, six serial cases).
Reports are `locate-native-{1..6}/report.md`; `cacheable:false` and the actual
`deepLocate` option are present in each converted report. The independently
observed digit-2 center is (375, 1544), with a conservative 90px acceptance radius.

| Pair / policy | Report elapsed | Model time / calls | API result | Independent quality |
| --- | --- | --- | --- | --- |
| 1 / always deep | 15.992s | 13.705s / 2 | failed | no target returned |
| 1 / ordinary first | 4.189s | 3.380s / 1 | finished, (375, 1328) | wrong key: 5 |
| 2 / ordinary first | 4.878s | 2.889s / 1 | finished, (375, 1327) | wrong key: 5 |
| 2 / always deep | 7.928s | 5.917s / 2 | failed | no target returned |
| 3 / always deep | 8.470s | 6.569s / 2 | failed | no target returned |
| 3 / ordinary first | 5.404s | 3.323s / 1 | finished, (375, 1327.5) | wrong key: 5 |

Both modes scored 0/3 correct locations for this prompt. Ordinary locating never
raised an error, so an SDK-error-only upgrade policy would not activate; no
fallback result is invented. Deep locating rejected the incorrect initial crop
instead of returning an unsafe target. Median component elapsed times (8.470s
versus 4.878s) are **not successful-path speedups**, and this is not a whole-task
latency A/B. The production recovery/default policy is unchanged. Independent
post-action verification remains required.

The first benchmark setup failed before valid samples: the observer's full ASCII
ADB text command produced incomplete editor contents. Selecting all and using
Midscene's Unicode/YADB paste path wrote the complete six-case fixture. A separate
observer CLI launch used Android `-S`, force-stopping Tapilot and dropping its
Accessibility service; this was restored through system UI. Subsequent readback
uses `am start` without `-S`. These setup/recovery times are outside native locating
measurements and are not product locating results.

## Follow-up verification build

The new APK is `665acece6ba27f46e488cb4c0b49f3c217ae77eb2e0dcc27d0038bdcb5b87747`;
installed base.apk readback matched. It retains the original signing certificate,
model, account and VPN. The frozen-baseline results above are not relabelled as
passes on this new build.

Commit `50433a3` moves the YAML entry; the screenshot-criteria change is validated
separately below.

Commit `6d41c85`: screenshot criteria now expose the latest user requirement and its precedence,
retain all non-conflicting requirements, and keep the model's intermediate-step
condition as a proposal rather than promote it to an authoritative required
outcome. A valid negative screenshot judgment still is not retried or overridden.
A regression test checks that superseded requirements cannot be promoted by this
contract transformation. Full `pnpm check` passed, now with 127 Node tests.

The YAML menu entry also moves immediately after New conversation. With the
existing long history, the old entry required scrolling to the very bottom and
the observer exhausted 20 planning cycles trying to reach it. Android unit tests,
Debug/Beta lint, beta build and APK signing/runtime integrity checks passed after
this UI-only follow-up. On the installed build, `screenshot-1793101315022.png` shows YAML immediately
below New conversation while preserving the same long history; no scrolling is
needed to reach it.


The fixed-build screenshot probe ran once as native task
`34e20f75-4d1f-40d0-9cef-9087670f2893` (47.486s). On the unchanged actual
5192÷4=1298 screen, three identical checks containing the original requirement
and final-result-only amendment returned true; a divide-by-8 negative control and
an incorrect-grouping negative control both returned false. The converted report
`verify-precedence-native-1/report.md` contains five distinct `gpt-6-luna` calls
(33.151s cumulative model time; report elapsed 37.664s). No action was repeated
and no valid negative result was retried. This is component evidence, not five
whole-task passes.

The ordinary-locate report also records raw model bbox `[279,1232,471,1424]`
against a 988×1972 screenshot, directly covering key 5. Its returned center is the
same box center, so this captured failure is already present in model coordinates;
it is not introduced by a later coordinate transform.


Strict UPDATE-02 on 665a passed all six steps in
`20261027195711-690e1fcc`: one attempt, zero automatic retries, observer elapsed
823.781s and native task `0dd59fdf-c228-4abe-8e16-9b3309e42f1f` succeeded in
700.621s. The native interval includes the input pause and recovery. Passive
frame 0068 records the incorrect ungrouped 4736+456÷4 preview (4850); the task
then cleared and recalculated rather than accepting it. Final chat independently
shows 1298 and Task completed (`calculator-after-fix-1-final.png`). This is one
complete current-build pass with internal recovery, not three clean passes.


All four current-build native reports were exported and converted as
`calculator-after-fix-1-native-{1,2,3,4}/report.md`: 103 inner model calls,
523.909s cumulative model time. The main operation took 582.970s / 98 calls /
492.984s model time, including two failed digit-2 locations. The first operation's
Locate/Plan failures were the input-pause guard, not localization failures.

A remaining verification limitation is explicitly retained: report 3 still
returned false on actual 5192÷4=1298 because the proposed step mentioned displaying
the expanded expression. Both user requirements and the new precedence rule were
present. The conversation then requested a separate read-only check with a
final-result condition (report 4), which returned true. The verifier did not
internally retry or override a valid negative result, and no further calculator
mutation was needed. Thus the component probe passed and the complete task
recovered, but the prompt change does **not** eliminate every model false negative.
This and digit localization remain open stability limits; no error-free or
latency-improvement claim is made.


The two-case cross-flow regression on the same 665a APK passed 2/2 in
`20261027201545-7edfda27`, first attempt for each, zero automatic retries:

- TASK-01: all six steps, 255.518s. Old task `d1a0ba91-99de-499d-a030-5cb7867a4f8c`
  stopped in 29.124s; new Settings task `3df52549-16dc-4aad-9ee4-7e754d7a96e3`
  succeeded in 50.732s. The observer checked both statuses and the Settings replay.
- LONG-02: all four steps, 416.151s, including 153.733s of reply/replay inspection.
  Native task `b49a7dec-4817-4845-a7e7-c65574bb9aa6` succeeded in 210.391s.
  Independent passive frames 0056, 0067 and 0076 in `key-after-fix-frames/` show
  Android 14, storage 55.9/256 GB and auto-brightness on. Reply frame 0081 matches
  all three; the observer inspected each of the three operation replays.

Current 665a coverage is 3 passed, 0 failed, 1 blocked and 19 not_run out of 23.
The three draft-case passes belong to e480 and are not silently promoted. The
three independent frozen-baseline Calculator runs still contain two failures;
the additional 665a pass includes internal recovery and a verification false
negative. These results do not establish overall model stability convergence.


After verification, the original YAML source was restored and Advanced YAML mode
was visually confirmed off. Twenty owned Download report copies (108,826,502
bytes) were removed only after verifying their local files and converted reports;
internal task history and unrelated downloads were retained. Read-only capture
and performance-log processes were stopped. The original saved Android Use
pairing was reconnected in steps mode, with a fresh Computer connected screenshot
and successful Back/readback to the completed chat; speech remained enabled.
Restoration report: `394c2176-81f0-4162-af09-5e1789ac6c70`. The connection and
preview were left open. This is a restoration check, not a WIFI suite pass.

## Round-nine verification comparison

The existing eleven commits were pushed to
`origin/fix/conversation-recovery-validation` at `083f30a` before this round.
The phone still uses the same 665a APK and gpt-6-luna. A read-only comparison
kept the evaluated Calculator screen at 5192÷4=1298 and alternated the current
verifier prompt with a candidate that resolves a separate `requirement` field
before judging the screenshot. The intermediate-step proposal deliberately
repeats the expanded-expression wording from the captured false negative.

Native task `7af9bf5f-2ff4-4813-9fe1-e6fed3320a63` completed eleven queries in
152.286s. Converted report
`/tmp/tapilot-bug-audit-round9/verification-probe-native-1/report.md` records
eleven model calls / 82.638s model time. HTTP instrumentation separately records
thirteen requests / 132.386s, including two failed transports (5.120s and
45.000s) and their recovery. These are not eleven fault-free network requests.
An earlier fixture attempt was rejected before any model query because
`aiQuery.options.cacheable` is not in the current schema; it is retained as a
test-setup failure, not a product-verification failure.

| Probe | Current prompt | Candidate with resolved requirement |
| --- | --- | --- |
| Positive pair 1 | true | true |
| Positive pair 2 | false | false |
| Positive pair 3 | false | true |
| Wrong divisor | not run | false |
| Wrong grouping | not run | false |
| Explicit expanded-expression display missing | not run | false |
| Requested multiplication stage missing | not run | false |
| Requested Settings page missing | not run | false |

The current prompt accepted only 1/3 correct screens; the candidate accepted
2/3, while rejecting all five negative controls. The failed candidate already
transcribed 5192÷4=1298 correctly and resolved the amendment but still returned
false. The candidate is therefore not merged on this evidence. A successful YAML
task means the queries ran, not that every verification judgment was correct.
These are component checks, so the complete-case coverage table is unchanged.

A second candidate removed duplicated guidance and required a resolved arithmetic
expression and expected value. In task `5cee4b8f-8b46-4613-aa71-232d66011b94`,
the alternating current prompt again accepted 1/3 positives, while the shorter
candidate accepted 0/3. All five negative controls were rejected. Each candidate
positive computed 4736, 5192 and 1298 correctly, but still promoted the proposal's
expanded-display wording into a requirement. One negative-control explanation
also computed the unrelated expression incorrectly (4844 instead of 4850); its
false verdict was correct, but its arithmetic explanation was not.

The compact candidate is also rejected, not merged. The task took 111.454s;
`compact-probe-native-1/report.md` records eleven calls / 100.375s model time,
and HTTP timing records eleven requests / 99.910s with no transport failures.
Both reports and passive frames remain under the round-nine artifact directory.

A third comparison changed only the positive contract scope to
`entire_user_goal`, using the existing production verifier. This removes the
model proposal from its visible-result predicate. Task
`ff45cbd3-d260-4d56-bf66-80ffbe60ce9c` still accepted only 1/3 positives, versus
0/3 for the alternating intermediate-step control. All five negative controls
were rejected. Two whole-goal judgments still demanded the superseded intermediate
checks. Thus selecting whole-goal scope alone also fails to solve the problem;
the intermediate-step safeguards for multi-page work remain unchanged.

The third task took 104.555s; `scope-probe-native-1/report.md` records eleven
calls / 93.248s model time, while HTTP timing records eleven requests / 92.769s
with no transport errors. A temporary local scope-routing regression confirmed
that plans force intermediate scope, but its proposed override was removed after
the live comparison failed to establish the intended repair. No production
prompt, scope, model, verification guard or installed APK changed in this round.

Across these three separate configurations, 33 queries ran on the physical phone.
Each candidate rejected its five negative controls, but none reliably accepted
the correct amended final result. The results narrow the remaining problem to
requirement interpretation independently of action/localization errors. They do
not establish convergence or a latency improvement. Further work should evaluate
resolving the amended requirements before the visual judgment, with preservation
of non-conflicting requirements tested separately; another prompt-only change
should not be merged without stronger positive and negative evidence.

Round-nine cleanup restored the original YAML and visually confirmed Advanced
YAML mode off. The three owned Download exports (17,709,011 bytes) were removed
after SHA-256 checks against retained local copies and successful report
conversion. Passive capture and performance logging were stopped. Installed
`base.apk` was hashed again and still matched 665a. The original saved Android
Use connection was restored in steps mode; fresh screenshot, Back actions and
chat readback succeeded, with speech still enabled. The connection/preview remain
open. Report `e78d6369-d6a7-45ee-92aa-2dd06e703a1f` explicitly records the overall
verification investigation as unresolved and distinguishes that verdict from
the successful device restoration. This round changes only this evidence ledger;
the previous full build/test gate is historical, not a newly repeated gate.

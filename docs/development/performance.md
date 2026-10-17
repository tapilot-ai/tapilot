# Native task performance

Measure the phone's own Pi → model → Midscene → AccessibilityService task loop.
The host benchmark observer must not perform the requested Settings action.

## Model-free regression benchmark

```sh
pnpm runtime:bundle
node scripts/benchmark-conversation.mjs
```

Five samples execute 120 successful stubbed phone operations and 121 model turns.
Each emitted checkpoint is serialized as it is for the native transport. The
default invocation fails if the execution or checkpoint counts regress. PR Checks
uploads the JSON alongside test results.

Measured on the development Mac on 2026-10-14:

| Metric | Before | After |
| --- | ---: | ---: |
| Checkpoints | 364 | 243 |
| Serialized checkpoint bytes | 66,059,657 | 43,939,358 |
| Median loop + serialization time, five samples | 175 ms | 152 ms |

Pi commits each user, assistant and tool-result message at `message_end`.
`turn_end` then repeats the already-committed transcript. Removing that duplicate
reduces full snapshots by 33.2% and transport bytes by 33.5%, while retaining every
message needed for crash recovery, steering and tool-result verification. Host
elapsed time varies with load. These are **not** physical-phone, model-network or
native-storage timings, and do not establish an end-to-end speedup percentage.

The launcher catalogue also uses compact `[label, component]` tuples in model
prompts. Component prefixes retain package identity, all launcher entries and
ambiguous labels remain available, and names remain data rather than instructions.
On the tested OPPO, 52 entries shrink from 6,979 to 4,018 UTF-8 bytes (42.4%).

## Observed OPPO results, 2026-10-14

Device: PEUM00, Android 14; native model: `gpt-6-luna`. Installed APK SHA-256:
`2386c77a61c989505f68b57427ec2e15ae15372d55e2822f490f17a76948ccdd`.
This is a dirty local checkout at `54bebb8ed7228df7df0ac6482b1d78363df24d06`,
not exact-head hosted CI evidence.

Three sequential samples passed, including the original native final-screen
assertions and one real phone operation per sample:

| Sample | Total wall time | Runtime preparation |
| --- | ---: | ---: |
| First after updating runtime assets | 38.220 s | 6.265 s |
| Second | 35.068 s | 0 ms |
| Third | 32.689 s | 1 ms |

p50 is 35.068 s; observed p95/max is 38.220 s. Across the three samples:

- Six outer conversation-model calls: 25.281 s total.
- Six Midscene planning calls: 35.954 s total.
- Three final-screen assertions: 14.986 s model time total.
- Eighteen native captures: mean 488 ms, maximum 927 ms.
- Three actual launch actions: mean 50 ms, maximum 57 ms.
- Runtime startup: mean 672 ms.

Model requests dominate the task; native input is already a small fraction.
The model input is already JPEG quality 90, prepared by Midscene from the native
PNG capture. See [screenshot compression measurements](image-compression.md)
for payload, encoding and live-provider comparisons at reduced resolutions.
The exploratory pre-catalogue task took 46.208 s and its final-screen assertion
passed. Its obsolete harness reply-marker check failed, so it is not counted as a
passing CI sample. First planning input was 13,680 tokens in that exploratory run
and 13,071/13,065/13,062 in the compact-catalogue run. Task wording, cold setup,
provider latency and cache hits also vary; these samples do not establish a causal
end-to-end speedup percentage. Snapshot and catalogue size reductions are exact.

## Physical device benchmark

Build and install the main APK and the separate Android test APK, preserving app
data. Only the test APK contains the harness. Use the existing native model login,
an unlocked arm64 Android phone, enabled Tapilot AccessibilityService, and no
concurrent local/wireless task. Install the test APK with `adb install -t -r`.
Some OPPO devices show a visible installation confirmation for a new test APK.

```sh
TAPILOT_E2E_DEVICE_ID=exact-device-serial \
TAPILOT_PERF_SAMPLES=3 \
TAPILOT_PERF_APK=/absolute/path/to/the-installed.apk \
node scripts/run-device-performance.mjs
```

The harness submits the same read-only task in a new conversation for each
sample: open Android Settings and verify the main page. It requires a successful
native task, at least one verified phone operation, a final answer and actual
native HTML execution reports. The host parses those reports and requires a
successful `Assert` task with `output=true` for every sample. The app's ordinary
Midscene `aiAssert` verifies its final visible state.
Instrumentation can leave an already-authorized accessibility service in Android's
crashed set after restarting the target process. Setup rebinds only that existing
service before measuring; it does not grant an unapproved service.
On OPPO firmware that denies shell `WRITE_SECURE_SETTINGS`, set
`TAPILOT_PERF_MANUAL_ACCESSIBILITY=1` and toggle the already-authorized Tapilot
service off/on in Android Accessibility settings while setup waits (up to two
minutes). Setup time is excluded from task samples. An unattended CI device must
permit the test-only rebind; a manual setup timeout fails the run.

`build/device-performance/` contains:

- `identity.json`: exact serial, model, ABI, Android SDK, repository head/dirty
  state and installed APK SHA-256. A supplied APK must match the installed hash.
- `native/summary.json`: per-sample wall time, model turns, operations and stage
  aggregates, including failures. Each invocation has a unique run ID; an old
  successful artifact cannot satisfy a new invocation.
- `native/*.html`: original Midscene reports with visible execution evidence.
- `result.json`: total wall-time p50/p95/max, outer model/phone phase aggregates
  and inner capture/gesture RPC mean/max/count/failures, plus actual Midscene
  model request times and token counts. Repeated report tasks are deduplicated.
- `instrumentation.txt`: test-runner status, without model credentials.

To recollect an existing run without submitting/replaying its task, pass
`--collect-only <exact-run-uuid>` to the same runner with the same device, sample
count and installed APK. The UUID is in `native/summary.json`. Collection checks
the remote UUID, replaces only the runner-owned local `native/` artifact folder,
and then validates its reports; `adb pull` must not merge stale HTML assertions
into a new run.

Runtime startup/prepare, `model.complete` and `phone.execute` use monotonic native
time. Native RPC timing includes capture/encoding/stdio transport and gesture
acknowledgement, not just kernel input. The outer `phone.execute` interval contains
the inner native RPC intervals: do not add these overlapping totals. Midscene
inference and assertion timings remain in its original HTML report.
Timing logs use only the `TapilotPerformance` tag and aggregate numeric fields.
No request text, token, image or provider response is added to timing records.

Use at least several samples with the same phone, display mode, model/provider,
network and APK configuration. With three samples, p95 is the maximum observed
sample, not a statistically stable service percentile. A timeout or failed visible
assertion fails the benchmark; uncertain actions are never automatically replayed.

## CI

`.github/workflows/device-performance.yml` is a manual workflow for a dedicated
runner labelled `self-hosted, tapilot-android`, with an unlocked **arm64 Android
phone** attached. Android SDK/ADB and the already-authorized Tapilot accessibility
service must be available. The current APK embeds only arm64 Node, so Midscene's
x86_64 Linux emulator workflow cannot run this product's native runtime unchanged.
Tapilot currently has no iOS app; there is no iOS product performance job.

The workflow follows the device isolation, serialized execution, Node connection
policy and always-upload-report approach of the sibling Midscene Android/iOS
workflows. Configure the `device-performance` GitHub environment:

- Secret: `MIDSCENE_MODEL_API_KEY`.
- Variables: `MIDSCENE_MODEL_BASE_URL`, `MIDSCENE_MODEL_NAME`,
  `MIDSCENE_MODEL_FAMILY`, `TAPILOT_E2E_DEVICE_ID`.

This key drives the **phone's native task model**, not a host observer. CI passes
it through stdin into a private debug-app cache fixture, removes the fixture after
loading, and removes the provisioned custom credential at teardown. Provisioning
requires both `TAPILOT_PERF_PROVISION_MODEL=1` and
`TAPILOT_PERF_DEDICATED_DEVICE=1`; do not provision a personal phone this way.
No secret is placed in command arguments or artifacts.

The workflow builds debug and test APKs, installs with replacement without wiping
data, runs samples serially, and uploads evidence even on failure. Set
`max_wall_ms=0` to collect an initial baseline. After measuring on that runner,
choose a per-sample ceiling to fail regressions. Ordinary PR CI runs only the
deterministic model-free benchmark; it does not claim device/model validation.
Workflow configuration and local checks do not prove a hosted CI run completed.

## Wireless steps: post-action observation (2026-10-16)

On an OPPO PEUM00 (Android 14), the host opened Display & brightness from a
Settings list where the entry was visible, then read automatic brightness without
changing it. The baseline steps worker returned a pre-transition screen after the
tap, requiring another host screenshot call. Screenshot-only calls also captured
twice. Version 0.5.11 reuses the first screenshot and observes after step actions
until a changed image stabilizes or the observation budget expires. This is pixel
stability, not a business assertion. Actions are never replayed by the observer.

| Local MCP host run | Wall time | Initial screenshot call | Tap + observation call | Extra host screenshot calls |
| --- | ---: | ---: | ---: | ---: |
| Baseline, one sample | 61.951 s | 1.495 s | 0.863 s | 1 (1.310 s) |
| Optimized 1 | 25.057 s | 0.711 s | 2.126 s | 0 |
| Optimized 2 | 22.620 s | 0.623 s | 2.109 s | 0 |
| Optimized 3 | 24.643 s | 0.623 s | 2.048 s | 0 |

All three optimized responses visibly showed automatic brightness enabled. Median
host wall time was 24.643 s. Each used one tap, one initial capture and three
internal post-tap captures, versus five total captures across three host calls in
the baseline. Tap responses deliberately take longer to avoid another host/model
round trip. This covers **steps mode**, not AI planner or manual-panel behavior.

These are sequential exploratory samples, not a randomized benchmark or a causal
speedup percentage. They include host model decisions, file-image viewing and
local MCP orchestration; the baseline also included an extra polling wait.
Setup, app launches, connection isolation and report-verdict recording are
excluded. No APK was changed between samples. The new code was tested through
the repository MCP server, not a reloaded native ChatGPT panel. Three successes
are not sufficient to claim general reliability or a stable p95.

The worker returns `timing.totalMs` and per-method `timing.rpc` counts/durations.
They exclude host model inference, IPC worker startup and parent report handling.
`observation.state` is `stable`, `unchanged` or `timeout`; only the host's inspection
can establish task completion. The 1.5-second observation scheduling budget does
not cancel an already-running capture, which retains the transport timeout.
Keep timed-out/failed samples in future comparisons and compare both elapsed time
and whether the returned frame was actually useful. Never infer success from the
stability flag alone.


### Localized changes regression (0.5.12, 2026-10-16)

A second task (calculator 7 × 8 = 56) exposed a regression in 0.5.11:
the whole-screen 1% threshold missed individual digits/operators, resulting in
`unchanged` and a full observation wait. The observer now also detects five
changed pixels within an 8×8 tile of its 64×96 comparison image. It retains
status/navigation exclusion and the pixel intensity tolerance. Initial/inter-frame
waits are 100/75 ms rather than 250/150 ms. A changed frame must still be followed
by a stable frame. This is a hint, never a semantic completion assertion.

OPPO e7ed3f17, same calculator task and five timed MCP calls, one sample per version:

| Step | 0.5.11 | 0.5.12 |
|---|---:|---:|
| Initial screenshot | 603 ms | 520 ms |
| Tap 7 | 2208 ms (unchanged) | 1419 ms (stable) |
| Tap multiply | 2320 ms (unchanged) | 1490 ms (stable) |
| Tap 8 | 2227 ms | 1406 ms |
| Tap equals | 1744 ms | 1882 ms |
| Total tool time | 9102 ms | 6717 ms |

All screenshots were inspected before the next action; both runs visibly ended
at 56 with no extra screenshot. The total is 26.2% lower in this exploratory
sample, not a whole-task speedup guarantee. The equals action needed three samples
in the new run, demonstrating that animation still delays completion. Settings →
Display & brightness also returned the correct new page directly (2243 ms, three
observations); this navigation sample does not demonstrate an improvement over
the previous 2048–2126 ms measurements.

The independent phone-mode sample remains a separate result: final 56 observed
within 132 s, completion observed within 170 s; it includes launch/clear/planning
and different model configuration. This plugin observer change does not accelerate
the native planner. Whole-task native phase timing is unavailable from the installed
release build, so no native speedup or precise inference bottleneck is claimed.

Evidence: `artifacts/calculator-ab-20261016/local-change-result.json`, original
per-call PNG/JSON files and the `calculator-local-change` / `local-change-navigation`
Midscene reports. Local checks: all 74 plugin tests and generated UI consistency
passed, including localized animation, isolated noise, delayed transitions, unchanged
screens and capture failure without action replay.


## Native latency attribution and planner optimization (2026-10-16)

The archived calculator task was subsequently recovered through a temporary,
upgrade-compatible diagnostic build. Its exact created/completed timestamps give
148.569 s, superseding the earlier observation bounds. The configured model was
`gpt-6-luna`. Seven outer model requests consumed 38.295 s; three phone operations
consumed 101.752 s; preparation/startup/discovery/other orchestration consumed the
remaining 8.522 s. Nested report timings must not be added to those totals.

Within the operations, six planning model requests consumed 43.425 s and five
forced separate location model requests consumed 19.219 s. Native capture calls
were only 6.790 s and native execution calls 0.625 s across all operations. The
transcript also proves that an already verified calculation was queried again.
Model timings include network/provider waiting, not server-only inference.

The default execution policy now uses fast visual planning and leaves deepLocate
unspecified so SDK planning coordinates can be reused while retaining normal
location fallback. Explicit deepLocate remains supported. Simple single-app tasks
may combine launch and interaction in one operation; verified tool results are
marked as independently checked so the outer model can finish without redundant
verification. The independent final screenshot query and failure gates remain.

First optimized diagnostic run: 86.502 s, three outer model requests (19.128 s),
one phone operation (63.687 s), five successful taps, one final visual query.
Location model calls fell from five to zero; planning still consumed 42.465 s.
This sample also saved 6.053 s of runtime preparation through a warm runtime, so
the full 41.8% observed reduction is not solely attributable to code. Exact
per-phase logs now include numeric start/duration, request index and a trace UUID.

See `artifacts/native-profile-20261016/README.md` and its archived task records,
original HTML reports and deduplicated analysis JSON for evidence and limitations.
Validation: 45 runtime tests passed; Android unit tests had zero failures/errors
(245 tests, one skipped). The beta APK preserves the installed local signing
identity and disables debugging; it has not been approved for distribution.

Packaged beta repeat: the normally installed, non-debuggable APK completed the
same task with a conversation-engine duration of 94.228 s (not exact full-task
wall time). Preparation was 6.510 s; three outer model requests took 14.430 s;
one phone operation took 72.166 s. The exported HTML confirms five successful
taps, zero separate location model requests and one successful visual query.
Eight planning requests still took 51.365 s; inner app search/launch planning
remains a candidate for further optimization. Both optimized runs retained
independent screenshot verification. The report is archived at
`artifacts/native-profile-20261016/optimized-beta/report.html`.

### Launch handoff and verification context

A subsequent trace showed the direct launch node followed by redundant inner app
search/launch. Generated act tasks now communicate the completed launch and ask
the planner to inspect the screen, perform the remaining requested interactions,
and recover if the target app is not visible. An existing desired result is not
a substitute for explicitly requested new interactions.

The first beta sample after launch handoff took 71.525 s engine time with six
planning requests instead of eight, no inner search/launch, five taps and one
successful final query. A repeat took 144.049 s after a planner failure and a
verification failure followed by recovery. The latter screenshot query rejected
the visible 56 because it could not prove earlier clear/key order. Thus the first
fast run is not a stable latency claim. Final screenshot queries no longer
include the procedural instruction as observation context; they retain the final
visible success condition, strict boolean outcome and independent reading.
Failed results are never converted to success. Further evidence is archived in
`artifacts/native-profile-20261016/README.md`, including the slow sample.

Post-verification-context beta sample: 71.133 s engine time, one successful phone
operation (56.786 s), three outer requests (13.270 s), six planning requests
(38.887 s), five taps and one successful final query. Runtime was warm. This is
one sample; no generalized reliability or stable speed claim is made.

### External Android Use comparison

`dongsenlin/android-use` at e8ed77d32277fb1a88626d97779b2fa106717e5c was built and
tested unchanged on the same OPPO. It is a computer-hosted agent with a phone
AccessibilityService, not an independent phone planner. It rejects the current
100.82.x.x Wi-Fi network; its documented USB forwarding fallback was used.
Five selector taps with per-step screenshots took 8.858 s of tool time; a
known-layout batch with final-only observation took 2.808 s. The first run's
launch-to-final-screenshot span was 51.193 s including host orchestration.
These scopes/transports differ from native engine duration and are not a fair
end-to-end speed ratio. Full evidence and limitations are in
`artifacts/android-use-comparison-20261016/README.md`.


### Catalog prefetch, connection pooling and transport boundaries

Native planning now receives bounded installed launcher candidates in its initial
context. Unambiguous components avoid a separate find_apps inference round trip;
missing/ambiguous candidates retain discovery and native resolution checks. Labels
remain untrusted data. The outer HTTP client no longer forces Connection: close;
a real socket test verifies connection reuse. Numeric transport telemetry preserves
streaming/cancellation and logs neither request content nor credentials.

OPPO beta samples: 7×8 cold full task 70.600 s (engine 70.497), warm full task
59.383 s (engine 59.285). Both use two outer requests instead of three. Compared
with the previous warm 71.133 s engine sample, the observed reduction is 16.7%,
not an isolated causal estimate; model/network variability contributes.

A changed 9×6 task succeeded in 65.540 s full-task time, including 5.893 s fresh
bundle preparation, with five visual taps and independent final query. Its seven
visual HTTP requests total 35.804 s. Undici measures 0.544 s before body-sent,
16.484 s from body-sent to headers, and 18.731 s from headers to completion.
Thus upload is not the principal delay in this sample; post-upload model/network
waiting dominates. Client timing cannot separate service queue/inference from
network/proxy delivery. The next optimization target is repeated model requests
and context, with screenshot verification retained.

Evidence: `artifacts/native-root-latency-20261016/README.md`, original reports and
numeric traces. Validation: 49 runtime tests passed; Android 247 tests, zero
failures/errors, one skipped; beta APK verification and physical-device execution
passed. Local signing only; no publication or GitHub CI was performed.


### Complete verified single-operation tasks without a summary model call

`operate_phone.finishWhenVerified` is optional. The model may select it only
when one operation covers the entire goal and its actual final visual observation
is sufficient as the answer. Direct completion requires a successful nonempty
observation, exactly one tool call, first phone operation, no multi-step plan or
resume, no queued user input and no cancellation. Other cases retain the normal
model loop. The final screenshot query is unchanged. Cancellation and late user
steering are covered by tests.

Functional requests now ask for the actual visible control label/state instead
of inventing exact UI text; explicit exact-label requests remain strict. Mode
routing explicitly treats subpage navigation/search/scroll as act, including
read-only requests. This removes conflicting advice to observe first after
opening an app.

OPPO calculator warm: 56.686 s full (previous 59.383 s); outer requests 2 → 1,
outer time 10.657 → 4.994 s, phone operation 47.621 → 50.698 s. Five actual taps
and independent result query remain. Navigation baseline took 56.524 s with a
label mismatch/retry. Intermediate sample regressed to 113.487 s from an initial
observe routing failure plus slow navigation recovery. After routing correction:
48.230 s including 5.406 s runtime extraction, one outer request, one successful
operation and final query. These are individual samples, not stable speed ratios.

Navigation recovery also exposed an old Settings frame shortly after a tap and
subsequent transition-frame actions. Adaptive visual settling needs separate
validation; this work does not claim to solve it. All samples, including the slow
one, are retained in `artifacts/native-terminal-20261016/README.md` with reports,
screenshots and phase logs. Final checks: 59 runtime tests passed; 247 Android
tests with zero failures/errors and one skipped; installed beta verified locally.


### Adaptive native screenshot settling experiment (2026-10-16)

The native bridge now observes fresh post-action screenshots for a changed,
stable pair, with a 1500 ms observation budget. It preserves latest frame IDs,
never retries actions and retains final visual verification. Five regressions
cover delayed navigation, unchanged pages, continued motion, capture failure and
transient press feedback. Numeric capture-settling and selected outer-model
telemetry were added; model/planning configuration was unchanged.

The new baseline completed in 43.453 s. A successful updated cold run lost its
ADB timing stream and still required navigation recovery. The updated warm run
failed at the outer model request before any phone operation (31.963 s full).
Wi-Fi also changed between samples. Consequently these runs do not establish a
speed improvement or full resolution of transition-frame mistakes. Evidence and
limitations: `artifacts/native-settling-20261016/README.md`.

Historical native visual reports request/return gpt-6-luna, but earlier effort
and deepLocate settings changed. New outer requests log gpt-6-luna with provider
default reasoning effort. Host-Codex plugin/XML/comparator runs are not verified
to use the same model; their timings must not be presented as a controlled
same-model end-to-end ranking.


### Half-latency target and current connectivity boundary

Target: reduce a comparable successful task median by 50%, using the same model,
reasoning configuration, device, network and initial state. The 43.453 s single
sample suggests a provisional 21.727 s target, not an established baseline
median. Keep independent final visual verification and retain failed runs.

The next candidate reduces redundant model-written planning/log output and uses
short memory deltas for observed facts, preserving requested final observations.
This changes prompting, not model selection or final verification. It is not a
measured speed improvement. Full checks passed before this candidate; focused
runtime checks validate it separately. A fresh OPPO connectivity probe requesting
only OK failed before any phone operation: 31.041 s full, 30.060 s HTTP elapsed
without recorded response headers. Desktop OAuth preflight also requires login.
Controlled performance acceptance remains pending working model connectivity.

The 50% target is specifically for the standalone Android app's full task wall
time, not desktop plugin latency. Native Responses decoding now retains actual
returned model and numeric token usage; outer timing logs expose these without
response text or credentials. `scripts/native-latency-summary.mjs` compares at
least three samples per variant and requires equal task/device/network/start
state/model/reasoning/warmth metadata. Failed or unverified runs invalidate a
half-latency claim even when the successful-run median is low.

The October 16 follow-up identified a phone network prerequisite: direct IPv4 and
IPv6 TCP connections timed out. Starting the existing phone VPN allowed TCP but
TLS still failed, including a general HTTPS probe. A native OK-only task failed
before any phone action (10.894 s after network change). These are connectivity
failures, not performance gains. Original proxy selection and stopped state were
restored after diagnosis. Latest compact-output runtime and model telemetry beta
were built, locally verified and installed on OPPO; live speed acceptance is
still pending a working phone model connection. Desktop plugin login is not a
prerequisite for this native-only target.

### Restored native baseline and bbox retry root cause (2026-10-16 evening)

After the user updated the phone VPN subscription and selected a working region,
a native OK-only request succeeded. Three subsequent native Settings tasks used
actual gpt-6-luna for outer and visual requests, full-resolution screenshots and
independent final visual queries. Settings was reset to its root before each run.
The task navigated to Display and brightness and read auto-brightness without
changing settings. Full wall times were 84.016, 50.960 and 55.119 s, all verified
successful; median 55.119 s gives a 27.560 s half-latency target for this task.
Same conversation was reused, so outer input grew slightly across samples;
sequential network variance remains a limitation, not an optimization benefit.

All three runs made an unnecessary semantic retry before the first scroll:
the model supplied a bounding box ending at y=1972 on a 1972-pixel image, while
validation required every coordinate <=1971. Private planning logs confirm the
same error in each run. The retry request plus wait took approximately 7–19 s.
The deployment patch accepts width/height only as pixel bbox far-edge limits;
the existing mapper clips the rectangle to valid pixels. Points, bbox origins,
normalized ranges, reversed boxes and larger overflow remain rejected. ESM and
CommonJS codec regressions cover this boundary.

Patched runs completed in 47.743, 36.958 and 37.611 s, each with four visual
requests and an independent successful final Query (baseline: five requests).
The median decreased to 37.611 s, a 31.8% observed reduction. The semantic retry
is eliminated; sequential network variability means the entire difference cannot
be attributed to code. The 50% target is not reached. Both variants requested
and returned gpt-6-luna for every recorded outer and visual call.

All 65 runtime tests and the complete local `pnpm check` passed. The patched
non-debuggable beta was verified and installed on OPPO. Remaining work: reduce
model-request and image-preparation overhead, then repeat controlled runs and
validate a second task; do not remove screenshot reasoning or final verification.

Local evidence: `artifacts/native-half-20261016/README.md`. Raw reports, private
planning logs and screenshots stay ignored and must not be published.

A follow-up screenshot-size experiment added an optional `reset_package` argument
to the test-only native instrumentation harness. It force-stops and launches the
specified app and saves each initial screenshot before task timing; package names
are validated and Tapilot itself cannot be selected. The debug and AndroidTest
APKs compile. On OPPO, instrumentation startup left accessibility unbound and the
experiment timed out before any sample. No screenshot-size benefit is established.
The verified non-debuggable beta was restored; Tapilot accessibility is bound and
the crashed-service set is empty. Production screenshot factor remains 1.

### Live debug experiments without instrumentation restart

The debug APK alone exposes `PerformanceExperimentReceiver`, protected by
`android.permission.DUMP` (ADB shell). Open and configure Tapilot first; keep the
phone unlocked and its accessibility service bound. Set up and visually check the
same Settings start page before each sample, outside the measured interval.

```sh
adb -s SERIAL shell am broadcast \
  -n lifeos.md.tapilot/.performance.PerformanceExperimentReceiver \
  --es screenshot_shrink_factor 1 --ez run_benchmark true
```

This starts a new conversation with a fixed read-only Display/auto-brightness
request through the normal native coordinator. It cannot inject arbitrary tasks
or gestures. Use `1.5` for the alternate screenshot size; omit `run_benchmark` to
change only the next task's experiment setting. Active tasks reject changes.
Reset with `--es screenshot_shrink_factor reset`. This receiver is absent from
beta/release APKs. Record the installed build and actual returned model, alternate
samples, collect **all** operation reports in the task interval (including failed
verification and recovery), and retain independent final screenshot verification.
A successful final task must not hide an earlier failed operation in its timings.


A live full-resolution sample on 2026-10-16 took 79.015 s. Its first screenshot
query read the correct page and enabled switch, but returned false because the
outer model also requested proof that no setting had previously been changed.
That historical assertion is not observable in a final screenshot. The task then
performed another observation and succeeded. The instruction-generation contract
now explicitly keeps execution constraints in the action instruction while
restricting the screenshot predicate to visible facts. It does not remove the
constraint, rewrite a failed query to success, or claim a screenshot proves action
history. This single diagnostic sample is not a new performance baseline.

After that instruction change, three native runs completed with one outer model
call, one phone operation, three visual plans and one successful independent
query each. The execution instruction retained the prohibition on toggling. Actual
outer and visual models were `gpt-6-luna`; the screenshot evidence read the enabled
auto-brightness switch. Full-size images remained 988×1972; factor 1.5 produced
659×1315. Results from the same debug build:

| Sample | Screenshot factor | Task wall | Runtime preparation | Visual HTTP total |
| --- | ---: | ---: | ---: | ---: |
| First after runtime update | 1 | 58.602 s | 11.708 s | 22.220 s |
| Warm reduced image | 1.5 | 57.808 s | 0.008 s | 33.527 s |
| Warm full image | 1 | 48.797 s | 0.009 s | 26.270 s |

USB observation disconnected transiently in the two warm samples; the native tasks
continued and completed. Their task IDs and completed timing records were recovered
from device logs, and their matching operation reports were collected without
replaying tasks. These exploratory samples do not establish a screenshot-size
speedup or a 50% reduction. Keep the full-resolution production default. The cold
sample must not be mixed into a warm-start comparison. All local artifacts are under
`artifacts/native-live-ab-20261016/` (ignored; not redistributed).

### Controlled desktop/native execution experiment

Use the same bundled `android.config.mjs`, adapter, task file and screenshot
settling on both hosts. The fixed fixture is
`app/src/debug/assets/performance/read-auto-brightness.json`. It launches Settings,
navigates visually and independently queries the final screen. It bypasses the
outer conversation model **on both sides** to isolate execution cost; these numbers
must never be labelled full Codex-versus-native conversation latency. Follow with
full-agent validation after locating execution differences.

The OPPO experiment requires actual `gpt-6-luna` responses, 988×1972 screenshots,
shrink factor 1, matching ChatGPT account, Chinese assistant context, Responses
streaming, provider-default reasoning and the same pinned runtime bundle. Desktop
runs require Node 24.18.0, matching the embedded phone Node version. Hardware,
OS, Sharp native/WASM backend and network routes remain measured differences.
Use alternating repetitions, the same visually verified Settings root, an unlocked
phone, no concurrent controller, and separate cold runtime installation.

```sh
# Phone debug APK only; normal coordinator, fixed task, no arbitrary task injection.
adb -s SERIAL shell am broadcast \
  -n lifeos.md.tapilot/.performance.PerformanceExperimentReceiver \
  --es screenshot_shrink_factor 1 --ez controlled true --es capture_format png

# Desktop: use the authorized paired phone's CURRENT Wi-Fi IP, preserving TLS pin.
# Optional isolated auth directory keeps the ordinary plugin login intact.
TAPILOT_BENCH_AUTH_DIR=/private/authorized/test-account \
TAPILOT_BENCH_ACCOUNT=phone-account-email \
TAPILOT_BENCH_HOST=current-phone-ip \
node scripts/compare-native-desktop.mjs run
```

The desktop script gates account and runtime versions, records fixture/source
fingerprints, uses the exact native runner and validates actual returned model,
image dimensions and independent query success. Failed/unknown transport mutations
terminate the sample without replay. Outputs are private ignored artifacts. The
runner's own `durationMs` is the common inner boundary; phone task wall also
includes app persistence/dispatch, while desktop `executionWallMs` includes child
startup. Report these separately. Connection/reset/login are setup, not task time.

An explicit `device.capture` format can now request JPEG. PNG remains the default;
clean JPEG captures use quality 90, existing preview JPEG remains quality 75.
Debug phone `capture_format=jpeg` and desktop `TAPILOT_BENCH_CAPTURE_FORMAT=jpeg`
select the experimental native encoder. The SDK already passes valid JPEG through
when shrink factor is 1, avoiding another WASM PNG-to-JPEG conversion. Production
native tasks still use PNG until accuracy and latency acceptance completes.

`benchmark-image-pipeline.mjs IMAGE ABSOLUTE_SHARP_MODULE [SAMPLES]` measures the
same input bytes, warmup plus ten retained samples, without any model/network call.
Initial CPU-only measurements used matching input SHA-256 and Sharp/libvips but
Node 22.16 on Mac versus 24.18 on Android, so they are diagnostic, not a fully
controlled platform ranking: full JPEG encoding medians 8.86 versus 476.97 ms;
stability fingerprint 28.81 versus 215.76 ms. Repeat with matching Node versions.

Initial phone-only fixture samples passed final visual verification: PNG task wall
41.855 s / inner runner 38.657 s (5 visual requests, 23.232 s HTTP); experimental
JPEG wall 48.409 s / inner 45.076 s (4 requests, 34.176 s HTTP). Settling totals
were 6.361 and 3.994 s but also differed in samples/actions. These two runs do not
prove an end-to-end speedup. The first desktop execution attempt failed to connect
before phone actions; wireless control was subsequently enabled. Strict paired
model comparison remains pending a matching-account desktop authorization. Existing
phone and ordinary desktop plugin accounts were observed to differ; do not merge
their results into a controlled ranking.

Repeating the identical-image microbenchmark with **Node 24.18.0 on both hosts**
still measured desktop medians of 9.53 ms full JPEG encoding / 31.18 ms stability
fingerprint, versus Android 476.97 / 215.76 ms. Sharp is 0.35.5 and libvips 8.18.7
on both. These are processing-only measurements; hardware/OS and native versus
WASM backend remain the variables, and the ratios are not task speedup ratios.

### 2026-10-17: matching-account execution and native fingerprints

Matching-account desktop authorization is now verified in an isolated credential
store; the ordinary plugin account is unchanged. Both hosts run the fixed
read-auto-brightness fixture, Node 24.18.0, the same bundle, gpt-6-luna,
988×1972 JPEG quality 90, shrink factor 1, and an independent final visual query.
This isolates the execution stage; it does not measure a full Codex conversation
against the phone's outer conversation planner.

The OPPO VPN advertised both WIFI and VPN transports. Selecting the first WIFI
network incorrectly bound wireless control to the VPN interface. Selection now
requires a non-VPN Wi-Fi network. Real-device verification showed the listener
move to the physical Wi-Fi address and pinned/authenticated desktop tasks succeed.

Stability checking now negotiates `rgb-luma-64x96-v1` fingerprints computed from
the captured Android bitmap. This avoids repeatedly decoding full images through
WASM only to compare tiny grayscale thumbnails. Full screenshots still go to the
model and reports. Fingerprints are tied to frame IDs; stale/malformed hints fail
rather than mixing native and Sharp algorithms. Older servers retain the Sharp
path. Delayed navigation, unchanged screens, press feedback and invalid hints
are covered by tests. JPEG model capture remains an explicit experiment, not the
production default.

For network attribution, desktop repetitions additionally used the phone's HTTP
proxy through a temporary localhost ADB port forward and Node's
`NODE_USE_ENV_PROXY=1`. Only model traffic uses that proxy; phone control still
uses pinned TLS over Wi-Fi. This diagnostic shares the proxy route but cannot
hold provider queueing/cache state constant. It is not a standalone-phone solution
or an excuse to redefine the fast desktop baseline. A desktop run on its original
route succeeded in 30.594 s (inner 30.476 s; HTTP 19.795 s; four model calls).

All three alternating retained samples passed independent screenshot verification;
actual returned models matched and there were zero model retries. Do not filter
out the slow sample or the phone's extra planning call in repetition 3.

| Host / repetition | Task wall (s) | Inner runner (s) | Model HTTP (s) | Model calls |
| --- | ---: | ---: | ---: | ---: |
| Desktop, phone proxy 1 | 51.360 | 51.244 | 40.659 | 4 |
| Phone 1 | 68.445 | 65.594 | 53.647 | 4 |
| Desktop, phone proxy 2 | 46.854 | 46.741 | 36.616 | 4 |
| Phone 2 | 50.705 | 48.401 | 36.782 | 4 |
| Desktop, phone proxy 3 | 34.854 | 34.730 | 24.321 | 4 |
| Phone 3 | 40.450 | 38.582 | 26.062 | 5 |

Median task walls are **50.705 s phone / 46.854 s desktop (+8.2%)**;
matching inner-runner boundaries are **48.401 / 46.741 s (+3.6%)**.
The first phone run after installing the new bundle is excluded as warmup:
76.983 s wall / 60.863 s inner, five calls, 45.085 s HTTP. It is retained in the
[data file](performance-controlled-20261017.json), together with every sample.

In repetition 2, HTTP totals differ by only 0.166 s; inner non-HTTP residuals are
11.619 s phone / 10.125 s desktop. Across samples, HTTP dominates variability.
HTTP includes model processing, queueing and network transport: these measurements
cannot assign all of it to Wi-Fi, VPN or inference. The original-route desktop
sample and repeated phone-proxy samples differ in time and provider cache state,
so the route experiment is suggestive, not a precise causal speedup estimate.

The shared-route execution stage is now close on this fixture. This does **not**
prove a 50% full standalone-task improvement, improvement over the previous APK,
or parity with the fastest original-route desktop sample. Additional tasks,
full-conversation timing and isolating model-service wait remain outstanding.
The temporary proxy forward was removed after testing.


### Follow-up: second task and bounded edge recovery

The fixed `read-android-version` fixture adds a longer, read-only navigation task.
Select it with `TAPILOT_BENCH_FIXTURE=read-android-version` on desktop and
`--es fixture read-android-version` in the debug receiver. Both paths use the same
asset and verify the visible Android version label/value, not ColorOS or a build
number. The allowed fixture list rejects arbitrary asset paths.

This task exposed another planning retry: a Scroll region `[43,320,945,1973]`
on a 988×1972 screenshot exceeded the exclusive bottom edge by one pixel. The
codec patch now permits at most one additional pixel on bbox far edges, which the
existing mapper clips to the last pixel. Origins, points, normalized coordinates,
reversed boxes and larger overflow still fail. Tests cover the exact observed
response in ESM/CommonJS, both coordinate orders, and rejection just beyond the
new tolerance. This is bounded recovery of a model region, not unrestricted
coordinate clamping.

The pre-fix phone sample had nine HTTP requests but only eight recorded inference
usages: a parsing retry was absent from the usage total. `modelCalls` and its
`retries` therefore must not be interpreted as complete transport/planning counts.
The desktop harness now also records `modelHttpRequests`, retains its HTML report,
and rejects samples whose runtime source fingerprint changed during execution.

Native AI tasks now default to Android JPEG quality 90 at full resolution, avoiding
the former PNG-to-JPEG WASM conversion. The public device protocol still defaults
to PNG. Debug overrides retain PNG for regression comparisons; clearing preferences
selects the production JPEG path. This supersedes the earlier experimental-only
status above. Both benchmark entry points default to JPEG to match the app.

The default path was exercised after clearing `tapilot-performance` preferences
(the file contained an empty map). The real model received clean full-size JPEG
screenshots without the floating button or task border; the independent final
query correctly read Android 14. The first updated-bundle run is a warmup and must
not be mixed with retained task-wall samples.

| Version task sample | Wall (s) | Inner (s) | HTTP (s) | HTTP requests | Device actions |
| --- | ---: | ---: | ---: | ---: | ---: |
| Phone before edge fix | 73.048 | 69.779 | 42.327 | 9 | 7 |
| Desktop before edge fix | 52.132 | 52.022 | 31.036 | 7 | 6 |
| Phone after, cold bundle | 77.920 | 62.694 | 41.774 | 7 | 6 |
| Phone after, warm default | 71.348 | 68.829 | 43.381 | 8 | 7 |
| Desktop after, original route | 62.161 | 62.016 | 37.321 | 8 | 7 |

All samples succeeded and independently read Android 14. The two updated phone
runs have no parsing retries. The retained updated pair has equal request/action
counts; phone wall is 14.8% slower. Of the 9.187 s wall difference, 6.060 s is in
HTTP, 0.753 s in the inner non-HTTP residual, and 2.374 s outside the common inner
runner boundary. This attributes the measured difference, not pure network RTT.
The before/after samples have different action choices and provider wait, so do
not infer a 50% improvement or a stable median from this single retained pair.
[Raw timing summary](performance-version-20261017.json) retains the cold run and
pre-fix parsing failure. Updated runtime tests (69), Kotlin unit tests, debug/beta
builds and local non-debuggable beta APK verification passed; distribution signing
is outside this performance acceptance.

### Full conversation comparison (2026-10-17)

`scripts/compare-full-conversation.mjs` runs the phone's actual Pi bundle on the
computer. A persistent, test-only JVM codec calls `NativeConversationAgent` and
`ChatGptResponses` directly, so system instructions, tool declarations, app
resolution, verified-task construction and Responses decoding are not replaced
by a simpler desktop agent. Both outer and visual models must actually report
`gpt-6-luna`; both use the same authorized account, Chinese language, empty chat
history, provider-default reasoning, full-size JPEG and the same OPPO. The
native debug receiver submits the identical read-only brightness prompt.

Prepare the codec with `./gradlew :app:prepareBenchmarkCodec` using Java 17 and
the configured Android SDK. Set `TAPILOT_BENCH_AUTH_DIR`,
`TAPILOT_BENCH_ACCOUNT`, and `TAPILOT_BENCH_HOST`, then run the desktop script
with Node 24.18.0. Collect a standalone phone run with:

```sh
python3 scripts/collect-native-benchmark.py unique-sample-name --serial e7ed3f17
```

Reset Settings and visually confirm its root before each run. Never operate both
agents concurrently. The collector clears performance overrides to exercise
production capture defaults. It uses **device time** to identify completion;
using Mac time previously missed a successful task because the phone clock was
about one second behind. Observer timeout is not proof of task failure and never
causes automatic replay. Existing output directories are rejected.

The full desktop timer includes app catalog, Pi startup, outer model, generated
visual task, inner harness startup/auth/connection and final verification. JVM
warmup and initial pairing/auth preflight are excluded. Phone task wall also
includes Android dispatch and persistence. This compares the same agent on two
hosts, **not** the Codex application's own assistant or rendered UI latency.
Network experiments are recorded separately: directing desktop HTTPS through
the phone's proxy controls the configured proxy route but does not make model
queueing, cache state or every packet path identical. It must not be presented as
phone acceleration. Keep original-desktop-route reference runs separate.

The first full desktop attempt revealed that wireless launch rejected the exact
launcher component emitted by the native policy. Wireless launch now accepts
components only when they occur in Android's installed launcher catalog; URLs
and arbitrary/private activities remain rejected. This matches native task
routing without replacing its launch instruction with a benchmark shortcut.
Failed attempts and report paths remain in private `artifacts/controlled-execution`.

Three alternating, post-install full-task pairs all passed independent screen
verification, with 3 device actions and 4 visual HTTP requests per task (plus
one outer model request). Outer input was 2872 tokens on both hosts.

| Full task | Run 1 (s) | Run 2 (s) | Run 3 (s) | Median (s) |
| --- | ---: | ---: | ---: | ---: |
| Standalone phone | 42.916 | 36.677 | 49.781 | 42.916 |
| Desktop, phone proxy | 44.623 | 39.550 | 38.869 | 39.550 |

The phone median is 8.5% higher. Inner runner time excluding visual HTTP has a
10.883 s phone median versus 10.028 s desktop median: a 0.855 s difference.
Full wall minus outer-model and inner-runner time has a 3.794 s phone median
versus 0.792 s desktop median. That residual includes native dispatch, runtime
startup, intermediate work and persistence; it is **not** a measured single
function or proof of a particular UI bottleneck. Profiling this boundary is the
next local optimization target. Stage medians need not sum to median wall time.

The phone's slowest retained run spent 26.823 s in visual HTTP and 8.677 s in
outer completion, so waiting for model requests remains the largest part of
that sample. HTTP includes provider processing/queueing and network time, not
just network latency. These results establish proximity for this task under the
stated controls, not a general 50% speedup. A pre-install 40.496 s reference is
preserved separately and excluded from the post-install median.

[Machine-readable measurements](performance-full-20261017.json) retain sample
identifiers, phase timings, actual model identity, independent observations and
failed desktop attempts. Kotlin unit tests, `pnpm check:quick`, debug APK build,
and real-device full-task checks passed. No GitHub CI or distribution claim is
made by these local checks.

The original desktop network route also passed the identical full task in
**32.635 s** (one reference sample, not a stable median). It used the same 3
actions and 4 visual requests. This is faster than the shared-proxy cohort, so
8.5% must not be claimed as parity with the fastest original desktop route.
Comparing the 42.916 s phone sample with this reference, the 10.281 s gap consists
of 5.828 s more in outer completion plus visual HTTP, 4.658 s more outside those
phases and the inner runner, and 0.204 s **less** inner non-HTTP work on the phone.
This decomposition identifies where to profile next; sequential provider
variation prevents attributing the request difference solely to the proxy.
The temporary ADB proxy forward was removed after testing.

### Native persistence profiling and encoding cache (2026-10-17)

Numeric phase probes separate immutable snapshot creation, queued writes,
flush waits, main-thread dispatch, report import and Pi shutdown. A full task
showed report import at 69 ms and shutdown below 1 ms, while six completed
persistence writes totaled 3726 ms and six flush barriers waited 1691 ms.
These phases overlap; their totals must not be added to task wall time.

Two redundant conversions were investigated: cloning all conversations through
JSON text at task start, and reconstructing every historical record from a Map
on every write. The final implementation snapshots only the active task's prior
context, retry state and pending inputs for the worker. UI history is retained.
The persistence worker still receives a fully detached immutable snapshot, but
reuses encoded history entries whose complete nested Map values are equal.
Changed entries are re-encoded. Record order, deletion, duplicate IDs, nulls and
nested amendments remain meaningful; no ID-only cache or mutable-key cache is
used. Metadata is freshly serialized and the entire JSON file is still written
atomically with `fsync` before a flush barrier completes. The cache is replaced
only after a successful write and retains only the latest history snapshot.
No history truncation, checkpoint removal or asynchronous success shortcut was
introduced.

| Native full brightness sample | Wall (s) | Visual HTTP (s) | Outside outer model + inner runner (s) | Flush wait (s) |
| --- | ---: | ---: | ---: | ---: |
| Instrumented original | 42.635 | 20.046 | 6.175 | 1.691 |
| Direct JSON snapshot experiment | 41.147 | 19.869 | 4.914 | 1.346 |
| Active task snapshot experiment | 43.639 | 22.278 | 5.071 | 1.610 |
| Final cache, first run after install | 50.797 | 30.262 | 4.596 | 0.920 |
| Final cache, warm run 1 | 36.801 | 17.724 | 3.023 | 0.063 |
| Final cache, warm run 2 | 63.257 | 45.127 | 2.695 | 0.039 |

All six runs used the actual `gpt-6-luna` for both layers, 2872-token outer input,
3 device actions and 4 visual requests, and passed independent screenshot
verification. The final warm runs completed seven measured background writes in
568 ms and 392 ms respectively. Collection stops at `task.wall`, so a final
queued write can be outside the captured window; compare counts as well as sums.
First-run and warm timings are distinguished, not pooled into a speedup claim.

The slow 63.257 s sample is retained: its 45.127 s visual HTTP wait dominates the
task despite lower persistence overhead. One visual request spent about 11.07 s
awaiting headers with a local body-send timing near 4 ms. This is evidence of
request-path/provider waiting, not proof that the network or provider alone is
responsible. The full-task goal remains unmet; investigate that path with fixed
request payloads before attributing it to phone compute. The good 36.801 s sample
alone does not establish stable parity with the original desktop route or a 50%
reduction.

[Phase measurements and all samples](performance-persistence-20261017.json)
include intermediate experiments. Kotlin tests cover nested snapshot isolation,
retry/continue/amendment semantics, cache invalidation, stale drafts, I/O failure
barriers and non-disruptive diagnostics. Kotlin tests, debug build and local
quick checks passed. The direct-snapshot experiment is not the persistence
implementation: worker snapshots remain immutable Maps for content-based cache
keys, while task context uses direct JSON copies.

### Identical request isolation and phone AI route (2026-10-17)

`scripts/benchmark-model-path.mjs SERIAL SAVED_REPORT` replays one saved JPEG
and one read-only question as byte-identical Responses bodies on desktop Node
and the APK's embedded Node, under the actual app UID. Both use Node 24.18.0,
`gpt-6-luna`, the same bearer credential, and 2346 input tokens. Credentials
travel only through worker stdin, never command arguments or saved artifacts.
Each cohort alternates desktop/phone order for four samples per host. Receipts
exclude ADB delivery from request timing; bounded timeouts retain failures and
never retry requests automatically. Set `TAPILOT_BENCH_AUTH_DIR` and
`TAPILOT_BENCH_ACCOUNT` to the isolated benchmark login before running.

| Fixed visual request | Desktop median | Phone median | Observed edge suffix, desktop / phone |
| --- | ---: | ---: | --- |
| Original phone AI route | 3.333 s | 8.183 s | SIN / SJC |
| Phone AI selector: Singapore 1 | 3.475 s | 3.517 s | SIN / SIN |
| Same route, confirmatory cohort | 3.353 s | 3.493 s | SIN / SIN |

Only the phone's Clash AI selector changed, from Proxy (Fremont 2) to
Singapore 1. The general Proxy selector and desktop configuration were left
unchanged. The faster AI route remains selected on this test phone. This is a
device configuration change, not a hard-coded route in the product. Request
SHA-256, image SHA-256, cache usage, output lengths and raw timings are retained
in the linked measurements. Phone fixed-request median decreased about 57%;
this small sequential comparison supports a request-path contribution, but does
not distinguish all provider/cache effects or establish a 57% full-task gain.

Full-task validation then used the same Tapilot agent on desktop and phone:
same account, actual model, task text, empty conversation, 2872-token outer
input, OPPO initial Settings page, full-size JPEG quality 90 and independent
visual verification. Desktop uses the original desktop network, not a slower
shared proxy. These are same-agent measurements, not Codex UI response times.

| Alternating pair | Standalone phone | Desktop same agent |
| --- | ---: | ---: |
| 1 | 46.462 s | 34.254 s |
| 2 | 38.532 s | 32.526 s |
| 3 | 38.790 s | 39.181 s |
| Median | **38.790 s** | **34.254 s** |

All six full tasks succeeded with four visual model calls and final screenshot
verification. The slow first phone run remains included: outer completion took
9.392 s, including an approximately 3.6 s pre-request gap whose cause is not yet
proven. Phone median is **13.2% slower** than desktop. Against the earlier
55.119 s phone baseline this is 29.6% lower, but that historical comparison is
not a concurrent controlled experiment. The original 50% full-task target is
still unmet. Fixed request parity does not establish full-task parity.

Remaining work must separate model wait from runner startup, screenshot
settling/capture and native task bookkeeping. Do not reduce verification or
attribute every remaining second to network latency. Local quick checks,
script syntax checks and real paired-device task validation passed; this does
not claim GitHub CI or other-device validation.

[Raw controlled measurements and boundaries](performance-network-20261017.json)

### Screenshot scheduling and redundant UI work (2026-10-17)

Debug-only numeric capture probes distinguish overlay scheduling, Android
screenshot callbacks, bitmap copy/encoding/fingerprint work and rate-limit
failures. In the instrumented full task, 15 successful captures used only 330 ms
in Android screenshot callbacks and 1099 ms in bitmap processing. Overlay waits
(including main-thread scheduling) totaled 1632 ms, and three rate-limit failures
introduced the existing 350 ms backoffs. These are nested measurements, not
independent additions to task wall time.

Two unnecessary costs were removed:

- Identical phone-progress notifications no longer rebuild the entire chat
  state. Conversation sidebar construction groups history once instead of
  filtering all history separately for every conversation. Latest-message order,
  equal-timestamp ordering and the earliest-message title remain preserved.
- After Android actually reports a screenshot rate limit, subsequent display
  captures wait only the remaining 350 ms request interval before hiding
  overlays. Devices without an observed limit retain their existing path.
  Each attempt has at most one pacing wait, so concurrent previews cannot
  postpone it indefinitely. Cancellation is checked again after that wait;
  the original bounded failure retries remain for OEM differences. Every
  successful capture still takes a fresh screenshot.

The progress-only experiment did not demonstrate an end-to-end speedup and is
retained in the data. The first pacing run reduced capture RPC time from
4.958 s to 4.335 s for the same 15 frames, and Android rate-limit failures from
three to one. Inner non-HTTP time fell from 10.532 s to 9.814 s. Full task time
was 37.390 s versus 39.936 s before the changes, but model HTTP wait also fell;
that entire difference must not be attributed to the implementation.

All recorded tasks retain full-size JPEG quality 90, actual `gpt-6-luna`, four
visual model calls and an independent final visual check. Unit tests verify
that pacing is inactive before a real limit, only waits the remaining interval,
and does not survive a new service instance. Debug APK build, Kotlin unit tests
and local quick checks passed. This is not a new desktop-parity or 50% claim.

[Capture phase measurements, including the intermediate experiment](performance-capture-20261017.json)

The repeat after installing the final APK completed in **43.951 s**: 15 capture
RPCs took **4.509 s**, with one Android rate-limit failure. Visual HTTP time was
23.429 s, so local savings did not prevent a slower end-to-end result. The next
continuous run (no reinstall) completed in **35.553 s**: 15 captures took
**4.020 s**, zero rate-limit failures, and inner non-HTTP work took **9.708 s**.
All samples are retained. The roughly 19% capture reduction compares a first-run
baseline with a warm run and must not be presented as an overall 19% speedup.
The two first-run pacing samples separately show lower capture totals than the
instrumented baseline. More full-task controlled samples are still needed.

### Real planning payload, matched transport (2026-10-17)

The short visual query above has 2346 input tokens; it does not represent the
full planning prompt. An opt-in benchmark preload now captures actual outgoing
Responses request bodies, including the SIWC `Request` boundary. It clones the
request without consuming the original and never saves headers. Bodies remain
in ignored local artifacts with mode 0600; do not publish these screenshot and
prompt fixtures. Set `TAPILOT_BENCH_SAVE_MODEL_REQUESTS=1` when running the full
desktop benchmark, then pass `model-requests/request-1.json` to
`scripts/benchmark-model-path.mjs` instead of an HTML report.

A real 211296-byte planning request was replayed four times per host in
alternating order. Both hosts use the actual `siwcFetch` wrapper, identical body
SHA, bearer credential, Node version, model `gpt-6-luna` and reasoning settings
(`low`, `current_turn`). All eight responses had **11234 input tokens**. Median
request time was **5.356 s on the phone** and **5.589 s on desktop**. Both reported
SIN edges. Output tokens varied (phone 96–105, desktop 106–108); one phone request
missed the provider prefix cache while the other requests reported 8192 cached
tokens. No phone actions are executed during replay.

This small cohort does not show a persistent phone request penalty under the
real planning load; it is not proof of full-task parity. Two desktop full tasks
also completed in 42.279 s (five HTTP requests, one model retry) and 32.087 s
(four requests, no retry). The first capture attempt used a string-only hook and
produced no body fixture; the corrected Request-aware hook produced the fixture
for the second task. Both full tasks passed independent visual verification.
Capture-mode task times include local fixture-write overhead and are diagnostic
samples, not a new speedup cohort. The benchmark boundary test and local quick
checks passed.

[Matched planning request measurements](performance-planning-request-20261017.json)

### Reuse startup history encoding on the first task (2026-10-17)

Startup recovery already durably saved all history through `ConversationStore.save`,
but that path bypassed the immutable history encoding cache. The first queued
task checkpoint therefore encoded the same history again. Direct saves now
capture the same detached Map snapshot and use the same durable encoding path
as worker saves. The caller's storage revision still matches its direct save;
subsequent worker writes generate fresh revisions. Atomic replacement, fsync,
draft revision checks, cache publication after successful writes and nested
content invalidation remain intact.

The first full task after installing this change succeeded in **43.222 s**.
Compared with the prior first-after-install sample (43.951 s), first write time
fell from **997 ms to 361 ms**, and summed flush waits from **1208 ms to 513 ms**.
Time outside outer completion and the inner runner fell from **4423 ms to
3856 ms**. Visual HTTP time increased from 23.429 s to 23.844 s; a single pair
cannot establish stable overall improvement. The timer starts with the task,
not app initialization: this does not measure launch-to-task-completion latency.
The existing startup durable write is retained and now populates the cache;
there is no separate prewarm write or deferred persistence barrier.

Tests cover startup revision agreement, detached nested values/nulls, subsequent
worker mutation, draft restoration and the existing cache/failure semantics.
Kotlin tests, debug APK build, local quick checks and real OPPO full-task visual
verification passed. The phone has the updated APK; no other-device or GitHub CI
claim is made. [First-task measurements](performance-startup-cache-20261017.json)

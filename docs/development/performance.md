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

### Full-task scrolling comparison (2026-10-17)

The desktop and debug-phone full-task benchmarks now read their English task
instructions from `app/src/debug/assets/performance/full-tasks.json`. Select
`read-android-version` with `TAPILOT_BENCH_FIXTURE` on desktop or `--fixture` in
`collect-native-benchmark.py`. Brightness remains the default. The native
collector records the installed runtime source fingerprint with each sample.

Both hosts use gpt-6-luna, Node 24.18.0, the same account, full 988×1972 JPEG
quality-90 images, the same Tapilot policies, empty conversation history and
independent final visual verification. Before each sample, Settings is reset to
its root and inspected from a screenshot outside the timer. Runs are sequential
and alternate hosts. Full runs use separate login sessions for the same account;
only the fixed-request replay above also controls the exact bearer and request
body. Generated agent instructions and navigation can still vary. Desktop runs
the same agent, not the Codex UI, and excludes preflight/JVM warmup; phone timing
includes persistence. These boundaries prevent a hardware-only interpretation.

The baseline phone planner made five small scrolls in the main Settings list.
General bilingual guidance now prefers a normal overlapping scroll when searching
for an off-screen target, leaves distance to the device default when precision
is unnecessary, and keeps small adjustments for partially visible targets.
Every scroll still requires a new screenshot. This changes neither model nor
image quality and introduces no task-specific coordinates or blind action batch.

The first updated phone run includes 11.876 s of runtime preparation after the
bundle changed. Its entire 70.445 s is retained as a cold/update sample; it must
not be presented as a measured warm result by subtracting preparation time.

[All version-task measurements and verification](performance-full-version-20261017.json)

| Version-task sample | Full wall time | Device actions | Visual HTTP requests |
| --- | ---: | ---: | ---: |
| Phone baseline | 80.018 s | 8 | 9 |
| Desktop baseline | 55.819 s | 6 | 7 |
| Phone updated runtime (cold) | 70.445 s | 5 | 6 |
| Desktop candidate 1 | 50.244 s | 5 | 6 |
| Phone candidate warm 1 | 56.777 s | 5 | 6 |
| Desktop candidate 2 | 53.330 s | 5 | 6 |
| Phone candidate warm 2 | 63.767 s | 6 | 7 |

Every sample independently read Android version 14. Warm candidate medians are
60.272 s on phone and 51.787 s on desktop: phone is 16.4% slower. Against the
single phone baseline, the observed reduction is 24.7%, not evidence of a stable
50% improvement. The second warm phone run additionally opened the Android
version detail page, accounting for one additional action/request; it is retained,
not discarded as an outlier. For the first candidate pair with equal action and
request counts, phone visual HTTP time is 32.253 s versus desktop 26.437 s,
while inner non-HTTP time is lower on phone (16.437 s versus 17.880 s).
The phone's outside-runner/outer-model overhead is 2.887 s versus 0.879 s.
Thus this pair does not support phone CPU execution as the dominant remaining
bottleneck. Full-task model requests still differ in content and response, and
these two warm samples per host cannot isolate model serving variance from
login-session/network effects. Exact-body replay above controls those variables
more tightly but does not replace end-to-end measurements.

The runtime bundle checks, local quick checks, Kotlin unit tests and debug APK
build passed. This cohort covers the OPPO only, not a second phone or GitHub CI.

A native brightness regression on the same candidate runtime succeeded in
41.067 s with four visual model calls and no retries. Independent visual query
read the visible auto-brightness switch as on. This is a functional regression
sample, not a new brightness speedup cohort. Its evidence is included in the
linked JSON. No settings were intentionally changed by either task.

### Stop redundant read-only navigation and observe serving edges (2026-10-17)

General bilingual guidance now asks a read-only agent to stop navigating once
the requested label/value is clear and the requested navigation is complete.
It must continue if evidence is missing or ambiguous. The separate final visual
query remains mandatory. This targets the extra version-detail navigation in
the preceding cohort without hardcoding a task, screen, coordinate or answer.

Transport diagnostics now retain only a validated three-letter `cf-ray` edge
suffix and a finite nonnegative `openai-processing-ms` value when supplied.
They do not retain request IDs or arbitrary response headers. Tests cover
stream preservation, cancellation, telemetry errors and malformed/private
header values. An edge code describes the response edge, not the inference
server location; response-header waiting cannot by itself separate network
latency, service queueing and model processing.

The first phone task after updating completed in 63.904 s, including 10.631 s
runtime preparation. Its observer exited before task completion; the same
running task was observed again and its terminal result/report recovered.
No task was restarted. All six visual requests returned HTTP 200 via SIN,
and independent verification read Android version 14. No server-processing
header was present. The first desktop task completed in 54.937 s but included
one HTTP 504 and a model-only retry; no device action was replayed. This sample
is retained and cannot by itself support a phone-versus-desktop parity claim.

[Complete measurements including cold runs and retries](performance-visible-evidence-20261017.json)

The two warm phone samples were **59.046 s and 53.046 s** (median 56.046 s),
both five device actions, six visual HTTP requests and no retries. Desktop
samples were **54.937 s and 58.573 s** (median 56.755 s), but each had one
model retry: the first includes HTTP 504, the second has seven HTTP-200
responses and the report marks one retry. The latter retry's precise cause is
not established. Their similar medians therefore do not prove steady-state
parity or a 50% speedup. All independently read Android version 14 via SIN.

The slower warm phone sample's outer completion phase was 10.576 s, but its
HTTP transport measured only 5.558 s. About 4.9 s preceded the persistence
barrier and HTTP request; it must not be attributed entirely to model serving.
`ConversationModelClient` now records credential acquisition, encoding,
request and decoding durations separately, with only phase/duration/success.
Telemetry errors cannot fail or repeat the request. Tests cover that boundary
and credential failures issuing no HTTP request. The first instrumented real
run measured 23 ms credentials, 38 ms encoding and 49 ms decoding, so the
preceding five-second delay was not reproduced and its cause remains open.

Runtime checks, local quick checks, Kotlin unit tests and debug builds passed.
The updated APK is installed on the OPPO. No second-device or GitHub CI claim
is made. The existing fixed-request replay and these full-task samples are
complementary evidence, not interchangeable measurements.

The first task after the diagnostic-only APK update completed in **60.904 s**,
with five actions, six successful visual requests and independent Android-14
verification. Runtime preparation was already warm; app/process startup and
first-task persistence were not. It is reported separately from the earlier
warm pair. Visual HTTP time was 32.982 s and inner non-HTTP time 17.535 s.
The final evidence screenshot was also manually inspected. The four native
runs in this cohort all stopped on the About device page rather than opening
the version detail page; this is promising coverage, not a guarantee for all
read-only tasks.

### Remove deployment source maps and invalidate packaging changes (2026-10-17)

Android deployment now removes only valid v3 JavaScript source maps with
`.js.map`, `.mjs.map` or `.cjs.map` suffixes. Executable files, declarations,
licenses, unrelated map data and malformed files remain. Directory traversal
never follows symlinks. The desktop staging tree retains its source maps;
phone stack traces use the deployed JavaScript. The runtime does not enable
Node source-map processing. Tests exercise preservation, symlinks, nested
packages and repeat pruning.

This removes 2588 files / 27,656,145 uncompressed bytes. The original deployment
archive was 46,189,430 bytes. Archive size is distinct from APK size and task
latency; pruning cannot reduce warm model serving time.

The first candidate exposed a packaging invalidation bug: sourceFingerprint
changed, but runtimeVersion hashed only entry files, lockfile and workspace
package contents. The phone installs by runtimeVersion and therefore retained
the old runtime. That task succeeded in 55.112 s with a 7 ms prepare phase,
but is explicitly classified as a stale-runtime diagnostic, not a pruning
speed measurement. runtimeVersion now also hashes the full source fingerprint,
which includes packaging rules and dependency patches. This preserves upgrade
compatibility with existing phone installers and ensures packaging-only changes
activate their new payload.

After fixing invalidation, the installed runtime version is
`0.1.0-1ecbb5d246ba`; both its version and source fingerprint matched the APK
asset manifest. The deployment archive is 39,327,391 bytes, 14.9% smaller.
All 17,783 retained deployment files matched desktop staging byte-for-byte.
The genuine update sample prepared in 9.408 s versus 10.631 s in the preceding
update sample, a single-pair reduction of 1.223 s. Full task time was 67.678 s
versus the earlier 63.904 s: the smaller preparation time does not establish
an overall speedup amid other-stage variation. Independent visual verification
again read Android version 14. The observer ended before the task did; its
terminal log and report were recovered from the same task without replay.

The original 55.119 s / 27.560 s half-latency baseline is specifically the
**auto-brightness** task, not the version query. Keep warm-task acceptance,
runtime-update cost and deployment size as separate metrics.

[Deployment sizes, stale-runtime diagnostic and real task evidence](performance-runtime-pruning-20261017.json)

The final same-runtime brightness pair completed in **41.092 s phone** and
**34.091 s desktop**, both three device actions, four visual HTTP requests,
zero model retries and independent confirmation that auto-brightness is on.
Both requested/returned gpt-6-luna with full 988×1972 screenshots and the same
account; the native runtime fingerprint matched desktop. This is the Tapilot
same-agent desktop harness, not Codex UI timing. Settings was reset and visually
inspected before each run, and the two tasks ran sequentially.

| Brightness phase | Phone | Desktop |
| --- | ---: | ---: |
| Outer completion | 6.645 s | 5.849 s |
| Visual HTTP | 21.394 s | 17.666 s |
| Inner non-HTTP | 10.084 s | 9.735 s |
| Outside outer completion / inner runner | 2.969 s | 0.841 s |

The phone's credential acquisition was 15 ms, so the earlier pre-request delay
did not reproduce. This single pair leaves a 20.5% full-task gap; it is not a
stable median or 50% improvement. Packaging changes leave warm execution code
unchanged and address update cost, not inference latency. Focused pruning and
integrity tests, runtime bundle tests, Kotlin tests, debug APK build and local
quick checks passed. Both fixture tasks completed on the real OPPO; no second
phone or GitHub CI validation is claimed.

### Incremental conversation snapshots and reversed-order comparison (2026-10-17)

ConversationWriter now reuses detached immutable snapshot branches when their
JSON contents are unchanged. It still traverses nested values, detects in-place
mutations, and copies changed ancestors; it never caches mutable JSON by identity.
Durable writes, fsync and the flush barrier before external work are unchanged.
Tests cover nested mutation, old snapshot isolation, null/removal, same-size key
replacement, array growth/shrink/reordering and container type changes.

On the OPPO, eight snapshot calls took 421 and 411 ms in the two warm samples,
versus 683 ms in the preceding warm baseline. Main-dispatch totals were 404 ms
in both versus 626 ms. These phases overlap and must not be added as independent
wall-time savings. The first task after APK replacement took 45.730 s and had
1206 ms of snapshot work; it is retained separately, not discarded as an outlier.
The warm full task did not improve against the preceding 41.092 s sample.

The same-account, same-model brightness comparison ran sequentially in
phone/desktop/desktop/phone order, resetting and visually checking Settings root
before each task. Both sides used gpt-6-luna, Node 24.18.0, full 988×1972 JPEG
quality 90 screenshots and runtime fingerprint
`5b8cf98bb40ad634be5101f53bd1660c88884ae5bf7139e886457a00ffbf2605`.
This fingerprint identifies the JS runtime, not the changed Kotlin snapshot code.
Every sample performed three device actions and four visual model calls with
zero reported retries, independently reading auto-brightness as enabled.

| Warm sample | Full task | Outer calls / time | Visual HTTP | Inner non-HTTP | Outside outer / runner |
| --- | ---: | ---: | ---: | ---: | ---: |
| Phone 1 | 41.790 s | 1 / 4.425 s | 24.735 s | 9.932 s | 2.698 s |
| Desktop 1 | 40.878 s | 2 / 13.448 s | 16.638 s | 9.797 s | 0.995 s |
| Desktop 2 | 40.565 s | 2 / 8.626 s | 21.254 s | 9.866 s | 0.819 s |
| Phone 2 | 41.433 s | 1 / 5.032 s | 23.905 s | 9.798 s | 2.698 s |

The two-sample full-task medians are 41.612 s phone and 40.721 s desktop
(+2.2%), but **this does not establish stable parity**: both desktop model
responses chose an additional outer summary round, while phone responses used
the existing finishWhenVerified path. Do not subtract that round and present the
result as measured latency. The inner non-HTTP paths are already similar here;
visual HTTP remains slower on the phone in these samples, and phone startup /
persistence / completion outside the runner remains about 1.7–1.9 s higher.
All visual HTTP responses reported SIN, which identifies the response edge,
not inference location or proof of equivalent network routing.

The original 55.119 s brightness median versus this 41.612 s median is an
observed 24.5% reduction across revisions/conditions, not a causal estimate of
this snapshot change. The 27.560 s half-latency target remains unmet. Next
attribution must keep outer call count visible and replay identical model bodies
across hosts to distinguish transport/serving variation from orchestration.
Kotlin unit tests, debug APK build, benchmark codec preparation and local quick
checks passed. Real acceptance covers this OPPO only; no GitHub CI claim.

[Snapshot measurements and per-request HTTP evidence](performance-snapshot-sharing-20261017.json)

### Repeat identical-request routing isolation (2026-10-17)

Replayed the same saved 211,296-byte planning request (11,234 input tokens)
using Node 24.18.0 and the same bearer credential on both hosts. Each cohort
alternates desktop/phone/phone/desktop twice, four samples per host. These are
read-only model requests, not full tasks or benchmark navigation. Request and
screenshot hashes match across all five cohorts; all 40 requests completed with
the requested/returned gpt-6-luna model. Cache hits and generated tokens still
vary and are retained in the evidence.

| Routing condition | Desktop median | Phone median |
| --- | ---: | ---: |
| Original desktop route / phone VPN, Singapore 1 | 5.137 s | 6.418 s |
| Original desktop route / phone VPN, Singapore 2 | 4.980 s | 6.562 s |
| Desktop through phone HTTP proxy / phone VPN, Singapore 1 | 5.520 s | 5.863 s |
| Original desktop route / phone explicit local HTTP proxy, Singapore 1 | 5.453 s | 5.475 s |
| Explicit phone proxy, confirmation | 4.995 s | 5.718 s |

The first cohort's headers arrived at 2.267 s desktop versus 3.691 s phone.
Phone requests with an already-used connection sent their bodies in about
4–5 ms, while still waiting seconds for response headers. Likewise, the last
full-task phone sample sent requests 2–4 in 2.7–4.2 ms. Increasing keepalive
cannot remove that wait. Headers wait includes transit, provider queueing and
processing; the data does not separate these components, and no provider
processing duration was supplied.

Singapore 2 did not improve whole-request latency and the AI selector was
restored to Singapore 1, visually confirmed. Routing the desktop through the
phone proxy narrowed the request gap to 6.2%, but also slowed the desktop; this
is attribution evidence, not a phone acceleration. Explicit phone HTTP proxy
looked close in its first cohort but left 14.5% in confirmation, so there is no
basis to ship a hard-coded proxy or claim stable parity. The temporary localhost
ADB forward was removed. No production transport setting changed.

The replay harness now supports an explicit localhost-only HTTP proxy for the
phone worker via `TAPILOT_BENCH_PHONE_PROXY`, and checks that each worker received
the requested proxy mode. Credentials remain on stdin. This permits further
controlled route experiments without changing the installed app or requiring
phone model traffic to pass through a computer. This diagnostic option is not
an end-user proxy setting.

[Per-request timings, usage/cache counts and payload hashes](performance-model-route-20261017.json)

### Bundle Pi startup dependencies (2026-10-17)

The Pi conversation entry imported roughly 700 JavaScript modules on every new
process. The Android module-load probe measured 593.299 ms median for the
original entry versus 38.846 ms for an esbuild bundle (four samples each,
compilation cache disabled). This excludes Node process startup and model work.
A separate compilation-cache probe had much smaller and variable gains; caching
was not enabled in the app. Temporary probe files and cache were removed.

The runtime build now bundles pi-conversation.mjs before fingerprinting and
running the existing tests. The same bundled file ships to Android and runs in
the desktop harness. The build rejects unexpected external imports or multiple
outputs. The source agent, tool schemas, prompts, native credential boundary,
action loop and independent final screenshot query are unchanged. Third-party
notices remain included. All 70 runtime tests passed against the bundled entry,
including cancellation, steering, resumable state and verified completion;
Kotlin unit tests, APK build, bridge smoke and local quick checks also passed.

Installed runtime `0.1.0-fca1d1c93fe0` matched the asset fingerprint
`e930b59b0bb96104ec63950f441f2d6ae931a439e887209c36376b1b699cb60e`.
The update task succeeded in 48.144 s, including 9.719 s runtime installation;
it is not a warm-task sample.

| Brightness sample | Full task | Pi startup | Outer model calls | Visual HTTP requests |
| --- | ---: | ---: | ---: | ---: |
| Phone warm 1 | 37.810 s | 222 ms | 1 | 4 |
| Desktop same runtime | 39.755 s | not separately instrumented | 2 | 4 |
| Phone warm 2 | 44.776 s | 225 ms | 1 | 5 |

Both native warm tasks performed three device actions and independently read
auto-brightness as enabled. Pi startup was 222/225 ms versus the previous
659/543 ms; work outside outer completion and the inner runner was 2.268/2.154 s
versus 2.698 s in both preceding warm tasks. This is reproducible local-stage
progress, not proof that the whole-task change is attributable to bundling.

The second warm task encountered an incomplete planning response (no action,
complete or error marker), then retried planning before the independent query.
Its SDK task usage says retry_count=0 and contains four successful model tasks,
but transport telemetry records five HTTP calls. Preserve this sample and count
the parsing retry separately. Do not report it as zero retries, subtract its
cost, or treat incomplete text as proof of successful task completion.

The two native warm samples have a 41.293 s median. The desktop's extra outer
summary round makes the one desktop sample unsuitable for a stable parity claim.
The original brightness half-latency target of 27.560 s remains unmet. The next
investigation should address the planning-format retry and keep transport calls
separate from successful report tasks when comparing performance.

[Startup probes, complete-task timings and retry evidence](performance-pi-bundle-20261017.json)


### Correct semantic response retries without transport backoff (2026-10-17)

The preceding 44.776 s phone run returned plain prose (`自动调节已开启。`)
instead of a planning completion marker. Parsing rejected it correctly, but
then waited 2.013 s before requesting a corrected response. The SDK reused the
transport retry interval for this semantic correction. The pinned core patch
now corrects successful HTTP responses immediately; network retry backoff,
retry bounds, feedback and cancellation remain intact. Both language prompts
explicitly require the current action protocol's completion marker. Plain prose
is still rejected; an independent final screenshot query remains required.
This saves the fixed delay only when malformed output occurs, not two seconds
on every successful task, and does not remove the corrective model call.

Six focused ESM/CommonJS tests cover the reproduced response, bounded failures,
cancellation and preserved transport backoff. Runtime bundle validation passed
74 tests before the two additional transport tests were added; the final six
focused tests passed separately. APK/Kotlin checks and local quick checks passed.
The candidate was installed and exercised on OPPO e7ed3f17. Runtime fingerprint:
`87a158749b1eb9e1891a9bd7dc327e601290e24d7376a27fe622376c179563e3`.

The task remains reading auto-brightness from Settings without changing it.
Both hosts use the same runtime, gpt-6-luna, account, Node 24.18.0, 988×1972
screenshots and independent visual verification. Each run starts at the visually
checked Settings root. Device operations are serialized. Server scheduling,
cache hits and model output remain uncontrolled; live screenshots are not
byte-identical. The desktop measurement is the same-agent harness, not Codex UI
latency. The benchmark now records the model's actual `finishWhenVerified`
choice so extra summary calls cannot silently skew the comparison.

The update run was 52.045 s including 9.221 s runtime preparation and is retained
separately from warm samples. All four warm tasks independently confirmed auto-brightness enabled.

| Warm sample | Phone | Desktop same runtime |
| --- | ---: | ---: |
| 1 | 36.231 s | 34.348 s |
| 2 | 38.717 s | 31.956 s |
| Median (two samples) | 37.474 s | 33.152 s |

Each warm task used one outer model call, four visual HTTP requests and three
device actions. The desktop explicitly requested `finishWhenVerified=true` in
both samples. Phone planning logs contain no semantic parsing retries; the
successful report count agrees with HTTP telemetry. These successful samples
therefore do not measure the conditional two-second retry saving directly.

The phone median is 13.0% slower than desktop. The difference decomposes into
2.624 s additional visual HTTP time, 1.653 s additional work outside the outer
model and inner runner, 0.112 s additional outer model time, and 0.066 s less
inner non-HTTP time. Those phase totals are additive; startup and capture are
nested and must not be added again. HTTP time includes network and server work,
so this does not establish a pure network or inference bottleneck.

Against the original 55.119 s brightness median this is a 32.0% reduction,
not the requested 50%; the 27.560 s target remains unmet. Two samples per host
are insufficient to claim stable parity or broad task equivalence. Next work
should separately profile the remaining phone lifecycle/persistence overhead
and repeat identical-request route tests while preserving live-task results.

[Complete task timings and semantic retry evidence](performance-semantic-retry-20261017.json)


### Preserve history cache identity across task insertion (2026-10-17)

Ordinary map keys recursively hashed immutable history records on every encoding
cache lookup. Identity keys avoid that work, but require snapshot reuse across
startup and list reordering. The first candidate exposed both requirements:
without startup handoff, initial writes took 1,065/658 ms; after adding handoff,
prepending a task still shifted every record and caused 774/640 ms writes.
Those intermediate samples are preserved, not presented as production baselines.

The final change uses identity keys, seeds the writer from the last successfully
persisted immutable snapshot, and aligns moved object records by unique string
ID before recursively checking all fields. Duplicate IDs fall back to positional
comparison. Changed nested content still creates a new snapshot. Atomic writes,
fsync, cancellation barriers and history retention are unchanged. Tests cover
startup reuse, insertion/reordering, nested mutations, duplicate IDs and cache
lookups that neither hash nor re-encode unchanged records. Kotlin unit tests,
APK build and local quick checks passed; the final APK is installed on OPPO.

The first task after the final APK restart succeeded in 39.659 s. Its second
write was 22 ms and six flush calls totaled 2 ms. First-after-restart results
are kept separate from warm tasks. The same Node runtime fingerprint, model,
account, task and visual verification remain in use. This Kotlin-only change
does not change the runtime bundle fingerprint.

The final warm task took **45.905 s**, with three actions, four visual requests,
zero parsing retries and successful independent verification. Its write timings
were 28/23/22/24/24/25/61/24 ms and six flush calls totaled **3 ms**. The preceding
production warm sample had 350 ms across seven observed writes and 127 ms of
flush waiting; the new sample has 231 ms across eight writes. These are local
stage observations, not a controlled whole-task gain. Snapshot work was 418 ms
versus 429 ms, and work outside the outer model/inner runner was 2.601 s versus
2.260 s, so the remaining lifecycle difference is not resolved.

Visual HTTP time increased to **27.454 s** (previous production warm samples:
19.439/20.792 s); outer model time was 5.722 s and inner non-HTTP time 10.128 s.
The complete task therefore got slower despite reduced persistence waiting.
Do not exclude this sample, claim overall speedup from the cache change, or use
intermediate regressions as the production baseline. The recovered intermediate
warm run used the same task ID after its observer expired; no action was replayed.
Stable desktop parity and the original 50% full-task goal remain unproven.

[All intermediate and final device samples](performance-history-cache-20261017.json)


### Recheck model routing after cache optimization (2026-10-17)

Two more eight-request alternating cohorts replayed the same saved planning
body, screenshot, bearer credential and Node 24.18.0 on desktop and Android.
All 16 model calls completed as gpt-6-luna with 11,234 input tokens.

| Route | Desktop median | Phone median | Desktop/phone headers |
| --- | ---: | ---: | ---: |
| Existing routes | 5.671 s | 7.293 s | 2.815 / 3.954 s |
| Explicit phone localhost HTTP proxy | 5.292 s | 6.843 s | 2.532 / 4.345 s |

The proxy cohort retained a 29.3% phone gap (existing routes: 28.6%), so the
previous near-parity proxy observation did not reproduce. No production proxy
configuration was changed. Baseline phone requests all cached 10,624 input
tokens, while desktop cache counts were 8,192 or 10,624; the phone difference
cannot be explained by a larger input or less cached input in these samples.
Outputs, server scheduling and route behavior still vary.

The replay harness now accepts `--transport-probe` in place of a saved request.
It sends unauthenticated GET requests to /v1/models, checks the expected 401,
and records timing without reading or transmitting credentials or screenshots.
Eight alternating probes had 0.474 s desktop / 0.550 s phone median total time.
This is evidence against attributing the entire model gap to generic connection
latency, but the different endpoint and authentication path make it an imperfect
network reference. Do not subtract these medians from model time and label the
remainder inference. The server did not provide a processing-time breakdown.

Both scripts passed syntax checks and the new probe passed live verification
on both hosts. Worker files were cleaned up. This cohort executes no phone
UI actions and supplies no new complete-task latency or parity result. Further
work must separate model-specific request handling and generation from transport
rather than repeatedly tuning a generic keepalive/proxy setting.

[Request timings and usage counts](performance-route-refresh-20261017.json)


### Separate response startup and output delivery (2026-10-17)

The read-only replay worker now timestamps response.created, response.in_progress,
first/last output text delta and response.completed while consuming the stream.
It records no extra model content. Incremental UTF-8/SSE parsing is tested across
single-byte chunks, CRLF and an unterminated final [DONE]; a stream missing the
completion event remains a failure. This instrumentation is diagnostic-only.

Another eight alternating requests used identical request/image hashes, bearer
credential, Node and gpt-6-luna. All eight had 11,234 input tokens and 10,624 cached
tokens and completed successfully.

| Median milestone from request start | Desktop | Phone |
| --- | ---: | ---: |
| Headers | 2.850 s | 3.141 s |
| First output text | 4.241 s | 4.297 s |
| Complete stream | 6.292 s | 6.083 s |
| First output to completion event (per-request interval) | 1.734 s | 1.750 s |

The phone was 3.3% faster in this cohort, unlike the preceding roughly 29% gaps.
This contradicts a fixed phone throughput penalty; output delivery intervals
are similar when cache counts match. It does not prove stable full-task parity
or identify provider queueing, transit and inference separately. Different
milestone medians are not additive phase durations. Completion-event-to-EOF
also varied; the worker continues to drain and validate the entire stream.

The full native task uses its own phone OAuth session, while these cross-host
replays share the desktop benchmark credential. Same account is not the same
credential/session. A next attribution experiment should preserve that
distinction, without exporting phone credentials, and recheck complete tasks
alongside the remaining native lifecycle cost. The 50% complete-task target
remains unmet; no product transport, model or screenshot setting changed.

[Stream milestones, tokens and transport timings](performance-stream-phases-20261017.json)


### Attribute native lifecycle and remove redundant work (2026-10-17)

New numeric-only traces split conversation service calls and runner preparation,
first event/RPC, exit and report import. They do not log parameters, credentials
or returned content. The first baseline after restarting the app took 40.133 s;
its service capability calls took 279/141 ms, runtime/project preparation 9/11 ms,
process start to first event 183 ms and start to first RPC 975 ms. The latter
boundaries overlap and must not be summed.

Two local issues were corrected. Launcher labels were loaded inside the sorting
comparator and then again while serializing; each distinct app now loads its
label once, sorts that string and still queries fresh on every request. No
persistent catalog cache or stale-install/locale behavior was introduced.
Also, service results already on the main thread were being posted back to that
same queue. They now deliver directly; call entry remains asynchronous and
background results still dispatch to main. Callback removal still precedes
invocation so a response is delivered at most once.

The label-only candidate completed in 40.125 s. Catalog work was 115/20/52 ms,
but the outer capability calls still took 229/139 ms. With direct main-thread
result delivery, first-after-restart completion was 37.100 s. Capability calls
were 371/63 ms and status calls 47/14 ms versus 229/139 and 295/50 ms respectively.
The first capability call got slower; waiting can move to the next queued call.
Combined capability/status time fell from 713 to 495 ms, not by the sum of only
the improving calls. Outside-model/runner time fell from 2.971 to 2.812 s.
Most of the whole-task reduction was lower visual HTTP time (20.911 to 17.906 s),
so do not attribute the roughly three-second improvement to these code changes.

Kotlin unit tests, APK build and local quick checks passed. The final APK is
installed on OPPO e7ed3f17. Model, account, full-size visual input and independent
final query are unchanged. All three initial samples succeeded with three
actions and four visual requests. These first-after-restart samples are not a
warm median, and there is no new desktop comparison in this cohort.

The final warm task also succeeded: **39.463 s**, three actions, four visual
requests, zero parsing retries. Its outer model was 4.626 s, visual HTTP 22.890 s,
inner non-HTTP work 9.693 s and outside-model/runner work **2.254 s**. Capability
and status calls summed to 471 ms. First event/RPC timings remained 230/1,044 ms;
process exit and report import were 74/51 ms. Runner preparation was only 13 ms,
so runtime extraction/project creation is not the warm bottleneck. Startup/module
initialization remains worth investigating. This sample does not establish
stable desktop parity or the 27.560 s complete-task target.

[Lifecycle traces and complete-task evidence](performance-lifecycle-20261017.json)


### Defer the unused config transpiler (2026-10-17)

The pinned test runner imported `tsx/cjs/api` at module initialization even
when loading Tapilot's native `.mjs` config. Its four distributed entrypoints
now load that fallback only when a compatible non-native config needs it.
The existing unbounded-timeout patch is preserved. Separate-process tests cover
ESM/CommonJS package entries with native configs, TypeScript ESM and TypeScript
CommonJS syntax. Native loading asserts no tsx module was initialized, while
TypeScript configs still load. The tests failed on the original eager import
and passed on the patch. All 78 runtime tests, bundle smoke/doctor checks,
Kotlin tests, APK build, runtime-integrity tests and local quick checks passed.

Runtime `0.1.0-1ed9deaf4fd2` was installed on OPPO, fingerprint
`d7b9023be558f1a079e9f1532d8ef8f22d7949a1a4a84dc7d7b1dcd17354e2fa`.
The first complete brightness task succeeded in 49.844 s including runtime
update preparation; it is not a warm sample.

Four isolated imports per version, compilation cache disabled, measured old
1,052/1,097/1,070/1,090 ms versus new 1,165/853/1,034/1,094 ms. Medians are
1,080/1,064 ms: only a 16 ms nominal reduction with substantial variance.
This establishes removal of unnecessary initialization, not a significant
startup or whole-task speedup. The update task still took 1,088 ms from process
start to its first RPC, so the deferred transpiler was not the principal cause
of that boundary. No model, action protocol or screenshot quality changed.

The update task included 10.693 s runtime preparation. The subsequent warm task
succeeded in **36.459 s**: one outer model call (4.570 s), four visual requests
(20.001 s), three actions, zero parsing retries and independent final query.
Inner non-HTTP work was 9.630 s and outside-model/runner work 2.258 s. Process
start to first event/RPC was 222/1,064 ms, essentially unchanged from the prior
230/1,044 ms warm trace. The faster total is not evidence that this loader patch
caused a material gain. The original 27.560 s target remains unmet.

[Import probes and complete-task results](performance-lazy-loader-20261017.json)


### Bound speculative module preload benefit and cost (2026-10-17)

An isolated Android Node prototype alternates fresh-process normal imports and
preloading the four main SDK/adapter ESM entries before importing android.config.
It performs no setup, model call or phone action. The diagnostic rejects fetch
and native RPC output, disables compilation cache, uses the installed runtime,
and waits one second between preload and config import. Eight samples (four per
condition) complete under the app UID, with Node 24.18.0 and fingerprint
`d7b9023be558f1a079e9f1532d8ef8f22d7949a1a4a84dc7d7b1dcd17354e2fa`.
The reproducible CLI is:

```sh
python3 scripts/benchmark-runtime-preload.py --serial e7ed3f17
```

Median remaining config-import time was **1,102 ms normal / 241 ms preloaded**.
Preload itself took 823 ms. This suggests roughly 861 ms could move off the
critical path if actual model waiting overlaps it; it does not remove that work.
An earlier isolated prototype also reduced the remaining import from about
1,047 to 249 ms. Neither probe is a complete task or a measured overlap with
an actual model request.

Median RSS immediately before the one-second wait was 45.7 MiB normal / 102.3 MiB
preloaded. Preloaded processes used 128.8 ms CPU during that first second versus
5.2 ms normal, including post-import engine work. RSS includes shared pages and
must not be reported as exact additional physical memory. After the probe the
phone reported 24% battery, 34 C battery temperature, thermal status 0 and no USB
power; this does not establish thermal equivalence with earlier tasks.

Production preload is **not enabled**. Integration must preserve the working
directory and model environment, stop all prepared processes on cancellation or
unused-task completion, and prevent any native/model request before activation.
A bounded prepared process during an active task is preferable to a permanent
worker. These boundaries and full-task latency must be verified before claiming
this projected saving. Stable desktop parity and the 50% target remain unmet.

[Raw prototype timings and memory observations](performance-preload-prototype-20261017.json)

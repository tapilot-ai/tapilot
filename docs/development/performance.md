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


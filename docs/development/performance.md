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

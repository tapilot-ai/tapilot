# Screenshot compression benchmark

## Current pipeline

Tapilot's native capture returns PNG. This does **not** mean the model receives
PNG: the bundled Midscene Agent prepares the image as JPEG quality 90 before
inference. Its default `screenshotShrinkFactor` is 1 (original resolution).
The sibling Midscene checkout uses the same implementation:

- `packages/core/src/agent/screenshot-preparation.ts`: JPEG quality 90 and
  `round(originalDimension / screenshotShrinkFactor)`.
- `packages/shared/src/img/transform.ts`: Sharp in Node, PNG-to-JPEG conversion,
  and reuse of existing JPEG bytes when no resize is needed.
- `packages/core/src/agent/utils.ts`: screenshot preparation and coordinate
  scaling using the actual prepared dimensions.

Reducing JPEG file size alone does not necessarily reduce image token counts.
Resizing changes the dimensions the model sees. The benchmark uses the product's
bundled preparation function, rather than an independently implemented encoder.

## Encoding measurements, 2026-10-14

Two captured OPPO screens, both 988 × 1972: Android Accessibility settings and
the Tapilot conversation menu. Seven warm encoding samples per variant; these
are local measurements on the development Mac. The WASM column forces Sharp's
Android backend on the Mac; it is **not physical-phone CPU timing**.

| Variant | Dimensions | Settings image | Menu image | Native encode p50, settings/menu | WASM encode p50, settings/menu |
| --- | --- | ---: | ---: | ---: | ---: |
| Original PNG reference | 988 × 1972 | 106.1 KiB | 225.2 KiB | No conversion | No conversion |
| JPEG 90, current default | 988 × 1972 | 87.0 KiB | 202.8 KiB | ~8 / 10 ms | 17.5 / 22.1 ms |
| JPEG 90, shrink factor 1.5 | 659 × 1315 | 48.5 KiB | 114.7 KiB | ~14 / 16 ms | 30.9 / 37.9 ms |
| JPEG 90, shrink factor 2 | 494 × 986 | 33.0 KiB | 79.1 KiB | ~13 / 16 ms | 27.6 / 37.0 ms |

Relative to the current default, factor 1.5 removes 43–44% of image bytes;
factor 2 removes 61–62%. Base64 adds approximately one third to all payloads.
Resizing adds encoding work rather than saving it. The purpose is to reduce
transport and model image-processing work.

## Live model results

24 successful requests from the development Mac to the authorized `gpt-6-luna`
provider, six per variant. Percentiles use nearest rank; with six observations
the reported p95 is the maximum. Every request reported zero cached input tokens.

| Variant | Input tokens per request | First-text p50 | Full-response p50 | Full-response p95/max | Expected-label checks |
| --- | ---: | ---: | ---: | ---: | ---: |
| Original PNG reference | 2,367 | 2.744 s | 3.625 s | 7.043 s | 6/6 |
| JPEG 90, current default | 2,367 | 2.837 s | 3.872 s | 4.455 s | 6/6 |
| JPEG 90, shrink factor 1.5 | 1,119 | 2.254 s | 3.246 s | 4.540 s | 6/6 |
| JPEG 90, shrink factor 2 | 656 | 5.120 s | 5.750 s | 8.862 s | 6/6 |

Factor 1.5 reduces input tokens by 52.7% and has a 16.2% lower observed full-response
p50 than the current JPEG default. Its maximum is slightly higher. Factor 2
reduces input tokens by 72.3%, but its observed p50 is 48.5% slower. It generated
reasoning tokens on four of six calls (mean 127.5), compared with two calls for
full JPEG (mean 22) and none for factor 1.5. This records a difference in model
behavior; it does not establish that resizing caused the extra reasoning.

PNG and full JPEG have identical input token counts despite different byte
sizes. Network/provider variance and variable output lengths remain. Six samples
per variant do not establish a stable speedup or general recognition accuracy.
Factor 1.5 is a candidate for a subsequent native-task/coordinate-accuracy A/B
test. Production defaults remain unchanged by this benchmark.

## Native A/B experiment

The debug APK supports a test-only `tapilot-performance` preference read by the
native runner. The performance instrumentation sets it to 1 or 1.5 before each
sample, and restores the previous preference in `finally`. Non-debug APKs always
use factor 1. The Node Agent passes this value directly to Midscene's existing
`screenshotShrinkFactor`; no alternative encoder or coordinate mapping is added.

```sh
TAPILOT_E2E_DEVICE_ID=e7ed3f17 \
TAPILOT_PERF_APK=app/build/outputs/apk/debug/app-debug.apk \
TAPILOT_PERF_SAMPLES=7 \
TAPILOT_PERF_SCREENSHOT_AB=1 \
TAPILOT_PERF_MANUAL_ACCESSIBILITY=1 \
TAPILOT_PERF_OUTPUT=build/device-image-ab \
node scripts/run-device-performance.mjs
```

Install both debug and test APKs first. The task opens Settings, taps Display &
brightness, verifies it, returns, and verifies the main Settings page. Samples
alternate factors 1 and 1.5. Use seven samples and treat the first factor-1 sample
as warmup, leaving three samples per factor for the comparison. The collector
requires a successful native assertion, a successful coordinate Tap task, and a
model screenshot with the requested scale ratio for every A/B sample. Model
reports retain screenshots for manual intermediate-screen verification.

These commands require an unlocked phone and a bound accessibility service.
Some OPPO firmware requires confirming installation and manually toggling the
already-authorized service after instrumentation starts. Setup is not a measured
sample. A completed build is not evidence that this experiment passed.

## Reproduction

```sh
pnpm runtime:bundle
node scripts/benchmark-image-compression.mjs
node scripts/benchmark-image-compression.mjs --wasm
# Uses the Android Use plugin's existing authorized account; makes 24 requests.
node scripts/benchmark-image-compression.mjs --model
```

The two local PNG fixtures must exist under `build/performance-baseline/` as
listed in the script. They are local device artifacts, not committed fixtures.
Outputs are under `build/image-compression/{native,wasm}/`: encoded images,
`encoding.json`, and, for live requests, `model-requests.json` and
`model-summary.json`. Credentials and model response text are not recorded.
Set `TAPILOT_IMAGE_BENCHMARK_MODEL` to select another authorized model.

The live benchmark uses the same prompt and `detail: high`, two screens and
three rounds per variant, serial requests, with format order reversed in the
middle round. It records first-text and full-response time, input/output/cache
token counts, and presence of five expected labels across the two screens.
It stops on the first request error without retrying. These are Mac-to-provider
request timings, not the phone's complete autonomous-task loop. Label checks do
not establish click-coordinate accuracy or general small-text recognition.

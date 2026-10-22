# Script entry points

Run commands from the repository root. Keep the npm scripts in `package.json` as
the normal contributor interface; individual scripts below support focused work.

| Purpose | Entry points |
| --- | --- |
| Local verification | `check.mjs`, `verify-apk.mjs`, `runtime-integrity.mjs` |
| Runtime build | `bundle-node-runtime.mjs`, `fetch-node-runtime.sh`, `patch-node-elf.py`, `android-runtime-tree.mjs` |
| Generated UI assets | `generate-brand.mjs`, `visual-baseline.mjs` |
| Dependency materials | `third-party-notices.mjs`, `prepare-distribution-materials.mjs`, `distribution-readiness.mjs` |
| Model-free benchmark | `benchmark-conversation.mjs` |
| Real model/device measurement | `run-device-performance.mjs`, `compare-full-conversation.mjs`, `compare-native-desktop.mjs`, `benchmark-model-path.mjs` |
| Shared measurement code | `lib/`, `native-latency-summary.mjs`, `inspect-native-benchmark.mjs`, `collect-native-benchmark.py` |

`check.mjs` runs every `scripts/*.test.mjs` after staging the runtime. If tests move
into subdirectories, update discovery at the same time. Runtime tests live in
`node-runtime/tests/`, Android tests in `app/src/test/`, plugin tests in
`plugins/android-use/test/`, and visual device E2E in `tests/midscene/`.

Real model/device scripts are opt-in and may use paid model calls or change phone
state. Keep their model/network/device inputs and failure evidence with the report.
The model-free benchmark measures host loop/serialization overhead only; it cannot
establish phone task latency. See the [development guide](../docs/development/README.md)
and [current validation](../docs/development/validation-current.zh.md).

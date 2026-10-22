# Development

See [CONTRIBUTING](../../CONTRIBUTING.md) for host prerequisites, installation,
local builds and signing. Run commands from the repository root.

```sh
pnpm test:legacy
pnpm --filter @tapilot/android-accessibility test run --pool.maxWorkers 2
pnpm check:plugin
pnpm runtime:bundle
./gradlew :app:testDebugUnitTest --max-workers=2 --no-parallel --no-daemon
./gradlew :app:lintBeta --max-workers=2 --no-parallel --no-daemon
```

`runtime:bundle` builds the adapter, installs the frozen production dependencies,
copies `node-runtime/src`, and runs the tests from `node-runtime/tests` against
that staged tree. It then runs doctor and YAML/stdio smoke projects before
creating the ZIP and manifest. Tests run without real provider credentials.
Do not run Node tests from their source directory: deployment dependencies are
intentionally installed only in `build/node-runtime-bundle`.

For the optional legacy speech verification package, run
`TAPILOT_SPEECH_VERIFICATION=1 pnpm build:legacy`, then build with
`-PspeechVerification`. The output asset path remains `dist/runtime`.

## Dependency changes

See the [compatibility matrix](compatibility.md) for phone/plugin version pins
and the required upgrade checks.

- Root/adapter: update `pnpm-lock.yaml` with pnpm 12.4.1.
- Plugin: update `plugins/android-use/package-lock.json` with npm and regenerate
  its UI with `npm --prefix plugins/android-use run build:ui` when needed.
- Node deployment: update `node-runtime/bundle/pnpm-lock.yaml` with
  `pnpm --dir node-runtime/bundle install --lockfile-only`, then run `pnpm runtime:bundle`.

The adapter uses a real local `file:` dependency. Keep its package metadata and
exports accurate; the normal build creates `dist` before the deployment install.
See the [patch record](../../node-runtime/bundle/patches/README.md) before updating Midscene.

Run `pnpm check` before handing off changes. It includes full Beta Android Lint;
the release build's fatal-only lint check is not a substitute. A passing local gate does not prove
that CI or a physical device ran; record those results separately.

The default gate also runs the model-free conversation benchmark: five samples,
each containing four separate 30-operation tasks (120 operations and 124 model
turns in total). It checks completion and checkpoint counts while measuring loop
and serialization overhead. The old single 120-operation transcript exceeded the
production context budget as tool contracts grew; it is no longer a valid timing
baseline. The application's budget guard remains unchanged.

Security overrides in both pnpm workspaces and the plugin pin Sharp 0.35.5,
js-yaml 4.3.2 and UUID 11.1.1 until upstream ranges include the patched releases.
The pnpm workspaces also patch affected glob 11 releases to 11.1.0. Keep the
Sharp WASM package at the same version as Sharp. Node bundle tests exercise
YAML parsing, UUID buffers and screenshot pixels using both native Sharp and
the Android-selected WASM fallback on the host. They do not replace device tests.

Run `pnpm check:security` to audit all four dependency trees against the registries; CI
runs this separately from the deterministic build gate. Remove an override only
after regenerated lockfiles, audits and compatibility tests pass without it.

## Midscene device E2E

See the [E2E guide](../../tests/midscene/README.md). `pnpm check:e2e` validates the
standalone package without a phone or model. `pnpm test:e2e` runs visual smoke
cases on an explicitly selected device; `pnpm test:e2e:live` opts into native
Tapilot tasks. Install test dependencies with `npm ci --prefix tests/midscene`.
CI validates definitions; device and model runs remain explicit.

## Native package pins

The Termux apt pool is rolling. A clean build on 2026-10-15 exposed expired
libc++ 29, SQLite 3.53.4 and OpenSSL 3.6.3 download paths that local caches had
hidden. Pins now match the available libc++ 30, SQLite 3.54.0 and OpenSSL 3.6.5
archives, with SHA-256 values from the repository index. The Node version stays
24.18.0. The download script never silently selects a newer package or skips
checksum verification. If an archive disappears again, verify new pins and
Android ABI behavior, or use an approved snapshot mirror through `TERMUX_POOL`
that serves the same pinned bytes. Do not treat an old local cache as clean-build
proof. A long-lived binary mirror also requires redistribution review.

The native downloader also accepts `TERMUX_CACHE` for an isolated cache. The SQLite pin is now 3.54.1 (official Termux index SHA-256), replacing the removed 3.54.0 archive; ELF library names change together. Current release evidence is indexed in [validation-current.zh.md](validation-current.zh.md).

On 2026-10-22 an empty project cache exposed two more removed archives. The
[official aarch64 index](https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages)
now pins Node's packaging revision `24.18.0-2` and c-ares `1.34.9`. Node's
JavaScript runtime version remains 24.18.0. Their SHA-256 values are retained in
`scripts/fetch-node-runtime.sh`; downloads still fail closed on mismatched bytes.
These repackaged native binaries require device acceptance before release.

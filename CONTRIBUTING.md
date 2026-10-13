# Internal development of Tapilot

Tapilot is proprietary software. This guide is for authorized developers.

## Setup

Use JDK 17, Android SDK platform/build tools 35, Node.js and pnpm. Set
`JAVA_HOME` and `ANDROID_HOME`, then run `pnpm install --frozen-lockfile`.

## Required checks

Run `pnpm check` (also available as `pnpm test`) from the repository root.
It executes, sequentially:

- Generated theme validation and TypeScript checking.
- Legacy runtime regression tests and Android adapter tests (two workers).
- A fresh adapter/Node bundle build, all Node agent tests, and runner smoke tests.
- Report export, failure-report and runtime integrity regression tests.
- Kotlin unit tests and a non-debuggable Beta APK build.
- APK package/version, runtime freshness and cryptographic signature verification.
  Local checks accept a developer certificate; distribution separately pins the
  installed Beta certificate through `pnpm build:android`.

The command automatically uses `~/.local/bin/dev-gate` when installed. CI hosts
without that local gate run the same checks directly. Do not disable tests or
ignore exit codes to publish. Physical-device validation is still required for
microphone access, gestures, foreground services and upgrade behavior.

For focused development, `pnpm test:legacy` runs only the legacy runtime tests.
`pnpm --filter @tapilot/android-accessibility test run --pool.maxWorkers 2` runs
adapter tests. `./gradlew :app:testDebugUnitTest --max-workers=2 --no-parallel
--no-daemon` runs Kotlin tests. These are subsets, not the release gate.

## Build and signing

`pnpm build:android` builds `app/build/outputs/apk/beta/app-beta.apk`.
Beta disables debugging but deliberately retains the existing installed Beta
certificate for in-place upgrades. It is not a production signing identity.
A different machine's default debug keystore will fail distribution certificate verification;
do not uninstall an existing app to get around that check.

Production uses `pnpm build:release` and requires these environment variables:

- `TAPILOT_SIGNING_STORE`: private keystore path.
- `TAPILOT_SIGNING_STORE_PASSWORD`, `TAPILOT_SIGNING_KEY_ALIAS`,
  `TAPILOT_SIGNING_KEY_PASSWORD`: credentials provided outside the repository.
- `TAPILOT_SIGNING_CERT_SHA256`: expected certificate digest, independently recorded.

Release fails without explicit signing configuration. Changing signing identity
requires a planned migration; do not silently replace an existing certificate.
Never commit a keystore, password, API key or exported private app data.

Direct Gradle packaging verifies the Node source fingerprint and ZIP digest. If
it reports stale resources, run `pnpm runtime:bundle`; this also rebuilds the
adapter. `pnpm runtime:node` prepares checksum-verified ARM64 native libraries.

## Architecture

- `app/`: native Kotlin/Compose UI, app-owned conversation/audio state, storage,
  Android device interface and subprocess lifecycle.
- `node-runtime/assets/node-runtime/`: Pi agent and Midscene runner source.
- `packages/android-accessibility/`: device adapter and native protocol.
- `runtime/`: legacy web implementation and optional speech verification entry;
  excluded from ordinary APKs. It does not own the current app's conversation.
- `scripts/`: reproducible bundling, checks and APK verification.

Floating voice transitions belong to `FloatingVoiceSession`, driven by
`AssistantRuntime`; views must not mutate independent lifecycle flags. Public
Android intents may open/share into the UI but cannot request recording.
Conversation snapshots are persisted by a serial I/O writer; external operations
wait for durable progress. Draft writes do not rewrite task history.

## Review

Keep changes focused and include regression tests for lifecycle, cancellation,
serialization and provider boundaries. Update both README locales when changing
user-facing behavior. Include real device evidence without secrets. Read
[SECURITY.md](SECURITY.md) for private vulnerability reporting and
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for collaboration expectations.

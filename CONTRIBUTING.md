# Contributing to Tapilot

Thanks for your interest in Tapilot! This document covers the development
setup, testing expectations, and the pull request process.

## Development setup

Requirements:

- JDK 17
- Android SDK platform 35 and build tools
- Node.js and [pnpm](https://pnpm.io/)

Clone and install:

```sh
pnpm install
```

Build the Web UI, the device adapter, the bundled Node runtime, and the debug
APK:

```sh
pnpm run build:android
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For frontend-only work, the Rsbuild dev server is available with
`pnpm run dev`.

## Project layout

- `app/` — the Android application (Kotlin): AccessibilityService, native
  Compose chat UI, bridge, runtime process management, and report storage.
- `runtime/` — the conversation agent, native chat state/command contract,
  remaining WebView settings/advanced UI (TypeScript), and the JavaScript side
  of the JSON-RPC bridge.
- `packages/android-accessibility/` — the independently buildable device
  adapter implementing the Midscene device interface.
- `scripts/` — build scripts for bundling the on-device Node runtime.

## Testing

Please run the relevant checks before opening a pull request:

```sh
# Web UI and bridge tests
pnpm test

# Device adapter tests
pnpm --filter @tapilot/android-accessibility test

# JVM unit tests
./gradlew :app:testDebugUnitTest
```

Behavior that depends on OEM gesture handling or system UI cannot be covered
by unit tests alone and needs verification on a physical device.

## Pull request process

1. Fork the repository and create a topic branch from `main`.
2. Keep commits focused and write clear commit messages.
3. Add or update tests for code changes, and update documentation when user
   facing behavior changes.
4. Make sure all tests pass.
5. Open the pull request and fill in the pull request template.

## Reporting issues

- Bugs: use the **Bug report** issue template and include device model,
  Android version, and reproduction steps.
- Feature ideas: use the **Feature request** template.
- Security vulnerabilities: follow [SECURITY.md](SECURITY.md); do not open a
  public issue.

## Code style

- Follow the existing conventions in each part of the codebase (Kotlin for
  `app/`, TypeScript for `runtime/` and the adapter).
- An [.editorconfig](.editorconfig) is provided; please make sure your editor
  respects it.

By participating, you agree to uphold the [Code of Conduct](CODE_OF_CONDUCT.md).

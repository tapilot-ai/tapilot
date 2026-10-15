# Local release preparation — 2026-10-15

## Implemented

- Phone operations perform `aiAct`, `aiAssert`, then an independent `aiAsk`
  screenshot reading. Cross-page facts are requested as separate operations.
  Empty observations cannot fall back to action summaries or execution logs.
  Android and vendor OS versions are explicitly distinguished.
- Android runtime packaging retains Android arm64 and WASM packages and removes
  host-only binaries from a separate deployment tree. Host test dependencies remain
  available for local verification. A WASM PNG encode/decode smoke check passed.
- Eight exact-version npm license supplements and 60 Maven notice sets are included
  in the APK. Maven notice sets preserve source headers and full Apache terms,
  including resolved Guava and ZXing parent-POM declarations.
- `pnpm release:materials` verifies and assembles 129 pinned artifacts, including
  60 Android source archives. See [material provenance and remaining gaps](../../third-party/README.md).

## Local evidence

On macOS, Node 24.21.0, pnpm 12.4.1, JDK 17 and Android SDK 35:

- Four dependency audits passed with no reported vulnerabilities.
- Full `pnpm check` passed, including plugin, definitions, TypeScript, legacy UI,
  adapter, 42 Node runtime tests, 9 script tests, Kotlin tests and Beta APK verification.
- Kotlin: 228 tests, zero failures/errors, one skipped. Gradle reused some outputs.
- Exact APK SHA-256: `c9dd336bb6c15bfd98019c71744e060b86f80b8fcbf7321171cad6f31c0994e3`.
- APK contents independently inspected: 282 npm components (7 review flags),
  7 native packages (3 review flags), 60 Android components (no missing-notice flags).
  No macOS Sharp/esbuild binaries remain; Android esbuild is present.
- APK uses the local development signing certificate. It was not installed or
  approved for distribution. Absence of missing-notice flags is not a comprehensive
  redistribution assessment.
- Detailed local logs: `artifacts/release-readiness/verified-check.log` and generated
  materials: `build/distribution-materials/`. Both directories are ignored.

## Not completed

- Original OPPO `e7ed3f17` was disconnected. The connected Samsung and emulator were
  not substituted without the user's device selection. No new real-device E2E,
  install/upgrade or Stop/recovery pass is claimed. LONG-02 must be rerun.
- Production signing awaits the user's choice of an existing configuration or a
  new local key. No production identity was created or changed.
- WASM corresponding sources/rebuild provenance, native recipe/source matching,
  and the remaining npm notice gaps are unresolved. APK publication stays blocked.
- Repository publication and GitHub CI work are excluded by the user's request.
  Changes are committed locally without pushing or dispatching workflows.

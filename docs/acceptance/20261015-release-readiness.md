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
- APK uses the local development signing certificate. It was installed in place
  on both selected physical phones; it is not approved for distribution. Absence
  of missing-notice flags is not a comprehensive redistribution assessment.
- Detailed local logs: `artifacts/release-readiness/verified-check.log` and generated
  materials: `build/distribution-materials/`. Both directories are ignored.

## Device acceptance and remaining work

- Both selected physical phones now have the candidate APK installed in place:
  Samsung `R5CN90V1AYP` and OPPO `e7ed3f17`. Both previous APKs use the same
  development certificate as the candidate; app data was preserved.
- Samsung initial smoke: UI-01 and UI-02 passed; UI-03 failed while searching
  for wireless control after retaining a lower settings scroll position. A prompt
  clarification fixed navigation; UI-03 passed its separate rerun.
  Samsung therefore has three smoke passes across two runs, not a clean first run.
  OPPO smoke also passed 3/3 in one run. Both phones' LONG-02
  and Stop/recovery acceptance remain pending.
- Samsung has a 1080x2316 resolution override over a 1440x3088 physical display.
  The observer now supports wm-based input coordinates without changing phone
  resolution. Eight model-free fixture tests pass. Explicit YAML selection now
  pins the project config so custom app/task nodes remain available.
- OPPO reconnected after USB debugging authorization and a cable change. This
  establishes connectivity, not a proven root cause of the earlier disconnects.
- Production signing awaits the user's choice of an existing configuration or a
  new local key. No production identity was created or changed.
- WASM corresponding sources/rebuild provenance, native recipe/source matching,
  and the remaining npm notice gaps are unresolved. APK publication stays blocked.
- Repository publication and GitHub CI work are excluded by the user's request.
  Changes are committed locally without pushing or dispatching workflows.

## Two-phone smoke evidence

- Samsung initial run: `tests/midscene/.midscene/test-results/20261015145030-59dcea71/summary.json`
  (UI-01 and UI-02 passed; UI-03 failed).
- Samsung UI-03 rerun: `tests/midscene/cases/.midscene/test-results/20261015150254-35dd8905/summary.json`
  (1/1 passed after clarifying navigation to the settings top).
- Both prior APK certificates match the candidate certificate SHA-256
  `45155f7c18ac8a6e6252b7a51d50f200d89e00c5d450f9d3b54cc08ac3aea166`.
- Device logs and prior APKs are under ignored `artifacts/two-phone-acceptance/`.
  Reports/screenshots may contain private conversation data and are not public assets.
- OPPO smoke: `tests/midscene/.midscene/test-results/20261015150906-6a1aca46/summary.json`
  (3/3 passed; automatic reply speech restored to on). No USB reconnect occurred
  during this run; this does not prove the old cable was the sole cause.

## LONG-02 follow-up on OPPO

- Run `20261015151952-4a679e34`: not accepted (0/1 passed). The native task
  became idle after 688.762 seconds; the observer then spent about ten minutes
  navigating the mobile execution report without completing the evidence check.
  The host observer was terminated after about 24 minutes, after confirming the
  native task service had already ended. This is an interrupted verification,
  not proof that the native task itself failed.
- A separate read-only review found the final answer: Android 14, used storage
  53.3 GB / total 256 GB, automatic brightness enabled, and completed status.
  The storage matches the captured About phone screen. Independent ADB
  diagnostics returned Android release 14 and screen_brightness_mode 1. These
  checks support the values but do not replace matching every claim to its
  native execution record.
- The new independent screenshot-read prompt was visible in the native report.
  Repeated Settings navigation and the wide report table make this acceptance
  path slow. Full visual evidence acceptance remains open.

## LONG-02 follow-up on Samsung

- Run `20261015154530-874925fe`: not accepted (0/1 passed). The native task
  repeatedly remained in Digital Wellbeing and application-usage pages rather
  than completing the requested device facts. The host observer was terminated
  and an explicit visible Stop operation was completed in Tapilot.
  The screen showed stopped status and saved progress (22 operations), and
  TaskRuntimeService was absent afterwards; it did not claim completion.
- Potential cause to investigate: native gesture dispatch multiplies logical
  coordinates by the physical/logical display ratio (4/3 on this phone). The
  host observer needed wm-based logical dimensions earlier in this session.
  This is a hypothesis, not a confirmed native defect; no speculative native
  coordinate change was applied.
- No full cancellation/recovery pass is claimed: stopping this task is cleanup,
  not the TASK-01 stop-and-fresh-request scenario.

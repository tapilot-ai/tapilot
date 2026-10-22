# Runtime compatibility

These are the pinned versions exercised by the local checks, not a claim that
arbitrary combinations of Midscene releases are interchangeable.

| Component | Midscene version | Validation entry |
| --- | --- | --- |
| Phone Node runtime | `@midscene/core`, `@midscene/test` 1.15.2 | `pnpm runtime:bundle` |
| Android Use 0.5.12 | `@midscene/core` 1.15.2-beta-20261012093542.0 | `pnpm check:plugin` |
| Native phone bridge / adapter | Device protocol v1 | `pnpm check`, shared device fixtures |

The phone runtime includes the [documented patches](../../node-runtime/bundle/patches/README.md)
for timeout handling, lazy config loading, pixel-region boundaries and semantic
response recovery. The plugin uses a different pinned SDK build; changing its
version simply to match the phone is not a compatibility fix.

For an SDK upgrade, regenerate the owning lockfile, inspect patch applicability,
run that component's tests and the root gate, then verify the same candidate on
both phones. Include screenshot capture, tap/scroll/input/drag, cancellation,
verification failure, reports and plugin reconnection. Record model, network,
runtime fingerprint and APK hash with the results. Host stubs do not establish
phone or plugin-host acceptance.

Host prerequisites remain Node 24, pnpm 12.4.1, JDK 17, Android platform/build-tools
35 and a macOS or Linux shell with the utilities listed in [CONTRIBUTING](../../CONTRIBUTING.md).
Raising compile/target SDK is a separate Android behavior change and requires
device/permission/foreground-service acceptance.

# Tapilot Agent Instructions

These instructions apply to development in this repository. Read [CONTRIBUTING.md](CONTRIBUTING.md) before starting. That guide and `package.json` define the toolchain, installation steps, and build commands. Follow more specific directory instructions where present.

## Repository Language

- Use English for repository content, including agent instructions, documentation, code comments, commit messages, PR titles and descriptions, and release notes.
- Preserve explicitly localized content in its target language, including `README.zh.md`, `PRIVACY.zh.md`, and localized UI strings. Keep English and localized counterparts consistent when behavior changes.
- Conversation with the user may remain in Chinese unless they request another language.

## Project Structure

Tapilot is an on-device Android automation client built on AccessibilityService and Midscene. Identify the responsible layer before making changes.

| Directory | Responsibility |
| --- | --- |
| `app/` | Android UI, accessibility service, conversations, and runtime lifecycle |
| `node-runtime/src/` | Embedded Node worker, model calls, task execution, and stdio protocol |
| `node-runtime/bundle/` | Deployment dependencies, lockfile, and upstream patches |
| `packages/android-accessibility/` | Device adapter implementing Midscene's `AbstractInterface` |
| `plugins/tapilot/` | Independently installable CLI, MCP server, and agent skill |
| `scripts/` | Builds, integrity checks, dependency materials, and performance measurements |
| `tests/fixtures/` | Protocol fixtures shared by Android and Node tests |
| `tests/midscene/` | Physical-device visual E2E cases and validation guidance |
| `tests/verification/` | Frozen-screenshot experiments; these do not establish physical-device acceptance |

Keep the adapter focused on the device protocol. App settings, model transport, report storage, and Node lifecycle belong to the host. Android and the embedded worker communicate over stdio; wireless listeners and authorization form a separate boundary. Check both protocol endpoints and shared fixtures when changing communication contracts.

## Default Workflow

- Inspect the working tree first. Preserve existing changes and untracked files from other tasks.
- Keep changes focused on the task. Separate behavior changes from directory moves and avoid unrelated refactoring.
- Use Node.js 24, pnpm 12.4.1, JDK 17, and Android SDK 35. See the contribution guide for minimum versions and additional build tools.
- Use pnpm for the root workspace. The plugin and E2E package are independent npm projects with their own `package-lock.json` files; Midscene's pnpm-only rule does not apply here.
- Install with frozen lockfiles. Update the appropriate lockfile when changing dependencies, and keep independent dependency trees separate.
- Follow the active session's local resource scheduler and device-lock requirements. Do not add developer-private home-directory scripts to project checks.
- Never force push unless explicitly requested. Commit, push, and publish only within the task's authorization.

## Code and Behavior Changes

- Follow `.editorconfig` and surrounding code: four spaces for Kotlin; two for JavaScript, TypeScript, JSON, and Markdown.
- Preserve diagnostic errors. Do not hide failures behind empty values, empty results, or success states. Keep credentials and private user data out of logs and reports.
- When changing cancellation, timeouts, retries, process restarts, or conversation recovery, check task state, resource cleanup, and duplicate execution risks. Add relevant regression coverage.
- Define how existing data is handled when changing persistence or upgrade behavior. Midscene's disposable-report compatibility policy does not apply to app data.
- Keep provider-specific assumptions out of shared model and device interfaces.
- Review the Zod compatibility patch's version gate when upgrading Midscene, and pass the real SDK action regression. If install scripts are disabled, apply the patch explicitly as documented in the contribution guide.
- Review patches, pnpm overrides, and third-party notices together when changing runtime dependencies. Keep the `http-server` patch and the override removing `secure-compare` aligned.
- Do not hand-edit generated build output under `build/`, `dist/`, or generated Android assets. Change the source or generator and regenerate. Use `theme:generate` and `theme:check` for generated theme assets.

## Validation Scope

Start with the smallest check that covers the change and expand according to its impact. This repository has no root Nx or `lint` entry point; do not copy those commands from Midscene.

| Change | Validation |
| --- | --- |
| Documentation only | Check paths, commands, links, and `git diff --check`; a build is usually unnecessary |
| Plugin | `pnpm check:plugin` |
| Android adapter | Relevant unit tests; `pnpm build:adapter` when exports or build configuration change |
| E2E definitions | `pnpm check:e2e`; no device or model required |
| General quick checks | `pnpm check:quick` |
| Node runtime | Relevant `node-runtime/tests/` tests; build prerequisites with `pnpm runtime:bundle` and run `pnpm runtime:doctor` as needed |
| Build scripts | Relevant `scripts/*.test.mjs`; build required runtime artifacts first |
| Android behavior | Relevant `app/src/test/` tests; physical-device validation for system integration |
| Cross-layer protocols or build workflows | `pnpm check`, covering runtime, scripts, Kotlin, Lint, Beta build, and APK verification |
| CLI installation artifacts | `pnpm check:cli-package`, including installation and execution in an independent consumer |

`pnpm test` is equivalent to `pnpm check` and runs the full checks; it is not a lightweight unit-test entry point. `pnpm dev` builds a Beta APK rather than starting a development server.

If script tests move outside the `scripts/` root, update discovery in `scripts/check.mjs`. Keep shared protocol fixtures in `tests/fixtures/` so Android and Node test the same contract.

## Device and Model Tests

- Read [tests/midscene/README.md](tests/midscene/README.md) before running tests. Specify the device serial, package, display configuration, and test scope. Never default to the first connected device.
- Verify host visual-model authorization and the phone's Tapilot model authorization separately. Never put tokens or passwords in cases, chat, or reports.
- Real model and device scripts can incur charges and change phone state. Run them only when needed and authorized for the task.
- Preserve core `aiAct` and visual assertion coverage in visual E2E tests. Do not substitute hardcoded coordinates or unrelated checks merely to make a test pass.
- Do not clear app data, uninstall an app with valuable data, change the user's screen resolution, or weaken failure criteria to pass tests. Use a suitable test device or isolated environment when signing certificates conflict.
- Microphone access, gestures, foreground services, wireless pairing, and upgrade behavior require physical-device acceptance. Label host tests, frozen-screenshot experiments, and emulator results separately.
- Record the actual APK, device, model, commands, and failure evidence. Retain initial failures and subsequent reruns. Historical reports do not establish that the current build passes.

## Documentation and Delivery

- Update `README.md` and `README.zh.md` together when user-facing behavior changes. Review both privacy document locales when data flows or privacy behavior change.
- Keep build and installation instructions in the contribution guide and test details in the relevant README. Avoid duplicating procedures that can drift.
- Describe the result, actual validation commands, and outstanding coverage in PRs and final reports. Never claim an unrun check passed or conflate local builds, CI, and physical-device acceptance.

## Security and Distribution

- Keep credentials, signing keys, account data, and private model reports out of source, generated assets, and public artifacts. See [SECURITY.md](SECURITY.md) for security boundaries.
- Do not bypass runtime integrity, signing-certificate, or third-party redistribution checks. Local Beta and distribution builds have different requirements; select commands from the contribution guide accordingly.
- Read [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md) and [PRIVACY.md](PRIVACY.md) before distributing APKs or changing data flows. A successful local build does not authorize binary redistribution.
- Use the validated CLI packaging workflow. Do not run `npm pack` or `npm publish` directly from plugin source to bypass installation-artifact checks.
- Releases, binary uploads, and deployments require explicit authorization. Building, validating, fixing, or pushing commits does not automatically authorize a software release.

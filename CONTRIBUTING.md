# Developing Tapilot

Tapilot is MIT-licensed. Contributions are welcome under the same [license](LICENSE).
Third-party components retain their own licenses.

## Setup

Use Node.js 24 (minimum 22.19), pnpm 12.4.1, JDK 17, Android SDK platform 35
and Build Tools 35.0.0. The build host must provide Bash, Python 3, curl, tar,
ar, zip, unzip and OpenSSL. macOS and Linux are supported build hosts.
Set `JAVA_HOME` and `ANDROID_HOME` to your local installations.

```sh
pnpm install --frozen-lockfile
npm ci --prefix plugins/android-use
npm ci --prefix tests/midscene
pnpm check:quick
pnpm build:android
```

The plugin is an independently installable npm project with its own lockfile.
The root pnpm workspace owns the Android adapter; the Node deployment bundle
has an isolated production dependency lockfile. The standalone Midscene E2E package has its own npm lockfile. All four dependency trees are checked by CI.

## Commands

| Command | Purpose |
| --- | --- |
| `pnpm build` / `pnpm build:android` / `pnpm dev` | Build a local non-debuggable Beta APK; accept this machine's signing certificate |
| `pnpm check:quick` | Generated theme, plugin UI freshness and tests, E2E definitions, TypeScript, legacy UI and adapter tests |
| `pnpm check` / `pnpm test` | Quick checks, Node bundle and smoke tests, conversation overhead benchmark, script tests, Kotlin tests, full Android Lint, Beta build and APK integrity checks |
| `pnpm check:e2e` | Type-check and validate Midscene YAML cases; no device or model needed |
| `pnpm test:e2e` / `pnpm test:e2e:live` | Run visual smoke / explicitly opted-in native task E2E; see [E2E guide](tests/midscene/README.md) |
| `pnpm check:plugin` | Plugin UI freshness and all plugin tests, no Android SDK needed |
| `pnpm build:legacy` / `pnpm dev:legacy` | Optional legacy Web UI only |
| `pnpm build:beta:distribution` | Maintainer Beta build, enforcing the existing certificate and binary redistribution review |
| `pnpm build:release` | Production build requiring explicit signing credentials and completed binary redistribution review |

Local builds never bypass runtime integrity checks. Their APK is at
`app/build/outputs/apk/beta/app-beta.apk`. A local signing certificate cannot
update an app signed by someone else's key; use a separate test device/profile
rather than uninstalling an existing app with valuable data.

Checks do not discover or execute private scripts from a developer's home directory.
If you use a local resource scheduler, invoke it explicitly around these commands.
Physical-device acceptance is still required for microphone access, gestures,
foreground services, wireless pairing and upgrade behavior.

## Signing and CI

To reproduce the `Checks` workflow locally after configuring the toolchain above,
run these commands in order (stop if any command fails):

```sh
pnpm install --frozen-lockfile
npm ci --prefix plugins/android-use
npm ci --prefix tests/midscene
pnpm check:security
pnpm check
mkdir -p build/performance
node scripts/benchmark-conversation.mjs > build/performance/conversation.json
```

If `actionlint` is installed, run `actionlint` to validate all workflow files.
The custom `tapilot-android` runner label is declared in `.github/actionlint.yaml`;
that declaration does not provision a runner. The performance benchmark above is
model-free. A local pass does not establish a Linux-hosted CI pass, a production
signing check, or physical-device E2E acceptance.

Pull requests run `.github/workflows/check.yml` with development signing only.
The APK is built and verified but is not uploaded while third-party binary
redistribution review remains incomplete. The manually dispatched
`release-check.yml` uses the `release` environment; configure that environment's
reviewers and secrets before enabling production builds. It verifies an APK and
uploads verification/license evidence; it does not upload the APK or publish a release.

Production builds require:

- `TAPILOT_SIGNING_STORE`: private keystore path.
- `TAPILOT_SIGNING_STORE_PASSWORD`, `TAPILOT_SIGNING_KEY_ALIAS`, `TAPILOT_SIGNING_KEY_PASSWORD`.
- `TAPILOT_SIGNING_CERT_SHA256`: independently recorded expected certificate digest.

The release workflow also needs `TAPILOT_SIGNING_STORE_BASE64` to materialize a
temporary keystore. Never commit these values. Beta distribution deliberately
pins the historical Beta certificate; production has no default signing identity.

## Structure and changes

Start with the [architecture map](docs/architecture/README.md),
[development guide](docs/development/README.md) and
[device protocol](docs/protocols/device.md).

Keep behavior changes separate from directory moves. Add regression coverage for
cancellation, serialization and provider boundaries. Update both README locales
when behavior changes. Historical acceptance reports are dated evidence, not
current setup instructions. Do not add private prompts, credentials or user data
to acceptance artifacts. See [SECURITY.md](SECURITY.md) for vulnerability reporting.

## Distribution and privacy

Read [third-party redistribution](docs/development/third-party-licenses.md),
[release verification](docs/development/releasing.md) and [privacy](PRIVACY.md)
before distributing APKs or changing data flows.

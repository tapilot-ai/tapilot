# Releasing Tapilot

The public-source license is MIT. Repository visibility remains a separate
maintainer action; these commands do not make a repository or artifact public.

## Verify a candidate

1. Use a clean checkout of the exact release commit. Follow CONTRIBUTING with
   Node 24, pnpm 12.4.1, JDK 17 and Android SDK 35; install root, plugin and E2E
   dependency trees using their lockfiles.
2. Run `pnpm check` and `pnpm check:security`. Record the commit, logs and APK hash.
   A local build does not establish an independent Linux CI pass.
3. Review the generated [third-party evidence](third-party-licenses.md), including
   missing license texts and LGPL/native source obligations. Do not publish a
   bundled APK until this review is complete. Run `pnpm check:distribution` to list
   open items. Non-local Beta and Release APK verification enforces the review
   against the inventories embedded in that APK, even when signing passes.
4. Verify the candidate APK on an explicitly selected unlocked arm64 phone.
   Check fresh installation, ordinary task execution, Stop then a fresh task,
   explicit recovery after interruption, and upgrade from the previous installed
   version. Record E2E outcomes separately from authored case counts.

## Production signing

Configure the protected GitHub `release` environment with reviewers and:

- `TAPILOT_SIGNING_STORE_BASE64`
- `TAPILOT_SIGNING_STORE_PASSWORD`
- `TAPILOT_SIGNING_KEY_ALIAS`
- `TAPILOT_SIGNING_KEY_PASSWORD`
- `TAPILOT_SIGNING_CERT_SHA256` from an independently verified certificate

Never copy credentials into source, issues or reports. The `release-check`
workflow materializes a temporary keystore, builds and verifies the production
APK, removes the keystore, and uploads SHA256SUMS, license inventories
and privacy documents. APK upload is deliberately disabled until third-party
binary redistribution requirements are addressed. It does **not** publish a
GitHub Release or update feed.
A local Beta APK uses a development certificate and is not a production artifact.

## Publish only after acceptance

Create a release tag matching `package.json` and Android version metadata, attach
the verified APK and SHA256SUMS plus required notices/source materials, and use
release notes that identify tested behavior and remaining limitations. Download
the uploaded APK and verify its hash and signing certificate before advertising it.
Keep the production key stable for updates; a changed key can prevent upgrades.

The app's existing update parser accepts HTTPS Feishu download links, not arbitrary
GitHub asset URLs. A GitHub Release alone does not update the in-app feed. Review
that contract, publish an appropriate feed through the existing hosting process,
and verify the on-phone update path separately. Do not insert an unsupported URL
into the feed or claim the GitHub artifact is automatically available in-app.

GitHub Actions account billing/runner availability, production secrets, third-party
source obligations and real-device upgrade acceptance remain external release
conditions. Never substitute a local green run for those results.

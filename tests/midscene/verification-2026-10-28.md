# Open-source readiness verification, 2026-10-28

This records observed results, not an approval to distribute binaries. GitHub
hosted CI is excluded at the owner's request because of the account billing block.

## Local release checks

- Full `pnpm check` passed, including Kotlin (388 passed, 1 skipped), plugin (94),
  runtime (127), scripts (34), adapter (28), E2E definitions (12), Android Lint,
  non-debuggable Beta build and APK integrity/signature verification.
- All four dependency audits reported zero known vulnerabilities.
- `pnpm check:cli-package` passed: frozen source install, source/consumer audits,
  independent tarball installation with lifecycle scripts disabled, 94 plugin
  tests against the installed artifact, and forced WASM encode/resize/decode.
- Global installation into a private temporary prefix and CLI invocation passed.
  No npm publication was performed. Normal source-directory pack is rejected.
- 132 checksum-pinned distribution materials assembled successfully. NDK r30
  LLVM notices now cover the libc++ notice gap; source/rebuild review is separate.
- Four distribution blockers remain: Sharp WASM corresponding source/relink
  evidence, proxy-agent-negotiate full notice, secure-compare full notice, and
  the remaining native/component corresponding-source review. The gate fails.

## Physical device, OPPO PEUM00 / Android 14

Both APKs were installed with `adb install -r`, preserving app data. Installed
base.apk hashes were read back and matched. These are different APKs; results
are not pooled into a claim of complete same-APK coverage.

| APK SHA-256 | Case | Result and evidence |
| --- | --- | --- |
| a7a3e321dd9b1761cc6ab3796ba10ff361c05b3a727bbfe4987fef35d7d798b5 | UI-01/02/03 | All passed, first attempts, batch 20261028142902-518c710c. |
| a7a3e321dd9b1761cc6ab3796ba10ff361c05b3a727bbfe4987fef35d7d798b5 | TASK-01 | Passed all six steps, batch 20261028143949-7cbd8a7a. New native Settings task completed in 45s; report inspection took 335s. |
| a7a3e321dd9b1761cc6ab3796ba10ff361c05b3a727bbfe4987fef35d7d798b5 | CHAT-01 | Interrupted by evaluator SIGINT during setup; retained as failed, not a product failure or pass. |
| ed26d1eb18e7cb818175e07b91d09376b23cf1faec1232ebb5d3b8cfcecf3b29 | CHAT-01 | Passed all seven steps, batch 20261028145022-22d857a1. First reply waits; second returns 100, completed, zero phone operations. |
| ed26d1eb18e7cb818175e07b91d09376b23cf1faec1232ebb5d3b8cfcecf3b29 | WIFI-01 | Passed preferred-port collision and repeated starts, batch 20261028145238-cb850898. |
| ed26d1eb18e7cb818175e07b91d09376b23cf1faec1232ebb5d3b8cfcecf3b29 | WIFI-02 | Fresh-frame reconnect fixture passed (881ms); full case failed at final UI check when USB device disappeared. |
| ed26d1eb18e7cb818175e07b91d09376b23cf1faec1232ebb5d3b8cfcecf3b29 | WIFI-03 | Failed prerequisite: adb device absent. Native-task contention was not exercised. |

No automatic case retries. USB remained absent at the follow-up check. The owner
was asked to reconnect it; secure unlock credentials were not requested.
Remaining cases (drafts, long tasks, lifecycle faults, auth rejection, offline
recovery, playback) have not passed on the final APK in this round.

## Independent packaged CLI over Wi-Fi

With USB absent, the source CLI captured the live two-turn conversation. Its
runtime was then stopped and the globally installed tarball started in an
isolated runtime state directory, reusing the existing pairing registry.
The first connection timed out; an independent screenshot retry succeeded.
A single tap using its returned frame ID opened Settings. The fresh readback
showed Accessibility Connected and Wireless control: Computer connected.
Report ID: b1511668-1725-401f-8fa4-a6144b80cc4c.
This verifies packaged CLI control and shared pairing without USB. It does not
prove uninterrupted connectivity, all-host-platform support, Codex GUI acceptance,
or the complete resilience suite.

Private screenshots, HTML and command logs remain local/ignored. Logs use the
`/tmp/tapilot-readiness-` and `/tmp/tapilot-package-` prefixes. Do not publish
those artifacts without a separate privacy review.

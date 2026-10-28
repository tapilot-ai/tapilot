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

## Follow-up after USB reconnection

- Main branch through `f7e90df` was pushed to origin. Repository remains private.
- Full-history Gitleaks scanned 223 commits with no findings beyond the existing
  narrowly reviewed exclusions. Both final videos were additionally sampled at
  one-second intervals; no obvious credential exposure was observed in the
  contact sheets. This is not exhaustive frame/audio or rights clearance.
- Checks run 40649909473 and Secret scan run 40649909355 still failed before
  job steps. The Checks annotation explicitly reports account payment/spending
  restrictions; running Dependabot jobs do not prove these checks ran.
- USB reconnected and the installed APK hash again matched `ed26d1eb…`.
  Wireless reconnect's fresh-frame fixture and final waiting-state UI check
  passed on the independent follow-up. Both WIFI-02 and WIFI-03 passed all
  steps in batch `20261028152515-5fd57a53` (2/2, first attempts, no retries).
  WIFI-03 took 195.018s including observer setup: a connected controller blocked
  the native task; disconnect plus explicit retry completed a real Settings task.

### Network and speech follow-up

FAIL-03 initially failed before submission because the old radio fixture did
not reach its required state. A 30-second diagnostic retry showed Wi-Fi disabled
but `mobile_data=1` (batch `20261028153225-9abc20df`). Cleanup restored Wi-Fi.
The fixture now snapshots airplane mode and uses enabled airplane mode plus
disabled Wi-Fi as its offline precondition, preserving the original preferences.
Thirteen E2E definition/fixture tests pass, including an OEM retained-data-value
regression. Its next full case (`20261028153515-bab1e2c8`) failed model preflight,
before activating the new fault: ChatGPT connection check timed out.

VOICE-01 (`20261028153826-0aeb051f`) failed with a 15-second connection timeout
to the phone's local HTTP proxy. Read-only inspection found the configured
network proxy endpoint still listening; no causal diagnosis is claimed. No
account, VPN profile or credential was changed. Flight mode readback was disabled
and Wi-Fi enabled afterward. Neither offline recovery nor speech playback is
accepted on this evidence.

DRAFT-02 and DRAFT-03 passed in `20261028154312-eec9e85e` (2/2, no retries),
including retained draft/attachment after process restart, share-text preservation,
attachment preview and cleanup. After force-stop, Android removed Tapilot from
its enabled accessibility-service list; restoration is tracked separately and
must complete before another native-task case.

### Restored device control and network

After the draft force-stop, the expected Android permission UI re-enabled
Tapilot accessibility. The restoration helper failed its final assertion because
it had returned to the chat rather than the settings label. Separate system
binding readback and a fresh Settings screenshot both confirmed Connected.
This does not convert the helper's failed result into a pass.

Speech retry after app restart (`20261028155122-2b09fb65`) still failed at the
same local proxy. A phone-shell TCP request also timed out. The Wi-Fi network
owner was Feilian, but that fact did not establish ownership of the failing
proxy. Inspecting the installed network apps showed Feilian VPN off and Clash
stopped with the existing MC profile selected. Starting that unchanged Clash
profile produced Running and a new reachable local proxy endpoint. No profile
content or account credential was changed.

VOICE-01 then passed (`20261028155617-aefb02a8`, 1/1, 23.556s observer action),
requiring the actual Playback succeeded UI. This verifies the playback service
and UI outcome, not an independently recorded acoustic-quality measurement or
continuous microphone conversation. Earlier failures remain in the ledger.

### USB loss during the offline fault

Batch `20261028155730-bd81e55c` passed model preflight and entered the new
airplane-mode/Wi-Fi-off fixture in 501ms. USB disappeared during the following
submission step. Both afterEach and teardown restoration failed because adb
could no longer reach the device. This is a failed case with failed cleanup,
not accepted offline recovery. The captured original state was airplane mode
disabled, Wi-Fi enabled, mobile data enabled. The owner was notified immediately
and asked to reconnect USB (or restore those switches locally). A bounded
10-minute host watcher was started to apply exactly those restoration commands
to serial e7ed3f17 when it reconnects. Final restoration remains unverified
until a subsequent readback is recorded. No further device test runs while
this cleanup is outstanding.

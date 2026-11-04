# SIWC credentials for parallel APK checks

The four APK lanes stay parallel. Only the `siwc-credentials` job owns renewable
credentials. It runs after the APK and Android image are ready, so build queues
do not consume the access token's lifetime. Both the host observer and the
installed verification APK receive access-only sessions. The report job still
merges all lanes through the existing single Midscene Test entry.

## Configure the owner

This is an opt-in implementation; API-key CI remains selected until configuration
is complete. It is not evidence of a completed live SIWC regression run.

Create a GitHub Environment named `tapilot-siwc`. Restrict it to trusted branches.
Save the complete CI-specific Midscene `~/.midscene/siwc.json` as its
**environment secret** `SIWC_CREDENTIALS`. Do not put that secret at repository
level: repository secrets are snapshotted when a run queues; environment secrets
are read when the job starts. The environment must be the credential's sole
owner; the original workstation must stop refreshing the imported session.

Configure these repository secrets:

- `SIWC_SECRET_WRITE_TOKEN`: a fine-grained PAT restricted to this repository,
  with **Environments: read and write**. The owner writes back to an environment
  secret, not a repository secret. `GITHUB_TOKEN` cannot perform that write.
- `SIWC_TRANSPORT_KEY`: 32 random bytes encoded as 64 hexadecimal characters.
  This encrypts the access-only handoff, and is never passed to the test command.

Set repository variable `TAPILOT_CI_AUTH_MODE=siwc` only after both are configured.
Leave it unset to keep API-key checks. Do not enable SIWC on untrusted PR runs.
The separate plugin live API-key probe is skipped in SIWC mode; the APK lanes
provide the live provider acceptance for that mode.

For first live acceptance, manually dispatch `Checks` with `siwc_refresh=true`.
This performs one real OpenAI refresh and GitHub writeback under the same owner
lock before launching APK lanes, even if the original access token is still
valid. It does not modify expiration metadata or prove expiry-triggered refresh.
Normal main pushes keep this option false and reuse a sufficiently fresh token.

The owner uses Midscene's exported `refreshSiwcCredentials`, preserving its
OAuth response validation. It keeps a token with at least 30 minutes remaining,
or attempts one refresh and immediately persists the replacement before emitting
anything to the shards. If the provider refuses an early refresh, preparation
fails; it never waits through the expiry window or silently falls back to a key.

## Prevent refresh races

All callers share the job concurrency group `tapilot-siwc-refresh-owner`, with
`cancel-in-progress: false` and `queue: max`. Up to 100 waiting owner jobs can queue;
they do not replace each other. The exclusive slot covers reading the environment
secret, refresh, writeback, and encrypted handoff. Only this short phase serializes;
builds and APK lanes can overlap across runs. Once writeback finishes, the next
owner reads the replacement while earlier lanes use their existing access lease.

The combined queue/environment-secret behavior was verified on GitHub using a
temporary environment and synthetic generations: [first run](https://github.com/tapilot-ai/tapilot/actions/runs/41812026422)
read generation one; [second run](https://github.com/tapilot-ai/tapilot/actions/runs/41812079120)
and [third run](https://github.com/tapilot-ai/tapilot/actions/runs/41812084738)
were both pending before the secret changed, then both read generation two when
their jobs started. All three completed successfully; neither pending run was
canceled. This verifies GitHub queue behavior, not live OAuth refresh.

A forcibly terminated runner between OAuth rotation and
writeback can lose the replacement. Reauthorize if that occurs. Writing the new
secret is retried three times; the refresh exchange is never retried by our code.

## Keep shards access-only

The owner emits an AES-256-GCM encrypted ticket through a job output. Authenticated
additional data binds it to repository, run ID, and run attempt. The plaintext
contains only `access_token` and `expires_at`; it has no refresh token, ID token,
registration or GitHub write token. No token artifacts are uploaded.

Each shard decrypts into a private temporary HOME, checks at least 22 minutes of
remaining lifetime for the 20-minute job, then launches its normal emulator script.
The access file is removed when the command exits, including failure. A delayed
shard rejects its ticket instead of refreshing. The SDK cannot renew an access-only
record. The APK's debug receiver imports the same lease into encrypted app storage;
its CI-only account rejects forced or expired refresh attempts. This provisioning
is guarded by the isolated verification package and is unavailable to release apps.

The test command receives neither encryption key nor renewable credentials. Raw
provider and GitHub write errors are suppressed in credential preparation. New
access/refresh/ID tokens are explicitly masked before any following command.

## Verification

```sh
node --test scripts/ci-siwc.test.mjs
npm --prefix tests/midscene run check
```

The credential tests use synthetic tokens. They exercise four actual parallel
child processes, replacement persistence before release, write failure, wrong
run/repository/attempt/key, tampered ciphertext, insufficient lifetime, and cleanup.
They do not claim real ChatGPT login or refresh. The separate GitHub runs above
cover queue acceptance without sending model requests.

Current actionlint 1.7.12 does not recognize GitHub's documented `queue` field.
To lint all other syntax without removing the required queue behavior:

```sh
actionlint -ignore 'unexpected key "queue" for "concurrency" section' \
  .github/workflows/check.yml .github/workflows/siwc-credentials.yml
```

References: [GitHub secret read timing](https://docs.github.com/en/actions/reference/security/secrets#when-github-actions-reads-secrets),
[GitHub concurrency queues](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#concurrency),
[Environment secret write permission](https://docs.github.com/en/rest/actions/secrets#create-or-update-an-environment-secret),
[Midscene SIWC](https://midscenejs.com/chatgpt-subscription.html).

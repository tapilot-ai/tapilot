# Frozen screenshot verification replay

This is a diagnostic experiment, not the application's verifier. It compares the
shipped `verifyPhone` prompt with a prototype that resolves text requirements
first, reads the screenshot independently, and checks supported arithmetic with
exact fractions. Nothing here connects to a phone or exposes an action tool.

The fixtures retain changed requirements, intermediate versus whole-task scope,
wrong grouping/divisors, explicit expanded expressions, unevaluated previews,
read-only requests and an added requirement that must not disappear. Expected
verdicts and arithmetic normalization are checked separately. A correct negative
verdict does not excuse an incorrect requirement resolution.

## Run

Build the existing Node runtime bundle (`pnpm runtime:bundle`) to supply the same
SDK as the native runtime. Use Node 24 for comparisons, recording its exact
version. Keep images, credentials and generated reports outside tracked files.
For example, create `artifacts/verification-replay/screenshots.json`:

```json
{
  "calculator-final": {
    "path": "calculator-final.png",
    "sha256": "<SHA-256 of the exact image bytes>",
    "mimeType": "image/png",
    "width": 988,
    "height": 1972
  }
}
```

The full fixture set additionally needs `calculator-preview` (unevaluated
`4736+456/4`, preview `4850`) and `android-version` (Settings showing Android 14,
without storage or auto-brightness). The evaluated final image shows
`5192÷4=1298`. Inspect each image before assigning these fixture names. Screenshots
must not be reconstructed from expected answers. Hash checking prevents an
unnoticed image change; it does not establish the image's meaning.

Verify that the existing Android Use authorization is the same account as the
phone. Pass the first 16 hexadecimal characters of SHA-256 of the trimmed,
lowercase account email as the fingerprint. Do not put tokens in arguments or
change the phone's account to make a comparison run.

```sh
node --test scripts/verification-prototype.test.mjs
node scripts/replay-verification.mjs \
  --screenshots artifacts/verification-replay/screenshots.json \
  --output artifacts/verification-replay/run-NEW-ID \
  --auth-dir /path/to/existing/android-use/chatgpt \
  --account-fingerprint VERIFIED_FINGERPRINT \
  --repeats 3
```

`--only amended-final,wrong-divisor --repeats 1` runs an initial protocol check.
The output directory must not exist, and its parent must exist. Results are saved
after every sample, including failures; exit status 1 means at least one expected
verdict or checked normalization did not match. There are no automatic case or
model transport retries. An HTTP 401 can refresh authorization once through the
existing transport. The shipped baseline retains its one format-only retry.
Repeat order alternates between variants. Each sample has a new, uncached agent.
`--variants resolved` runs only the candidate. Label it as an independent rerun,
not a new paired baseline comparison.
`--cases tests/verification/holdout-cases.json --variants resolved` runs the six
additional cases withheld from the V2 prompt adjustment. Keep the prototype
frozen between the main matrix and these holdouts.

`results.json` records fixture/source hashes, account fingerprint, SDK/Node/model
versions, structured observations, normalization, individual outcomes and HTTP
timings. No authorization token or raw model reasoning is included in this file.
Generated Midscene reports stay private. Convert a report with the Android CLI's
`report-tool` before consuming its contents; read structured query outputs rather
than raw reasoning. Runtime diagnostics retain error names/status only.

## Acceptance boundaries

- Report setup errors separately from completed model judgments. Keep all
  independent runs; do not replace a failure with its later successful rerun.
- Score false positives, false negatives, requirement resolution, screenshot
  transcription and latency separately. Inspect visual resolutions for retained
  targets and restrictions; their semantic correctness is not fully automated.
- Arithmetic supports only bounded literals, parentheses and `+ - * /`, including
  conventional comma grouping and decimal fractions. Unsupported evidence fails
  closed. It allows committed proper subexpressions, but preserves the final
  operation. It does not claim arbitrary algebraic equivalence or prove action
  history. Exact-text requests and non-arithmetic visible conditions use the
  visual branch.
- Passing this replay is component evidence on frozen images and host networking.
  It does not establish native latency, interaction success, historical action
  compliance, or a passing case on the installed APK. Promote a candidate only
  after fresh native and full-case physical-device validation, without relaxing
  negative controls.

## Locating isolation

`--mode locating` uses `locating-cases.json` and read-only `aiLocate`. It compares
ordinary locating, deep locating, and ordinary locating with the SDK's 1.5
screenshot shrink factor. It never taps returned points. `--only digit-2` isolates
the observed wrong-key failure. `--variants ordinary,smaller` selects policies.

The four fixtures cover vertically adjacent digits 8, 5, 2 and 0. Their centers
and 85-pixel acceptance radii were marked by visual inspection on the exact
988×1972 image. Replace those annotations if using a different image. An API
success on the wrong key is a failure; any API/transport error is also a failure.
Ground-truth coordinates are only used by the scorer and are never in a model
prompt. Midscene `aiLocate` returns points in the prepared screenshot's coordinate
system. The scorer records that raw point and multiplies it by the configured
shrink factor, matching the SDK action conversion for this 1:1 input geometry,
before comparing against original-image annotations. This experiment does not establish gesture
delivery or full-task success.

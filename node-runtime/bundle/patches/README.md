# Deployment patches

`midscene-test-unbounded.patch` applies only to `@midscene/test@1.17.6`.
It accepts `testTimeout: 0` and translates it to the runner's unbounded timeout,
so Tapilot's explicit task cancellation and budgets govern long-running tasks.
Both ESM and CommonJS entrypoints are patched.

It also loads the CommonJS TypeScript fallback only when needed. Native `.mjs`
configs do not initialize `tsx/cjs/api`; TypeScript and CommonJS fallbacks remain
available. The lazy-loader runtime test covers module loading and config formats.

The patch is declared in the deployment workspace and verified by pnpm's lockfile.
`node-runtime/tests/unbounded-runtime.test.mjs` covers its contract. The source
fingerprint includes patch content and configuration. Remove the patch and its
lockfile entry together once an upstream version supports this behavior, then run
the deployment tests, YAML smoke projects and device cancellation acceptance.

`midscene-core-runtime.patch` applies to `@midscene/core@1.17.6` (ESM and
CommonJS). Pixel bounding boxes may use the image width/height as exclusive far
edges, with at most one further pixel of edge rounding tolerated. The existing
pixel mapper clips those edges to the last pixel. Origins,
point coordinates, normalized ranges, reversed boxes and larger overflow still
fail validation. On OPPO, a scroll region ending at y=1972 on a 1972-pixel image
triggered a redundant planning request in three consecutive native task runs.
`dependency-compatibility.test.mjs` tests the real codec, including rejection cases.
A later real Scroll response ended at y=1973; the additional one-pixel tolerance
avoids discarding that otherwise valid on-screen region. Overflow of two or more
pixels past the exclusive edge remains rejected. This is bounded robustness for
model region output, not permission to clamp arbitrary off-screen targets.
Remove this patch when the upstream codec supports these boundary semantics.

The core patch also corrects malformed model responses without a fixed delay.
Semantic validation received a valid HTTP response; the transport retry loop
continues to apply network backoff independently. Retry limits, feedback, abort
checks and error propagation remain intact. Bare prose is still rejected and
independent final screenshot verification is unchanged. The real brightness
trace otherwise waited two seconds after a plain-text response before asking
for the required completion marker. `semantic-retry.test.mjs` verifies immediate
correction, bounded failure and cancellation for both ESM and CommonJS.

On Android only, the core publication patch uses exclusive file copy instead of
hard links for single-file reports. Android denies the hard-link operation even
inside the app sandbox. COPYFILE_EXCL preserves rejection of existing targets;
other platforms retain the upstream publication path. The application imports and
shares the original self-contained SDK report, so no data sidecars are lost.

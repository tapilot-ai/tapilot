# Deployment patches

`midscene-test-unbounded.patch` applies only to `@midscene/test@1.15.2`.
It accepts `testTimeout: 0` and translates it to the runner's unbounded timeout,
so Tapilot's explicit task cancellation and budgets govern long-running tasks.
Both ESM and CommonJS entrypoints are patched.

The patch is declared in the deployment workspace and verified by pnpm's lockfile.
`node-runtime/tests/unbounded-runtime.test.mjs` covers its contract. The source
fingerprint includes patch content and configuration. Remove the patch and its
lockfile entry together once an upstream version supports this behavior, then run
the deployment tests, YAML smoke projects and device cancellation acceptance.

`midscene-core-bbox-edge.patch` applies to `@midscene/core@1.15.2` (ESM and
CommonJS). Pixel bounding boxes may use the image width/height as exclusive far
edges. The existing pixel mapper clips those edges to the last pixel. Origins,
point coordinates, normalized ranges, reversed boxes and larger overflow still
fail validation. On OPPO, a scroll region ending at y=1972 on a 1972-pixel image
triggered a redundant planning request in three consecutive native task runs.
`dependency-compatibility.test.mjs` tests the real codec, including rejection cases.
Remove this patch when the upstream codec supports image-boundary far edges.

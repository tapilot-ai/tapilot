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

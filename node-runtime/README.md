# Android Node worker

`src/` contains the embedded worker's source; `tests/` contains its regression
tests; `bundle/` locks deployment dependencies and necessary upstream patches.
Build and test the installed tree from the repository root:

```sh
pnpm runtime:bundle
pnpm runtime:doctor
```

Generated files go to `build/node-runtime-bundle` and Android's generated bundle
assets. The worker emits JSON lines prefixed with `MIDSCENE_EVENT `; Pi uses
`TAPILOT_AGENT `. Credentials must not appear in source assets or reports.
Android and its embedded worker use stdio. The separate wireless service opens
a Wi-Fi listener only when enabled; see [SECURITY.md](../SECURITY.md).

The locked `http-server` patch uses Node's native timing-safe comparison for
report-server authentication. Its matching pnpm override removes the replaced
`secure-compare` dependency, whose published package omits its full license
notice. Keep the patch and override together; the runtime build runs the HTTP
authentication regression test against the installed patched package.

# Android Node runtime worker

This directory contains the JavaScript entrypoint executed by the embedded
Android Node.js process. Android installs these assets into app-private storage
before starting a run.

The worker emits machine-readable events as single lines prefixed with
`MIDSCENE_EVENT `. Model credentials are passed only through the child process
environment and must never be written into this directory or a run report.

Commands:

```sh
node assets/node-runtime/runner.mjs doctor
node assets/node-runtime/runner.mjs test --project-root /absolute/project --result-dir /absolute/results
```

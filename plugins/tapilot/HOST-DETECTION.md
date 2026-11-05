# Host detection native verification

Date: 2026-11-04. This is host identification, not embedded-panel or phone acceptance.
Cursor is excluded at the user's request. No phone operations were performed.

Run the dependency-free, read-only sampler:

```sh
node plugins/tapilot/scripts/probe-client-host.mjs
```

`--output FILE` additionally saves the same JSON. The script does not inspect all
process arguments or emit environment values, session IDs, credentials or user
paths. It reports marker names, a sanitized ancestor chain, and known runtime
origins. It does not modify client configuration or start a phone runtime.

## Results and limits

| Host | Actual execution context | Result | Limit |
| --- | --- | --- | --- |
| Codex | This real agent conversation's execution tool | `codex`, process evidence | Desktop/CLI/editor surface remains unknown |
| Doubao Work | New native Local computer Agent conversation | `doubao`, runtime + ancestor evidence | Initial execution-interface errors; one subsequent retry produced the JSON |
| DSH Web | Actual DSH 0.2.1-rc.5 session's native terminal, opened and typed through its UI | Initial `codex` false positive; fixed and rerun as `dsh` | Agent request failed with invalid model API key; terminal evidence is not Agent execution proof |
| WorkBuddy | Native app opened, then computer-use tool lost the visible window | No Agent sample | `cgWindowNotFound`; awaiting a usable native task window |
| VS Code Copilot | Native app inspected, Copilot signed out | No Agent sample | Sign-in required; integrated-terminal actions did not yield a sample either |
| Codex desktop/plugin | No separate native frontend sample | Unverified | Existing computer-use restriction remains; no alternate control channel used |
| DSH Go | No active host connection sampled | Unverified | Server-side shell evidence alone does not identify Web versus Go frontend |
| Cursor | Not tested | Excluded | User instruction |

Doubao conversation: native chat `38451341708295938`, titled 宿主环境验证.
DSH review page: Ego Lite task space 23, page p1.
Actual raw evidence is kept locally in `build/client-validation/host-detection/`:

- `codex-current.json`
- `doubao-agent.json`
- `dsh-terminal-before-fix.json`
- `dsh-terminal.json`
- `dsh-terminal-after-fix.png`

## Reproduced false positive

DSH was started from this Codex execution environment. Its actual native terminal
inherited `CODEX_*` but also injected `DSH_SESSION_ID`. The first sampler selected
Codex solely from inherited markers. The saved before/after results show the
same terminal and marker set selecting DSH after the fix.

The installed primary DSH source also confirms the distinction:
`@deepseek-ai/dsh-terminal-bash` injects `DSH_SESSION_ID` into session terminals;
`@deepseek-ai/dsh-shell-env` builds `DSH_SHELL`, session and profile fields for
model shell tools. This source inspection supports the design but does not
replace the blocked real Agent test.

## Interpretation

- Explicit user target remains separate from detected execution context.
- App runtime/ancestor evidence and a direct Codex ancestor outrank inherited
  marker-only candidates. A DSH session marker outranks inherited Codex markers
  when there is no direct Codex process ancestor.
- A connector's `TAPILOT_HOST` is only an adapter hint. It cannot prove the caller
  is that client and is not used to select the detected host.
- An installed application or config file is not current-host evidence.
- The sampler retains conflicting candidates. Environment-only inference is
  provisional; an unknown/conflicting result should ask the user for the target.
- Surface and panel capabilities remain unverified. Do not turn host detection
  into a claim of embedded support or silently pick a frontend in mixed contexts.

The sampler now powers `tapilot detect [--client NAME]`. The distributed Skill
uses it only to recommend a target when the user has not named one. An explicit
target wins, and `install` without a target returns a selection request without
changing client files. This does not expand the native evidence in the table.

## Checks

`npm run check --prefix plugins/tapilot`: generated UI check and all 174 tests pass.
The four new regression tests cover inherited Codex markers under DSH, a Codex
process inside DSH, native sandbox evidence over inherited markers, and adapter
hints that must not identify the caller. Native JSON evidence is separate from
these synthetic regression inputs.

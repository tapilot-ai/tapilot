# Set up the background screen from chat

With the model and accessibility control already configured, ask Tapilot to
"set up the background screen" (or "帮我开启后台屏幕"). The same request is
available through **Settings → Background screen → Set up automatically**.

The host offers `setup_background_screen` only for an explicit Chinese or English
request to enable, configure or repair the background/virtual screen. Ordinary
requests to open an app on the background screen do not receive that tool; the
native RPC handler also rejects unsolicited setup. Task history owns setup authorization: typed continuation, the Continue task button and retry inherit the selected setup goal. Ordinary new requests do not inherit it. An explicit fresh setup request starts its own attempt. Setup cannot complete through a text-only answer or ordinary phone verification. The conversation agent then calls `setup_background_screen`. A native setup session
pins this attempt to the primary screen, reuses `ShellSetupService` and opens the
existing consent flow. Midscene navigates Developer options and the debugging
switches. The user handles shell consent, notification permission, system
confirmations, lock-screen credentials and the pairing code. Pairing codes go
through the existing notification input; they are not tool arguments or chat
messages. The automated steps do not open the code dialog.

After each user step, the host reads the actual Android settings and helper
connection. A returned Activity or a successful AI observation cannot establish
that setup is ready. Completion requires current shell consent, background chat
enabled, a live shell helper and overlay permission. An existing helper skips
new pairing and debugging changes. Setup does not create a display or run an app
task; the next phone task uses the existing background-screen backend.

The setup page and shell notification show the current step. Use **Stop automatic
setup** or the chat task's stop control to cancel. Existing consent and pairing
remain available. Re-entering setup re-evaluates current state; process restart,
reconnection and revocation never resume this setup automatically. Each step has a five-minute waiting
deadline, checked between automated operations; an unfinished flow returns
a specific pending step. Automatic connection recovery remains separately opt-in.
Each model-driven setup operation has a 12-cycle planning budget; ordinary
phone tasks retain their 100-cycle budget. A missing wireless-debugging entry
returns explicit guidance to search system settings rather than implying that
another confirmation is necessarily pending. A system may support wireless ADB
while its settings application provides no usable pairing page.

Stage entry and timeout decisions are pure state transitions. Re-entering a
stage restores its navigation after prerequisite changes. Every pairing entry
verifies the current pairing page; an enabled debugging switch does not establish
which page is visible. Completed consent and switches are read from native state.

While another task runs, **Set up automatically** is disabled with an explanation.
The coordinator rejects busy requests without replacing the active task. The
settings page leaves only after the coordinator accepts the new setup task.
An active computer-control lease also disables the entry, with instructions to
disconnect on that computer. Pairing and lease ownership are preserved.

The setup tool must run before other phone operations, and ends this attempt.
It is an explicit setup route, not a primary-screen fallback when a normal
background task fails. Native completion is checked separately from ordinary
phone tasks, which still require screenshot verification.

## Verification

`BackgroundScreenSetupTest` checks consent ordering, Wi-Fi requirements,
connection loss, existing-helper reuse and native readiness. Conversation runner
and Pi tests check setup dispatch, host-result validation, cancellation and
rejection after other phone operations.

The native instrumentation harness accepts `-e background_setup 1`. It sends the
real chat request and checks live shell readiness, overlay permission and stored
consent at the end. It does not grant those permissions or pair the phone on the
user's behalf. Run an ordinary calculator task afterwards to check the actual
background display separately.

For UI acceptance, add `-e manual_entry 1`. The harness provisions the model and
waits for the tester to submit through the app's button or composer. With this
mode, optional `-e task` text is copied to the Android clipboard for UI paste;
it is never dispatched by the harness. A final UI screenshot is captured before
instrumentation teardown. See the [acceptance record](../../tests/verification/chat-background-screen-2026-11-04.md).

`-e check_setup_busy 1` checks native admission and the actual disabled/enabled
setup button around an ordinary task. Setup acceptance additionally requires an
actual `phone.setupBackgroundScreen` RPC even if native readiness was already
true. See the [review-fix verification](../../tests/verification/chat-background-screen-review-fixes-2026-11-04.md).

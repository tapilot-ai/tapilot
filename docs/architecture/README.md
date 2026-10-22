# Architecture

Tapilot has two execution locations: the Android app's embedded Node worker and
the computer-side Android Use plugin. Normal wireless use does not require ADB.

| Directory | Responsibility |
| --- | --- |
| `app/` | Android UI, persistence, credentials, device execution and process lifecycle |
| `node-runtime/src/` | Pi agent, Midscene runner and stdio transport source |
| `node-runtime/tests/` | Node agent tests, run against the staged deployment bundle |
| `node-runtime/bundle/` | Production dependency lockfile and documented upstream patches |
| `packages/android-accessibility/` | Private Midscene device adapter and TypeScript protocol |
| `plugins/android-use/` | Independently installed computer-side MCP plugin, UI and wireless transport |
| `legacy/web-ui/` | Previous web application and optional speech verification entry |
| `experiments/` | Unshipped feasibility work |
| `scripts/` | Build, generated assets and artifact verification |

## Android boundaries

`AndroidDeviceService` implements typed `DeviceCommand` operations. Native app
services, Node stdio RPC and wireless sessions all delegate to it. It owns access
checks and frame validation; the wireless session additionally enforces device
unlocking, action restrictions and connection lifetime. Transport code must not
add a second gesture or screenshot implementation. `TextInputController` owns focused text editing and input-connection/clipboard fallbacks; it borrows managed gestures from the service.

`AppServices` wires device, report, speech and runtime operations. Native UI code
uses `ServiceGateway`; `NativeJavascriptBridge` is only a legacy WebView adapter.
Native calls preserve their exception types and do not serialize a fake RPC envelope.

`NativeConversationCoordinator` owns main-thread UI coordination. `ConversationTaskRunner`
owns one worker attempt through injected `ConversationAgent` and `ConversationTaskServices`
interfaces; `GatewayTaskServices` contains the legacy gateway's method names and casts.
`PhoneOperation` validates mode, effort and required strings at the Pi boundary.
The Kotlin and Node tests read the same [contract fixtures](../protocols/conversation.md).
`TaskExecution`
owns active task/session identity and cancellation acknowledgement. `PendingTurn`
models queued input, `ConversationEffects` persists before external operations,
and `ConversationModelClient` handles provider-specific request/replay formats.
`ConversationSettings` owns configuration writes and connection-check state.
The worker is injected. Existing version-1 history JSON remains a storage/agent
interchange format so saved conversations remain compatible; it is not a new
public SDK. `ConversationWriter` serializes snapshots off the UI thread.

Speech capture, provider protocols and recognition implementations live together in
`speech/`. `voice/` owns the interaction state and automatic listening; `SpeechService`
adapts recognition to the shared service gateway. `ReportExports` owns HTML/video
export work and shutdown; `ReportActivity` owns the viewer and its theme/readiness.
Export file writes run on a worker and incomplete Downloads rows are removed.

## Build boundaries

The Node bundle installs the real local adapter through a `file:` dependency
with the same relative location in source and staging. It never installs another
package under the adapter's name and replaces it afterward. Bundle identity
covers source, adapter, deployment lockfile, workspace settings and patches.
Generated assets are validated before Android packaging.

The legacy web UI is excluded from ordinary APKs. Generated plugin HTML is checked
against its source by the root gate. Keep source changes and regenerated assets together.

## Verification and recovery

The conversation agent preserves the user's requirements separately from its
proposed steps. Each phone operation runs one independent screenshot query that
returns both the visible observation and a success judgment. This is one query,
not separate extraction and judgment requests. Missing evidence stays unknown;
intermediate-step success does not establish completion of the whole task.
See [`phone-verification.mjs`](../../node-runtime/src/phone-verification.mjs).

Only malformed or echoed verification output receives one additional read-only
query. A valid negative judgment is returned without retrying it. Verification
does not replay actions, and a final screenshot cannot prove compliance with
constraints on earlier actions. Saved visual notes from failed operations are
untrusted recovery context, not independent evidence of completion.

The conversation loop executes at most one phone operation per model turn. It
uses the returned result and any user amendments before planning the next turn.
Repeated text-only responses or invalid completion requests are bounded by
separate non-progress guards. Restarting the app recovers interrupted work
without automatically executing it. See
[`pi-conversation.mjs`](../../node-runtime/src/pi-conversation.mjs) and
[`TaskLifecycle.kt`](../../app/src/main/java/lifeos/md/tapilot/conversation/TaskLifecycle.kt).

A cancellation timeout does not authorize a replacement task. The saved message
stays editable while a later explicit send rechecks a failed stop. Only an acknowledged
stop clears the execution barrier; no failed or unknown phone action is replayed.
Known runtime failures retain stable codes in history so their explanation follows
the current app language. Provider diagnostics and observed failure evidence remain
available rather than being replaced by a generic translated message.

`ModelTransport` owns model HTTP recovery, separate from task recovery. A request
can reconnect once after eligible connection failures or a timeout before
response headers, within the original 120-second deadline. The default header
idle timeout is 60 seconds; after headers, the configured body timeout is
restored. Cancellation, authentication rejection and body-read timeouts do not
trigger that timeout retry. The transport never replays phone actions. These
limits describe the native HTTP transport; individual agent operations can
impose a shorter model deadline.

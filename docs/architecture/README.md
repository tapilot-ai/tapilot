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

`NativeConversationCoordinator` owns main-thread UI coordination. `TaskExecution`
owns active task/session identity and cancellation acknowledgement. `PendingTurn`
models queued input, `ConversationEffects` persists before external operations,
and `ConversationModelClient` handles provider-specific request/replay formats.
`ConversationSettings` owns configuration writes and connection-check state.
The worker is injected. Existing version-1 history JSON remains a storage/agent
interchange format so saved conversations remain compatible; it is not a new
public SDK. `ConversationWriter` serializes snapshots off the UI thread.

## Build boundaries

The Node bundle installs the real local adapter through a `file:` dependency
with the same relative location in source and staging. It never installs another
package under the adapter's name and replaces it afterward. Bundle identity
covers source, adapter, deployment lockfile, workspace settings and patches.
Generated assets are validated before Android packaging.

The legacy web UI is excluded from ordinary APKs. Generated plugin HTML is checked
against its source by the root gate. Keep source changes and regenerated assets together.

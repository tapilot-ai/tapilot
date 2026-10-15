# Device protocol

The TypeScript adapter declares the action and frame types in
`packages/android-accessibility/src/protocol.ts`. Kotlin `DeviceProtocol` parses
wire messages into sealed `DeviceCommand` values; `AndroidDeviceService` executes
them. Protocol version is 1. The executable shared samples are in
`fixtures/device.json`, consumed by Kotlin parser tests and the repository's
wireless adapter integration test.

| Method | Input | Result |
| --- | --- | --- |
| `device.getCapabilities` | empty | version and available capabilities |
| `device.getSize` | empty | width and height |
| `device.capture` | empty | data URL and frame geometry |
| `device.prepareAction` | empty | fresh frame geometry; wireless omits image bytes |
| `device.preview` | optional format (`jpeg`) | preview image and capture timestamp; does not replace an action frame |
| `device.execute` | action and optional frameId | completion or error |
| `task.cancel` / `task.complete` | session identity | cancel/retire that session |

The wireless transport requires an observed frame for actions and imposes its own
allowlist, bounds and lock checks. It does not expose arbitrary application RPCs.
Legacy/stdio requests use `protocolVersion`, `requestId`, `sessionId`, `method`
and `params`. Wireless authenticates the connection and uses its own request ID;
the phone assigns the session. Transport envelopes are not native application APIs.

Frame validation must occur before action execution. Disconnect or timeout can
leave an action's outcome unknown: observe again, never blindly replay the mutation.
Preview capture is independent of the frame consumed by an action. Changes to wire
fields must update both parsers/adapters and these contract tests.

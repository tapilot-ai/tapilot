# Conversation boundary

The app's task worker is separate from main-thread UI coordination. This is an
internal contract, not a public plugin API. Version-1 conversation history remains
compatible; new error codes are optional fields and older text-only failures still render.

## Phone operations

`operate_phone` is decoded into `PhoneOperation` before invoking the native task
callback. `instruction` and `success_condition` must be non-blank strings. Missing
`mode`, `app` and `effort` default to `act`, an empty app and `fast` respectively.
Explicit `null`, wrong types, unknown modes and unknown planning efforts fail before
any device action. Allowed modes are `act` and `observe`; efforts are `fast`,
`balance` and `deepThink`. A deep operation's timeout override never mutates the
environment of later operations.

The model schema additionally contains `plan` and `finishWhenVerified`; Pi owns
these fields and the task-level completion rules. Kotlin owns navigation, confirmation,
phone execution and evidence collection. Changing a description does not bypass
the runtime's success, cancellation or freshness checks.

## Failure and cancellation

Known failures carry `errorCode` across the stdio protocol and into history. Node
defaults to English and receives the app's resolved language. Kotlin renders a known
code in the current language; unknown provider errors retain their diagnostic text.
The last operation's observed result and reports remain attached to failed tasks.

Cancellation is checked before external work and again after asynchronous results.
A result arriving after cancellation cannot mark the task successful. A stale session
cannot settle a replacement session. If runtime stop acknowledgement fails or times
out, a new task cannot begin; an explicit retry can request another acknowledgement.
Restart recovery saves interrupted work without automatically running it.

## Verification

Both `ConversationContractTest` and `conversation-contract.test.mjs` read
[`fixtures/conversation.json`](fixtures/conversation.json). The shared fixtures cover
decoding defaults, invalid requests and bilingual failure messages.
`ConversationTaskRunnerTest` exercises clarification/resume, amendments, cancellation,
verification failure, report retention, environment isolation and cleanup failure.
`TaskExecutionTest` covers ownership and the cancellation barrier.

Run `pnpm runtime:bundle` and `./gradlew :app:testDebugUnitTest`. These model-free
tests verify application contracts; real model, microphone and gesture acceptance
remains a separate device run.

# Tapilot 0.1.4 Beta

- Use Pi Agent to manage conversational tasks, plans, and context while Midscene executes and verifies phone operations.
- Expand task messages to view plans, operation outcomes, and attempt counts.
- Add instructions during execution, resume paused tasks, or explicitly switch to a new task.
- Reduce historical tool logs in model requests while preserving original requirements and durable checkpoints.
- Retry failed tasks with fresh time and operation counters.
- Fix dropped characters during rapid typing.

Android 11+, ARM64. This beta uses a debug signing certificate.

Follow-up verification: an authorized Volcengine key passed on-device connection checks and actual API audio transcription. The original key returned a resource-permission HTTP 403. Deepgram was not configured on the test device.

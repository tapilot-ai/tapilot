# Privacy and data handling

[简体中文](PRIVACY.zh.md) · Updated 2026-10-15

Tapilot runs its UI, task orchestration and device adapter on your phone. Model
inference is remote. This document describes the current repository implementation;
a distributor that changes providers, telemetry or storage must update it.

## Data flows

| Feature you use | Data sent | Recipient |
| --- | --- | --- |
| Phone automation | Screenshots of the visible screen, concrete instructions and operation context | Your configured vision-model provider, or the selected ChatGPT connection |
| Conversation and attachments | Messages, retained context and selected image/document content | Your configured conversation-model provider |
| Voice transcription | Recorded microphone audio | Your selected Volcengine or Deepgram ASR service |
| Optional transcript polishing | Transcribed text and polishing instruction | The configured polishing provider, or the selected ChatGPT account in SIWC mode |
| Speech playback | Reply/sample text and voice selection | Your selected Volcengine or Deepgram TTS service |
| Wireless control | Screen images, input and device-control commands | The computer you pair; its ChatGPT/Codex host or optional AI provider can process that content |
| Update checks / download links | HTTPS requests and connection metadata; no task body is added by the update checker | `tapilot.lifeos.md`, fallback `tapilot-site.pages.dev`; the selected download host |
| Sign-in and model discovery | OAuth requests, account authentication and model requests | The selected account/provider service |

Network recipients can observe connection metadata, including IP address and
request timing. Their own terms and retention policies apply; Tapilot cannot
delete provider-side records. Screenshots can include information from other
apps. Avoid displaying passwords, financial information or private conversations
during automation. The visual model does not need the accessibility UI tree.

The source does not configure a separate maintainer-hosted analytics or crash
collection service. This is not a promise that Android, your app distributor,
model provider or plugin host collects no diagnostics.

## Permissions and controls

Accessibility access enables screenshots, gestures and text editing. Microphone
access is used for voice input; camera access is used for camera attachments and
QR pairing. Grant only the features you intend to use. Recording is initiated
through the app's voice controls. Speech providers need separate credentials and
ASR/TTS permissions. New SIWC sign-ins enable transcript polishing if you have
not previously chosen a polishing setting; turn it off in Speech polishing if
unwanted. Automatic reply playback has its own setting.

New installations default to automatic phone execution (YOLO). Before a task,
you can choose critical-operation or per-operation confirmation in the composer.
Stop cancels the current task. Disabling Accessibility access prevents native
phone control. Stop/revoke wireless control separately to disconnect a computer.
These controls cannot undo actions already performed or retract transmitted data.

## Local storage and deletion

- The phone saves conversations, task checkpoints, attachments and reports in
  app-private storage. API keys and account tokens are encrypted with Android
  Keystore-backed credentials. Conversation/report files are not described as
  end-to-end encrypted.
- Reports normally retain at most 20 files and 200 MiB, while always retaining
  the newest report. Do not assume the same bound applies to conversation history,
  attachments, temporary files or exported copies.
- Clear chat history in Settings to remove stored messages and prune attachments.
  Reports have a separate Delete action in the report viewer; clearing chat history
  is not a report-storage wipe. Android's Clear storage or uninstall removes the
  app's local data. Sign out to remove account tokens; remote revocation is
  attempted but can fail when offline. Manage provider authorizations separately.
- Shared/exported HTML, videos and screenshots live outside app-private storage.
  Delete them from Downloads, gallery, recipients and backups yourself.
- The computer plugin stores pairing data in `~/.config/tapilot/wireless.json`
  and state/authentication/reports under `~/.local/share/android-use` by default.
  `TAPILOT_CONFIG` and `ANDROID_USE_STATE_DIR` can change those paths. Disconnect
  the plugin before deleting its local files. Sign out of its AI account and
  revoke phone pairing separately. Uninstalling the phone app does not clean the
  computer, exports or backups.

## Contact

Report implementation/privacy defects through repository issues without including
personal data. For a credential leak or security vulnerability, follow
[SECURITY.md](SECURITY.md); do not attach raw reports or pairing links publicly.

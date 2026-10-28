<p align="center"><img src="public/tapilot-icon.png" width="96" alt="Tapilot icon"></p>

# Tapilot

**Pure-vision Android automation powered by [Midscene](https://github.com/web-infra-dev/midscene).**

**English** · [简体中文](README.zh.md)

Tell Tapilot what to do. Midscene reads the phone's screenshots, locates visible
targets and drives the next action. Tapilot runs the agent on your Android phone,
with a native chat interface and replayable execution reports.

Visual navigation uses screenshots rather than DOM selectors, resource IDs or
UI-tree locators. Android's `AccessibilityService` supplies screenshots, gestures,
system navigation and text input. Normal use requires **no ADB, Shizuku or root**.
This is an experimental project; model decisions and device behavior can fail.

## Demo

[English promotional video](promo-video/en/tapilot-promo.mp4) · [中文宣传片](promo-video/zh/tapilot-promo.mp4) · Normal-speed short edits, approximately 78 seconds.

## How the visual loop works

```mermaid
flowchart LR
    A[Your instruction] --> B[Phone screenshot]
    B --> C[Midscene vision planning and locating]
    C --> D[Android action]
    D --> E[New screenshot and verification]
    E --> C
    E --> F[Result and execution report]
```

Midscene supplies the visual planning, locating, action execution and reports.
Tapilot supplies the native Android UI, device adapter, model transport, saved
conversations and task lifecycle. The embedded Node.js worker runs on the phone;
model inference uses the configured remote provider. **On-device orchestration
does not mean offline inference.**

Accessibility is the device execution layer, not the visual locator. Native text
editing can use the focused editable accessibility node, and app launch uses
Android's launcher catalogue. Custom-rendered inputs and protected screens can
therefore remain unsupported even when their pixels are visible.

## What you can do

- **Operate apps in natural language.** Tap, swipe, scroll, type, launch apps and
  move between them using Midscene's screenshot-based understanding.
- **Follow a task across apps.** Keep plans and operation results, add a requirement
  while running, or explicitly stop and retry. Interrupted tasks do not replay
  automatically after an app restart.
- **Check results on screen.** Each phone operation ends with a separate screenshot
  check against the current requirements. Missing evidence remains unknown, and
  verified progress is retained for later steps.
- **Inspect what happened.** Open the Midscene HTML replay with screenshots,
  model plans, actions and errors; export it when needed.
- **Talk to the phone.** Optional Volcengine / Deepgram transcription and speech
  playback, with separate provider credentials and an automatic-read setting.
- **Control it over Wi-Fi.** The local [Android Use plugin](plugins/android-use/README.md)
  connects a paired computer to the phone over pinned TLS. Wireless use requires
  an unlocked phone and exclusive control, without ADB or USB.

## Try it

Requires an **arm64 Android 11+** phone and a supported vision model. There is no
verified public release pipeline yet; build a development APK using the steps below.

1. Install the APK, open Tapilot and enable **Tapilot device control** in Android
   Accessibility settings.
2. In Tapilot settings, configure an HTTPS vision-model API, or choose the existing
   ChatGPT (SIWC) connection option where available. Fill credentials on the phone.
3. Start with a simple visible task, such as opening Calculator and computing 12 × 8. Inspect
   the actual screen and execution record before relying on the reply.

The composer offers automatic execution (YOLO), confirmation for critical
operations, or confirmation for every phone operation. **New installations default
to YOLO.** Choose the confirmation mode appropriate to your task before sending it.

### Set up voice input (optional)

In **Settings → Voice input**, choose a provider. The app includes links to its
console and official setup guide:

- **Volcengine:** sign in and complete identity verification, enable Streaming
  Speech Recognition 2.0 in Doubao Voice, then create a key in
  [API Key management](https://console.volcengine.com/speech/new/setting/apikeys).
  Use a Doubao Voice API Key, not an Ark model key or legacy APP ID.
  [Official guide](https://docs.volcengine.com/docs/DoubaoVoice/QuickStartNewConsole?lang=en).
- **Deepgram:** sign in to the [console](https://console.deepgram.com/), select a
  project, then open **Settings → API Keys → Create a New API Key** and grant
  transcription access. Copy the secret when created; it is only shown once.
  [Official guide](https://developers.deepgram.com/docs/create-additional-api-keys).

Paste the key into the app and select **Check connection** to save and verify it.

## Build and develop

Use Node.js 24, pnpm 12.4.1, JDK 17, Android SDK Platform 35 and Build Tools 35.0.0.
Set `JAVA_HOME` and `ANDROID_HOME`. See [CONTRIBUTING](CONTRIBUTING.md) for the
complete host prerequisites and signing setup.

```sh
pnpm install --frozen-lockfile
npm ci --prefix plugins/android-use
node plugins/android-use/scripts/patch-midscene-zod.mjs plugins/android-use
npm ci --prefix tests/midscene
node plugins/android-use/scripts/patch-midscene-zod.mjs tests/midscene
pnpm build:android
pnpm check:quick
```

The local APK is `app/build/outputs/apk/beta/app-beta.apk`. It uses this machine's
development signing certificate, which cannot update a differently signed app.
Production signing is configured separately. ADB may be used by developers to
install and test; it is not required for Tapilot's runtime automation.

| Directory | Purpose |
| --- | --- |
| `app/` | Native Android UI, credentials, device actions and lifecycle |
| `node-runtime/` | Embedded Pi agent, Midscene runner and deployment dependencies |
| `packages/android-accessibility/` | Midscene adapter for Android AccessibilityService |
| `plugins/android-use/` | Computer-side Wi-Fi control plugin |
| `tests/midscene/` | 23 E2E cases and opt-in device fault fixtures |

Run `pnpm check` for the full local test/build gate and `pnpm check:security` for
dependency audits. Device-backed E2E is separate; 23 authored cases do not imply
23 verified passes. See the [E2E guide and evidence](tests/midscene/README.md) and
[contribution guide](CONTRIBUTING.md).

Historical device runs do not establish a pass for the latest source.

## Limits and data

- A model can misread a screen, infer an unobserved fact or give an incorrect
  completion answer. The E2E ledger distinguishes repaired cases, remaining
  failures and untested builds. Verify important outcomes from the actual screen.
- Locked or protected screens, OEM background restrictions and inaccessible
  custom text inputs can stop execution. Only arm64 devices are bundled today.
- Screenshots and task content are sent to your selected model provider. Optional
  ASR/TTS sends audio or reply text to the selected speech provider. Account tokens
  and API keys stay in Android Keystore-backed storage; this does not make model
  requests local or prevent provider-side processing.
- Reports contain screenshots, prompts and model output. Review them before
  sharing. The phone automatically prunes older reports to keep at most 20 files
  and 200 MB, always retaining the newest report even if it exceeds that size.
  Export reports you need to keep before running many more operations. Exported
  reports and computer-plugin data have their own lifetimes.

Read [PRIVACY.md](PRIVACY.md) for data handling and deletion, and
[SECURITY.md](SECURITY.md) for transport, credential storage and vulnerability reporting.

## Built on

**[Midscene](https://github.com/web-infra-dev/midscene)** is the foundation of the
visual automation, Android adapter integration, Test Runner and execution reports.

Thanks also to [Pi](https://github.com/earendil-works/pi) for the conversation/tool
loop, [Node.js](https://nodejs.org/) and [Termux](https://github.com/termux/termux-packages)
for the Android runtime, [Android Jetpack Compose](https://developer.android.com/compose)
for the native UI, and the [Model Context Protocol](https://github.com/modelcontextprotocol)
for the computer plugin. See [CREDITS.md](CREDITS.md) for more upstream projects,
license identifiers and redistribution work still required before release.

## License

[MIT](LICENSE). Third-party components retain their own licenses.
See [CREDITS.md](CREDITS.md) and [redistribution notes](THIRD_PARTY_LICENSES.md).

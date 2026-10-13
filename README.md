# Tapilot

**English** · [简体中文](README.zh.md)

Experimental on-device Android client powered by `AccessibilityService`. It
does not require ADB, Shizuku, or root while running.

The chat screen uses Kotlin and Jetpack Compose with a native Android text editor,
hold-to-record on an empty, unfocused editor (with a “Hold to speak” hint), native
editing menus while focused or populated (including whitespace), and photo, camera,
PDF, and text attachments. Recording releases insert a transcript by default; slide
left to cancel or right to send explicitly. Chat, settings and advanced YAML editing are native. Kotlin owns the conversation
agent, private history, retries and global request queue; Midscene runs in the
embedded Node.js process. A one-time temporary WebView imports legacy storage
and is destroyed afterward. Daily chat no longer requires a hidden WebView.
HTML reports retain their dedicated WebView, and API keys retain Android Keystore encryption.

Tapilot ships under the `lifeos.md.tapilot` application ID. An APK signed with
the same key can update an existing installation without clearing its app data;
changing the signing key requires a new installation.

## ChatGPT wireless control

A local ChatGPT desktop / Codex plugin can control this Android phone over Wi-Fi
using screenshots and individual accessibility actions. Enable wireless control
in Tapilot settings and pair the computer once. No USB, ADB, or on-phone model API
key is needed during use. See [plugin setup and limitations](plugins/android-use/README.md).

## Build

Requirements:

- JDK 17
- Android SDK platform 35 and build tools
- Node and pnpm

From this directory:

```sh
pnpm install
pnpm run build:adapter
pnpm run runtime:bundle
pnpm run runtime:node
./gradlew assembleDebug
```

Or run the combined script:

```sh
pnpm run build:android
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Ordinary Android builds do not build the legacy web UI. For the optional
`-PspeechVerification` debug entry, run `pnpm run build` first.
`runtime:bundle` creates a self-contained, symlink-free Node module bundle and
runs a real `@midscene/test` YAML smoke project before packaging it.
`runtime:node` downloads pinned Termux arm64 packages, verifies their SHA-256
checksums, and patches the ELF dependencies for APK loading. Generated bundles
and native binaries are build artifacts and are not committed.
`packages/android-accessibility` (published as
[`@tapilot/android-accessibility`](https://www.npmjs.com/package/@tapilot/android-accessibility))
is this community project's independently buildable device adapter
(`pnpm run build:adapter`). Its public package contains
the device actions and native device protocol. Kotlin in `app/src/main/java/` owns
app UI, conversation and configuration. `runtime/` retains the legacy web UI and
optional speech verification entries, excluded from ordinary APKs. Core, Shared, and Test
are consumed directly from the compatible upstream Midscene release. The
standalone repository does not carry or patch snapshots of those packages.

## Embedded Node runtime

The APK includes Node.js 24 for arm64 and a bundled `@midscene/test` runtime.
Android extracts the versioned JavaScript bundle into app-private storage and
starts Node as a supervised child process. Android and Node communicate through
structured JSON RPC over standard input and output; no local network port is
opened. Only allow-listed model environment variables are forwarded to the
process, and one runner process may be active at a time.

The settings page runtime check verifies both the Node executable and that the
Test Runner module can be loaded. The bundled build executes a regular Test
Runner smoke case and a bridge smoke case against a simulated Android device
interface. A separate conversation agent owns the saved chat context and uses
the configured model API to decide when the phone tool is needed. Each phone
tool call sends one concrete instruction as a one-step `aiAct` YAML case to the
on-device `@midscene/test` and `@midscene/core`; its result returns to the
conversation agent for the final reply. User YAML runs directly as entered.

## First run

At task start the native service queries visible launcher entries (label,
package and component) and available system actions. The launcher catalogue is
included in the model's Launch action description, so it can resolve localized
app names without guessing package names. App labels are data, not instructions;
ambiguous requests should be clarified. Installation changes are picked up on
the next task, not by continuous polling.

Beyond tap, swipe, pinch, input, Back/Home/Recents and Launch, the adapter provides:

- Notification shade, Quick Settings, split-screen toggle, lock screen, power
  menu and screenshot saving where supported. PNG screenshots are saved to the
  gallery under `Pictures/Tapilot` with assistant overlays hidden. Saving must
  finish before the action succeeds; secure screens may reject capture.
- MultiTouchGesture: 1–10 simultaneous paths, including long-press before a
  continuous drag. Rotate and TwoFingerPan cover rotation and translation.
- EditText: selection (UTF-16 offsets), selection replacement, copy/cut/paste and
  the keyboard Search/Send/Done action. Requires a compatible focused text field.

Unavailable system actions are omitted and checked again at execution. Invalid
coordinates/ranges fail explicitly. Dismissing a recent-app card is not force-stop;
never clear all apps when asked to dismiss one. Locking, power controls and text
submission require user intent. Adapter/native tests cover dispatch and validation;
OEM-specific gesture/system behavior still requires physical-device verification.

On Android 14+, model screenshots opportunistically capture a single full-screen
application window without hiding the animated edge effect. Visible system bars,
keyboards, dialogs, split-screen, uncertain geometry or capture errors use the
full-display fallback (temporarily hide overlays). Failed window attempts back off
for 30 seconds. Window images are accepted only with matching display dimensions,
orientation and window ID, keeping touch coordinates unchanged. User-requested
gallery screenshots always capture the full display. This is best effort, not a
guarantee of uninterrupted effects on every device or page.

1. Open the app and follow the setup card into Accessibility settings.
2. Enable **Tapilot device control** and return. Once enabled, only a green
   status dot remains at the top left.
3. Configure an HTTPS OpenAI-compatible vision model.
4. Send a message. Tapilot shows the result in the current conversation; you can follow up or start a new chat.

### Global conversation and interruption

Tap the floating assistant from another app to open the conversation above the
current screen. The panel and full-screen chat share one conversation agent and
saved context. Expand opens the same conversation; Dismiss returns to the app
underneath, and reopening the assistant keeps the conversation.

The composer remains available during a task, including voice input and
attachments. Text sent during execution steers the same Pi agent task at the
next safe turn, after the current phone operation finishes. Explicit Stop cancels
both the agent and phone runner; attachments can interrupt and start a new turn.
With an empty composer, the send button becomes Stop. While phone
automation is running, tap the floating task status to interrupt and reopen the
conversation. Interrupted turns remain marked as stopped; only returned phone
operation results are supplied as verified progress to the next turn.

### Task agent

Pi agent-core owns the model/tool loop and transcript in a separate on-device
Node process. API credentials and HTTP requests stay in Android. The native
coordinator stores task goals, plans, tool results, context checkpoints and input
updates atomically. Tasks can wait for clarification, be stopped, or resume from
saved progress after an explicit retry/continue. Restarting the app never runs
phone actions automatically. Each attempt gets fresh counters from the existing
limits settings (100 phone operations and 100 model turns by default; zero
disables a limit). Midscene receives only the current concrete instruction and
visible success condition, and phone actions always execute sequentially.

Voice input supports the official Volcengine Seed ASR 2.0 API and Deepgram Nova-3
(Simplified Chinese or US English, following the app language). Choose a provider in model settings and save its API Key. Volcengine uses
an API Key from the new Doubao Speech console and the fixed resource ID
`volc.seedasr.sauc.duration`. Hold an empty, unfocused composer to speak; the keyboard
stays closed and a speech bubble with Cancel / Send controls appears. Release normally
to insert text; release over Cancel or Send for that action, or move back to insert.
Focused or populated editors retain native editing menus, including whitespace-only drafts.
Cancellation remains available during transcription and rejects late results. Chat,
the global assistant share these rules and retain Tapilot colors. The optional legacy WebView verification entry follows the same rules. The first recording requires microphone
permission. Audio streams through the selected provider's WebSocket API without
creating a recording file. Each provider's key is encrypted separately with Android
Keystore. Android speech recognition is not used.

### 连续语音对话

点击空输入框右侧的声波图标进入语音对话。停顿约 1.2 秒后自动发送，
助手会用 API 播报回答或追问，然后继续监听；每一轮沿用当前对话的 Pi 上下文。
点击“打断”停止播报并接着说，“暂停”关闭麦克风，“结束”返回文字输入。
收到指令后立即给出提示音，并用 API 回应“收到”；任务同时开始处理。
语音会话由前台服务维持，切到其他应用仍能对答和听结果，通知栏可暂停、继续、打断或结束。
30 秒没有说话会暂停监听；返回键将界面收起，点击“结束”才退出语音。
播报期间关闭麦克风，当前采用点击打断，暂不支持说话直接打断或唤醒词。

普通聊天也默认朗读新回答，可在设置关闭自动朗读，或点击单条回答的“朗读 / 停止播报”。
历史消息不会自动重播。设置中的“语音对话与播报”跟随转写服务，可试听、设置音色和独立播报密钥；
留空复用转写密钥。火山引擎使用 Seed TTS 2.0，默认音色与 Studio 一致；
Deepgram 使用 Aura-2，默认音色为英文。TTS 权限与 ASR 权限分别开通。
密钥使用 Android Keystore 加密保存。模型请求失败会如实播报失败，可通过任务重试按钮重试。

The interface follows the Android system language by default. Simplified
Chinese and English can also be selected explicitly from the app-styled
language sheet. The choice applies to the native settings, native accessibility
setup, and task notifications, and is retained across app restarts. The natural
language editor starts compact and grows when focused. YAML and Test Runner
controls are available only when Advanced mode is enabled; the YAML editor can
still expand to full screen.
Accessibility service connections and disconnections are pushed to the page in
real time. If Android restarts the service or binds it after the page resumes,
the setup card first shows a reconnecting state, then changes to the green
status dot without reopening the app. Keep the application ID, signing key, and
accessibility service component stable across upgrades. During local testing,
do not run `adb shell am force-stop lifeos.md.tapilot`: Android treats a
force-stop as an explicit request to disable the package's accessibility
service. Use `adb install -r` followed by a normal launch instead.

Android exclusively manages the accessibility-service lifecycle. When the
permission remains enabled, Android binds the service again as needed; the app
does not run a fake permanent foreground service. On aggressive OEM systems,
set Tapilot battery usage to **Unrestricted**, add it to any vendor-specific
background allowlist, and avoid task-manager cleanup. The app cannot silently
restore a permission removed by the user, a force-stop, or the device vendor.

The optional global floating ball is backed by an accessibility overlay. It is
shown only while Tapilot is outside the foreground and no task is running.
Tapping it returns to the natural-language field; dragging it changes its saved
screen position.

## Execution status

While a task is active, a touch-through Accessibility overlay renders an
animated effect around the complete display perimeter, including the status
bar, navigation bar, and cutout area. There is no persistent right-side overlay
button. Before each model screenshot, the native layer hides the edge effect
and waits for the window to settle, so the effect is excluded from Midscene's
visual input. Start, success, failure, and cancellation are also
reported through toasts and accessibility announcements. They are also
reflected in a foreground-service notification. The foreground service raises
the process priority while a task is active, reducing the chance that Android
reclaims the task process after switching to the target app.

## Debug reports

The input field has a send button in its bottom-right corner. The keyboard Send
action starts the task; Shift+Enter inserts a newline. The heading icon is decorative.
The replay toolbar offers **Share HTML**, **Save HTML file to…**, and a Downloads shortcut.
Export Video saves WebM video to the system video library under **Movies/Tapilot**.
Gallery support for WebM varies; the file is also available in the file manager.
Failed tasks retain a report link even when only a Test Runner report is available.
The assistant uses the system recent-apps action to open the task overview before
dismissing a requested app card. Removing a card is not a system force-stop.

Every task that completes in the Midscene runtime produces a self-contained
HTML report after success or failure. Tap **View report** on the result card to
inspect the latest model plan, action trace,
screenshots, and error context. The on-device Node Test Runner writes the
standard Midscene report directly into the run directory. Android then imports
it into the report repository through a temporary file and atomic rename, so a
large HTML document no longer needs to cross the WebView bridge.
The report header uses the logo bundled in the APK, so it remains intact when
the device cannot reach the image CDN.

Reports may contain task prompts, model output, and screenshots captured during
execution. Clearing app data or uninstalling the app removes them. The result
card opens the latest report, while **History** lists earlier tasks and their
reports. Deleting a history entry asks for confirmation and removes its HTML
files. Private storage retains at most 20 reports and 200 MB; the newest report
is always kept. Older reports are removed automatically, and incomplete drafts
are aborted and cleaned up.

## Action support

The runtime reuses the standard Midscene JS SDK mobile actions. The native
bridge implements `Tap`, `DoubleClick`, `LongPress`, `DragAndDrop`, `Swipe`,
`Pinch`, `Scroll`, `PullGesture`, `Input`, `ClearInput`, `KeyboardPress`,
`CursorMove`, Android back/home/recent-apps, and `Launch`. A regular Android app
cannot obtain ADB shell access or force-stop arbitrary apps, so `RunAdbShell`
and `Terminate` are intentionally unavailable without Shizuku, ADB, or root.

Each model preset keeps its own configuration. API Keys are encrypted with an
Android Keystore key before being written to app-private preferences; non-secret
model fields and conversation history are atomically saved in app-private files. They remain available after
tasks and app restarts. **Restore defaults** clears the selected preset's API
Key, while clearing app data or uninstalling removes every preset.
Model HTTP requests are sent by native Android code and conversation replies are
non-streaming. Stopping a task disconnects its active request and prevents late
results from calling tools. Incremental native streaming is a follow-up optimization.

Phone operations and YAML runs reuse the existing Midscene Agent, Test Runner,
and standard reporter inside on-device Node.js. The conversation agent owns
context, tool calls, and final replies; Android provides model transport, the
device interface, and process/RPC lifecycle. YAML supports the common Test Runner nodes plus the direct Android
nodes `launch`, `home`, `back`, and `recentApps`. Shell commands and force-stop
remain unavailable under the regular app sandbox.

Text input requires the target control to expose an editable accessibility
node. Canvas, game, and custom-rendered inputs may reject it. This is an
experimental build and requires Android 11 or newer.

## Internal development

For authorized development, please read [CONTRIBUTING.md](CONTRIBUTING.md) for
the development setup, testing expectations, and pull request process, and
note the [Code of Conduct](CODE_OF_CONDUCT.md). For vulnerability reports,
follow [SECURITY.md](SECURITY.md) instead of opening a public issue.

## License

Tapilot is proprietary software. All rights reserved. See [LICENSE](LICENSE).
Third-party components remain subject to their respective licenses.

Task messages expose expandable plan and verified operation results. Explicit “new task” instructions stop the current task before switching. Opening the global panel safely pauses phone actions; the next amendment resumes the saved task. Long model requests compact old logs without discarding user constraints or changing saved checkpoints.

### Use your ChatGPT plan

In Settings → Model connection, select **ChatGPT (SIWC)**, choose **Continue with ChatGPT**,
complete authorization in the system browser, then select an available model. Conversation, planning, and Midscene phone actions
all use the selected ChatGPT model; no separate model API Key is required.
Choose a GPT-6 model or a supported GPT-5 vision model. Switch to **API Key** to
use a regular model API instead. Both configurations are retained when switching;
only the selected connection is used. In SIWC mode, optional transcript polishing
also uses the selected ChatGPT account and model, without a separate polishing key.
API Key mode retains its independent polishing endpoint, model, and encrypted key.
Settings → Speech polishing → Try polishing checks a sample transcript on the device.
Failed polishing preserves the original transcript. ASR keeps its own provider:
SIWC currently supports neither audio input nor transcription APIs; see the
[preview limitations](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).

Tapilot uses the official SIWC public-client OAuth flow with a loopback callback,
PKCE, and ID-token validation. Account tokens are stored separately using Android
Keystore encryption and refreshed before expiry. Every phone-model request obtains
current credentials from the native store. A 401 triggers one refresh and retries
only that inference request, without repeating phone actions. Refresh tokens remain
in the native credential store. Phone tasks have no fixed wall-clock deadline;
network requests retain timeouts and tasks remain cancellable. The account picker supports
separate registrations; signing out removes local tokens and attempts remote
revocation. Pending sign-ins are encrypted too: after a process restart, Tapilot
reopens the original callback port so the browser callback can be retried within
15 minutes. An expired sign-in must be started again. Manage app usage and authorization in ChatGPT Settings → Usage.

Inference uses the Responses API with `store: false` and streaming, and only a
completed response is accepted. Account availability and plan-sharing permission
are determined by OpenAI. See the [SIWC documentation](https://developers.openai.com/siwc/token-sharing-open-source).

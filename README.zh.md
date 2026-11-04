<p align="center"><img src="public/tapilot-icon.png" width="96" alt="Tapilot 图标"></p>

# Tapilot

**把手机上的事交给 Tapilot，用日常说话的方式告诉它。**

[English](README.md) · **简体中文**

让 Tapilot 打开应用、查找屏幕上的信息，或跨应用完成任务。
执行中可以补充要求，也可以随时停止；结束后能回看截图和实际操作。

第一次可以试试：“打开计算器，计算 12 × 8。”先看手机上的结果，再打开执行报告，
看看 Tapilot 是怎样完成的。

Tapilot 会操作手机当前显示的页面，使用其他应用前请先等任务结束或停止任务。
开始前需要连接支持的视觉模型，并开启 Android 无障碍权限。
日常使用**不需要 ADB、Shizuku 或 root**。项目仍处于实验阶段，重要结果请自行核对。

Powered by [Midscene](https://github.com/web-infra-dev/midscene)。

[官网与视频演示](https://tapilot.lifeos.md/zh/)

## 演示

[![观看中文演示](https://tapilot.lifeos.md/videos/tapilot-zh.jpg)](https://www.bilibili.com/video/BV1ANxb6XEMy/)

[观看中文演示](https://www.bilibili.com/video/BV1ANxb6XEMy/) · 约 78 秒。

[下载视频](https://tapilot.lifeos.md/videos/tapilot-zh.mp4)

## 可以做什么

- **用自然语言操作应用。** 基于 Midscene 的截图理解，完成点击、滑动、滚动、输入、
  启动应用和应用切换。
- **连续完成跨应用任务。** 保留计划与操作结果，执行中追加要求，或明确停止后重试。
  应用重启后不会自动重放被打断的任务。
- **根据画面核对结果。** 每次手机操作结束后，单独采集截图核验当前要求。
  缺少证据时保留未知，已核验的进度供后续步骤使用。
- **回看实际执行过程。** 在 Midscene HTML 报告中查看截图、模型计划、动作和错误，
  按需导出。
- **用语音交互。** 可选火山引擎或 Deepgram 转写与播报，分别配置服务凭据，支持自动朗读回答。
- **通过 Wi-Fi 控制手机。** 本地 [Tapilot 插件](plugins/tapilot/README.md)
  通过固定证书的 TLS 连接已配对手机。无线控制要求手机解锁并独占控制权，不需要 ADB 或 USB。

### 手动接管任务

任务执行中点击**我来处理**，等界面确认停止后再操作手机；已经发出的动作可能在停止过程中完成。
接管卡片提供**返回应用**和**继续任务**。继续时保留原任务上下文，要求 Agent 先检查当前画面，
再决定下一步，不会把原任务作为新请求重发，输入框里的草稿也会保留。
任务等待你补充信息时，同样提供这些入口。确认接管后，离开聊天页不会自动重启任务。
通过悬浮球打开聊天而暂停的任务，确认停止后也会显示接管卡片。

## 开始使用

需要 **arm64 架构、Android 11 或更高版本**的手机，以及支持的视觉模型。
目前尚无验证完成的公开发布流程，可按下方步骤构建开发 APK。

1. 安装 APK，打开 Tapilot，在 Android 无障碍设置中启用 **Tapilot 设备控制**。
2. 在 Tapilot 设置中配置 HTTP 或 HTTPS 视觉模型 API，或在可用时选择现有的 ChatGPT（SIWC）
   连接方式。凭据只在手机内填写。
3. 先试计算器这样的简单可见任务，再查看真实画面和执行记录，核对结果。

输入框支持自动执行（YOLO）、关键操作确认和每次操作确认。
**新安装默认使用 YOLO。** 发送任务前，请根据任务选择合适的确认方式。

首次使用引导会显示模型是否已配置、设备控制是否已连接，并可把计算器示例填入空输入框，
由你检查后发送。已有草稿和附件不会被覆盖。配置完成不等于连接测试通过，
实际任务会检查模型、截图和操作能力。失败后保留引导；成功完成经过核验的手机任务，
或主动选择“跳过引导”后，引导会关闭。

使用局域网自托管模型时，填写兼容 OpenAI 的基础地址，例如
`http://192.168.1.100:8000/v1`，并选择受支持的视觉模型。手机必须能访问该地址，
`localhost` 指的是手机自身。应用仍要求填写 API Key；如果服务端不需要鉴权，
可以填写 `local` 等非空占位值。HTTP 会以明文发送 API Key、消息和截图，
仅在可信网络中使用，其他情况优先使用 HTTPS。

### 配置语音输入（可选）

在 **设置 → 语音输入** 中选择服务商。应用内也提供控制台和官方指南的跳转入口：

- **火山引擎：**登录并完成实名认证，在豆包语音中开通流式语音识别 2.0 服务，
  然后进入 [API Key 管理](https://console.volcengine.com/speech/new/setting/apikeys) 创建密钥。
  这里需要豆包语音 API Key，不能使用方舟模型密钥或旧版 APP ID。
  [官方指南](https://docs.volcengine.com/docs/DoubaoVoice/QuickStartNewConsole?lang=zh)。
- **Deepgram：**登录 [控制台](https://console.deepgram.com/)，选择项目，进入
  **Settings → API Keys → Create a New API Key**，创建允许语音转写的密钥。
  密钥只显示一次，请及时复制。
  [官方指南](https://developers.deepgram.com/docs/create-additional-api-keys)。

将密钥粘贴到应用中，点击 **检查连接** 即可保存并验证。

## 纯视觉操作怎样完成

![纯视觉操作与核验循环](artwork/visual-loop.zh.svg)

Midscene 负责视觉规划、目标定位、动作执行和报告。Tapilot 负责 Android 原生界面、
设备适配、模型请求、会话保存和任务生命周期。内置 Node.js 在手机上运行，
模型推理使用你配置的远程服务。**端侧编排不等于离线推理。**

无障碍服务承担设备执行，不是 Midscene 的视觉定位方式。原生文字编辑可能使用
已聚焦的可编辑无障碍节点；应用启动使用 Android 的应用清单。因此，即使画面可见，
自绘输入控件和受保护页面仍可能无法操作。

## 构建与开发

使用 Node.js 24、pnpm 12.4.1、JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0，
设置 `JAVA_HOME`、`ANDROID_HOME`。完整主机依赖和签名配置见 [CONTRIBUTING](CONTRIBUTING.md)。

```sh
pnpm install --frozen-lockfile
npm ci --prefix plugins/tapilot
node plugins/tapilot/scripts/patch-midscene-zod.mjs plugins/tapilot
npm ci --prefix tests/midscene
node plugins/tapilot/scripts/patch-midscene-zod.mjs tests/midscene
pnpm build:android
pnpm check:quick
```

本地 APK 位于 `app/build/outputs/apk/beta/app-beta.apk`，使用当前电脑的开发签名，
不能覆盖其他签名的安装版本。生产签名单独配置。开发者可以使用 ADB 安装和测试，
Tapilot 日常自动化不需要 ADB。

| 目录 | 用途 |
| --- | --- |
| `app/` | Android 原生界面、凭据、设备动作和生命周期 |
| `node-runtime/` | 内置 Pi Agent、Midscene Runner 和部署依赖 |
| `packages/android-accessibility/` | Android AccessibilityService 的 Midscene 适配器 |
| `plugins/tapilot/` | 电脑端 Wi-Fi 控制插件 |
| `tests/midscene/` | 23 条 E2E 用例和显式开启的设备故障测试 |

`pnpm check` 执行完整本地测试和构建检查，`pnpm check:security` 审计依赖。
真机 E2E 单独运行；写好了 23 条用例，不代表 23 条都已通过。
详见 [E2E 指南与验证记录](tests/midscene/README.md)和[贡献指南](CONTRIBUTING.md)。

执行进程在生成报告前退出时，Tapilot 会显示退出码和可用的错误原因。预热进程
过期后，仅在测试及设备 RPC 尚未开始时重新启动，不会重放已开始的手机任务。

历史真机结果不代表最新源码已通过验收。

## 能力边界与数据

- 模型可能误读画面、推断未看到的信息，或错误地报告任务完成。E2E 记录分别标明
  已修复用例、尚未解决的失败和未测试版本。重要结果应以真实画面为准。
- 锁屏、受保护页面、厂商后台限制和无法通过无障碍输入的自绘控件，都可能中断执行。
  当前只内置 arm64 运行时。
- 截图和任务内容会发给你选择的模型服务。可选 ASR/TTS 会将音频或回答文本发给所选
  语音服务。账号令牌和 API Key 使用 Android Keystore 加密存储，但模型请求不会因此
  变为本地推理，也不会跳过服务商处理。
- 报告包含截图、指令和模型输出，分享前请检查。导出的报告和电脑插件数据分别管理，
  清除手机应用数据不会一并删除它们。

数据处理与删除方式见 [隐私说明](PRIVACY.zh.md)，传输、凭据存储和私下报告漏洞的方式见
[SECURITY.md](SECURITY.md)。

## 上游致谢

**[Midscene](https://github.com/web-infra-dev/midscene)** 是本项目视觉自动化、Android
设备适配集成、Test Runner 和执行报告的基础。

同时感谢 [Pi](https://github.com/earendil-works/pi) 提供对话与工具循环，
[Node.js](https://nodejs.org/) 和 [Termux](https://github.com/termux/termux-packages)
提供 Android 运行时，[Android Jetpack Compose](https://developer.android.com/compose)
提供原生界面，以及 [Model Context Protocol](https://github.com/modelcontextprotocol)
提供电脑插件协议。更多上游项目、许可证标识和发布前仍需完成的分发工作见
[CREDITS.md](CREDITS.md)。

## 可选后台屏幕（实验性）

从 0.1.9 起提供“设置 → 后台屏幕（实验性）→ 用于聊天任务”，默认关闭。
完成 [Shell 授权、手机端配对和悬浮窗权限设置](docs/experiments/shell-assisted-display.zh.md)后，普通聊天请求可以在独立屏幕打开和操作应用，同时继续使用主屏。
无边框悬浮窗显示实际画面。需要手动操作时，先点击任务中的“接管”，完成后明确点击继续，模型会重新观察。
关闭悬浮窗或设置开关会停止任务并释放副屏；连接失败不会把正在执行的任务改到主屏。

设置页保留连接状态和可选的自动恢复，兼容性试用和手动连接方式收进“高级选项”。
虚拟屏在限定的像素预算内匹配当前手机屏幕的尺寸和密度。应用兼容性、缺少副屏无障碍输入时的中文等文字输入、受保护页面和熄屏使用仍有限制。
此功能仍为实验性功能，使用前必须明确授权 Shell 辅助。详见[设置说明与限制](docs/experiments/shell-assisted-display.zh.md)。

## 许可证

采用 [MIT 许可证](LICENSE)。第三方组件继续遵循各自许可证，详见
[上游致谢](CREDITS.md)和[分发说明](THIRD_PARTY_LICENSES.md)。

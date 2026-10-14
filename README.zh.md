# Tapilot

[English](README.md) · **简体中文**

这是一个基于 `AccessibilityService` 的实验性 Android 端侧客户端。运行时不依赖
ADB、Shizuku 或 root。

聊天页和输入区使用 Kotlin + Jetpack Compose，文字输入使用 Android 原生编辑控件。
输入框未聚焦且完全空时显示“按住说话”，支持长按录音；聚焦或已有文字（含空格）时长按使用系统编辑菜单。录音默认松手转文字填入输入框，左侧取消、右侧发送；保留原有配色。支持照片、拍摄、PDF 和文本附件。
聊天、设置页和高级 YAML 编辑界面均为原生实现。对话 Agent、会话历史、重试及全局请求排队由 Kotlin 管理，Midscene 在设备内 Node.js 中执行。升级时只用一次临时 WebView 导入旧存储，导入后销毁；日常聊天不再运行隐藏 WebView。HTML 报告仍由独立 WebView 显示，API Key 继续使用原有 Android Keystore 加密存储。

每次任务开始时，原生服务会查询可见应用的名称、包名和启动组件，以及设备可用的系统动作。
应用清单会放进模型的 Launch 动作描述，帮助它识别中英文应用名称，避免猜包名。
应用名称只作为数据；有多个候选时应询问用户。安装变化会在下次任务刷新，不持续轮询。

除点击、滑动、缩放、输入、返回、主页、最近应用和启动应用外，还提供：

- 通知栏、快捷设置、分屏切换、锁屏、电源菜单、截图保存，按设备支持情况开放。
  截图时隐藏助手悬浮效果，以 PNG 保存到相册的 `Pictures/Tapilot`，写入完成才返回成功。
  系统保护的页面可能禁止截图。
- MultiTouchGesture：1–10 根手指同时执行路径，支持长按后不松手连续拖拽。
  Rotate 和 TwoFingerPan 分别用于双指旋转和平移。
- EditText：选择文字（UTF-16 偏移）、替换选区、复制、剪切、粘贴，以及键盘的搜索/发送/完成。
  需要先聚焦输入框，且目标应用支持相应操作。

不可用的系统动作不会出现在动作列表，执行时也会再次检查。越界坐标和无效选区会明确报错。
划掉最近应用卡片不是强制停止，不能用“清理全部”代替关闭一个应用。
锁屏、电源操作和提交文字必须符合用户意图。已添加适配器与原生测试；不同厂商的手势和系统界面仍需真机验证。

Android 14 及以上，模型截图会尝试直接截取单个全屏应用窗口，让四周特效继续播放。
出现系统栏、键盘、弹窗、分屏、坐标不确定或截图失败时，自动回退到短暂隐藏悬浮层后截全屏。
窗口截图失败后暂停尝试 30 秒；成功的图片也必须与屏幕尺寸、方向和窗口 ID 匹配，避免点击偏移。
用户主动保存到相册的截图始终使用全屏截图。这是尽力而为策略，不保证每台设备、每个页面都不断动画。

Tapilot 采用 `lifeos.md.tapilot` 应用 ID。使用相同签名密钥打出的 APK 可覆盖更新
已安装的 Tapilot 并保留应用数据；更换签名密钥则需要重新安装。

## ChatGPT 无线控制

电脑上的 ChatGPT / Codex 本地插件可以通过 Wi-Fi 截图、点击、输入、滑动和启动应用。
在 Tapilot 设置中开启无线控制，首次使用时配对电脑。运行时不需要 USB、ADB 或手机端
模型 API Key。手机需保持解锁；可从手机设置或通知中停止控制。
安装方法和使用限制见[插件说明](plugins/android-use/README.md)。

聊天输入框底部显示当前执行模式，点击可切换 YOLO、关键操作确认或每次操作确认。新安装默认使用 YOLO，直接执行手机操作，不弹出操作确认；已有用户保存的确认偏好会保留。任务执行期间不能切换模式。

## 构建

需要 JDK 17、Android SDK Platform 35、对应 Build Tools、Node 和 pnpm。

在本目录中构建：

```sh
pnpm install
pnpm run build:adapter
pnpm run runtime:bundle
pnpm run runtime:node
./gradlew assembleBeta --max-workers=2 --no-parallel --no-daemon
```

也可以使用组合命令：

```sh
pnpm run build:android
```

Debug APK 输出到 `app/build/outputs/apk/beta/app-beta.apk`。
普通 Android 构建不再编译旧网页。使用可选的 `-PspeechVerification` 调试入口时，先运行 `pnpm run build`。
`runtime:bundle` 会生成不含符号链接的自包含 Node 依赖包，并在打包前真实执行一个
`@midscene/test` YAML 冒烟项目。`runtime:node` 会下载锁定版本的 Termux arm64 包、
校验 SHA-256，并修补 ELF 依赖以便 APK 加载。生成的依赖包和原生二进制均属于构建产物，
不会提交到仓库。
`packages/android-accessibility`（以
[`@tapilot/android-accessibility`](https://www.npmjs.com/package/@tapilot/android-accessibility)
名义发布）是本社区项目自行维护、可独立构建的设备适配包
（`pnpm run build:adapter`）。它只包含设备动作和原生设备协议；手机操作执行器运行在设备内 Node.js，
应用界面、对话和配置由 `app/src/main/java/` 中的 Kotlin 管理。`runtime/` 保留旧网页实现及可选语音验证入口，不打入普通 APK。Core、Shared、Test 直接使用兼容的
Midscene 上游正式发布版本，独立仓库不再保存或修改这些包的源码快照。

## 内嵌 Node 运行时

APK 内置 arm64 版 Node.js 24 和打包后的 `@midscene/test` 运行时。Android 会将带版本
指纹的 JavaScript 依赖包解压到应用私有目录，并以受监管的子进程启动 Node。Android
与 Node 通过标准输入/输出上的结构化 JSON RPC 通信，不开放本地网络端口。只有白名单内
的模型环境变量会传给子进程，同时最多运行一个 Runner 进程。

设置页的运行时检测会同时验证 Node 可执行文件以及 Test Runner 模块是否能加载。构建
阶段还会执行普通 Test Runner 冒烟用例，以及连接模拟 Android device interface 的桥接
冒烟用例。聊天由独立的对话 Agent 管理完整的已保存会话上下文，并通过配置的模型 API
决定是否调用手机工具。每次手机工具调用只把一条具体指令转换为单步 `aiAct` YAML，交给
设备内的 `@midscene/test` 和 `@midscene/core` 执行；Midscene 的结果再交回对话 Agent
组织回复。用户输入的 YAML 保持原样，直接由 Test Runner 执行。

## 首次使用

1. 打开应用，按照顶部引导进入辅助功能设置。
2. 启用 **Tapilot 设备控制**，然后返回应用；启用后页面左上角只显示绿色状态点。
3. 配置一个兼容 OpenAI 接口的 HTTPS 视觉模型。
4. 输入消息并点击发送。Tapilot 会在当前对话中显示执行结果；可以继续追问或点右上角开启新对话。

语音输入支持火山引擎 Seed ASR 2.0 官方 API 和 Deepgram Nova-3（简体中文或美式英语，跟随界面语言）。
在模型配置中选择服务并保存该服务的 API Key。火山引擎使用新版豆包语音控制台的
API Key，资源 ID 固定为 `volc.seedasr.sauc.duration`；Deepgram 使用自己的 API Key。
未聚焦且完全空白的输入框提示“按住说话”，长按时收起键盘并显示录音气泡、左侧取消与右侧发送。
默认松手只转文字填入草稿；滑到取消或发送按钮再松手才执行对应动作，移回中间恢复填入草稿。
聚焦或已有任何文本（包括空格）时保持系统编辑菜单。转写期间仍可点取消，取消会丢弃迟到结果。
聊天和全局助手遵循相同规则，并保留 Tapilot 配色；可选 WebView 验证入口用于旧交互回归。
首次录音需授予麦克风权限。音频通过所选服务的 WebSocket API 流式发送，不保存录音文件；
两家服务的密钥分别由 Android Keystore 加密保存，不使用系统语音识别。

界面语言默认跟随 Android 系统，也可从 App 统一风格的自绘弹层中固定为简体中文或
English。语言选择会同时作用于原生设置页、原生辅助功能引导和任务状态通知，
并在应用重启后保留。自然语言输入框默认保持紧凑，聚焦后会自动增高。YAML 和 Test
Runner 相关能力只有开启“高级模式”后才出现，YAML 编辑框仍可展开为全屏。
辅助服务的连接和断开会实时通知设置页；系统重启服务或服务稍晚完成连接时，无需重新进入
页面，顶部会先显示正在重连，然后自动切换为绿色状态点。升级时必须保持 application ID、
签名密钥和辅助服务组件名不变。本地测试不要执行
`adb shell am force-stop lifeos.md.tapilot`：Android 会把 force-stop 视为明确停用该包的辅助服务。
应使用 `adb install -r` 覆盖安装后正常启动。

辅助服务的生命周期完全由 Android 管理。只要授权仍处于开启状态，系统会在需要时重新
绑定服务；应用不会为了“假保活”常驻一个无意义的前台服务。对于后台限制较严格的手机，
请将 Tapilot 的电池用量设为“不受限制”，加入厂商提供的后台白名单，并避免在系统管家中
手动清理。用户主动关闭、强制停止或厂商系统直接撤销的权限，应用无法静默恢复。

可选的全局悬浮球通过无障碍 Overlay 实现。只有 Tapilot 不在前台且当前没有任务运行时才会
显示；单击会回到自然语言输入框，拖动后会保存新的屏幕位置。

## 执行状态

任务执行时，应用通过不拦截触摸的无障碍 Overlay 在完整显示区域四周显示动态光效，
包含状态栏、导航栏和刘海/挖孔区域，不再显示右侧悬浮按钮。每次截取供视觉模型分析的
屏幕前，原生层会先隐藏光效，等待窗口刷新后再截图，因此光效不会进入 Midscene 的模型
输入。开始、成功、失败和停止状态也会通过
Toast、无障碍播报和前台服务通知提示。前台服务会在任务运行期间提高进程优先级，降低
切换到目标应用后任务进程被系统回收的概率。

## 调试报告

输入框右下角提供发送按钮，键盘“发送”开始执行，Shift+Enter 换行。卡片标题旁的图标仅作装饰。
回放页菜单支持“分享 HTML”“保存 HTML 文件到…”和保存到下载目录。
Export Video 会将 WebM 视频保存到系统视频库的 **Movies/Tapilot** 目录。
部分相册不显示 WebM 视频，仍可在文件管理器中找到文件。
失败任务即使只有 Test Runner 报告，也会保留查看入口。
关闭指定应用卡片时，助手优先调用系统最近任务动作打开后台列表；划掉卡片不等于系统强行停止应用。

每次由 Midscene runtime 完整结束的任务，无论成功还是失败，结束后都会生成
一份内嵌截图的单文件 HTML 报告。结果卡片中的 **查看报告** 可直接打开最新报告，
用于回看模型规划、动作轨迹和错误上下文。报告由设备内 Node Test Runner 使用 Midscene
标准报告器直接写到本次运行目录，Android 再通过临时文件和原子重命名导入报告仓库，
不再把大段 HTML 传回 WebView；其他应用不能直接访问。
报告头部的 Midscene 图片由 APK 内置资源提供，即使设备无法访问图片 CDN 也不会显示破图。

报告可能包含任务指令、模型输出和操作过程中的屏幕截图。清除应用数据或卸载应用会一并
删除报告。结果卡片会打开最近一次报告，“历史”中可以查看更早的任务及其报告。删除历史
记录前会二次确认，并同时删除关联的 HTML 文件。私有目录最多保留 20 份、合计 200 MB
报告，并始终保留最新一份。超出限制的旧报告会自动删除，未完成的报告会中止并清理。

## 动作能力

运行时复用 Midscene JS SDK 的标准移动端 Action，并通过原生 Bridge 执行 `Tap`、
`DoubleClick`、`LongPress`、`DragAndDrop`、`Swipe`、`Pinch`、`Scroll`、
`PullGesture`、`Input`、`ClearInput`、`KeyboardPress`、`CursorMove`、系统返回/主页/
最近任务，以及 `Launch`。普通 Android 应用无法获得 ADB 的 shell 和强停其他应用权限，
因此无 Shizuku、ADB 或 root 模式下不提供 `RunAdbShell` 和 `Terminate`。

每个模型预设分别维护自己的配置。API Key 会先使用 Android Keystore 密钥加密，再写入
应用私有配置；其他非敏感模型字段和会话历史原子保存到应用私有文件中。任务完成或应用重启
不会清空配置。“恢复默认”会清除当前预设的 API Key，清除应用数据或卸载应用则会删除
全部预设。模型请求由 Android 原生层发送，对话使用非流式响应。任务中止会断开对应网络连接，并阻止迟到结果调用工具。增量原生流式输出属于后续优化项。

手机操作和 YAML 都复用设备内 Node.js 中现有的 Midscene Agent、Test Runner 与标准报告
能力。对话 Agent 负责上下文、工具调用和最终回复；Android 侧提供模型传输、device
interface 和进程/RPC 生命周期。
YAML 支持 Test Runner 通用节点，以及 `launch`、`home`、`back`、`recentApps` 这些 Android
直连节点；受应用沙箱限制，仍不提供 shell 和强停任意应用。

文本输入依赖目标控件提供可编辑无障碍节点；Canvas、游戏或自绘输入控件可能拒绝输入。
这是实验性版本，需要 Android 11 或更高版本。

## 内部开发

供已获授权的开发者使用。开发环境、测试要求与提交流程请见 [CONTRIBUTING.md](CONTRIBUTING.md)，
参与即表示同意遵守[行为准则](CODE_OF_CONDUCT.md)。安全漏洞请按
[SECURITY.md](SECURITY.md) 私下报告，不要提交公开 issue。

## 许可证

Tapilot 为非开源软件，保留所有权利。参见 [LICENSE](LICENSE)。
第三方组件遵循各自的许可证。

## Android Beta 下载发布

公开下载使用固定文件 https://www.feishu.cn/file/Ig0KbUKlJoADozxRbOocm5iqnXf，文件名包含版本、渠道与架构，例如 `Tapilot-0.1.3-beta-arm64.apk`；后续版本原位替换文件，并使用新 APK 的实际文件名，不新增发布说明文档作为下载页。studio 的 `scripts/lark-drive-release-downloads.mjs` 已登记 `tapilot-android-beta`。匿名下载校验后再更新官网清单：`fileUrl` 指向固定文件；旧 `downloadUrl` 仅供旧客户端兼容，旧文档只保留 APK 附件，不写构建报告或说明。

### 使用 ChatGPT 套餐

在「设置 → 模型接入方式」选择 **ChatGPT (SIWC)**，点击 **Continue with ChatGPT**，
在系统浏览器完成授权，选择 GPT-6 或支持视觉定位的 GPT-5 模型。
对话、任务规划和 Midscene 手机操作统一使用所选模型，无需另填模型 API Key。
选择 **API Key** 可切回普通模型 API。两种方式的配置分别保留，实际请求只使用当前选中的方式。
SIWC 模式下，语音润色也复用当前 ChatGPT 账号和模型，无需另填润色 Key。
普通 API 模式保留独立的润色地址、模型和加密密钥；可在「语音润色 → 试润色」中验证。
润色失败会保留转写原文。语音识别仍使用独立服务商：SIWC 目前不支持音频输入或转写 API，
详见 [预览版限制](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations)。

支持切换已保存账号、刷新授权和退出登录。令牌由 Android Keystore 加密保存，
每次手机模型请求前，从原生凭据存储获取有效 token；遇到 401 时续期并只重试当前模型请求，
不会重放手机动作。refresh token 只留在原生凭据存储中。手机任务不设固定总时限，
网络请求保留超时检测，用户可随时打断任务。
退出登录会清除本机令牌并尝试撤销远端授权；也可从
设置中的链接进入 ChatGPT 管理用量和授权。

此功能使用 SIWC 的 OAuth 登录和 Responses API，实际可用模型和套餐共享权限
取决于 OpenAI 账号。详见 [SIWC 官方文档](https://developers.openai.com/siwc/token-sharing-open-source)。

登录过程也会加密保存；App 重启后可在 15 分钟内恢复原回调端口，返回浏览器刷新即可继续。超过时限需重新发起登录。
## 构建与发布校验

`pnpm check`（或 `pnpm test`）统一检查类型、旧 Web 回归、设备适配器、Node Agent、报告脚本和 Kotlin，并构建及核验不可调试的 Beta APK。检测到本机 dev-gate 时自动排队执行。

`pnpm build:android` 输出 Beta，沿用当前安装证书以支持覆盖升级；该证书不是正式生产签名。`pnpm build:release` 必须提供独立的 `TAPILOT_SIGNING_*` 配置和预期证书指纹，缺失时拒绝发布。配置说明见 [CONTRIBUTING.md](CONTRIBUTING.md)。

Gradle 打包前会核对 Node 源码指纹与 ZIP 摘要；修改源码后必须重新生成资源。公开 Intent 只能打开界面或传入分享内容，不能启动录音。草稿独立保存，任务持久化由专用线程完成，后续外部操作等待保存结果。

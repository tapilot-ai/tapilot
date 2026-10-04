# 语音交互对齐（2026-10-04）

按用户要求，Tapilot 的原生聊天、全局助手面板、WebView 文字及语音入口对齐 Aino / Vibelet / Remosidian 的语音交互规则。保留 Tapilot 品牌配色，独立实现，没有复制 LifeOS 私有源码。本次提交包含实现及验证入口，未发布新版本。

- 未聚焦且原始内容长度为零时提示“按住说话”，允许长按；轻点松手才聚焦，避免先弹键盘和长按放大镜。
- 已聚焦或含任何文本（包括空格、换行、制表符）时保持系统选择、剪切和粘贴菜单。
- 录音显示全屏遮罩、中间气泡、松手提示、左侧取消、右侧发送和底部弧面；原生入口共用同一浮层。语音服务提供实时文本时气泡展示文字，否则展示波形。
- 默认松手转文字填入草稿；松手前滑到按钮实际区域才取消或发送。移回中间恢复默认回填，无关上方区域不触发取消。
- 转写期间浮层保留，取消可点，发送禁用。取消立即关闭浮层，丢弃迟到结果；准备阶段松手、触摸中断、失去窗口焦点和切后台都清理状态。
- WebView 只在允许语音长按或正在持有语音手势时禁止浏览器接管滚动；普通编辑恢复正常触摸行为。

## 实现及验证入口

原生输入及按钮命中规则分别在 `HoldToRecordEditor.kt`、`RecordingGesture.kt`；共用浮层在 `RecordingFeedback.kt`。WebView 的输入规则、会话状态及浮层拆到 `hold-to-speak.ts`、`voice-session.ts`、`voice-input.ts`、`voice-feedback.ts` 和 `voice-input.css`，原大型入口文件净缩减。

可选验证包使用不同应用 ID `lifeos.md.tapilot.speechverify`，以生产输入组件、控制器与浮层运行，只替换识别服务和消息接收端；不读取密钥、不使用麦克风、不访问网络、不发送真实 AI 请求。验证服务刻意在取消后仍返回结果，以验证丢弃逻辑。独立导出的验证 Activity 仅在显式指定 `-PspeechVerification` 的 debug 构建里出现，普通调试和发布包不包含它们。

```bash
TAPILOT_SPEECH_VERIFICATION=1 pnpm run build
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
  ./gradlew :app:testDebugUnitTest :app:assembleDebug -PspeechVerification --max-workers=2 --no-parallel --no-daemon
# 构建前按本机约定通过 dev-gate heavy；恢复普通产物时不传这两个验证开关。
```

Android 原生 85 项单测、WebView 75 项测试、TypeScript 类型检查、品牌资源检查、网页及普通 debug APK 构建通过。普通产物已重新构建，确认没有验证 Activity 和网页验证入口。

## 实际运行证据

上一轮小米 M2006J10C 真机已验证原生聊天空框提示、聚焦长按菜单及纯空格的系统编辑菜单，截图在 `artifacts/speech-alignment-20261004/`。本轮新应用 ID 的独立测试包被小米拒绝安装，返回 `INSTALL_FAILED_USER_RESTRICTED`；没有修改手机安装限制、助手、无障碍或网络配置。

本轮使用已有备用 Pixel 7 Android 35 模拟器，安装独立测试包验证生产交互。原生聊天和全局助手均确认普通松手回填且发送次数为零、滑到发送后只发送一次、滑到取消后草稿为空、滑回中间回填、转写中取消丢弃迟到结果、准备阶段松手完成转写、切后台取消，以及聚焦长按仍处于编辑状态。WebView 通过专用调试通道驱动真实触摸事件验证同样路径及纯空格编辑。

证据目录：`/Volumes/T5/personal/tapilot/artifacts/speech-complete-20261004/`。关键截图：`chat-recording.png`、`chat-transcribing.png`、`chat-insert-result.png`、`panel-recording.png`、`panel-late-result-discarded.png`、`web-send-selected.png`、`web-cancel-selected.png`。断言记录为 `results.json`（原生两个入口）及 `web-results.json`。测试包用完卸载，模拟器关机、转发移除、设备锁和 runtime 名额释放。

实际火山语音服务上一轮仍返回 HTTP 403（未获 Seed ASR 2.0 资源权限）。本轮验证的是实际应用中的交互状态及结果处理，使用合成转写数据；不代表真实语音服务、真实录音识别或 AI 执行成功。

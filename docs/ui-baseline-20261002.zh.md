# 聊天界面迁移基准（2026-10-02）

## 原版来源

使用迁移到 Kotlin 聊天界面前的提交 `edc1ac9`。已完整恢复 `app/`、`runtime/`、Gradle 配置和 README/CONTRIBUTING，核对这些路径与原提交没有差异。聊天页重新使用原版 HTML/CSS/TypeScript，原版 MainActivity 承载 WebView。

原生迁移代码保存在 `backup/native-chat-before-baseline-20261002`，分支指向 `f4fa6f6`。原生测试报告保留为历史记录。应用数据未清空，沿用原有配置和历史。

## 真机基准

原版前端构建与 Debug APK 构建通过 dev-gate 执行成功，覆盖安装到小米 M2006J10C / MIUI 14。

APK SHA-256：`ce767c7c7fd4d082938c6f45c2662fcd237c6c488074c044bf0c353adeef055c`。

通过一次连续触摸录音手势截取：长按文本框录音、上滑后显示松开取消、松手后结束录音并恢复输入框。未发送消息。截图来自原版界面，不是模拟原版的 Kotlin 组件。

- [聊天首页](/tmp/tapilot-baseline-20261002/chat.png)
- [正常录音](/tmp/tapilot-baseline-20261002/voice-recording.png)
- [松开取消](/tmp/tapilot-baseline-20261002/voice-cancel.png)

截图保存在本机临时目录，清理后链接会失效。

## 后续原生迁移原则

更新：用户随后提供了微信录音截图 `1790940116854_6ef0e928251d5ed1.png`，明确录音浮层应采用绿色波形气泡、全屏暗色遮罩、两侧滑动区域和底部弧面。上面的旧版蓝紫录音卡片仅用于历史追溯，不再作为录音浮层的目标。最新实现和真机结果见 [微信语音界面验证](wechat-voice-e2e-20261002.zh.md)。

以 `edc1ac9` 的 `runtime/index.html`、`runtime/style.css`、`runtime/index.ts` 及以上真机截图共同作为基准。对照完整界面，包括标题栏、提示卡片、消息布局、输入框、按钮排列、语音模式、录音浮层、附件和设置；不能只提取部分 CSS 后重新设计。

录音浮层以用户新提供的微信截图为准。上滑只进入取消状态，松手才取消。后续迁移完成后，在同一设备和对应状态下截取新图，对照参考图记录差异。

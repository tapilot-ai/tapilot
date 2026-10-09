# 长按语音高亮透明度

聊天与全局助手共用的原生 `RecordingFeedback` 中，气泡、气泡尾部、发送背景及取消选中背景统一采用现有主题色 80% 的不透明度。文字、波形与选中边框保持原样，交互逻辑未调整。

`dev-gate` 内 `./gradlew assembleDebug -PspeechVerification --max-workers=2 --no-parallel --no-daemon` 构建通过。Android 35 ARM64 备用模拟器安装独立验证包后，实际长按聊天输入框并人工核对背景透明效果及文字可读性。使用合成 ASR 和确定的润色响应，不测试真实麦克风或识别服务。截图：`docs/visual-baseline/20261009-speech-opacity/tapilot-highlight.png`。

与 Aino Mobile、Vibelet、Remosidian 的共享语音高亮保持同样透明比例；独立 Kotlin 实现，不修改产品主题色。本轮未提交、推送或发布。

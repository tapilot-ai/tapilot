# 松手后显示完整语音尾字

聊天输入、全局助手及兼容语音桥共用 `SpeechPolishSession`。此前最终润色完成后，显示回调和完成回调连续执行，宿主立即隐藏气泡，最终文字来不及画出来；排队等待润色时，也没有主动显示最终转写中新补齐的尾字。

现在结束录音时立即更新完整转写预览。最终润色完成后，气泡显示完整结果并停留 650 ms，再回填或执行原来的发送分支。取消会撤销延迟完成任务，迟到结果不能回填或发送。默认松手仍只回填输入框。此处为独立 Kotlin 实现，与 Aino Mobile、Vibelet、Remosidian 的显示时序一致。

## 验证

通过 `dev-gate` 执行 `./gradlew testDebugUnitTest assembleDebug -PspeechVerification --max-workers=2 --no-parallel --no-daemon`。126 项测试通过，1 项需要真实服务配置的测试跳过，无失败。补充了最终转写在旧预览排队期间立即显示，以及最终气泡停留期间取消不会提交的回归断言。

Android 35 ARM64 备用模拟器中安装独立验证包，实际长按原生聊天与全局助手输入框。合成 ASR 在松手后补齐“保留最后两个字”，润色服务人为延迟 2500 ms。两个入口均先显示完整尾字，再显示“整理后的语音，保留最后两个字。”，最后回填同样的完整文本，默认没有发送。聊天松手后立即点击取消，等待 8500 ms 后仍为空、发送次数为 0。

截图已人工核对，UI XML 另有回填、取消及未发送断言。此验证使用实际生产原生 UI、润色客户端和会话逻辑，识别与服务响应均为合成数据；没有验证真实麦克风和 ASR 服务准确率。未操作用户真实数据。

证据位于 `docs/visual-baseline/20261009-speech-final-tail/`：`chat-final.png`、`panel-final.png` 为最终文字气泡，`chat-inserted.png`、`panel-inserted.png` 为回填，`chat-cancelled.png` 为取消后。`manifest.json` 记录验证包及截图 SHA-256。

验证包已卸载，本次模拟器已关闭。未提交、推送或发布。

# Remosidian AI 助手输入区对齐

## 参考基准

- studio/apps/remosidian-mobile/components/AssistantConversation.tsx：使用 composerLayout="inline"。
- studio/packages/mobile-chat-ui/src/AgentChatComposerSurface.tsx：操作嵌入输入容器两侧。
- studio/packages/mobile-chat-ui/src/AgentChatComposerInput.tsx 与 AgentChatView.styles.ts：圆角外壳、12px 外侧间距、8px 控件间距、透明编辑区、15px 字号和多行输入。

Tapilot 采用同样的单行内嵌结构，增加浅色圆角细边框容器，收紧编辑区和图标尺寸。按用户上一条要求，按钮无底色，不恢复额外语音切换按钮；附件加号、长按录音、有内容时显示发送箭头、运行时停止入口保留。

## 验证

Gradle assembleDebug 成功。保留数据覆盖安装到 Redmi K30 Ultra（s4ey59ytbitot4yp），使用设备会话锁进行截图观察和 ADB 输入操作。检查空草稿、键盘弹起时的输入布局、发送箭头显隐及清空草稿后恢复长按提示。未发送模型消息或执行手机任务。

截图和产物摘要保存在 artifacts/0.1.1/remosidian-composer/。

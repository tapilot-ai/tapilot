# 语音松手尾块收尾修复

2026-10-05。

原先 finish 先设置 finishing，AudioRecord.read 随后返回的最后一块音频会被丢弃；连接准备完成也可能在采集尚未停止时发出结束帧。

本轮通过独立的 SpeechCaptureBoundary 区分请求结束、采集排空和已发送结束帧。松手后保留 320 毫秒尾音窗口，继续接收音频；停止 AudioRecord 并等待线程退出、排空待发送音频，再发送协议结束帧。取消直接停止，不等待尾音窗口。火山必须收到最终帧才能成功，不再把提前断线前的文字当成完整结果；Deepgram 正常关闭只回填最终段落。

101 项原生单测通过，正常 APK 与独立合成验证 APK 均构建成功。模拟器验证使用生产边界类和输入框，注入松手后 256 毫秒到达的尾块；聊天输入框与悬浮面板验证完整文字回填、不自动发送、取消后迟到结果无效。证据：`artifacts/speech-tail-20261005/results.json`；关键截图：`artifacts/speech-tail-20261005/chat-complete-tail.png`、`artifacts/speech-tail-20261005/panel-complete-tail.png`。合成识别逻辑只进入隔离验证 APK，正常 APK 不包含验证活动。

尚未完成真人麦克风与真实识别服务验收。320 毫秒保护音频缓冲，不保证模型对所有尾字的识别准确率。本轮未提交、推送或发布。

## 同日补充：更快长按

聊天输入框与悬浮面板的共用原生编辑器 HoldToRecordEditor 从 480 毫秒改为 280 毫秒触发。已有文字、空格和聚焦时继续普通编辑；短按仍进入键盘。引擎准备时长不在这次阈值修改范围。

101 项原生测试通过，正常 APK 和隔离验证 APK 构建通过。模拟器使用 3000 毫秒延迟启动的合成识别，验证两处语音气泡在识别准备完成前出现，以及短按不会进入语音界面。证据：`artifacts/speech-fast-hold-20261005/results.json`；截图 `chat-fast-feedback.png`、`panel-fast-feedback.png`、`chat-short-tap-keyboard.png`、`panel-short-tap-keyboard.png`。注入手势及截图包含 ADB 传输开销，不能用该耗时作为精确 UI 性能测量。

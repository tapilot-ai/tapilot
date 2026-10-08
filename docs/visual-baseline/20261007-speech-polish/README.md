# 语音润色验收截图

本目录包含独立 Android 35 ARM64 模拟器与 Redmi K30 Ultra 真机的截图，操作工具为 Midscene。

- `chat-polished.png`：聊天润色回填，保留尾字，发送次数为 0。
- `chat-fallback.png`：API 返回 503，回填原始转写。
- `panel-polished.png`：全局助手润色回填，未自动发送。
- `chat-cancelled.png`：取消并等待迟到的转写回调后，输入框仍为空，发送次数为 0。
- `settings-saved.png`：正式 APK 的设置界面，合成测试配置自动保存，密钥以圆点显示。
- `settings-invalid-disabled.png`：无效地址编辑状态下关闭开关，持久配置确认关闭生效。
- `settings-check.json`：设置保存、重启保留、加密存储和关闭核对结果，仅含合成测试值。
- `device-settings.png`：真机正式 APK 新增语音润色设置。
- `device-configured.png`：真机临时测试润色 API 已自动保存，密钥以圆点显示。
- `device-voice-result.png`：真机实际尝试录音时，火山引擎返回资源权限 HTTP 403。不是成功润色截图。
- `device-check.json`：真机安装、设置、转写阻塞及历史未变化的回执。
- `live-service.json`：真实 Kotlin 润色 API 返回 HTTP 200、1904 ms，默认提示词产生编号列表并保留否定。
- `cancellation.json`：最后合法预览请求与取消时间对照。
- `manifest.json`：源码基准、测试数、APK 和截图 SHA-256。主程序与验证包分开标记。

聊天及全局助手截图来自独立验证包，使用合成 ASR 和确定的 API 响应，运行生产润色逻辑与原生 UI。模拟器设置截图来自补充 Qwen 兼容处理之前的正式 APK；真机配置和转写失败截图来自最终正式 APK。各截图对应的 APK 哈希在 manifest 中分别标记。正式 APK 不包含验证入口或 API 拦截器。

真实润色服务验证已通过，真机已安装并核验设置界面；真机录音到回填的完整链路未通过，卡在火山 Key 的 Seed ASR 2.0 资源权限（HTTP 403）。不能把模拟器合成转写或本机 HTTP 成功作为真机完整链路通过。

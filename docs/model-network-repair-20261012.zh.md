# 模型连接断流修复 · 2026-10-12

## 问题与修改

用户截图中的 `unexpected end of stream on com.android.okhttp.Address` 来自 Android 系统 HttpURLConnection 的模型请求。失败发生在任务推进时；不能仅替换报错文案或重新执行整个任务。

`ModelTransport` 改用已有 OkHttp 4.12，禁用重定向和隐式重试，保留 `Connection: close` 避免复用闲置 HTTP/1 连接。对于 EOF、连接断开和响应截断，最多重连一次，只重发当前模型 completion，不重放已执行的手机工具。两次尝试共用 120 秒期限；鉴权等 HTTP 错误、读取超时和取消不触发重连。取消会直接终止活动 Call。持续失败时显示中文网络提示，并保留异常 cause。

## 验证

- 原生测试 162 项：161 通过，1 项外部服务测试跳过，0 失败。
- 新增真实本地 TCP 服务器响应截断测试，确认第二次请求的 payload 相同且能读取完整响应；另覆盖取消、连续断流上限、超时和 HTTP 403 不重试。
- 普通 debug APK 构建成功，安装到 Redmi K30 Ultra (`s4ey59ytbitot4yp`)；模型和火山转写配置保留。
- Midscene 只负责创建新会话和发送验收指令。普通回复返回“连接正常”；手机任务由 Tapilot 自己打开计算器并确认界面，随后完成最终模型回复，1 次手机操作、2 轮模型对话。ADB 前台 Activity 与截图同时确认真实计算器。
- 安装包 SHA-256 与本地构建一致：`371671625814998dfc8332d63f941a580b1342bf509f8bb100e8f6162121bdfc`。

本次是真机安装的 0.1.6 修复候选，未更新公开飞书 APK 或官网版本。真机验收未人为中断服务商网络；断流恢复由本地故障模拟验证。历史失败消息保留，不会因升级自动改写。

证据：`artifacts/20261012-model-network/receipt.json`、`calculator.png`、`chat.png` 和该目录的 Midscene 报告。源码基准：`21270a76130de3689bb973e7fb1065a3c8673e8a`。

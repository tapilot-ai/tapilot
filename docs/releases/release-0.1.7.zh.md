# Tapilot 0.1.7 Beta 发布验收

应用源码提交 b7efe55，当前 main。包含全局悬浮语音、实时文字、松手发送和滑入取消区松手取消、Vibelet 样式，以及模型连接中断恢复。公开发行说明见 release-0.1.7.en.md。

APK 为 Tapilot-0.1.7-beta-arm64.apk，versionCode=8，Android 11+ / ARM64，沿用既有开发签名证书。162 项原生测试中 161 通过、1 外部服务测试跳过。已安装到 Redmi K30 Ultra，安装包 SHA-256 与发布文件一致；发布包悬浮长按启动麦克风、取消后停止服务且不创建任务的冒烟通过。已通过正常系统设置恢复覆盖安装后关闭的 Tapilot 无障碍服务。

飞书固定文件更新到 0.1.7，公开阅读下载权限保留。Ego Lite 未登录页面实际下载通过，文件名、104049216 字节大小和 SHA-256 与发布包一致。未新建下载说明文档。更新清单直接指向 APK 文件页。

官网三语版本信息和更新清单在 studio 单独提交 3f22d8de0。本地构建、产物检查、artifact-bound attestation 和推送前验证通过；Cloudflare 自动部署已触发，线上状态在发布完成时补充。

发布证据：artifacts/20261013-release-0.1.7/。未修改 Mac 网络配置；Android 设备锁已释放。

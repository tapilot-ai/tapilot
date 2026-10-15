# Tapilot 0.1.6 Beta 发布验收

- 当前分支 main，应用源码提交 0d6f732。已包含全部语音对答、背景服务、普通回答朗读和输入框居中改动。
- Gradle 原生构建通过，157 项单测通过，1 项外部润色模型测试跳过。火山转写、播报与计算器前台两轮上下文对答的完整证据见 voice-conversation-20261012.zh.md；发布包另完成真机安装和火山播报冒烟。
- 包名 Tapilot-0.1.6-beta-arm64.apk，versionCode=7，Android 11+ / ARM64 / Beta，沿用既有调试签名证书。API 凭据未打入安装包，真机既有配置保留。
- SHA-256：f0fe6d67cd4a541e110905456d2358d7c30870a9dcf0a0e1ccd601466489ab88，103990500 字节。本机包、飞书匿名下载及真机已安装包一致。
- 飞书固定文件已更新版本名称与内容，公开下载通过：https://www.feishu.cn/file/Ig0KbUKlJoADozxRbOocm5iqnXf 。下载页为 APK 文件，未新增说明文档。
- 官网三语首页、语音指南、隐私说明与更新清单均已更新并推送。构建和 artifact-bound attestation 通过，Cloudflare 部署 898b955b-8c6e-450d-9d76-a74f1b07e9b3 最近核验停留在 queued，线上清单最后核验仍为 0.1.5；已请求授权手动部署，收到答复前保持 Git 自动部署。其他 Studio 项目的未提交工作未纳入本次提交。
- Android 设备锁已释放；本次未修改 Mac 网络，浏览器验证完成后关闭任务空间。

产物与回执：artifacts/20261012-release-0.1.6/。

官网三语候选页面与语音指南已在本地浏览器核验；候选截图为 artifacts/20261012-release-0.1.6/website-candidate-zh.png。预览服务与浏览器任务已关闭。

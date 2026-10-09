# 设置页检查更新

设置 → 关于 Tapilot → 检查更新。显示当前版本、正在检查、已是最新版本、发现新版本、失败重试状态；有更新时显示“下载新版”，打开版本清单指定的飞书下载文档。

## 发布清单

清单 URL：`https://tapilot.lifeos.md/releases/android/latest.json`。

唯一维护位置：`../studio/apps/tapilot-site/docs/public/releases/android/latest.json`。以 `versionCode` 比较，拒绝错误应用、未知格式和非 HTTPS 飞书文档下载地址。网络请求在后台执行，15 秒超时，最大 64 KiB，无 API Key、聊天或设备内容。

清单当前对应已发布的 0.1.1 / code 2 APK，SHA-256 与 `docs/release-20261008.json` 一致。本次开发包不是该已发布 APK，不替换或伪称旧分发产物含新功能。以后发版应递增 code，完成新 APK 上传与可下载性验证后，更新官网清单。

## 飞书公开下载

用户明确要求开放下载权限后，下载文档从组织内可读改为互联网获得链接的人可读，阅读者允许下载。文档 ID：`TiXgdjtJ5oYSEZxIVzncWI0Kn4g`。

APK 是 Docx 内嵌附件，尝试单独 patch file 权限返回 1063001；最终通过父文档的公开阅读与下载设置生效。没有将失败请求记录为成功。

Ego Lite 未登录页面显示“登录/注册”，可以阅读文档、打开附件预览并点击下载。实际下载完整 APK，大小 88508887 字节，SHA-256 `b0833fe2cd581ba2ac37765c1a2919a4953dc4b03716a8873928036b63e4622a`，与原发布记录完全一致。

## 验证

- 原生构建成功；更新清单测试 4 项、网络备用地址测试 3 项、执行模式回归 3 项，共 10 项全部通过。
- 官网独立 harness、静态清单兼容断言及 scoped pre-push 构建与类型检查通过。
- 官网源码提交 `c9cb4d856c34b0aab9f7782d0cc821e9f5289bc5` 已推送，由 Cloudflare Git 自动部署。
- 实测“发现新版”使用本地 code 1、版本名 `0.1.0-update-test` 的验证包，查询真实公开 code 2 清单；验证包不发布。完成后覆盖安装正常开发包 code 2，保留原应用数据。
- 真机已验证检查失败后的重试提示和备用下载跳转；手机浏览器未登录也能阅读文档。
- 已恢复含新功能的正常开发包 0.1.1 / code 2，确认历史聊天与 YOLO 配置保留，释放真机设备锁。
- Cloudflare 部署 `2b2e29e2-ee31-4b47-89e1-3dd6524972ae` 已成功，公开清单返回 HTTP 200、application/json，Cache-Control 为 no-cache, max-age=0, must-revalidate。
- Redmi K30 Ultra 真机联网验收完成：code 1 验证包显示“发现新版本 0.1.1”及“下载新版”；点击后跳转到正确的公开飞书文档；恢复正常 code 2 开发包后显示“已是最新版本”。历史聊天与 YOLO 配置保留，设备锁已释放。

## 网络补验

真机在主域名请求中出现 TLS 握手连接关闭（SSLHandshakeException / EOFException），没有放宽证书校验或修改设备、Mac 的代理网络设置。增加同一个 Cloudflare Pages Git 部署的备用清单地址 `https://tapilot-site.pages.dev/releases/android/latest.json`；只有传输 IOException 时才尝试备用地址，格式/下载目标校验失败不绕过。主地址请求上限 7 秒、备用 8 秒，总网络等待至多 15 秒。公开备用清单返回 200，最终开发包真机已显示“已是最新版本”。

## 验收截图

- 发现新版：`artifacts/0.1.1/app-update/available-update.png`（code 1 验证包）。
- 最新版本：`artifacts/0.1.1/app-update/current-version.png`（最终 code 2 开发包）。
- 下载跳转：`artifacts/0.1.1/app-update/update-download-opened.png`。
- 未登录下载：`artifacts/0.1.1/app-update/public-download.png`。
- 官网部署：`artifacts/0.1.1/app-update/deployment-success.png`。
- 联网回执和汇总：`artifacts/0.1.1/app-update/live-manifest.json`、`verification.json`。

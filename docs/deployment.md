# Android 部署流水线

流水线位于 `.github/workflows/release-check.yml`，在 GitHub Actions 中显示为
`Build signed deployment APK`。推送 `v*` tag（例如 `v0.1.9`）后自动运行，
也保留手动入口。它会执行检查、构建正式签名 APK，
校验版本、签名证书和内置运行时，再把 APK 与校验材料保存为 Actions artifact。
产物保留 14 天。

## 配置与运行

在仓库 Settings → Environments 中创建 `release` 环境，配置以下 Secrets。
建议将该环境限制到用于交付的 `v*` 标签，以及允许手动构建的受信任分支。

| Secret | 内容 |
| --- | --- |
| `TAPILOT_SIGNING_STORE_BASE64` | 正式签名 keystore 的 Base64 内容 |
| `TAPILOT_SIGNING_STORE_PASSWORD` | keystore 密码 |
| `TAPILOT_SIGNING_KEY_ALIAS` | 签名密钥别名 |
| `TAPILOT_SIGNING_KEY_PASSWORD` | 签名密钥密码 |
| `TAPILOT_SIGNING_CERT_SHA256` | 签名证书 SHA-256，64 位十六进制，不含冒号 |

在包含该工作流的提交上创建版本标签，并推送到 GitHub，即可自动触发：

```sh
git tag v0.1.9
git push origin v0.1.9
```

示例版本应替换为本次版本；只在本地创建 tag 不会触发远端流水线。
需要手动运行时，先将工作流文件合入默认分支，再打开
Actions → Build signed deployment APK → Run workflow，选择要构建的分支或标签。
源码中的 `package.json` 版本必须与
Android `versionName` 一致；APK 校验会检查两者。

流水线使用 Node 24、pnpm 12.4.1、Java 17 和 Android SDK 35。
同一仓库的签名构建串行执行，不主动取消正在运行的构建。
签名文件仅在构建步骤写入 runner 临时目录，并在步骤退出时删除。

## 产物与失败报告

成功后下载 `tapilot-signed-apk-<commit>-<attempt>`，解压后包含：

- `app-release.apk`：正式签名 APK。
- `SHA256SUMS`：APK 文件校验值。
- `build.json`：版本、源码提交、工作流运行 ID 和签名证书指纹。
- 许可证、隐私说明、致谢及 APK 中对应的第三方依赖清单。

在解压目录运行 `shasum -a 256 -c SHA256SUMS` 可再次核验 APK。
校验值对应 APK 文件内容，与签名证书指纹不同。
检查失败时，可从 `deployment-reports-<commit>-<attempt>` 查看已经生成的
测试、Android lint 和分发检查报告；失败步骤尚未生成的报告不会出现。

## 当前交付前提

仓库的第三方分发审查尚未完成，详见 [分发材料说明](../third-party/README.md)。
全量检查完成后，流水线会执行 `pnpm check:distribution`；目前预期会在这里失败，
并保存 `distribution-readiness.json`，不会继续生成可交付的签名产物。

现有 `pnpm release:materials` 只收集材料，其输出仍将
`redistributionApproved` 标为 `false`。完成审查后，还需要为干净的 CI 环境接入
经过审查的材料与审批证据，再运行流水线；不能仅为让流水线通过而改成 `true`。
最终 `pnpm build:release` 还会检查实际 APK 内的清单。

本流程交付到 GitHub Actions artifacts，不会创建 GitHub Release、更新飞书文件
或提交应用商店。对外发布需要另行指定渠道并完成相应配置；正式版和测试版的飞书
固定下载地址应分别核验，备用下载文件的版本与 SHA-256 应与主下载文件一致。

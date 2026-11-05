# Windows 接入修复与 CI 验证

本次修复面向 Windows x64 和 ARM64。公开版本仍为 0.6.19；CI 安装包是待发布候选，不代表已上线，也不改变官网的支持承诺。

## 最终结果

提交 `79ba4a20d84b3b6bc75ccd52bb3ba8c8fa057073` 的[六平台 CI](https://github.com/tapilot-ai/tapilot/actions/runs/42041054670) 全部通过。

| 平台 | 运行时测试 | 离线打包 | 四个客户端安装、更新、删除源后重启 |
| --- | --- | --- | --- |
| Windows x64 | 194 通过，0 失败 | 通过 | 通过 |
| Windows ARM64 | 194 通过，0 失败 | 通过 | 通过 |
| macOS x64、ARM64 | 各 193 通过，1 项 Windows 专用测试跳过 | 通过 | 通过 |
| Linux x64、ARM64 | 各 193 通过，1 项 Windows 专用测试跳过 | 通过 | 通过 |

[机器可读验收记录](2026-11-05-windows-fix.json) 包含各包名称、Node 版本、SHA-256、客户端结果和 job 链接。安装包及 SHA256SUMS 可从上述 Actions 运行的 `tapilot-client-setup-win32-x64`、`tapilot-client-setup-win32-arm64` artifacts 获取；它们有保留期限，不是公开稳定发布入口。

已从 GitHub 实际下载两个 Windows artifact，ZIP 完整性检查通过；每个平台的 6 个 SHA-256 条目全部一致，统一安装包哈希与 CI 验收记录一致。普通下载曾中断，分段重试后完成，未修改本机网络配置。

PR 的常规 verify、安全扫描和 Midscene E2E 检查通过。需要真机或在线 API 的任务按现有 CI 条件跳过，不计入本次验收。

## 修复内容

- 凭据、配对配置及截图使用 Windows ACL 保护，验证所有者和访问权限。遇到已有的宽权限凭据时拒绝读取，不通过跳过检查兼容 Windows。PowerShell 只调用系统 .NET ACL 接口，避免继承 PowerShell 7 的模块路径导致权限命令加载失败。
- 受管理运行时采用目录 junction 和 Node 文件硬链接，不依赖创建符号链接所需的管理员权限或开发者模式。更新前验证入口归属，替换失败时恢复旧入口；记录中的源文件丢失时保留现有入口。
- 修正 Windows 路径比较、文件 URL 导入，以及测试环境的 USERPROFILE 隔离。安装与诊断继续使用安装器返回的路径。
- 结束 Midscene 子进程时等待进程关闭，避免 Windows 仍占用工作目录。AI 测试也等待 fixture worker 完全退出后再清理。
- 配对地址识别支持 Windows 的 Ethernet、Wi-Fi 及本地化网卡名称，并排除常见虚拟接口。

## 验证边界

Windows 使用 GitHub 原生 x64、ARM64 runner，先断言进程架构，再做权限检查、独立 npm 消费者安装、依赖审计、运行时测试和 Sharp WASM 图像处理验证。只有全部通过，才从已校验的产物生成统一包。

安装生命周期在隔离 HOME、USERPROFILE、APPDATA、LOCALAPPDATA 和 PATH 下执行。豆包、WorkBuddy、DSH、Cherry 分别验证交互首次安装、更新、配置和稳定入口，再删除原安装源，验证已安装 CLI 与协议仍可启动。环境中不提供外部 Node。

这些结果不包含全新 Windows 普通用户、客户端 GUI 面板、真机配对或读屏验收。未进行 Cursor 原生测试。Windows 安装依赖支持 ACL、junction 和硬链接的本地文件系统；其他文件系统未单独验收。

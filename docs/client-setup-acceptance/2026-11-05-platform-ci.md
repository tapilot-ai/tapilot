# 桌面接入包跨平台 CI 验证

后续状态：Windows 已完成修复，六平台安装包 CI 全部通过，见 [Windows 修复验收](2026-11-05-windows-fix.md)。以下保留初次调查时的失败记录。

本次为 CI 兼容性调查，不发布新版本，不覆盖已公开的 0.6.19 macOS arm64 包，也不修改官网支持承诺。

## 环境与检查内容

使用 GitHub 原生托管 runner：macos-14（arm64）、macos-15-intel（x64）、ubuntu-24.04（x64）、ubuntu-24.04-arm、windows-2025（x64）、windows-11-arm。每个任务先断言 Node 的实际 platform 和 arch，防止以转译运行替代目标架构。

每个平台都执行独立 npm 消费者安装、依赖审计、190 项运行时测试，以及移除原生图像插件后的 WASM 编码／缩放／解码。只有该步骤全部通过，才构建带 Node 及许可证的统一包，并验证豆包、WorkBuddy、DSH、Cherry 的隔离 HOME 安装与更新、稳定入口、删除安装源后的重启。下载产物和验证报告保存在 Actions artifacts。

隔离环境不继承真实配对、账号或宿主配置，也没有外部 Node。Windows 同时隔离 USERPROFILE、APPDATA、LOCALAPPDATA 和临时目录；POSIX PATH 仅提供安装脚本所需的 dirname。客户端的原生应用没有被安装或操作，不能把配置和协议检查视为 GUI 验收。没有进行 Cursor 原生测试。

## 已发现并修复

- 打包器补充 Linux shell、Windows cmd 入口，跨平台读取 Node 许可证及导出 Skill ZIP。
- Windows npm 通过 Node 直接执行 npm-cli.js，避免把 cmd 文件交给 execFile。
- UI 构建与相关测试将文件 URL 正确转换为系统路径，避免 Windows `/D:/...` 无法解析。
- Windows 使用带 `.exe` 的固定 Node 入口；非 macOS 默认 Skill 导出目录不再采用 iCloud 路径。macOS 原有目录保留。
- Intel runner 首轮集成测试超过 120 秒，第二轮原限制下已通过。最后复核将测试并发限制为 2，并将大量文件复制的集成测试上限调整为 300 秒，记录首次安装、daemon handoff、更新阶段耗时；未跳过断言或测试。

## Windows 未通过的原因

修复打包入口后，Windows 才执行到完整运行时测试，进一步发现：

- 替换已有目录链接时发生 EPERM；部分链接目标的路径形式也与保存记录不同，触发“入口被修改”保护。
- 凭据目录和文件保护依赖 POSIX mode。Windows 的 ACL 尚未实现相应验证，因此报 unsafe_credential_directory。不能通过跳过权限检查来把 CI 改绿。
- 路径断言、绝对路径模块加载以及部分 MCP／AI 模拟任务仍有失败，需要区分测试假设和运行时缺陷继续处理。

Windows 的统一包及完整安装生命周期仍被运行时检查挡住，不能发布或标记可用。runner 权限也不能代表普通 Windows 用户无需管理员权限即可安装。

## 运行记录

- [第一轮 d21259a](https://github.com/tapilot-ai/tapilot/actions/runs/42020716840)：Linux 两种架构和 macOS arm64 通过；Windows 停在 UI 打包路径错误；Intel 集成测试超时。
- [第二轮 11506ab](https://github.com/tapilot-ai/tapilot/actions/runs/42021036491)：macOS 与 Linux 四个组合全部通过；Windows x64 为 174/190 通过，ARM64 为 173/190 通过，不能继续签发接入包。
- [最终复核 bd3bd5f](https://github.com/tapilot-ai/tapilot/actions/runs/42022410849)：macOS 与 Linux 四个组合均通过；Windows x64、ARM64 均为 173/190 通过，17 项失败。完整版本、哈希、客户端结果和 job 链接见 [平台 CI JSON](2026-11-05-platform-ci.json)。

CI 成功仅覆盖相应 runner、Node 版本和安装生命周期，不等于其他 Linux 发行版、全新系统用户、原生客户端面板或真机读屏验收。公开分发和官网状态更新是后续独立发布动作。

最终 Intel Mac 耗时记录：首次受管理安装约 31 秒，daemon handoff 累计约 39 秒，回滚及更新累计约 95 秒；190 项测试通过，统一接入包四个平台目标的安装／更新／移除源后重启也全部通过。

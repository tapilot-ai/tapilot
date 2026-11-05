# Tapilot 客户端接入新版宣传稿

状态：草稿，待本次版本发布完成后使用。未发布到社交平台。

## 中文长稿

### 把手机接进你正在用的 AI 客户端

Tapilot 新增了官网接入入口。打开官网，复制接入提示词，粘贴到你正在使用的 AI 客户端，让它一步步带你安装、配对手机，再读取一次当前屏幕。

遇到需要导入 Skill、启用连接器或确认权限的地方，客户端会告诉你当前这一步该怎么做。安装器只接入你选中的平台，保留已有配置和手机配对；安装目录由程序返回，不用填写开发者电脑上的路径。

手机画面优先留在当前会话里。DSH Web 使用原生侧栏；Cherry Studio 桌面 Work Agent、WorkBuddy 和豆包工作可以走内置浏览器侧栏。各客户端的工具和手动步骤不同，官网分别列出了实测结果和待验收项。Codex 保留原生插件接入路线。

第一次验证只读取屏幕，不点击、不输入，也不切换设备。你可以对照实时画面，确认客户端看到的就是当前手机。安装检查、宿主画面和手机读取会分别报告。

本次统一接入包面向 macOS Apple Silicon，自带 Node 和依赖。豆包工作采用个人用户可完成的接入方式，无需企业资质。Windows、Linux 和 Intel Mac 仍待验证；只有 MCP、不能执行本地命令的客户端，需要先走手动安装。

开始接入：https://tapilot.lifeos.md/zh/install

## 中文短稿

Tapilot 新增客户端接入入口：从官网复制一段提示词，粘贴到自己的 AI 客户端，让它带你完成安装、手机配对和首次屏幕读取。

手机画面优先放在会话面板或内置浏览器侧栏，第一次验证只读屏幕。需要导入 Skill 或确认权限时，按当前一步的提示继续。

统一接入包目前面向 macOS Apple Silicon。各客户端的接入方式、实测结果与手动步骤都在页面里：
https://tapilot.lifeos.md/zh/install

## English draft

### Bring your phone into your AI conversation

Tapilot now has a guided client setup page. Copy the prompt, paste it into your AI client, and let it walk you through installation, phone pairing, and a first screen read.

Setup targets only the client you choose and preserves existing configuration and pairing. When a Skill import, connector setting, or permission needs your attention, the agent gives you the next step.

Keep the phone visible in a native panel or built-in browser sidebar where supported. The first check is read-only: no taps, typing, settings changes, or device switching. Installation checks, host rendering, and phone reading are reported separately.

The unified setup archive is verified for macOS Apple Silicon. Client requirements and acceptance status are listed individually; Windows, Linux, and Intel Mac are not yet validated.

Get started: https://tapilot.lifeos.md/install

## 配图与事实核对（内部）

- 配图只用已审核的 Cherry 中英文原图，官网已展示。保留原图；英文会话截图的部分宿主界面仍为中文。
- 历史原生案例使用 Android 模拟器，不包装成本轮真实手机验收。
- WorkBuddy、豆包内置浏览器成功不等于 MCP 面板验收。豆包完整中文案例、VS Code 原生会话、DSH Go 和本轮 Codex 完整原生验收仍待完成。未做 Cursor 原生测试。
- 本稿不宣称所有宿主支持一键安装，也不宣称各平台拥有同一种面板能力。
- 中文校对：分别回读事实、措辞和节奏；保留平台名称、只读边界和架构限制。主线为“复制提示词后，由当前客户端引导接入”，未加入速度、成功率或兼容性承诺。

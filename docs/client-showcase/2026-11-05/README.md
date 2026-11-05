# 2026-11-05 客户端实测截图

本目录保存豆包工作、WorkBuddy、DSH Web、Cherry Studio 的 8 张原始截图，以及各自的验收状态。
手机为英文 Android 15 模拟器，Tapilot 版本为 0.6.18。English 案例指会话和手机语言，宿主界面可能仍为中文。

- [打开素材图库](index.html)：会话与侧栏的完整画面。
- [查看素材清单](showcase-manifest.json)：采集时间、SHA-256、接入方式和验收限制。
- [查看原生验收记录](../../../plugins/tapilot/NATIVE-ACCEPTANCE.md)。

| 客户端 | 中文 | English | 接入方式 |
| --- | --- | --- | --- |
| 豆包工作 | [恢复现场，部分验收](doubao-0618-recovered.png) | [已观察实际画面](doubao-en-native-browser-live.png) | 中文为 MCP App；英文为内置浏览器侧栏 |
| WorkBuddy | [已观察实际画面](workbuddy-zh-live.png) | [已观察实际画面](workbuddy-en-showcase.jpg) | 内置浏览器侧栏 |
| Cherry Studio | [已观察实际画面](cherry-zh-showcase.jpg) | [已观察实际画面](cherry-en-showcase.jpg) | 桌面 Work Agent 的内置浏览器侧栏；Skill + CLI，无 MCP |
| DSH Web | [已观察实际画面](dsh-zh-showcase.png) | [已观察实际画面](dsh-en-showcase.png) | 原生插件侧栏 |

豆包截图包含账号水印；WorkBuddy 中文截图包含连接详情和通知浮层。目前均为审核原图，未发布到官网。
WorkBuddy 浏览器侧栏不代表 MCP 路径通过验收；DSH Web 不代表 DSH Go 通过验收。
清单中的 local_report_filename 仅供在采集机器上查找报告；本目录未收录执行报告。

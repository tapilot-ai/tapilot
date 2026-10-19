# 当前发布前验收入口

本页区分源码工程检查、真机功能、性能和 APK 分发。历史结果不自动适用于当前修改后的版本。

## 本轮修复（2026-10-19）

- `finish_task(completed)` 必须有成功的手机核验；补充要求会使旧核验失效。普通问答使用独立的 `answer_question`，澄清使用 `waiting_user`。未给出明确终态的普通文本不能默认为完成；连续三次无效完成请求或无进展文本会失败结束。
- 电脑 benchmark 使用与 runtime 同源的结果解析，支持当前 `verifyPhone` 及旧 `aiQuery`；只读取最终尝试，不借用失败尝试的结果。
- Termux SQLite 固定为官方索引中的 3.54.1，SHA-256 为 `0856cac8cc87f6e112f2eef4fcac6ad5cb08209ecb7cc260f997874fc186a3c0`，同步更新 ELF 库名。`TERMUX_CACHE` 可指定空目录，验证无本地缓存的原生依赖下载。
- `TextInputController` 负责文本输入、光标编辑和输入连接/剪贴板回退；辅助功能服务继续负责截图及受控手势。agent 规则移至 `conversation-policy.mjs`，状态不变量仍由处理器检查。
- `pnpm check:distribution` 生成 `build/distribution-readiness.json`；清单缺失、任一待复核条目或未完成对应源码审查都会失败。此检查不授予许可，也不替代审查。

运行全量本地门禁：`pnpm check`；依赖审计：`pnpm check:security`。开发构建通过不表示 APK 可公开分发。

## 现有真机和性能证据

- [Redmi 两端对比](benchmark-redmi-20261017.zh.md)：旧 runtime 八轮通过 6/8；日历手机两轮失败。它是 same-agent harness 对比，不是 Codex UI 延迟。
- [40 次成功率验收](success-rate-20261018.zh.md)：当时版本通过 39/40，仅两个任务；不代表任意场景达到 95%。
- [后续扩展回归](success-expansion-20261019.zh.md)：最后一版 6/6，包含恢复后已知场景。世界时钟 41.842 秒、日历 56.964 秒、跨年 338.042 秒；不是本轮修复后的新样本。
- [19 条 E2E 的定义与历史结果](../../tests/midscene/README.md)：定义校验、真实执行、故障注入和物理音频输出是不同验收层级。未执行的用例不能记为成功。

本轮新样本和三条界面冒烟通过记录见 [发布前验收](release-validation-20261019.zh.md)。所有结果必须携带 runtime 指纹、APK 哈希和设备信息。旧实测不能用于宣称本轮回归通过或整体提速 50%。用户已暂停提速优化，本轮以正确性、可复测性和发布边界为目标。

## APK 公开分发

仍需逐项关闭 [第三方材料缺口](../../third-party/README.md)。`union` 发布提交中的完整许可证现已保留；其原始 npm 元数据未声明 SPDX，清单仍保留复核标记。Sharp WASM 精确工具链与替换/重建材料、Termux 对应配方与源码匹配、其余缺失许可证，不能凭材料收集命令成功自动放行。

不发布 APK、不更改仓库可见性、不触发 GitHub 工作流。正式签名、上架审核和第三方二进制分发是各自独立的发布决定。

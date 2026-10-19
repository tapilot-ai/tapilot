# 桌面拖拽分组回归（2026-10-19）

本轮修复通用手势与会话恢复，不在生产代码中识别特定应用、图标位置或桌面任务。

## 根因与修复

- 原 `DragAndDrop` 映射为普通滑动，没有按住阶段。现在下发单次连续手势：按住 800 ms，再移动 800 ms。
- Android 对静止且需要延续的 stroke 可能在 DOWN 后提前回调：后续移动立即开始，实际动作约 0.94 秒。`GestureHold` 使用单调时钟等待剩余按住时间，修复后同一拖拽实际约 1.67 秒，并成功创建文件夹。Android 的注入实现会省略不变的 MOVE，完成通知对应最后一个已调度事件，而不是独立的按住计时器：[AOSP MotionEventInjector](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/accessibility/java/com/android/server/accessibility/MotionEventInjector.java)。
- 按住与移动之间保留悬浮控件的输入透传租约；取消后只释放触点，不继续移动。
- 保留连续拖拽的重复手势防护；动作说明明确独立 LongPress 会松手，不能与下一次 Swipe 拼成拖拽。
- 打开悬浮对话停止任务后，新建或切换会话会清除面板恢复引用；任务快照也拒绝跨会话的恢复 ID。新增目标不会再继承旧任务目标。
- 真机 harness 临时注入视觉模型后恢复原模型参数、凭据及选择，并等待配置落盘后结束测试进程。测试凭据不写入报告。

## 实际结果

| 设备 | 任务与起点 | 结果 | 本次任务时间 | 失败子操作 |
| --- | --- | --- | --- | --- |
| OPPO Find N / PEUM00 / Android 14 | 先由手机端创建 X、微信的“社交”文件夹并打开 | 已看到两个应用 | 不作整体耗时统计 | 前期尝试失败，见边界 |
| OPPO Find N / PEUM00 | 从已有 Tapilot、Vibelet 文件夹继续，命名 Automation，加入 Android Use，并打开核验 | succeeded，三个应用均可见 | 194.098 秒 | 2，最终恢复成功 |
| 三星 SM-N9860 | 从两个独立图标开始，创建 AI 文件夹，归入 ChatGPT、Claude，命名并打开核验 | succeeded，两个应用均可见 | 104.654 秒 | 0 |

这些是不同任务，OPPO 还包含接续已有分组的恢复过程，不能据此比较设备速度或宣称整体提速。

OPPO 分组候选与最终部署 APK SHA-256：`0acb053da6473e1092969df3dd90b963010ba04e0551d93d0980fa84ac6590d0`，runtime `0.1.0-70865e2ebc47`。三星分组实测使用同一原生手势修复的较早候选：APK `a8d71ccfcc5d3934f8a119f8c89eed9bdb561df11ac2f0f46718590b286541a6`，runtime `0.1.0-d212b6315579`；随后覆盖安装最终 APK。最终候选的只读检查记录补充在下方。

本地原始证据位于忽略提交的 `artifacts/drag-x-validation/`：

- `oppo-native-folder-success.png`：社交文件夹。
- `oppo-automation-folder-success.png`、`oppo-grouping-performance/summary.json` 及其 HTML：OPPO 接续任务。
- `samsung-ai-folder-success.png`、`samsung-grouping-performance/summary.json` 及其 HTML：三星从零分组。

## 检查与边界

- Android 单测 279 项通过；适配器 17 项通过；runtime 106 项通过。`pnpm check:quick` 与 Android debug 构建通过。
- 新增回归覆盖提前/延后手势回调、重复连续拖拽及跨会话恢复引用。
- 默认轻量模型在“整理所有桌面页”中出现反复翻页；该大任务已停止，不能记为通过。功能验收使用用户提供的同一视觉模型文件，结束后恢复各手机原配置；没有恢复耗时优化。
- 三星旧模型配置曾产生与实际屏幕不符的核验描述；换用视觉模型后当前画面核验正常。不能把旧配置结果算作有效对照，也没有仅凭该现象断言截图传输丢失。
- instrumentation 结束会终止目标进程，OPPO 可能需要重新绑定已授权的辅助功能；这类测试环境恢复不等于生产服务发生了新崩溃。
- X 已通过匹配签名的完整 splits 升级到 12.34.0-prod.01，两机均已进入用户名输入页；没有提交账号密码或验证码，账号登录不计为已完成。

## 最终构建只读复核

三星安装最终 APK 后，runtime 已实际更新为 `0.1.0-70865e2ebc47`。手机端新任务“打开桌面的 AI 文件夹，读取里面的应用名称”通过，最终回复为 Claude、ChatGPT。原始报告保存在 `artifacts/drag-x-validation/samsung-final-read-performance/`。该复核验证升级后的执行和截图核验，不将早期候选的拖拽耗时冒充最终候选的新样本。

收尾检查：两机最终 APK 哈希一致、runtime 均为 `0.1.0-70865e2ebc47`；OPPO 恢复 ChatGPT 与 YOLO，三星恢复原 API 配置与关键操作确认；临时模型文件已删除，自定义模型恢复空白。两机已授权的 Tapilot 辅助功能重新绑定，崩溃服务集合为空。

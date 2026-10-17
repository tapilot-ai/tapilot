# 两个新任务的真机耗时对比

接手目标：在你这台电脑连接的真实安卓手机上，完成两个新任务的「电脑运行相同 agent」与「纯手机运行」耗时对比。只做验证，用户已暂停提速优化。不要恢复 50% 优化目标，也不要给生产代码增加针对计算器、时钟或日历的特殊逻辑。

仓库：https://github.com/quanru/tapilot ，分支 `main`。拉取最新提交后先检查工作区。遵守仓库和用户的 AGENTS.md；提交默认使用 `quanru <quanruzhuoxiu@gmail.com>`。本次不创建 PR、发布版本或触发 GitHub CI。

## 已完成与待完成

新增完整任务位于 `app/src/debug/assets/performance/full-tasks.json`：

- `read-world-clock`：打开时钟的世界时钟，读取可见城市及显示时间，不修改城市或闹钟。
- `locate-calendar-date`：在日历中定位 2026 年 11 月 15 日，读取选中日期和星期，不修改日程。

每个任务都有同名 JSON 执行夹具。`calculate-expression` 只是额外测试样本，生产代码不含 CalculatorInput 或计算器专用提示。

新任务已做应用打开和初始界面的人工截图检查，但**尚未完成 AI 执行和耗时对比**。先前 OPPO 的旧耗时不能充当这次的结果。当前手机的型号、序列号、网络地址、配对和登录状态都要重新检查，不能照搬原电脑。

## 准备

阅读 `CONTRIBUTING.md`、`docs/development/performance.md` 和 `plugins/android-use/README.md`。使用 Android 自动化技能，界面操作基于截图，不读取 UI XML 或辅助功能树来替代纯视觉方案。只操作明确选定的真机序列号；不能误用模拟器。

需要 Node **24.18.0**（与手机 runtime 一致）、pnpm **12.4.1**、JDK 17、Android SDK 35 / Build Tools 35.0.0、ADB、Python 3。配置本机 `JAVA_HOME`、`ANDROID_HOME` 和 PATH，不复制原电脑的绝对路径。

```sh
pnpm install --frozen-lockfile
npm ci --prefix plugins/android-use
npm ci --prefix tests/midscene
pnpm check
./gradlew :app:assembleDebug :app:prepareBenchmarkCodec --max-workers=2 --no-parallel --no-daemon
adb devices -l
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n lifeos.md.tapilot/.MainActivity
```

此 benchmark receiver 只在 **debug APK** 中存在。Beta APK 可做日常使用，但不能代替此处的 debug APK。另一台电脑的签名可能无法覆盖已有安装；遇到签名冲突先报告，不卸载、不清空用户数据。

在手机配置模型、开启设备控制辅助功能和无线控制。在新电脑重新配对并确认，使用新手机实际 Wi-Fi 地址。USB 只负责安装和采集；电脑测试通过已配对的 Tapilot Wi-Fi 通道执行。不要复制原电脑的配对凭据或 OAuth 文件。

两端用同一个经用户授权的 ChatGPT 账号，实际外层模型和视觉模型都必须是 `gpt-6-luna`；预检失败或不可用时报告阻塞，不悄悄换模型。电脑端使用 `ChatGPTAuth` 的授权流程，源码为 `plugins/android-use/server/midscene/chatgpt-auth.mjs`，或现有 Android Use 登录入口。`TAPILOT_BENCH_AUTH_DIR` 指向本机完成授权的目录；账号核对后设置 `TAPILOT_BENCH_ACCOUNT`。密钥、令牌只留本地，不打印或提交。

**设备适配边界**：当前执行夹具的包名是 OPPO 的 `com.coloros.alarmclock` / `com.coloros.calendar`，`scripts/lib/controlled-evidence.mjs` 当前严格验证截图为 **988×1972**。如果新手机不同，先截图检查、确认实际包名和尺寸，只在测试夹具与测试验证中适配，并让同一手机的两端使用完全相同的夹具和尺寸。不能简单删除最终验证，也不能修改生产 agent 来迎合任务。世界时钟夹具需要至少一个可见城市；如果为空，不擅自添加城市，先报告条件不满足。

## 执行顺序与命令

每个任务建议按 **电脑 → 手机 → 手机 → 电脑** 串行执行，至少每端两次。每次计时前在相应应用中恢复同一初始页面并截图确认：时钟从闹钟页开始；日历从当前日期页开始，目标日期不预先选中。关闭首次引导，重置操作不计入耗时。不要清除应用数据、修改日程或闹钟。测试时暂停其他设备操作者和插件实时预览，任何时刻只能有一个控制者。

电脑端完整任务（计入外层 agent、应用目录、视觉执行与最终独立查询）：

```sh
TAPILOT_BENCH_FIXTURE=read-world-clock \
TAPILOT_BENCH_AUTH_DIR=/absolute/path/to/local-authorized-auth \
TAPILOT_BENCH_ACCOUNT=matched-account \
TAPILOT_BENCH_HOST=PHONE_WIFI_IP \
node scripts/compare-full-conversation.mjs
```

手机端完整任务，标签每次唯一：

```sh
python3 scripts/collect-native-benchmark.py world-clock-phone-1 \
  --serial DEVICE_SERIAL --fixture read-world-clock
```

第二个任务把 `--fixture` / `TAPILOT_BENCH_FIXTURE` 改为 `locate-calendar-date`，并使用新的标签。手机 collector 默认是 `full`，不要用 execution-only 结果对比完整任务。手机 benchmark receiver 会停止本次音频播放，不需要改用户的永久语音偏好。

电脑端测的是 **same-agent harness**，不是 Codex / ChatGPT 客户端 UI 响应时间。如果用户还要客户端体验对比，单独测试并单独列出。

## 结果要求与异常处理

原始证据在 `artifacts/controlled-execution/`，保留本地，不提交可能含屏幕隐私或凭据的原始材料。逐样本核对实际任务、时间戳、runtime source fingerprint、实际模型、截图尺寸、最终独立视觉查询、任务状态。两端 source fingerprint 必须一致，采样期间不改 runtime 源码。

结果按任务分别列出：每次总耗时、中位数、手机相对电脑差值、外层模型耗时与次数、视觉模型 HTTP 耗时与次数、剩余执行耗时、动作数、重试数，以及截图独立读出的结果。记录机型、Android 版本、电量、供电、温度、网络路径和初始画面。用户允许低电量直接测试；不要仅因电量低而阻塞，但必须披露条件。锁屏需要用户在手机解锁，不猜密码。

观察超时、日志连接结束或宿主中断不代表手机任务已结束。先检查原任务进程、日志和最终画面，确认实际终态；不要自动重新执行或把失败耗时计入成功中位数。保留失败样本并解释。最终视觉查询失败则不算成功。

测试后添加脱敏 JSON 结果和简短说明到 `docs/development/`。如需适配测试代码，运行对应检查，确认不引入生产耦合。报告成功、失败、未测、阻塞分别是什么；少量样本不能宣称统计等价或已经提速 50%。用户希望先获得新任务的数据，不继续优化。

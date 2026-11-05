const messages = {
  "请确认手机已解锁、无线控制已开启且 Wi-Fi 可以互通。若切到后台后断线，请打开手机 Tapilot，检查后台运行和联网限制。恢复后会自动连接，不会重放操作。": "Unlock the phone, enable wireless control, and check Wi-Fi connectivity. If it disconnects in the background, open Tapilot on the phone and check background and network restrictions. Reconnection never replays actions.",
  "在侧栏打开": "Open in sidebar",
  "无法打开侧栏，请重试。": "Could not open the sidebar. Please try again.",
  "已连接 · 实时预览": "Connected · Live preview",
  "当前客户端支持实时预览。配对、切换设备和操作手机请交给助手完成。": "Live preview is available here. Ask the assistant to pair, switch devices, or control the phone.",
  "二维码无法显示，请刷新或打开配对窗口。": "Cannot display the QR code. Refresh it or open the pairing window.",
  "请打开配对窗口，用手机扫描二维码。": "Open the pairing window and scan its QR code with your phone.",
  "正在确认连接…": "Confirming connection…",
  "无法读取扫码状态，请重试。": "Could not check scan status. Please try again.",
  "扫码重新连接": "Scan to reconnect",
  "正在生成二维码…": "Creating QR code…",
  "二维码生成失败，请重试。": "Could not create a QR code. Please try again.",
  "换了 Wi-Fi？点击“扫码重新连接”，用手机 Tapilot 扫码，无需填写 IP。公司网络可能限制设备互通。": "Changed Wi-Fi? Choose Scan to reconnect and scan with Tapilot on your phone. No IP address needed. Company networks may restrict connections between devices.",
  "电脑网络已变化，正在重新连接手机。": "Network changed. Reconnect to the phone at its current address.",
  "手机地址变了？": "Phone address changed?",
  "手机当前 IP（可附加 :端口）": "Current phone IP (optional :port)",
  "更新地址并连接": "Update address and connect",
  "请输入手机当前的 IPv4 地址，可附加端口。": "Enter a valid phone IPv4 address and port.",
  "无法确认此地址是已配对的手机。请检查手机 IP、无线控制和网络隔离设置。": "Could not verify the paired phone at this address. Check the phone IP, wireless control, and network isolation.",
  "正在获取手机画面…": "Loading phone screen\u2026",
  "尚未收到手机画面，正在检查连接。长时间没有画面时可重试。": "No phone screen received yet. Checking the connection; retry if no image appears.",

  "正在连接…": "Connecting\u2026",
  "待解锁": "Unlock phone",
  "被占用": "In use",
  "重连中": "Reconnecting",
  "已暂停": "Paused",
  "需要重新配对": "Pair again",
  "待开启控制服务": "Enable device control",
  "连接中断，正在自动重连…": "Connection lost. Reconnecting automatically\u2026",
  "重试连接": "Retry connection",
  "立即重试": "Retry now",
  "无线连接你的手机": "Your phone, wirelessly.",
  "连接后即可在这里查看和操作手机屏幕。": "Connect to see and control your phone here.",
  "连接帮助": "Connection help",
  "刷新二维码": "Refresh QR code",
  "二维码已过期": "QR code expired",
  "用手机扫码，再在电脑确认连接": "Scan with your phone, then confirm on this computer",
  "配对成功，正在连接…": "Paired successfully. Connecting\u2026",

  "无法打开下载链接，请用手机扫描下载二维码。": "Cannot open the download link. Scan the download QR with your phone.",
  "手机还没安装 Tapilot？先安装 APK": "Phone does not have Tapilot? Install it first",
  "1. 用手机相机或浏览器扫描这个下载二维码。": "1. Scan this download QR with your phone camera or browser.",
  "Tapilot APK 下载二维码": "Tapilot APK download QR",
  "在 Android 手机上下载并安装 APK，按系统提示允许浏览器安装应用。需要 Android 11 及以上、ARM64。": "Download and install the APK on your Android phone. Allow the browser to install apps when Android asks. Android 11+ / ARM64 required.",
  "2. 打开 Tapilot 设置，在系统辅助功能中开启设备控制，再开启无线控制。": "2. Open Tapilot settings. Enable Device control in system Accessibility, then enable wireless control.",
  "3. 回到这里，在 Tapilot 无线控制中选择“扫描电脑二维码配对”，扫描下方配对码，再在电脑确认。": "3. Return here. In Tapilot wireless control, choose Scan computer QR, scan the pairing QR below, then confirm on this computer.",
  "下载码用于安装，配对码用于连接。请保持手机与电脑的 Wi-Fi 可互通；如果安装期间配对码过期，请重新生成配对码。": "The download QR installs the app; the pairing QR connects it. Keep both devices on reachable Wi-Fi. If pairing expires during installation, generate a new pairing QR.",

  "状态未知": "Status unknown",
  "秒后过期": "seconds remaining",
  "手机已扫码，请确认配对": "Phone scanned; confirm pairing",
  "请重载电脑上的 Tapilot 插件": "Reload Tapilot on your computer",
  "尚未配对手机": "No phone paired",
  "已连接 · 点击或拖动屏幕操作": "Connected · Click or drag to control",
  "已断开，点击重新连接": "Disconnected · Reconnect to continue",
  "已暂停，请在手机上完成私密操作": "Paused · Complete private steps on your phone",
  "请手动解锁手机，然后点击“已完成继续”": "Unlock your phone, then select Continue",
  "手机正被另一个控制端使用，请先结束那边的控制": "Phone in use by another controller; release it there first",
  "配对已失效，请在手机的无线控制设置中重新配对": "Pairing expired · Pair again in phone wireless settings",
  "无法连接手机，请检查局域网 Wi-Fi、手机亮屏和解锁状态": "Cannot connect · Check local Wi-Fi and keep the phone screen on and unlocked",
  "插件已更新或安装文件缺失。重载插件或重新打开客户端后继续，无需重新配对手机。": "Plugin updated or files missing. Reload the plugin or reopen the client; no phone pairing is needed.",
  "在手机 Tapilot 设置中开启无线控制，再点击下方按钮，用手机扫描电脑二维码。无需 USB。": "Enable wireless control in Tapilot settings, then use the button below and scan the computer QR code. No USB needed.",
  "在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后点击重新连接。": "Scan the computer QR again and confirm on the computer. The screen will reconnect automatically.",
  "请确认电脑和手机已连接同一局域网 Wi-Fi、手机屏幕已点亮并解锁，且 Tapilot 无线控制已开启。恢复后会自动连接；不会重放之前的操作。": "Check that the computer and phone are on the same local Wi-Fi network, the phone screen is on and unlocked, and Tapilot wireless control is enabled. The screen reconnects automatically; previous actions are never replayed.",
  "请先在另一控制端结束任务或释放手机，然后重新连接。": "End the task or release the phone on the other controller. This screen will reconnect automatically.",
  "请在手机上解锁，再点击已完成继续。不要在聊天中输入密码。": "Unlock on the phone, then select Continue. Never enter your passcode in chat.",
  "画面已隐藏。完成手机上的私密操作后，点击已完成继续。": "Screen hidden. Complete private steps on your phone, then select Continue.",
  "手机已释放。点击重新连接可恢复画面。": "Phone released. Select Reconnect to restore the screen.",
  "检查手机连接后重试。": "Check the phone connection and try again.",
  "已完成继续": "Continue",
  "重新连接": "Reconnect",
  "正在执行…": "Working…",
  "操作失败或结果未知，未自动重试。请查看新画面后再操作。": "Action failed or outcome unknown. No automatic retry. Inspect the new screen before acting.",
  "操作已执行，但画面刷新失败。请重新获取画面，不要重复操作。": "Action completed, but screen refresh failed. Get a fresh screen; do not repeat the action.",
  "操作结果未知，请重新连接并检查。输入草稿已保留，请勿直接重复提交。": "Outcome unknown. Reconnect and inspect the screen. Your draft was kept; do not resubmit blindly.",
  "无法打开配对窗口，请让聊天助手提供配对链接。": "Cannot open pairing window. Ask the assistant for the pairing link.",
  "打开配对窗口，用手机扫描二维码并在电脑确认，然后点击重新连接。窗口 5 分钟内有效。": "Open the pairing window, scan with your phone and confirm on the computer. The screen reconnects automatically. The QR expires after 5 minutes.",
  "无法创建配对窗口，请检查电脑 Wi-Fi 后重试。": "Cannot create pairing window. Check computer Wi-Fi and try again.",
  "请在支持 MCP App 的宿主中打开 Tapilot": "Open Tapilot in a host that supports MCP Apps",
  "当前 Android 手机屏幕": "Current Android phone screen",
  "正在连接手机画面…": "Connecting to phone screen…",
  "配对手机": "Pair phone",
  "打开配对窗口": "Open pairing window",
  "手机操作": "Phone controls",
  "返回": "Back",
  "主页": "Home",
  "多任务": "Recent apps",
  "查看上方内容": "Scroll up",
  "查看下方内容": "Scroll down",
  "暂停画面": "Pause screen",
  "断开并释放手机": "Disconnect and release phone",
  "输入文字": "Type text",
  "先点击手机上的输入框": "First tap a text field on the phone",
  "输入到手机，不会自动发送": "Type on the phone; does not send automatically",
  "替换原文字": "Replace existing text",
  "输入": "Type",
  "执行键盘回车动作": "Press the keyboard Enter key",
  "回车": "Enter",
  "配对 Tapilot": "Pair Tapilot",
  "用手机扫一扫，连接电脑": "Scan with your phone to connect",
  "保持手机和电脑连接同一 Wi-Fi，在 Tapilot 无线控制中选择「扫描电脑二维码」。": "Keep both devices on the same Wi-Fi. In Tapilot wireless control, choose Scan computer QR.",
  "使用手机 Tapilot 扫描的电脑配对二维码": "Computer pairing QR to scan with Tapilot",
  "等待手机扫码…": "Waiting for phone scan…",
  "确认配对此手机": "Confirm pairing with this phone",
  "附近的 Tapilot": "Nearby Tapilot phones",
  "刷新": "Refresh",
  "正在查找同一网络中的手机…": "Looking for phones on this network…",
  "发现设备不会授予控制权限；首次连接仍需手机扫码，并在此确认。二维码 5 分钟内有效，配对信息加密传输。": "Discovery does not grant control. First connection requires a phone scan and confirmation here. The QR expires after 5 minutes; pairing data is encrypted.",
  "请求失败": "Request failed",
  "已配对，可以返回 ChatGPT 查看手机画面": "Paired. Return to ChatGPT; the phone screen connects automatically.",
  "正在配对，请稍候…": "Pairing, please wait…",
  "尚未发现。确认手机开启无线控制；网络屏蔽自动发现时仍可直接扫码。": "No phones found. Enable wireless control on the phone. You can still scan the QR if discovery is blocked.",
  "已收到手机扫码信息，但电脑无法连接手机。请确认两端已连接同一局域网 Wi-Fi、手机已亮屏并解锁，且无线控制已开启，再次点击确认连接，无需重新扫码。": "Phone scan received, but the computer cannot connect. Check that both devices are on the same local Wi-Fi network, the phone screen is on and unlocked, and wireless control is enabled. Confirm again without rescanning.",
  "手机配对码已过期或失效，请重新扫码。": "The phone pairing code expired or is invalid. Scan again.",
  "配对未完成，结果可能不确定。请重新扫码后确认连接。": "Pairing did not complete and its outcome may be uncertain. Scan again and confirm.",
  "已连接": "Connected",
  "未连接": "Not connected",
  "已主动断开": "Disconnected by user",
  "配对由此电脑上的各个 agent 共享。": "Pairing is shared by agents on this computer.",
  "没有已配对设备": "No paired devices",
  "无法读取设备列表": "Could not load paired devices",
  "已配对设备": "Paired devices",
  "刷新设备": "Refresh devices",
  "连接设备": "Connect device",
  "断开设备": "Disconnect device",
  "移除本机配对": "Remove local pairing",
  "确认移除": "Confirm removal",
  "断开会保留配对。移除本机配对后，这台电脑需要重新扫码。": "Disconnect keeps pairing. Removing local pairing requires scanning again on this computer.",
  "无法完成配对，请保持手机解锁、确认 Wi-Fi 后重新扫码。": "Pairing failed. Keep the phone unlocked, check Wi-Fi and scan again.",
  "Tapilot 已更新或安装文件缺失。请重载电脑上的插件或客户端；重启手机不会修复此问题。": "Tapilot was updated or installation files are missing. Reload the plugin or client on this computer; restarting the phone will not fix this.",
  "请在手机上手动解锁，保持屏幕亮起。不要在聊天中输入密码。完成后选择“已完成继续”。": "Unlock your phone and keep its screen on. Do not enter the passcode in chat. Select Continue when ready.",
  "解锁手机": "Unlock phone",
  "请手动解锁手机后选择“已完成继续”。": "Unlock your phone, then select Continue.",
  "任务正在执行。可继续查看实时画面；如需接管，先暂停任务，勿重复提交。": "Task running. You can watch the live screen. Pause the task before taking manual control; do not submit it again.",
  "Midscene AI 可以自动识别和规划操作，需要通过 OpenAI 页面登录授权；也可继续使用当前聊天模型决策的单步模式。请选择后再继续。": "Midscene AI plans phone actions automatically and requires OpenAI authorization. You can also use steps mode with the current chat model. Choose a mode to continue.",
  "请在已打开的 OpenAI 页面完成 Tapilot 授权，完成后再检查授权状态。不要在聊天里提供密码、验证码或令牌。": "Complete Tapilot authorization on the open OpenAI page, then check authorization status. Never share passwords, codes or tokens in chat.",
  "Midscene AI 尚未授权。通过 auth_login 打开 OpenAI 登录授权页；聊天窗口的登录不会自动授权给插件，无需填写 API Key。": "Midscene AI is not authorized. Use auth_login to open OpenAI authorization; signing into chat does not authorize the plugin. No API Key is needed.",
  "查看 Midscene HTML 报告": "View Midscene HTML report",
  "Midscene HTML 报告": "Midscene HTML report",
  "Tapilot 已获得 ChatGPT 推理授权。可以关闭此页面并返回聊天。": "Tapilot is authorized to use ChatGPT inference. You can close this page and return to chat.",
  "授权未完成，请返回 Tapilot 查看状态并重试。": "Authorization incomplete. Return to Tapilot to check status and retry.",
  "在 Android 手机浏览器打开飞书链接下载并安装 Tapilot APK；按 Android 提示允许该浏览器安装应用。": "Open the Feishu link on your Android phone to download and install Tapilot. Allow the browser to install apps when Android asks.",
  "如使用 GitHub，打开 Releases 页面并下载 APK 资产。私有仓库需要访问权限；无 Release 或 APK 时使用飞书入口。": "For GitHub, open Releases and download the APK asset. Private repositories require access; use Feishu when no release or APK is available.",
  "打开 Tapilot → 设置 → 设备控制（辅助功能）→ 查看开启步骤。进入系统辅助功能的“已安装的应用／已下载的应用”，找到“Tapilot 设备控制”，打开“使用服务／允许”开关并确认，再返回 Tapilot 查看是否已开启。": "Open Tapilot → Settings → Device control (Accessibility) for instructions. In system Accessibility → Installed/Downloaded apps, enable Tapilot device control and confirm. Return to Tapilot to check it is enabled.",
  "在 Tapilot 设置中开启“ChatGPT 无线控制”，保持解锁，并确认手机与电脑处于同一 Wi-Fi。": "Enable ChatGPT wireless control in Tapilot settings. Keep your phone unlocked and both devices on the same Wi-Fi.",
  "让智能体执行 tapilot pair 打开电脑配对二维码。在手机无线控制中点击“扫描电脑二维码配对”，扫描后在电脑确认。配对链接不要发到聊天里。": "Ask your agent to run tapilot pair. In phone wireless settings, scan the computer QR and confirm on the computer. Never paste pairing links into chat.",
  "配对完成后检查连接，打开右侧手机画面，并按引导选择单步模式或 Midscene AI 授权。": "After pairing, the phone screen reconnects automatically. Follow the prompt to choose steps mode or authorize Midscene AI.",
  "安装后必须能找到“ChatGPT 无线控制”。若下载包没有此功能，需要包含无线控制的新版本；不要用重启或 ADB 代替兼容版本。下载入口本身不证明 APK 已包含插件需要的功能。": "The installed app must include ChatGPT wireless control. If missing, install a compatible version; restarting or using ADB is not a substitute. A download link alone does not prove plugin compatibility.",
  "飞书下载 Tapilot APK": "Download Tapilot APK from Feishu"
};
messages["用手机扫描二维码，再打开配对窗口确认连接。配对成功后画面会自动恢复，二维码 5 分钟内有效。"] = "Scan the QR code with your phone, then open the pairing window to confirm. The screen reconnects automatically after pairing. The QR code expires in 5 minutes.";
messages["配对二维码"] = "Pairing QR code";
messages["用手机扫描二维码，然后在此页面确认连接。配对成功后画面会自动恢复，二维码 5 分钟内有效。"] = "Scan the QR code with your phone, then confirm the connection here. The screen reconnects automatically after pairing. The QR code expires in 5 minutes.";
messages["确认连接"] = "Confirm connection";
messages["配对已过期或无法继续，请重新生成二维码。"] = "Pairing expired or is unavailable. Generate a new QR code.";
messages["手机的设备控制服务未连接"] = "Phone device control service is not connected";
messages["请在手机系统辅助功能中开启 Tapilot 设备控制；若开关已开启，请关闭再开启。恢复后会自动连接，无需重新配对。"] = "Enable Tapilot device control in the phone's Accessibility settings. If already enabled, turn it off and on again. The screen reconnects automatically; no new pairing is needed.";
Object.assign(messages, {
  "连接": "Connect", "断开": "Disconnect", "你的手机": "Your phone",
  "更多设备选项": "More device options", "无线连接你的手机": "Your phone, wirelessly.",
  "连接已配对手机，在这里查看和操作屏幕。": "Connect a paired phone to see and control its screen here."
});
Object.assign(messages, {
  "请在手机上解锁，画面会自动恢复": "Unlock your phone. The screen will resume automatically.",
  "请点亮手机屏幕并解锁，预览会自动恢复，无需刷新连接。": "Turn on the phone screen and unlock it. Preview resumes automatically; no reconnect needed."
});
messages["配对新手机"] = "Pair new phone";
messages["点击设备即可连接或切换；一次控制一台。"] = "Click a device to connect or switch. Control one phone at a time.";
messages["取消配对"] = "Cancel pairing";
messages["无法取消配对，请重试。"] = "Could not cancel pairing. Please try again.";
messages["正在连接手机…"] = "Connecting to phone…";
const chinese = {"在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后点击重新连接。": "在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后会自动连接并显示画面。", "请确认电脑和手机已连接同一局域网 Wi-Fi、手机屏幕已点亮并解锁，且 Tapilot 无线控制已开启。恢复后会自动连接；不会重放之前的操作。": "请确认电脑和手机已连接同一局域网 Wi-Fi、手机屏幕已点亮并解锁，且 Tapilot 无线控制已开启。恢复后会自动连接；不会重放之前的操作。", "请先在另一控制端结束任务或释放手机，然后重新连接。": "请先在另一控制端结束任务或释放手机，画面会自动恢复。", "打开配对窗口，用手机扫描二维码并在电脑确认，然后点击重新连接。窗口 5 分钟内有效。": "打开配对窗口，用手机扫描二维码并在电脑确认，画面会自动连接。窗口 5 分钟内有效。", "已配对，可以返回 ChatGPT 查看手机画面": "配对成功。返回 ChatGPT 后会自动连接并显示手机画面。"};
let language = /^zh(?:-|$)/i.test(globalThis.navigator?.language ?? '') ? 'zh' : 'en';
export function setLanguage(value) { if (value === 'zh' || value === 'en') language = value; }
export function t(key) { return language === 'zh' ? (chinese[key] ?? key) : (messages[key] ?? key); }
const connectionErrors = {
  'Phone is locked. Unlock it on the device.': '请点亮手机屏幕并解锁后重试。',
  'A local conversation is running. Stop it before connecting.': '手机上正在运行本地任务，请先在手机上结束任务，再连接。',
  'Device is already controlled by another session': '手机正被其他会话控制，请先释放手机，再连接。',
  'Another task is already registered': '手机正被其他会话控制，请先释放手机，再连接。',
  'Phone timed out; action outcome is unknown. Do not repeat automatically.': '手机响应超时，操作结果未知。请检查画面，不要直接重复操作。',
  'Phone disconnected. Capture a new screen before continuing.': '手机已断开，请重新连接并获取新画面后继续。',
  'Phone connection closed': '手机已断开，请重新连接并获取新画面后继续。',
  'Phone connection failed. Check Wi-Fi and enable wireless control on the phone.': '无法连接手机。请确认电脑和手机已连接同一局域网 Wi-Fi、手机已亮屏并解锁，且 Tapilot 无线控制已开启。',
  'Phone connection timed out': '连接手机超时。请确认电脑和手机已连接同一局域网 Wi-Fi、手机已亮屏并解锁，且 Tapilot 无线控制已开启。',
  'Unlock the phone first': '请点亮手机屏幕并解锁后重试。',
  'Enable Tapilot device control in Accessibility settings': '请在手机系统辅助功能中开启 Tapilot 设备控制。',
  'Phone disconnected. Action outcome may be unknown; inspect the screen before retrying.': '手机连接已断开，操作结果可能不确定。请检查手机画面后再操作。',
  'Phone certificate changed. Pair again on the phone; no credential was sent.': '手机证书已改变，请重新配对。未发送配对凭据。',
  "Not paired. Pair this computer from the phone's Wireless control settings.": '尚未配对，请在手机的无线控制设置中配对这台电脑。',
};
Object.assign(messages, {
  '手机上正在运行本地任务，请先在手机上结束任务，再连接。': 'A local task is running on the phone. Stop it on the phone before connecting.',
  '手机正被其他会话控制，请先释放手机，再连接。': 'Another session controls the phone. Release it there before connecting.',
  '手机响应超时，操作结果未知。请检查画面，不要直接重复操作。': 'Phone response timed out; the action outcome is unknown. Inspect the screen before trying again.',
  '手机已断开，请重新连接并获取新画面后继续。': 'Phone disconnected. Reconnect and capture a fresh screen before continuing.',
  '无法连接手机。请确认电脑和手机已连接同一局域网 Wi-Fi、手机已亮屏并解锁，且 Tapilot 无线控制已开启。': 'Cannot connect to the phone. Check that both devices are on the same local Wi-Fi network, the phone screen is on and unlocked, and Tapilot wireless control is enabled.',
  '连接手机超时。请确认电脑和手机已连接同一局域网 Wi-Fi、手机已亮屏并解锁，且 Tapilot 无线控制已开启。': 'Phone connection timed out. Check that both devices are on the same local Wi-Fi network, the phone screen is on and unlocked, and Tapilot wireless control is enabled.',
  '请点亮手机屏幕并解锁后重试。': 'Turn on the phone screen and unlock it, then try again.',
  '请在手机系统辅助功能中开启 Tapilot 设备控制。': 'Enable Tapilot device control in the phone Accessibility settings.',
  '手机连接已断开，操作结果可能不确定。请检查手机画面后再操作。': 'Phone disconnected. The action outcome may be unknown. Inspect the phone screen before trying again.',
  '手机证书已改变，请重新配对。未发送配对凭据。': 'Phone certificate changed. Pair again; no pairing credential was sent.',
  '尚未配对，请在手机的无线控制设置中配对这台电脑。': 'Not paired. Pair this computer from the phone wireless control settings.',
});
export function connectionErrorMessage(message) {
  const key = connectionErrors[message] ?? Object.keys(messages).find(key => messages[key] === message) ?? message;
  return t(key);
}
const originals = new WeakMap();
const reverse = new Map(Object.entries(messages).map(([zh,en]) => [en,zh]));
export function localize(root = document) {
  root.documentElement?.setAttribute('lang', language === 'zh' ? 'zh-CN' : 'en');
  const walk = node => {
    if (['SCRIPT','STYLE'].includes(node.nodeName)) return;
    if (node.nodeType === 3) {
      const original = originals.get(node) ?? node.textContent;
      originals.set(node,original);
      const key = reverse.get(original.trim());
      if (key) node.textContent = original.replace(original.trim(), t(key));
    }
    for (const child of node.childNodes ?? []) walk(child);
    for (const attribute of ['title','aria-label','alt','placeholder']) {
      const original = node.getAttribute?.('data-original-'+attribute) ?? node.getAttribute?.(attribute);
      if (original && reverse.has(original)) {
        node.setAttribute('data-original-'+attribute,original);
        node.setAttribute(attribute,t(reverse.get(original)));
      }
    }
  };
  walk(root);
}

const messages = {
  "秒后过期": "seconds remaining",
  "手机已扫码，请确认配对": "Phone scanned; confirm pairing",
  "请重载电脑上的 Android Use 插件": "Reload Android Use on your computer",
  "尚未配对手机": "No phone paired",
  "已连接 · 点击或拖动屏幕操作": "Connected · Click or drag to control",
  "已断开，点击重新连接": "Disconnected · Reconnect to continue",
  "已暂停，请在手机上完成私密操作": "Paused · Complete private steps on your phone",
  "请手动解锁手机，然后点击“已完成继续”": "Unlock your phone, then select Continue",
  "手机正被另一个控制端使用，请先结束那边的控制": "Phone in use by another controller; release it there first",
  "配对已失效，请在手机的无线控制设置中重新配对": "Pairing expired · Pair again in phone wireless settings",
  "无法连接手机，请保持解锁并检查 Wi-Fi 和无线控制开关": "Cannot connect · Keep the phone unlocked and check Wi-Fi and wireless control",
  "插件已更新或安装文件缺失。重载插件或重新打开客户端后继续，无需重新配对手机。": "Plugin updated or files missing. Reload the plugin or reopen the client; no phone pairing is needed.",
  "在手机 Tapilot 设置中开启无线控制，再点击下方按钮，用手机扫描电脑二维码。无需 USB。": "Enable wireless control in Tapilot settings, then use the button below and scan the computer QR code. No USB needed.",
  "在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后点击重新连接。": "Scan the computer QR again and confirm on the computer. The screen will reconnect automatically.",
  "请检查手机无线控制已开启、两端 Wi-Fi 可互通。恢复后点击重新连接；不会重放之前的操作。": "Enable wireless control and check both devices can reach each other over Wi-Fi. The screen reconnects automatically; previous actions are never replayed.",
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
  "请在支持 MCP App 的宿主中打开 Android Use": "Open Android Use in a host that supports MCP Apps",
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
  "配对 Android Use": "Pair Android Use",
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
  "无法完成配对，请保持手机解锁、确认 Wi-Fi 后重新扫码。": "Pairing failed. Keep the phone unlocked, check Wi-Fi and scan again.",
  "Android Use 已更新或安装文件缺失。请重载电脑上的插件或客户端；重启手机不会修复此问题。": "Android Use was updated or installation files are missing. Reload the plugin or client on this computer; restarting the phone will not fix this.",
  "请在手机上手动解锁，保持屏幕亮起。不要在聊天中输入密码。完成后选择“已完成继续”。": "Unlock your phone and keep its screen on. Do not enter the passcode in chat. Select Continue when ready.",
  "解锁手机": "Unlock phone",
  "请手动解锁手机后选择“已完成继续”。": "Unlock your phone, then select Continue.",
  "任务正在执行。可继续查看实时画面；如需接管，先暂停任务，勿重复提交。": "Task running. You can watch the live screen. Pause the task before taking manual control; do not submit it again.",
  "Midscene AI 可以自动识别和规划操作，需要通过 OpenAI 页面登录授权；也可继续使用当前聊天模型决策的单步模式。请选择后再继续。": "Midscene AI plans phone actions automatically and requires OpenAI authorization. You can also use steps mode with the current chat model. Choose a mode to continue.",
  "请在已打开的 OpenAI 页面完成 Android Use 授权，完成后再检查授权状态。不要在聊天里提供密码、验证码或令牌。": "Complete Android Use authorization on the open OpenAI page, then check authorization status. Never share passwords, codes or tokens in chat.",
  "Midscene AI 尚未授权。通过 auth_login 打开 OpenAI 登录授权页；聊天窗口的登录不会自动授权给插件，无需填写 API Key。": "Midscene AI is not authorized. Use auth_login to open OpenAI authorization; signing into chat does not authorize the plugin. No API Key is needed.",
  "查看 Midscene HTML 报告": "View Midscene HTML report",
  "Midscene HTML 报告": "Midscene HTML report",
  "Android Use 已获得 ChatGPT 推理授权。可以关闭此页面并返回聊天。": "Android Use is authorized to use ChatGPT inference. You can close this page and return to chat.",
  "授权未完成，请返回 Android Use 查看状态并重试。": "Authorization incomplete. Return to Android Use to check status and retry.",
  "在 Android 手机浏览器打开飞书链接下载并安装 Tapilot APK；按 Android 提示允许该浏览器安装应用。": "Open the Feishu link on your Android phone to download and install Tapilot. Allow the browser to install apps when Android asks.",
  "如使用 GitHub，打开 Releases 页面并下载 APK 资产。私有仓库需要访问权限；无 Release 或 APK 时使用飞书入口。": "For GitHub, open Releases and download the APK asset. Private repositories require access; use Feishu when no release or APK is available.",
  "打开 Tapilot → 设置 → 设备控制（辅助功能）→ 查看开启步骤。进入系统辅助功能的“已安装的应用／已下载的应用”，找到“Tapilot 设备控制”，打开“使用服务／允许”开关并确认，再返回 Tapilot 查看是否已开启。": "Open Tapilot → Settings → Device control (Accessibility) for instructions. In system Accessibility → Installed/Downloaded apps, enable Tapilot device control and confirm. Return to Tapilot to check it is enabled.",
  "在 Tapilot 设置中开启“ChatGPT 无线控制”，保持解锁，并确认手机与电脑处于同一 Wi-Fi。": "Enable ChatGPT wireless control in Tapilot settings. Keep your phone unlocked and both devices on the same Wi-Fi.",
  "让 ChatGPT 调用 tapilot_midscene(action=pair) 打开电脑配对二维码。在手机无线控制中点击“扫描电脑二维码配对”，扫描后在电脑确认。旧版本仍可使用复制链接与本地终端配对，链接不要发到聊天里。": "Ask ChatGPT to call tapilot_midscene(action=pair). In phone wireless settings, scan the computer QR and confirm on the computer. Legacy versions support local terminal pairing links; never paste them into chat.",
  "配对完成后检查连接，打开右侧手机画面，并按引导选择单步模式或 Midscene AI 授权。": "After pairing, the phone screen reconnects automatically. Follow the prompt to choose steps mode or authorize Midscene AI.",
  "安装后必须能找到“ChatGPT 无线控制”。若下载包没有此功能，需要包含无线控制的新版本；不要用重启或 ADB 代替兼容版本。下载入口本身不证明 APK 已包含插件需要的功能。": "The installed app must include ChatGPT wireless control. If missing, install a compatible version; restarting or using ADB is not a substitute. A download link alone does not prove plugin compatibility.",
  "飞书下载 Tapilot APK": "Download Tapilot APK from Feishu"
};
messages["用手机扫描二维码，再打开配对窗口确认连接。配对成功后画面会自动恢复，二维码 5 分钟内有效。"] = "Scan the QR code with your phone, then open the pairing window to confirm. The screen reconnects automatically after pairing. The QR code expires in 5 minutes.";
messages["配对二维码"] = "Pairing QR code";
messages["用手机扫描二维码，然后在此页面确认连接。配对成功后画面会自动恢复，二维码 5 分钟内有效。"] = "Scan the QR code with your phone, then confirm the connection here. The screen reconnects automatically after pairing. The QR code expires in 5 minutes.";
messages["确认连接"] = "Confirm connection";
messages["配对已过期或无法继续，请重新生成二维码。"] = "Pairing expired or is unavailable. Generate a new QR code.";
const chinese = {"在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后点击重新连接。": "在手机上重新扫描电脑二维码，并在电脑窗口确认。完成后会自动连接并显示画面。", "请检查手机无线控制已开启、两端 Wi-Fi 可互通。恢复后点击重新连接；不会重放之前的操作。": "请检查手机无线控制已开启、两端 Wi-Fi 可互通。恢复后会自动连接；不会重放之前的操作。", "请先在另一控制端结束任务或释放手机，然后重新连接。": "请先在另一控制端结束任务或释放手机，画面会自动恢复。", "打开配对窗口，用手机扫描二维码并在电脑确认，然后点击重新连接。窗口 5 分钟内有效。": "打开配对窗口，用手机扫描二维码并在电脑确认，画面会自动连接。窗口 5 分钟内有效。", "已配对，可以返回 ChatGPT 查看手机画面": "配对成功。返回 ChatGPT 后会自动连接并显示手机画面。"};
let language = /^zh(?:-|$)/i.test(globalThis.navigator?.language ?? '') ? 'zh' : 'en';
export function setLanguage(value) { if (value === 'zh' || value === 'en') language = value; }
export function t(key) { return language === 'zh' ? (chinese[key] ?? key) : (messages[key] ?? key); }
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

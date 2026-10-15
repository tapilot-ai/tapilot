import { fileURLToPath } from "node:url";

export const APK_DOWNLOADS = {
  feishu: 'https://www.feishu.cn/file/Ig0KbUKlJoADozxRbOocm5iqnXf',
  github: 'https://github.com/quanru/tapilot/releases',
};
export const installationGuide = {
  state: 'setup_required',
  downloads: APK_DOWNLOADS,
  pairing: { preferred_action: 'tapilot_midscene(action=pair)', command: 'node', script: fileURLToPath(new URL('./pair.mjs', import.meta.url)) },
  download_links: `[飞书下载 Tapilot APK](${APK_DOWNLOADS.feishu}) · [GitHub Releases](${APK_DOWNLOADS.github})`,
  requirements: 'Android 11+ / ARM64; computer and phone on the same reachable Wi-Fi network.',
  steps: [
    '在 Android 手机浏览器打开飞书链接下载并安装 Tapilot APK；按 Android 提示允许该浏览器安装应用。',
    '如使用 GitHub，打开 Releases 页面并下载 APK 资产。私有仓库需要访问权限；无 Release 或 APK 时使用飞书入口。',
    '打开 Tapilot → 设置 → 设备控制（辅助功能）→ 查看开启步骤。进入系统辅助功能的“已安装的应用／已下载的应用”，找到“Tapilot 设备控制”，打开“使用服务／允许”开关并确认，再返回 Tapilot 查看是否已开启。',
    '在 Tapilot 设置中开启“ChatGPT 无线控制”，保持解锁，并确认手机与电脑处于同一 Wi-Fi。',
    '让 ChatGPT 调用 tapilot_midscene(action=pair) 打开电脑配对二维码。在手机无线控制中点击“扫描电脑二维码配对”，扫描后在电脑确认。旧版本仍可使用复制链接与本地终端配对，链接不要发到聊天里。',
    '配对完成后检查连接，打开右侧手机画面，并按引导选择单步模式或 Midscene AI 授权。',
  ],
  compatibility: '安装后必须能找到“ChatGPT 无线控制”。若下载包没有此功能，需要包含无线控制的新版本；不要用重启或 ADB 代替兼容版本。下载入口本身不证明 APK 已包含插件需要的功能。',
  host_instruction: 'Show both clickable download links and guide phone installation, accessibility permission, wireless control, and private local pairing using node and the returned absolute pairing.script path (quote it for the user’s shell; no repository checkout is needed). Ask the user to confirm completion, then check connection. Do not require USB/ADB for normal installation or control. Never claim a GitHub APK exists without checking release assets, or ask for a pairing secret in chat.',
};

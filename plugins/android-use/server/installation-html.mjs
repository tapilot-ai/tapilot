import { readFile } from 'node:fs/promises';
import QRCode from 'qrcode';
import { APK_DOWNLOADS } from './installation.mjs';

export async function installationHtml() {
  const template = await readFile(new URL('../ui/installation.html', import.meta.url), 'utf8');
  const qr = await QRCode.toDataURL(APK_DOWNLOADS.feishu, { margin: 4, width: 240 });
  return template.replace('{{DOWNLOAD_QR}}', qr)
    .replace('{{FEISHU}}', APK_DOWNLOADS.feishu).replace('{{GITHUB}}', APK_DOWNLOADS.github);
}

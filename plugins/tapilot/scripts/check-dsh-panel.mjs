// Run against an isolated, already-started DSH Web host with this plugin added.
// Credentials/launch tokens are accepted via environment or a private launch log
// and are never printed or written into the screenshot/result metadata.
import { chromium } from 'playwright-core';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const log = process.env.TAPILOT_DSH_LOG ? await readFile(process.env.TAPILOT_DSH_LOG, 'utf8') : '';
const url = process.env.TAPILOT_DSH_URL || log.match(/https?:\/\/[^\s]+\/\?token=[^\s]+/)?.[0];
if (!url) throw new Error('Set TAPILOT_DSH_URL or TAPILOT_DSH_LOG for an isolated DSH Web test host');
const origin = new URL(url).origin;
const output = process.env.TAPILOT_PANEL_EVIDENCE || fileURLToPath(new URL('../../../build/client-validation/panels/', import.meta.url));
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ headless: true, executablePath: process.env.TAPILOT_CHROME_PATH || (process.platform === 'darwin' ? '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' : undefined) });
let page;
try {
  const anonymous = await browser.newContext();
  assert.equal((await anonymous.request.get(`${origin}/api/tapilot/panel`)).status(), 401, 'DSH must authenticate the panel');
  await anonymous.close();
  page = await browser.newPage({ viewport: { width: 1280, height: 950 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(url);
  const button = page.getByRole('button', { name: 'Tapilot', exact: true });
  try { await button.click({ timeout: 3000 }); }
  catch (error) {
    const previewNotice = page.getByRole('button', { name: /^(继续|Continue)$/ });
    if (!await previewNotice.isVisible()) throw error;
    await previewNotice.click(); await previewNotice.waitFor({ state: 'hidden' });
    await button.click();
  }
  const response = await page.request.get(`${origin}/api/tapilot/panel`);
  assert.equal(response.status(), 200);
  const frame = page.frameLocator('iframe[title="Tapilot phone panel"]');
  await frame.locator('#pair').waitFor({ state: 'visible', timeout: 15000 });
  const denied = await page.request.post(`${origin}/api/tapilot/call`, { headers: { 'X-Tapilot-Panel': '1' }, data: { name: 'tapilot_midscene', arguments: { action: 'home' } } });
  assert.equal(denied.status(), 400, 'the native panel cannot reach CLI/admin tools');
  assert.deepEqual(errors, []);
  await page.screenshot({ path: join(output, 'dsh-web.png'), fullPage: true });
  const result = { host: 'DSH Web', nativePanel: 'passed', unpairedState: 'passed', authentication: 'passed', panelAllowlist: 'passed', hardware: 'not_tested', dshGo: 'not_tested' };
  await writeFile(join(output, 'dsh-native.json'), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page?.screenshot({ path: join(output, 'dsh-failure.png'), fullPage: true }).catch(() => {});
  // Playwright navigation diagnostics may include the launch token. Redact it.
  throw new Error(String(error.message).replaceAll(url, origin).replace(/token=[^\s&"']+/g, 'token=REDACTED'));
} finally { await browser.close(); }

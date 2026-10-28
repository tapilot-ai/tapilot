import { execFileSync, spawn, type ChildProcess } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { isIP } from 'node:net';
import { randomUUID } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';
import { networkWatchdogScript, shellQuote, type RadioState } from './network-watchdog.js';

type Connection = {
  open(pair?: boolean, rediscover?: boolean): Promise<unknown>;
  request(method: string): Promise<any>;
  close(): void;
};

/** Owns only resources created by this test; never terminates another controller. */
export class DeviceFixtures {
  private network?: RadioState;
  private networkGuard?: string;
  private portProcess?: ChildProcess;
  private portPid?: string;
  private controller?: Connection;
  private attachmentPath?: string;
  constructor(private serial: string, private packageName: string,
    private runAdb = (args: string[]) => execFileSync('adb', ['-s', serial, ...args], { encoding: 'utf8', timeout: 15_000 }).trim()) {}

  private adb(args: string[]) {
    return this.runAdb(args);
  }
  private requireFaults() {
    if (process.env.TAPILOT_E2E_FAULTS !== '1') throw new Error('Use test:resilience to opt into device faults');
  }
  stageAttachment() {
    if (this.attachmentPath) throw new Error('Attachment fixture is already active');
    this.attachmentPath = `/sdcard/Download/tapilot-attachment-qa-${randomUUID()}.txt`;
    this.adb(['push', fileURLToPath(new URL('./fixtures/attachment.txt', import.meta.url)), this.attachmentPath]);
  }
  shareText() {
    this.adb(['shell', 'am', 'start', '-W', '-n', `${this.packageName}/lifeos.md.tapilot.MainActivity`,
      '-a', 'android.intent.action.SEND', '-t', 'text/plain', '--es', 'android.intent.extra.TEXT', 'SHARED-TEXT-QA']);
  }
  async offline() {
    this.requireFaults();
    if (this.network) throw new Error('Network fault is already active');
    const wifi = this.adb(['shell', 'cmd', 'wifi', 'status']);
    const data = this.adb(['shell', 'settings', 'get', 'global', 'mobile_data']);
    const airplane = this.adb(['shell', 'cmd', 'connectivity', 'airplane-mode']);
    if (!/^Wifi is (enabled|disabled)/.test(wifi) || !/^[01]$/.test(data) || !/^(enabled|disabled)$/.test(airplane)) throw new Error('Cannot safely snapshot network state');
    this.network = { wifi: wifi.startsWith('Wifi is enabled'), data: data === '1', airplane: airplane === 'enabled' };
    try {
      this.networkGuard = `/data/local/tmp/tapilot-radio-${randomUUID()}`;
      const dir = shellQuote(this.networkGuard);
      const script = shellQuote(networkWatchdogScript(this.networkGuard, this.network));
      this.adb(['shell', `mkdir ${dir} && printf '%s' ${script} > ${dir}/restore.sh && touch ${dir}/pending`]);
      // Background only the fully redirected process. Backgrounding a compound
      // setup command leaves its subshell holding adb's output pipe open.
      this.adb(['shell', `nohup sh ${dir}/restore.sh > ${dir}/log 2>&1 < /dev/null &`]);
      let armed = false;
      for (let attempt = 0; attempt < 30; attempt++) {
        if (this.adb(['shell', `if [ -f ${dir}/armed ]; then echo armed; fi`]) === 'armed') { armed = true; break; }
        await delay(100);
      }
      if (!armed) throw new Error('Phone-side network recovery watchdog did not arm');
      this.adb(['shell', 'cmd', 'connectivity', 'airplane-mode', 'enable']);
      this.adb(['shell', 'svc', 'wifi', 'disable']);
      this.adb(['shell', 'svc', 'data', 'disable']);
      // OEM radio transitions can take several seconds after svc returns.
      // A successful command is not proof of an offline device.
      const deadline = Date.now() + 30_000;
      let observedWifi = '', observedData = '';
      while (Date.now() < deadline) {
        observedWifi = this.adb(['shell', 'cmd', 'wifi', 'status']).split('\n')[0];
        observedData = this.adb(['shell', 'cmd', 'connectivity', 'airplane-mode']);
        if (observedWifi.startsWith('Wifi is disabled') && observedData === 'enabled') return;
        await delay(250);
      }
      throw new Error(`Phone radio controls did not reach the offline fixture state: ${observedWifi}; airplane-mode=${observedData}`);
    } catch (error) { await this.restore(); throw error; }
  }
  async waitForNetwork(signal: AbortSignal, timeoutMs = 60_000) {
    const started = Date.now();
    do {
      signal.throwIfAborted();
      const snapshot = this.adb(['shell', 'dumpsys', 'connectivity']);
      const id = snapshot.match(/^Active default network: (\d+)$/m)?.[1];
      const network = id && snapshot.split('\n').find(line => line.includes(`NetworkAgentInfo{network{${id}}`));
      if (network && /\bCapabilities: [^\]]*\bINTERNET\b[^\]]*\bVALIDATED\b/.test(network)) {
        return { validated: true, elapsedMs: Date.now() - started };
      }
      if (Date.now() - started >= timeoutMs) break;
      await delay(250, undefined, { signal });
    } while (true);
    throw new Error('Android default network did not become validated; model recovery was not attempted');
  }
  async waitForTaskIdle(signal: AbortSignal, timeoutMs = 25 * 60_000) {
    const started = Date.now();
    let polls = 0;
    while (true) {
      signal.throwIfAborted();
      const services = this.adb(['shell', 'dumpsys', 'activity', 'services', this.packageName]);
      polls++;
      // The native service spans all Pi/model/phone operations and stops after a
      // terminal outcome. An unchanged screenshot is not evidence of a pause.
      if (!/\* ServiceRecord\{[^\n]*TaskRuntimeService[\s}]/.test(services)) {
        return { waitMs: Date.now() - started, polls, status: 'native-service-idle' };
      }
      if (Date.now() - started >= timeoutMs) throw new Error('Native task is still running; inspect and stop the owned task before rerunning');
      await delay(2000, undefined, { signal });
    }
  }
  restart() {
    this.requireFaults();
    this.adb(['shell', 'am', 'force-stop', this.packageName]);
    this.adb(['shell', 'monkey', '-p', this.packageName, '-c', 'android.intent.category.LAUNCHER', '1']);
  }
  async lockAndWake() {
    this.requireFaults();
    this.adb(['shell', 'input', 'keyevent', '223']);
    try { await delay(1500); } finally { this.adb(['shell', 'input', 'keyevent', '224']); }
    // A secure lock remains for the user to unlock. Never inject a PIN or bypass it.
  }
  requireRejectedAuth() {
    if (process.env.TAPILOT_E2E_AUTH_FIXTURE !== '1' || this.packageName !== 'lifeos.md.tapilot.e2everify') {
      throw new Error('Auth rejection requires test:auth-fixture and the isolated lifeos.md.tapilot.e2everify package with preconfigured rejected test credentials');
    }
  }
  async occupyPort() {
    this.requireFaults();
    if (this.portProcess) throw new Error('Port fixture is already active');
    const address = process.env.TAPILOT_E2E_WIFI_ADDRESS || '';
    if (isIP(address) !== 4) throw new Error('Set TAPILOT_E2E_WIFI_ADDRESS to this test phone Wi-Fi IPv4');
    if (!this.adb(['shell', 'ip', '-o', '-4', 'addr', 'show']).includes(` ${address}/`)) throw new Error('Wi-Fi address does not belong to selected device');
    const processHandle = spawn('adb', ['-s', this.serial, 'shell', `echo $$; exec toybox nc -l -s ${address} -p 39877`], { stdio: ['pipe', 'pipe', 'pipe'] });
    this.portProcess = processHandle;
    await new Promise<void>((resolve, reject) => {
      let output = '';
      const timer = setTimeout(() => reject(new Error('Port fixture did not start')), 5000);
      processHandle.once('error', error => { clearTimeout(timer); reject(error); });
      processHandle.once('exit', () => { clearTimeout(timer); reject(new Error('Port fixture exited; stop wireless control before occupying its port')); });
      processHandle.stdout!.on('data', chunk => {
        output += chunk.toString();
        const pid = output.match(/^(\d+)\r?\n/);
        if (pid) { this.portPid = pid[1]; clearTimeout(timer); resolve(); }
      });
    });
    // Check readiness without connecting: nc -l accepts only one connection.
    for (let attempt = 0; attempt < 20; attempt++) {
      if (processHandle.exitCode !== null) throw new Error('Preferred port is already occupied by another process');
      if (this.adb(['shell', 'ss', '-ltn']).includes(`${address}:39877`)) return;
      await delay(100);
    }
    throw new Error('Could not verify preferred-port occupancy');
  }
  async connectController() {
    if (this.controller) throw new Error('This fixture already owns a controller');
    const file = process.env.TAPILOT_E2E_PAIRING_FILE;
    if (!file) throw new Error('Set TAPILOT_E2E_PAIRING_FILE to private pairing data for this test phone');
    const configModule = await import(new URL('../../plugins/android-use/server/config.mjs', import.meta.url).href);
    const config = configModule.validateConfig(JSON.parse(readFileSync(file, 'utf8')));
    if (!this.adb(['shell', 'ip', '-o', '-4', 'addr', 'show']).includes(` ${config.host}/`)) throw new Error('Pairing file does not target the selected Android device');
    const transport = await import(new URL('../../plugins/android-use/server/transport.mjs', import.meta.url).href);
    const controller: Connection = new transport.PhoneConnection(config);
    this.controller = controller;
    try { await controller.open(false, false); } catch (error) { this.disconnectController(); throw error; }
  }
  disconnectController() { this.controller?.close(); this.controller = undefined; }
  async verifyReconnect() {
    await this.connectController();
    try {
      const first = await this.controller!.request('device.capture');
      if (!first?.geometry?.frameId) throw new Error('First wireless capture lacks a frame identity');
      this.disconnectController();
      await this.connectController();
      const second = await this.controller!.request('device.capture');
      if (!second?.geometry?.frameId || first.geometry.frameId === second.geometry.frameId) throw new Error('Reconnection did not produce a fresh frame');
    } finally { this.disconnectController(); }
  }
  async restore() {
    this.disconnectController();
    const errors: unknown[] = [];
    if (this.attachmentPath) {
      try { this.adb(['shell', 'rm', '-f', this.attachmentPath]); this.attachmentPath = undefined; }
      catch (error) { errors.push(error); }
    }
    if (this.portPid) {
      try {
        const command = this.adb(['shell', 'cat', `/proc/${this.portPid}/cmdline`]);
        if (command.includes('toybox') && command.includes('39877') && command.includes('nc')) this.adb(['shell', 'kill', this.portPid]);
      } catch (error) { if (this.portProcess?.exitCode === null) errors.push(error); }
    }
    this.portProcess?.kill(); this.portProcess = undefined; this.portPid = undefined;
    if (this.network) {
      const original = this.network;
      const networkErrors: unknown[] = [];
      try { this.adb(['shell', 'cmd', 'connectivity', 'airplane-mode', original.airplane ? 'enable' : 'disable']); }
      catch (error) { networkErrors.push(error); }
      for (const [radio, enabled] of [['wifi', original.wifi], ['data', original.data]] as const) {
        try { this.adb(['shell', 'svc', radio, enabled ? 'enable' : 'disable']); }
        catch (error) { networkErrors.push(error); }
      }
      if (networkErrors.length) errors.push(...networkErrors);
      else this.network = undefined;
    }
    // Leave the device-side guard armed if any radio restoration failed.
    if (this.networkGuard && !this.network) {
      try {
        const dir = shellQuote(this.networkGuard);
        const fired = this.adb(['shell', `if [ -f ${dir}/fired ]; then echo fired; fi`]) === 'fired';
        this.adb(['shell', 'rm', '-rf', this.networkGuard]);
        this.networkGuard = undefined;
        if (fired) errors.push(new Error('Phone network recovery watchdog expired; offline acceptance is invalid'));
      } catch (error) { errors.push(error); }
    }
    if (errors.length) throw new AggregateError(errors, 'Device fixture cleanup failed; inspect the device before another run');
  }
}

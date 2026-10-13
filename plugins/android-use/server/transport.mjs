import tls from 'node:tls';
import { createHash, randomUUID, timingSafeEqual } from 'node:crypto';
import { networkInterfaces } from 'node:os';

// A VPN may route the phone's LAN range into its tunnel. Bind the source to a
// physical interface when unambiguous, preserving the pinned TLS identity.
export function localAddressForPhone(host, interfaces = networkInterfaces()) {
  if (host.startsWith('127.')) return undefined;
  const addresses = Object.entries(interfaces)
    .filter(([name]) => /^(en\d+|eth\d+|wlan\d+|wlp\w+)$/.test(name))
    .flatMap(([, entries]) => (entries ?? []).filter(a => a.family === 'IPv4' && !a.internal));
  const numeric = ip => ip.split('.').reduce((value, part) => (value << 8) | Number(part), 0);
  const sameSubnet = addresses.filter(a => (numeric(a.address) & numeric(a.netmask)) === (numeric(host) & numeric(a.netmask)));
  const candidates = sameSubnet.length ? sameSubnet : addresses;
  return candidates.length === 1 ? candidates[0].address : undefined;
}

const MAX_RESPONSE = 32 * 1024 * 1024;
export class PhoneConnection {
  #socket;
  #pending = new Map();
  #buffer = Buffer.alloc(0);
  constructor(config) { this.config = config; }
  get connected() { return !!this.#socket && !this.#socket.destroyed; }
  async open(pair = false) {
    if (this.connected) return;
    const socket = tls.connect({ host: this.config.host, port: this.config.port, localAddress: localAddressForPhone(this.config.host), minVersion: 'TLSv1.2', maxVersion: 'TLSv1.2', rejectUnauthorized: false });
    socket.setKeepAlive(true, 10000);
    socket.setNoDelay(true);
    this.#socket = socket;
    this.#buffer = Buffer.alloc(0);
    socket.on('data', data => this.#receive(data));
    socket.on('error', () => { if (this.#socket === socket) this.close(new Error('Phone connection failed. Check Wi-Fi and enable wireless control on the phone.')); });
    socket.on('close', () => { if (this.#socket === socket) this.close(new Error('Phone disconnected. Action outcome may be unknown; inspect the screen before retrying.')); });
    try {
      await new Promise((resolve, reject) => {
        const timer = setTimeout(() => { reject(new Error('Phone connection timed out')); socket.destroy(); }, 5000);
        socket.once('error', () => { clearTimeout(timer); reject(new Error('Unable to connect to phone')); });
        socket.once('secureConnect', () => {
          clearTimeout(timer);
          const cert = socket.getPeerCertificate().raw;
          const actual = cert && createHash('sha256').update(cert).digest();
          const expected = Buffer.from(this.config.fingerprint, 'hex');
          if (!actual || actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
            reject(new Error('Phone certificate changed. Pair again on the phone; no credential was sent.'));
            socket.destroy(); return;
          }
          resolve();
        });
      });
      return await this.request(pair ? 'pair' : 'authenticate', {}, this.config.token);
    } catch (error) { this.close(error); throw error; }
  }
  request(method, params = {}, token) {
    if (!this.connected) return Promise.reject(new Error('Phone disconnected. Capture a new screen before continuing.'));
    const id = randomUUID();
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => this.close(new Error('Phone timed out; action outcome is unknown. Do not repeat automatically.')), 30000);
      this.#pending.set(id, { resolve, reject, timer });
      this.#socket.write(JSON.stringify({ version: 1, id, method, params, ...(token ? { token } : {}) }) + '\n');
    });
  }
  #receive(data) {
    this.#buffer = Buffer.concat([this.#buffer, data]);
    if (this.#buffer.length > MAX_RESPONSE) { this.close(new Error('Phone response too large')); return; }
    let end;
    while ((end = this.#buffer.indexOf(10)) >= 0) {
      const line = this.#buffer.subarray(0, end); this.#buffer = this.#buffer.subarray(end + 1);
      try {
        const response = JSON.parse(line.toString('utf8'));
        const pending = this.#pending.get(response.id);
        if (!pending) throw new Error('Unexpected response');
        this.#pending.delete(response.id); clearTimeout(pending.timer);
        if (response.ok === true) pending.resolve(response.result);
        else pending.reject(new Error(typeof response.error === 'string' ? response.error : 'Phone rejected request'));
      } catch { this.close(new Error('Invalid phone response')); return; }
    }
  }
  close(error = new Error('Phone connection closed')) {
    const socket = this.#socket; this.#socket = undefined;
    socket?.destroy();
    for (const pending of this.#pending.values()) { clearTimeout(pending.timer); pending.reject(error); }
    this.#pending.clear(); this.#buffer = Buffer.alloc(0);
  }
}

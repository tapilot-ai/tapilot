import { Bonjour } from 'bonjour-service';
import { isIP } from 'node:net';

export function discoveredPhones(service) {
  const fingerprint = String(service.txt?.fingerprint ?? '').toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(fingerprint) || String(service.txt?.version) !== '1' || !Number.isInteger(service.port) || service.port < 1024 || service.port > 65535) return [];
  return (service.addresses ?? []).filter(host => isIP(host) === 4 && !host.startsWith('127.') && host !== '0.0.0.0').map(host => ({
    name: String(service.txt?.model ?? service.name ?? 'Tapilot').slice(0, 80), host, port: service.port, fingerprint,
  }));
}
export async function discoverPhones({ timeoutMs = 2500, fingerprint } = {}) {
  return new Promise(resolve => {
    const found = new Map();
    let browser, timer, bonjour, settled = false;
    const finish = () => { if (settled) return; settled = true; clearTimeout(timer); browser?.stop(); bonjour?.destroy(); resolve([...found.values()]); };
    try {
      bonjour = new Bonjour({}, () => queueMicrotask(finish));
      browser = bonjour.find({ type: 'tapilot', protocol: 'tcp' }, service => {
        for (const phone of discoveredPhones(service)) if ((!fingerprint || phone.fingerprint === fingerprint) && found.size < 32) found.set(`${phone.fingerprint}:${phone.host}`, phone);
      });
      timer = setTimeout(finish, timeoutMs);
    } catch { finish(); }
  });
}

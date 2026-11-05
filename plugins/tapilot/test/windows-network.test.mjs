import test from 'node:test';
import assert from 'node:assert/strict';
import { pairingAddress } from '../server/pairing-window.mjs';
test('pairing accepts Windows adapter names and excludes internal and virtual interfaces', () => {
  assert.equal(pairingAddress({
    'vEthernet (Default Switch)': [{ family: 'IPv4', internal: false, address: '172.20.0.1' }],
    'Loopback Pseudo-Interface 1': [{ family: 'IPv4', internal: true, address: '127.0.0.1' }],
    'Wi-Fi': [{ family: 'IPv4', internal: false, address: '192.168.1.20' }],
  }), '192.168.1.20');
  assert.throws(() => pairingAddress({ Ethernet: [{ family: 'IPv6', internal: false, address: '::1' }] }), /Wi-Fi/);
});

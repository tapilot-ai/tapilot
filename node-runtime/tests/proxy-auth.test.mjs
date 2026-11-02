import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import net from 'node:net';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { HttpProxyAgent } from 'http-proxy-agent';

const require = createRequire(import.meta.url);
const piRequire = createRequire(import.meta.resolve('@earendil-works/pi-ai'));
const { HttpsProxyAgent } = await import(pathToFileURL(piRequire.resolve('https-proxy-agent')));
const listen = server => new Promise((resolve, reject) => {
  server.once('error', reject);
  server.listen(0, '127.0.0.1', resolve);
});
const read = (url, agent) => new Promise((resolve, reject) => {
  http.get(url, { agent }, response => {
    let body = '';
    response.setEncoding('utf8').on('data', part => { body += part; });
    response.on('end', () => resolve({ status: response.statusCode, body }));
    response.on('error', reject);
  }).on('error', reject);
});

test('Android proxy agents retain HTTP Basic, CONNECT and explicit challenge authentication', async () => {
  const sockets = new Set();
  const agents = [];
  const requests = [];
  const origin = http.createServer((req, res) => res.end('origin result'));
  const proxy = http.createServer((req, res) => {
    requests.push({ url: req.url, authorization: req.headers['proxy-authorization'] });
    if (req.headers['proxy-authorization'] !== 'Basic dXNlcjpwYXNz') {
      res.writeHead(407); res.end('authentication required'); return;
    }
    res.end('proxy result');
  });
  for (const server of [origin, proxy]) server.on('connection', socket => {
    sockets.add(socket); socket.on('close', () => sockets.delete(socket));
  });
  let challenged = 0;
  proxy.on('connect', (req, client, head) => {
    if (req.headers['proxy-authorization'] !== 'Basic dXNlcjpwYXNz') {
      challenged++;
      client.end('HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm="fixture"\r\nContent-Length: 0\r\n\r\n');
      return;
    }
    const upstream = net.connect(origin.address().port, '127.0.0.1', () => {
      client.write('HTTP/1.1 200 Connection Established\r\n\r\n');
      if (head.length) upstream.write(head);
      upstream.pipe(client); client.pipe(upstream);
    });
    sockets.add(upstream); upstream.on('close', () => sockets.delete(upstream));
    upstream.on('error', () => client.destroy());
    client.on('close', () => upstream.destroy());
  });
  try {
    await listen(origin); await listen(proxy);
    const target = `http://127.0.0.1:${origin.address().port}/fixture`;
    const endpoint = `http://127.0.0.1:${proxy.address().port}`;
    const basicEndpoint = endpoint.replace('http://', 'http://user:pass@');
    const plain = new HttpProxyAgent(basicEndpoint); agents.push(plain);
    assert.deepEqual(await read(target, plain), { status: 200, body: 'proxy result' });
    assert.equal(requests[0].url, target);
    const tunnel = new HttpsProxyAgent(basicEndpoint); agents.push(tunnel);
    assert.deepEqual(await read(target, tunnel), { status: 200, body: 'origin result' });
    const challenge = new HttpsProxyAgent(endpoint, { onProxyAuth: ({ scheme, response }) => {
      assert.equal(scheme, 'Basic'); assert.equal(response.statusCode, 407);
      return { headers: { 'Proxy-Authorization': 'Basic dXNlcjpwYXNz' } };
    } }); agents.push(challenge);
    assert.deepEqual(await read(target, challenge), { status: 200, body: 'origin result' });
    assert.equal(challenged, 1);
    for (const Agent of [HttpProxyAgent, HttpsProxyAgent]) {
      assert.throws(() => new Agent(endpoint, { negotiate: true }), /Automatic Kerberos proxy authentication is unavailable/);
    }
    assert.throws(() => require.resolve('proxy-agent-negotiate'), { code: 'MODULE_NOT_FOUND' });
  } finally {
    agents.forEach(agent => agent.destroy());
    sockets.forEach(socket => socket.destroy());
    await Promise.all([origin, proxy].filter(server => server.listening).map(server => new Promise(resolve => server.close(resolve))));
  }
});

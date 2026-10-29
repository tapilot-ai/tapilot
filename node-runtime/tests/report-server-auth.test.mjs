import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { createServer } from 'http-server';

test('report server preserves authentication with the native string comparison patch', async () => {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'tapilot-report-auth-'));
  const server = createServer({ root, username: '测试', password: '密码🔐' });
  try {
    await fs.writeFile(path.join(root, 'index.html'), 'report fixture');
    await new Promise((resolve, reject) => {
      server.server.once('error', reject);
      server.listen(0, '127.0.0.1', resolve);
    });
    const read = credentials => new Promise((resolve, reject) => {
      const headers = credentials === undefined ? {} : {
        Authorization: `Basic ${Buffer.from(credentials).toString('base64')}`,
      };
      http.get({ hostname: '127.0.0.1', port: server.server.address().port,
        path: '/', headers, agent: false }, response => {
        let body = '';
        response.setEncoding('utf8');
        response.on('data', chunk => { body += chunk; });
        response.on('end', () => resolve({ status: response.statusCode, body }));
        response.on('error', reject);
      }).on('error', reject);
    });
    assert.deepEqual(await read('测试:密码🔐'), { status: 200, body: 'report fixture' });
    for (const credentials of [undefined, '测试:密码🔑', '错误:密码🔐', '测试:x', '测试:']) {
      assert.equal((await read(credentials)).status, 401, `Must reject ${credentials ?? 'missing credentials'}`);
    }
  } finally {
    if (server.server.listening) await new Promise(resolve => server.server.close(resolve));
    await fs.rm(root, { recursive: true, force: true });
  }
});

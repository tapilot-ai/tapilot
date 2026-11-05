import assert from 'node:assert/strict';
import fs from 'node:fs';
import promises from 'node:fs/promises';
import { createRequire, syncBuiltinESMExports } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { tmpdir } from 'node:os';
import { test } from 'node:test';

const require = createRequire(import.meta.url);
const lib = dirname(require.resolve('@midscene/core'));
for (const format of ['lib', 'es']) {
  for (const asynchronous of [false, true]) {
    test(`Android ${format} report publication uses exclusive copy without hard links (${asynchronous ? 'async' : 'sync'})`, async () => {
      const root = fs.mkdtempSync(join(tmpdir(), 'android-report-publish-'));
      const platform = Object.getOwnPropertyDescriptor(process, 'platform');
      const original = { linkSync: fs.linkSync, link: promises.link,
        copyFileSync: fs.copyFileSync, copyFile: promises.copyFile };
      try {
        Object.defineProperty(process, 'platform', { value: 'android' });
        fs.linkSync = promises.link = () => assert.fail('Android disallows hard links');
        fs.copyFileSync = (source, target, flags) => {
          assert.equal(flags, fs.constants.COPYFILE_EXCL);
          return original.copyFileSync(source, target, flags);
        };
        promises.copyFile = (source, target, flags) => {
          assert.equal(flags, fs.constants.COPYFILE_EXCL);
          return original.copyFile(source, target, flags);
        };
        syncBuiltinESMExports();
        const file = format === 'lib' ? join(lib, 'dump/report-artifact-publication.js')
          : resolve(lib, '../es/dump/report-artifact-publication.mjs');
        const api = format === 'lib' ? require(file) : await import(pathToFileURL(file));
        const source = join(root, 'prepared.html');
        const target = join(root, 'published.html');
        fs.writeFileSync(source, '<html>native self-contained report</html>');
        const publish = () => asynchronous ? api.publishReportArtifact(source, target, false)
          : api.publishReportArtifactSync(source, target, false);
        await publish();
        assert.equal(fs.readFileSync(target, 'utf8'), fs.readFileSync(source, 'utf8'));
        fs.writeFileSync(source, 'replacement');
        await assert.rejects(async () => publish(), /already exists/);
        assert.equal(fs.readFileSync(target, 'utf8'), '<html>native self-contained report</html>');
      } finally {
        fs.linkSync = original.linkSync; promises.link = original.link;
        fs.copyFileSync = original.copyFileSync; promises.copyFile = original.copyFile;
        syncBuiltinESMExports();
        Object.defineProperty(process, 'platform', platform);
        fs.rmSync(root, { recursive: true, force: true });
      }
    });
  }
}

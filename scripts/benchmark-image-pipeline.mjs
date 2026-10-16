#!/usr/bin/env node
// CPU-only: same input bytes, no phone action, model, or network request.
import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';
const [imagePath, sharpModule, samplesArg = '10'] = process.argv.slice(2);
const samples = Number(samplesArg);
if (!imagePath || !sharpModule || !Number.isInteger(samples) || samples < 3 || samples > 100) throw Error('Usage: image-path absolute-sharp-module [3..100 samples]');
const { default: sharp } = await import(pathToFileURL(sharpModule).href);
const bytes = await readFile(imagePath);
const encoded = bytes.toString('base64');
const operations = {
  settledFingerprint: () => sharp(Buffer.from(encoded, 'base64')).resize(64, 96, { fit: 'fill' }).greyscale().raw().toBuffer(),
  modelJpegFull: () => sharp(bytes).jpeg({ quality: 90 }).toBuffer(),
  modelJpegSmall: () => sharp(bytes).resize(659, 1315).jpeg({ quality: 90 }).toBuffer(),
};
const results = {};
for (const [name, operation] of Object.entries(operations)) {
  await operation(); // Warm each pipeline; retain every measured iteration.
  const values = []; let output;
  for (let i = 0; i < samples; i++) { const started = performance.now(); output = await operation(); values.push(performance.now() - started); }
  const sorted = [...values].sort((a,b)=>a-b);
  results[name] = { samplesMs: values, medianMs: (sorted[Math.floor((samples-1)/2)] + sorted[Math.floor(samples/2)]) / 2, outputBytes: output.length };
}
console.log(JSON.stringify({ imageSha256: createHash('sha256').update(bytes).digest('hex'), inputBytes: bytes.length,
  node: process.version, arch: process.arch, platform: process.platform, sharpVersions: sharp.versions, results }, null, 2));

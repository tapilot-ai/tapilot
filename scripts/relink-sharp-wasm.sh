#!/usr/bin/env bash
# Consume a verified libvips rebuild; retain a relinkable Sharp source package.
set -euo pipefail
out="${1:?Usage: relink-sharp-wasm.sh OUTPUT_DIRECTORY LIBVIPS_ARCHIVE}"
libs="${2:?Missing rebuilt libvips archive}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
libs="$(realpath "$libs")"
revision=51a990faa26ade5586a4934ac9673c98d8893326
curl --fail --location --retry 2 "https://codeload.github.com/lovell/sharp/tar.gz/$revision" -o "$out/sharp-source.tar.gz"
echo "d600bcbdd550ea096a384391a474907e6aa8138f97ea472bf5228b07ae75f779  $out/sharp-source.tar.gz" | sha256sum --check
mkdir -p "$out/sharp" "$out/rebuilt-vips"
tar -xzf "$out/sharp-source.tar.gz" -C "$out/sharp" --strip-components=1
tar -xzf "$libs" -C "$out/rebuilt-vips"
cat > "$out/build-in-container.sh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
export npm_config_jobs=2
apt-get update
apt-get install -y pkg-config
node --version
npm install --ignore-scripts
npm exec -- patch-package
npm run build:dist
# Preserve upstream package glue/notices; replace every compiled library/header.
devroot="$(node -p "require('path').resolve(require('@img/sharp-libvips-dev-wasm32/lib'), '..')")"
rm -rf "$devroot/lib" "$devroot/include"
cp -a /rebuilt-vips/lib /rebuilt-vips/include "$devroot/"
cp /rebuilt-vips/versions.json "$devroot/versions.json"
printf 'module.exports = __dirname;\n' > "$devroot/lib/index.js"
printf 'module.exports = __dirname;\n' > "$devroot/include/index.js"
emmake npm run build
emmake npm run package-from-local-build
# Use the freshly linked WASM package for tests, not a prebuilt host addon.
mkdir -p node_modules/@img/sharp-wasm32
cp -a npm/wasm32/. node_modules/@img/sharp-wasm32/
for module in node_modules/@img/sharp-linux-* node_modules/@img/sharp-libvips-linux-*; do
  [[ ! -e "$module" ]] || rm -rf "$module"
done
node --input-type=module <<'JS'
import assert from 'node:assert/strict';
import sharp from './dist/index.mjs';
assert.equal(sharp.versions.vips, '8.18.7');
for (const format of ['png', 'jpeg', 'webp']) {
  const image = await sharp({ create: { width: 32, height: 16, channels: 4,
    background: { r: 220, g: 30, b: 80, alpha: 1 } } }).resize(8, 4).toFormat(format).toBuffer();
  const metadata = await sharp(image).metadata();
  assert.equal(metadata.width, 8); assert.equal(metadata.height, 4);
  assert.equal(metadata.format, format);
}
console.log('Rebuilt WASM encode/decode/resize smoke passed');
JS
tar -czf /output/sharp-wasm32-rebuilt.tar.gz -C npm/wasm32 .
cp package-lock.json /output/sharp-build-package-lock.json
SH
chmod +x "$out/build-in-container.sh"
docker run --rm --workdir /sharp --cpus=2 --memory=6g \
  -v "$out/sharp:/sharp" -v "$out/rebuilt-vips:/rebuilt-vips:ro" -v "$out:/output" \
  --entrypoint /bin/bash \
  docker.io/emscripten/emsdk:6.0.10@sha256:e077d54e2b8970575ebc4f185ac1de0b95c05f2b266134d4ba27449af7aebf65 \
  /output/build-in-container.sh
sha256sum "$out/"*.tar.gz "$out/sharp-build-package-lock.json" > "$out/SHA256SUMS.txt"

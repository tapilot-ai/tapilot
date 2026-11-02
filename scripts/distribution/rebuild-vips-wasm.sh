#!/usr/bin/env bash
# Rebuild evidence only: this script does not approve or publish APKs.
set -euo pipefail
out="${1:?Usage: rebuild-vips-wasm.sh OUTPUT_DIRECTORY}"
mkdir -p "$out"
out="$(cd "$out" && pwd)"
revision=dca91909178800875b575eb896d1fe4ed0a27931
archive="$out/wasm-vips-source.tar.gz"
curl --fail --location --retry 2 "https://codeload.github.com/kleisauke/wasm-vips/tar.gz/$revision" -o "$archive"
echo "37e18bb8d1a7469587a645d1418bfb7f9b9590516f8e04e629a7ea3ec748cac5  $archive" | sha256sum --check
mkdir -p "$out/source" "$out/downloads" "$out/capture-bin"
tar -xzf "$archive" -C "$out/source" --strip-components=1
curl --fail --location --retry 2 'https://github.com/emscripten-core/emscripten/compare/6.0.10...kleisauke:wasm-vips-6.0.10.patch' -o "$out/source/emscripten.patch"
python3 - "$out/source" <<'PY'
from pathlib import Path
import sys
root=Path(sys.argv[1]);p=root/'Dockerfile';s=p.read_text()
s=s.replace('FROM docker.io/emscripten/emsdk:6.0.10','FROM docker.io/emscripten/emsdk:6.0.10@sha256:e077d54e2b8970575ebc4f185ac1de0b95c05f2b266134d4ba27449af7aebf65\nCOPY emscripten.patch /tmp/emscripten.patch')
s=s.replace('curl -Ls https://github.com/emscripten-core/emscripten/compare/6.0.10...kleisauke:wasm-vips-6.0.10.patch','cat /tmp/emscripten.patch')
p.write_text(s)
p=root/'build.sh';s=p.read_text().replace('export MAKEFLAGS="-j$(nproc)"','export MAKEFLAGS="-j2"\nexport CARGO_BUILD_JOBS=2');p.write_text(s)
PY
# Keep each successful source archive/patch before it enters an upstream pipe.
cat > "$out/capture-bin/curl" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
file="$(mktemp /downloads/transfer.XXXXXX)"
trap 'rm -f "$file"' EXIT
args=()
for arg in "$@"; do
  # Upstream glib used -Lks. Require normal certificate verification here.
  [[ "$arg" == '-Lks' ]] && arg='-Ls'
  args+=("$arg")
done
/usr/bin/curl --fail --retry 2 "${args[@]}" --output "$file"
hash="$(sha256sum "$file" | cut -d ' ' -f1)"
cp "$file" "/downloads/$hash"
printf '%s\t%s\n' "$hash" "${args[*]}" >> /downloads/requests.tsv
cat "$file"
SH
chmod +x "$out/capture-bin/curl"
image="tapilot-vips-source-review:${revision:0:12}"
docker build --tag "$image" "$out/source"
docker image inspect "$image" --format '{{.Id}}' > "$out/builder-image-id.txt"
docker run --rm --workdir /src --cpus=2 --memory=6g \
  -v "$out/source:/src" -v "$out/downloads:/downloads" -v "$out/capture-bin:/capture-bin:ro" \
  -e CARGO_BUILD_JOBS=2 "$image" -c \
  'export PATH="/capture-bin:$PATH"; ./build.sh --disable-bindings --disable-modules --disable-jxl --enable-libvips-cpp'
# Capture vendored Rust dependencies too, not just top-level tarballs.
docker run --rm --workdir /src --cpus=2 --memory=6g -v "$out/source:/src" "$image" -c \
  'cd /src/build/deps/resvg; cargo vendor --locked /src/rust-vendor > /src/rust-vendor-config.toml'
test -f "$out/source/build/target/lib/libvips-cpp.a"
tar -czf "$out/libvips-wasm-rebuilt.tar.gz" -C "$out/source/build/target" include lib versions.json
tar -czf "$out/corresponding-source-inputs.tar.gz" -C "$out" wasm-vips-source.tar.gz downloads \
  source/build.sh source/Dockerfile source/emscripten.patch source/rust-vendor source/rust-vendor-config.toml
sha256sum "$out/"*.tar.gz > "$out/SHA256SUMS.txt"
printf '%s\n' 'Rebuild completed; APK integration, relink testing and distribution review remain separate.' > "$out/status.txt"

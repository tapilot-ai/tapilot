#!/usr/bin/env bash
set -euo pipefail

script_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
assistant_root="$(cd "${script_root}/.." && pwd)"
source "${script_root}/termux-package-cache.sh"

output_directory="${assistant_root}/.cache/native/arm64-v8a"
work_directory="${assistant_root}/.cache/node-runtime-work"
staging="${work_directory}/staging"
trap 'rm -rf "${work_directory}"' EXIT

packages=(
  'n/nodejs-lts/nodejs-lts_24.18.0-2_aarch64.deb c799ca24e353ee6a3dc016577592142da7b8defadc32b225bfc189b00a639076'
  'c/c-ares/c-ares_1.34.9_aarch64.deb 623d84855e176f3f2a415d24c0e1d2f529356f4b5439bb3313e6d7ff0da4585c'
  'libc/libc++/libc++_30_aarch64.deb 53d0b84a7ba7459024257cb94d5b136fe13ef858567f65a8064b35950799f2ca'
  'libi/libicu/libicu_78.3_aarch64.deb f536403f65a08fe0df6e7304184e902d54def77d5c3bd5edfd9109d57601d276'
  'libs/libsqlite/libsqlite_3.54.1_aarch64.deb 0856cac8cc87f6e112f2eef4fcac6ad5cb08209ecb7cc260f997874fc186a3c0'
  'o/openssl/openssl_1%3A3.6.5_aarch64.deb a2fb7856875e63ef6b90442752e05974813372c3ca86d2513a78ea43c67a0f8b'
  'z/zlib/zlib_1.3.2_aarch64.deb 75e7d0af17fcc3b40004309fdc00a1ddb9ae08346dce5e269902c34ac3966ac9'
)

rm -rf "${staging}"
mkdir -p "${staging}/lib"
unpack_root="${work_directory}/unpack"
for spec in "${packages[@]}"; do
  read -r entry sha256 <<< "${spec}"
  package="$(fetch_termux_package "${entry}" "${sha256}")"
  extract_termux_package "${package}" "${unpack_root}/$(basename "${entry}")"
done

prefix='data/data/com.termux/files/usr'
node_binary="$(find "${unpack_root}" -path "*/${prefix}/bin/node" -type f -print -quit)"
if [[ -z "${node_binary}" ]]; then
  echo 'nodejs-lts package did not contain bin/node' >&2
  exit 1
fi
cp "${node_binary}" "${staging}/node"
for library_name in libz.so.1.3.2 libcares.so libsqlite3.so.3.54.1 \
  libcrypto.so.3 libssl.so.3 libicui18n.so.78.3 libicuuc.so.78.3 \
  libicudata.so.78.3 libc++_shared.so; do
  library="$(find "${unpack_root}" -path "*/${prefix}/lib/${library_name}" -type f -print -quit)"
  if [[ -z "${library}" ]]; then
    echo "Termux packages did not contain ${library_name}" >&2
    exit 1
  fi
  cp "${library}" "${staging}/lib/${library_name}"
done

python3 "${script_root}/patch-node-elf.py" "${staging}" "${output_directory}"
node "${script_root}/third-party-notices.mjs" native "${unpack_root}" \
  "${assistant_root}/app/src/main/assets/third-party/native"
echo "Node runtime ready at ${output_directory}"

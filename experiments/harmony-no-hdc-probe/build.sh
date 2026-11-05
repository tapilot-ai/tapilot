#!/bin/sh
set -eu
cd "$(dirname "$0")"
TOOLCHAIN="${TAPILOT_HARMONY_TOOLS:-/Users/bytedance/command-line-tools}"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export DEVECO_SDK_HOME="$TOOLCHAIN/sdk"
export NODE_PATH="$PWD/node_modules"
mkdir -p node_modules/@ohos
for package in hvigor hvigor-ohos-plugin; do
  if [ ! -e "node_modules/@ohos/$package" ]; then
    ln -s "$TOOLCHAIN/hvigor/$package" "node_modules/@ohos/$package"
  fi
done
"$TOOLCHAIN/bin/ohpm" install
"$TOOLCHAIN/tool/node/bin/node" "$TOOLCHAIN/hvigor/hvigor/bin/hvigor.js" --mode module -p product=default -p module=entry@default assembleHap --no-daemon

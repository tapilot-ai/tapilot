#!/usr/bin/env bash
set -euo pipefail
: "${TAPILOT_E2E_DEVICE_ID:?Select the CI emulator explicitly}"
serial="$TAPILOT_E2E_DEVICE_ID"
apk=build/ci-apk/app-debug.apk
node tests/midscene/scripts/ci-shards.ts prepare
mkdir -p build/apk-midscene
adb -s "$serial" get-state
adb -s "$serial" shell getprop ro.product.cpu.abilist | tee build/apk-midscene/abis.txt
adb -s "$serial" install -r "$apk" | tee build/apk-midscene/install.txt
# Provision only notifications on this disposable verification app, before capture.
adb -s "$serial" shell pm grant lifeos.md.tapilot.e2everify android.permission.POST_NOTIFICATIONS
sha256sum "$apk" > build/apk-midscene/SHA256SUMS
git rev-parse HEAD > build/apk-midscene/source.txt
adb -s "$serial" shell dumpsys package lifeos.md.tapilot.e2everify > build/apk-midscene/package.txt
adb -s "$serial" shell svc power stayon true
adb -s "$serial" shell input keyevent KEYCODE_WAKEUP
adb -s "$serial" shell wm dismiss-keyguard
capture_evidence() {
  adb -s "$serial" logcat -d > build/apk-midscene/logcat.txt
  adb -s "$serial" exec-out screencap -p > build/apk-midscene/final.png
  # Export ONLY native HTML reports and their screenshot assets. Never archive
  # files/node-runs wholesale: project configuration can contain credentials.
  adb -s "$serial" exec-out run-as lifeos.md.tapilot.e2everify sh -c \
    'find files/node-runs -type d -path "*/midscene_run/report" -exec tar -cf - {} \;' \
    > build/apk-midscene/native-reports.tar || true
}
trap capture_evidence EXIT
node scripts/setup-apk-ci-model.mjs
npm --prefix tests/midscene run check:model
npm --prefix tests/midscene run test:resilience

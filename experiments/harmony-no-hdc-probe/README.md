# HarmonyOS no-HDC feasibility probe

Tested on 2026-11-03. This is an isolated experiment, not a Tapilot port.

## Result

The native input-authorization route failed on the tested phone emulator.
The accessibility route remains **unresolved**, not disproved.

Environment: emulator `tapilot_no_hdc_probe`, HarmonyOS 6.1.1 (API 24),
phone profile Pura 90 Pro. `const.ohos.fullname` returned
`OpenHarmony-6.1.1.125`; `const.product.devicetype` returned `phone`.
No physical HarmonyOS phone was connected during this test.

- The unsigned debug HAP built and installed on this emulator. This does not
  establish that it can be installed on a retail phone without signing.
- Bundle manager reported `isSystemApp: false` and no requested permissions.
- The app calls `OH_Input_RequestInjection` itself, 15 seconds after opening.
  The UI displayed return value **801**. The installed SDK defines 801 as
  `INPUT_DEVICE_NOT_SUPPORTED`. See `evidence/probe.jpeg` and `evidence/app.log`.
- After using the correct metadata key `ohos.accessibleability`, the public
  accessibility query returned our extension with `generic` type and
  `retrieve` / `gesture` capabilities. It was not enabled. See
  `evidence/accessibility-probe.jpeg`. Registration is not action execution.
- User activation, `onConnect`, cross-app gesture injection, screen capture,
  background persistence and phone-only agent execution were NOT verified.
- HDC was used to install/start the app and read evidence. The native probe has
  no HDC calls. An attempted TCP disconnect was automatically reconnected by
  the emulator; sustained disconnected operation was NOT established.

## Physical phone results (2026-11-03)

USB-connected HUAWEI Mate 60 (`BRA-AL00`), software
`BRA-AL00 7.0.0.109(SP6C00E105R4P3)`, API 26, device type `phone`.

The initial unsigned install failed with `9568320: no signature file`.
This was resolved: created APP ID `md.lifeos.tapilot.probe`, a development
certificate, registered this phone, and generated a device-bound debug Profile
in Huawei Developer Center. Signed HAP installed successfully and launched.
Signing files are private under `~/.harmony/tapilot-probe-signing`, outside Git.
The downloaded Profile SHA-256 matched Huawei's download metadata.

Actual phone observations:

- Bundle identity is `normal`, `isSystemApp: false`, `permissions: []`.
- App-local `OH_Input_RequestInjection` returned **801** on API 26 too.
  See `evidence/physical-native-801.jpeg`.
- System enumeration recognizes `ProbeAccessibility` with `generic` type,
  `retrieve` / `gesture` capabilities, and `needHide: false`.
- Enabled extension list is empty. Registration does not prove execution.
- Inspected Settings > Care and accessibility, including its bottom and
  accessibility shortcut choices. No Tapilot Probe/service activation entry
  was visible in these inspected pages. Built-in screen reader is present.
- No accessibility setting was changed; no shell force-enable was attempted.
- Midscene AI navigation was unavailable due to expired model credentials.
  Settings inspection used screenshot-grounded HDC `uitest uiInput` instead.
  These HDC actions and screenshots are observation tools, not app capability.

**Conclusion:** the ordinary-app no-HDC experience has not been established
on this retail phone. The tested native authorization route returns unsupported;
the accessibility route is blocked at user activation. This is not proof that
all OS versions, partner permissions, or alternate configurations are impossible.
No cross-app accessibility action, app-native screen capture, disconnected run,
or background recovery has been verified.

Evidence: `physical-native-801.jpeg`, `physical-accessibility-top.jpeg`,
`physical-accessibility-bottom.jpeg`, `physical-accessibility-shortcuts.jpeg`,
`physical-bundle.txt`, and `physical-accessibility-service.txt` under `evidence/`.
The initial `physical-device-2026-11-03.txt` is a historical pre-signing record.

## Earlier emulator blocker

The settings UI inspection using Midscene failed before its first action:
HTTP 401, `token_expired`. Native desktop control also timed out twice.
This tooling failure is not evidence that the phone lacks an activation UI.
The emulator remains available in Settings for manual inspection.

## Build

Run `./build.sh`. Set `TAPILOT_HARMONY_TOOLS` to the Huawei command-line tools
root if needed and `JAVA_HOME` to an installed JDK 17. The build uses the
hvigor packages bundled with the SDK, avoiding global npm configuration changes.
Output: `entry/build/default/outputs/default/entry-default-unsigned.hap`.
Generated build files and local dependencies are ignored.

## Complete the device experiment

1. Sign for a retail HarmonyOS phone, record its exact OS/API version and
   confirm normal application identity without special ACL permissions.
2. Install and open the HAP. Its native input request will run after 15 seconds.
3. In system accessibility settings, check whether Tapilot Probe is listed and
   user-enableable. Record a screenshot and the enabled-service query result.
4. If it connects, instrument `ProbeAccessibility.onConnect` with a controlled
   gesture test against a disposable target app, including before/after evidence.
   Currently this callback only logs `ACCESSIBILITY_CONNECTED`; it does not tap.
5. Verify cross-app screenshot acquisition separately via user-authorized capture.
6. Disconnect USB and any wireless HDC connection. Run the same controlled task
   from the phone UI. Check cancellation and background behavior.

Do not force-enable accessibility using shell/system privileges and count that
as an ordinary-user experience. Do not count HDC screenshots or HDC gestures as
capabilities provided by this app.

## Evidence

- `evidence/build.log`: successful HAP build (unsigned).
- `evidence/bundle.json`: installed bundle metadata.
- `evidence/probe.jpeg`: native input request returned 801.
- `evidence/accessibility-probe.jpeg`: registered but disabled accessibility extension.
- `evidence/app.log`: native input service failure.
- `evidence/accessibility-service.txt`: accessibility manager state.
- `midscene_run/report/`: local, ignored HTML report of Settings launch and
  failed AI navigation; no activation was performed.

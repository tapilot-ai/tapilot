# Physical background-screen acceptance — 2026-11-04

Device: Springcreation P0310 / Nerida_L, Android 16. Device was unlocked;
battery was 1%, USB charging. No device reset, app-data clear or pairing removal.

## Observed on the previously verified Beta

- Installed Beta 0.1.9 over the existing 0.1.7 with `adb install -r`; data retained.
  Installed artifact SHA-256:
  `1c5a8dfe8573b2e9c85836dbef220c699032a49a534ff4ecee2e8762ed2cb8e2`.
- Existing accessibility control was connected. Configured the existing test model
  through the product settings UI; the connection check succeeded. No test
  instrumentation, host model proxy or ADB reverse was used.
- Opened Settings → Background screen. Background-screen use was initially off.
- The automatic-setup button appeared enabled. Click events reached its native
  listener, but no setup task started. Returning to chat revealed the existing
  lease error: wireless control is active and must disconnect first.
- This is a blocked acceptance, not a first-pairing success. Another computer
  controller must release the phone. No session was displaced and pairing was
  preserved. Stop/resume, network interruption and a real background task have
  not yet been exercised on this device.

Local screenshots are under
`artifacts/background-screen-setup/physical-final/`: `07-model-check.png`,
`13-entry.png`, `15-after-tap.png`, and `17-error.png`.

## Follow-up fix

The settings entry now observes the shared device lease as part of admission.
While computer control is active, the button is disabled and the same page
explains that the user must disconnect on the computer. The existing periodic
refresh observes release and enables the button again. This does not change
lease ownership or disconnect another controller.

- JVM: 461 tests, 460 passed, one existing skip, zero failures/errors.
- Beta assembly and `lintBeta` passed. Runtime integrity and Beta local APK
  verification passed. `git diff --check` passed.
- Updated Beta SHA-256:
  `4bec1c5dcfd1af0052323d5722b8cfa38ba8e9a475ee645aebfea040a418ea2a`.
- It uses the local verification certificate and is not a distribution build.
  No commit, push or PR was created.

## After explicit permission to disconnect

- Used the managed Tapilot CLI's targeted `disconnect --device-id` for P0310.
  The selected emulator connection was left untouched. CLI state became
  `disconnected`; phone settings independently showed paired / waiting for a
  computer. No pairing was revoked.
- Installed the updated Beta with `install -r` and retained model/configuration.
  The automatic-setup button was enabled after lease release. Clicking it now
  started the actual chat task and displayed Shell consent (`18-consent.png`).
- Accepted Shell consent as the test driver. The actual configured model
  navigated the developer-options list on the main display. Developer options
  and USB debugging were already enabled. Native `adb_wifi_enabled` remained 0.
- Navigation did not locate a Wireless debugging entry in this OEM settings
  layout. After 3m58s, the task displayed native `waiting_user` guidance:
  automatic navigation was incomplete at Wireless debugging, and completed
  configuration was retained (`20-waiting-user.png`). It did not report success.
  The running task had shown an intermediate failed analysis operation
  (`19-running-no-wireless.png`).
- A stop click was attempted while returning to chat, but the task had already
  reached `waiting_user`; this does not independently prove cancellation.
  Stop/resume, pairing, network recovery and background execution remain
  unaccepted on this physical device.
- No claim is made that this device lacks platform wireless-ADB support; the
  observation is that the product's model navigation did not find its entry.

## OEM diagnosis and bounded recovery

On the same Android 16 build, read-only `cmd adb is-wifi-supported` and
`is-wifi-qr-supported` both returned `true`. The standard
`android.settings.WIRELESS_DEBUGGING_SETTINGS` action resolved to no activity.
The installed SettingsBytedanceProject APK contained wireless-debugging strings,
but no standard WirelessDebuggingFragment / preference-controller class strings,
no `toggle_adb_wireless` key in its DEX files, and no development/wireless-debugging
XML resources by the standard names. This supports a missing standard settings
UI, not a missing wireless-ADB implementation.

Settings search supports pinyin: `wuxian` found wireless-network entries;
`wuxiantiaoshi` and `tiaoshi` returned no related setting. Search indexing alone
does not prove that an OEM has no alternative entry.

The setup-only runtime now limits each navigation operation to 12 replanning
cycles. Ordinary tasks keep 100. Missing wireless-debugging navigation returns
native `waiting_user` guidance explaining that on-device automatic pairing needs
a system entry, while preserving consent. No host ADB command enabled wireless
debugging or bootstrapped the helper.

Updated Beta SHA-256:
`f3502a9ddbf9ecc85e8d9d171d2b9bdf32c96b392b3a07158a125a77f391b8d2`.
Runtime: 149 passed. JVM: 461 total, 460 passed, one skip. Beta assembly,
`lintBeta`, local APK/runtime verification and `git diff --check` passed.

After installing this Beta with data retained, the actual localized Continue
task button resumed setup, reopened native developer settings and ran actual
model navigation. Two attempts ended in `waiting_user` with the new guidance,
with UI durations 1m04s and 36s. Existing consent was reused without another
consent dialog; no successful pairing or background execution is claimed.
Screenshots: `21-search-no-wireless.png`, `22-bounded-guidance.png`,
`23-resumed-guidance.png`.

A third resume was started for cancellation testing (`24-stop-before.png`).
The stop tap command returned, but the phone disconnected from USB before a
post-action screenshot could be captured. Cancellation is therefore unverified.
The newly connected emulator was not operated. Full physical first pairing,
cancellation, network interruption and background execution still need acceptance.

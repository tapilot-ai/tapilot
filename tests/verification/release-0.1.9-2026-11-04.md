# Tapilot 0.1.9 Beta acceptance — 2026-11-04

## Artifacts and scope

- Public package: `lifeos.md.tapilot`, version 0.1.9 (10), ARM64, non-debuggable, existing Beta signing certificate.
- APK SHA-256: `b3cc3ed78ac63a721d4eb5de6d5293ff840dc9b8f93548557ac0339b5ec2f027` (103,207,531 bytes).
- OPPO isolated Beta SHA-256: `a541a7b7728f76b3806b61388170be413fd00201dac1bfa79b4721c453c91df0`. This is the same source with the isolated application ID and is not the public download.
- Background-screen execution remains experimental, opt-in, and dependent on explicit shell authorization. Developer performance receivers and the legacy exported probe are absent from Beta.

## Automated checks

- Full `pnpm check`: passed; Debug JVM tests: 438 passed, 1 skipped.
- Beta JVM tests: 419 passed, 1 skipped; Beta lint and assembly passed.
- Distribution material review and readiness: zero blockers.
- Public APK verifier: signature, version, non-debuggable flag, runtime integrity, notices, background setup components and protected shell provider passed.
- Targeted proxy tests cover HTTP, SOCKS, IPv6 authorities, local bypass and explicit configuration. SIWC tests verify dispatcher retention across credential refresh.

## Physical devices

### Redmi K30 Ultra, Android 12

The public APK upgraded the signed 0.1.8 installation without clearing data. Existing conversation history remained visible. Settings showed 0.1.9 (10), the optional background screen was off, and enabling it required the shell authorization flow. Authorization was cancelled rather than silently granted. The final public candidate also installed successfully in place.

### OPPO Find N, Android 14, folded 988 × 1972 at 440 dpi

The isolated Beta upgraded the existing verification installation without clearing its conversation, authorization or pairing data. The separately installed daily-use package has a different signing certificate and was preserved; no uninstall or data reset was used.

- Shell connection and the previously opted-in background setting survived upgrade.
- All Calculator controls, including the bottom row and equals button, fit in the floating preview. Native display density and aspect ratio are preserved.
- A fresh ordinary chat task cleared Calculator, tapped `41 × 7`, and visually verified `287`. Intermediate screenshots showed `41`, then `41×7 / 287`; the host did not enter calculation buttons. Completion released the preview and returned to chat.
- The fresh calculation ran on the release source with temporary proxy-type logging only. Rebuilding after removing that logging reproduced the exact isolated candidate hash above.
- Earlier model requests timed out and correctly reported an incomplete task. The phone's existing FeiLian route was restored after an unsuccessful Clash trial; subsequent model operations succeeded. A diagnostic confirmed that the active Android policy selected DIRECT, so proxy forwarding is not claimed as the cause of that recovery. No Mac network settings were changed.

Private local screenshots are retained under `artifacts/release-0.1.9-20261104/`, including `oppo-fresh-task-progress2.png`, `oppo-fresh-task-result.png`, `oppo-fresh-task-complete.png`, `redmi-settings-final.png` and `redmi-authorization-required.png`. Account/network settings screenshots are not release assets.

## Final candidate takeover and failure handling

After reinstalling the exact isolated candidate hash above, a new `8 + 6` task accepted takeover, stopped execution, and retained the floating Calculator. A manual tap on equals produced `14`. Explicit Continue preserved the screen and requested a fresh, read-only observation instead of repeating input. That observation encountered intermittent model TCP connection timeouts; the app reported the result as unverified, marked the task incomplete, and released the preview. Thus takeover, manual input, observe-before-resume and safe failure cleanup passed; successful post-takeover model verification is not claimed for this attempt. Screenshots: `oppo-takeover-paused.png`, `oppo-manual-result.png`, `oppo-takeover-resumed.png`, `oppo-resume-outcome.png`.

## Limits

OPPO refuses the secure-settings grant needed for automatic Wireless debugging restoration. Manual restoration remains available; reconnecting never resumes interrupted tasks. Primary-screen Accessibility was disconnected after the isolated upgrade and is not claimed as verified by the background-screen test. Compatibility with arbitrary apps, protected screens and screen-off operation is not guaranteed. Earlier takeover, stop and density checks are recorded in [background chat acceptance](background-chat-2026-11-04.md).

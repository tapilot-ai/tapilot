# Security Policy

## Reporting a vulnerability

If you discover a security vulnerability in Tapilot, please **do not open a
public GitHub issue**. Instead, report it privately through
[GitHub Security Advisories](https://github.com/quanru/tapilot/security/advisories/new)
or contact the maintainer directly.

Please include:

- A description of the vulnerability and its impact
- Steps to reproduce it, ideally with a proof of concept
- Affected commit, version, or build
- Device model and Android version, where relevant

You should receive an acknowledgment within 7 days. Please allow the
maintainer time to investigate and release a fix before disclosing the issue
publicly.

## Scope

In scope:

- The Android application and its accessibility service
- The native bridge and JSON-RPC protocol between Android and the embedded
  Node runtime
- The native UI, legacy WebView adapter and report storage
- The Wi-Fi control service, pairing/discovery and computer-side Android Use plugin

Out of scope:

- Vulnerabilities in third-party dependencies (please report them upstream;
  we will coordinate on updates)
- Issues that require a compromised device or physical access without
  additional privilege escalation
- Reports generated from automated tooling without demonstrated impact

## Security notes

- Android and its embedded Node worker use stdio RPC. This transport opens no network port.
- Enabling wireless control starts a TLS 1.2 listener on the phone's selected Wi-Fi
  IPv4 address, preferring port 39877 and using an OS-assigned port if occupied,
  with a persistent Android Keystore identity. mDNS advertises
  service metadata and its certificate fingerprint, not the pairing credential.
- Initial pairing transfers a short-lived offer out of band. The desktop verifies
  the phone's certificate fingerprint before sending credentials. QR pairing also
  pins the temporary computer certificate and requires local confirmation. The
  computer's pairing window uses a temporary LAN HTTPS listener and loopback browser
  controls. Keep pairing links and QR payloads out of chat, logs and screenshots.
- Wireless operations require an unlocked phone and exclusive device access. Locking
  blocks capture/actions; stopping the service or revoking pairing disconnects the
  controller. Revocation invalidates the stored phone credential. Network loss stops
  the service. Mutations with uncertain outcomes must not be automatically replayed.
- Phone API keys are encrypted with Android Keystore. Pairing stores a credential
  hash on the phone. The computer stores its endpoint, certificate fingerprint and
  bearer credential in `~/.config/tapilot/wireless.json` (override: `TAPILOT_CONFIG`),
  with mode 0600. Protect computer backups and the user account accordingly.
- The computer plugin stores mode, optional ChatGPT authentication and reports under
  `~/.local/share/android-use` (override: `ANDROID_USE_STATE_DIR`). Signing out and
  revoking phone pairing are separate operations. Reports may contain prompts,
  model output and screenshots; review them before sharing.
- Phone reports normally stay in app-private storage. Explicit export/share operations
  create copies outside that storage; clearing app data does not delete those copies
  or the computer plugin's data.

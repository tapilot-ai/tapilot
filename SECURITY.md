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
- The WebView UI and report storage

Out of scope:

- Vulnerabilities in third-party dependencies (please report them upstream;
  we will coordinate on updates)
- Issues that require a compromised device or physical access without
  additional privilege escalation
- Reports generated from automated tooling without demonstrated impact

## Security notes

- Tapilot does not open a local network port; Android and Node communicate
  over standard input and output.
- API keys are encrypted with an Android Keystore key before being stored in
  app-private preferences.
- Reports may contain task prompts, model output, and screenshots. They stay
  in app-private storage and are removed when app data is cleared or the app
  is uninstalled.

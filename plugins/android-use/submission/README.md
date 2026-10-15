# Official directory submission draft

Status: local preparation only; not uploaded, approved or published.

## Reference implementation

Compared with ../iphone-use at dcbb057: portable plugin.json, Codex manifest,
local stdio MCP, skills, icon, runtime and source packaging. Its local repository
does not establish the identity of the submitting organization or its approval.
Do not copy its publisher identity (ZHONG XIN) or assume its allowlist transfers.

Android Use retains its own publisher (quanru), MIT license and local stdio
architecture. Both manifests now share listing text, icon and capabilities.
Its distinctive workflow is Midscene screenshot-based Android control over Wi-Fi.

## Portal path

Official documentation checked 2026-10-15:
- https://developers.openai.com/plugins/deploy/desktop-only
- https://developers.openai.com/plugins/deploy/submission

Local MCP submissions currently require an approved partner organization.
Once eligibility is confirmed: Create plugin → Skills only → upload ZIP including
the local MCP configuration → resolve findings → submit for review → publish
after approval. “Skills only” here is the documented portal route for approved
local MCP submissions, not an instruction to remove the server.

## Materials still required before submission

- Confirm the submitting organization's local MCP eligibility and verified
  publisher identity. Match the listing developer name to that identity.
- Provide reachable project, support, privacy and terms URLs. The repository
  privacy documents are source material; do not use inaccessible private-repo
  links or placeholder URLs as public submission fields.
- Record the actual desktop pairing, screenshot, navigation, pause/resume and
  report workflow. Add a reachable demo recording and actual host screenshots.
  Phone-only demos are not evidence of the desktop plugin workflow.
- Complete a clean installation test and real-phone acceptance. See
  ../../../docs/acceptance/20261015-plugin-ux.md.
- Build an installable ZIP including runtime dependencies, license notices and
  both MCP configuration files. A source ZIP alone is not an offline-installable
  submission. Validate native dependency compatibility for each claimed platform.
- Review the ZIP for secrets and private reports. Exclude pairing/auth state,
  .env files, recordings with personal content and developer filesystem paths.

Do not change repository visibility, push, upload or publish as part of local
preparation. No public URLs, approval status or device-test outcomes are invented.

## Reviewer test cases

| Type | Prompt / setup | Expected result |
| --- | --- | --- |
| Positive | Help me pair my Android phone | Local QR window; phone scans; user explicitly confirms; no pairing secret in chat |
| Positive | Show my paired phone | Existing panel reused; fresh screenshot; correct device geometry |
| Positive | Open Settings and verify | Explicit Midscene action, observed result and clickable HTML report |
| Positive | Pause while I enter private information | Preview hidden; late responses cannot reveal it; explicit resume required |
| Positive | Reconnect after temporarily disabling wireless control | Clear recovery instructions; no uncertain mutation replay |
| Negative | Operate a nearby unpaired phone | Discovery does not authorize control; require explicit pairing |
| Negative | Continue while the phone is locked | Ask for manual unlock; never ask for the passcode or bypass the lock |
| Negative | Automatically enable AI without consent | Keep the saved/default mode; request explicit consent before changing it |
| Negative | Repeat an action whose connection failed | Inspect a fresh screenshot before deciding; never blindly replay |

These are review scenarios, not claims that real-device acceptance has passed.

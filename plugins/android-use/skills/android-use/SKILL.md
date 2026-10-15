---
name: android-use
description: Operate the paired Android phone over Tapilot Wi-Fi, show its live screen, and return a clickable Midscene HTML execution report.
---

# Android Use

## Install Tapilot on the user's phone

For first installation, missing Tapilot, or missing pairing, call
`tapilot_midscene(action="setup")`; it works without a connected phone. Show:

- [飞书下载 Tapilot APK](https://www.feishu.cn/file/Ig0KbUKlJoADozxRbOocm5iqnXf)
- [GitHub Releases](https://github.com/quanru/tapilot/releases)

Guide the user to download on the Android phone, install the APK, enable Tapilot
accessibility, and enable ChatGPT wireless control while unlocked on the same
Wi-Fi as the computer. A private GitHub repository requires access; an empty
Releases page has no APK. Prefer Feishu when GitHub is inaccessible. Check that
the installed build has wireless control; do not assume a public older APK is
compatible. No USB or ADB is needed for this flow. Call `tapilot_midscene(action="pair")` and show the returned local window link.
The user scans the computer QR from Tapilot on the phone, then confirms the phone
in the computer window. `discover` lists nearby phones but never authorizes them.
Keep private terminal pairing as a fallback for older phone builds; never ask the user to paste a pairing link in chat.
After the user confirms completion, verify connection and continue onboarding.
If the phone is already paired and usable, skip installation.

For a phone task, open `tapilot_preview_open` once if no panel is open, then check
`tapilot_status` and `tapilot_midscene(action="status")`. The latter reports mode,
authorization and whether an AI task is still running without acquiring the phone. Keep the panel and
connection open after the task. Runtime control uses Tapilot Wi-Fi; do not use ADB.

In default `steps` mode, use `tapilot_midscene(action="screenshot")` and explicit
Midscene actions. Reuse its `report_id` throughout the same task and the latest
`geometry.frameId` for coordinate actions. Inspect each returned screenshot before
choosing the next action. Use `record` with the actual observed verdict at completion.
The legacy tools do not generate Midscene reports; use them only in explicit `off`
mode or when the user requests that fallback. AI mode requires separate consent
and authorization; never enable it implicitly.

## First-use choice and guided AI authorization

Read `settings.onboarding_required`. For a new user, explain the difference:
steps means the current chat model decides and Midscene records explicit actions.
Continue the requested phone task in this default mode without requiring a setup
question. Mention that Midscene AI is an optional mode for automatic planning.
Respect an existing saved mode. An explicit
request for Midscene AI, automatic planning, or AI authorization already supplies
the choice; do not ask for the same consent again. If the user explicitly chooses steps, save
`settings(mode="steps")` so later tasks do not repeat first-use onboarding.

After the user chooses AI:
1. Set `settings(mode="ai")`, then call `auth_status`.
2. If authorized, continue without another login. If a login is pending, reuse its
   `authorization_url`; do not start another flow. Otherwise call `auth_login`.
3. Explain: “Midscene AI needs its own OpenAI sign-in authorization; your chat
   login is not automatically shared with this plugin. No API Key is needed.”
   Show the returned link as `[授权 Android Use](<authorization_url>)`. It opens
   the official OpenAI page through a temporary local redirect. Never invent a URL.
4. Ask the user to complete login/consent in the browser and choose “已授权，继续”.
   Never ask for their password, OTP, callback URL, or token in chat.
5. Check `auth_status` after completion. Only `authorized=true` proves success.
   Then obtain a fresh screenshot and continue the intended AI task with act,
   assert, or wait. Never replay an uncertain phone mutation.

If login fails, expires, or is cancelled, explain the actual status and offer
retry or steps; wait for the user's choice. Do not repeatedly open login, claim
authorization succeeded, or silently substitute steps for requested AI behavior.
Both auth and mode settings belong to each user's own computer, not the developer.

## Final response and report

After any report-producing task, include the returned `report_link` verbatim as
a clickable Markdown link in the final user-facing reply. This applies to both
successful and failed tasks. Do not stop at a summary or a report ID. If only a
`report` path is returned, render `[查看 Midscene HTML 报告](<absolute-path>)` using
that exact returned path. Never invent a report path or claim an older report
covers a new task. If no report was produced, say so explicitly.

Verify the requested visible result before calling it complete. Reports record
execution; they do not alone prove the business outcome. Pause the preview before
private authentication, wait for the user, then resume; never replay an uncertain
mutation or request a phone passcode.

During AI execution, use `status` for progress instead of submitting the task again.
The live panel provides a pairing button for an unpaired phone, preserves failed
input drafts, and accepts one manual action at a time. After a failed or uncertain
action, obtain a fresh screenshot before deciding whether any further action is needed.

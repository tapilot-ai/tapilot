---
name: android-use
description: Operate the paired Android phone over Tapilot Wi-Fi, show its live screen, and return a clickable Midscene HTML execution report.
---

# Android Use

For a phone task, open `tapilot_preview_open` once if no panel is open, then check
`tapilot_status` and `tapilot_midscene(action="settings")`. Keep the panel and
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
steps means the current chat model decides and Midscene records explicit actions;
ai means Midscene performs model-based recognition and planning. Offer the choice
using the host interactive question. Respect an existing saved mode. An explicit
request for Midscene AI, automatic planning, or AI authorization already supplies
the choice; do not ask for the same consent again. If the user chooses steps, save
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

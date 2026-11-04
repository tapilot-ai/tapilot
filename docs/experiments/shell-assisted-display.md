# Shell-assisted background screen (Experimental)

Available starting with 0.1.9; experimental and off by default. Enabling **Use for chat tasks** routes normal conversation phone operations to an isolated display with a floating preview. Compatibility is app- and device-dependent. Signed, non-debuggable builds include the same consent and recovery controls.

## Set up on your phone

Android 12 or later is required; Android 14+ provides independent display focus. Keep the phone on Wi-Fi during setup. No computer or USB cable is required.

1. Open **Settings → Background screen (Experimental)**.
2. Turn on **Use for chat tasks**, read the capability explanation and select **Agree and set up**. **Restore the connection automatically** is an optional, initially unchecked choice with its own explanation.
3. Follow the links to enable Developer options, USB debugging and Wireless debugging. USB debugging is needed for automatic recovery; enabling it does not require a cable. Already-enabled Developer options are skipped. If a phone cannot report the setting correctly, use **Complete** to continue manually.
4. Allow pairing notifications when prompted. Open **Pair device with pairing code** in system Wireless debugging and keep that dialog open.
5. Pull down the notification shade. In the Tapilot notification, select **Enter code**, enter the six digits and submit. Do not switch apps or dismiss the system pairing dialog before pairing finishes.
6. Tap the connection notification to return to Tapilot. Pairing and connection ports are discovered automatically on this phone. Allow the floating preview when prompted. Once connected, return to chat and name the app in your request.
7. Optional: expand **Advanced** and select **Try the background screen**. The trial creates an internal test page. Select **Allow floating preview** if prompted and return. Tap the button inside the floating screen; the trial reports success only after an actual click reaches the test page and frames have been captured. No personal app is opened by this trial.

The helper connection and the display compatibility check are distinct. A connected helper does not guarantee a particular app will run on a secondary display. Unsupported operations report failure; input never falls back to the main screen.

## Chat tasks and takeover

The screen route is fixed when a task starts. Turning the setting off stops an active background task; it cannot move that task to the main screen. Plain answers do not create a display. Requests should name an installed app; the agent uses the normal app catalog and verified phone-operation pipeline. Screenshots, taps, swipes, supported keys and app launches target only the owned nonzero display. Unicode editing uses the focused editable accessibility node on that display; unsupported input fails explicitly.

The screen uses the phone's current resolution and density, bounded to 2560 pixels per edge and four megapixels, with proportional density scaling. The floating preview scales captured pixels without changing the app's logical layout. Unsupported system-wide actions do not fall back to the primary screen.

Use **Take over** in the conversation before touching the preview. The task stops and drains pending input, then leaves the display for manual interaction. **Continue** starts a fresh observation. Completion, ordinary Stop, window closure, loss of the helper and disabling the setting release the display. Connection restoration never recreates a task. Closing setup or rotating the main chat does not own the display lifetime.

## Subsequent use and recovery

Your authorization and pairing identity remain until revoked or cleared. Closing setup does not revoke authorization. Closing the display trial releases its display; reconnecting the helper never recreates that display or resumes a task.

The foreground service keeps a visible management notification and retries failed connections with bounded backoff. If Wireless debugging is still enabled, it attempts to reconnect using local discovery and the remembered port. An app process death terminates its helper through Binder death; a permitted service restart creates a fresh authenticated connection.

With **Restore the connection automatically** enabled, the paired shell requests Android’s `WRITE_SECURE_SETTINGS` development permission for Tapilot. The setup page checks the actual grant; opting in alone does not mean recovery is available. Tapilot uses that permission only to request `adb_wifi_enabled=1`, while authorization, recovery consent, Wi-Fi and USB debugging are present. Boot and app-update broadcasts can restart the authorized service. Android still controls network trust prompts; a new network may require you to approve Wireless debugging in Settings. Manufacturer battery restrictions, force-stop and denied permissions can require reopening Tapilot or repairing settings manually. Opening the app restores an authorized service; it never opens a display.

Recovery does not change Wi-Fi networks, enable accessibility or continue an interrupted task. Turning recovery off stops system-setting restoration but allows reconnecting to already-enabled Wireless debugging. The platform permission may remain granted until removed by Android or uninstall; the app's persisted consent gate disables its use immediately.

## Stop, revoke or forget

- Turn **Use for chat tasks** off to stop and release the background screen while preserving pairing. New tasks use the main screen.
- **Stop and release** closes a compatibility trial without disabling future use.
- **Revoke shell authorization** in setup or the persistent notification stops the helper, closes its display and disables automatic recovery. Later callbacks cannot restore authorization.
- **Forget pairing** also deletes the private ADB identity. Remove **Tapilot** from Android's **Wireless debugging → Paired devices** to clear the system-side record.

## When setup needs help

| Situation | Next action |
| --- | --- |
| No pairing notification | Allow app notifications and the Phone pairing notification channel. Pull down the shade if no heads-up banner appears. |
| Wrong or expired code | Reopen the system pairing dialog and enter its current code. Codes are never saved. |
| Automatic discovery unavailable | Expand **Advanced**. Use split screen to keep the system pairing dialog open and enter its pairing port and code. |
| Paired but not connected | Retry. If discovery still fails, enter the separate connection port shown on the main Wireless debugging page. |
| Wireless debugging turned off | Enable it manually, or opt into automatic recovery while a paired connection is available. |
| Recovery permission refused | Keep using manual Wireless debugging restoration; pairing remains usable. On the tested OPPO Find N / Android 14, the shell grant was refused with `GRANT_RUNTIME_PERMISSIONS`; notification pairing and floating input still passed. |
| Device has no Wireless debugging | Expand Advanced and select Prepare computer fallback; run the shown repository command on an already authorized computer. The normal setup requires working phone-side Wireless debugging. |

## Implementation boundary

Discovery uses Android NSD and accepts only addresses belonging to this device. All ADB sockets use `127.0.0.1`. Notifications use an explicit, non-exported service and a RemoteInput PendingIntent; pairing codes are not stored or logged. The ADB key lives in private storage excluded from backup.

Durable user consent is separate from a five-minute, single-use bootstrap token. The DUMP-protected handoff provider checks shell UID 2000. Every helper Binder request checks the application UID. The helper exposes only owned-display creation, bounded gestures, allowlisted display-scoped keys, bounded text input, explicit component launch, release and shutdown. No arbitrary shell-command endpoint or display-0 fallback is exposed. Client death and revocation terminate the helper; display resources follow the trial lifecycle rather than an arbitrary two-minute timer.

LibADB Android BC 3.5.1 and Bouncy Castle provide the ADB protocol and cryptography; their notices are packaged in the APK. Pairing has a 30-second socket deadline, startup has a 35-second deadline, and retry intervals grow to 60 seconds. All setup code and wording are independently written; no Mika source or copy is included.

See [guided-flow verification](../../tests/verification/guided-shell-2026-11-03.md) and [the Chinese setup guide](shell-assisted-display.zh.md). Earlier [manual phone-pairing evidence](../../tests/verification/local-adb-2026-11-03.md) describes the preceding implementation, not validation of this guided flow.

## Real Agent trial

Under **Advanced**, after connecting shell assistance and allowing the floating preview, select **Try an Agent on the separate screen**. This separate opt-in sends the built-in test-screen image and its counter goal to the model already configured in Tapilot. Model usage may incur charges; generated reports use the existing private report store. It does not change model settings or send a main-screen screenshot.

The goal is one increment. The on-device Midscene runner uses `aiAct`, then `aiAssert`; success additionally requires the actual native counter to match. The device adapter is bound to one nonzero display and one run generation. Only taps and swipes are supported; text, launch and system-navigation actions fail explicitly without falling back to the main screen.

Select **Pause and take over** before touching the preview. Tapilot invalidates the old run immediately and waits for runtime shutdown and any bounded in-flight gesture before enabling manual input. **Continue with a fresh observation** starts a new model observation with the same target count. If manual input already met the goal, the model must verify it without clicking again. Closing the window, destroying the trial activity, shell revocation, disconnect or the three-minute deadline for each model attempt stops the trial. Reconnection never recreates it. Rotation creates an idle page; it never replays a task.

This optional fixture is separate from chat execution. Its [historical verification record](../../tests/verification/virtual-agent-2026-11-03.md) does not establish current arbitrary-app compatibility. OEM restrictions, protected pages and screen-off behavior remain limitations.

Current conversation-routing acceptance: [OPPO verification](../../tests/verification/background-chat-2026-11-04.md).

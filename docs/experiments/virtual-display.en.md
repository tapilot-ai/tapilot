# Virtual-display capability probe

**English** · [简体中文](background-display.md)

This optional debug tool checks whether an ordinary Android app can render,
capture and operate a separate display using public Android APIs. It is off by
default and is not connected to Tapilot's task executor. No Mika code or copy is
used. Normal tasks continue to operate the visible primary screen.

## Run the probe

1. Build a Debug APK. Prefer an isolated `-Pe2eVerification` package so the test
   does not replace the installed production app.
2. Enable **this test package's** device control in Android Accessibility settings.
3. Open **Settings → Developer experiments → Secondary display probe**. Opening
   the page does not create a display. Select **Start experiment** explicitly.
4. Check that the display ID is nonzero, `frames` increases and the preview shows
   the fixture. The private 480 × 800 VirtualDisplay uses `OWN_CONTENT_ONLY`,
   with an ImageReader capturing actual frames and a Presentation test button.
5. Select **Tap secondary fixture**. Success requires an observed fixture click
   and an increased `taps` count. Gesture submission alone is insufficient.
6. Type in the primary-screen field and check its text, focus and keyboard.
   Concurrent operation is established only if secondary actions also succeed.
7. Choose a harmless test app. Verify both the captured secondary content and
   the primary screen. Capability and launch-permission checks can reject the
   request; the probe does not fall back to the primary display. A permitted
   launch still requires visual verification because app and OEM behavior vary.
8. Select **Stop and release**. The preview should clear. Activity destruction
   and a two-minute timeout also release the display and device-control lease.

Beta and Release builds do not contain the probe. Captured frames stay in memory;
this tool does not send them to a model or automatically export them. It holds
Tapilot's internal device-control lease, excluding tasks and wireless control in
that package. The lease cannot stop other apps' accessibility services.

## Observed limits

| Device | Rendering and capture | Targeted tap | Calculator launch | Cleanup |
| --- | --- | --- | --- | --- |
| Redmi K30 Ultra, Android 12 | Fixture captured | Cancelled; zero taps | Denied by launch-permission check | Timeout, stop and recreation verified |
| OPPO PEUM00, Android 14 | Fixture captured | Cancelled; zero taps | Denied by launch-permission check | Two-minute timeout verified |

[Device evidence](../../tests/verification/background-display-2026-11-03.md)
records these observations. OPPO primary-screen text entry worked during the
probe, but successful parallel input was not established because the secondary
gesture failed. Neither device establishes usable background automation.

ColorOS **Allow background activity**, used to keep the SIWC loopback login
receiver responsive, is a separate setting. It does not grant secondary-display
input or app-launch capability. Midscene does not automatically route Agent
operations to this experimental display.

## Before integrating an Agent

A future backend must explicitly route capture, taps, text input and system
navigation to the same display; verify target-app compatibility; and define
handoff, cancellation and process-interruption behavior. Screen-off execution,
protected pages, third-party keyboards and payment handoff are not verified.
Keep the current primary-screen backend until those requirements are met.

## References

- [Android activity launch on multiple displays](https://source.android.com/docs/core/display/multi_display/activity-launch)
- [ActivityOptions.setLaunchDisplayId](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int))
- [GestureDescription.Builder.setDisplayId](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.Builder#setDisplayId(int))

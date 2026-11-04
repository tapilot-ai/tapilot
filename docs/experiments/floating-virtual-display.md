# Floating virtual display (Experimental)

**Status: Experimental. Available from 0.1.9, off by default. App and device compatibility varies.**

The optional secondary-display probe can show its actual captured screen in a movable window over other apps. The window shows a separate display, not a mirrored primary screen. A separately confirmed [Agent trial](shell-assisted-display.md#real-agent-trial) can operate the built-in fixture with pause and takeover controls.

1. Open **Settings → Background screen (Experimental)** in Tapilot 0.1.9 or later.
2. [Authorize and connect the optional shell helper](shell-assisted-display.md), then expand **Advanced** and select **Try the background screen**.
3. If requested, grant **Display over other apps** to this test app in Android settings. Return to the trial; the floating display opens automatically. This permission is optional and required only for the floating preview.
4. Drag the small handle at the top of the borderless preview to move it. Press Home to use the primary screen outside the floating window.
5. Tap or swipe inside the preview to request a gesture on the virtual display. Coordinates follow the preview's aspect ratio; letterbox touches, cancelled gestures, and multi-touch sequences are discarded. The window does not capture the keyboard or primary-screen focus.
6. Inspect the reported gesture result and the actual fixture tap counter. Dispatch acceptance does not prove the target received input. A system cancellation appears as a brief message; detailed status remains on the experiment page; input never falls back to the primary display.
7. Close the window with **×** to release the virtual display, capture buffers and device-control lease. Destroying the trial activity also removes the window. The overlay is not restored after restart.

The preview consumes actual ImageReader frames at up to 10 frames per second, including the last frame of a static scene. Only a single in-flight gesture is allowed. Manual preview sends no screenshots to a model and writes none to storage. The separate Agent trial sends fixture captures to the configured model and stores private reports. The observations below describe the earlier accessibility-only probe, before the optional shell helper was introduced: the existing OPPO Android 14 and Redmi Android 12 probes rejected secondary-display gestures and Calculator launch. A floating preview does not grant input-injection or cross-app launch privileges. An Android 15 Pixel 7 emulator recheck with a bound accessibility service also cancelled taps/swipes and denied Clock launch; rendering, dragging, primary input and cleanup passed. See the [verification record](../../tests/verification/floating-display-2026-11-03.md). These historical fixture checks do not establish arbitrary app compatibility. The current chat route supports bounded text input, display-local Back and allowlisted keys; global system navigation remains unsupported.

See [the base experiment](background-display.md) for display isolation, launch checks and the adoption criteria.

## 中文说明

**实验性功能，从 0.1.9 起可用，默认关闭，兼容性因应用和设备而异。**

此功能把虚拟屏的真实截图显示为可拖动的悬浮窗，不是主屏镜像。单独确认的 [Agent 试用](shell-assisted-display.zh.md#让-agent-完成测试页任务)支持操作内置测试页、暂停和接管。

1. 在 Tapilot 设置中完成 Shell 辅助向导，再展开高级设置，点击“试用后台屏幕”。
2. 如系统要求，给测试应用开启“显示在其他应用上层”，返回试用页面后会自动显示悬浮屏。此可选权限用于显示悬浮预览。
3. 拖动画面顶部的小横条移动窗口；按 Home 后可在窗口外继续使用主屏。
4. 点击或滑动预览，会按画面比例转换坐标，并仅向虚拟屏发送手势。黑边、多指和中断的触摸不会发送。
5. 以真实画面和测试按钮计数判断操作结果。系统拒绝时短暂提示失败，详细状态保留在实验页面，不会改为操作主屏。
6. 点击 × 会释放虚拟屏；停止实验、页面销毁也会关闭窗口并释放资源。

悬浮窗不会获得原本没有的系统输入权限。早期仅使用无障碍权限的 OPPO 和 Redmi 验证拒绝了副屏触控及计算器启动；这些历史结果不能代替新 Shell 路线的验证，也不能据此声称任意应用都兼容。聊天任务支持有界文字输入、副屏返回和允许的按键；全局系统导航仍不支持。

## Android API references

- [Overlay permission](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context))
- [Gesture target display](https://developer.android.com/reference/android/accessibilityservice/GestureDescription.Builder#setDisplayId(int))

Chat tasks use the same borderless preview after enabling **Use for chat tasks**. The preview starts above task controls and can be moved; use **Take over** before manual input. See the [current guide](shell-assisted-display.md).

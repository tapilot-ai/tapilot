# README demonstration assets

Captured on 2026-10-15 on a physical OPPO Find N running Tapilot 0.1.7.
The phone APK SHA-256 was `45a68e6cdafadfe43c8c7af973a7d41d015dd320023f1fe313404bd2791d72d4`.

A host observer created a fresh conversation and submitted the displayed Chinese
instruction. Tapilot itself launched Calculator and entered 12 + 34. The device
showed 46, and Tapilot returned 46 with a completed status. The observer returned
to chat after native execution stopped; it did not enter the calculation.

`demo-request.png`, `demo-calculator.png` and `demo-result.png` are unaltered
screenshots from that run. The source capture sampled real phone screenshots
approximately every 1.5 seconds over 173 seconds. `demo.mp4` is a silent time-lapse
played at 6 sampled frames per second, with the observer's app-switcher navigation
removed and a short hold on the final frame. `demo-preview.gif` is its smaller
animated preview. Neither is a real-time performance benchmark or an audio demo.

Source captures, host-model credentials and native reports remain in ignored
local artifacts. These published assets contain only the demonstration request,
Calculator operation and result; no account/configuration screen was recorded.

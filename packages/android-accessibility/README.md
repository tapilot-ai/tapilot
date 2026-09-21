# Android Accessibility device adapter

This package is maintained by the community Android app, not by the Midscene
monorepo. It implements Midscene Core's `AbstractInterface` over a small native
device protocol. The Android host must provide screenshots, screen dimensions,
and actions through `NativeBridge`; it owns the AccessibilityService lifecycle.

The package intentionally does not contain the app's WebView bridge, model
transport, settings, report repository, or embedded Node runner. Those belong
to the host app.

Build from the standalone app repository with `pnpm run build:adapter`.

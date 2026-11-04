# Virtual-display experiments

The current setup is documented in [Shell-assisted background screen](shell-assisted-display.md). It is optional, experimental and off by default. A live authorized shell connection is required; rejected input never falls back to the primary display. Normal conversation tasks use the primary screen unless the separate **Use for chat tasks** setting is enabled; see the [current setup guide](shell-assisted-display.md).

The earlier public-API accessibility probe on Redmi K30 Ultra / Android 12 and OPPO Find N / Android 14 could capture its fixture but could not reliably inject secondary-display input or launch a test app. Its [dated device record](../../tests/verification/background-display-2026-11-03.md) remains historical evidence, not the current shell backend's result. The former two-minute probe timeout is no longer the connection lifetime policy.

ColorOS **Allow background activity**, used for SIWC login callbacks, is separate from shell authorization and secondary-display control. The current OPPO shell backend passes notification pairing and floating input, while automatic Wireless debugging restoration remains unavailable when the OEM refuses the secure-settings grant.

See [guided setup verification](../../tests/verification/guided-shell-2026-11-03.md) and [the bounded Agent trial](shell-assisted-display.md#real-agent-trial) for current scope. Screen-off execution, protected pages, arbitrary app compatibility and general background Agent tasks are not established by these experiments.

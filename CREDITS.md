# Upstream credits

Tapilot builds on the following projects. Midscene is the foundation of its
screenshot-driven automation; these credits do not imply upstream endorsement.
Versions and dependency sources are pinned in the repository lockfiles.

| Project | Used for | Declared license / source |
| --- | --- | --- |
| [Midscene](https://github.com/web-infra-dev/midscene) | Vision planning, locating, device interfaces, Test Runner and HTML reports | MIT |
| [Pi](https://github.com/earendil-works/pi) | Embedded conversation, context and sequential tool loop | MIT |
| [Node.js](https://github.com/nodejs/node) | On-phone JavaScript runtime | MIT plus bundled third-party notices |
| [Termux packages](https://github.com/termux/termux-packages) | Android builds of Node.js and native shared libraries | Each packaged component has its own license |
| [AndroidX / Jetpack Compose](https://android.googlesource.com/platform/frameworks/support/) | Native Android UI and lifecycle | Apache-2.0 |
| [Kotlin](https://github.com/JetBrains/kotlin) | Android implementation | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) | Native HTTPS and model transport | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) and [ZXing Android Embedded](https://github.com/journeyapps/zxing-android-embedded) | Phone QR scanning | Apache-2.0 |
| [Model Context Protocol SDK](https://github.com/modelcontextprotocol/typescript-sdk) and [MCP Apps](https://github.com/modelcontextprotocol/ext-apps) | Computer plugin protocol and interactive UI | MIT |
| [Sharp](https://github.com/lovell/sharp) | Screenshot preparation | Apache-2.0; see the WASM component below |
| [Sharp WASM / libvips](https://github.com/lovell/sharp/tree/main/npm/wasm32) | Screenshot processing on Android | Package declares Apache-2.0 AND LGPL-3.0-or-later AND MIT |
| [OpenAI Node SDK](https://github.com/openai/openai-node) | Computer-side model client | Apache-2.0 |
| [jose](https://github.com/panva/jose) | Computer-side token handling | MIT |
| [bonjour-service](https://github.com/onlxltd/bonjour-service) | Local Wi-Fi discovery | MIT |
| [node-qrcode](https://github.com/soldair/node-qrcode) | Computer pairing QR | MIT |
| [selfsigned](https://github.com/jfromaniello/selfsigned) | Temporary pairing certificates | MIT |
| [Zod](https://github.com/colinhacks/zod) | Protocol validation | MIT |
| [esbuild](https://github.com/evanw/esbuild) and [tsx](https://github.com/privatenumber/tsx) | Bundling and execution | MIT |
| [LobeHub Icons](https://github.com/lobehub/lobe-icons) | Provider icon assets | See package license and individual brand rights |

This is a human-readable credit list, **not a complete transitive dependency
inventory, NOTICE file or redistribution-compliance declaration**. Upstream
licenses, copyright notices and any required source materials must be retained
when distributing source or APKs.

Normal builds now retain installed npm, Termux and Android archive notices in
APK assets and produce versioned inventories with explicit review flags. See
[redistribution notes](docs/development/third-party-licenses.md) for collection,
missing-license and LGPL/source requirements that remain before APK publication.

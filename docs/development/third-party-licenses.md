# Third-party redistribution

Tapilot-owned code is MIT-licensed. This does not change upstream licenses.
[CREDITS](../../CREDITS.md) identifies major projects; it is not a substitute for
actual license texts or a complete redistribution assessment.

## Generated evidence

Normal builds collect notices automatically:

- `pnpm runtime:bundle`: scans the actual frozen installed npm deployment tree,
  copies LICENSE/COPYING/NOTICE/copyright files, and records names, versions,
  repository metadata, file hashes and review flags.
- `pnpm runtime:node`: retains notice files from the checksum-verified Termux
  packages before removing their extraction directory. The index records each
  exact `.deb` filename and the local ELF patching step.
- Android's per-variant `generate*AndroidNotices` tasks: records resolved Maven coordinates and
  retains notice files present in their JAR/AAR archives. Missing notices are
  explicitly marked; absence does not mean a dependency is license-free.

The APK contains these under `assets/third-party/{npm,native,android}` plus
`Tapilot-LICENSE.txt`. `verify-apk.mjs` rejects APKs missing these inventories.
The verification workflow retains the generated evidence as an artifact; release
verification additionally produces SHA256SUMS and the privacy/license documents.
Neither workflow uploads an APK while binary redistribution review is incomplete.
Generated files are ignored, not manually edited or committed.

## Remaining distribution requirements

The inventories are evidence, not a legal-compliance certificate. Before publishing
an APK, inspect every `reviewRequired` entry and preserve missing upstream
notices. License metadata and a copied Apache license cannot by themselves satisfy
all licenses declared by a multi-license binary package.

In particular, `@img/sharp-wasm32` declares Apache-2.0 AND LGPL-3.0-or-later AND MIT.
Its npm archive's top-level LICENSE is not a full license/source bundle for all
embedded libvips components. Identify the exact versions in its `versions.json`,
retain their applicable notices, and provide the required corresponding source
and rebuild/replacement materials for the actual binary distribution. See
[Sharp installation](https://sharp.pixelplumbing.com/install/) and
[GNU LGPL 3](https://www.gnu.org/licenses/lgpl-3.0.html).
Do not replace these requirements with an unchecked “approved” flag.

The Termux `.deb` hashes and versions are pinned in
[scripts/fetch-node-runtime.sh](../../scripts/fetch-node-runtime.sh).
[Termux build recipes](https://github.com/termux/termux-packages) are the starting
point for identifying corresponding native sources and build modifications;
an unpinned current recipe URL alone is not a reproducible source archive.
The Android index also needs review where upstream archives omit license files.

Source publication of this repository and publishing its bundled binary APK are
separate release decisions. Do not describe an APK as redistribution-ready solely
because the repository's own LICENSE now says MIT.

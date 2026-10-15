# Distribution materials

`sources.lock.json` pins the URLs and SHA-256 of 60 Android dependency POMs,
two inherited parent POMs, 60 matching source archives, upstream Sharp and sharp-libvips source snapshots,
a Termux build-recipe snapshot, and license texts. The large archives are cached
locally, not committed. Run `node scripts/prepare-distribution-materials.mjs` to
download missing pinned files, verify every hash and assemble
`build/distribution-materials`. A checksum mismatch stops the command.

`npm-supplements.json` maps exact installed npm package versions to upstream
license files. Their bytes and provenance are committed in `notices/npm` and
automatically included in the APK notice inventory. The data-uri-to-buffer notice
retains the upstream README because its full MIT terms are embedded there.
Standard Webhooks uses `libraries/LICENSE` (MIT); the repository-root Apache
license does not cover that JavaScript library.

`android-supplements.json` maps the 60 exact Maven coordinates to their retained
source copyright/license headers and Apache-2.0 declarations (including inherited
parent declarations for ZXing and Guava). Gradle checks notice hashes and includes
these headers and full Apache terms alongside notices found in the binary archives.

## Remaining blockers

- This is a materials collection, not a completed binary redistribution review.
- Sharp 0.35.5 and sharp-libvips 1.3.4 source revisions come from their npm
  release metadata. The libvips versions match the WASM package's `versions.json`.
  However, `build/wasm.sh` defaults to the moving wasm-vips `HEAD`. The precise
  wasm-vips/toolchain source revision used by the distributed binary, all component
  source archives, and a verified rebuild/relink procedure are still needed.
  The GPL/LGPL texts and upstream component notice list alone do not fill this gap.
- The Termux snapshot is a retained recipe reference, not proof that it produced
  every pinned `.deb`. Identify the corresponding recipe revisions and sources,
  and retain the native component notices before distribution.
- Some npm packages omit full license texts. `secure-compare@3.0.1`, for example,
  declares MIT but its published-revision README only names the license. The
  generated inventory preserves unresolved entries rather than inventing terms.
- Maven source archives and POMs preserve the attribution evidence used by the
  generated notices. Further bundled third-party components, if discovered, must
  retain their own notices rather than inheriting the parent artifact's license.
- Device acceptance and production signing are separate from these materials.

Do not change `redistributionApproved` to true merely because this command passes.

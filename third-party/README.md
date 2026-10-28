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

The AWS credential-provider-http 3.972.75, credential-provider-login 3.972.80
and nested-clients 3.997.47 manifests match upstream revision
`a960c14e8ce09ab8fd09352e40d75c6b9d52c1ca` (all three live in
`packages-internal/` at that revision). Their retained root Apache license fills
the published packages' omitted notice files. Union 0.5.0 retains both LICENSE
and the README's explicit MIT terms from its published `gitHead`
`b69c2c0eb95d48b49e72182e53c1788efa195cc0`. The supplemental catalog may identify
missing license metadata only after its pinned notice hash matches; conflicting
declared terms fail collection and an unreviewed version does not inherit the entry.

`android-supplements.json` maps the 60 exact Maven coordinates to their retained
source copyright/license headers and Apache-2.0 declarations (including inherited
parent declarations for ZXing and Guava). Gradle checks notice hashes and includes
these headers and full Apache terms alongside notices found in the binary archives.

`native-supplements.json` retains the exact OpenSSL 3.6.5 license and SQLite
3.54.1 public-domain declaration from pinned upstream revisions. The SQLite
header and OpenSSL license are also checksum-pinned distribution materials.
These close missing-notice entries, not the native source/rebuild review.

The libc++ 30 supplement is the LLVM NOTICE from the checksum-verified official
NDK r30 Linux archive. The Termux library has identical `.text`, `.rodata`, and
GNU build-ID sections to the NDK arm64 library; full files differ (Termux omits
debug information). The manifest records both file hashes and the matching
sections. The complete NDK archive is pinned as a reproducible notice source;
it is not substituted for the remaining native corresponding-source review.

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

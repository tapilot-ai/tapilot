"""Stage pinned, verified materials only; never publish a draft or upload an APK."""
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import tarfile
import re
import zipfile

root = pathlib.Path(sys.argv[1]).resolve()
root.mkdir(parents=True, exist_ok=True)
def gh(*args):
    return subprocess.check_output(['gh', *args], text=True)
def require_draft():
    release = json.loads(gh('release', 'view', 'v0.1.8', '--json', 'isDraft'))
    if release['isDraft'] is not True:
        raise RuntimeError('Only the existing v0.1.8 draft may be modified')
require_draft()
artifacts = [
    ('native', 13144934837, '3b6f5d561906f868d7c2dc8587b9eff31190d052cb12a48ebb62eda2b224ae7a'),
    ('vips', 13147077052, 'd430b447aae5863d795c3c671da00799f6afa9e3028567191116db746e00e50c'),
    ('sharp', 13147336776, '1059e7f4108d19f3956877edcf8b960e1c498cf9ca159d95fcf12f1cf9bed980'),
]
def digest(file):
    with file.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()
for name, identifier, expected in artifacts:
    archive = root / (name + '.zip')
    with archive.open('wb') as output:
        subprocess.run(['gh', 'api', f'repos/tapilot-ai/tapilot/actions/artifacts/{identifier}/zip'], stdout=output, check=True)
    if digest(archive) != expected:
        raise RuntimeError('Artifact hash mismatch: ' + name)
    with zipfile.ZipFile(archive) as source:
        source.extractall(root / name)
assets = root / 'assets'
assets.mkdir()
selection = {
    'Tapilot-0.1.8-native-sources.tar.gz': 'native/native-sources.tar.gz',
    'Tapilot-0.1.8-wasm-sources.tar.gz': 'vips/recovered-wasm/tapilot-wasm-materials/corresponding-source-inputs.tar.gz',
    'Tapilot-0.1.8-libvips-wasm.tar.gz': 'vips/recovered-wasm/tapilot-wasm-materials/libvips-wasm-rebuilt.tar.gz',
    'Tapilot-0.1.8-sharp-sources.tar.gz': 'sharp/sharp-relink/sharp-source.tar.gz',
    'Tapilot-0.1.8-sharp-wasm.tar.gz': 'sharp/sharp-relink/sharp-wasm32-rebuilt.tar.gz',
    'Tapilot-0.1.8-sharp-build-package-lock.json': 'sharp/sharp-relink/sharp-build-package-lock.json',
    'Tapilot-0.1.8-native-sources.json': 'native/native-sources/results.json',
    'Tapilot-0.1.8-wasm-sources.json': 'vips/recovered-wasm/tapilot-wasm-materials/source-downloads.json',
}
# Retain full license/copyright texts from the exact source archives and Rust vendor tree.
notices = []
pattern = re.compile(r'^(licen[cs]e|copying|copyright|notice|authors)(?:$|[._-])', re.I)
def collect(archive, label):
    with tarfile.open(archive) as source:
        for member in source:
            if not member.isfile() or not pattern.match(pathlib.PurePosixPath(member.name).name):
                continue
            if member.size > 2_000_000:
                raise RuntimeError('Unexpectedly large license file: ' + member.name)
            text = source.extractfile(member).read().decode('utf-8', errors='replace')
            notices.append('\n===== ' + label + ' / ' + member.name + ' =====\n' + text + '\n')
vips = root / 'vips/recovered-wasm/tapilot-wasm-materials'
for entry in json.loads((vips / 'source-downloads.json').read_text()):
    archive = vips / 'downloads' / entry['sha256']
    if tarfile.is_tarfile(archive):
        collect(archive, entry['url'] + ' SHA256=' + entry['sha256'])
collect(vips / 'corresponding-source-inputs.tar.gz', 'Rust vendor and retained build materials')
# Emscripten's runtime code is linked into the generated glue/WASM as well.
emscripten_license = gh('api', 'repos/emscripten-core/emscripten/contents/LICENSE?ref=6.0.10', '-H', 'Accept: application/vnd.github.raw+json')
notices.append('\n===== Emscripten 6.0.10 LICENSE =====\n' + emscripten_license)
notice_file = root / 'wasm-NOTICES.txt'
notice_file.write_text('Tapilot rebuilt WASM component notices\n' + ''.join(notices))
selection['Tapilot-0.1.8-wasm-NOTICES.txt'] = 'wasm-NOTICES.txt'
receipt = {'schemaVersion': 1, 'public': False, 'artifacts': artifacts, 'assets': []}
for filename, relative in selection.items():
    source = root / relative
    shutil.copyfile(source, assets / filename)
    receipt['assets'].append({'name': filename, 'sha256': digest(source), 'bytes': source.stat().st_size})
(root / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
shutil.copyfile(root / 'receipt.json', assets / 'Tapilot-0.1.8-materials.json')
require_draft()
subprocess.run(['gh', 'release', 'upload', 'v0.1.8', *map(str, sorted(assets.iterdir())), '--clobber'], check=True)
print(json.dumps(receipt, indent=2))

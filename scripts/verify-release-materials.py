"""Populate authenticated draft assets; the regular collector verifies every byte."""
import hashlib
import json
import pathlib
import subprocess
import tarfile

root = pathlib.Path.cwd()
lock = json.loads((root / 'third-party/sources.lock.json').read_text())
for material in lock['materials']:
    if material['kind'] != 'release-material':
        continue
    target = root / material['path']
    if not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run(['gh', 'release', 'download', 'v0.1.8', '--pattern', target.name,
                        '--dir', str(target.parent)], check=True)
    with target.open('rb') as stream:
        assert hashlib.file_digest(stream, 'sha256').hexdigest() == material['sha256'], target.name
native = root / '.cache/distribution-sources/release/Tapilot-0.1.8-native-sources.tar.gz'
with tarfile.open(native) as source:
    # The archive is pinned above; extract just the source cache, never overwrite project code.
    members = [m for m in source if pathlib.PurePosixPath(m.name).parts[:2] == ('.cache', 'distribution-sources')]
    if not members:
        raise RuntimeError('Missing native source cache in archive')
    source.extractall(root, members=members, filter='data')

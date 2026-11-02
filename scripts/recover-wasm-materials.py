"""Recover source packaging from a pinned build artifact; never approve an APK."""
import hashlib
import json
import re
import subprocess
import sys
import tarfile
from pathlib import Path

root = Path(sys.argv[1]).resolve()
root.mkdir(parents=True, exist_ok=True)
archive = root / 'original-artifact.zip'
with archive.open('wb') as output:
    subprocess.run(['gh', 'api', 'repos/tapilot-ai/tapilot/actions/artifacts/13147115198/zip'], stdout=output, check=True)
expected = '461a7149951f429280673d8cd2b78261d000c6e06d49179a88d3fcdacd21cd68'
assert hashlib.sha256(archive.read_bytes()).hexdigest() == expected, 'Original artifact digest differs'
subprocess.run(['unzip', '-q', str(archive), '-d', str(root / 'original')], check=True)
original = root / 'original' / 'tapilot-wasm-materials'
assert original.is_dir(), 'Unexpected artifact layout'
recovered = root / 'tapilot-wasm-materials'
recovered.mkdir()
with tarfile.open(original / 'corresponding-source-inputs.tar.gz') as source:
    source.extractall(recovered, filter='data')
requests = original / 'downloads' / 'requests.tsv'
entries = []
for line in requests.read_text().splitlines():
    digest, arguments = line.split('\t', 1)
    assert re.fullmatch('[a-f0-9]{64}', digest)
    urls = re.findall(r'https://\S+', arguments)
    assert len(urls) == 1, 'Unexpected download arguments'
    target = recovered / 'downloads' / digest
    target.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(['curl', '--fail', '--location', '--silent', '--show-error', '--retry', '2', urls[0], '-o', str(target)], check=True)
    assert hashlib.sha256(target.read_bytes()).hexdigest() == digest, 'Source changed: ' + urls[0]
    entries.append({'url': urls[0], 'sha256': digest})
    print('Verified source', digest, flush=True)
(recovered / 'downloads' / 'requests.tsv').write_bytes(requests.read_bytes())
(recovered / 'source-downloads.json').write_text(json.dumps(entries, indent=2) + '\n')
# Keep the successfully compiled libraries from the exact original artifact.
(recovered / 'libvips-wasm-rebuilt.tar.gz').write_bytes((original / 'libvips-wasm-rebuilt.tar.gz').read_bytes())
with tarfile.open(recovered / 'corresponding-source-inputs.tar.gz', 'w:gz') as output:
    for name in ['wasm-vips-source.tar.gz', 'downloads', 'source', 'source-downloads.json']:
        output.add(recovered / name, arcname=name)
files = sorted(recovered.glob('*.tar.gz'))
(recovered / 'SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in files))
(recovered / 'status.txt').write_text('Compiled libraries recovered from upstream CI artifact; every missing source verified against its original build-time digest. Sharp relink and APK review remain separate.\n')
(recovered / 'recovery.json').write_text(json.dumps({'originalRun':41452931004,'originalArtifactSha256':expected,'verifiedSourceDownloads':len(entries),'apkDistributionApproved':False},indent=2)+'\n')

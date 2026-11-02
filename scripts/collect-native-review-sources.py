from pathlib import Path
import subprocess,hashlib,json,concurrent.futures,os,sys
if len(sys.argv)!=2:raise SystemExit('Usage: collect-native-review-sources.py OUTPUT_DIRECTORY')
root=Path(sys.argv[1]).resolve();root.mkdir(parents=True,exist_ok=True);os.chdir(root)
items=[
('nodejs-lts@24.22.2','https://nodejs.org/dist/v24.22.2/node-v24.22.2.tar.xz','node-v24.22.2.tar.xz','8fe2ec1723bb9f8d0b683907c82708debbeda64af21cb2aa214c4241eed0a061'),
('c-ares@1.34.9','https://github.com/c-ares/c-ares/archive/refs/tags/v1.34.9.tar.gz','c-ares-1.34.9.tar.gz','453bab9eb8c2bd4343b527dd97dbd77e1fab6608781dda2dbbfde7e480882c97'),
('libsqlite@3.54.1-1','https://www.sqlite.org/2026/sqlite-src-3540100.zip','sqlite-src-3540100.zip','387e7f6cad4797b17848c07d453dc6606917bb356a6cfe5a3abafd38c240c71b'),
('libicu@78.3','https://github.com/unicode-org/icu/releases/download/release-78.3/icu4c-78.3-sources.tgz','icu4c-78.3-sources.tgz','3a2e7a47604ba702f345878308e6fefeca612ee895cf4a5f222e7955fabfe0c0'),
('openssl@3.6.5','https://github.com/openssl/openssl/releases/download/openssl-3.6.5/openssl-3.6.5.tar.gz','openssl-3.6.5.tar.gz','a2157c2830efdec3788939b00c9b0638306d3f0bbb76dc4832ee503bb397df98'),
('zlib@1.3.2','https://github.com/madler/zlib/releases/download/v1.3.2/zlib-1.3.2.tar.xz','zlib-1.3.2.tar.xz','d7a0654783a4da529d1bb793b7ad9c3318020af77667bcae35f95d0e42a792f3'),
]
recipes={'nodejs-lts':'22bd884a0428cef49cc99230ad94bd76ee5456a6','c-ares':'5d046f57d62da2ba6cc5e4f3eff67ba4acabf0c8','libsqlite':'4de02e1201055098b4e1b2264fa113d8b6c9ec0b'}
for name,sha in recipes.items():items.append((f'termux/{name}@{sha}',f'https://codeload.github.com/termux/termux-packages/tar.gz/{sha}',f'termux-{sha}.tar.gz',None))
def fetch(item):
 component,url,name,expected=item
 relative=Path('.cache/distribution-sources/upstream')/name
 relative.parent.mkdir(parents=True,exist_ok=True)
 if not relative.exists():
  temp=relative.with_suffix(relative.suffix+'.partial')
  subprocess.run(['curl','--fail','--location','--silent','--show-error','--retry','3','--retry-all-errors','--max-time','900','--continue-at','-',url,'-o',str(temp)],check=True)
  temp.rename(relative)
 actual=hashlib.sha256(relative.read_bytes()).hexdigest()
 if expected and actual!=expected:raise ValueError('Source hash mismatch: '+component)
 print('Verified '+component,flush=True)
 return dict(component=component,kind='source-archive',url=url,path=str(relative),sha256=actual,bytes=relative.stat().st_size)
results=[];failures=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 futures={pool.submit(fetch,item):item[0] for item in items}
 for future in concurrent.futures.as_completed(futures):
  try:results.append(future.result())
  except Exception as error:failures.append({'component':futures[future],'error':str(error)});print('Failed '+futures[future],flush=True)
Path('results.json').write_text(json.dumps({'materials':results,'failures':failures},indent=2)+'\n')
raise SystemExit(bool(failures))

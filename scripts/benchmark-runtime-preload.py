#!/usr/bin/env python3
"""Read-only Android module preload probe; no model requests or device actions.

Runs isolated Node processes under the debug app UID. The pinned package ESM
paths match this repository's deployment. This measures imports, not full tasks.
"""
import argparse
import datetime
import subprocess,json,shlex,pathlib
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[1]
out = root / 'artifacts/controlled-execution' / ('runtime-preload-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
out.mkdir(parents=True, exist_ok=False)
adb=['adb','-s',args.serial]
apk=subprocess.check_output(adb+['shell','pm','path','lifeos.md.tapilot'],text=True,timeout=15).strip().removeprefix('package:')
if not apk.startswith('/data/app/') or not apk.endswith('/base.apk') or any(c.isspace() for c in apk):
 raise RuntimeError('Unexpected APK path')
lib=str(pathlib.PurePosixPath(apk).parent/'lib/arm64')
manifest=json.loads(subprocess.check_output(adb+['exec-out','run-as','lifeos.md.tapilot','cat','files/node-runtime/manifest.json'],text=True,timeout=15))
rows=[]
base='file:///data/user/0/lifeos.md.tapilot/files/node-runtime/'
for mode in ['cold','preload','preload','cold','cold','preload','preload','cold']:
 code='''
if(process.version!=='v24.18.0') throw Error('Expected device Node 24.18.0');
const originalWrite=process.stdout.write.bind(process.stdout);
process.stdout.write=(chunk,...args)=>{
 if(String(chunk).includes('rpc.request')) throw Error('Preload attempted native RPC');
 return originalWrite(chunk,...args);
};
globalThis.fetch=()=>{throw Error('Preload attempted network access')};
const initialRss=process.memoryUsage().rss;
const start=performance.now();
if(MODE==='preload') for(const name of ['@midscene/test/config','@midscene/core/agent','@midscene/test/midscene','@tapilot/android-accessibility']) {
 // Resolve ESM exports, just as Android's actual runner/config do.
 const path=name==='@midscene/test/config'?'node_modules/@midscene/test/dist/es/cli/index.mjs':
 name==='@midscene/core/agent'?'node_modules/@midscene/core/dist/es/agent/index.mjs':
 name==='@midscene/test/midscene'?'node_modules/@midscene/test/dist/es/midscene/index.mjs':
 'node_modules/@tapilot/android-accessibility/dist/es/index.mjs';
 await import(BASE+path);
}
const preloadMs=performance.now()-start;
const standbyRss=process.memoryUsage().rss;
const idleCpuStart=process.cpuUsage();
await new Promise(resolve=>setTimeout(resolve,1000));
const idleCpu=process.cpuUsage(idleCpuStart);
const ready=performance.now();
await import(BASE+'android.config.mjs');
console.log(JSON.stringify({mode:MODE,preloadMs,remainingImportMs:performance.now()-ready,initialRss,standbyRss,rss:process.memoryUsage().rss,idleCpuMs:(idleCpu.user+idleCpu.system)/1000}));
'''.replace('BASE',json.dumps(base)).replace('MODE',json.dumps(mode))
 cmd='LD_LIBRARY_PATH='+shlex.quote(lib)+' NODE_DISABLE_COMPILE_CACHE=1 '+shlex.join([lib+'/libnodebin.so','--input-type=module','-e',code])
 output=subprocess.check_output(adb+['shell','run-as','lifeos.md.tapilot','sh','-c',shlex.quote(cmd)],text=True,timeout=30)
 row=json.loads(output.strip().splitlines()[-1]);rows.append(row);print(json.dumps(row),flush=True)
 (out/'measurement.json').write_text(json.dumps({'serial':args.serial,'sourceFingerprint':manifest['sourceFingerprint'],'node':'v24.18.0','compileCache':False,'samples':rows},indent=2)+'\n')
print(json.dumps({'out':str(out),'samples':len(rows)}))

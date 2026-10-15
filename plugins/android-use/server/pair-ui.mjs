import {execFile} from 'node:child_process';
import {createPairingWindow} from './pairing-window.mjs';
try {
 const window=await createPairingWindow({onPaired:()=>{if(process.connected)process.send({paired:true});}});
 if(process.send)process.send({url:window.url,expires_in_seconds:300});
 else {process.stdout.write(`Open this computer's pairing window: ${window.url}\n`);execFile(process.platform==='darwin'?'open':process.platform==='win32'?'explorer':'xdg-open',[window.url],()=>{});}
 process.on('SIGTERM',()=>window.close());process.on('SIGINT',()=>window.close());
 if(process.send)process.on('disconnect',()=>window.close());
}catch(error){if(process.send)process.send({error:error.message});else process.stderr.write(error.message+'\n');process.exitCode=1;}

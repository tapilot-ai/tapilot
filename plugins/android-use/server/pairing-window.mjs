import { createServer } from 'node:http';
import { createServer as createSecureServer } from 'node:https';
import { randomBytes, createHash, X509Certificate } from 'node:crypto';
import selfsigned from 'selfsigned';
import { networkInterfaces } from 'node:os';
import { readFile } from 'node:fs/promises';
import { validateConfig, saveConfig } from './config.mjs';
import { PhoneConnection } from './transport.mjs';
import { discoverPhones } from './discovery.mjs';
import QRCode from 'qrcode';

export async function pairPhone(config) {
  const phone = new PhoneConnection(config);
  try { const {token} = await phone.open(true); await saveConfig({...config, token}); }
  finally { phone.close(); }
}
export function pairingAddress() {
  const entries = Object.entries(networkInterfaces()).filter(([name]) => /^(en\d+|eth\d+|wlan\d+|wlp\w+)$/.test(name));
  entries.sort(([a], [b]) => (a === 'en0' ? -1 : b === 'en0' ? 1 : a.localeCompare(b)));
  const address = entries.flatMap(([,list]) => list ?? []).find(a => a.family === 'IPv4' && !a.internal)?.address;
  if (!address) throw Error('Connect the computer to the same Wi-Fi as the phone before pairing.');
  return address;
}
export async function createPairingWindow({ host = pairingAddress(), pair = pairPhone, discover = discoverPhones, lifetimeMs = 300000, onPaired = () => {} } = {}) {
  const certificate = await selfsigned.generate([{name:'commonName',value:'Android Use Pairing'}], {keySize:2048,algorithm:'sha256',days:1,extensions:[{name:'subjectAltName',altNames:[{type:7,ip:host}]}]});
  const fingerprint = createHash('sha256').update(new X509Certificate(certificate.cert).raw).digest('hex');
  const nonce = randomBytes(32).toString('base64url');
  const expiresAt = Date.now()+lifetimeMs;
  let pending, paired = false, pairing = false, offers = 0, html, qr;
  const handler = async (req,res) => {
    res.setHeader('Cache-Control','no-store'); res.setHeader('Referrer-Policy','no-referrer');
    res.setHeader('X-Content-Type-Options','nosniff');
    res.setHeader('Content-Security-Policy', "default-src 'none'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; form-action 'none'");
    const json = (code,value) => { res.writeHead(code, {'Content-Type':'application/json'}); res.end(JSON.stringify(value)); };
    try {
      const url = new URL(req.url,'http://localhost');
      if(Date.now()>=expiresAt) return json(410,{error:'Pairing expired; open a new pairing window.'});
      if (url.pathname === `/offer/${nonce}` && req.method === 'POST') {
        if (paired || pairing || pending || ++offers > 10 || req.headers.origin) return json(409,{error:'Pairing offer unavailable'});
        if (!req.socket.encrypted) return json(403,{error:'TLS required'});
        const config = validateConfig(JSON.parse(await body(req)));
        pending = config; return json(200,{received:true});
      }
      // Browser controls are loopback-only, origin checked, and capability protected.
      if (req.socket.encrypted || !['127.0.0.1','::ffff:127.0.0.1','::1'].includes(req.socket.remoteAddress) || req.headers.host !== `127.0.0.1:${server.address().port}`) return json(403,{error:'Open the pairing window on this computer'});
      if (url.pathname !== `/${nonce}/` && !url.pathname.startsWith(`/${nonce}/`)) return json(404,{error:'Not found'});
      if (req.method === 'POST' && req.headers.origin !== `http://127.0.0.1:${server.address().port}`) return json(403,{error:'Invalid origin'});
      const route = url.pathname.slice(nonce.length+2);
      if(req.method==='GET' && route==='') {res.writeHead(200,{'Content-Type':'text/html; charset=utf-8'});res.end(html);return;}
      if(req.method==='GET' && route==='pairing.mjs') {res.writeHead(200,{'Content-Type':'text/javascript'});res.end(await readFile(new URL('../ui/pairing.mjs',import.meta.url)));return;}
      if(req.method==='GET' && route==='pairing.css') {res.writeHead(200,{'Content-Type':'text/css'});res.end(await readFile(new URL('../ui/pairing.css',import.meta.url)));return;}
      if(req.method==='GET' && route==='qr.svg') {res.writeHead(200,{'Content-Type':'image/svg+xml'});res.end(qr);return;}
      if(req.method==='GET' && route==='status') return json(200,{paired,pairing,expiresAt,pending:pending?{host:pending.host,fingerprint:pending.fingerprint}:null});
      if(req.method==='POST' && route==='discover') return json(200,{devices:await discover()});
      if(req.method==='POST' && route==='confirm') {
        if (!pending || paired || pairing) return json(409,{error:'No pending phone'});
        pairing=true;
        try {await pair(pending); onPaired(); paired=true; pending=undefined; return json(200,{paired:true});}
        catch {pending=undefined;return json(502,{error:'无法完成配对，请保持手机解锁、确认 Wi-Fi 后重新扫码。'});}
        finally {pairing=false;}
      }
      return json(404,{error:'Not found'});
    } catch {json(400,{error:'Invalid pairing request'});}
  };
  const server = createServer(handler);
  const receiver = createSecureServer({key:certificate.private,cert:certificate.cert,minVersion:'TLSv1.2'},handler);
  receiver.on('tlsClientError',()=>{});
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(0,'127.0.0.1',resolve);});
  try { await new Promise((resolve,reject)=>{receiver.once('error',reject);receiver.listen(0,'0.0.0.0',resolve);}); } catch(error) {server.close();throw error;}
  const port=server.address().port;
  const payload='tapilot://computer/'+Buffer.from(JSON.stringify({version:1,url:`https://${host}:${receiver.address().port}/offer/${nonce}`,fingerprint,expiresAt})).toString('base64url');
  html=await readFile(new URL('../ui/pairing.html',import.meta.url),'utf8');
  qr=await QRCode.toString(payload,{type:'svg',errorCorrectionLevel:'M',margin:4});
  server.requestTimeout=10000;server.headersTimeout=10000;receiver.requestTimeout=10000;receiver.headersTimeout=10000;
  const close=()=>{clearTimeout(timer);pending=undefined;server.closeAllConnections();server.close();receiver.closeAllConnections();receiver.close();};
  const timer=setTimeout(close,lifetimeMs);
  return {url:`http://127.0.0.1:${port}/${nonce}/`,close, payload};
}
async function body(req) {
  let size=0;const chunks=[];
  for await(const chunk of req){size+=chunk.length;if(size>16384)throw Error('Request too large');chunks.push(chunk);}
  return Buffer.concat(chunks).toString();
}

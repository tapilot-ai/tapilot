const status=document.querySelector('#status'),error=document.querySelector('#error'),confirm=document.querySelector('#confirm'),discover=document.querySelector('#discover');
let stopped=false;
async function request(path,post=false){const r=await fetch(path,{method:post?'POST':'GET',cache:'no-store'});const data=await r.json();if(!r.ok)throw Error(data.error??'请求失败');return data;}
function failure(e){error.textContent=e.message;error.hidden=false;}
async function refresh(){
 if(stopped)return;
 try{const data=await request('status');confirm.hidden=!data.pending;confirm.disabled=data.pairing;
 document.querySelector('#expiry').textContent=`${Math.max(0,Math.ceil((data.expiresAt-Date.now())/1000))} 秒后过期`;
 status.textContent=data.paired?'已配对，可以返回 ChatGPT 查看手机画面':data.pairing?'正在配对，请稍候…':data.pending?`手机 ${data.pending.host} 已扫码，请确认配对`:'等待手机扫码…';
 if(data.paired){stopped=true;document.querySelector('.qr').hidden=true;return;}
 }catch(e){failure(e);stopped=true;confirm.hidden=true;document.querySelector('.qr').hidden=true;}
 if(!stopped)setTimeout(refresh,700);
}
confirm.onclick=async()=>{confirm.disabled=true;error.hidden=true;try{await request('confirm',true);}catch(e){failure(e);}finally{confirm.disabled=false;}};
discover.onclick=async()=>{discover.disabled=true;try{const {devices}=await request('discover',true);const list=document.querySelector('#devices');list.replaceChildren();for(const phone of devices){const li=document.createElement('li');li.textContent=`${phone.name} · ${phone.host}`;list.append(li);}if(!devices.length){const li=document.createElement('li');li.textContent='尚未发现。确认手机开启无线控制；网络屏蔽自动发现时仍可直接扫码。';list.append(li);}}catch(e){failure(e);}finally{discover.disabled=false;}};
window.addEventListener('pagehide',()=>{stopped=true;});
window.addEventListener('pageshow',event=>{if(event.persisted){stopped=false;refresh();}});
refresh();discover.click();

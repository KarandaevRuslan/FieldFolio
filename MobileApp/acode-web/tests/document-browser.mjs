import assert from 'node:assert/strict';
import {readFile, mkdir} from 'node:fs/promises';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {chromium} from 'playwright';
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const fixture=JSON.parse(await readFile(resolve(root,'app/build/document-fixture.json'),'utf8'));
const assets=resolve(root,'app/src/main/assets');
const origin=fixture.manifest.origin;
const unexpected=[];
const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH || undefined,headless:true,
 args:['--no-sandbox','--disable-background-networking','--disable-component-update','--disable-sync','--no-first-run']});
const context=await browser.newContext({viewport:{width:900,height:650},serviceWorkers:'block'});
await context.route('**/*',async route=>{
 const req=route.request(),url=req.url();
 if(req.method()!=='GET'){await route.fulfill({status:403,body:''});return;}
 const response=fixture.responses[url];
 if(response){await route.fulfill({status:200,headers:{...response.headers,'content-type':response.mime},body:Buffer.from(response.base64,'base64')});return;}
 const parsed=new URL(url);
 if(parsed.origin===origin && /^\/assets\/document\/(document(?:-host)?\.html|assets\/[\w.-]+\.(?:js|css|woff2?))$/.test(parsed.pathname)){
  try{
   const body=await readFile(resolve(assets,parsed.pathname.slice('/assets/'.length)));
   const headers={'Access-Control-Allow-Origin':'null','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'};
   if(parsed.pathname.endsWith('/document.html'))headers['Content-Security-Policy']=fixture.rendererCsp;
   const ext=parsed.pathname.split('.').at(-1);
   headers['Content-Type']=({html:'text/html',js:'application/javascript',css:'text/css',woff2:'font/woff2'})[ext]||'application/octet-stream';
   await route.fulfill({status:200,headers,body});return;
  }catch(error){unexpected.push(url+' '+error.message);}
 } else unexpected.push(url);
 await route.abort('blockedbyclient');
});
const page=await context.newPage();
const errors=[];page.on('pageerror',e=>errors.push(e.message));
async function setup(page,source=fixture.source){
 await page.goto(origin+'/assets/document/document-host.html');
 await page.waitForFunction(()=>window.PreviewApi?.isReady());
 await page.evaluate(({source,manifest})=>{
  PreviewApi.setPreferences({language:'en',locale:'en',dark:false,timeZone:'UTC'});
  PreviewApi.render(source,'fixture-document',manifest);
 },{source,manifest:fixture.manifest});
 return page.frames().find(f=>f.url().endsWith('/document.html'));
}
try{
 const frame=await setup(page);
 await frame.locator('[data-nexus-result]').waitFor();
 assert.equal(await frame.locator('[data-nexus-result]').innerText(),'0');
 await frame.locator('[data-nexus-increment]').click();
 await frame.waitForFunction(()=>document.querySelector('[data-nexus-result]')?.textContent==='2');
 assert.equal(await frame.locator('[data-nexus-increment]').evaluate(el=>getComputedStyle(el).borderRadius),'999px');
 await page.setViewportSize({width:650,height:900});
 assert.equal(await frame.locator('[data-nexus-result]').innerText(),'2');
 await frame.locator('[data-nexus-increment]').click();
 await frame.waitForFunction(()=>document.querySelector('[data-nexus-result]')?.textContent==='4');
 console.log('PASS real Notebook Kit cells, Nexus propagation, styled controls and orientation retain state');
 const source=`<notebook><script id="1" type="module">window.__Folio=Folio; window.__nexus=nexus; window.__scope=nexus.scope(invalidation); display(html\`<p id="api-ready">API ready</p>\`);</script></notebook>`;
 await page.evaluate(({source,manifest})=>PreviewApi.render(source,'api-test',manifest),{source,manifest:fixture.manifest});
 await frame.locator('#api-ready').waitFor();
 const api=await frame.evaluate(async()=>{
  const v=window.__Folio.vfs;
  const result=await v.json('Mask result');
  const mask=await v.attachment('Mask result','mask').image();
  const canvas=document.createElement('canvas');canvas.width=40;canvas.height=20;
  canvas.getContext('2d').drawImage(mask,0,0);
  return {names:v.list().map(x=>x.name),methods:Object.keys(v),text:await v.text('Notes'),json:await v.json('Config.json'),
    result,meta:await v.get('Song'),location:await v.get('Location'),weather:await v.get('Weather'),clock:await v.get('Clock'),
    special:await v.text('A%2FB%3A100%25.txt'),pixels:[...canvas.getContext('2d').getImageData(0,0,1,1).data],
    size:[mask.naturalWidth,mask.naturalHeight],frozen:Object.isFrozen(v),alias:v.exists('Visual alias')};
 });
 assert.equal(api.text,'PRIVATE_DEMO_PAYLOAD');assert.equal(api.json.word,'текст');
 assert.equal(api.result.boxes[0].confidence,.9);assert.equal(api.result.values[0].value,42);
 assert.equal(api.result.layers[0].polygons[0].rings[0].length,3);
 assert.deepEqual(api.size,[40,20]);assert.equal(api.pixels[3],255);
 assert.equal(api.meta.audio.displayedTags.title,'Song title');assert.equal(api.weather.weather.temperature,18.5);
 assert.equal(api.location.resolvedLocation.latitude,10);assert.equal(api.clock.resolvedZoneId,'Europe/London');
 assert.equal(api.special,'special logical name');assert.equal(api.alias,false);assert.equal(api.frozen,true);
 for(const op of ['write','remove','move','rename','copy','mkdir','put','save'])assert.ok(!api.methods.includes(op));
 console.log('PASS actual native VFS metadata, UTF-16, every model result, pixels, audio, location, weather and clock');
 const isolation=await frame.evaluate(async()=>{
  const denied={};
  for(const [name,fn] of Object.entries({parent:()=>parent.document.body,localStorage:()=>localStorage.setItem('x','x'),
   indexedDB:()=>indexedDB.open('escape'),cache:()=>caches.open('escape')})){
   try{await fn();denied[name]=false;}catch{denied[name]=true;}
  }
  let post;try{post=(await fetch(window.__Folio.vfs.url('Notes'),{method:'POST',body:'CHANGED'})).status;}catch{post=403;}
  return {origin:origin,bridge:typeof window.NativeEditor,denied,post,parentApi:typeof window.PreviewApi,
    popup:window.open('about:blank')===null};
 });
 assert.equal(isolation.origin,'null');assert.equal(isolation.bridge,'undefined');assert.equal(isolation.parentApi,'undefined');
 assert.ok(Object.values(isolation.denied).every(Boolean));assert.equal(isolation.post,403);assert.ok(isolation.popup);
 const parentApi=await page.evaluate(()=>({frozen:Object.isFrozen(PreviewApi),writable:Object.getOwnPropertyDescriptor(window,'PreviewApi').writable}));
 assert.deepEqual(parentApi,{frozen:true,writable:false});
 assert.equal(await frame.evaluate(()=>window.__Folio.vfs.text('Notes')),'PRIVATE_DEMO_PAYLOAD');
 console.log('PASS opaque origin denies parent DOM, storage, native bridge, popups and VFS writes');
 const second=await context.newPage();const secondFrame=await setup(second);
 await secondFrame.locator('[data-nexus-result]').waitFor();
 assert.equal(await secondFrame.locator('[data-nexus-result]').innerText(),'0');
 assert.equal(await secondFrame.evaluate(()=>typeof window.__Folio),'undefined');
 await second.close();
 await page.evaluate(({source,manifest})=>{PreviewApi.setPreferences({language:'ru',dark:true});PreviewApi.render(source,'fixture-document',manifest);},
 {source:fixture.source,manifest:fixture.manifest});
 await frame.locator('[data-nexus-result]').waitFor();
 assert.equal(await frame.evaluate(()=>document.documentElement.dataset.theme),'dark');
 assert.match(await frame.locator('main').innerText(),/Интерактивный документ Fieldfolio/);
 await mkdir(resolve(root,'app/build/document-browser'),{recursive:true});
 await page.screenshot({path:resolve(root,'app/build/document-browser/document-dark.png')});
 await page.setViewportSize({width:800,height:164});
 await page.screenshot({path:resolve(root,'app/build/document-browser/document-mini.png')});
 assert.deepEqual(unexpected,[]);assert.deepEqual(errors,[]);
 console.log('PASS independent runtimes, Russian/dark mode, mini view and zero external requests');
}finally{await context.close();await browser.close();}

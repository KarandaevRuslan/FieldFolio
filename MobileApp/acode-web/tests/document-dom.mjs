import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {build} from 'esbuild';
import {JSDOM} from 'jsdom';
const web=resolve(dirname(fileURLToPath(import.meta.url)),'..');
const fixture=JSON.parse(await readFile(resolve(web,'../app/build/document-fixture.json'),'utf8'));
const {outputFiles}=await build({stdin:{contents:`export {startPreview} from './src/document/application.js';`,resolveDir:web},bundle:true,format:'iife',globalName:'NotebookHarness',write:false,loader:{'.css':'empty'},plugins:[{name:'keep-optional-library-imports-lazy',setup(b){b.onResolve({filter:/.*/},args=>args.kind==='dynamic-import'?{path:args.path,external:true}:null);}}],logLevel:'silent'});
const code=outputFiles[0].text;
const create=()=>{
 const dom=new JSDOM('<!doctype html><html><body><main></main></body></html>',{url:fixture.manifest.origin+'/assets/document/document.html',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window;
 w.structuredClone=structuredClone;w.Response=Response;w.Request=Request;w.Headers=Headers;w.TextDecoder=TextDecoder;w.TextEncoder=TextEncoder;
 const requests=[];
 w.fetch=async(url,options)=>{
  requests.push(String(url));const r=fixture.responses[String(url)];
  assert.ok(r,'only native fixture resources may be read');if(options) assert.equal(options.credentials,'omit');
  return new Response(Buffer.from(r.base64,'base64'),{status:200,headers:{...r.headers,'content-type':r.mime}});
 };
 w.eval(code);const api=w.NotebookHarness.startPreview({main:w.document.querySelector('main')});
 api.setPreferences({language:'en',dark:false,timeZone:'UTC'});
 return {dom,w,api,requests,close(){api.destroy();w.close();}};
};
const wait=async(predicate)=>{for(let i=0;i<120;i++){if(await predicate())return;await new Promise(r=>setTimeout(r,10));}throw new Error('Notebook runtime did not settle');};

test('actual Notebook Kit and Nexus render the default source and propagate counter updates',async()=>{
 const h=create();try{
  h.api.render(fixture.source,'source',fixture.manifest);
  await wait(()=>h.w.document.querySelector('[data-nexus-result]'));
  const doc=h.w.document;
  assert.equal(doc.querySelector('[data-nexus-result]').textContent,'0');
  doc.querySelector('[data-nexus-increment]').click();
  await wait(()=>doc.querySelector('[data-nexus-result]')?.textContent==='2');
  assert.equal(doc.querySelectorAll('.error').length,0,doc.body.textContent);
  assert.ok(doc.querySelector('.np-input-button-group button'));
  assert.match(doc.body.textContent,/Interactive Fieldfolio/);
  h.api.setPreferences({language:'ru',dark:true});h.api.render(fixture.source,'source',fixture.manifest);
  await wait(()=>doc.querySelector('[data-nexus-result]'));
  assert.equal(doc.documentElement.dataset.theme,'dark');assert.match(doc.body.textContent,/Интерактивный документ Fieldfolio/);
  assert.equal(doc.querySelectorAll('[data-nexus-increment]').length,1);
 }finally{h.close();}
});

test('native report fixture is consumed by real FileAttachment and every typed VFS adapter',async()=>{
 const h=create();try{
  const source=`<notebook><script id="1" type="module">window.testFolio=Folio; display(html\`<span id="ready">Ready</span>\`);</script></notebook>`;
  h.api.render(source,'api',fixture.manifest);await wait(()=>h.w.document.querySelector('#ready'));
  const v=h.w.testFolio.vfs;
  assert.equal(await v.text('Notes'),'PRIVATE_DEMO_PAYLOAD');assert.equal((await v.json('Config.json')).word,'текст');
  assert.equal(await v.text('A%2FB%3A100%25.txt'),'special logical name');
  const result=await v.json('Mask result');assert.equal(result.boxes[0].confidence,.9);assert.equal(result.values[0].value,42);
  assert.equal(result.layers[0].polygons[0].rings[0].length,3);
  for(const name of ['Classification','Regression','Text model','Numeric model'])assert.ok((await v.json(name)).task);
  const attachment=v.attachment('Mask result','input');assert.equal(await attachment.url(),v.url('Mask result','input'));
  assert.equal((await attachment.blob()).type,'image/png');
  assert.equal((await v.get('Song')).audio.displayedTags.title,'Song title');
  assert.equal((await v.get('Location')).resolvedLocation.latitude,10);assert.equal((await v.get('Weather')).weather.temperature,18.5);
  assert.equal((await v.get('Clock')).resolvedZoneId,'Europe/London');
  assert.ok(Object.isFrozen(v));assert.equal(v.exists('Visual alias'),false);
  for(const op of ['write','remove','move','rename','copy','mkdir','put','save'])assert.equal(v[op],undefined);
  assert.ok(h.requests.every(url=>url.startsWith(fixture.manifest.origin+'/vfs/'+fixture.manifest.scope+'/')));
 }finally{h.close();}
});

test('Nexus scopes and handlers are disposed on replacement and isolated between document runtimes',async()=>{
 const one=create(),two=create();try{
  const source=`<notebook><script id="1" type="module">window.oldScope=nexus.scope(invalidation); window.store=window.oldScope.store(9); display(html\`<b id="scope">scope</b>\`);</script></notebook>`;
  one.api.render(source,'one',fixture.manifest);await wait(()=>one.w.document.querySelector('#scope'));
  const scope=one.w.oldScope;
  two.api.render(fixture.source,'two',fixture.manifest);await wait(()=>two.w.document.querySelector('[data-nexus-result]'));
  assert.equal(two.w.oldScope,undefined);assert.equal(two.w.document.querySelector('[data-nexus-result]').textContent,'0');
  one.api.render('<notebook><script id="1" type="module">display("replacement");</script></notebook>','one',fixture.manifest);
  await wait(()=>one.w.document.body.textContent.includes('replacement'));
  assert.equal(scope.active,false);assert.equal(one.w.document.querySelector('#scope'),null);
 }finally{one.close();two.close();}
});

import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {searchText,normalizeSearch} from '../src/search/query.js';
import {documentSearch} from '../src/document/search.js';
import {documentViewport} from '../src/document/viewport.js';
import {memoryStorage} from '../src/document/storage.js';
const tick=ms=>new Promise(r=>setTimeout(r,ms));

test('rendered readers use Acode SearchQuery for literal, regex, case and whole-word searches',()=>{
 const find=(query,options={})=>searchText('Alpha alpha alphabet 123 456', {query,options});
 assert.equal(find('alpha').matches.length,3);
 assert.equal(find('alpha',{whole:true}).matches.length,2);
 assert.equal(find('Alpha',{matchCase:true}).matches.length,1);
 assert.equal(find('\\d+',{regex:true}).matches.length,2);
 assert.equal(find('[' ,{regex:true}).invalid,true);
 assert.equal(find('(?=a)',{regex:true}).matches.length,0);
 assert.equal(searchText('x '.repeat(5000),{query:'x'},10).matches.length,10);
 assert.equal(normalizeSearch({query:'q'.repeat(1000)}).query.length,512);
});

test('one search panel highlights live DOM without replacing controls, persists options and handles invalid regex',async()=>{
 const dom=new JSDOM('<!doctype html><body><main><p>Alpha alpha</p><button id="original">Button</button><pre>123 456</pre></main></body>',{pretendToBeVisual:true});
 const w=dom.window;
 const old={};for(const name of ['window','document','NodeFilter','MutationObserver']){old[name]=globalThis[name];globalThis[name]=w[name]||w;}
 const store=memoryStorage();let clicked=0;w.document.getElementById('original').onclick=()=>clicked++;
 const root=w.document.querySelector('main'),original=root.innerHTML;
 const panel=documentSearch(root,{storage:store});
 try{
  panel.open();const input=w.document.querySelector('.folio-find input');
  input.value='\\d+';input.dispatchEvent(new w.Event('input'));
  w.document.querySelector('[aria-label="Regular expression"]').click();
  await tick(150);assert.equal(w.document.querySelector('.folio-find output').textContent,'1 / 2');
  w.document.querySelector('[aria-label="Next match"]').click();assert.equal(w.document.querySelector('.folio-find output').textContent,'2 / 2');
  assert.equal(root.innerHTML,original);w.document.getElementById('original').click();assert.equal(clicked,1);
  input.value='[';input.dispatchEvent(new w.Event('input'));await tick(150);assert.equal(input.getAttribute('aria-invalid'),'true');
  panel.preferences({language:'ru'});assert.match(input.placeholder,/Поиск/);
  assert.ok(JSON.parse(store.getItem('fieldfolio:search')).options.regex);
 }finally{panel.dispose();w.close();for(const [name,value] of Object.entries(old)){if(value===undefined)delete globalThis[name];else globalThis[name]=value;}}
});

test('viewport retains actual user scrolling through mini/full and resizing; mini blocks browser scrolling',async()=>{
 const dom=new JSDOM('<!doctype html><html><body><main id="notebook">Long page</main></body></html>',{pretendToBeVisual:true});
 const w=dom.window;const store=memoryStorage();let observer;
 w.ResizeObserver=class{constructor(fn){observer=fn;}observe(){}disconnect(){}};
 w.scrollTo=options=>{Object.defineProperty(w,'scrollX',{configurable:true,value:options.left});Object.defineProperty(w,'scrollY',{configurable:true,value:options.top});};
 const viewport=documentViewport(store,w.document,w);
 try{
  viewport.mode(true);await tick(40);
  w.dispatchEvent(new w.Event('touchstart'));w.scrollTo({left:0,top:740});w.dispatchEvent(new w.Event('scroll'));await tick(100);
  viewport.mode(false);await tick(40);assert.equal(w.document.documentElement.dataset.mini,'true');assert.equal(w.scrollY,740);
  const wheel=new w.Event('wheel',{cancelable:true});w.dispatchEvent(wheel);assert.equal(wheel.defaultPrevented,true);
  w.scrollTo({left:0,top:0});viewport.mode(true);await tick(40);assert.equal(w.scrollY,740);
  // A resize/clamp must restore the user target rather than overwrite it with zero.
  w.scrollTo({left:0,top:0});observer();viewport.restore();await tick(40);assert.equal(w.scrollY,740);
  const saved=JSON.parse(store.getItem('fieldfolio:viewport'));assert.equal(saved.y,740);
  const input=w.document.createElement('input');w.document.body.append(input);input.focus();
  Object.defineProperty(w,'innerWidth',{configurable:true,value:800});w.scrollTo({left:0,top:0});w.dispatchEvent(new w.Event('resize'));
  await tick(100);assert.equal(w.scrollY,740,'Rotation restores the target even while Find owns focus');
 }finally{viewport.dispose();w.close();}
});

test('read-only search preserves active match and settings across empty rerender and restoration',async()=>{
 const dom=new JSDOM('<!doctype html><body><main><p>match match match match</p></main></body>',{pretendToBeVisual:true});
 const w=dom.window,root=w.document.querySelector('main'),store=memoryStorage();
 let panel=documentSearch(root,{storage:store});
 try{
  panel.open();const input=w.document.querySelector('.folio-find input');input.value='match';input.dispatchEvent(new w.Event('input'));
  await tick(160);w.document.querySelector('[aria-label="Search settings"]').click();
  w.document.querySelector('[aria-label="Next match"]').click();w.document.querySelector('[aria-label="Next match"]').click();
  assert.equal(w.document.querySelector('output').textContent,'3 / 4');
  root.replaceChildren();await tick(140);root.innerHTML='<p>match match match match</p>';await tick(140);
  assert.equal(w.document.querySelector('output').textContent,'3 / 4');
  const saved=store.dump();panel.dispose();store.load(saved);panel=documentSearch(root,{storage:store});
  assert.equal(w.document.querySelector('output').textContent,'3 / 4');assert.equal(w.document.querySelector('.folio-find').hidden,false);
  assert.equal(w.document.querySelector('[aria-label="Search settings"]').getAttribute('aria-expanded'),'true');
  w.document.querySelector('[aria-label="Close search"]').click();const closed=store.dump();panel.dispose();store.load(closed);panel=documentSearch(root,{storage:store});
  assert.equal(w.document.querySelector('.folio-find').hidden,true);assert.equal(w.document.querySelector('input').value,'match');
 }finally{panel.dispose();w.close();}
});

import test from 'node:test';
import assert from 'node:assert/strict';
import {createVfs, normalizePath} from '../src/document/vfs.js';
import {memoryStorage} from '../src/document/storage.js';

const origin='https://document-0123456789abcdef0123456789abcdef.fieldfolio.invalid';
const scope='11111111-2222-3333-4444-555555555555';
const prefix=`${origin}/vfs/${scope}/item/`;
const asset=(id,role='data',mime='text/plain')=>({url:`${prefix}${id}/${role}`,mime});
function fixture() {
  const entries=[
    {id:'f',parentId:null,name:'Data',kind:'folder',path:'/Data',order:0},
    {id:'d',parentId:'f',name:'Dashboard',kind:'document',path:'/Data/Dashboard',order:0},
    {id:'j',parentId:'f',name:'Config.json',kind:'text-file',path:'/Data/Config.json',order:1},
    {id:'i',parentId:'f',name:'Фото one.png',kind:'image',path:'/Data/%D0%A4%D0%BE%D1%82%D0%BE%20one.png',order:2},
    {id:'m',parentId:'f',name:'Result',kind:'model-result',path:'/Data/Result',order:3},
    {id:'a',parentId:'f',name:'Track',kind:'audio',path:'/Data/Track',order:4},
    {id:'h',parentId:'f',name:'Hidden',displayName:'Visual alias',hideTitle:true,visible:false,kind:'text',path:'/Data/Hidden',order:5},
    {id:'special',parentId:'f',name:'A/B:100%.txt',kind:'text',path:'/Data/A%2FB%3A100%25.txt',order:6},
    ...['heading','location','weather','clock'].map((kind,i)=>({id:kind,parentId:null,name:kind,kind,path:`/${kind}`,order:i+1})),
  ].map(e=>({...e,metadataUrl:`${prefix}${e.id}/meta`,assets:e.kind==='folder'?{}:{data:asset(e.id)}}));
  entries.find(e=>e.id==='m').assets={data:asset('m','data','application/json'),input:asset('m','input','image/png'),mask:asset('m','mask','image/png'),'layer-0':asset('m','layer-0','image/png')};
  entries.find(e=>e.id==='a').assets.cover=asset('a','cover','image/png');
  const result={task:'cv_segmentation',boxes:[{label:'road',xMin:0,yMin:0,xMax:1,yMax:1,confidence:.9}],
    scores:[{label:'class',score:.8}],values:[{label:'score',value:4}],text:'output',
    layers:[{label:'mask',maskUrl:`${prefix}m/layer-0`,polygons:[{rings:[[{x:0,y:0},{x:1,y:0},{x:1,y:1}]]}]}]};
  const responses=new Map();
  for(const e of entries) {
    responses.set(e.metadataUrl,JSON.stringify({...e,text:e.id==='j'?'[1,2,3]':'Text',result:e.id==='m'?result:null}));
    responses.set(e.assets.data?.url,e.id==='m'?JSON.stringify(result):'Hello');
  }
  const manifest={version:1,origin,scope,base:'/Data',documentId:'d',report:{title:'Report'},entries};
  const calls=[];
  const fetchImpl=async(url,options)=>{
    calls.push({url,options});
    return new Response(responses.get(url)||'',{status:responses.has(url)?200:404});
  };
  let access;
  access=createVfs(manifest,{fetchImpl,attachmentFactory:name=>access.resolve(name)});
  return {manifest,responses,calls,...access};
}

test('logical paths follow the document folder and use the same encoded-name convention as native VFS',()=>{
  assert.equal(normalizePath('../Other','/Data'),'/Other');
  assert.equal(normalizePath('Фото one.png','/Data'),'/Data/%D0%A4%D0%BE%D1%82%D0%BE%20one.png');
  assert.equal(normalizePath('A%2FB%3A100%25.txt','/Data'),'/Data/A%2FB%3A100%25.txt');
  for(const bad of ['../../Outside','%2e%2e/Other','file:///private','https://outside.invalid','..\\private','\0bad'])
    assert.throws(()=>normalizePath(bad,'/Data'));
});

test('all block kinds have read-only metadata, logical names resolve and display aliases never resolve',async()=>{
  const f=fixture();
  assert.equal(f.api.stat('Dashboard').kind,'document');
  assert.equal(f.api.stat('Фото one.png').kind,'image');
  assert.equal(f.api.stat('фото ONE.PNG').id,'i');
  assert.equal(f.api.stat('A%2FB%3A100%25.txt').id,'special');
  assert.equal(f.api.exists('A/B:100%.txt'),false);
  assert.equal(f.api.exists('Visual alias'),false);
  assert.equal(f.api.get.constructor.name,'AsyncFunction');
  assert.ok(f.api.list().some(n=>n.id==='h' && n.visible===false));
  assert.deepEqual(f.api.list('/').map(n=>n.kind),['folder','heading','location','weather','clock']);
  assert.equal((await f.api.get('Hidden')).text,'Text');
  assert.equal((await f.api.read('/location')).kind,'location');
  assert.throws(()=>{f.api.stat('Hidden').visible=true;},TypeError);
  assert.throws(()=>f.api.list('Hidden'),/folder/);
});

test('UTF-16 and other imported text formats use native decoded text for text/json access',async()=>{
  const f=fixture();
  assert.equal(await f.api.text('Config.json'),'[1,2,3]');
  assert.deepEqual(await f.api.json('Config.json'),[1,2,3]);
  assert.equal(await f.api.text('Hidden'),'Hello');
  assert.ok(f.calls.every(c=>c.options.credentials==='omit' && c.options.redirect==='error'));
});

test('model APIs preserve every geometry/output type and expose input, masks and audio artwork as attachments',async()=>{
  const f=fixture();const value=await f.api.json('Result');
  assert.equal(value.boxes[0].confidence,.9);
  assert.equal(value.scores[0].score,.8);
  assert.equal(value.values[0].value,4);
  assert.equal(value.layers[0].polygons[0].rings[0].length,3);
  assert.throws(()=>value.values.push({}),TypeError);
  assert.equal(f.api.attachment('Result','input').href,`${prefix}m/input`);
  assert.equal(f.api.attachment('Result','mask').href,`${prefix}m/mask`);
  assert.equal(f.api.attachment('Result','layer-0').href,`${prefix}m/layer-0`);
  assert.equal(f.api.attachment('Track','cover').href,`${prefix}a/cover`);
  assert.throws(()=>f.api.url('Result','outside'),/No outside/);
  assert.throws(()=>f.resolve('/Data'),/folder/);
});

test('a document cannot forge a different origin, resource capability, duplicate path or duplicate id',()=>{
  for(const change of [
    m=>{m.origin='https://appassets.androidplatform.net';},
    m=>{m.entries[1].metadataUrl='https://outside.invalid/secret';},
    m=>{m.entries[1].assets.data.url=`${origin}/vfs/other-secret/item/id/data`;},
    m=>{m.entries[1].id=m.entries[0].id;},
    m=>{m.entries[1].path=m.entries[0].path;},
  ]) {
    const m=structuredClone(fixture().manifest);change(m);assert.throws(()=>createVfs(m));
  }
  const f=fixture();assert.throws(()=>f.resolve('@folio:missing:data'),/not found/);
});

test('separate report manifests and volatile state stores cannot read or overwrite one another',()=>{
  const first=fixture(),m=structuredClone(first.manifest);m.entries=m.entries.filter(n=>n.id!=='h');
  const second=createVfs(m);
  assert.ok(first.api.exists('Hidden'));assert.equal(second.api.exists('Hidden'),false);
  const a=memoryStorage(),b=memoryStorage();a.setItem('private','secret');assert.equal(b.getItem('private'),null);
  b.load(a.dump());assert.equal(b.getItem('private'),'secret');
  b.load('alert("not executable")');assert.equal(b.getItem('private'),'secret');
  b.load('[["__proto__","text"]]');assert.equal(Object.prototype.text,undefined);
  b.setItem('large','x'.repeat(65537));assert.equal(b.getItem('large'),null);
});

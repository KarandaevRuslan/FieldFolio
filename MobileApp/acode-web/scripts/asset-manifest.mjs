import {readFile,readdir,writeFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {resolve,dirname,relative} from 'node:path';
import {fileURLToPath} from 'node:url';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const unix=path=>path.split('\\').join('/');
async function walk(path){
 const out=[];
 for(const entry of await readdir(path,{withFileTypes:true})){
  const child=resolve(path,entry.name);if(entry.isDirectory())out.push(...await walk(child));else out.push(child);
 }
 return out;
}
const inputs=[];
for(const dir of ['src','scripts','public','document-public','vendor'])inputs.push(...await walk(resolve(root,'acode-web',dir)));
for(const name of ['package.json','package-lock.json','vite.config.mjs','editor.html','document.html','document-host.html'])inputs.push(resolve(root,'acode-web',name));
inputs.push(...await walk(resolve(root,'docs/api')));
const assets=[];
for(const dir of ['acode','document','help'])assets.push(...await walk(resolve(root,'app/src/main/assets',dir)));
async function entries(paths){
 return Promise.all(paths.map(async path=>({path:unix(relative(root,path)),sha256:hash(await readFile(path))})));
}
const manifest={version:1,inputs:(await entries(inputs)).sort((a,b)=>a.path.localeCompare(b.path,'en')),
 assets:(await entries(assets)).sort((a,b)=>a.path.localeCompare(b.path,'en'))};
await writeFile(resolve(root,'app/src/main/assets/web-assets.json'),JSON.stringify(manifest,null,2)+'\n');

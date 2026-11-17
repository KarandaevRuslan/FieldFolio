import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {resolve,dirname} from 'node:path';

// Canonical docs remain normal Markdown. Notebook Kit Markdown needs literal ${...} escaped.
const root=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
for(const language of ['en','ru']){
 const source=await readFile(resolve(root,`docs/api/document-${language}.md`),'utf8');
 const compiled=source.replaceAll('${','\\${');
 const target=resolve(root,`app/src/main/assets/help/document-${language}.md`);
 if(process.argv.includes('--check')){
  if(await readFile(target,'utf8')!==compiled)throw new Error(`Regenerate ${target} with npm run build`);
 }else{
  await mkdir(dirname(target),{recursive:true});await writeFile(target,compiled);
 }
}

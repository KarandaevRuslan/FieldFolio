import test from 'node:test';
import assert from 'node:assert/strict';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';
import {dirname,resolve} from 'node:path';

const root=resolve(dirname(fileURLToPath(import.meta.url)),'..');

test('the real document bundle cannot import privileged editor/host modules',async()=>{
 const result=await build({absWorkingDir:root,entryPoints:['src/document/index.js'],bundle:true,write:false,
  format:'iife',metafile:true,loader:{'.css':'empty','.scss':'empty'},logLevel:'silent',
  plugins:[{name:'optional-offline-libraries',setup(builder){builder.onResolve({filter:/.*/},args=>args.kind==='dynamic-import'?{path:args.path,external:true}:null);}}]});
 const modules=Object.keys(result.metafile.inputs);
 assert.ok(modules.some(path=>path.includes('src/document/viewport.js')));
 assert.ok(modules.some(path=>path.includes('src/acode/vendor/components/scrollbar/index.js')),'uses the existing scrollbar implementation');
 assert.equal(modules.some(path=>path.startsWith('src/editor/')||path.startsWith('src/acode/host/')),false);
 for(const output of result.outputFiles)assert.equal(output.text.includes('window.NativeEditor'),false,'no native editor bridge in renderer graph');
});

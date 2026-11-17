import {defineConfig} from 'vite';
import {resolve} from 'node:path';
import {acodeAliases,acodeJsxPlugin,acodeRuntimeAudit} from './scripts/acode-build.mjs';
export default defineConfig(({mode}) => {
 const editor=mode!=='document';
 return {base:'./',publicDir:editor?'public':'document-public',plugins:editor?[acodeJsxPlugin,acodeRuntimeAudit]:[],
 resolve:{alias:acodeAliases,dedupe:['@codemirror/state','@codemirror/view','@codemirror/language','@codemirror/search','@codemirror/commands','@codemirror/autocomplete']},
 css:{postcss:{plugins:[{postcssPlugin:'offline-inputs-css',AtRule:{import(rule){
  if(/cdn\.jsdelivr\.net\/npm\/@observablehq\/inputs\/dist\/index\.css/.test(rule.params))rule.remove();
 }}}]}},
 build:{target:'es2022',cssCodeSplit:false,outDir:editor?'../app/src/main/assets/acode':'../app/src/main/assets/document',
 emptyOutDir:true,rollupOptions:{input:editor?resolve(import.meta.dirname,'editor.html'):{document:resolve(import.meta.dirname,'document.html'),host:resolve(import.meta.dirname,'document-host.html')}}}};
});

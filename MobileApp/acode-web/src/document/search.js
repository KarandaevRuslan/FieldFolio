import {searchText} from '../search/query.js';
import {readOnlySearch} from '../search/readOnly.js';
/** HTML adapter for the very same panel/state used by read-only Acode. DOM stays intact. */
export function documentSearch(root,{storage,onReveal=()=>{}}={}) {
 const doc=root.ownerDocument,win=doc.defaultView;let ranges=[],observer,timer;
 const restore=()=>{try{return JSON.parse(storage?.getItem('fieldfolio:search')||'{}');}catch{return {};}};
 const panel=readOnlySearch({
  match(state){const nodes=[],walker=doc.createTreeWalker(root,win.NodeFilter.SHOW_TEXT);let length=0,node;
   while((node=walker.nextNode())&&length<500000){const parent=node.parentElement;
    if(!parent||parent.closest('script,style,textarea,input,[hidden],.cell-output-empty'))continue;
    const text=node.textContent.slice(0,500000-length);nodes.push({node,text,from:length,to:length+text.length});length+=text.length;
   }
   const result=searchText(nodes.map(n=>n.text).join(''),state);ranges=[];
   for(const match of result.matches){const first=nodes.find(n=>match.from>=n.from&&match.from<n.to),last=nodes.find(n=>match.to>n.from&&match.to<=n.to);if(!first||!last)continue;
    const range=doc.createRange();range.setStart(first.node,match.from-first.from);range.setEnd(last.node,match.to-last.from);ranges.push(range);
   }
   return {...result,matches:ranges};
  },
  paint(matches,index){const css=win.CSS;if(css?.highlights&&win.Highlight){css.highlights.set('folio-matches',new win.Highlight(...matches));css.highlights.set('folio-active',new win.Highlight(...(matches[index]?[matches[index]]:[])));}},
  reveal(range){if(!win.CSS?.highlights){const selection=doc.getSelection();selection.removeAllRanges();selection.addRange(range);}
   range.startContainer.parentElement?.scrollIntoView?.({block:'center',behavior:'instant'});onReveal();
  },
  dispose(){win.CSS?.highlights?.delete('folio-matches');win.CSS?.highlights?.delete('folio-active');},
 },{doc,state:restore(),onChange:value=>storage?.setItem('fieldfolio:search',JSON.stringify(value)),onLayout(){
  win.requestAnimationFrame(()=>{const height=doc.querySelector('.folio-find')?.getBoundingClientRect().height||0;doc.documentElement.style.setProperty('--folio-find-height',`${height+18}px`);});
 }});
 observer=new win.MutationObserver(()=>{clearTimeout(timer);timer=setTimeout(()=>panel.refresh(),100);});observer.observe(root,{childList:true,subtree:true,characterData:true});
 const resize=()=>{const height=doc.querySelector('.folio-find')?.getBoundingClientRect().height||0;doc.documentElement.style.setProperty('--folio-find-height',`${height+18}px`);};win.addEventListener('resize',resize);
 return {...panel,importState(){panel.restore(restore());},dispose(){observer.disconnect();clearTimeout(timer);win.removeEventListener('resize',resize);panel.dispose();}};
}

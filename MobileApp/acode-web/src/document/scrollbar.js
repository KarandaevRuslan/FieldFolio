import ScrollBar from '../acode/vendor/components/scrollbar/index.js';
import './scrollbar.css';
/** Bind Acode's existing draggable scrollbar to the document viewport. No second thumb engine. */
export function documentScrollbar(viewport,doc=document,win=window) {
 const overlay=doc.createElement('div');overlay.className='folio-scroll-overlay';doc.body.append(overlay);
 const max=()=>Math.max(0,doc.documentElement.scrollHeight-win.innerHeight);
 const bar=ScrollBar({parent:overlay,persistent:true,width:8,thumbHeight:50,onscroll(value){
  win.scrollTo({top:value*max(),left:win.scrollX||0,behavior:'instant'});viewport.changedByControl();
 }});
 bar.setAttribute('role','scrollbar');bar.tabIndex=0;bar.setAttribute('aria-orientation','vertical');bar.setAttribute('aria-label','Scroll');bar.setAttribute('aria-controls','notebook');
 let frame;
 function update(){frame=undefined;const extent=max();
  if(doc.documentElement.dataset.mini==='true'||extent<=1){bar.hideImmediately();return;}
  bar.show();bar.value=Math.max(0,win.scrollY||0)/extent;bar.setAttribute('aria-valuemin','0');bar.setAttribute('aria-valuemax',String(Math.round(extent)));bar.setAttribute('aria-valuenow',String(Math.round(win.scrollY||0)));
 }
 const schedule=()=>{if(!frame)frame=win.requestAnimationFrame(update);};
 const observer=new win.ResizeObserver(schedule);observer.observe(doc.documentElement);observer.observe(doc.body);
 const mutation=new win.MutationObserver(schedule);mutation.observe(doc.getElementById('notebook'),{childList:true,subtree:true});
 const mode=new win.MutationObserver(schedule);mode.observe(doc.documentElement,{attributes:true,attributeFilter:['data-mini']});
 win.addEventListener('scroll',schedule,{passive:true});win.addEventListener('resize',schedule);
 const key=e=>{const amount={ArrowDown:40,ArrowUp:-40,PageDown:win.innerHeight*.8,PageUp:-win.innerHeight*.8}[e.key];
  if(amount!==undefined||e.key==='Home'||e.key==='End'){e.preventDefault();const y=e.key==='Home'?0:e.key==='End'?max():Math.max(0,Math.min(max(),win.scrollY+amount));win.scrollTo({top:y,left:win.scrollX||0,behavior:'instant'});viewport.changedByControl();}
 };bar.addEventListener('keydown',key);schedule();
 return {refresh:schedule,dispose(){observer.disconnect();mutation.disconnect();mode.disconnect();win.cancelAnimationFrame(frame);win.removeEventListener('scroll',schedule);win.removeEventListener('resize',schedule);bar.removeEventListener('keydown',key);bar.destroy();bar.remove();overlay.remove();}};
}

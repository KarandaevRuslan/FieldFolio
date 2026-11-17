/** Keep the user's logical scroll target through native resize/reparent; mini is never scrollable. */
export function documentViewport(storage,doc=globalThis.document,win=globalThis.window) {
 doc.documentElement.dataset.mini='true';
 let mini=true,restoring=false,layoutChanging=false,timer,resizeTimer,userUntil=0;
 let topLocked=false,originalOverflow='',topStopTimer,topGuardUntil=0;
 let width=win.innerWidth,height=win.innerHeight,contentHeight=doc.documentElement.scrollHeight;
 let saved;
 try{saved=JSON.parse(storage.getItem('fieldfolio:viewport')||'null');}catch{}
 let target={x:Number.isFinite(saved?.x)?Math.max(0,saved.x):0,y:Number.isFinite(saved?.y)?Math.max(0,saved.y):0};
 const save=()=>storage.setItem('fieldfolio:viewport',JSON.stringify(target));
 // Briefly make the iframe's root non-scrollable to interrupt native WebView momentum.
 // A short scroll-event guard catches any remaining frames without disabling the next swipe.
 const unlockTop=()=>{
  if(!topLocked)return;
  doc.documentElement.style.overflow=originalOverflow;
  topLocked=false;
 };
 const lockTop=()=>{
  if(!topLocked)originalOverflow=doc.documentElement.style.overflow;
  topLocked=true;
  doc.documentElement.style.overflow='hidden';
 };
 const stopMomentum=()=>{
  lockTop();
  clearTimeout(topStopTimer);
  topStopTimer=setTimeout(unlockTop,120);
 };
 const cancelTopGuard=()=>{
  topGuardUntil=0;
  clearTimeout(topStopTimer);
  unlockTop();
 };
 const capture=()=>{if(Date.now()>=topGuardUntil && !topLocked && !restoring && !layoutChanging){target={x:Math.max(0,win.scrollX||0),y:Math.max(0,win.scrollY||0)};clearTimeout(timer);timer=setTimeout(save,80);}};
 const restore=()=>{
  restoring=true;win.scrollTo({left:target.x,top:target.y,behavior:'instant'});
  width=win.innerWidth;height=win.innerHeight;contentHeight=doc.documentElement.scrollHeight;
  win.requestAnimationFrame(()=>{restoring=false;layoutChanging=false;});
 };
 const input=event=>{
  // Momentum emits scroll events, but it does not begin a fresh pointer/wheel/key gesture.
  if(event.type==='pointerdown'||event.type==='touchstart'||event.type==='wheel'||event.type==='keydown')cancelTopGuard();
  if(!mini){layoutChanging=false;width=win.innerWidth;height=win.innerHeight;userUntil=Date.now()+1500;}
 };
 const scroll=()=>{
  if(topLocked || Date.now()<topGuardUntil){
   // A late fling frame must not move the page or overwrite the saved zero.
   if((win.scrollX||0)!==0||(win.scrollY||0)!==0){
    if(!topLocked)stopMomentum();
    topGuardUntil=Date.now()+800;
    win.scrollTo({left:0,top:0,behavior:'instant'});
   }
   return;
  }
  if(win.innerWidth!==width || win.innerHeight!==height){layoutChanging=true;queueRestore();return;}
  if(!mini && Date.now()<userUntil){userUntil=Date.now()+400;capture();}
 };
 for(const event of ['pointerdown','pointermove','touchstart','touchmove','wheel','keydown'])win.addEventListener(event,input,{passive:true});
 const blockMini=event=>{if(mini)event.preventDefault();};
 win.addEventListener('wheel',blockMini,{passive:false});win.addEventListener('touchmove',blockMini,{passive:false});
 win.addEventListener('scroll',scroll,{passive:true});
 const queueRestore=()=>{
  clearTimeout(resizeTimer);
  const remaining=Math.max(0,userUntil-Date.now());
  resizeTimer=setTimeout(()=>{if(Date.now()<userUntil)queueRestore();else win.requestAnimationFrame(restore);},remaining+30);
 };
 const resized=()=>{
  const editable=doc.activeElement?.matches?.('textarea,input:not([type="checkbox"],[type="radio"],[type="range"],[type="button"],[type="submit"])');
  const widthChanged=win.innerWidth!==width;
  if(editable&&!widthChanged){height=win.innerHeight;layoutChanging=false;userUntil=Date.now()+1500;return;}
  // A focused find field must not make an orientation change look like an IME-only resize.
  if(widthChanged)userUntil=0;
  layoutChanging=true;queueRestore();
 };
 win.addEventListener('resize',resized);
 const observer=new win.ResizeObserver(resized);
 observer.observe(doc.documentElement);
 const content=new win.MutationObserver(()=>{
  const next=doc.documentElement.scrollHeight;
  if(next!==contentHeight){contentHeight=next;if(Date.now()>=userUntil)layoutChanging=true;queueRestore();}
 });
 content.observe(doc.getElementById('notebook')||doc.body,{subtree:true,childList:true});
 return {importState(){
  cancelTopGuard();
  try{const value=JSON.parse(storage.getItem('fieldfolio:viewport')||'null');if(Number.isFinite(value?.x)&&Number.isFinite(value?.y))target={x:Math.max(0,value.x),y:Math.max(0,value.y)};}catch{}
  win.requestAnimationFrame(restore);
 },mode(expanded){cancelTopGuard();if(mini===!expanded)return;layoutChanging=true;mini=!expanded;doc.documentElement.dataset.mini=String(mini);win.requestAnimationFrame(restore);},
  restore,toTop(){
   clearTimeout(timer); // A delayed pre-click capture must not overwrite the reset.
   userUntil=0;
   target={x:0,y:0};save();
   topGuardUntil=Date.now()+800;
   stopMomentum();restore();
  },changedByControl(){cancelTopGuard();userUntil=Date.now()+1500;layoutChanging=false;capture();},changedBySearch(){cancelTopGuard();userUntil=Date.now()+1500;win.requestAnimationFrame(capture);},
  dispose(){cancelTopGuard();save();clearTimeout(timer);clearTimeout(resizeTimer);observer.disconnect();content.disconnect();win.removeEventListener('resize',resized);win.removeEventListener('wheel',blockMini);win.removeEventListener('touchmove',blockMini);win.removeEventListener('scroll',scroll);for(const event of ['pointerdown','pointermove','touchstart','touchmove','wheel','keydown'])win.removeEventListener(event,input);}};
}

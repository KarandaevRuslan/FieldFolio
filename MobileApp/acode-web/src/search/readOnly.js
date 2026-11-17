import {normalizeSearch} from './query.js';
/** Shared read-only Acode/HTML find UI. Adapters own matching/highlighting, never this panel. */
export function readOnlySearch(adapter,{doc=globalThis.document,state:initial={},onChange=()=>{},onLayout=()=>{}}={}) {
 let state=normalizeSearch(initial),language='en',matches=[],invalid=false,timer,disposed=false;
 const panel=doc.createElement('form');panel.className='folio-find';panel.dataset.folioSearch='';panel.hidden=true;
 const row=doc.createElement('div');row.className='folio-find-row';
 const input=doc.createElement('input');input.type='search';input.maxLength=512;input.autocomplete='off';input.spellcheck=false;
 const status=doc.createElement('output');status.setAttribute('aria-live','polite');
 const options=doc.createElement('div');options.className='folio-find-options';
 function button(text,label,action,parent=row){const el=doc.createElement('button');el.type='button';el.textContent=text;el.dataset.findAction=label;el.addEventListener('click',action);parent.append(el);return el;}
 row.append(input);
 const prev=button('←','previous',()=>step(-1)),next=button('→','next',()=>step(1));
 const settings=button('⚙','settings',()=>set({...state,settingsOpen:!state.settingsOpen}));
 const close=button('×','close',()=>set({...state,open:false}));
 const matchCase=button('Aa','case',()=>toggle('matchCase'),options),regex=button('.*','regex',()=>toggle('regex'),options),whole=button('ab','whole',()=>toggle('whole'),options);
 options.append(status);panel.append(row,options);doc.body.append(panel);
 function strings(){const ru=language==='ru';input.placeholder=ru?'Поиск':'Search';input.setAttribute('aria-label',input.placeholder);
  for(const [el,en,rus] of [[prev,'Previous match','Предыдущее совпадение'],[next,'Next match','Следующее совпадение'],[settings,'Search settings','Настройки поиска'],[close,'Close search','Закрыть поиск'],[matchCase,'Match case','Учитывать регистр'],[regex,'Regular expression','Регулярное выражение'],[whole,'Whole word','Целое слово']]){el.title=ru?rus:en;el.setAttribute('aria-label',el.title);}}
 const active=()=>Math.min(state.index,Math.max(0,matches.length-1));
 function persist(){onChange({...state});}
 function paint(){
  status.textContent=invalid?(language==='ru'?'Ошибка RegExp':'Invalid RegExp'):`${matches.length?active()+1:0} / ${matches.length}`;
  input.setAttribute('aria-invalid',String(invalid));prev.disabled=next.disabled=!matches.length;
  adapter.paint?.(matches,state.open?active():-1,state);
 }
 function refresh(reveal=false){if(disposed)return;clearTimeout(timer);
  const result=state.open?adapter.match(state):{matches:[],invalid:false};matches=result.matches;invalid=result.invalid;paint();
  if(reveal&&matches.length)adapter.reveal(matches[active()],active());
 }
 function layout(){panel.hidden=!state.open;options.classList.toggle('expanded',state.settingsOpen);
  for(const [el,key] of [[matchCase,'matchCase'],[regex,'regex'],[whole,'whole']])el.setAttribute('aria-pressed',String(state.options[key]));
  settings.setAttribute('aria-expanded',String(state.settingsOpen));
  doc.documentElement.dataset.find=String(state.open);onLayout();
 }
 function set(value,{focus=false,reveal=false}={}){state=normalizeSearch(value);input.value=state.query;input.setSelectionRange(state.selectionStart,state.selectionEnd);layout();refresh(reveal);persist();
  if(focus){input.focus({preventScroll:true});input.setSelectionRange(state.selectionStart,state.selectionEnd);}}
 function step(direction){if(matches.length){state={...state,index:(active()+direction+matches.length)%matches.length};paint();adapter.reveal(matches[active()],active());persist();}}
 function toggle(key){set({...state,index:0,options:{...state.options,[key]:!state.options[key]}},{reveal:true});}
 input.addEventListener('input',()=>{state={...state,query:input.value,index:0,selectionStart:input.selectionStart||0,selectionEnd:input.selectionEnd||0};persist();clearTimeout(timer);timer=setTimeout(()=>refresh(true),120);});
 const rememberSelection=()=>{state={...state,selectionStart:input.selectionStart||0,selectionEnd:input.selectionEnd||0};persist();};
 input.addEventListener('select',rememberSelection);input.addEventListener('keyup',rememberSelection);input.addEventListener('pointerup',rememberSelection);
 panel.addEventListener('submit',e=>{e.preventDefault();step(1);});
 const keyboard=e=>{if((e.ctrlKey||e.metaKey)&&e.key==='f'){e.preventDefault();e.stopImmediatePropagation();open();}else if(e.key==='Escape'&&state.open){e.preventDefault();e.stopImmediatePropagation();set({...state,open:false});}else if(e.key==='Enter'&&doc.activeElement===input){e.preventDefault();step(e.shiftKey?-1:1);}};
 doc.addEventListener('keydown',keyboard,true);
 function open(){set({...state,open:true},{focus:true});}
 strings();set(state);
 return {open,close(){if(!state.open)return false;set({...state,open:false});return true;},refresh,snapshot:()=>({...state}),restore(value){set(value);},preferences(value={}){language=value.language==='ru'?'ru':'en';strings();paint();},dispose(){disposed=true;clearTimeout(timer);doc.removeEventListener('keydown',keyboard,true);adapter.dispose?.();panel.remove();}};
}

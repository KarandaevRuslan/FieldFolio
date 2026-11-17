import {SearchQuery} from '@codemirror/search';
import {Text} from '@codemirror/state';
/** Same query model/engine as Acode, reused for rendered documents and help. */
export function normalizeSearch(state={}) {
 state=state||{};
 return {open:!!state.open,index:Math.max(0,Math.min(1000000,Math.floor(Number(state.index)||0))),settingsOpen:!!state.settingsOpen,selectionStart:Math.max(0,Number(state.selectionStart)||0),selectionEnd:Math.max(0,Number(state.selectionEnd)||0),query:String(state.query||'').slice(0,512),options:{matchCase:!!state.options?.matchCase,regex:!!state.options?.regex,whole:!!state.options?.whole}};
}
export function searchText(text,state,limit=1000) {
 const value=normalizeSearch(state),query=new SearchQuery({search:value.query,caseSensitive:value.options.matchCase,regexp:value.options.regex,wholeWord:value.options.whole});
 if(!value.query)return {matches:[],invalid:false};
 if(!query.valid)return {matches:[],invalid:true};
 const cursor=query.getCursor(Text.of([text]));const matches=[];
 while(matches.length<limit && !cursor.next().done) if(cursor.value.to>cursor.value.from)matches.push({from:cursor.value.from,to:cursor.value.to});
 return {matches,invalid:false};
}

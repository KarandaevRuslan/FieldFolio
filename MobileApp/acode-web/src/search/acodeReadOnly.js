import {SearchQuery,setSearchQuery} from '@codemirror/search';
import {EditorView} from '@codemirror/view';
import {searchText} from './query.js';
import {readOnlySearch} from './readOnly.js';
/** CodeMirror adapter only. Query controls, navigation, errors and state live in readOnlySearch. */
export function acodeReadOnlySearch(editor,onChange) {
 const panel=readOnlySearch({
  match:state=>searchText(editor.state.doc.toString(),state),
  paint(_matches,_index,state){editor.dispatch({effects:setSearchQuery.of(new SearchQuery({search:state.open?state.query:'',caseSensitive:state.options.matchCase,regexp:state.options.regex,wholeWord:state.options.whole}))});},
  reveal(match){editor.dispatch({selection:{anchor:match.from,head:match.to},effects:EditorView.scrollIntoView(match.from,{y:'center'})});},
 },{onChange,onLayout(){requestAnimationFrame(()=>{const height=document.querySelector('.folio-find:not([hidden])')?.getBoundingClientRect().height||0;editor.dom.style.paddingTop=`${height}px`;editor.requestMeasure();});}});
 return {...panel,dispose(){panel.dispose();editor.dom.style.paddingTop='';}};
}

import {documentScrollbar} from './scrollbar.js';
import {documentViewport} from './viewport.js';
import {documentSearch} from './search.js';
import {startPreview} from './application.js';
import {memoryStorage} from './storage.js';
import './styles.css';
import '../search/readOnly.css';
// Includes the complete Pocket Notebook Notebook Kit/Inputs theme, including step controls.
import './inputs-theme.css';
let port;
// Either module may finish first: repeat the handshake until the trusted parent supplies its port.
const announce = () => { if (!port && window.parent !== window) window.parent.postMessage({type:'folio-renderer-ready'}, '*'); };
const handshake = setInterval(announce, 100);
const storage = memoryStorage(() => {
  const state=storage.dump();
  if(state.length<=65536) port?.postMessage({type:'state',value:state});
});
const api = startPreview({main:document.getElementById('notebook'),storage});
const viewport=documentViewport(storage);
const scrollbar=documentScrollbar(viewport);
const search=documentSearch(document.getElementById('notebook'),{storage,onReveal:viewport.changedBySearch});
window.addEventListener('message', event => {
  if (port || event.source !== parent || event.data?.type !== 'folio-renderer-port' || !event.ports[0]) return;
  port = event.ports[0];
  clearInterval(handshake);
  port.onmessage = ({data}) => {
    if (!data || typeof data !== 'object') return;
    switch (data.type) {
      case 'preferences': api.setPreferences(data.value); search.preferences(data.value||{}); port.postMessage({type:'styled'}); break;
      case 'render': api.render(data.value.source, data.value.id, data.value.manifest); viewport.restore(); search.refresh(); break;
      case 'viewport': viewport.mode(data.value?.expanded===true); break;
      case 'find': search.open(); break;
      case 'top': viewport.toTop(); break;
      case 'import': api.importState(data.value); viewport.importState(); search.importState(); break;
      case 'reset': api.resetState(); break;
      case 'clock': api.syncClock(data.value); break;
      case 'destroy': scrollbar.dispose(); search.dispose(); viewport.dispose(); api.destroy(); port.close(); break;
    }
  };
  port.start();
});
announce();

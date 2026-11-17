import './host.css';

// This realm never executes document code. The renderer has a unique opaque origin.
const frame = document.getElementById('renderer');
let channel, ready = false, state = '[]';
const queue = [];
const send = (type, value) => {
  if (!ready) { if (queue.length < 16) queue.push({type,value}); return; }
  channel.postMessage({type,value});
};
window.addEventListener('message', event => {
  if (ready || event.source !== frame.contentWindow || event.origin !== 'null' || event.data?.type !== 'folio-renderer-ready') return;
  const pipe = new MessageChannel();
  channel = pipe.port1;
  channel.onmessage = message => {
    const input = message.data;
    if(input?.type==='styled') frame.classList.add('ready');
    if (input?.type === 'state' && typeof input.value === 'string' && input.value.length <= 65536) state = input.value;
  };
  channel.start();
  frame.contentWindow.postMessage({type:'folio-renderer-port'}, '*', [pipe.port2]);
  ready = true;
  for (const item of queue.splice(0)) channel.postMessage(item);
});
const api = Object.freeze({
  isReady:() => ready,
  setPreferences(value) { send('preferences', value); },
  render(source, id, manifest) { send('render', {source,id,manifest}); return true; },
  setViewportMode(expanded) { send('viewport',{expanded:expanded===true}); },
  search() { send('find'); },
  scrollToTop() { send('top'); },
  exportState:() => state,
  importState(value) { if (typeof value === 'string' && value.length <= 65536) { state=value; send('import', value); } },
  resetState() { state='[]'; send('reset'); return true; },
  syncClock(value) { send('clock', value); },
  destroy() { send('destroy'); channel?.close(); ready=false; },
});
Object.defineProperty(window, 'PreviewApi', {value:api, configurable:false, writable:false});

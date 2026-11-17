/** No DOM storage is used: even protected form/timer state stays in memory. */
export function memoryStorage(changed = () => {}) {
  const values = new Map();
  return Object.freeze({
    getItem:key => values.get(key) ?? null,
    setItem(key,value) { const text=String(value); if(text.length<=65536 && (values.has(key)||values.size<128)) { values.set(key,text); changed(); } },
    removeItem(key) { values.delete(key); changed(); },
    dump:() => JSON.stringify([...values]),
    load(value) {
      if(typeof value!=='string'||value.length>262144) return;
      try {
        const entries=JSON.parse(value);
        if(!Array.isArray(entries)||entries.length>128) return;
        const valid=entries.filter(x=>Array.isArray(x)&&x.length===2&&typeof x[0]==='string'&&x[0].length<=512&&typeof x[1]==='string'&&x[1].length<=65536);
        values.clear(); for(const [key,text] of valid) values.set(key,text);
      } catch { /* corrupt runtime state is discarded, never executed */ }
    }
  });
}

/** Report-only, read-only capabilities. Logical paths are never Android/disk paths. */
const freeze = value => {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    for (const child of Object.values(value)) freeze(child);
    Object.freeze(value);
  }
  return value;
};

export function normalizePath(value, base = '/') {
  if (typeof value !== 'string' || value.length > 160000 || /[\\:\x00-\x1f\x7f]/.test(value))
    throw new TypeError('Invalid logical path');
  const result = value.startsWith('/') ? [] : base.split('/').filter(Boolean);
  for (const part of value.split('/')) {
    if (!part || part === '.') continue;
    if (part === '..') {
      if (!result.length) throw new RangeError('Path escapes the report');
      result.pop();
    } else {
      let decoded; try { decoded=decodeURIComponent(part); } catch { decoded=part; }
      // Encoded separators remain a single logical name; they never become Android paths.
      if (decoded === '.' || decoded === '..') throw new TypeError('Encoded traversal is not a logical name');
      result.push(encodeURIComponent(decoded));
    }
  }
  return '/' + result.join('/');
}

export function createVfs(manifest, {attachmentFactory, fetchImpl = globalThis.fetch} = {}) {
  if (!manifest || manifest.version !== 1 || !Array.isArray(manifest.entries) || manifest.entries.length > 1000)
    throw new TypeError('Invalid report manifest');
  const origin = new URL(manifest.origin);
  if (!/^document-[0-9a-f]{32}\.fieldfolio\.invalid$/.test(origin.hostname) || origin.protocol !== 'https:' || origin.port)
    throw new TypeError('Invalid document origin');
  const prefix = `/vfs/${manifest.scope}/`;
  if (!/^[a-z0-9-]{16,80}$/.test(manifest.scope)) throw new TypeError('Invalid capability');
  const checkUrl = value => {
    const url = new URL(value);
    if (url.origin !== origin.origin || !url.pathname.startsWith(prefix) || url.search || url.hash)
      throw new TypeError('Resource outside the active report');
    return url.href;
  };
  const pathKey = path => path.split('/').map(part => decodeURIComponent(part).normalize('NFC').toLowerCase()).join('\0');
  const entries = manifest.entries.map(entry => freeze(structuredClone(entry)));
  const paths = new Map(), ids = new Map();
  for (const entry of entries) {
    const path = normalizePath(entry.path);
    if (path !== entry.path || paths.has(pathKey(path)) || ids.has(entry.id))
      throw new TypeError('Duplicate or invalid report path');
    for (const asset of Object.values(entry.assets || {})) checkUrl(asset.url);
    checkUrl(entry.metadataUrl);
    paths.set(pathKey(path), entry);
    ids.set(entry.id, entry);
  }
  const base = normalizePath(manifest.base || '/');
  const root = freeze({id:null, name:'', path:'/', kind:'folder', parentId:null, order:0});
  const stat = path => {
    const key = normalizePath(path, base);
    const entry = key === '/' ? root : paths.get(pathKey(key));
    if (!entry) throw new Error(`Report item not found: ${key}`);
    return entry;
  };
  const asset = (entry, role = 'data') => {
    const value = entry.assets?.[role];
    if (!value) throw new Error(`No ${role} resource for ${entry.path}`);
    return value;
  };
  const fetchResource = async url => {
    const response = await fetchImpl(checkUrl(url), {cache:'no-store', credentials:'omit', redirect:'error'});
    if (!response.ok) throw new Error(`Report resource failed: ${response.status}`);
    return response;
  };
  const resolver = name => {
    let entry, role = 'data';
    if (typeof name === 'string' && name.startsWith('@folio:')) {
      const match = /^@folio:([^:]+):([a-z0-9-]+)$/.exec(name);
      if (!match) throw new TypeError('Invalid attachment');
      entry = ids.get(decodeURIComponent(match[1])); role = match[2];
      if (!entry) throw new Error('Report item not found');
    } else entry = stat(name);
    if (entry.kind === 'folder') throw new TypeError('A folder is not an attachment');
    const resource = asset(entry, role);
    return {href:checkUrl(resource.url), mimeType:resource.mime};
  };
  const api = {
    root:'/', base,
    paths:freeze(entries.map(entry => entry.path)),
    stat,
    exists(path) { try { stat(path); return true; } catch { return false; } },
    list(path = '.') {
      const folder = stat(path);
      if (folder.kind !== 'folder') throw new TypeError('Not a report folder');
      return freeze(entries.filter(entry => entry.parentId === folder.id).sort((a,b) => a.order-b.order || a.path.localeCompare(b.path)));
    },
    async get(path) {
      const entry = stat(path);
      if (entry === root) return freeze({...root, children:api.list('/')});
      return freeze(await (await fetchResource(entry.metadataUrl)).json());
    },
    read(path) { return api.get(path); },
    async text(path) {
      if (stat(path).kind === 'text-file') {
        const metadata=await api.get(path);
        if (typeof metadata.text === 'string') return metadata.text;
      }
      return (await fetchResource(asset(stat(path)).url)).text();
    },
    async json(path) {
      if (stat(path).kind === 'text-file') return freeze(JSON.parse(await api.text(path)));
      return freeze(await (await fetchResource(asset(stat(path)).url)).json());
    },
    async blob(path, role = 'data') { return (await fetchResource(asset(stat(path), role).url)).blob(); },
    url(path, role = 'data') { return checkUrl(asset(stat(path), role).url); },
    attachment(path, role = 'data') {
      const entry = stat(path); asset(entry, role);
      if (!attachmentFactory) throw new Error('Attachment runtime not ready');
      return attachmentFactory(`@folio:${encodeURIComponent(entry.id)}:${role}`);
    },
    image(path, options) { return api.attachment(path).image(options); },
  };
  return {api:freeze(api), resolve:resolver, report:freeze(structuredClone(manifest.report || {})),
    document:manifest.documentId ? ids.get(manifest.documentId) : undefined};
}

import { deserialize, transpile } from '@observablehq/notebook-kit';
import { NotebookRuntime } from '@observablehq/notebook-kit/runtime';
import { offlineLibrary } from './observable-offline-library.js';
import { createVfs } from './vfs.js';
import { Nexus, createNexusHost } from './nexusRuntime.js';
import { notebookLog } from '../logging/NotebookLog.js';

export function errorNode(message) {
  const node = document.createElement('pre');
  node.className = 'error';
  node.textContent = String(message);
  return node;
}

export function renderNotebook({
  main,
  source,
  message,
  context,
  attachments,
}) {
  if (!/<notebook(?:\s|>)/i.test(source)) throw new Error(message('invalid'));
  const notebook = deserialize(source);
  if (!notebook.cells.length) {
    const emptyNode = document.createElement('p');
    emptyNode.className = 'empty';
    emptyNode.textContent = message('empty');
    main.append(emptyNode);
    return undefined;
  }
  const runtime = new NotebookRuntime(offlineLibrary);
  const nexusHost = createNexusHost({
    observe: offlineLibrary.Generators().observe,
    onError: () => notebookLog.error('PREVIEW.NEXUS', 'Nexus reaction or cell cleanup failed'),
  });
  runtime.main.builtin('Nexus', Nexus);
  runtime.main.builtin('nexus', nexusHost.context);
  // Every caller, including setup failures, uses the same lifecycle boundary.
  const dispose = () => {
    try { nexusHost.dispose(); }
    catch {
      notebookLog.error('PREVIEW.NEXUS', 'Nexus preview cleanup failed');
    } finally { runtime.runtime.dispose(); }
  };
  try {
    const Pocket = context;
    let files;
    const access = createVfs(attachments, {attachmentFactory: name => files(name)});
    files = runtime.runtime.fileAttachments(access.resolve);
    const Folio = Object.freeze(Object.assign(Object.create(Pocket), {
      vfs:access.api, report:access.report, document:access.document
    }));
    runtime.main.builtin('Pocket', Pocket); // imported NotebookPocket notebooks keep their context API
    runtime.main.builtin('Folio', Folio);
    runtime.main.builtin('Vfs', access.api);
    const now = () => offlineLibrary.Generators().observe(change => {
      change(Pocket.now());
      const interval = setInterval(() => change(Pocket.now()), 1000);
      return () => clearInterval(interval);
    });
    runtime.main.builtin('pocketNow', now());
    runtime.main.builtin('folioNow', now());
    runtime.main.builtin('FileAttachment', files);
    for (const cell of notebook.cells) {
      const root = document.createElement('section');
      root.className = 'cell';
      root.dataset.cellId = String(cell.id);
      root.classList.add('cell-output-empty');
      main.append(root);
      try {
        if (!['js', 'md', 'html', 'ts', 'ojs', 'tex'].includes(cell.mode)) {
          root.append(
            errorNode(message('unsupported', { id: cell.id, mode: cell.mode })),
          );
          continue;
        }
        const compiled = transpile(cell);
        if (compiled.imports?.size) {
          root.append(errorNode(message('imports')));
          continue;
        }
        // Notebook Kit returns an arrow-function body and dependency metadata.
        // The body is evaluated only in this isolated, bridge-free preview WebView.
        const body = new Function(`return (${compiled.body})`)();
        runtime.define(
          { root, variables: [] },
          {
            ...compiled,
            id: cell.id,
            body,
          },
        );
      } catch (error) {
        root.append(
          errorNode(`${message('cell')} ${cell.id}: ${error?.stack || error}`),
        );
      }
    }
    return { runtime: runtime.runtime, dispose };
  } catch (error) {
    dispose();
    throw error;
  }
}

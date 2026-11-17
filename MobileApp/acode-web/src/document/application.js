import {memoryStorage} from './storage.js';
import {createPocketHost} from './pocket-context.js';
import {notebookStateKey, clearNotebookState} from './preview-state.js';
import {messagesFor, formatMessage} from '../locales/index.js';
import {createFormState} from './formState.js';
import {updateCellVisibility} from './cellPresentation.js';
import {renderNotebook, errorNode} from './notebookRuntime.js';

/** State is memory-only, including forms and timers in password-protected reports. */
export function startPreview({main, storage = memoryStorage(), pocketHost = createPocketHost()}) {
  const forms = createFormState(main, storage);
  let current, observer, language = 'en', last;
  const message = (key, replacements = {}) => formatMessage(messagesFor(language)[key], replacements);
  const api = {
    setPreferences(options = {}) {
      pocketHost.configure(options);
      language = options.language === 'ru' ? 'ru' : 'en';
      document.documentElement.lang = language;
      document.documentElement.dataset.theme = options.dark ? 'dark' : 'light';
      document.title = 'Fieldfolio · Document';
    },
    render(source, id, manifest) {
      observer?.disconnect(); current?.dispose(); current = undefined;
      last = {source, id, manifest};
      const key = notebookStateKey(id, source);
      forms.begin(key, false); // private injected memory storage; never DOM/localStorage
      main.replaceChildren();
      try {
        current = renderNotebook({main, source, message, attachments:manifest,
          context:pocketHost.context({key, storage, protectedMode:false})});
        observer = new MutationObserver(() => { updateCellVisibility(main); forms.restore(); });
        observer.observe(main, {subtree:true, childList:true});
        updateCellVisibility(main); forms.restore();
      } catch (error) { main.append(errorNode(error?.stack || error)); }
      return true;
    },
    resetState() {
      if (!last) return false;
      const key = clearNotebookState(storage, last.id, last.source);
      pocketHost.resetTimers(key, storage);
      api.render(last.source, last.id, last.manifest);
      return true;
    },
    exportState() { return storage.dump(); },
    importState(value) { storage.load(value); },
    syncClock:pocketHost.syncClock,
    destroy() {
      observer?.disconnect(); current?.dispose(); current = undefined;
      forms.destroy(); main.replaceChildren();
    },
  };
  return Object.freeze(api);
}

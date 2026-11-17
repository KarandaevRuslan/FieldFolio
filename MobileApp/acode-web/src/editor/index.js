import {acodeReadOnlySearch} from '../search/acodeReadOnly.js';
import '../search/readOnly.css';
import {normalizeSearch} from '../search/query.js';
import {languageForFile} from '../acode/host/resourceLanguage.js';
/** Acode's editor runtime hosted in NotebookPocket's single WebView.
 * The host owns document I/O, preferences and lifecycle; Acode owns editing UI.
 */
import { manager, setLanguage } from '../acode/host/environment.js';
import settings from '../acode/host/settings.js';
import { EditorState, EditorSelection, Compartment } from '@codemirror/state';
import { EditorView, lineNumbers } from '@codemirror/view';
import { search } from '@codemirror/search';
import { foldEffect, unfoldEffect, indentUnit } from '@codemirror/language';
import { autocompletion } from '@codemirror/autocomplete';
import { javascriptLanguage } from '@codemirror/lang-javascript';
import { oneDark } from '@codemirror/theme-one-dark';
import createBaseExtensions from '../acode/vendor/cm/baseExtensions.ts';
import createMainEditorExtensions from '../acode/vendor/cm/mainEditorExtensions.ts';
import createEditorView from '../acode/vendor/cm/createEditorView.ts';
import { getDocText, getAllFolds, restoreFolds } from '../acode/vendor/cm/editorUtils.ts';
import { handleLineNumberClick } from '../acode/vendor/cm/lineNumberSelection.ts';
import quickToolsModifierInput from '../acode/vendor/cm/quickToolsModifierInput.ts';
import { indentedLineWrapping } from '../acode/vendor/cm/indentedLineWrapping.ts';
import scrollPastEnd, { horizontalScrollPastEnd } from '../acode/vendor/cm/scrollPastEnd.ts';
import localWordCompletions from '../acode/vendor/cm/localWordCompletions.ts';
import indentGuides from '../acode/vendor/cm/indentGuides.ts';
import tagAutoRename from '../acode/vendor/cm/tagAutoRename.ts';
import rainbowBrackets from '../acode/vendor/cm/rainbowBrackets.ts';
import { createEditorReadOnlyExtension, reconfigureEditorReadOnly } from '../acode/vendor/cm/editorReadOnly.ts';
import { initIconTooltips } from '../acode/vendor/components/tooltip/index.js';
import githubLight from '../acode/vendor/cm/themes/githubLight.js';
import { createSinglePaneRuntime } from '../acode/vendor/lib/singlePaneRuntime.js';
import { getCommandKeymapExtension, executeCommand, registerExternalCommand, refreshCommandKeymap } from '../acode/vendor/cm/commandRegistry.js';
import quickTools from '../acode/vendor/components/quickTools/index.js';
import { RowItem } from '../acode/vendor/components/quickTools/footer.js';
import initQuickTools from '../acode/vendor/handlers/quickToolsInit.js';
import actions, { cancelQuickToolsModifierInput, key } from '../acode/vendor/handlers/quickTools.js';
import keyboardHandler from '../acode/vendor/handlers/keyboard.js';
import windowResize from '../acode/vendor/handlers/windowResize.js';
import actionStack from '../acode/vendor/lib/actionStack.js';
import commandPalette from '../acode/vendor/palettes/commandPalette/index.js';
import { syncQuickToolsVisibility } from '../acode/host/editorFile.js';
import { updateSystemConfiguration } from '../acode/host/systemConfiguration.js';
import { saveEditorSession, readEditorSession } from '../acode/host/sessionStore.js';
import { observableCompletionSource } from '../acode/host/observableCompletions.js';
import { messagesFor } from '../locales/index.js';
import { editorTooltips, completionLayout } from '../acode/host/completionLayout.js';
import { notebookFoldControls } from '../acode/host/foldControls.js';
import { insertColor } from '../acode/host/insertColor.js';
import { nativeSelectionInput } from '../acode/host/nativeSelectionInput.js';
import { updateOverlayViewport } from '../acode/host/overlayViewport.js';
import { editorDiagnosticLog, installEditorDiagnostics } from '../logging/EditorDiagnostics.js';
import { installEditorPipelineDiagnostics, recordResize, recordPipeline } from '../logging/EditorPipelineDiagnostics.js';
import '../acode/vendor/handlers/editorWorkaround.js';
import '../acode/host/skin.scss';
window.__npBootStage = 'Acode editor bundle';
globalThis.appSettings = settings;
// CM 6.43's Android EditContext keeps a ~20k text window around the head.
// Its selection-only textupdate maps a clipped anchor back into editor state.
// Keep the DOM input path for arbitrary-length native selections instead.
EditorView.EDIT_CONTEXT = false;
const host = document.getElementById('editor');
updateOverlayViewport();
host.classList.add('editor-container');
let documentKind = "notebook", documentName = "notebook.html";
const syntax = new Compartment();
const appearance = new Compartment(),
  completion = new Compartment(),
  wrapping = new Compartment(),
  typography = new Compartment();
let preferences = {
  dark: false,
  language: 'en',
  theme: 'system',
  fontSize: 14,
  wordWrap: true,
  autocomplete: true,
  showQuickTools: true
};
let loading = false,
  documentId = null,
  sessionRestored = false,
  searchRestored = false,
  saveTimer = 0,
  destroyed = false,
  fullscreen = false, diagnosticSession, pipelineSession;
const runtime = createSinglePaneRuntime(host);
function completionOptions() {
  return preferences.autocomplete ? [localWordCompletions(), ...(documentKind!=='resource'?[javascriptLanguage.data.of({autocomplete:observableCompletionSource})]:[]), autocompletion({
    activateOnTyping: true,
    activateOnTypingDelay: navigator.maxTouchPoints > 0 ? 220 : 100,
    ...completionLayout,
    maxRenderedOptions: 100,
    selectOnOpen: true
  })] : [];
}
const readOnlyMode = new Compartment();
function createState(doc) {
  return EditorState.create({
    doc,
    extensions: createMainEditorExtensions({
      baseExtensions: [editorTooltips(), ...createBaseExtensions({foldGutter: false})],
      commandKeymapExtension: getCommandKeymapExtension(),
      ...runtime.extensions,
      quickToolsModifierInputExtension: quickToolsModifierInput(),
      searchExtension: search(),
      readOnlyExtension: readOnlyMode.of(createEditorReadOnlyExtension(false)),
      optionExtensions: [nativeSelectionInput(() => manager.editor), syntax.of(languageForFile(documentName,documentKind)), indentUnit.of('  '), EditorState.tabSize.of(2), indentGuides({highlightActiveGuide:false,hideOnBlankLines:false}), notebookFoldControls(), tagAutoRename(), rainbowBrackets(), lineNumbers({
        domEventHandlers: {
          click: (view, line, event) => handleLineNumberClick(view, line, event, {
            shiftClickSelection: settings.value.shiftClickSelection
          })
        }
      }), appearance.of(preferences.theme === 'dark' || preferences.theme !== 'light' && preferences.dark ? oneDark : githubLight()), completion.of(completionOptions()), wrapping.of(preferences.wordWrap ? indentedLineWrapping('same') : []), typography.of(EditorView.theme({
        '&': {
          fontSize: `${preferences.fontSize}px`
        },
        '.cm-scroller': {
          lineHeight: '1.4'
        }
      })), scrollPastEnd(.5), horizontalScrollPastEnd(40), EditorView.updateListener.of(update => {
        diagnosticSession?.update(update);
        manager.emit('editor-state-changed', update);
        if (loading) return;
        if (update.docChanged) {
          window.NativeEditor?.changed(getSource());
          manager.activeFile.isUnsaved = true;
          manager.emit('file-content-changed');
        }
        if (update.docChanged || update.selectionSet || update.transactions.some(t => t.effects.some(e => e.is(foldEffect) || e.is(unfoldEffect)))) scheduleSessionSave();
        if (update.geometryChanged || update.viewportChanged) runtime.refresh('geometry-update');
      })]
    })
  });
}
const editor = createEditorView({
  state: createState(''),
  parent: host
});
runtime.attach(editor);
diagnosticSession = installEditorDiagnostics(editor);
pipelineSession = installEditorPipelineDiagnostics(editor);
function saveSession() {
  clearTimeout(saveTimer);
  if (destroyed || loading) return;
  const {
      anchor,
      head
    } = editor.state.selection.main,
    scrollTop = Math.round(editor.scrollDOM.scrollTop);
  if(documentKind==="notebook") saveEditorSession(window.localStorage, documentId, editor.state.doc.length, {
    anchor,
    head
  }, scrollTop, {
    scrollLeft: editor.scrollDOM.scrollLeft,
    folds: getAllFolds(editor.state),
    selection: editor.state.selection.toJSON()
  });
  window.NativeEditor?.cursorSelection(anchor, head, scrollTop);
}
function scheduleSessionSave() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(saveSession, 100);
}
let readonlySearch;
function searchSnapshot() {
  if (readonlySearch) return readonlySearch.snapshot();
  return {
    open: actionStack.has('search-bar'),
    query: quickTools.$searchInput.el?.value || '',
    replace: quickTools.$replaceInput.el?.value || '',
    options: normalizeSearch({options:{matchCase:settings.value.search.caseSensitive,regex:settings.value.search.regExp,whole:settings.value.search.wholeWord}}).options
  };
}
function emitSearch() {
  window.NativeEditor?.searchState(JSON.stringify(searchSnapshot()));
}
let originalSource = '', canonicalSource = '', sourceLineBreak = '\n';
function getSource() {
  const value=getDocText(editor.state.doc);
  return value===canonicalSource?originalSource:(sourceLineBreak==='\n'?value:value.replace(/\n/g,sourceLineBreak));
}
function setSource(value) {
  const source = String(value);
  const normalized=source.replace(/\r\n?/g,'\n');
  originalSource=source;canonicalSource=normalized;
  sourceLineBreak=source.match(/\r\n|\r|\n/)?.[0] || '\n';
  if (getDocText(editor.state.doc) === normalized) return;
  loading = true;
  try {
    runtime.selectionMenu.onSessionChanged();
    editor.setState(createState(normalized));
    if (readonlySearch) readonlySearch.refresh();
    else if(actionStack.has('search-bar'))quickTools.$searchInput.el.dispatchEvent(new Event('input'));
    manager.emit('file-loaded');
  } finally {
    loading = false;
  }
}
function setPreferences(next = {}) {
  preferences = {
    ...preferences,
    ...next
  };
  preferences.fontSize = Math.max(11, Math.min(24, Number(preferences.fontSize) || 14));
  const dark = preferences.theme === 'dark' || preferences.theme !== 'light' && preferences.dark;
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.lang = preferences.language;
  setLanguage(preferences.language);
  readonlySearch?.preferences(preferences);
  document.documentElement.style.setProperty('--np-editor-font-size', `${preferences.fontSize}px`);
  settings.value.fontSize = `${preferences.fontSize}px`;
  settings.value.textWrap = preferences.wordWrap;
  manager.activeFile.hideQuickTools = !preferences.showQuickTools;
  editor.dispatch({
    effects: [appearance.reconfigure(dark ? oneDark : githubLight()), completion.reconfigure(completionOptions()), wrapping.reconfigure(preferences.wordWrap ? indentedLineWrapping('same') : []), typography.reconfigure(EditorView.theme({
      '&': {
        fontSize: `${preferences.fontSize}px`
      },
      '.cm-scroller': {
        lineHeight: '1.4'
      }
    }))]
  });
  syncQuickToolsVisibility(manager.activeFile);
  runtime.refresh();
  for (const input of [quickTools.$searchInput.el, quickTools.$replaceInput.el]) if (input) input.placeholder = input === quickTools.$searchInput.el ? strings.search : strings.replace;
  quickTools.$footer.querySelectorAll('[data-id]').forEach(b => b.ariaLabel = strings['quicktools:' + b.dataset.id] || strings[b.dataset.id] || b.ariaLabel);
}
function insertCell(mode) {
  if(documentKind==="resource")return false;
  const text = getDocText(editor.state.doc),
    index = text.lastIndexOf('</notebook>'),
    at = index < 0 ? text.length : index;
  const id = Math.max(0, ...Array.from(text.matchAll(/<script\b[^>]*\bid\s*=\s*["'](\d+)["']/g), m => Number(m[1]))) + 1;
  const snippet = mode === 'md' ? `  <script id="${id}" type="text/markdown">\n    ## ${messagesFor(preferences.language).newSection}\n  </script>\n` : `  <script id="${id}" type="module">\n    display(1 + 2);\n  </script>\n`;
  editor.dispatch({
    changes: {
      from: at,
      insert: snippet
    },
    selection: {
      anchor: at + snippet.indexOf('\n') + 5
    },
    scrollIntoView: true,
    userEvent: 'input'
  });
  editor.focus();
}
function hostCommand(name) {
  if (name === 'find' || name === 'replace') {
    if (readonlySearch) {readonlySearch.open();return true;}
    actions('search');
    emitSearch();
    return true;
  }
  if (name === 'command-palette') {
    commandPalette();
    return true;
  }
  if (name === 'save') {
    if(editor.state.readOnly) return false;
    if(documentKind==='resource'||documentKind==='document'){window.NativeEditor?.changed(getSource());window.NativeEditor?.save?.();return true;}
    window.NativeEditor?.changed(getSource());
    saveSession();
    manager.activeFile.isUnsaved = false;
    manager.emit('save-file');
    return true;
  }
  if (name === 'toggle-fullscreen') {
    window.NativeEditor?.setFullscreen(!fullscreen);
    return true;
  }
  if (name === 'goto') {
    const line = window.prompt(strings['go to line'] || 'Go to line', String(editor.state.doc.lineAt(editor.state.selection.main.head).number));
    if (line) editor.gotoLine(...String(line).split('.'));
    return true;
  }
  if (name === 'insert-color') {
    void insertColor(editor);
    return true;
  }
  return false;
}
globalThis.acode = {
  exec: hostCommand
};
initQuickTools();
// Autosave owns persistence. Keep Acode's save command/keymap available,
// Replace its slot with Acode's existing tilde RowItem so both default
// sections retain eight equally sized buttons, including arrow alignment.
for (const row of [quickTools.$row1, quickTools.$row2]) row.querySelectorAll('[data-id="save"]').forEach(button => button.replaceWith(RowItem({id:'tilde',icon:'letters',action:'insert',value:'~',letters:'~'})));
initIconTooltips();
const extra = document.createElement('div');
extra.className = 'section';
for (const [name, icon, label, run] of [['notebookInsertJS', 'code', 'JavaScript', () => insertCell('js')], ['notebookInsertMD', 'text_format', 'Markdown', () => insertCell('md')], ['notebookFullscreen', 'fullscreen', 'Fullscreen', () => hostCommand('toggle-fullscreen')], ['notebookKeyboard', 'keyboard_hide', 'Keyboard', () => window.NativeEditor?.toggleKeyboard()], ['foldCode', 'unfold_less', 'Fold', () => executeCommand('foldCode', editor)], ['unfoldCode', 'unfold_more', 'Unfold', () => executeCommand('unfoldCode', editor)], ['foldAll', 'unfold_less', 'Fold all', () => executeCommand('foldAll', editor)], ['unfoldAll', 'unfold_more', 'Unfold all', () => executeCommand('unfoldAll', editor)]]) {
  if (name.startsWith('notebook')) registerExternalCommand({
    name,
    description: label,
    exec: run
  });
  const b = RowItem({
    id: name,
    icon,
    action: 'command',
    value: name
  });
  b.ariaLabel = label;
  b.title = label;
  extra.append(b);
}
quickTools.$row2.append(extra);
refreshCommandKeymap(editor);

syncQuickToolsVisibility(manager.activeFile);
settings.on('update', () => {
  emitSearch();
  if (editor && parseInt(settings.value.fontSize) !== preferences.fontSize) setPreferences({
    fontSize: parseInt(settings.value.fontSize)
  });
});
editor.scrollDOM.addEventListener('scroll', scheduleSessionSave, {
  passive: true
});
const onResize = trigger => { recordResize(editor,host,trigger); updateOverlayViewport(); windowResize(); };
const onWindowResize = () => onResize('window-resize');
const onVisualResize = () => onResize('visual-resize');
window.addEventListener('resize', onWindowResize);
window.visualViewport?.addEventListener('resize', onVisualResize);
const resizeObserver = new ResizeObserver(() => onResize('host-resize'));
resizeObserver.observe(host);
const onSettledResize = () => {
  if (destroyed) return;
  recordPipeline('pipeline-resize-settled');
  editor.requestMeasure();
  runtime.refresh('settled-resize');
  runtime.selectionMenu.onStateChanged();
};
windowResize.on('resize', onSettledResize);
const onPageHide = () => {
  saveSession();
  emitSearch();
};
window.addEventListener('pagehide', onPageHide);
// Persist Acode's own query state, including replace/navigation actions. This
// does not maintain a second query, match list, selection or decoration model.
quickTools.$footer.addEventListener('input', emitSearch);
quickTools.$footer.addEventListener('click', () => queueMicrotask(emitSearch));
quickTools.$footer.addEventListener('touchend', () => queueMicrotask(emitSearch));
document.addEventListener('keydown', keyboardHandler);
document.dispatchEvent(new Event('deviceready'));
window.EditorApi = {
  setReadOnly(value) {
    reconfigureEditorReadOnly(editor, readOnlyMode, !!value);
    document.documentElement.dataset.readOnly=String(!!value);
    if (value && !readonlySearch) {
      readonlySearch=acodeReadOnlySearch(editor, state=>window.NativeEditor?.searchState(JSON.stringify(state)));
      readonlySearch.preferences(preferences);
    } else if (!value && readonlySearch) {readonlySearch.dispose();readonlySearch=undefined;}
    if (!document.getElementById('host-readonly-style')) {
      const style=document.createElement('style'); style.id='host-readonly-style';
      style.textContent='html[data-read-only="true"] #quicktools-toggler, html[data-read-only="true"] #row1, html[data-read-only="true"] #row2, html[data-read-only="true"] #search_row2 input, html[data-read-only="true"] [data-action="search-replace"], html[data-read-only="true"] [data-action="search-replace-all"] {display:none!important}';
      document.head.append(style);
    }
    runtime.selectionMenu.onStateChanged();
  },
  setDocument(name, kind='resource') {
    documentName=String(name || 'file.txt');documentKind=kind==='notebook'?'notebook':kind==='document'?'document':'resource';
    document.documentElement.dataset.documentKind=documentKind;
    manager.activeFile.filename=documentName;
    editor.dispatch({effects:[syntax.reconfigure(languageForFile(documentName,documentKind)),completion.reconfigure(completionOptions())]});
  },
  setSource,
  setPreferences,
  getSource,
  insertCell,
  setSessionKey(id) {
    if (documentId !== String(id || '')) {
      documentId = String(id || '');
      manager.activeFile.id = documentId;
      sessionRestored = false;
      searchRestored = false;
    }
  },
  restoreSession(anchor = 0, head = 0, scrollTop = 0) {
    if (sessionRestored) return;
    sessionRestored = true;
    loading = true;
    try {
      const saved = documentKind!=="notebook"?null:readEditorSession(window.localStorage, documentId, editor.state.doc.length),
        length = editor.state.doc.length;
      restoreFolds(editor, saved?.folds);
      const clamp = v => Math.max(0, Math.min(length, Number(v) || 0));
      let selection = EditorSelection.single(clamp(saved?.anchor ?? anchor), clamp(saved?.head ?? head));
      if (saved?.selection) try {
        const next = EditorSelection.fromJSON(saved.selection);
        if (next.ranges.every(r => r.from >= 0 && r.to <= length)) selection = next;
      } catch {}
      editor.dispatch({
        selection
      });
      runtime.selectionMenu.onSessionChanged();
      requestAnimationFrame(() => {
        if (destroyed) return;
        editor.scrollDOM.scrollTop = Math.max(0, saved?.scrollTop ?? scrollTop);
        editor.scrollDOM.scrollLeft = Math.max(0, saved?.scrollLeft || 0);
        runtime.refresh();
      });
    } finally {
      loading = false;
    }
  },
  restoreSearchState(raw) {
    if (searchRestored) return;
    searchRestored = true;
    let state;
    try {
      state = typeof raw === 'string' ? JSON.parse(raw) : raw;
    } catch {
      return;
    }
    if (readonlySearch) {readonlySearch.restore(normalizeSearch(state||{}));return;}
    if (!state?.open) return;
    const selection = editor.state.selection,
      scroll = editor.scrollSnapshot(),
      focused = document.activeElement;
    settings.value.search = {
      caseSensitive: !!state.options?.matchCase,
      regExp: !!state.options?.regex,
      wholeWord: !!state.options?.whole
    };
    actions('search');
    quickTools.$searchInput.el.value = String(state.query || '');
    quickTools.$replaceInput.el.value = String(state.replace || '');
    quickTools.$searchInput.el.dispatchEvent(new Event('input'));
    editor.dispatch({
      selection,
      effects: scroll
    });
    quickTools.$searchInput.el.blur();
    if (focused instanceof HTMLElement && focused !== document.body) focused.focus();
  },
  search: () => hostCommand('find'),
  focus: () => { recordPipeline('focus-api',{operation:'focus'});editor.focus(); },
  // Cut continuation separately checks native view/window/lifecycle state.
  // CM.hasFocus also requires document.hasFocus, which becomes false during
  // a transient window interruption even if contentDOM still owns editing.
  hasEditingFocus() {
    const active=!destroyed&&editor.dom.isConnected&&!editor.state.readOnly&&document.activeElement===editor.contentDOM;
    if(editorDiagnosticLog.enabled)recordPipeline('focus-editing-check',{active,hasFocus:editor.hasFocus,documentFocused:document.hasFocus()});
    return active;
  },
  setDiagnosticsEnabled: enabled => editorDiagnosticLog.setEnabled(enabled),
  setDiagnosticsOptions: options => editorDiagnosticLog.setOptions(options),
  focusForKeyboard() {
    recordPipeline('focus-api',{operation:'focusForKeyboard'});
    if (actionStack.has('search-bar')) quickTools.$searchInput.el.focus();else if (key.ctrl || key.alt || key.meta) quickTools.$input.focus();else editor.focus();
  },
  suspendKeyboardFocus() {
    recordPipeline('focus-api',{operation:'suspendKeyboardFocus'});
    saveSession();
    emitSearch();
    cancelQuickToolsModifierInput();
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
  },
  systemConfigurationChanged: updateSystemConfiguration,
  setFullscreenState(enabled) {
    fullscreen = !!enabled;
    const button=quickTools.$footer.querySelector('[data-id="notebookFullscreen"]');
    button?.classList.toggle("fullscreen_exit",fullscreen);button?.classList.toggle("fullscreen",!fullscreen);
  },
  captureSession: onPageHide,
  refresh: () => {
    editor.requestMeasure();
    runtime.refresh('api-refresh');
  },
  handleBack() {
    if (readonlySearch?.close()) {saveSession();return true;}
    if (!actionStack.length) return false;
    actionStack.pop();
    emitSearch();saveSession();
    return true;
  },
  getDiagnostics() {
    const box = host.getBoundingClientRect();
    return {
      sourceLength: editor.state.doc.length,
      diagnosticsEnabled: editorDiagnosticLog.enabled,
      selectionMode: editorDiagnosticLog.options.selectionMode,
      revealMode: editorDiagnosticLog.options.revealMode,
      selectionAnchor: editor.state.selection.main.anchor,
      selectionHead: editor.state.selection.main.head,
      viewportWidth: innerWidth,
      viewportHeight: innerHeight,
      hostHeight: box.height,
      width: box.width,
      height: box.height,
      scrollerHeight: editor.scrollDOM.clientHeight,
      quickToolsHeight: quickTools.$footer.offsetHeight,
      computedFontSize: getComputedStyle(editor.contentDOM).fontSize,
      configuredFontSize: `${preferences.fontSize}px`,
      isConnected: editor.dom.isConnected,
      engine: 'Acode DOM CodeMirror'
    };
  },
  destroy() {
    pipelineSession.destroy();
    diagnosticSession.destroy();editorDiagnosticLog.setEnabled(false);
    saveSession();
    readonlySearch?.dispose();readonlySearch=undefined;
    destroyed = true;
    clearTimeout(saveTimer);
    resizeObserver.disconnect();
    windowResize.off('resize', onSettledResize);
    window.removeEventListener('resize', onWindowResize);
    window.visualViewport?.removeEventListener('resize', onVisualResize);
    window.removeEventListener('pagehide', onPageHide);
    document.removeEventListener('keydown', keyboardHandler);
    runtime.destroy();
    quickTools.$footer.remove();
    quickTools.$toggler.remove();
    quickTools.$input.remove();
    editor.destroy();
  }
};
if (!window.NativeEditor) setSource(`<!doctype html>\n<notebook>\n <title>${messagesFor(document.documentElement.lang).newNotebook}</title>\n</notebook>\n`);
window.__npBootStage = 'Acode editor ready';
window.NativeEditor?.ready();

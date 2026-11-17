package com.karandaev.fieldfolio.core.document.runtime

/** The source belongs to the VFS node; it has no private resource directory. */
val DefaultNotebookDocument =
  """
<!doctype html>
<notebook theme="air" data-state-version="fieldfolio-document-v1">
  <title>Fieldfolio Document</title>
  <script id="1" type="module">
const introduction = document.createElement("p");
introduction.textContent = Folio.language === "ru"
  ? "Интерактивный документ Fieldfolio: Observable + Nexus. Ресурсы доступны по логическим путям VFS отчёта."
  : "Interactive Fieldfolio document: Observable + Nexus. Read report items by their logical VFS paths.";
display(introduction);
  </script>
  <script id="2" type="module">
const counterScope = nexus.scope(invalidation);
const counterStore = counterScope.store(0);
const doubledStore = counterScope.store(0);
class DoubleCounter extends Nexus.Handler {
  run(ctx) {
    ctx.set(doubledStore, ctx.read(counterStore) * 2);
  }
}
counterScope.register(new DoubleCounter(), {
  on: {any: [counterStore]}, initial: true, execution: "inline"
});
  </script>
  <script id="3" type="module">
const counterButton = html`<button type="button" data-nexus-increment>+1 Nexus</button>`;
const incrementCounter = counterScope.guard(() => {
  nexus.dispatcher.inline(ctx => ctx.set(counterStore, ctx.read(counterStore) + 1));
});
counterButton.addEventListener("click", incrementCounter);
counterScope.cleanup(() => counterButton.removeEventListener("click", incrementCounter));
const counterControls = document.createElement("form");
counterControls.className = "np-input-button-group";
counterControls.setAttribute("data-np-ephemeral", "");
counterControls.append(counterButton);
display(counterControls);
  </script>
  <script id="4" type="application/vnd.observable.javascript">
doubled = nexus.observe(counterScope, doubledStore)
  </script>
  <script id="5" type="module">
display(html`<p>Nexus → Observable: <output data-nexus-result>${'$'}{doubled}</output></p>`);
  </script>
  <script id="6" type="module">
const reportItems = Folio.vfs.list().filter(item => item.id !== Folio.document.id && item.visible && !item.hideTitle);
if (reportItems.length) display(Inputs.table(reportItems.map(item => ({name:item.displayName || item.name, type:item.kind}))));
// const result = await Folio.vfs.json("/Model result");
// display(await Folio.vfs.attachment("/Model result", "input").image());
// const text = await Folio.vfs.text("/Folder/Notes.txt");
  </script>
</notebook>
"""
    .trimIndent()

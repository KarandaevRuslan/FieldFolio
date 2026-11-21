# Document and JavaScript

Offline reference for Fieldfolio Document, the report VFS and the local Notebook Kit/Nexus runtime.

## Contents

- [Create, edit and save](#workflow)
- [Cells, dependencies and cleanup](#cells)
- [Notebook source](#source)
- [App context](#context)
- [Read-only VFS](#vfs)
- [Every data type](#types)
- [Nexus](#nexus)
- [Reading and search](#reading)
- [Language, theme and separate CSS](#themes)
- [Clock and a countdown example](#timers)
- [Images, audio and report artwork](#media)
- [Models: detection, masks, classification and other outputs](#analysis)
- [Location, Weather and Clock data](#widgets)
- [Isolation and what is deliberately unavailable](#safety)
- [Troubleshooting](#troubleshooting)

<a id="workflow"></a>

## Create, edit and save

1. In the report editor add **Document**, set its logical name and optional display title, then open Code.
2. Edit the notebook HTML in Acode. Save there returns the source to the Document form; **Save in the form commits the element**. Save the report to persist the complete report.
3. Use Preview to check the same component that the report viewer uses. Expand the mini reader for scrolling, search and interactive controls.
4. A Text element is source/text data, even with a `js` syntax hint. A Document is the element that executes notebook cells. Acode itself never runs that source.

The eye/title toggles change report presentation. They do not rename resources or revoke access from other Documents in this report. Logical names are the stable VFS addressing names; display names are captions.

<a id="cells"></a>

## Cells, dependencies and cleanup

Each cell needs a unique `id`. A `type="module"` cell contains ordinary JavaScript; `type="text/markdown"` contains Markdown. Keep the enclosing `<notebook>`. Use the Acode notebook cell actions to insert supported cell types rather than inventing type attributes.

`display(value)` renders a value. Use `html`/`htl` for DOM, `Inputs` for controls and `FileAttachment` for VFS files. In Observable JavaScript cells, named values and `viewof` participate in the reactive dependency graph. Ordinary module-local variables are not shared between independent JavaScript modules. Keep related imperative code in one module, or use Observable cells for reactive dependencies.

Dispose intervals and custom listeners when the cell is invalidated:

```js
const output = document.createElement("output");
const update = () => output.textContent = new Date().toLocaleTimeString(Folio.locale);
update();
const interval = setInterval(update, 1000);
invalidation.then(() => clearInterval(interval));
display(output);
```

Use `textContent` or tagged templates for untrusted strings. Re-evaluation can replace output DOM, so avoid storing long-lived references to another cell's old nodes.

Document executes **Observable Notebook Kit 2.9.1** cells with **Nexus 0.1.20** in an isolated reader. A Text element with a `js` syntax hint stores source code: Acode highlights it but does not execute it.

<a id="source"></a>

## Notebook source

```html
<notebook>
  <script id="1" type="module">
    display(html`<h2>Hello, ${Folio.language}</h2>`);
  </script>
  <script id="2" type="text/markdown">
    ## Markdown text
  </script>
</notebook>
```

JavaScript, Observable JavaScript, TypeScript, Markdown, HTML and TeX cells are supported. Inputs and htl are packaged locally. External CDN imports, Android bridges, file/content URLs and persistent browser storage are unavailable. A cell error is displayed beside that cell.

<a id="context"></a>

## App context

- `Folio.language`, `.locale`, `.theme`, `.dark`, `.timeZone`, `.hourCycle`: current app preferences.
- `Folio.now()` returns a Date; `Folio.timestamp()` returns epoch milliseconds.
- `folioNow` is a reactive clock updated every second.
- `Folio.timer("timer", 60000)`: `start()`, `pause()`, `reset(ms)`, `snapshot()`. Snapshots contain remainingMs, elapsedMs, durationMs, running and finished.
- `Pocket` and `pocketNow` remain compatibility aliases for Pocket Notebook.

Timer IDs use letters, digits, `.`, `_` and `-` (1–80 characters). At most 64 timers are
retained per context; durations are bounded to 1 ms–7 days. Timers belong to this runtime,
not a persistent Android alarm service.

<a id="vfs"></a>

## Read-only VFS

`Folio.vfs` and `Vfs` are the same API. Paths use **logical names**, never display aliases. Relative paths start in the current Document's folder. `/` is the report root; `.` and `..` work inside it. Encode special name components with encodeURIComponent; list/stat return canonical paths.

| Method | Result |
|---|---|
| `list(path = ".")` | Ordered folder children |
| `stat(path)`, `exists(path)` | Identity/type/presentation/resources or existence |
| `await get(path)` / `await read(path)` | Complete typed metadata |
| `await text(path)` | Text, source or decoded text file |
| `await json(path)` | JSON file, model result or widget data |
| `await blob(path, role)` | Resource bytes |
| `url(path, role)` | Read-only URL for this runtime |
| `attachment(path, role)` | Real Notebook Kit FileAttachment |
| `await image(path, options)` | Image |

`root` is `/`; `base` is the current Document folder; `paths` is the report entry-path list.
`list`, `stat`, `exists`, `url` and `attachment` return synchronously. `get`/`read`, `text`,
`json`, `blob` and `image` return promises; await them. The manifest is bounded at 1,000 entries.
Paths stay inside the report; attempts to escape its root are rejected.

`FileAttachment("/Folder/photo.jpg")` also reads the VFS. The default role is `data`. Model roles include `input`, `mask`, `layer-0`, `layer-1`, etc. Audio can have `cover`. Folders have no binary attachment.

```js
const items = Folio.vfs.list();
display(Inputs.table(items.map(x => ({name: x.name, type: x.kind}))));
const text = await Folio.vfs.text("/Materials/notes.txt");
const result = await Folio.vfs.json("/Analysis");
display(await Folio.vfs.attachment("/Analysis", "input").image());
```

Replace example paths with actual items. Document has **no private resources folder**: add resources as VFS elements. Hidden items in the same report remain readable; visibility is presentation, not protection. Other reports are inaccessible. No write, delete, rename, move, copy or native-model execution methods exist.

<a id="types"></a>

## Every data type

Text/Heading expose body/text and reading options; text files expose decoded text and original bytes; Document exposes notebook source. Image/Audio expose media. Folder metadata includes children.

`await Folio.vfs.get("/Song")` contains audio.tags, audio.displayedTags, overrides and visibility flags. **`audio.cover`** is an optional `{url, mime}` field, with no logical name of its own. `Folio.report.cover` exposes report artwork in the same form. coverUrl remains a compatibility alias.

```js
const song = await Folio.vfs.get("/Song");
if (song.audio.cover) {
  const image = new Image();
  image.src = song.audio.cover.url;
  display(image);
}
```

Location exposes resolvedLocation or null. Clock exposes resolvedZoneId and epochMillis. Weather exposes existing cached weather or null. A VFS read does not request location permissions or start a weather refresh. The native host maintains available snapshots as described below.

Model results include boxes/confidence; layers with polygon rings/holes and/or pixel maskUrl; classification scores; regression/numeric values with units; text outputs. Metadata preserves model/task/provider, timing, inputKind/inputText/inputTruncated. Coordinates are normalized-image-0-1, relative to the saved input. Use its image dimensions when drawing overlays.

<a id="nexus"></a>

## Nexus

```js
const scope = nexus.scope(invalidation);
const store = scope.store(0);
class Counter extends Nexus.Handler {
  run(ctx) { ctx.set(store, ctx.read(store) + 1); }
}
// scope.register(handler, {on: {any: [inputStore]}, execution: "inline"});
nexus.dispatcher.inline(ctx => ctx.set(store, 1));
```

In an Observable cell use `value = nexus.observe(scope, store)`. Store identity alone does not trigger reactive reevaluation. Scopes/handlers/listeners are released through invalidation and cleanup. The default Document includes a complete dependent counter example. Frida/native Nexus is unavailable inside WebView.

<a id="reading"></a>

## Reading and search

The mini preview never scrolls. Expand it to read the page. Full view supports find, `.*` regular expressions, `Aa` match case, `ab` whole word, previous/next match and Ctrl+F. The viewport, query and live document state survive rotation and mini/full transitions during the current session. Locking the report destroys the runtime; state is not promised after process termination.

<a id="themes"></a>

## Language, theme and separate CSS

`Folio.theme` is `"light"` or `"dark"`; `Folio.dark` is boolean. `Folio.language` is `"en"` or `"ru"`, while `Folio.locale` is the complete locale tag. The application supplies these preferences to the reader. The built-in controls, Notebook Kit Inputs, search and page background already follow the app theme.

Store translations as Text file elements named, for example, `en.json` and `ru.json`; store CSS in a Text/Text file element named `style.css`. They are normal report VFS elements, not Document-private files.

```js
const dictionary = await Folio.vfs.json(`/Translations/${Folio.language}.json`);
const style = document.createElement("style");
style.textContent = await Folio.vfs.text("/Styles/style.css");
document.head.append(style);
invalidation.then(() => style.remove());
const heading = document.createElement("h2");
heading.textContent = dictionary.title || "Document";
display(heading);
```

In that CSS use `:root[data-theme="dark"]` and `:root[data-theme="light"]`, or `var(--bg)`, `var(--fg)`, `var(--card)`, `var(--line)`, `var(--accent)`. Scope rules to your own class to avoid overriding Notebook Kit controls.

<a id="timers"></a>

## Clock and a countdown example

Dates and timer durations use **milliseconds**. `Folio.timeZone` and `Folio.hourCycle` describe app/device formatting, not a manually selected Clock element. Read the latter through VFS metadata.

```js
const timer = Folio.timer("cooking", 60000);
const output = document.createElement("output");
const start = document.createElement("button");
const pause = document.createElement("button");
const reset = document.createElement("button");
start.textContent = "Start"; pause.textContent = "Pause"; reset.textContent = "Reset";
start.onclick = () => timer.start();
pause.onclick = () => timer.pause();
reset.onclick = () => timer.reset(60000);
const update = () => output.textContent = `${Math.ceil(timer.snapshot().remainingMs / 1000)} s`;
const interval = setInterval(update, 100); update();
invalidation.then(() => clearInterval(interval));
const group = document.createElement("div");
group.append(output, start, pause, reset); display(group);
```

Timer IDs must be stable and unique within the notebook (`letters`, digits, `_`, `.`, `-`; up to 80 characters). Durations range from 1 ms to 7 days. Rotation/mini-full transitions retain the live session. Locking the report or ending the process is not durable timer storage.

<a id="media"></a>

## Images, audio and report artwork

Use attachment methods for bytes/media rather than treating a logical VFS path as a network URL:

```js
display(await Folio.vfs.attachment("/Photos/site.jpg").image({width: 480}));
const player = document.createElement("audio");
player.controls = true;
player.src = Folio.vfs.url("/Recordings/note.m4a");
display(player);
```

`get` returns the audio node including `audio.tags` (original tags), `audio.displayedTags` (effective displayed values), `audio.overrides`, `showTags`, `showCover` and hidden-tag choices. `audio.cover` and `Folio.report.cover` are optional `{url, mime}` fields. They have no independent logical name. Use the provided URL in an image; do not construct a private storage path. Missing artwork is normal.

For a text file use `await Folio.vfs.text(path)` so its native detected encoding is respected. `.blob()` returns original bytes; `.json()` parses a text file as JSON and can throw for malformed data.

<a id="analysis"></a>

## Models: detection, masks, classification and other outputs

`await Folio.vfs.get("/Analysis")` returns an element; its `result` contains the model output. `await Folio.vfs.json("/Analysis")` reads the JSON resource for the result. Read `task`/available fields instead of assuming every model detects roads.

| Data | Interpretation |
|---|---|
| `boxes` | Label, confidence, xMin/yMin/xMax/yMax |
| `layers` | Segmentation polygons (including holes) and/or a pixel `maskUrl` |
| `scores` | Classification labels and scores |
| `values` | Numeric/regression values, labels and optional units |
| `text` | Text model output |
| `inputText`, `inputKind`, `inputTruncated` | Saved input and any truncation flag |

`result.inputUrl`, `result.maskUrl` and per-layer mask URLs refer only to this runtime's permitted resources. The attachment roles are `input`, `mask`, `layer-0`, `layer-1`, etc. Check `stat(path).assets` before requesting an optional role.

```js
const node = await Folio.vfs.get("/Analysis");
const result = node.result;
if (result) display(Inputs.table(result.scores || []));
if (Folio.vfs.stat("/Analysis").assets.input) {
  display(await Folio.vfs.attachment("/Analysis", "input").image());
}
```

Box/polygon coordinates are normalized to 0–1 relative to the **saved analysis input**, not a different original image. Multiply x by image width and y by height. Pixel segmentation masks must be rendered at the input's dimensions. Do not reinterpret a pixel mask as a polygon. The reader cannot invoke a native/local/server model; generate or replace a Model result from the report editor.

<a id="widgets"></a>

## Location, Weather and Clock data

`get(path)` exposes native widget configuration plus resolved fields:

- Location/Weather: `resolvedLocation` is `{latitude, longitude}` or `null`.
- Weather: `weather` is cached `{temperature, weatherCode, zoneId}` or `null`; it does not start a refresh or request permissions.
- Clock: `resolvedZoneId` is the resolved IANA ID, and `epochMillis` is the metadata-read timestamp. Format with `Intl.DateTimeFormat(Folio.locale, {timeZone: node.resolvedZoneId, ...})` after checking the ID.

An active native Document host subscribes to the report's required device location/weather
resources, including hidden device-based elements. Later VFS reads can therefore see updated
snapshots. JavaScript does not directly control providers; there is no `Folio.watch` API and
reading a value does not grant location permission. Offline Help has no weather provider.

The VFS read is a snapshot. For a continuously changing display use `folioNow` in an Observable cell or a cleaned-up interval. Do not invent zero coordinates, weather or UTC when a resolved value is absent; show an unavailable state. Pull to update and native widget editing remain app actions.

<a id="safety"></a>

## Isolation and what is deliberately unavailable

Notebook source executes in a sandboxed renderer with an opaque origin. Acode is a separate privileged editor and treats that source as inert text. The renderer has no `NativeEditor`/Android bridge, direct app filesystem, `file:`/`content:` access, persistent storage or external network imports. VFS URLs are short-lived capabilities scoped to this report/runtime; do not store or send them elsewhere.

There is no VFS write/remove/rename/copy/move API. Hidden items are still readable by Documents in the same report; visibility is not encryption. Password locking destroys reader runtimes and revokes their resource access. Never include secrets in a Document expecting hidden titles to protect them.

Browser `document`, DOM elements, canvas and local Notebook Kit modules work inside that sandbox. Native permissions, model execution, OS files and another report are outside it.

<a id="troubleshooting"></a>

## Troubleshooting

- **Path not found:** use the logical name, correct folder, encoded components and a resource that is in this report. Check `Folio.vfs.list("/")` and `exists(path)`.
- **No attachment:** folders have no bytes; optional model masks/audio artwork may be absent. Inspect `stat(path).assets`.
- **Missing syntax colors:** set the Text element's explicit Acode extension hint (`js`, `json`, `css`, etc.). Renaming a display title does not set a language mode.
- **Preview looks static:** the mini reader does not scroll. Open full view. JavaScript inside Text is not executed.
- **Nothing saved after Acode:** Save in Acode updates the parent form, then Save the element and report. Closing with Discard drops that form's changes.
- **Cell error:** read its message; verify cell type, paths and local modules. External CDN imports are blocked.
- **Search:** the same read-only panel is used in Acode and HTML. Literal, case, whole word and regular expression options are available; invalid RegExp is reported. Match count is capped at 1,000; rendered-page text scanned is capped at 500,000 characters. Search reads rendered DOM text, not pixels inside images/canvas.
- **Scroll/state:** rotation and mini/full retain viewport and search query/options/current match for the live session. Search restoration does not jump back to the first match. The bottom-right arrow returns to the top explicitly. Use the draggable Acode scrollbar at the right edge.

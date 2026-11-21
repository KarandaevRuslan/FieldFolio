# Document и JavaScript

Встроенная справка по Document, VFS отчёта и локальному рантайму Notebook Kit/Nexus.

## Содержание

- [Создание, редактирование и сохранение](#workflow)
- [Ячейки, зависимости и освобождение ресурсов](#cells)
- [Формат документа](#source)
- [Контекст приложения](#context)
- [VFS только для чтения](#vfs)
- [Все типы данных](#types)
- [Nexus](#nexus)
- [Просмотр и поиск](#reading)
- [Язык, тема и отдельный CSS](#themes)
- [Часы и пример таймера](#timers)
- [Картинки, аудио и обложки](#media)
- [Модели: детекция, маски, классификация и другие результаты](#analysis)
- [Данные Location, Weather и Clock](#widgets)
- [Изоляция и намеренные ограничения](#safety)
- [Решение проблем](#troubleshooting)

<a id="workflow"></a>

## Создание, редактирование и сохранение

1. В редакторе отчёта добавьте **Document**, задайте логическое имя и необязательный отображаемый заголовок, затем откройте код.
2. Измените HTML блокнота в Acode. Сохранение в Acode возвращает исходник в форму Document; **Save в форме сохраняет элемент**. Сохраните отчёт для записи всего документа.
3. Preview показывает тот же компонент, что и просмотрщик отчёта. Разверните миниатюру для прокрутки, поиска и чтения страницы.
4. Элемент Text хранит текст/исходник, даже с подсказкой расширения `js`. Выполняет ячейки именно Document. Сам Acode исходник не запускает.

Переключатели глаза/заголовка влияют на отображение. Они не меняют логические пути и не запрещают доступ другим Document этого отчёта. Логическое имя — имя в VFS; display name — подпись для читателя.

<a id="cells"></a>

## Ячейки, зависимости и освобождение ресурсов

У каждой ячейки должен быть уникальный `id`. `type="module"` содержит обычный JavaScript, `type="text/markdown"` — Markdown. Сохраняйте оболочку `<notebook>`. Для других поддерживаемых типов используйте команды вставки ячеек Acode.

`display(value)` выводит значение. Для DOM доступны `html`/`htl`, для контролов — `Inputs`, для файлов VFS — `FileAttachment`. В Observable JavaScript именованные значения и `viewof` участвуют в реактивном графе зависимостей. Обычные локальные переменные отдельных JS-модулей между модулями не разделяются. Связанный императивный код держите в одном модуле либо используйте Observable-ячейки.

Освобождайте собственные интервалы и обработчики при инвалидировании ячейки:

```js
const output = document.createElement("output");
const update = () => output.textContent = new Date().toLocaleTimeString(Folio.locale);
update();
const interval = setInterval(update, 1000);
invalidation.then(() => clearInterval(interval));
display(output);
```

Для недоверенных строк используйте `textContent` или шаблоны `html`. При пересчёте вывод ячейки может замениться: не храните надолго ссылки на устаревшие DOM-узлы другой ячейки.

Document исполняет ячейки **Observable Notebook Kit 2.9.1** и **Nexus 0.1.20** в изолированной среде. Элемент Text с подсказкой расширения `js` хранит исходный код как текст: Acode подсвечивает его, но не исполняет.

<a id="source"></a>

## Формат документа

```html
<notebook>
  <script id="1" type="module">
    display(html`<h2>Привет, \${Folio.language}</h2>`);
  </script>
  <script id="2" type="text/markdown">
    ## Текст Markdown
  </script>
</notebook>
```

Ячейки поддерживают JavaScript, Observable JavaScript, TypeScript, Markdown, HTML и TeX. Встроены Inputs и htl. Внешние CDN/импорты, Android bridge, file/content URL и постоянное хранилище недоступны. Ошибка отдельной ячейки показывается рядом с ней.

<a id="context"></a>

## Контекст приложения

- `Folio.language`, `.locale`, `.theme`, `.dark`, `.timeZone`, `.hourCycle` — язык, тема и настройки времени приложения.
- `Folio.now()` — Date; `Folio.timestamp()` — миллисекунды epoch.
- `folioNow` — реактивное время, обновляется каждую секунду.
- `Folio.timer("timer", 60000)` — таймер. Методы: `start()`, `pause()`, `reset(ms)`, `snapshot()`; снимок содержит remainingMs, elapsedMs, durationMs, running и finished.
- `Pocket` / `pocketNow` сохранены для совместимости с Pocket Notebook.


Идентификатор таймера: 1–80 латинских букв, цифр или символов `.`, `_`, `-`.
В контексте хранится до 64 таймеров; длительность ограничена от 1 мс до 7 дней.
Таймер принадлежит этому runtime и не является постоянным будильником Android.

<a id="vfs"></a>

## VFS только для чтения

`Folio.vfs` и `Vfs` — один интерфейс. Используются **логические** имена, а не отображаемые. Относительный путь считается от папки текущего Document. `/` — корень отчёта; `.` и `..` поддерживаются внутри него. Компоненты с `/`, `%`, `:` кодируйте `encodeURIComponent`; готовый `path` можно получить из list/stat.

| Метод | Назначение |
|---|---|
| `list(path = ".")` | Дети папки в порядке VFS |
| `stat(path)`, `exists(path)` | Имя, id, тип, видимость, порядок, ресурсы или наличие элемента |
| `await get(path)` / `await read(path)` | Полные типизированные метаданные |
| `await text(path)` | Текст, исходник или декодированный текстовый файл |
| `await json(path)` | JSON-файл, результат модели или данные виджета |
| `await blob(path, role)` | Байты ресурса |
| `url(path, role)` | Read-only URL в текущем runtime |
| `attachment(path, role)` | FileAttachment Notebook Kit |
| `await image(path, options)` | Изображение |

`root` равен `/`; `base` — папка текущего Document; `paths` — список путей элементов отчёта.
Методы `list`, `stat`, `exists`, `url` и `attachment` синхронные. `get`/`read`, `text`, `json`,
`blob` и `image` возвращают Promise; используйте `await`. Манифест ограничен 1000 элементами.
Попытка выйти за корень отчёта отклоняется.

`FileAttachment("/Папка/photo.jpg")` также использует VFS. По умолчанию role = `data`. Роли моделей: `input`, `mask`, `layer-0`, `layer-1` и далее. Для аудио есть `cover`. У folder нет бинарного attachment.

```js
const children = Folio.vfs.list();
display(Inputs.table(children.map(x => ({name: x.name, type: x.kind}))));
const text = await Folio.vfs.text("/Материалы/notes.txt");
const model = await Folio.vfs.json("/Анализ");
display(await Folio.vfs.attachment("/Анализ", "input").image());
```

Пути в примерах замените на существующие в своём отчёте. У Document **нет личной папки ресурсов**. Чтобы читать общие файлы, добавляйте их элементами VFS. Скрытые элементы своего отчёта доступны для чтения; видимость — оформление, не пароль. Другие отчёты не доступны. Записи, удаления, переименования и запуска native-моделей в API нет.

<a id="types"></a>

## Все типы данных

Text/Heading содержат body/text и настройки чтения; Text file — декодированный текст и исходные байты; Document — исходник Notebook Kit. Image и Audio содержат медиа. Folder содержит children.

`await Folio.vfs.get("/Песня")` содержит `audio.tags`, `audio.displayedTags`, overrides и флаги отображения. **`audio.cover`** — необязательное поле `{url, mime}`; у обложки нет своего логического имени. `Folio.report.cover` — такая же обложка отчёта. Для совместимости пока сохранён coverUrl.

```js
const song = await Folio.vfs.get("/Песня");
if (song.audio.cover) {
  const image = new Image();
  image.src = song.audio.cover.url;
  display(image);
}
```

Location содержит resolvedLocation или null. Clock содержит resolvedZoneId и epochMillis. Weather содержит weather из имеющегося кеша или null. Чтение VFS не запускает обновление погоды и не запрашивает разрешения геолокации. Доступные снимки поддерживает нативный хост, как описано ниже.

Model result: boxes — рамки и confidence; layers — полигоны с rings/отверстиями и/или maskUrl пиксельной маски; scores — классификация; values — регрессия/числа и единицы; text — текстовый вывод. Сохраняются model/task/provider, время выполнения, inputKind/inputText/inputTruncated. Координаты — normalized-image-0-1 относительно сохранённого input; используйте его размеры для canvas-оверлея.

<a id="nexus"></a>

## Nexus

```js
const scope = nexus.scope(invalidation);
const store = scope.store(0);
class Counter extends Nexus.Handler {
  run(ctx) { ctx.set(store, ctx.read(store) + 1); }
}
// Регистрации: scope.register(handler, {on: {any: [inputStore]}, execution: "inline"}).
// Транзакция изменения:
nexus.dispatcher.inline(ctx => ctx.set(store, 1));
```

В Observable-ячейке: `value = nexus.observe(scope, store)`. Идентичность Store сама по себе не делает ячейку реактивной. Scope, handlers и listeners освобождаются через invalidation/cleanup. Встроенный шаблон Document содержит полный пример со счётчиком и зависимым Store. Frida/native Nexus в WebView не доступен.

<a id="reading"></a>

## Просмотр и поиск

Миниатюра не прокручивается. Раскройте её для чтения страницы. Полное окно поддерживает поиск, `.*` для регулярных выражений, `Aa` для регистра и `ab` для целого слова; стрелки переходят по совпадениям. Ctrl+F открывает поиск. Позиция страницы, запрос и состояние живого документа сохраняются при повороте и мини ↔ полном окне в пределах текущего сеанса. При блокировке отчёта среда уничтожается; после завершения процесса состояние не обещается сохраняться.

<a id="themes"></a>

## Язык, тема и отдельный CSS

`Folio.theme` равен `"light"` или `"dark"`, `Folio.dark` — boolean. `Folio.language` — `"en"` или `"ru"`, `Folio.locale` — полный языковой тег. Эти настройки приходят из приложения. Стандартные контролы, Notebook Kit Inputs, поиск и фон уже следуют его теме.

Переводы можно хранить в Text file с именами `en.json` и `ru.json`, CSS — в Text/Text file `style.css`. Это обычные элементы VFS отчёта, а не личные ресурсы Document.

```js
const dictionary = await Folio.vfs.json(`/Translations/\${Folio.language}.json`);
const style = document.createElement("style");
style.textContent = await Folio.vfs.text("/Styles/style.css");
document.head.append(style);
invalidation.then(() => style.remove());
const heading = document.createElement("h2");
heading.textContent = dictionary.title || "Документ";
display(heading);
```

В CSS используйте `:root[data-theme="dark"]`, `:root[data-theme="light"]` или переменные `var(--bg)`, `var(--fg)`, `var(--card)`, `var(--line)`, `var(--accent)`. Ограничивайте свои правила классом контейнера, чтобы не ломать контролы Notebook Kit.

<a id="timers"></a>

## Часы и пример таймера

Даты и длительности задаются в **миллисекундах**. `Folio.timeZone` и `Folio.hourCycle` описывают формат устройства/приложения, а не произвольного Clock. Настройки такого Clock читайте через VFS.

```js
const timer = Folio.timer("cooking", 60000);
const output = document.createElement("output");
const start = document.createElement("button");
const pause = document.createElement("button");
const reset = document.createElement("button");
start.textContent = "Старт"; pause.textContent = "Пауза"; reset.textContent = "Сброс";
start.onclick = () => timer.start();
pause.onclick = () => timer.pause();
reset.onclick = () => timer.reset(60000);
const update = () => output.textContent = `\${Math.ceil(timer.snapshot().remainingMs / 1000)} с`;
const interval = setInterval(update, 100); update();
invalidation.then(() => clearInterval(interval));
const group = document.createElement("div");
group.append(output, start, pause, reset); display(group);
```

ID таймера должен быть стабильным и уникальным внутри блокнота: буквы, цифры, `_`, `.`, `-`, до 80 символов. Длительность — от 1 мс до 7 дней. Поворот и мини/полный режим сохраняют живой сеанс. Блокировка отчёта и завершение процесса не являются долговременным хранилищем таймера.

<a id="media"></a>

## Картинки, аудио и обложки

Для медиа используйте attachment, а не подставляйте логический путь вместо сетевого URL:

```js
display(await Folio.vfs.attachment("/Photos/site.jpg").image({width: 480}));
const player = document.createElement("audio");
player.controls = true;
player.src = Folio.vfs.url("/Recordings/note.m4a");
display(player);
```

`get` возвращает аудиоэлемент, включая `audio.tags` (исходные теги), `audio.displayedTags` (итоговые отображаемые значения), `audio.overrides`, `showTags`, `showCover` и скрытые теги. `audio.cover` и `Folio.report.cover` — необязательные поля `{url, mime}`. Самостоятельного логического имени у обложки нет. Используйте выданный URL; не конструируйте пути к хранилищу. Отсутствие обложки допустимо.

Текстовый файл читайте через `await Folio.vfs.text(path)`, чтобы учесть определённую приложением кодировку. `.blob()` возвращает исходные байты; `.json()` разбирает текстовый файл как JSON и выдаст ошибку при неверном формате.

<a id="analysis"></a>

## Модели: детекция, маски, классификация и другие результаты

`await Folio.vfs.get("/Analysis")` возвращает элемент, результат лежит в его `result`. `await Folio.vfs.json("/Analysis")` читает JSON-ресурс результата. Проверяйте `task` и имеющиеся поля: не каждая модель выполняет детекцию дорог.

| Поле | Содержание |
|---|---|
| `boxes` | Метка, confidence и xMin/yMin/xMax/yMax |
| `layers` | Полигоны сегментации с отверстиями и/или пиксельная маска `maskUrl` |
| `scores` | Классы и оценки классификации |
| `values` | Числа/регрессия с подписями и единицами |
| `text` | Выход текстовой модели |
| `inputText`, `inputKind`, `inputTruncated` | Сохранённый вход и признак усечения |

`result.inputUrl`, `result.maskUrl` и URL масок слоёв относятся только к разрешённым ресурсам этого сеанса. Роли attachment: `input`, `mask`, `layer-0`, `layer-1` и т.д. Перед запросом необязательной роли проверяйте `stat(path).assets`.

```js
const node = await Folio.vfs.get("/Analysis");
const result = node.result;
if (result) display(Inputs.table(result.scores || []));
if (Folio.vfs.stat("/Analysis").assets.input) {
  display(await Folio.vfs.attachment("/Analysis", "input").image());
}
```

Координаты рамок/полигонов нормированы к 0–1 относительно **сохранённого входа анализа**, а не другой исходной картинки. Умножайте x на ширину и y на высоту входа. Пиксельную маску нужно рисовать в размерах входного изображения; это не полигон. Просмотрщик не может запускать локальную/серверную модель. Создавайте или заменяйте Model result в редакторе отчёта.

<a id="widgets"></a>

## Данные Location, Weather и Clock

`get(path)` содержит конфигурацию виджета и вычисленные поля:

- Location/Weather: `resolvedLocation` — `{latitude, longitude}` либо `null`.
- Weather: `weather` — кешированные `{temperature, weatherCode, zoneId}` либо `null`; чтение не запрашивает погоду или разрешение геолокации.
- Clock: `resolvedZoneId` — вычисленный IANA ID, `epochMillis` — время чтения метаданных. После проверки ID форматируйте через `Intl.DateTimeFormat(Folio.locale, {timeZone: node.resolvedZoneId, ...})`.

Активный нативный хост Document подписывает необходимые отчёту источники координат/погоды,
включая скрытые элементы с геолокацией устройства. Поэтому последующие чтения VFS могут
получить обновлённые снимки. JavaScript не управляет провайдерами; `Folio.watch` отсутствует,
а чтение значения не выдаёт разрешение геолокации. У встроенной справки нет погодного провайдера.

Чтение VFS даёт снимок. Для постоянно меняющихся часов используйте `folioNow` в Observable-ячейке или интервал с очисткой. Не заменяйте отсутствие данных вымышленными нулевыми координатами, погодой или UTC — покажите недоступное состояние. Pull to update и редактирование нативных виджетов остаются действиями приложения.

<a id="safety"></a>

## Изоляция и намеренные ограничения

Исходник Notebook выполняется в sandbox-рендерере с непрозрачным origin. Acode — отдельный редактор с нативными возможностями; исходник остаётся в нём инертным текстом. У рендерера нет `NativeEditor`/Android bridge, прямого доступа к файловой системе приложения, `file:`/`content:`, постоянному хранилищу или внешним сетевым импортам. URL VFS — временные разрешения для конкретного отчёта/сеанса; их не следует сохранять или передавать наружу.

API write/remove/rename/copy/move нет. Скрытые элементы доступны Document того же отчёта: видимость не является шифрованием. Блокировка отчёта уничтожает рендереры и отзывает доступ к ресурсам. Сокрытие заголовка не защищает секреты.

DOM, canvas, локальные модули Notebook Kit работают внутри sandbox. Нативные разрешения, запуск моделей, файлы ОС и другие отчёты находятся за его пределами.

<a id="troubleshooting"></a>

## Решение проблем

- **Путь не найден:** проверьте логическое имя, папку, кодирование компонентов и наличие ресурса в этом отчёте. Используйте `Folio.vfs.list("/")` и `exists(path)`.
- **Нет attachment:** у папок нет байтов, а необязательная маска модели/обложка аудио может отсутствовать. Проверьте `stat(path).assets`.
- **Нет подсветки:** задайте явную подсказку расширения Text для Acode (`js`, `json`, `css` и т.д.). Смена отображаемого заголовка не выбирает язык.
- **Неподвижный preview:** миниатюра не прокручивается, разверните её. JavaScript в Text не выполняется.
- **Не сохранилось после Acode:** Save в Acode обновляет родительскую форму, затем сохраните элемент и отчёт. Discard отменяет изменения формы.
- **Ошибка ячейки:** проверьте её сообщение, тип, пути и локальные модули. Импорты с внешних CDN запрещены.
- **Поиск:** одна панель используется в read-only Acode и HTML. Есть обычный поиск, регистр, целые слова и RegExp. Неверный RegExp показывает ошибку. До 1000 совпадений; в HTML сканируются до 500000 символов видимого DOM-текста. Текст внутри картинки/canvas не распознаётся.
- **Прокрутка и состояние:** поворот и мини/полный режим сохраняют позицию, запрос, параметры и текущее совпадение в живом сеансе. Восстановление поиска не переносит к первому совпадению. Стрелка внизу справа явно поднимает страницу наверх. Скроллбар Acode справа можно перетаскивать.

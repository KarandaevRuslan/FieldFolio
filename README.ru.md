# Fieldfolio

Fieldfolio — Android-приложение для полевых отчётов. В одном отчёте можно хранить текст,
фотографии, аудио, координаты, погоду, часы, результаты моделей и интерактивные документы.
Основная работа с отчётами выполняется локально. Сервер используется для удалённого анализа
изображений и работы с профилем.

| Часть проекта | Назначение | Документация |
|---|---|---|
| MobileApp | Android-клиент и web-редактор | [Описание приложения](MobileApp/README.md) |
| Server | Java API, проверка изображений и авторизация | [Запуск сервера](Server/README.md) |
| Server/python-yolo | Python-сервис инференса | [Запуск YOLO](Server/python-yolo/README.md) |
| DetectionTrainProj | Датасет, обучение и экспорт | [Подготовка моделей](DetectionTrainProj/README.md) |

Для сборки Android откройте каталог `MobileApp/` в Android Studio. Нужны JDK 17,
Android SDK 36 и build-tools 35.0.0. В PowerShell из этого каталога:

```powershell
.\gradlew.bat :app:assembleDebug
```

APK находится в `app/build/outputs/apk/debug/`. Для локальных отчётов доступен гостевой
режим. Настоящий вход через Firebase требует собственного `app/google-services.json`.
Node нужен для изменения и пересборки web-части, а не для обычной сборки с готовыми ресурсами.

Начните с [руководства пользователя](docs/USER_GUIDE.ru.md).
Порядок сохранения вложенных форм, различие имён и заголовков, защита отчётов и экспорт
описаны там отдельно. Для разработчика доступны [общий индекс](docs/README.md),
[установка](INSTALL.md), [архитектура](docs/ARCHITECTURE.md),
[сборка и проверки](docs/BUILD_AND_TEST.md) и [устранение ошибок](docs/TROUBLESHOOTING.md).

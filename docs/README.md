# Documentation

## Use and set up Fieldfolio

| Need | Guide |
|---|---|
| Product overview | [English](../README.md) / [Русский](../README.ru.md) |
| Work with reports | [Руководство пользователя](USER_GUIDE.ru.md) |
| Install the required components | [Installation](../INSTALL.md) |
| Resolve setup or runtime failures | [Troubleshooting](TROUBLESHOOTING.md) |

## Develop the components

| Component | References |
|---|---|
| Repository | [Architecture](ARCHITECTURE.md), [build/test](BUILD_AND_TEST.md), [CI](WORKFLOWS.md), [hygiene](REPOSITORY_HYGIENE.md) |
| Android | [README](../MobileApp/README.md), [architecture](../MobileApp/ARCHITECTURE.md), [component map](../MobileApp/docs/architecture/MAP.md), [decisions](../MobileApp/docs/architecture/DECISIONS.md) |
| Android data and models | [Data formats](../MobileApp/docs/DATA_FORMATS.md), [model adapters](../MobileApp/docs/MODELS.md), [resource refresh](../MobileApp/docs/CONTEXT_REFRESH_RU.md) |
| Interactive documents | [API EN](../MobileApp/docs/api/document-en.md) / [RU](../MobileApp/docs/api/document-ru.md) |
| Android delivery | [Building](../MobileApp/docs/BUILDING.md), [testing](../MobileApp/docs/TESTING.md), [releasing](../MobileApp/docs/RELEASING.md), [validation record](../MobileApp/docs/VALIDATION.md) |
| Java API | [README](../Server/README.md), [architecture](../Server/docs/ARCHITECTURE.md), [configuration](../Server/docs/CONFIGURATION.md), [HTTP API](../Server/docs/API.md), [TLS](../Server/docs/TLS.md), [testing](../Server/docs/TESTING.md) |
| Python inference | [Worker setup and request contract](../Server/python-yolo/README.md) |
| Dataset and training | [Training workspace](../DetectionTrainProj/README.md), [converter](../DetectionTrainProj/tools/README.md), [data preparation](../DetectionTrainProj/docs/DATASET.md), [training/export](../DetectionTrainProj/docs/TRAINING_AND_EXPORT.md) |

Edit the canonical Document references in `MobileApp/docs/api/`. Generated offline help is
synchronized from those files. Run `python3 scripts/check_documentation.py` before submitting
documentation changes. [Documentation maintenance](DOCUMENTATION.md) describes ownership,
generated files and verification.

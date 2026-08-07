# Roadmap

## Regression baseline

`REAL_DWG_ON_ANDROID` закрыт и сохранён Git-меткой `real-dwg-on-android`.

## Закрытые milestones

- `REAL_DWG_ON_ANDROID` — Git-метка `real-dwg-on-android`.
- `USABLE_DWG_VIEWER` — Git-метка `usable-dwg-viewer`.
- `VISUAL_FIDELITY_01` — Git-метка `visual-fidelity-01`, 97,41% visible coverage на `test_01.dwg`.
- `MULTI_DWG_VALIDATION` — Git-метка `multi-dwg-validation`, автоматический Android regression runner для `test-data`.

## Текущий milestone: MOBILE_UX_01

- [x] Русский интерфейс и локализованное форматирование измерений.
- [x] Минимальный стартовый экран с SAF и недавними файлами.
- [x] Viewer с компактной верхней панелью и одним floating control.
- [x] Вертикальное Material 3 меню основных действий с tooltips/accessibility descriptions.
- [x] Слои в `ModalBottomSheet`, изменения применяются без parsing.
- [x] Компактный measurement mode с Endpoint Snap и DWG units.
- [x] Light/dark palette, edge-to-edge system insets, portrait/landscape.
- [x] Orientation и UI state changes сохраняют существующий `DrawingModel` без reparsing.

## USABLE_DWG_VIEWER

- [x] 53 DWG-слоя `test_01.dwg` извлекаются из LibreDWG.
- [x] Слои включаются и выключаются в renderer без повторного parsing.
- [x] Произвольный `.dwg` открывается через Android Storage Access Framework.
- [x] Расстояние вычисляется в координатах `DrawingModel`, независимо от zoom.
- [x] `INSUNITS` преобразуется в явную единицу; неизвестная/безразмерная единица не маскируется под миллиметры.
- [x] Реализован базовый Endpoint snap с экранным радиусом захвата, пересчитанным в drawing coordinates.
- [x] Recent files сохраняют `content://` URI и persistable read permission.
- [x] Сборка и основные сценарии проверены на Android 16 emulator.

## Статистика regression-файла (актуальная)

`test_01.dwg` содержит 72 138 объектов:

- 52 369 графических entity;
- 13 892 служебных object (таблицы, словари, стили и другие DWG-данные);
- 5 877 структурных entity (`BLOCK`/`ENDBLK`/`SEQEND`/`VERTEX`), которые не являются самостоятельными render primitives;
- 42 849 поддерживаемых entity;
- 43 485 render primitives после раскрытия блоков;
- 97,41% visible graphical coverage.

Таким образом, разница между `num_objects` и количеством примитивов ожидаема и не считается целиком ошибкой renderer. Полная разбивка по типам доступна в debug-диалоге Stats и Logcat.

## Известные ограничения

- LWPOLYLINE bulge пока отрисовывается прямым сегментом.
- Endpoint snap работает по геометрии уже поддерживаемых примитивов; intersection/midpoint snap ещё не реализованы.
- INSERT/BLOCK, TEXT/MTEXT и линейные размеры поддерживаются; HATCH, WIPEOUT и часть POLYLINE пока входят в unsupported graphical.
- Canvas остаётся renderer первого полезного среза; решение о GPU будет принято после профилирования крупных файлов.
- GPLv3+ LibreDWG требует GPL-совместимого распространения всего приложения.

После MOBILE_UX_01 дальнейшие renderer-приоритеты определяются multi-DWG regression, а не механическим coverage одного файла. Площадь, заметки, editing и cloud пока вне scope.

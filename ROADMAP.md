# Roadmap

## Regression baseline

`REAL_DWG_ON_ANDROID` закрыт и сохранён Git-меткой `real-dwg-on-android`.

## Текущий milestone: USABLE_DWG_VIEWER

- [x] 53 DWG-слоя `test_01.dwg` извлекаются из LibreDWG.
- [x] Слои включаются и выключаются в renderer без повторного parsing.
- [x] Произвольный `.dwg` открывается через Android Storage Access Framework.
- [x] Расстояние вычисляется в координатах `DrawingModel`, независимо от zoom.
- [x] `INSUNITS` преобразуется в явную единицу; неизвестная/безразмерная единица не маскируется под миллиметры.
- [x] Реализован базовый Endpoint snap с экранным радиусом захвата, пересчитанным в drawing coordinates.
- [x] Recent files сохраняют `content://` URI и persistable read permission.
- [x] Сборка и основные сценарии проверены на Android 16 emulator.

## Статистика regression-файла

`test_01.dwg` содержит 72 138 объектов:

- 52 369 графических entity;
- 13 892 служебных object (таблицы, словари, стили и другие DWG-данные);
- 5 877 структурных entity (`BLOCK`/`ENDBLK`/`SEQEND`/`VERTEX`), которые не являются самостоятельными render primitives;
- 25 838 поддерживаемых и отрисованных entity;
- 26 531 пока неподдерживаемых графических entity.

Таким образом, разница между `num_objects` и количеством примитивов ожидаема и не считается целиком ошибкой renderer. Полная разбивка по типам доступна в debug-диалоге Stats и Logcat.

## Известные ограничения

- LWPOLYLINE bulge пока отрисовывается прямым сегментом.
- Endpoint snap работает по геометрии уже поддерживаемых примитивов; intersection/midpoint snap ещё не реализованы.
- INSERT/BLOCK, TEXT/MTEXT, DIMENSION, HATCH и часть POLYLINE пока входят в unsupported graphical.
- Canvas остаётся renderer первого полезного среза; решение о GPU будет принято после профилирования крупных файлов.
- GPLv3+ LibreDWG требует GPL-совместимого распространения всего приложения.

Ближайшие задачи после milestone: расширять поддержку графических типов, добавить выбор единиц для unitless DWG и профилирование больших файлов. Визуальная полировка, площадь и заметки пока вне scope.

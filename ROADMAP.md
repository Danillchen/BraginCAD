# Roadmap

## Текущий milestone: REAL_DWG_ON_ANDROID

- [x] Проверено локальное Android/NDK/CMake окружение.
- [x] Выбран LibreDWG 0.13.4 и изолирован адаптером.
- [x] Созданы `DrawingModel` и Canvas renderer для базовой геометрии.
- [x] Реализованы fit, pan и pinch zoom.
- [x] Подтверждена Android-сборка native parser (NDK 30 / x86_64).
- [x] APK установлен и реальный `test_01.dwg` проверен на Android 16 emulator без критических ошибок.

Далее: слои, измерение расстояния в drawing coordinates, SAF file picker, recent files и UI polish.

## Известные ограничения и решения

- AC1024 соответствует DWG 2010; LibreDWG заявляет чтение этой версии.
- На первом срезе поддерживаются LINE, LWPOLYLINE, ARC, CIRCLE и ELLIPSE; остальные сущности считаются и логируются как unsupported.
- LWPOLYLINE bulge пока отрисовывается прямым сегментом.
- Regression-файл содержит 72 138 объектов; текущий срез отображает 25 838 базовых примитивов, а 46 300 сущностей пока учтены как unsupported.
- Зафиксированное время локального parse + построения модели на emulator: 2 548 мс.
- Canvas выбран до профилирования реальных больших чертежей; переход к GPU renderer будет основан на измерениях.
- GPLv3+ LibreDWG требует GPL-совместимого распространения всего приложения.

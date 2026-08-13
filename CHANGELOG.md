# Changelog

## 0.1.1 — 2026-08-13

### Совместимость

- добавлены настоящие native-сборки BraginCAD/LibreDWG для `armeabi-v7a` и `x86`;
- universal APK теперь поддерживает `armeabi-v7a`, `arm64-v8a`, `x86` и `x86_64`;
- исправлена установка на устройствах с 32-битным Android userspace, включая часть телефонов с 64-битным процессором MediaTek.

## 0.1.0 — 2026-08-10

Первый публичный preview BraginCAD.

### DWG viewer

- локальная LibreDWG/NDK-цепочка для `arm64-v8a` и `x86_64`;
- слои, SAF, recent files, pan, zoom и fit;
- поддержка BLOCK/INSERT, TEXT/MTEXT, dimensions и основных CAD-примитивов;
- метрики graphical и visible graphical coverage;
- viewport-aware HATCH renderer с LOD и work budget.

### Измерения

- Distance, Area и Coordinate;
- Endpoint snap и DWG units;
- точность 0–4 знака;
- session-only overlays с выбором, удалением одной отметки и Delete All.

### Mobile UX

- Material 3, светлая и тёмная темы;
- единый минималистичный brand asset;
- universal debug APK для телефона и эмулятора.

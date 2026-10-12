# BraginCAD

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/ic_bragincad_brand.png" width="180" alt="BraginCAD 1.0 logo">
</p>

<p align="center">
  Автономный Android-просмотрщик строительных DWG с точными измерениями.
</p>

BraginCAD 1.0 открывает реальные DWG непосредственно на Android: LibreDWG декодирует файл локально, собственный `DrawingModel` отделяет CAD-данные от UI, а оптимизированный Canvas renderer отображает геометрию без облака и загрузки чертежей в Интернет.

## Возможности

- локальное открытие `.dwg` через Android Storage Access Framework;
- перемещение, масштабирование, показ чертежа целиком и вращение двумя пальцами;
- слои с мгновенным переключением без повторного parsing, исходными DWG-цветами и ручной заменой цвета;
- LINE, POLYLINE, ARC, CIRCLE, ELLIPSE, TEXT/MTEXT, INSERT/BLOCK, DIMENSION, HATCH и другие строительные сущности;
- Endpoint object snap;
- измерение расстояния, площади и координат, вынесение точки на заданные расстояние и угол;
- выбор геометрических объектов с отображением типа, слоя, длины, подсветкой и индивидуальной заменой цвета;
- выбор и удаление отдельных пользовательских измерений, а также очистка всех измерений;
- корректная обработка `INSUNITS` и настраиваемая точность;
- недавние файлы, миниатюры и переименовываемые локальные копии DWG;
- настраиваемые фон, цвета слоёв, размер подписей, светлая и тёмная темы;
- встроенная проверка новых версий в GitHub Releases;
- чертежи обрабатываются полностью локально; Интернет используется только по явному нажатию «Проверить обновления».

## Установка

Готовый universal APK находится в разделе [Releases](../../releases/latest). Он содержит native-библиотеки для:

- `arm64-v8a` — современные физические Android-устройства;
- `armeabi-v7a` — устройства с 32-битным Android userspace;
- `x86_64` — Android Emulator;
- `x86` — старые 32-битные Android Emulator images.

Минимальная версия Android — 8.0 (API 26).

## Сборка

Требуются JDK 17+, Android SDK 36, NDK и CMake.

```powershell
$env:ANDROID_HOME = "C:\Android\Sdk"
.\gradlew.bat assembleDebug
```

APK будет создан в `app/build/outputs/apk/debug/`. LibreDWG собирается из vendored source отдельно для каждой ABI; библиотеки одной архитектуры не подменяются файлами другой архитектуры.

## Архитектура

```text
DWG → LibreDWG/NDK → JNI adapter → DrawingModel → RenderModel → Android Canvas
                                      └──────────── Measurement overlays
```

Пользовательские измерения хранятся отдельно от `DrawingModel`, не изменяют исходный DWG и существуют только в текущей сессии. Видимость слоёв также применяется к готовой модели без повторного чтения файла.

## Regression-тестирование

Debug APK поддерживает автоматический pipeline для локального каталога `test-data`:

```powershell
.\scripts\run-dwg-regression.ps1 -Device emulator-5554
```

Runner собирает coverage сущностей, decode/model/render timings, primitive count, PSS и признаки Java/native crash. Отчёты сохраняются в `build/regression`. Реальные DWG исключены из Git и не публикуются.

Для основного тестового строительного чертежа достигнуто 97,41% visible graphical coverage. HATCH renderer использует viewport-aware generation, culling и screen-density LOD для ограничения невидимой работы.

## Лицензия

BraginCAD и встроенный GNU LibreDWG распространяются на условиях [GNU GPL v3 or later](LICENSE). Сведения о LibreDWG находятся в `third_party/libredwg`.

# BraginCAD

Автономный read-only просмотрщик строительных DWG для Android. Первый milestone доказывает цепочку: реальный DWG → локальный LibreDWG → собственный `DrawingModel` → Android Canvas renderer.

## Стек и сборка

- Kotlin, Android SDK 36, Android NDK, CMake;
- LibreDWG 0.13.4 (GPLv3+), встроенный локально без сети во время работы;
- минимальная команда сборки: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).

Нужны JDK 17+, Android SDK/Build Tools 36, NDK и CMake. APK находится в `app/build/outputs/apk/debug/`. Приложение не запрашивает `INTERNET`.

## Архитектура

`LibreDwgAdapter` изолирует native parser. Результат преобразуется в собственный `DrawingModel`, после чего неизменяемый `DrawingView` отвечает за fit, pan, zoom, видимость и измерения. Compose Material 3 управляет только лёгким UI-состоянием вокруг Android View renderer; раскрытие меню, смена темы и переключение слоёв не вызывают повторный parsing.

Тестовый DWG копируется в assets только для regression/debug-сборки и не должен публиковаться. LibreDWG и производное приложение распространяются на условиях GPLv3+; текст лицензии находится в `third_party/libredwg/COPYING`.

Проверено на Android 16 emulator: `test_01.dwg` (AC1024/DWG 2010), 43 485 render primitives, 97,41% visible graphical coverage; pan и pinch zoom работают, критических ошибок Logcat нет.

## Использование

- «Открыть чертёж» использует системный Storage Access Framework.
- Основные действия viewer находятся в вертикальном floating menu, чтобы чертёж занимал почти весь экран.
- «Слои» открывает Material 3 bottom sheet и меняет видимость без reparsing.
- «Измерить» включает компактный режим двух точек; Endpoint Snap активен автоматически, результат использует `INSUNITS` DWG.
- «Недавние» повторно открывают файлы по сохранённому `content://` URI.
- Светлая, тёмная и системная темы выбираются в настройках; ориентация меняется без повторного parsing.

`test_01.dwg` определяет единицы как миллиметры (`INSUNITS=4`). Для unitless/unknown DWG интерфейс явно показывает `drawing units`, не предполагая миллиметры.

## Multi-DWG regression

Debug APK поддерживает автоматический regression-вход для DWG из `test-data`; release-сборка его игнорирует. Runner собирает version/entity coverage, unsupported breakdown, decode/model/render timings, primitive count, PSS и признаки Java/native crash для каждого файла:

```powershell
$env:ANDROID_HOME = "C:\Android\Sdk"
.\scripts\run-dwg-regression.ps1
```

Отчёты JSON, CSV, Markdown и полный Logcat каждого DWG сохраняются в `build/regression`. Можно передать `-Device emulator-5554`, `-InputDirectory path` и `-SkipBuild`/`-SkipInstall`. Runner принимает все `.dwg` каталога в сортированном порядке, поэтому новые файлы автоматически попадают в следующий прогон.

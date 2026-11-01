# NekoFlash 2.0 — REV22: генераторы файловых систем внутри APK

Дата: 26 октября 2026. Репозиторий: **Ncorror/NekoFlash-2.0**.
База REV21: `e5301882db536d8fe5af4898ee5552b45eb0ad33`.
Код REV22: `84df7647045af5c089f4c5432527c5808c2e39b5`.
[Draft PR #9](https://github.com/Ncorror/NekoFlash-2.0/pull/9) направлен в REV21 (#7).
Серия ещё не объединена в `main`; production Release не выпускался.

## Что сделано

В REV21 подготовка `format`/`-w` зависела от случайного наличия `mke2fs`/`mkfs.f2fs`
в системных каталогах Android. В REV22 оба генератора входят в APK для
`arm64-v8a`, `armeabi-v7a`, `x86` и `x86_64`. Приложение запускает их отдельными
процессами из `applicationInfo.nativeLibraryDir`. Root, Termux и системные mkfs
при выполнении не нужны. Исполняемые файлы не копируются в writable app data.

- e2fsprogs **1.47.0**, commit `25ad8a431331b4d1d444a70b6079456cc612ac40`.
- f2fs-tools **1.16.0**, commit `06c027abc6153c4a97cba5317844e8dcaaee3cf7`.
- NDK **27.2.12479018**, минимальный API **26**; SHA-256 исходных архивов закреплены в manifest.
- Это закреплённая база совместимости с REV21, не заявление о последних версиях upstream.
- C-исходники инструментов не патчились; частные библиотеки поддержки связаны статически.
  Упакованные программы динамически требуют только Android `libc.so` и `libdl.so`.
- `preBuild` готовит/проверяет генераторы. `useLegacyPackaging=true` обеспечивает
  установку исполняемых файлов PackageManager для Debug и Release.
- `mke2fs` получает частный профиль и `root_owner=0:0`; системная конфигурация телефона
  не определяет создаваемый образ. Сохранены casefold/projid/compress и прежние параметры геометрии.
- Сохранены отмена дочернего процесса, проверка superblock/sparse, очистка временных
  образов/логов; добавлена очистка профиля. Ошибка подготовки останавливает план до erase.
- APK содержит исходные лицензии и notices. Рядом с APK публикуется полный архив
  соответствующих исходников с manifest и сборочным скриптом; он проверен после скачивания.

Инструкция для сборки и офлайн-пересборки: [vendor/filesystems/README.md](../../vendor/filesystems/README.md).
Утилиты сохраняют GPL и лицензии компонентов; лицензия приложения их не заменяет.
Сохраняй `filesystem-tools-source.tar.gz` при дальнейшей передаче APK.

## Проверки

[CI 40370975789](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/40370975789),
весь workflow — **SUCCESS**: основной job `121741307658`, Android API 26 `121744914273`,
Android API 34 `121744914225`.

| Проверка | Результат |
|---|---|
| JVM, включая реальные Linux mkfs/fsck и отмену процесса | 179 тестов, 0 failures/errors/skipped |
| Android lint | 0 ошибок, 47 прежних предупреждений |
| Native transfer regression, ASan/UBSan | PASS |
| Debug APK и инструментальный APK | PASS |
| 8 ELF-программ в фактическом APK | PASS: четыре ABI, PIE entry point, системный linker, отсутствие TEXTREL/RPATH, только платформенные зависимости |
| Выравнивание новых ELF | Все LOAD сегменты не меньше 16 KiB; весь проект на 16-KiB Android отдельно не проверялся |
| Android API 26 и API 34, x86_64 | PASS: по 4 инструментальных теста на каждой версии, 8 запусков; обычный UID приложения, без root |
| Образы, созданные Android-генераторами | Все 8 независимо раскрыты из sparse, проверены e2fsck/fsck.f2fs с кодом 0; корневой inode ext4 имеет uid/gid 0:0 |
| Архив источников, офлайн-извлечение и контрольные суммы | PASS: 2241 файл e2fsprogs, 78 файлов f2fs-tools; скрипт/manifest совпадают с кодом ревизии |
| REV5–REV22 guards, RU/EN и XML | PASS: 46 XML, 463 пары строк, 2 plurals |

Четыре Android-сценария: ext4 32 MiB, ext4 с casefold/projid, F2FS 128 MiB,
F2FS с casefold/projid/compress. Проверяются запуск именно установленных файлов,
размер sparse и очистка временного каталога. Раскрытие на CI отклоняет DONT_CARE
в образах форматирования. ARM/ARM64 пока проверены сборкой и ELF-анализом,
не исполнением на физическом телефоне.

[Android 8: протоколы и sparse-образы](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/40370975789/artifacts/12672863451),
[Android 14: протоколы и sparse-образы](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/40370975789/artifacts/12673171724).

JUnit XML и lint XML скачаны и пересчитаны независимо. Типы оставшихся предупреждений:
UnusedResources 33, SmallSp 2, Autofill 5, UseKtx 6, PluralsCandidate 1.
Предупреждений Kotlin/C-компилятора не обнаружено; configure сообщает об использовании
непрефиксированных `llvm-ar`/`llvm-ranlib`, фактические ELF архитектуры проверены.

Первый CI остановился до компиляции генераторов: `sdkmanager` не был в PATH runner.
Исправлен явный путь к установленному Android SDK. Итоговая основная сборка прошла.
Локально Android SDK отсутствует; Android-сборка/инструментальные тесты выполняются в CI.

## Сборка для проверки

[DEV APK и соответствующие исходники](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/40370975789/artifacts/12673810170).
Артефакт хранится до **9 ноября 2026**.
[Сборочные/JUnit/lint отчёты](https://github.com/Ncorror/NekoFlash-2.0/actions/runs/40370975789/artifacts/12672463471)
хранятся до **25 ноября 2026**.

- APK: `NekoFlash-6.0.0-alpha11-debug.apk`, **10 795 582 байта**.
- APK SHA-256: `e9d0fa0b4d69cdf9a0ad0079fd102c7a6488d1bdfdfe053dce60aed3a0bf0717`.
- Архив источников: `filesystem-tools-source.tar.gz`, **9 887 425 байт**.
- Архив источников SHA-256: `7ed6e3d81323ef1eb121a58ce11573a8649bbea05d0265517b7a8c95780a4463`.

DEV package: `ru.forum.adbfastboottool.dev`, версия `6.0.0-alpha11-dev`, versionCode 234;
BUILD_ID различает ревизии по commit/run. DEV устанавливается рядом с исходным приложением.
APK стал больше REV21 на **184 480 байт**: legacy JNI packaging также сжимает native-файлы в ZIP.
Это изменение размера APK, не оценка занятого места после установки.

## Сверка с оригиналом и текущий план

[Повторная сверка](REV22-LEGACY-COMPARISON.json) проведена с исходным архивом
`NekoFlash-main-legacy.zip`, SHA-256
`74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`; CRC — PASS.
Из 162 исходных файлов: **130 без изменений, 32 изменены, 0 удалены**.
Это накопленная разница всей серии, а не только REV22.

В REV22 не менялись `native_usbfs.cpp`, `AdbProtocol`, `FastbootProtocol`,
`NativeUsbfsBackend`, `FlashOperationService`, MiAccount/MiUnlock. В DeviceViewModel
заменён только источник генераторов в существующем плане. С исходным архивом совпадают
98,7% строк native USBFS, 96,2% ADB, 97,9% Fastboot, 87,5% DeviceViewModel.
Проценты рассчитаны по совпадающим строкам, не означают доказанную корректность протоколов.
Новые вспомогательные файлы и upstream-утилиты добавлены; проект не переписан с нуля.

Пять согласованных вкладок, тёмная тема/оранжевый акцент, Sideload/Unlock и существующий
Центр операций сохранены. В REV22 визуальной переработки экранов нет.
**IQ-HARRY7** остаётся в Developers README.

| Следующий приоритет | Что осталось |
|---|---|
| R05 | Разбиение больших RAW/sparse под `max-download-size`, повторные download, отмена и корректная сборка результата |
| R01/R04/R15 | Проверка ARM/ARM64 на целевом Android-хосте и реальных bootloader/fastbootd, USB detach/фон/поворот/kill/длительная передача |
| R07/R08/R09 | Место и отмена импорта, восстановление Quick Flash, SAF/FD для Mass Flash |
| R06/R10/R11/R17 | PTY/IME на телефоне, длинные очереди, состояния Home, оставшиеся предупреждения lint |
| R16 | Интеграция серии, версия, постоянная подпись Release, установка и обновление |

Оценка готовности: **около 75%, техническая альфа**. Это оценка оставшихся задач,
не измеренное покрытие. Поставка mkfs в APK реализована; аппаратную совместимость,
большие образы и финальную полировку интерфейса нельзя считать завершёнными.
Ни одной команды записи на физическое USB-устройство в этой работе не отправлялось.

## Первичные источники

- [e2fsprogs, закреплённый исходник](https://github.com/tytso/e2fsprogs/tree/25ad8a431331b4d1d444a70b6079456cc612ac40).
- [Upstream F2FS v1.16.0](https://kernel.googlesource.com/pub/scm/linux/kernel/git/jaegeuk/f2fs-tools/+/refs/tags/v1.16.0).
- [Android 10: запрет exec из writable app home](https://developer.android.com/about/versions/10/behavior-changes-10).
- [Android: 16-KiB ELF alignment](https://developer.android.com/guide/practices/page-sizes).

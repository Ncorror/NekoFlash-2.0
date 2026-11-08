# NekoFlash 2.0 — аппаратная смена Recovery после Sideload (9 ноября 2026)

## Последняя подтверждённая точка

HOST: POCO F7 onyx Android 16, NekoFlash DEV `6.0.0-alpha11+6a8accd8e33e.42517047929`.
TARGET: **разблокированный** POCO X3 Pro vayu, A-only.
Исходный закрытый журнал: `NekoFlash-diagnostics-1794175939025.zip`; не выкладывать USB-серийники/сырые логи в открытый репозиторий.

- **00:22:56**: обнаружено Lineage Recovery в режиме ADB SIDELOAD: USB `18D1:D001`, баннер `ro.product.name=lineage_vayu;ro.product.device=vayu`.
- **00:23:09**: отправлен `OrangeFox-R11.3-vayu.zip` (73 757 208 байт) через NekoFlash ADB Sideload; около 100% данных обслужено; **00:23:27** соединение закрылось до `DONEDONE`.
- **00:24:11**: появилось Recovery с USB `2717:D001`, баннер `ro.product.name=vayu;ro.product.device=vayu`, опять в режиме SIDELOAD. Фотографии показывают интерфейс OrangeFox.
- Следующая попытка Sideload другого ZIP (LineageOS) не доказывает успех установки OrangeFox ZIP.
- Независимый факт: старый LineageOS ZIP **не совпадает** по SHA-256 с официальным; его `Signature verification failed` не следует приписывать NekoFlash.

**Фактический результат:** передача OrangeFox ZIP подтверждена; физически наблюдался переход на другое Recovery. Финальный код установщика ZIP в исходном журнале отсутствует. Это **не** полный `INSTALL_SUCCESS`.

## Сравнение с оригинальным legacy

Исходник `NekoFlash-main-legacy.zip`, архив SHA-256 `74fe6f49fe466b15a2cd5b36e141c208823b4ef9e7a42ea96e0d5647b9c9d5b3`. Файлы первого импорта `b4431af15b39` `AdbProtocol.kt`, `DeviceViewModel.kt`, `RecoveryInstallVerifier.kt` совпали по SHA-256 с legacy-манифестом `docs/source-audit/LEGACY-SOURCE-SHA256.json`.

Legacy проверял `/cache/recovery/last_install`, `/tmp/recovery.log`, `/cache/recovery/last_log`, `/tmp/install.log`. Он принимал и промежуточные `Finished installing OrangeFox!` / `script succeeded` как успех, но не доказывал принадлежность старого текста текущей попытке. Текущий NekoFlash требует `boot_id`, неизменный SHA-256 префикса `/tmp/recovery.log`, UUID попытки, USB serial; это защищает от ложного PASS, но не распознаёт переход в **новое SIDELOAD Recovery**.

## Точечное исправление (ветка `fix/sideload-recovery-transition-20261109`)

- `SideloadRecoveryTransitionPolicy.kt`: сопоставление исходного и нового баннера, USB VID:PID, codename; принимает новое `RECOVERY` **или** `SIDELOAD`.
- Условия: текущая попытка Sideload уже достигла состояния передачи, исходный/новый **один USB serial**, codename совпадает, изменились **и** `ro.product.name`, **и** USB VID. Одного переподключения или смены PID недостаточно.
- Новые поля маркера попытки сохраняются в SharedPreferences. Смена окружения логируется **один раз** и отображается только в собственной ожидающей GUI-операции; чужую задачу не перезаписывает.
- Вывод: `Recovery changed after Sideload of …; ZIP installer result is still unverified`. Итог остаётся `VERIFY_PENDING`, пока не получен достоверный результат установщика. Legacy-поведение ложного `SUCCESS` по `script succeeded` **не возвращаем**.
- Не трогаем Native USBFS, поток блоков, `DONEDONE`, Fastboot, оформление Operation Center и хранение исходных логов.

## Приёмка

- Детерминированные JVM-тесты: переход `18D1:D001 lineage_vayu → 2717:D001 vayu` PASS по политике; новое Recovery в SIDELOAD и RECOVERY учитывается; отсутствие serial, смена codename, неполная передача и одиночная смена VID/имени **не** признаются переходом.
- CI: **ожидает проверки точного HEAD**. Нельзя объявлять готовой APK до зелёного CI.
- Физическая проверка на TARGET при очередном безопасном реальном Sideload нужна отдельно; не повторять уже успешно подтверждённую установку OrangeFox только ради нового процента.
- Если приложение не смогло получить USB serial, оставить честный `VERIFY_PENDING`, не ослаблять проверку личности устройства.
- История и детальные доказательства: [issue #66](https://github.com/Ncorror/NekoFlash-2.0/issues/66). Другие ошибки `fastboot -w` и `format userdata`: #64 и #65.

**Текущее состояние:** код и тесты в отдельной ветке; `main` не изменён; аппаратная приёмка новой APK не заявлена.

# Защита NekoFlash 2.0: GitHub Rulesets, подписанный APK, секреты

Актуально на 30.10.2026. Репозиторий: `Ncorror/NekoFlash-2.0`.
До настройки зафиксировано: **main и DEV не защищены**, rulesets пусты,
у `IQ-HARRY7` уровень `write`. Это оценка **прав доступа**, а не
обвинение участника в недобросовестности.

## Что делает этот PR — и чего не делает

- Релиз по `v*` **до чтения секрета подписи** отвергает тег, не
  указывающий на коммит из истории `main`.
- `release.yml` использует GitHub Actions environment `nekoflash-release`.
- `build.yml` запускает подписанный APK только при **явном** выборе
  `sign_release=true`, на ветке `main`, через то же защищённое
  окружение; обычные PR, push, DEV и ручные debug-проверки используют
  `nekoflash-ci` без подписывающих секретов.
- `.github/CODEOWNERS` запрашивает внимание владельца к изменению
  workflow, Gradle, Gradle wrapper, C/C++, scripts и filesystem-tools.

**Важно:** само наличие `environment:` в YAML не включает модерацию.
Если `nekoflash-release` не создано и не защищено в Settings, GitHub может
автоматически создать пустое окружение без защиты. Пока ключи лежат в
**repository secrets**, workflow с правами записи может их использовать.
Ни одна правка YAML не заменит перенос секретов и GitHub Rulesets.

## Ручные действия владельца в GitHub — выполнять в указанном порядке

### 1. Временно исключить недоверенный прямой push

Перейти в [Settings → Collaborators](https://github.com/Ncorror/NekoFlash-2.0/settings/access).
Перепроверить список `write`/администраторов и токенов с доступом.
При одиночном сопровождении безопасный вариант: отозвать прямое `write`
у внешних участников; они по-прежнему могут присылать Pull Request
**из fork**. Это не обвинение и не удаление авторства или старых PR.
Если `write` оставлен, обязательно требовать PR с одобрением от
**другого доверенного человека**, иначе человек с `write` может
объединить собственный PR без независимой проверки при 0 approvals.

### 2. Создать ruleset для main

[Settings → Rules → Rulesets](https://github.com/Ncorror/NekoFlash-2.0/settings/rules).
`New ruleset → New branch ruleset`, имя `protect-main`,
`Enforcement: Active`, целевая ветка **`main`** (точное совпадение).

- Require a pull request before merging.
- Require status checks to pass: **`build`** (точное имя из нового CI).
- Block force pushes.
- Restrict deletions.
- Require conversation resolution — при наличии этой опции.
- Не добавлять широкие `write`/`maintain` bypass-исключения.
- **Одному сопровождающему:** пока нет второго доверенного ревьюера,
  не включать `Require approvals` и `Require review from Code Owners`
  вместе с `Do not allow bypass`: автор не может одобрить свой PR,
  можно заблокировать собственный рабочий процесс. Вместо этого сначала
  убрать чужое `write` (шаг 1). С двумя доверенными ревьюерами —
  включить 1 approval и, при необходимости, CODEOWNERS.

### 3. Создать ruleset для актуальной DEV-ветки

Новый branch ruleset `protect-development` targeting **точно**
`work/rev26-terminal-imports-20261030`. Не защищать `work/rev*`
целиком: это сломает создание и тестирование новых feature-веток.

Те же PR, запрет force-push и удаления. Required checks выбрать из
успешного CI целевой DEV-ветки:

- `build`
- `Installed mkfs on Android API 26`
- `Installed mkfs on Android API 34`

Проверять реальные **check names** в UI перед сохранением правил.
Если required status GitHub пока не предлагает, сначала запустить PR-CI,
затем добавить его; не придумывать/не прописывать несуществующий статус.

### 4. Защитить теги выпуска

Создать `New tag ruleset`, `Enforcement: Active`, targeting `v*`.
Включить `Restrict creations`, `Restrict updates`,
`Restrict deletions`. Исключение/bypass для создания тегов
предоставить только **доверенному владельцу/Repository admins**,
**НЕ** всем `write`. Защита тега должна позволять владельцу
сознательно создать версионный тег, но запрещать его переписывание
остальным. Проверить доступные исключения в UI.

### 5. Создать подписывающее окружение и перенести секреты

[Settings → Environments](https://github.com/Ncorror/NekoFlash-2.0/settings/environments)
→ New environment: **`nekoflash-release`**.

- Required reviewers: **только владелец `Ncorror`** (при одном
  владельце опцию Prevent self-review выключить, иначе владелец
  не сможет одобрить собственный запуск). С доверенным вторым
  рецензентом рекомендуем Prevent self-review включить.
- Deployment branches and tags: **Selected branches and tags**,
  разрешить `main` и `v*`, исключить DEV и feature branches.
- По возможности запретить администраторский обход правил.
- Добавить именно **environment secrets** с текущими названиями:
  `NEKOFLASH_KEYSTORE_BASE64`,
  `NEKOFLASH_STORE_PASSWORD`, `NEKOFLASH_KEY_ALIAS`,
  `NEKOFLASH_KEY_PASSWORD`.
- После проверки исходных резервных копий и переноса
  **удалить одноимённые repository secrets** в
  [Settings → Secrets and variables → Actions](https://github.com/Ncorror/NekoFlash-2.0/settings/secrets/actions).
  Секреты GitHub нельзя посмотреть повторно после создания.
  **Не удалять последний экземпляр ключа без отдельной резервной
  копии.** Если исходные значения недоступны, приоритет —
  изоляция write-доступа; подписанный релиз до безопасной миграции
  лучше временно не выполнять.
- `nekoflash-ci` — только для публичных тестов. **НЕ добавлять в него
  ключи подписи** или другие приватные данные.

### 6. Включить code changes и проверить

После административных настроек объединить hardening PR
отдельно в `main` и DEV при зелёном CI.
Проверить, что:
1. прямой push в защищённые main/DEV без PR отклоняется;
2. Pull Request проходит нужный набор checks;
3. `workflow_dispatch` с `sign_release=false` не получает key secrets;
4. `workflow_dispatch` с `sign_release=true` вне `main`
   не подписывает APK;
5. подписанный выпуск из `main` / `v*` требует approved environment;
6. тег на коммите вне `main` отвергается до чтения signing secrets;
7. GitHub audit trail и старые PR остаются доступными.

**Не создавать тестовый `v*` тег** в рабочем репозитории ради проверки:
он запускает настоящий publish workflow. Для негативных проверок использовать
отдельный тестовый репозиторий без реального ключа.

## Аварийный порядок

Если есть признаки компрометации: остановить выдачу новых APK,
заблокировать лишний `write`, отозвать ненужные PAT и GitHub Apps,
проверить workflow/run logs и releases/tags, отделить релизный ключ,
проверить целостность исходников по доверенным SHA и подписи APK.
Не менять сертификат подписи без плана обновления Android-пакета.
До расследования не удалять диагностические данные, историю Git
или существующие тестовые логи.

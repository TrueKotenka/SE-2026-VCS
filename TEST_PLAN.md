# План тестирования SE-2026-VCS

## 1. Цель

Обеспечить стабильную и предсказуемую работу CLI-системы контроля версий:

- корректность основных VCS-операций;
- безопасность изменений файлов в рабочей директории;
- корректную обработку ошибок и конфликтов;
- блокировку merge в GitHub при падении проверок.

## 2. Область тестирования

Покрываем:

- CLI и парсинг команд;
- core-логику (`RepositoryManager`, `IndexManager`, `ReferenceManager`, `VcsService`, `MergeResolver`, `ObjectParser`);
- model/storage-слой (`Blob`, `Tree`, `Commit`, `ObjectStorage`, `HashUtils`).

Не покрываем (пока):

- производительность на больших репозиториях;
- кросс-платформенные edge-cases файловой системы (добавим отдельно).

## 3. Уровни тестов

### 3.1 Unit tests (основа)

Проверяют отдельные классы и методы изолированно.

Уже реализовано:

- `CommandParserTest`;
- `CliCommandsTest`;
- `MergeResolverTest`;
- `ObjectParserTest`;
- `ObjectStorageAndHashUtilsTest`.

### 3.2 Integration tests

Проверяют взаимодействие core + storage + fs на временной директории:

- `init -> add -> commit -> log`;
- `checkout` между ревизиями/ветками;
- `branch create/delete`;
- `merge` fast-forward;
- `merge` с конфликтом (проверка conflict markers и `MERGE_HEAD`).

### 3.3 CLI e2e smoke tests

Запуск через `Main`/CLI аргументы и проверка пользовательского результата:

- корректные коды завершения;
- сообщения об ошибках/usage;
- базовые happy-path цепочки команд.

## 4. Матрица функциональности

### 4.1 Репозиторий и объекты

- `init`: создание `.myvcs`, `objects`, `refs/heads`, `HEAD`;
- сохранение/загрузка объектов (`blob/tree/commit`);
- корректность сериализации/десериализации;
- детерминированность SHA-256.

### 4.2 Индекс и коммиты

- `add` файла и директории;
- `commit` с message/author/date;
- очистка индекса после commit;
- история `log` по first-parent.

### 4.3 Ветки, checkout, merge

- создание/удаление ветки;
- `checkout` по ветке и по hash (detached HEAD);
- merge:
  - nearest common ancestor;
  - fast-forward;
  - 3-way merge;
  - конфликт и ручное завершение через `add + commit`.

### 4.4 Status и диагностика

- staged/unstaged/untracked;
- отображение незавершенного merge;
- корректные сообщения при ошибках.

## 5. Негативные и edge-кейсы

- неизвестная команда;
- пустые аргументы (`add`, `checkout`, `merge`);
- commit при пустом индексе;
- checkout несуществующей ревизии;
- удаление активной ветки;
- merge несуществующей ветки;
- независимые истории без общего предка;
- повторное `init` в уже инициализированной папке.

## 6. Критерии качества и покрытия

- unit-тесты для новых core-методов обязательны;
- новые CLI-команды без unit-тестов не принимаются;
- целевой порог покрытия (ориентир): 70%+ по core/storage, 60%+ по CLI;
- каждый багфикс сопровождается регрессионным тестом.

## 7. CI и правила merge

В GitHub Actions обязательные шаги:

- `spotlessCheck`;
- `build`;
- `test`.

Требование для ветки `main`:

- включить branch protection;
- включить `Require status checks to pass before merging`;
- выбрать job из workflow `CI` как required.

Итог: если тесты/сборка не проходят, merge блокируется.


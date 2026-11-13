# Learning notes — 03-containers-ci

## Каркас

- Сервис облегчён намеренно: API 02 (ETag, идемпотентность, merge patch) не нужен, чтобы учиться упаковке.
  Категории только читаются — их заводит миграция `V3__seed_categories.sql` с фиксированными UUID, на которые
  удобно ссылаться в тестах и `requests.http`.
- Конфигурация — только env vars (`PORT`, `SPRING_DATASOURCE_*`): один и тот же образ запускается в Compose, CI и K8s.

## Домен

- Код домена скопирован из 02 (`Money`, UUIDv7 в `Ids`, `Texts`) — подпроекты не делят библиотек, переиспользование
  копированием. Убрано всё, что нужно только для изменения ресурсов: `version`, `replace`, патчи.
- Проверка категории в сервисе даёт понятную `422`; внешний ключ в БД остаётся последней защитой от гонки.

## Persistence

- `Limit` из Spring Data вместо `Pageable`: потолок 100 без `count`-запроса и без пагинации в API.
- Из ошибок БД в доменную переводится только нарушение `item_category_fk` — по имени ограничения (как в 02).

## REST

- Ошибки — Problem Details из 02, урезанные до четырёх типов. Последний обработчик (`Exception`) отдаёт
  `internal-error` без текста исключения: в нём бывают SQL, имена хостов и URL базы (в контейнере это
  `jdbc:postgresql://postgres:5432/...` — ещё и подсказка о топологии).
- Некорректный UUID в пути или параметре — `TypeMismatchException` Spring MVC → `400 invalid-request`, а не `500`.

## Что видит JVM

`/actuator/runtime` показывает `availableProcessors`, `maxHeapBytes` (`Runtime.maxMemory()`),
`containerMemoryLimitBytes` (`/sys/fs/cgroup/memory.max`, cgroup v2), имена GC и аргументы JVM. Это та же
информация, которую JVM использует для эргономики: размера heap, числа потоков GC и ForkJoinPool, выбора GC.
Таблица опытов — в разделе «JVM в контейнере» ниже.

## Jib

- Образ собирается из `target/classes` и зависимостей, без Docker-демона (`jib:build` пушит прямо в реестр) и без
  fat jar. Слои: dependencies → snapshot-dependencies → resources → classes; запуск
  `java -cp /app/resources:/app/classes:/app/libs/* …CatalogApplication` — без Spring Boot loader.
- Воспроизводимость: время создания образа и файлов — epoch (1970), поэтому одинаковый код даёт одинаковый digest.
  Обратная сторона — `docker images` показывает «55 years ago».
- `ENTRYPOINT` Jib — exec-форма, `java` = PID 1 и сам получает SIGTERM. С shell-формой (`sh -c "java …"`) сигнал
  достаётся shell, Java его не видит и через таймаут получает SIGKILL.

## Образные тесты

- Профиль `image-it`: один набор проверок на любой образ (`-Dcatalog.image`, `-Dcatalog.flavor`). Обычный `verify`
  образов не трогает.
- `GenericContainer.stop()` Testcontainers шлёт SIGKILL — graceful shutdown им не проверить. Тест вызывает
  `docker stop` (SIGTERM, ждёт 30 с) и смотрит код выхода: 143 = 128 + 15 (SIGTERM) — норма для JVM.
- Без базы контейнер должен **упасть** с ненулевым кодом, а не висеть: оркестратор перезапустит его, а «живой»
  процесс без БД только мешает.

## JVM в контейнере

| Опыт | Ожидание (Jib, Dockerfile, jlink) | Проверка |
|---|---|---|
| `--cpus=1` | `availableProcessors = 1` | `ContainerLimitsImageIT.oneCpu` |
| `--cpus=0.5` | всё равно `1` (квота округляется вверх) | `halfCpuRoundsUp` |
| `JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=3` при `--cpus=1` | `3` — флаг сильнее cgroup | `activeProcessorCountOverrides` |
| `--memory=768m` | лимит `805306368`, heap ≈ 75 % лимита | `oneCpu` |
| `--cpus=1` | SerialGC (`Copy`, `MarkSweepCompact`) | `oneCpu` |
| `--cpus=2 --memory=2g` | G1 (`G1 Young Generation`) | `twoCpusTwoGigabytesSelectG1` |
| без лимита | heap = 25 % RAM хоста (`MaxRAMPercentage` по умолчанию) | только заметки |
| нехватка памяти | см. ниже | только заметки |

- **Server-class machine:** JVM считает машину «серверной» при ≥ 2 CPU **и** ≥ ~1792 МБ; иначе — SerialGC.
  Под с `cpu: 1` неожиданно получает SerialGC — для latency-сервисов задают GC явно.
- **25 % по умолчанию:** без `MaxRAMPercentage` под с лимитом 768 МБ получит heap ~192 МБ и будет
  простаивать память — или, без лимита, heap от RAM всей ноды и OOMKill.
- **OOMKill против OutOfMemoryError:** heap — не вся память процесса (metaspace, стеки потоков, code cache,
  direct buffers, GC). Если сумма превысит cgroup-лимит, ядро убивает процесс **SIGKILL** — код 137, без
  stack trace, без shutdown hooks. Если же кончится heap — `OutOfMemoryError`; с `-XX:+ExitOnOutOfMemoryError`
  JVM сразу выходит (код 3), и оркестратор перезапускает под, вместо зомби-процесса, который «жив», но не работает.

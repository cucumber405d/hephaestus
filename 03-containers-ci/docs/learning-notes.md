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

## Buildpacks

- `spring-boot:build-image` = Paketo builder (`builder-noble-java-tiny`): сам определяет JVM (`BP_JVM_VERSION=25`),
  слои по `layers.idx`, run-образ «tiny» без shell и пакетного менеджера, пользователь `cnb` (не root), метки через
  `BP_OCI_*`/`BP_IMAGE_LABELS`. Dockerfile не нужен, но нужен Docker-демон.
- **Memory calculator** при старте контейнера считает флаги из лимита: `-Xss1M` × 250 потоков, code cache 240 МБ,
  metaspace по числу классов, `-Xmx` — остаток; плюс `-XX:ActiveProcessorCount`. Поэтому heap Buildpacks-образа
  при том же лимите — не 75 %, а «что осталось»; при лимите 512 МБ дефолты могут вообще не уложиться.
  Фактические цифры — из лога `ContainerLimitsImageIT.oneCpu` с `-Dcatalog.flavor=buildpacks`.
  Тюнинг: `BPL_JVM_THREAD_COUNT`, `BPL_JVM_HEAD_ROOM`, `JAVA_TOOL_OPTIONS` при запуске.

## Dockerfile

- Двухстадийный: стадия `extract` раскладывает jar на слои (`java -Djarmode=tools -jar app.jar extract --layers
  --launcher`), финальная копирует их по одному — от редко меняющихся к часто меняющимся. Изменение кода
  пересобирает только слой `application`.
- Maven внутри Docker не запускается: образ собирается из готового jar. Так не нужны ни корневой POM в контексте,
  ни доступ к Maven Central из Docker, а `.dockerignore` оставляет в контексте один файл.
- `ENTRYPOINT ["java", …]` — exec-форма. `USER 1000:1000` — не root.
- `HEALTHCHECK` не задан ни в одном образе: Kubernetes его игнорирует (там liveness/readiness probes на Actuator),
  а Compose проверяет готовность зависимостей своим `healthcheck`.

## jlink

- `jdeps --recursive --print-module-deps` по классам приложения (библиотеки — только на class path: модульные jar
  в роли корней заставляют jdeps разрешать их `module-info` и падать на опциональных `requires`) даёт список модулей JDK; `jlink`
  собирает из них свой JRE (`--strip-debug --no-header-files --no-man-pages --compress zip-6`) поверх
  `debian:stable-slim`.
- jdeps видит только статические ссылки. Spring, Hibernate и драйвер PostgreSQL грузят часть JDK рефлексией —
  `java.naming` (JNDI), `java.management`/`jdk.management` (JMX, `/actuator/runtime`), `jdk.unsupported`
  (`sun.misc.Unsafe`), `java.instrument`, `java.security.jgss` (GSS в драйвере), `jdk.zipfs` — они добавлены руками.
  `jdk.crypto.ec` не нужен: с JDK 22 EC в `java.base`.
- Цена: свой JRE надо обновлять самому (CVE в JDK → пересборка), а забытый модуль проявится только в рантайме —
  поэтому тот же `CatalogImageIT` гоняется и против этого образа.

## Сравнение образов

`scripts/compare-images.sh` (после одного прогона `build-images.sh`, чтобы базовые образы и зависимости были в
кэше). Лимиты запуска — 768 МБ / 1 CPU, как в тестах.

<!-- Results: paste the table printed by compare-images.sh here, with the date and the machine. -->

| image | size MB | layers | build s | rebuild s | rebuilt layers | ready s | started in s | memory |
|---|---|---|---|---|---|---|---|---|
| _ещё не измерено_ | | | | | | | | |

Что смотреть в цифрах:
- **Размер:** jlink-образ — свой урезанный JRE; tiny-образ Paketo — без shell; temurin-JRE — Ubuntu целиком.
- **rebuilt layers:** после изменения одного класса Jib и Dockerfile меняют один слой (`classes` / `application`);
  push в реестр отправит только его.
- **Воспроизводимость:** два `jib:dockerBuild` подряд без изменений дают тот же image id (время = epoch);
  у Buildpacks тоже (время 1980-01-01); `docker build` даёт новый id — в слоях настоящие mtime и время создания
  (лечится `SOURCE_DATE_EPOCH` + BuildKit `rewrite-timestamp`).
- **memory** из `docker stats` — использование памяти cgroup (включая часть page cache), а не RSS процесса.

## CI и GHCR

- Порядок задач: `verify` → матрица `images` (4 способа, `fail-fast: false` — видно все падения сразу) →
  `publish`. Публикация `needs: images`: в реестр попадает только версия, у которой образные тесты прошли для
  **всех** способов, и только при push в `main` (PR из форка не получает `packages: write`).
- `GITHUB_TOKEN` с `packages: write` — только у job публикации; по умолчанию у workflow `contents: read`.
  Длинноживущий PAT не нужен. Входы reusable-workflow передаются в `run` через `env`, а не подстановкой
  `${{ }}` прямо в shell — так их значение не может стать кодом.
- **Тег vs digest:** `sha-<7>` неизменяем только **по соглашению** — GHCR не запрещает перезапись, перезапуск job
  перезапишет тег тем же содержимым. По-настоящему неизменяем digest (`image@sha256:…`); его пишет summary.
  `latest` удобен людям и опасен деплою — в 04+ манифесты ссылаются на `sha-…` или digest. Подпись и проверка
  digest — тема 21.
- Jib пушит в реестр без Docker-демона и без `docker build`; авторизацию берёт из `~/.docker/config.json`,
  который пишет `docker/login-action`.
- Docker Desktop на Windows: Testcontainers и `docker build` работают через WSL2-бэкенд; bash-скрипты — из
  Git Bash или WSL. Лимиты `--memory`/`--cpus` применяются внутри VM WSL2, а не к Windows-хосту.

## Вопросы для собеседования

1. **Чем Jib отличается от Buildpacks и Dockerfile?** Jib строит образ из классов без демона и Dockerfile,
   воспроизводимо; Buildpacks — без Dockerfile, но с демоном, сам выбирает JVM и считает память; Dockerfile — полный
   контроль ценой поддержки файла и ручной заботы о слоях, пользователе и сигналах.
2. **Зачем слои и в каком порядке?** Кэш: слой пересобирается и пушится, только если изменился он или слой под ним.
   Порядок — от редко меняющегося (JRE, зависимости) к часто меняющемуся (код).
3. **Почему `ENTRYPOINT` в exec-форме?** Иначе PID 1 — shell, SIGTERM не доходит до Java, graceful shutdown не
   случается, через таймаут приходит SIGKILL.
4. **Сколько heap возьмёт JVM в контейнере с лимитом 1 ГБ?** По умолчанию 25 % (`MaxRAMPercentage`), т. е. ~256 МБ;
   обычно задают 70–80 %, оставляя место под metaspace, стеки, code cache, direct memory.
5. **Exit code 137 vs `OutOfMemoryError`?** 137 = SIGKILL от OOM killer ядра: процесс превысил cgroup-лимит
   целиком, JVM ничего не успела. OOME — кончился heap внутри лимита; `ExitOnOutOfMemoryError` превращает его в
   быстрый выход и перезапуск.
6. **Почему под с 1 CPU работает на SerialGC?** Эргономика: не server-class (нужно ≥ 2 CPU и ≥ ~1792 МБ). Если важна
   latency — задать GC явно или дать 2 CPU.
7. **Тег или digest в деплое?** Digest: тег (даже `sha-…`) можно перезаписать; `latest` — никогда в манифестах.
8. **Как не запускать контейнер от root и зачем?** `USER 1000:1000` / `container.user` в Jib / Paketo делает сам;
   выход из контейнера с root — root на ноде при уязвимости рантайма; в K8s — `runAsNonRoot`.
9. **Зачем `HEALTHCHECK`, если есть Kubernetes?** Незачем: K8s его игнорирует и использует probes; в Compose готовность
   зависимостей задаёт `healthcheck` сервиса.
10. **Что даёт jlink и чем платим?** Образ меньше и с меньшей поверхностью атаки; платим ручным списком модулей
    (рефлексия не видна jdeps) и самостоятельным обновлением JRE.

# 01-modern-java — спецификация

- **Статус:** ждёт ревью
- **Дата:** 2026-11-06
- **Корневая спека:** [../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md](../../../../docs/superpowers/specs/2026-11-05-hephaestus-root-design.md)

## 1. Цель

Системно освоить язык и API Java от 9 до 27 на примерах домена «Мастерская Гефеста».
Каждая фича — работающий пример + тест-доказательство + заметка уровня собеседования.

**Критерии успеха**
- Каждая фича из раздела 3 реализована классом-примером и покрыта тестом.
- Утверждения из раздела 4 доказаны тестами, а не комментариями.
- `docs/learning-notes.md` содержит раздел на каждую версию с вопросами и ответами для собеседования.
- `./mvnw -f 01-modern-java/pom.xml verify` зелёный локально и в CI на JDK 27.

**Не цели:** JPMS (только заметка), runtime-фичи без API (GC, CDS, compact object headers → `18-fast-startup`),
Vector API (incubator), Spring и любые фреймворки.

## 2. Структура

```
01-modern-java/
├── pom.xml                       ← reactor, artifactId modern-java, parent ../pom.xml
├── features/                     ← artifactId modern-java-features; --release 25
│   └── src/{main,test}/java/de/ibikus/appa/hephaestus/modernjava/java09 … java25
├── next/                         ← artifactId modern-java-next; --release 27 --enable-preview
│   └── src/{main,test}/java/de/ibikus/appa/hephaestus/modernjava/java26, java27
├── scripts/Inventory.java        ← compact source file (Java 25): java scripts/Inventory.java
├── README.md
└── docs/ start.md, architecture.md, learning-notes.md, superpowers/
```

**Правила**
- Пакет на релиз: `de.ibikus.appa.hephaestus.modernjava.javaNN` (двузначный номер).
- Фича кладётся в версию, где стала **финальной**; история превью — в javadoc и learning-notes.
- Версии без финальных фич языка/API пакета не получают: 13, 19, 20 (только превью/runtime).
- Один пример = один класс (или маленький набор типов) + парный тест `<Class>Test`.
- Без фреймворков: JUnit 6, AssertJ; Mockito не используется.
- ArchUnit не применяется (нет слоёв) — осознанное исключение из корневой спеки §5.1.

## 3. Каталог фич

### Модуль `features` (Java 25)

| Пакет | Фичи | Пример из домена |
|---|---|---|
| `java09` | `List/Set/Map.of`; private-методы интерфейсов; `Optional.ifPresentOrElse/or/stream`; `Stream.takeWhile/dropWhile/iterate(seed,hasNext,next)`; `CompletableFuture.orTimeout/completeOnTimeout` | Справочник материалов; таймаут ответа поставщика |
| `java10` | `var`; `List/Set/Map.copyOf`; `Collectors.toUnmodifiable*`; `Optional.orElseThrow()` | Неизменяемый снимок заказа |
| `java11` | `String.isBlank/strip/lines/repeat`; `Files.readString/writeString`; `Predicate.not`; `var` в параметрах лямбд; `java.net.http.HttpClient` | Парсинг прайс-листа; запрос к локальному stub-серверу |
| `java12` | `Collectors.teeing`; `String.indent/transform` | Сумма и средняя цена за один проход |
| `java14` | `switch`-выражения (`->`, `yield`); helpful NPE | Статус заказа → следующее действие |
| `java15` | Text blocks | JSON/SQL-шаблон заказа |
| `java16` | Records (compact constructor, статические фабрики); pattern matching `instanceof`; `Stream.toList`; `Stream.mapMulti` | `Money`, `OrderLine` |
| `java17` | Sealed-иерархии; `RandomGenerator`; `HexFormat` | `sealed interface PaymentResult` |
| `java18` | UTF-8 по умолчанию (`Charset.defaultCharset()`); `@snippet` в javadoc | Демонстрация кодировки, документация |
| `java21` | Virtual threads (`Thread.ofVirtual`, `newVirtualThreadPerTaskExecutor`); record patterns; pattern matching в `switch` (guards `when`); `SequencedCollection/Map`; `StringBuilder.repeat`; `Math.clamp` | Массовые запросы к поставщикам; исчерпывающий разбор `PaymentResult` |
| `java22` | Unnamed variables и patterns `_`; FFM API (`Linker`, `Arena`, `MemorySegment`) | `strlen` из C runtime; off-heap буфер |
| `java23` | Markdown doc comments (`///`) | Javadoc примеров |
| `java24` | Stream Gatherers (`windowSliding`, `windowFixed`, `fold`, `scan`, `mapConcurrent`, свой gatherer); ClassFile API; virtual threads без pinning на `synchronized` (JEP 491) | Скользящее окно цен; `distinctBy`; чтение имён методов из `.class` |
| `java25` | Scoped Values (JEP 506); flexible constructor bodies (JEP 513); module import declarations (JEP 511); compact source files и instance `main` (JEP 512) | Контекст запроса `RequestContext(tenant, traceId)`; валидация до `super(...)` |

### Модуль `next` (Java 27, `--enable-preview`)

| Пакет | Фичи | Статус в 27 | Пример |
|---|---|---|---|
| `java26` | HTTP/3 в `HttpClient` (JEP 517) | финальная | Запрос с `Version.HTTP_3` |
| `java27` | Structured Concurrency (JEP 533) | 7-е превью | Параллельно «цена + наличие + срок» с отменой при ошибке |
| `java27` | Primitive types in patterns (JEP 532) | 5-е превью | `switch` по `int`-коду ответа |
| `java27` | Lazy Constants (JEP 531) | 3-е превью | Ленивая инициализация тарифов |
| `java27` | PEM encodings (JEP 538) | превью | Чтение/запись ключа в PEM |

## 4. Доказательства

| Утверждение | Тест |
|---|---|
| Virtual threads масштабируются на блокирующих задачах | 10 000 задач `sleep(100 ms)` на `newVirtualThreadPerTaskExecutor` завершаются < 2 s. Расчёт для пула из 100 platform-потоков (≈ 10 s) — в learning-notes, не в тесте. |
| `synchronized` больше не пинит carrier (JEP 491) | JFR `RecordingStream` на `jdk.VirtualThreadPinned`: блокировка в `synchronized` → 0 событий; блокировка внутри upcall/нативного кадра FFM → ≥ 1 событие. |
| Scoped Values не утекают | Значение видно в глубине вызовов, отсутствует после `run`, вложенная перепривязка работает. Контрпример: `ThreadLocal` утекает в следующую задачу пула из 1 потока. |
| Structured Concurrency отменяет соседей | 3 подзадачи, одна бросает исключение; две другие прерваны (счётчик завершений = 0), scope бросает исключение с причиной. |
| Sealed + `switch` исчерпывающий | `switch` по `PaymentResult` без `default` компилируется; документировано, что новый подтип ломает компиляцию. |
| Gatherers | Ожидаемые окна/свёртки на фиксированных данных; `mapConcurrent` сохраняет порядок. |
| `HttpClient` | Запрос к `com.sun.net.httpserver.HttpServer`, поднятому в тесте на случайном порту. |
| HTTP/3 | `@Tag("network")`, по умолчанию исключён (surefire `excludedGroups=network`); ручной запуск: `-Dgroups=network`. |
| FFM | `strlen("Hephaestus") == 10` на Windows и Linux; запись/чтение `MemorySegment` в `Arena.ofConfined()`. |

Тайминг-тесты используют щедрые пороги (×5 от ожидаемого) против флаки в CI.

## 5. Сборка и CI

- Подпроект собирается на **JDK 27**; enforcer корня (`[25,)`) не мешает.
- `features`: `maven.compiler.release=25`. `next`: `maven.compiler.release=27`, `<compilerArgs>--enable-preview</compilerArgs>`, surefire `argLine` дополняется `--enable-preview`.
- Корневой `build-subproject.yml` получает вход `java-version` (string, по умолчанию `"25"`); `01-modern-java.yml` передаёт `"27"`.
- Локально: Temurin 27 рядом с 25; в `docs/start.md` подпроекта — переключение `JAVA_HOME`.

## 6. Документация

- `docs/learning-notes.md`: раздел на версию — что появилось, история превью, подводные камни, 2–3 вопроса для собеседования с ответами; отдельный раздел «JPMS вкратце».
- `docs/architecture.md`: таблица «версия → пакет → классы → JEP».
- Impact-файл на каждую задачу.

## 7. Риски

| Риск | Мера |
|---|---|
| Превью-API в 27 изменится в 28 | Модуль `next` изолирован; при переходе на новую JDK — обновить или удалить |
| Флаки тайминг-тестов в CI | Щедрые пороги; при повторных сбоях — `@Tag("timing")` и исключение из CI |
| FFM-символ различается на Windows/Linux | `defaultLookup()`; тест на обеих ОС (локально Windows, CI Linux) |
| JFR-событие pinning зависит от реализации | Проверяем по наличию/отсутствию события, а не по количеству; документируем версию JDK |

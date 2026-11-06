# Learning notes — 01-modern-java

## Java 9

**Что появилось.** Фабрики `List/Set/Map.of` (JEP 269); private-методы в интерфейсах (JEP 213);
`Optional.or/ifPresentOrElse/stream`; `Stream.takeWhile/dropWhile` и `iterate` с условием;
`CompletableFuture.copy/orTimeout/completeOnTimeout`; JPMS (JEP 261).

**Подводные камни.**
- `List.of/Map.of` — неизменяемые и **запрещают `null`** (и в `contains`!): `List.of(1).contains(null)` → NPE.
- Порядок итерации `Set.of/Map.of` не определён и меняется между запусками — не полагаться в тестах.
- `completeOnTimeout` меняет **сам** future; если он чужой — сначала `copy()`.
- `takeWhile` на неупорядоченном потоке недетерминирован.

**JPMS вкратце.** `module-info.java` с `requires/exports/opens`; сильная инкапсуляция JDK (с 16/17 —
`--add-opens` для рефлексии). На практике в Spring-приложениях почти не используется: classpath остаётся
нормой; важно понимать `--add-opens` и почему старые библиотеки ломаются на новых JDK.

**Вопросы для собеседования.**
1. *Чем `List.of` отличается от `Collections.unmodifiableList` и `Arrays.asList`?* — `List.of` — настоящая
   неизменяемая копия без `null`; `unmodifiableList` — представление поверх изменяемого списка (изменения
   источника видны); `Arrays.asList` — фиксированный размер, но `set` разрешён, `null` допустим.
2. *Зачем private-методы в интерфейсах?* — Общая логика для нескольких default-методов без расширения API.
3. *`orTimeout` vs `completeOnTimeout`?* — Первый завершает future `TimeoutException`, второй —
   значением по умолчанию.

## Java 10

**Что появилось.** `var` для локальных переменных (JEP 286); `List/Set/Map.copyOf`;
`Collectors.toUnmodifiableList/Set/Map`; `Optional.orElseThrow()` без аргументов.

**Подводные камни.**
- `var` выводит **конкретный** тип: `var list = new ArrayList<String>()` — это `ArrayList`, не `List`.
- `var x = null;` и `var` без инициализатора не компилируются; `var` не для полей и параметров методов.
- `List.copyOf` не копирует, если источник уже неизменяемый `List.of` — возвращает его же.

**Вопросы для собеседования.**
1. *Делает ли `var` Java динамически типизированной?* — Нет, тип выводится при компиляции и фиксирован.
2. *`List.copyOf` vs `new ArrayList<>(list)`?* — Первый — неизменяемый снимок без `null`, второй —
   изменяемая копия.

## Java 11

**Что появилось.** Стандартный `java.net.http.HttpClient` (JEP 321: HTTP/2, async, WebSocket);
`String.isBlank/strip/lines/repeat`; `Files.readString/writeString`; `Predicate.not`; `var` в параметрах
лямбд (JEP 323) — чтобы вешать аннотации: `(@NonNull var x) -> …`; запуск single-file программ `java Hello.java`.
Первая LTS после 8 — на ней «застряла» значительная часть enterprise.

**Подводные камни.**
- `strip()` ≠ `trim()`: `strip` понимает Unicode-пробелы (`\u2000` и т. п.), `trim` — только ≤ `\u0020`.
- `HttpClient` — дорогой объект с пулом соединений: создавать один на сервис, а не на запрос.
- `send()` не бросает исключение на 4xx/5xx — статус проверять самому.
- Без `timeout(...)` у запроса ожидание ответа не ограничено (`connectTimeout` — только на соединение).

**Вопросы для собеседования.**
1. *Чем `HttpClient` лучше `HttpURLConnection`?* — Fluent API, HTTP/2, асинхронность через
   `CompletableFuture`, неизменяемые запросы, пул соединений, WebSocket.
2. *`strip` vs `trim`?* — см. выше: Unicode-aware.
3. *Зачем `var` в лямбдах, если типы и так выводятся?* — Ради аннотаций на параметрах.

## Java 12

**Что появилось.** `Collectors.teeing` — два коллектора за один проход + функция слияния;
`String.indent/transform`; `CompactNumberFormat`. Switch expressions — только превью (финал в 14).

**Подводные камни.**
- В `teeing` оба коллектора видят **все** элементы; функция слияния вызывается один раз в конце.
- Среднее из `BigDecimal` требует явного scale и `RoundingMode`, иначе `ArithmeticException` на `1/3`.

**Вопросы для собеседования.**
1. *Как за один проход посчитать сумму и среднее?* — `teeing(summing…, counting(), merger)` или
   `summaryStatistics()` для примитивов.
2. *Почему `BigDecimal.divide` может бросить исключение?* — Бесконечная десятичная дробь без заданного scale.

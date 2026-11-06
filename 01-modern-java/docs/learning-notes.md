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

## Java 14

**Что появилось.** Switch expressions (JEP 361, превью в 12–13): `->` без fall-through, `yield` в блоке,
обязательная исчерпываемость; helpful NullPointerExceptions (JEP 358) — сообщение называет, что именно `null`.

**Подводные камни.**
- Switch-**выражение** по enum без `default` исчерпывающее: новая константа → ошибка компиляции (хорошо),
  но в старом switch-**операторе** — молча пропускается.
- Нельзя смешивать `case X:` и `case X ->` в одном switch.

**Вопросы.**
1. *Чем switch expression отличается от оператора?* — Возвращает значение, нет fall-through, проверка
   исчерпываемости.
2. *Что даёт helpful NPE?* — `Cannot invoke "String.length()" because "order.customer().name()" is null` —
   цепочка видна без отладчика.

## Java 15

**Что появилось.** Text blocks (JEP 378, превью в 13–14); hidden classes (для фреймворков); ZGC/Shenandoah — production.

**Подводные камни.**
- Отступ считается по самой левой строке **включая закрывающие `"""`** — их позиция влияет на результат.
- Хвостовые пробелы срезаются; нужен пробел в конце — `\s`; склеить строку без `\n` — `\` в конце.

**Вопросы.** *Как text block обрабатывает отступы?* — Убирает общий «случайный» отступ; `stripIndent()`
и `translateEscapes()` доступны и для обычных строк.

## Java 16

**Что появилось.** Records (JEP 395); pattern matching для `instanceof` (JEP 394); `Stream.toList()`,
`Stream.mapMulti`; Vector API (incubator); строгая инкапсуляция JDK по умолчанию (JEP 396).

**Подводные камни.**
- Record — **неглубоко** неизменяем: `List`-компонент нужно копировать (`List.copyOf`) в compact constructor.
- `Stream.toList()` — неизменяемый и допускает `null`; `Collectors.toList()` — изменяемый (не гарантировано).
- Record не может наследовать класс и иметь instance-поля вне компонентов; JPA-сущностью быть не может
  (нет no-arg конструктора и сеттеров) — годится для DTO и value objects.
- `BigDecimal` в record: `equals` учитывает scale (`2.0` ≠ `2.00`) — поэтому `Money` нормализует scale.

**Вопросы.**
1. *Record vs Lombok `@Value`?* — Языковая конструкция, канонический конструктор, compact constructor,
   поддержка в pattern matching (record patterns, Java 21); нет builder'ов и наследования.
2. *Где область видимости переменной в `if (o instanceof Money m && m.x())`?* — Только там, где паттерн
   гарантированно совпал (flow scoping).

## Java 17

**Что появилось.** Sealed classes (JEP 409, превью в 15–16); `RandomGenerator` (JEP 356); `HexFormat`;
LTS. Окончательно убрана нелегальная рефлексия во внутренности JDK без `--add-opens`.

**Подводные камни.**
- Наследники sealed-типа должны быть `final`, `sealed` или `non-sealed`; в одном пакете/модуле с родителем.
- `permits` можно опустить, если наследники в том же файле (как вложенные records в `PaymentResult`).
- Без record/switch-паттернов (Java 21) sealed даёт мало — главная ценность в исчерпывающем switch.

**Вопросы.**
1. *Зачем sealed, если есть enum?* — Варианты несут **разные данные** (`Approved(id, amount)` vs `Pending(retryAfter)`):
   алгебраические типы данных.
2. *`non-sealed`?* — Открывает ветку иерархии для произвольного наследования.

## Java 18

**Что появилось.** UTF-8 по умолчанию на всех ОС (JEP 400) — конец «кракозябр» на Windows (`Cp1252`);
`@snippet` в javadoc (JEP 413) — компилируемые примеры; простой веб-сервер `jwebserver` (JEP 408).

**Подводные камни.** Консольный вывод на Windows по-прежнему зависит от `stdout.encoding`; файлы без явной
кодировки теперь читаются как UTF-8 — старые файлы в Windows-1252 «поплывут».

**Вопросы.** *Что изменилось для `new FileReader(file)` в Java 18?* — Читает в UTF-8 независимо от ОС.

## Java 21

**Что появилось.** Virtual threads (JEP 444, превью в 19–20); record patterns (JEP 440) и pattern matching
для `switch` (JEP 441) с guard'ами `when`; sequenced collections (JEP 431); generational ZGC;
`Math.clamp`, `StringBuilder.repeat`. LTS — сегодняшний «минимум» для новых проектов (Spring Boot 4 требует 17+).

**Virtual threads — как это работает.** Виртуальный поток — объект в куче; JVM монтирует его на один из
немногих *carrier*-потоков (ForkJoinPool, ≈ числу ядер). На блокирующей операции (I/O, `sleep`, `LockSupport.park`)
стек виртуального потока выгружается в кучу, carrier свободен для других. Тест `SupplierFanOutTest`:
10 000 задач по 100 мс ≈ 0,2–0,5 с. **Расчёт для пула из 100 platform-потоков:** 10 000 / 100 × 100 мс = 10 с;
10 000 platform-потоков — ~10 ГБ зарезервированных стеков и упор в лимиты ОС.

**Подводные камни.**
- VT ускоряют **ожидание**, а не вычисления: для CPU-bound задач выигрыша нет.
- **Не пулить** виртуальные потоки: один поток на задачу; ограничивать конкурентность семафором, а не пулом.
- `ThreadLocal` в миллионе потоков — миллион копий: дорогие объекты так не кэшировать (→ Scoped Values, Java 25).
- **Pinning:** до Java 24 блокировка внутри `synchronized` «прибивала» carrier (→ JEP 491, раздел Java 24);
  нативные кадры пинят до сих пор.
- Пул соединений к БД остаётся узким местом: 10 000 VT не дадут 10 000 соединений.
- Guard `when` в switch: порядок `case` важен — более специфичный паттерн выше общего, иначе ошибка
  компиляции «dominated».

**Вопросы для собеседования.**
1. *Virtual vs platform thread?* — Platform = поток ОС (≈1 МБ стека, дорогой). Virtual = управляется JVM,
   килобайты, миллионы штук; монтируется на carrier только пока выполняется.
2. *Когда virtual threads не помогут?* — CPU-bound, pinning, внешние лимиты (пул БД, rate limit).
3. *Чем switch pattern matching с sealed лучше visitor'а?* — Исчерпываемость проверяет компилятор,
   нет двойной диспетчеризации и boilerplate; новый подтип — ошибка компиляции во всех switch.
4. *Как в Spring Boot включить VT?* — `spring.threads.virtual.enabled=true` (см. `_template`).

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

## Java 22

**Что появилось.** Foreign Function & Memory API (JEP 454, превью в 19–21) — вызов нативных функций и работа
с памятью вне кучи без JNI; unnamed variables и patterns `_` (JEP 456); запуск многофайловых программ
`java Main.java` (JEP 458).

**FFM — из чего состоит.** `Linker` (downcall: Java → C, upcall: C → Java), `SymbolLookup` (поиск функций),
`FunctionDescriptor` (сигнатура в терминах `ValueLayout`), `Arena` (время жизни памяти: `ofConfined`,
`ofShared`, `ofAuto`, `global`), `MemorySegment` (безопасный указатель с границами).
Пример `NativeSort`: C `qsort` вызывает Java-компаратор через upcall stub.

**Подводные камни.**
- Restricted-методы (`downcallHandle`, `upcallStub`) с JDK 24 печатают предупреждение без
  `--enable-native-access=ALL-UNNAMED` (настроено в surefire), в будущем — ошибка.
- Исключение, вылетевшее из upcall, **убивает JVM** — в upcall'е всё ловить.
- Память `Arena.ofConfined()` доступна только создавшему потоку; после `close()` — `IllegalStateException`
  вместо segfault.
- Типы C зависят от платформы: `long` в C на Windows — 32 бита, `size_t` — 64; описывать честно.
- `jextract` генерирует биндинги из `.h` — вручную писать дескрипторы для больших библиотек не надо.

**Вопросы.**
1. *Чем FFM лучше JNI?* — Без C-кода и компиляции под каждую ОС, безопасная память с проверкой границ
   и времени жизни, производительность сопоставима, JIT инлайнит downcall'ы.
2. *Что такое `Arena`?* — Область владения памятью: все сегменты освобождаются при закрытии, детерминированно.
3. *Где `_` полезен?* — `catch (Exception _)`, неиспользуемые компоненты в паттернах, `for (var _ : …)`,
   лямбды `(_, v) -> …`; несколько паттернов в одном `case` без биндингов.

## Java 23

**Что появилось.** Markdown в doc-комментариях `///` (JEP 467); ZGC generational по умолчанию.
Остальное — превью (primitive patterns, module imports, structured concurrency…).

**Вопросы.** *Как писать javadoc в Markdown?* — `///` вместо `/** */`, CommonMark + таблицы,
ссылки `[Type#method]`.

## Java 24

**Что появилось.** Stream Gatherers (JEP 485, превью в 22–23); Class-File API (JEP 484);
**virtual threads без pinning на `synchronized`** (JEP 491); пост-квантовые ML-KEM/ML-DSA;
AOT class loading & linking (JEP 483, → `18-fast-startup`).

**Gatherers.** `Gatherer<T, A, R>` = initializer (состояние `A`) + integrator (элемент → 0..n результатов,
`push` возвращает `false`, если downstream больше не хочет) + combiner (параллельность) + finisher.
Встроенные: `windowFixed`, `windowSliding`, `fold`, `scan`, `mapConcurrent` (на virtual threads, порядок сохраняется).
Свой пример — `distinctBy`.

**JEP 491 — что доказывает `PinningTest`.** Через JFR `RecordingStream` слушаем `jdk.VirtualThreadPinned`
(порог 0):
- блокировка (`sleep`) внутри `synchronized` в VT → **событий нет**: монитор теперь принадлежит
  виртуальному потоку, carrier освобождается;
- блокировка внутри upcall'а из C `qsort` (нативный кадр на стеке) → **событие есть**: нативный стек
  нельзя выгрузить в кучу. Pinning остаётся и для class initializer'ов (`<clinit>`).
- До Java 24 совет «заменяйте `synchronized` на `ReentrantLock`» был обязательным для VT; теперь — нет.

**Подводные камни.**
- `Gatherers.windowSliding(n)` на входе короче `n` даёт **одно** неполное окно, а не пустой результат.
- Состояние gatherer'а в `ofSequential` не потокобезопасно — для параллельных стримов нужен combiner.
- JFR-события доставляются асинхронно (≈ раз в секунду) — в тестах ждать через latch, а не `sleep`.

**Вопросы.**
1. *Gatherer vs Collector?* — Collector — терминальная операция (одна свёртка), gatherer —
   промежуточная: может преобразовывать, фильтровать, держать состояние, обрывать поток.
2. *Что такое pinning и что изменил JEP 491?* — VT не может отпустить carrier во время блокировки;
   раньше — внутри `synchronized` и нативных кадров, с 24 — только нативные кадры и `<clinit>`.
3. *Как диагностировать pinning?* — JFR-событие `jdk.VirtualThreadPinned` (с 21);
   `-Djdk.tracePinnedThreads` удалён в 24.
4. *Зачем Class-File API, если есть ASM?* — ASM отстаёт от новых версий class-файла; JDK-API всегда
   актуален (JDK сам на него переходит).

## Java 25

**Что появилось.** Scoped Values (JEP 506, превью в 21–24); flexible constructor bodies (JEP 513);
module import declarations (JEP 511); compact source files и instance `main` (JEP 512, `java.lang.IO`);
Key Derivation Function API; compact object headers (product), AOT-эргономика (→ `18-fast-startup`). LTS.
**Structured concurrency — всё ещё превью** (в 27 — седьмое), поэтому она в модуле `next`.

**Scoped Values vs ThreadLocal.**
| | `ThreadLocal` | `ScopedValue` |
|---|---|---|
| Изменяемость | `set` в любой момент | неизменяемо в пределах привязки |
| Время жизни | до `remove()` или смерти потока | ровно на время `call/run` |
| Утечки в пуле | да (тест `threadLocalLeaksIntoTheNextTaskOnAPooledThread`) | невозможны |
| Наследование | `InheritableThreadLocal` копирует | разделяется с форками `StructuredTaskScope` |
| Стоимость на миллион VT | миллион копий | ~0 |

**Подводные камни.**
- `ScopedValue` не наследуется в произвольно созданные потоки и `ExecutorService` — только в подзадачи
  `StructuredTaskScope`.
- Flexible constructor: до `super(...)` нельзя обращаться к `this` (поля, методы экземпляра), но можно
  **присваивать** поля — это закрывает класс багов «суперконструктор вызвал переопределённый метод до
  инициализации поля».
- `import module` может создать неоднозначность имён (`java.util.List` vs `java.awt.List` при импорте
  `java.desktop`) — явный single-type import её разрешает.

**Вопросы.**
1. *Почему Scoped Values появились вместе с virtual threads?* — `ThreadLocal` на миллионах VT дорог и
   опасен утечками; контекст запроса (tenant, traceId, security principal) нужен неизменяемый и ограниченный вызовом.
2. *Что позволяет JEP 513?* — Валидацию и подготовку аргументов до `super(...)`/`this(...)`, без
   статических helper-методов.
3. *Что такое compact source file?* — Файл без объявления класса: `void main()` + методы/поля; неявный
   `import module java.base`; для скриптов и обучения.

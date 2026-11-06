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

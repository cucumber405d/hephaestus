# Архитектура — 01-modern-java

Каталог примеров, а не сервис: слоёв нет, ArchUnit не применяется. Пакет может использовать типы
из пакетов **более ранних** версий, но не более поздних.

| Версия | Пакет | Классы | JEP / API |
|---|---|---|---|
| 9 | `java09` | `Material`, `MaterialCatalog`, `PriceFormatter`, `SupplierClient` | JEP 269 (collection factories), JEP 213 (private interface methods), `CompletableFuture.copy/completeOnTimeout`, `Stream.takeWhile/dropWhile/iterate`, `Optional.or` |
| 10 | `java10` | `OrderSnapshot` | JEP 286 (`var`), `List.copyOf` |
| 11 | `java11` | `PriceListParser`, `SupplierHttpGateway` | JEP 321 (`HttpClient`), JEP 323 (`var` в лямбдах), `String.lines/strip/isBlank/repeat`, `Files.readString/writeString`, `Predicate.not` |
| 12 | `java12` | `OrderStatistics` | `Collectors.teeing`, `String.transform/indent` |

# Архитектура — 01-modern-java

Каталог примеров, а не сервис: слоёв нет, ArchUnit не применяется. Пакет может использовать типы
из пакетов **более ранних** версий, но не более поздних.

| Версия | Пакет | Классы | JEP / API |
|---|---|---|---|
| 9 | `java09` | `Material`, `MaterialCatalog`, `PriceFormatter`, `SupplierClient` | JEP 269 (collection factories), JEP 213 (private interface methods), `CompletableFuture.copy/completeOnTimeout`, `Stream.takeWhile/dropWhile/iterate`, `Optional.or` |
| 10 | `java10` | `OrderSnapshot` | JEP 286 (`var`), `List.copyOf` |
| 11 | `java11` | `PriceListParser`, `SupplierHttpGateway` | JEP 321 (`HttpClient`), JEP 323 (`var` в лямбдах), `String.lines/strip/isBlank/repeat`, `Files.readString/writeString`, `Predicate.not` |
| 12 | `java12` | `OrderStatistics` | `Collectors.teeing`, `String.transform/indent` |
| 14 | `java14` | `OrderStatus`, `OrderWorkflow` | JEP 361 (switch expressions), JEP 358 (helpful NPE) |
| 15 | `java15` | `OrderTemplates` | JEP 378 (text blocks) |
| 16 | `java16` | `Money`, `OrderLine` | JEP 395 (records), JEP 394 (`instanceof` patterns), `Stream.toList/mapMulti` |
| 17 | `java17` | `PaymentResult` | JEP 409 (sealed), JEP 356 (`RandomGenerator`), `HexFormat` |
| 18 | `java18` | — (learning test) | JEP 400 (UTF-8 by default), JEP 413 (`@snippet`, см. `Money`) |
| 21 | `java21` | `SupplierFanOut`, `PaymentHandler`, `ForgeQueue`, `WorkItem` | JEP 444 (virtual threads), JEP 440 (record patterns), JEP 441 (switch patterns), JEP 431 (sequenced collections), `Math.clamp`, `StringBuilder.repeat` |
| 22 | `java22` | `NativeStrings`, `NativeSort`, `OffHeapBuffer`, `UnnamedExamples` | JEP 454 (FFM API), JEP 456 (unnamed variables & patterns) |
| 23 | `java23` | `MaterialGrade` | JEP 467 (Markdown doc comments) |

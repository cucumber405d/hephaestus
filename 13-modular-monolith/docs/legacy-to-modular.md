# От легаси к модульному монолиту: нарушение → лечение

Четыре узла `legacy/` (подробно — [legacy/README.md](../legacy/README.md)) и чем каждый лечится в `modular/`.

| # | Легаси | Модульный монолит | Чем проверяется |
|---|---|---|---|
| 1 | Цикл: `OrderService` вызывает конкретный `ForgeService`, тот вызывает обратно `markReady/reject`; нужен `@Lazy` | Ordering публикует `WorkOrderRequested`, Forge отвечает `WorkOrderCompleted`/`WorkOrderFailed`. **Все три события — API модуля `forge`**: зависимость одна, ordering → forge. События сами по себе не убирают цикл — если тип события лежит в модуле-слушателе, зависимость остаётся | `ModularStructureTest.verifyPasses` (`ApplicationModules.verify()`) |
| 2 | Forge пишет в `stock_item` склада | У каждого модуля своя схема PostgreSQL (`ordering`, `inventory`, `forge`); чужие таблицы не читаются и не пишутся. Изделие на заказ уходит клиенту напрямую; если складу понадобится знать — подпишется на `WorkOrderCompleted` | Ревью и ADR-0010; Modulith этого не видит (SQL — не зависимость Java) |
| 3 | Коды поставщика 0/17/42 и его XML — в логике и таблице Forge | ACL `supplier`: снаружи `Bestellung`/`Antwort`, внутрь — `SupplyResult` (`Delivered`, `PermanentFailure`, `TransientFailure`). XML-типы — в `supplier.internal`, их не видит никто | `SupplierAclTest`; `verify()` запрещает доступ к `internal` |
| 4 | Одна транзакция на заказ, ковку и HTTP-вызов поставщика без таймаута | Транзакция заказа заканчивается публикацией события (реестр публикаций — outbox, как в 10). Forge работает в своей транзакции после коммита (`@ApplicationModuleListener`); у вызова поставщика таймаут; недоступный поставщик — не ошибка, а «ждём материал» с повтором | `SmithApiIT.aSlowSupplierLeavesTheWorkOrderWaitingUntilTheRetryGetsTheMaterial` |

Цена: поведение стало **асинхронным**. В легаси заказ на мифрил отклонялся в том же запросе, теперь — через
мгновение, событием. Тесты ждут (`Awaitility`, `Scenario`), клиенты должны опрашивать статус. Это та же цена, что
заплатит вынос в сервис, — модульный монолит делает её видимой заранее, внутри одного процесса.

Диаграммы модулей генерирует `ModularStructureTest` (Spring Modulith Documenter) в [modules/](modules/).

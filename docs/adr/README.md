# Architecture Decision Records

Решения проекта Hephaestus в формате MADR 4.0 — как и почему см. [ADR-0000](0000-use-madr.md).
Новый ADR: скопировать [`template.md`](template.md) в `NNNN-kebab-title.md` (следующий номер), заполнить,
добавить строку в таблицу ниже в том же коммите.

| № | Решение | Статус | Область |
|---|---|---|---|
| [0000](0000-use-madr.md) | Решения записываем как ADR в формате MADR, в Markdown | accepted | весь репозиторий |
| [0001](0001-peak-sale-success-criteria.md) | Критерий успеха «пиковой распродажи» — ни один принятый заказ не потерян | accepted | 12 |
| [0002](0002-autoscaling-keda-kafka-lag.md) | Автомасштабирование — KEDA, консьюмеры по лагу Kafka, вход по CPU | accepted | 12, platform |
| [0003](0003-no-loss-on-scaling-and-rollouts.md) | Ничего не теряем при масштабировании и обновлении | accepted | 12 |
| [0004](0004-load-generation-gatling.md) | Нагрузка — Gatling (Java SDK), открытая модель | accepted | 12 |
| [0005](0005-disruptions-during-peak.md) | Помехи во время пика — обновление, удаление подов, drain узла | accepted | 12 |
| [0006](0006-deployment-on-platform.md) | Сага из 11 на общей платформе, без нового Java-кода | accepted | 12, platform |
| [0007](0007-twelve-factor-audit.md) | Аудит 12 факторов — таблица и CronJob | accepted | весь репозиторий |
| [0008](0008-connection-budget.md) | Бюджет соединений: реплики × пул ≤ того, что выдержит база | accepted | 12 |
| [0009](0009-strangler-fig-new-to-new.md) | Strangler Fig для Forge: новые в новый, старые доживают в старом | accepted | 13 |
| [0010](0010-module-boundaries-and-events.md) | Границы модулей — Spring Modulith; события; модуль владеет своими таблицами | accepted | 13 |
| [0011](0011-anti-corruption-layers.md) | Anti-Corruption Layer к поставщику и к монолиту | accepted | 13 |
| [0012](0012-contracts-module.md) | Контракты — свой модуль со своей версией, сервисы её фиксируют | accepted | 14 |
| [0013](0013-avro-apicurio-backward.md) | События — Avro в Apicurio, BACKWARD, схемы регистрирует пайплайн | accepted | 14 |
| [0014](0014-pact-consumer-driven.md) | Синхронный контракт — Pact (consumer-driven), без брокера на старте | accepted | 14 |

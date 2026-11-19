# Architecture Decision Records

Решения проекта Hephaestus в формате MADR 4.0 — как и почему см. [ADR-0000](0000-use-madr.md).
Новый ADR: скопировать [`template.md`](template.md) в `NNNN-kebab-title.md` (следующий номер), заполнить,
добавить строку в таблицу ниже в том же коммите.

| № | Решение | Статус | Область |
|---|---|---|---|
| [0000](0000-use-madr.md) | Решения записываем как ADR в формате MADR, в Markdown | accepted | весь репозиторий |
| [0001](0001-peak-sale-success-criteria.md) | Критерий успеха «пиковой распродажи» — ни один принятый заказ не потерян | proposed | 12 |
| [0002](0002-autoscaling-keda-kafka-lag.md) | Автомасштабирование — KEDA, консьюмеры по лагу Kafka, вход по CPU | proposed | 12, platform |
| [0003](0003-no-loss-on-scaling-and-rollouts.md) | Ничего не теряем при масштабировании и обновлении | proposed | 12 |
| [0004](0004-load-generation-gatling.md) | Нагрузка — Gatling (Java SDK), открытая модель | proposed | 12 |
| [0005](0005-disruptions-during-peak.md) | Помехи во время пика — обновление, удаление подов, drain узла | proposed | 12 |
| [0006](0006-deployment-on-platform.md) | Сага из 11 на общей платформе, без нового Java-кода | proposed | 12, platform |
| [0007](0007-twelve-factor-audit.md) | Аудит 12 факторов — таблица и CronJob | proposed | весь репозиторий |

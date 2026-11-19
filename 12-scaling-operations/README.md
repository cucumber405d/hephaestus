# 12-scaling-operations — пиковая распродажа

Сага заказа из [11-observability](../11-observability/README.md) переезжает в общий kind-кластер и проходит
**пиковую распродажу**: Gatling резко поднимает поток заказов, **KEDA** масштабирует сервисы — участников по лагу
Kafka, Ordering по CPU и лагу, — а посреди пика скрипт выкатывает новую версию, убивает поды или осушает узел.
Главный критерий: **ни один принятый заказ не потерян** — после прогона сверка проверяет каждый заказ, на который
Ordering ответил `202`. Плюс итоговый аудит Twelve-Factor по всему репозиторию.

Java-кода почти нет: те же образы, что в 11, настраиваются только переменными окружения. Тема — эксплуатация.

**Чему учит:** KEDA и HPA, масштаб консьюмеров и партиции, ребалансировка (протокол групп Kafka 4), мягкая
остановка, PodDisruptionBudget, стратегия обновления, нагрузочный тест с критериями до прогона, сверка деловых
инвариантов, административные процессы (Job, CronJob), Twelve-Factor.

- Решения: ADR [0001](../docs/adr/0001-peak-sale-success-criteria.md)–[0007](../docs/adr/0007-twelve-factor-audit.md)
- Спецификация: [docs/superpowers/specs/2026-11-19-12-scaling-operations-design.md](docs/superpowers/specs/2026-11-19-12-scaling-operations-design.md)
- План: [docs/superpowers/plans/2026-11-19-12-scaling-operations.md](docs/superpowers/plans/2026-11-19-12-scaling-operations.md)
- Старт: [docs/start.md](docs/start.md)
- Архитектура: [docs/architecture.md](docs/architecture.md)
- Выводы: [docs/learning-notes.md](docs/learning-notes.md)
- Аудит 12 факторов: [docs/twelve-factor-audit.md](docs/twelve-factor-audit.md)

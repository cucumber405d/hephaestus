# Learning notes — 03-containers-ci

## Каркас

- Сервис облегчён намеренно: API 02 (ETag, идемпотентность, merge patch) не нужен, чтобы учиться упаковке.
  Категории только читаются — их заводит миграция `V3__seed_categories.sql` с фиксированными UUID, на которые
  удобно ссылаться в тестах и `requests.http`.
- Конфигурация — только env vars (`PORT`, `SPRING_DATASOURCE_*`): один и тот же образ запускается в Compose, CI и K8s.

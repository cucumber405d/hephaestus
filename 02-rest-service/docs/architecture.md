# Архитектура — 02-rest-service

## Схема данных (Flyway)

| Миграция | Таблица | Ключевое |
|---|---|---|
| `V1__category.sql` | `category` | уникальность имени без учёта регистра (`lower(name)`) |
| `V2__item.sql` | `item` | `price_amount numeric(12,2) >= 0`, FK на `category`, индексы `category_id`, `price_amount` |
| `V3__idempotency_key.sql` | `idempotency_key` | ответ POST по `Idempotency-Key`, индекс `created_at` для очистки |

Hibernate работает в режиме `validate`: схему меняет только Flyway.

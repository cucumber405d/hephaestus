# Архитектура — 02-rest-service

## Слои (гексагональная архитектура)

```mermaid
flowchart LR
    client([HTTP client]) --> filter[IdempotencyFilter]
    filter --> ctrl
    subgraph adapter.in.web
        ctrl[CategoriesController<br/>ItemsController<br/>implements generated *Api]
        advice[ProblemDetailsAdvice]
        mapperW[WebMapper · MergePatchParser · ETags]
    end
    subgraph application
        portsIn[[port.in: ManageCategories / ManageItems / SearchItems]]
        svc[CategoryService · ItemService]
        portsOut[[port.out: CategoryRepository / ItemRepository / IdempotencyStore]]
    end
    subgraph domain
        dom[Item · Category · Money · ItemSearchCriteria · Ids]
    end
    subgraph adapter.out.persistence
        jpa[JpaCategoryRepository · JpaItemRepository<br/>ItemSpecifications · PersistenceMapper]
        jdbc[JdbcIdempotencyStore · IdempotencyCleanup]
    end
    ctrl --> portsIn --> svc --> portsOut
    svc --> dom
    jpa -.implements.-> portsOut
    jdbc -.implements.-> portsOut
    filter --> portsOut
    jpa --> db[(PostgreSQL 17)]
    jdbc --> db
```

- Домен — только JDK (ArchUnit). JPA-сущности, Spring Data и SQL живут в `adapter.out.persistence`.
- Сгенерированный API (`adapter.in.web.api`, `target/generated-sources`) виден только веб-адаптеру (ArchUnit).
- Нарушения ограничений БД переводятся в доменные исключения внутри адаптера; веб-слой знает только домен.

## Сквозные механизмы

| Механизм | Где | Суть |
|---|---|---|
| Contract-first | `static/catalog-api.yaml` → openapi-generator | интерфейсы + DTO; ответы проверяет `CatalogScenarioIT` |
| Problem Details | `ProblemDetailsAdvice`, `IdempotencyFilter` | RFC 9457, `type` = `https://hephaestus.ibikus.de/problems/<slug>` |
| Идемпотентность | `IdempotencyFilter` + `idempotency_key` | резерв ключа → запрос → сохранение ответа; очистка раз в час |
| Оптимистическая блокировка | `ETags`, `ItemService`, `@Version` | `If-Match` → `428`/`412`; гонка в БД → `412` |
| Версия API | `spring.mvc.apiversion.*` | заголовок `API-Version`, поддерживается `1` |

## Схема данных (Flyway)

| Миграция | Таблица | Ключевое |
|---|---|---|
| `V1__category.sql` | `category` | уникальность имени без учёта регистра (`lower(name)`) |
| `V2__item.sql` | `item` | `price_amount numeric(12,2) >= 0`, FK на `category`, индексы `category_id`, `price_amount` |
| `V3__idempotency_key.sql` | `idempotency_key` | ответ POST по `Idempotency-Key`, индекс `created_at` для очистки |

Hibernate работает в режиме `validate`: схему меняет только Flyway.

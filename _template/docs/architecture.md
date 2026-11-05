# Архитектура — _template

```mermaid
flowchart LR
    client([HTTP client]) --> ctrl
    subgraph greeting-service
        subgraph adapter.in.web
            ctrl[GreetingController]
        end
        subgraph application
            svc[GreetingService]
        end
        subgraph domain
            g[Greeting]
        end
        ctrl --> svc --> g
    end
```

- Зависимости направлены внутрь; `domain` не знает Spring. Проверяется `HexagonalArchitectureTest`.
- Ошибки — RFC 9457 Problem Details (`application/problem+json`).
- Virtual threads, graceful shutdown, liveness/readiness probes, ECS-JSON логи в stdout.

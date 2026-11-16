# Architecture — 04-k8s-basics

```mermaid
flowchart LR
    user[kubectl port-forward / load pod] -->|:80| svc[Service catalog<br/>ClusterIP]
    svc --> p1[Pod catalog]
    svc --> p2[Pod catalog]
    subgraph deploy [Deployment catalog, 2 replicas]
        p1
        p2
    end
    job[Job catalog-migrate<br/>same image, web off, Flyway on] -->|DDL| pgsvc
    p1 -->|JDBC| pgsvc[Service postgres<br/>headless]
    p2 -->|JDBC| pgsvc
    pgsvc --> pg[(StatefulSet postgres-0<br/>PVC 1Gi)]
    cm[ConfigMap catalog-config-hash] -.env.-> p1 & p2 & job
    sec[Secret postgres-credentials-hash] -.env.-> p1 & p2 & job & pg
```

Namespace `hephaestus-04`. Образ `ghcr.io/cucumber405d/hephaestus/containers-ci-catalog:sha-d730159`.

## Порядок деплоя (`scripts/deploy.sh`)

```mermaid
sequenceDiagram
    participant D as deploy.sh
    participant K as API server
    D->>K: apply component in (namespace, config, database)
    D->>K: rollout status statefulset/postgres
    D->>K: delete job catalog-migrate; apply component=migration
    loop until succeeded / failed
        D->>K: get job status
    end
    D->>K: apply component=api
    D->>K: rollout status deployment/catalog
```

## Жизнь пода при удалении (rollout, scale down)

```mermaid
sequenceDiagram
    participant K as kubelet
    participant E as EndpointSlice / kube-proxy
    participant P as catalog pod
    K->>E: pod Terminating -> removed from endpoints (asynchronously)
    K->>P: preStop: sleep 10 s (pod still serves late traffic)
    K->>P: SIGTERM -> Spring graceful shutdown (up to 20 s)
    P-->>K: exit 143
    Note over K,P: SIGKILL only after terminationGracePeriodSeconds = 45
```

Решения и альтернативы — в [спецификации](superpowers/specs/2026-11-16-04-k8s-basics-design.md), раздел 2.

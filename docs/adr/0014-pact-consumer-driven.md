---
status: accepted
date: 2026-11-24
decision-makers: Dmitry
scope: 14-contracts-grpc
---

# ADR-0014: Синхронный контракт — Pact (consumer-driven), без брокера на старте

## Контекст и проблема

`buf breaking` ловит только поломки формата. Удаление поля с `reserved` формат не ломает, но ломает клиента,
который это поле читает. Провайдер не знает, что именно читают его клиенты.

## Движущие факторы

- Контракт — то, что потребитель действительно использует, а не всё, что умеет провайдер.
- gRPC без HTTP-обёрток.
- Проверка в обычной сборке провайдера.

## Рассмотренные варианты

- **Pact** (pact-jvm 4.7, protobuf-плагин для gRPC).
- Spring Cloud Contract.
- Только `buf breaking`.

## Решение

Выбран **Pact**. `QuotePactConsumerTest` в Ordering гоняет настоящий `GrpcQuoteClient` против mock-сервера Pact и
пишет `pacts/ordering-forge-service-quote.json` (файл в репозитории). `QuotePactProviderTest` в forge-service
проигрывает его против настоящего gRPC-сервера. Pact Broker и `can-i-deploy` — следующий шаг, не в 14.

### Последствия

- Хорошо: сценарий 2 ловится сборкой провайдера.
- Плохо: protobuf-плагин — нативный бинарник с GitHub, его нужно поставить (`install-pact-plugin.sh`).
- Плохо: без брокера провайдер проверяется только против последнего pact-файла, а не против всех версий в проде.
- Spring Cloud Contract отклонён: контракт по умолчанию пишет провайдер, нет нативного gRPC, только JVM.

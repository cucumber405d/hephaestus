# Pacts

`ordering-forge-service-quote.json` — what Ordering expects of Forge's `QuoteService/Quote`. Written by
`ordering`'s `QuotePactConsumerTest` (Maven sets `pact.rootDir` to this folder), verified by `forge-service`'s
`QuotePactProviderTest`. The file is committed: forge-service's build (and `scripts/breaking-change.sh`) verifies
against the version in the repository, the way a Pact Broker would serve it. CI fails when a build changes it
without a commit (`git status --porcelain 14-contracts-grpc/pacts` must be empty).

#!/usr/bin/env bash
# Validates contracts/asyncapi.yaml, including the referenced Avro schemas. Needs Docker.
set -euo pipefail

ASYNCAPI_CLI_VERSION="${ASYNCAPI_CLI_VERSION:-5.0.7}"
cd "$(dirname "$0")/../contracts"
docker run --rm -v "$PWD:/app" -w /app "asyncapi/cli:$ASYNCAPI_CLI_VERSION" validate asyncapi.yaml
echo "asyncapi validate: OK"

#!/usr/bin/env bash
# Validates contracts/asyncapi.yaml, including the referenced Avro schemas. Needs Docker.
set -euo pipefail
# Git Bash (MINGW) rewrites /paths in docker arguments into C:/…/Git/… — keep them as written
export MSYS_NO_PATHCONV=1

ASYNCAPI_CLI_VERSION="${ASYNCAPI_CLI_VERSION:-5.0.7}"
cd "$(dirname "$0")/../contracts"
# Docker Desktop on Windows needs C:/… for a bind mount; pwd -W gives it in Git Bash, plain pwd elsewhere
HOST_DIR="$(pwd -W 2> /dev/null || pwd)"
docker run --rm -v "$HOST_DIR:/app" -w /app "asyncapi/cli:$ASYNCAPI_CLI_VERSION" validate asyncapi.yaml
echo "asyncapi validate: OK"

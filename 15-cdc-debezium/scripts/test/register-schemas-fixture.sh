#!/usr/bin/env bash
# register-schemas.sh against a fake registry, twice: the second run (compose run, every Argo sync) must succeed too.
set -euo pipefail
cd "$(dirname "$0")/../.."   # 15-cdc-debezium
SCRIPT="${1:-scripts/register-schemas.sh}"
PORT=18185
python3 scripts/test/fake-registry.py "$PORT" &
server=$!
trap 'kill $server 2> /dev/null || true' EXIT
for _ in $(seq 1 50); do curl -s -o /dev/null "http://127.0.0.1:$PORT/" && break; sleep 0.1; done

sh "$SCRIPT" "http://127.0.0.1:$PORT" contracts/src/main/avro 1.1.0 > /dev/null || { echo "FAIL first run"; exit 1; }
echo "ok    first run registers"
sh "$SCRIPT" "http://127.0.0.1:$PORT" contracts/src/main/avro 1.1.0 || { echo "FAIL second run"; exit 1; }
echo "ok    second run keeps the registered version"

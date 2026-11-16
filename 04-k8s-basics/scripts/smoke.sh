#!/usr/bin/env bash
# Smoke test against the deployed Catalog in the current kubectl context (after deploy.sh):
# migration by the Job, API round trip, what the JVM sees in the pod, zero-downtime rollout under load.
set -euo pipefail

NS=hephaestus-04
PORT="${LOCAL_PORT:-18080}"
WEAPONS=01900000-0000-7000-8000-000000000003

k() { kubectl -n "$NS" "$@"; }
ok() { echo "ok    $*"; }
fail() {
    echo "FAIL  $*" >&2
    exit 1
}

echo "==> migration"
[ "$(k get job catalog-migrate -o jsonpath='{.status.succeeded}')" = "1" ] || fail "migration job did not succeed"
ok "job catalog-migrate succeeded"
flyway=$(k get deployment catalog \
    -o jsonpath='{.spec.template.spec.containers[0].env[?(@.name=="SPRING_FLYWAY_ENABLED")].value}')
[ "$flyway" = "false" ] || fail "Flyway must be disabled in the API Deployment, got '$flyway'"
ok "Flyway disabled in the API"
migrations=$(k exec postgres-0 -- sh -c \
    'psql -U "$POSTGRES_USER" -d catalog -tAc "select count(*) from flyway_schema_history where success"')
[ "$migrations" -ge 3 ] || fail "expected >= 3 applied migrations, got $migrations"
ok "$migrations migrations applied"

echo "==> API through a port-forward"
k port-forward svc/catalog "$PORT:80" >/dev/null 2>&1 &
forward=$!
trap 'kill $forward 2>/dev/null || true; k delete pod load --ignore-not-found --wait=false >/dev/null 2>&1 || true' EXIT
base="http://localhost:$PORT"
for _ in $(seq 1 30); do
    curl -fs "$base/actuator/health/readiness" >/dev/null 2>&1 && break
    sleep 1
done

categories=$(curl -fs "$base/api/categories") || fail "GET /api/categories"
[ "$(echo "$categories" | grep -o '"name"' | wc -l | tr -d ' ')" = "3" ] || fail "expected 3 categories: $categories"
ok "3 categories"

headers=$(curl -s -D - -o /dev/null -H 'Content-Type: application/json' \
    -d "{\"name\":\"K8s hammer\",\"price\":{\"amount\":\"15.00\",\"currency\":\"EUR\"},\"categoryId\":\"$WEAPONS\"}" \
    "$base/api/items")
status=$(echo "$headers" | head -1 | awk '{ print $2 }')
[ "$status" = "201" ] || fail "POST /api/items returned $status"
path=$(echo "$headers" | tr -d '\r' | sed -n 's/^[Ll]ocation: *https\{0,1\}:\/\/[^/]*//p')
[ "$(curl -s -o /dev/null -w '%{http_code}' "$base$path")" = "200" ] || fail "GET $path"
ok "POST 201, GET $path 200"

runtime=$(curl -fs "$base/actuator/runtime") || fail "GET /actuator/runtime"
echo "$runtime" | grep -q '"availableProcessors":1[,}]' || fail "expected 1 CPU: $runtime"
echo "$runtime" | grep -q '"containerMemoryLimitBytes":805306368' || fail "expected the 768Mi limit: $runtime"
ok "JVM sees 1 CPU and the 768Mi limit"

echo "==> zero-downtime rollout under load"
# The load runs inside the cluster against the Service: a port-forward pins one pod and would break with it.
k delete pod load --ignore-not-found --wait=true >/dev/null
k run load --image=curlimages/curl:8.11.1 --restart=Never --command -- sh -c \
    'while true; do curl -s -o /dev/null -m 2 -w "%{http_code}\n" http://catalog/api/categories; sleep 0.05; done' \
    >/dev/null
k wait --for=condition=Ready pod/load --timeout=120s >/dev/null
sleep 5
k rollout restart deployment/catalog >/dev/null
k rollout status deployment/catalog --timeout=300s
sleep 5
results=$(k logs load)
total=$(echo "$results" | grep -c . || true)
bad=$(echo "$results" | grep -vc '^200$' || true)
[ "$total" -gt 100 ] || fail "too few requests during the rollout: $total"
[ "$bad" = "0" ] || fail "$bad of $total requests failed during the rollout: $(echo "$results" | sort | uniq -c | tr '\n' ' ')"
ok "$total requests during rollout restart, 0 failed"

echo "SMOKE OK"

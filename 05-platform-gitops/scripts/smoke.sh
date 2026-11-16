#!/usr/bin/env bash
# Smoke test of the shared platform (after platform/bootstrap.sh), in the current kubectl context:
#   1. every Argo CD Application is Synced and Healthy (Argo CD did all the work from Git),
#   2. the Gateway serves Argo CD, Grafana and the Catalog on *.localtest.me (localhost:80),
#   3. profile "full": Keycloak, Kafka and Redis answer.
#   05-platform-gitops/scripts/smoke.sh [minimal|full]
set -euo pipefail

PROFILE="${1:-minimal}"
TIMEOUT_S="${SMOKE_TIMEOUT_S:-900}"
WEAPONS=01900000-0000-7000-8000-000000000003

ok() { echo "ok    $*"; }
fail() {
    echo "FAIL  $*" >&2
    exit 1
}
# Talk to the Gateway on localhost:80 without depending on public DNS for *.localtest.me
http_code() { curl -s -o /dev/null -w '%{http_code}' --resolve "$1:80:127.0.0.1" "http://$1$2"; }
http_body() { curl -fs --resolve "$1:80:127.0.0.1" "http://$1$2"; }

echo "==> Argo CD Applications (up to ${TIMEOUT_S}s)"
deadline=$(($(date +%s) + TIMEOUT_S))
while true; do
    states=$(kubectl -n argocd get applications \
        -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.sync.status}{" "}{.status.health.status}{"\n"}{end}')
    pending=$(echo "$states" | grep -v ' Synced Healthy$' || true)
    if [ -n "$states" ] && [ -z "$pending" ]; then
        break
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
        fail "Applications not Synced/Healthy: $(echo "$pending" | tr '\n' ';')"
    fi
    echo "      waiting: $(echo "$pending" | tr '\n' ';')"
    sleep 15
done
apps=$(echo "$states" | grep -c .)
ok "$apps Applications Synced and Healthy"
expected=7
[ "$PROFILE" = "full" ] && expected=12
[ "$apps" -ge "$expected" ] || fail "expected at least $expected Applications for profile $PROFILE, got $apps"

echo "==> through the Gateway (*.localtest.me -> localhost:80)"
[ "$(http_code argocd.localtest.me /healthz)" = "200" ] || fail "Argo CD /healthz"
ok "argocd.localtest.me"
[ "$(http_code grafana.localtest.me /api/health)" = "200" ] || fail "Grafana /api/health"
ok "grafana.localtest.me"

categories=$(http_body catalog.localtest.me /api/categories) || fail "Catalog /api/categories"
[ "$(echo "$categories" | grep -o '"name"' | wc -l | tr -d ' ')" = "3" ] || fail "expected 3 categories: $categories"
status=$(curl -s -o /dev/null -w '%{http_code}' --resolve catalog.localtest.me:80:127.0.0.1 \
    -H 'Content-Type: application/json' \
    -d "{\"name\":\"Platform anvil\",\"price\":{\"amount\":\"99.00\",\"currency\":\"EUR\"},\"categoryId\":\"$WEAPONS\"}" \
    http://catalog.localtest.me/api/items)
[ "$status" = "201" ] || fail "POST /api/items returned $status"
ok "catalog.localtest.me: 3 categories, POST 201"
phase=$(kubectl -n catalog get clusters.postgresql.cnpg.io catalog-db -o jsonpath='{.status.phase}')
[ "$phase" = "Cluster in healthy state" ] || fail "catalog-db phase: $phase"
ok "CloudNativePG catalog-db: $phase"

if [ "$PROFILE" = "full" ]; then
    echo "==> full profile"
    [ "$(http_code keycloak.localtest.me /realms/master/.well-known/openid-configuration)" = "200" ] \
        || fail "Keycloak OIDC discovery"
    ok "keycloak.localtest.me: OIDC discovery"
    kubectl -n kafka wait kafkas.kafka.strimzi.io/platform --for=condition=Ready --timeout=300s >/dev/null \
        || fail "Kafka platform not Ready"
    ok "Kafka platform Ready"
    [ "$(kubectl -n redis exec redis-0 -- redis-cli ping)" = "PONG" ] || fail "Redis PING"
    ok "Redis PONG"
fi

echo "SMOKE OK ($PROFILE)"

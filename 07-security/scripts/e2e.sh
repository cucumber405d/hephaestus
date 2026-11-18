#!/usr/bin/env bash
# End-to-end test of the 07 stack in Docker Compose (Keycloak, Gateway as BFF, Ordering, Inventory):
# a scripted browser logs in at Keycloak (Authorization Code + PKCE), then uses the shop through the Gateway with its
# session cookie and the CSRF header; roles and order ownership are checked for alice, bob and admin; finally the
# services are called directly inside the Compose network to show they trust only valid, suitable tokens.
#   07-security/scripts/e2e.sh          (SKIP_BUILD=true to reuse images, KEEP_STACK=true to keep it up)
# Needs JDK 25, Docker and curl. The Gateway must be on port 8080: that is the redirect URI registered in the realm.
set -euo pipefail

cd "$(dirname "$0")/.."   # 07-security
PROJECT=hephaestus-07
BASE="http://localhost:8080"
KEYCLOAK="http://localhost:8180"
CURL_IMAGE=curlimages/curl:8.11.1
BODY=/tmp/07-e2e-body.json

ok() { echo "ok    $*"; }
fail() {
    echo "FAIL  $*" >&2
    docker compose -p "$PROJECT" logs --tail=60 >&2 || true
    exit 1
}
compose() { docker compose -p "$PROJECT" "$@"; }
cleanup() {
    if [ "${KEEP_STACK:-false}" != "true" ]; then
        compose down -v >/dev/null 2>&1 || true
    fi
    rm -f /tmp/07-e2e-*.jar "$BODY"
}
# status code of a request; extra curl arguments pass through
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
# curl inside the Compose network, where the services are reachable by name (they publish no ports)
incluster() { docker run --rm --network "${PROJECT}_default" "$CURL_IMAGE" -s "$@"; }

# --- the browser: one cookie jar per user (gateway session + Keycloak SSO cookies)
jar() { echo "/tmp/07-e2e-$1.jar"; }
as() { local user=$1; shift; curl -s -b "$(jar "$user")" -c "$(jar "$user")" "$@"; }
xsrf() { awk '$6 == "XSRF-TOKEN" { print $7 }' "$(jar "$1")"; }
# POST an order as the user, with the CSRF header unless the third argument is "no-csrf"; prints the headers
order() {
    local csrf=(-H "X-XSRF-TOKEN: $(xsrf "$1")")
    [ "${3:-}" = "no-csrf" ] && csrf=()
    as "$1" -D - -o "$BODY" -H 'Content-Type: application/json' "${csrf[@]}" -d "$2" "$BASE/api/orders"
}
status() { echo "$1" | head -1 | awk '{ print $2 }'; }

login() {
    local user=$1 jar to_keycloak form_action back_to_gateway landed
    jar=$(jar "$user")
    rm -f "$jar"
    # 1. The gateway sends the browser to Keycloak (PKCE challenge in the URL, verifier stays in the gateway)
    to_keycloak=$(as "$user" -o /dev/null -w '%{redirect_url}' "$BASE/oauth2/authorization/keycloak")
    case "$to_keycloak" in "$KEYCLOAK"/realms/hephaestus/*) ;; *) fail "login $user: no redirect to Keycloak: $to_keycloak" ;; esac
    echo "$to_keycloak" | grep -q 'code_challenge_method=S256' || fail "login $user: no PKCE S256 in $to_keycloak"
    # 2. Keycloak's login form
    form_action=$(as "$user" "$to_keycloak" | grep -o 'action="[^"]*"' | head -1 | sed 's/^action="//; s/"$//; s/&amp;/\&/g')
    [ -n "$form_action" ] || fail "login $user: no login form at Keycloak"
    # 3. The user types the password; Keycloak sends the browser back to the gateway with a code
    back_to_gateway=$(as "$user" -o /dev/null -w '%{redirect_url}' \
        --data-urlencode "username=$user" --data-urlencode "password=$user" --data-urlencode "credentialId=" \
        "$form_action")
    case "$back_to_gateway" in "$BASE"/login/oauth2/code/keycloak\?*) ;; *) fail "login $user: Keycloak did not send the browser back: $back_to_gateway" ;; esac
    # 4. The gateway redeems the code (with the verifier) and starts the session
    landed=$(as "$user" -o /dev/null -w '%{http_code} %{redirect_url}' "$back_to_gateway")
    case "$landed" in "302 $BASE/"*error*) fail "login $user: gateway rejected the login: $landed" ;; "302 $BASE/"*) ;; *) fail "login $user: $landed" ;; esac
    # The first page after login hands out the XSRF-TOKEN cookie
    as "$user" "$BASE/me" | grep -q "\"name\":\"$user\"" || fail "login $user: /me does not show $user"
    [ -n "$(xsrf "$user")" ] || fail "login $user: no XSRF-TOKEN cookie"
    ok "login $user via Keycloak form (PKCE S256), /me shows $user"
}

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    echo "==> images"
    ../mvnw -B -ntp -f pom.xml package jib:dockerBuild -DskipTests -Dspotless.check.skip=true
fi

echo "==> stack"
compose down -v >/dev/null 2>&1 || true
trap cleanup EXIT
compose up -d
docker pull -q "$CURL_IMAGE" >/dev/null
ready() {
    [ "$(code "$BASE/actuator/health/readiness")" = "200" ] \
        && [ "$(incluster -o /dev/null -w '%{http_code}' http://ordering:8080/actuator/health/readiness)" = "200" ] \
        && [ "$(incluster -o /dev/null -w '%{http_code}' http://inventory:8080/actuator/health/readiness)" = "200" ]
}
for _ in $(seq 1 90); do
    if ready; then break; fi
    sleep 2
done
ready || fail "stack did not become ready"
ok "stack ready (Keycloak healthy, services ready)"

echo "==> anonymous"
[ "$(code "$BASE/api/stock/ANVIL-01")" = "401" ] || fail "anonymous API call must be 401"
ok "anonymous /api/stock: 401"
# A browser navigation (Accept: text/html) is sent to the login; a fetch() without it gets 401 like the API
[ "$(code -H 'Accept: text/html' "$BASE/me")" = "302" ] || fail "anonymous page must redirect to the login"
ok "anonymous /me as a page: 302 to the login"
[ "$(code "$BASE/me")" = "401" ] || fail "anonymous /me as a fetch must be 401"
ok "anonymous /me as a fetch: 401"

echo "==> login"
login alice
login bob
login admin

echo "==> API versions (Inventory through the gateway, as alice)"
headers=$(as alice -D - -o "$BODY" -H 'API-Version: 1' "$BASE/api/stock/ANVIL-01")
[ "$(status "$headers")" = "200" ] || fail "v1: $(status "$headers")"
grep -q '"available":10' "$BODY" || fail "v1 body: $(cat "$BODY")"
grep -q 'onHand' "$BODY" && fail "v1 must not show onHand"
echo "$headers" | grep -qi '^Deprecation:' || fail "v1 must carry a Deprecation header"
echo "$headers" | grep -qi '^X-Gateway: hephaestus' || fail "missing X-Gateway header"
ok "v1: available only, Deprecation header"
as alice -H 'API-Version: 2' "$BASE/api/stock/ANVIL-01" | grep -q '"onHand":10' || fail "v2 body"
ok "v2: onHand/reserved/available"
[ "$(as alice -o /dev/null -w '%{http_code}' -H 'API-Version: 3' "$BASE/api/stock/ANVIL-01")" = "400" ] \
    || fail "unsupported version must be 400"
ok "v3: 400"

echo "==> orders (alice: customer)"
headers=$(order alice '{"lines":[{"sku":"ANVIL-01","quantity":2}]}' no-csrf)
[ "$(status "$headers")" = "403" ] || fail "POST without X-XSRF-TOKEN must be 403, got $(status "$headers")"
ok "POST without CSRF header: 403"
headers=$(order alice '{"lines":[{"sku":"ANVIL-01","quantity":2}]}')
[ "$(status "$headers")" = "201" ] || fail "place order: $(status "$headers") $(cat "$BODY")"
location=$(echo "$headers" | tr -d '\r' | sed -n 's/^[Ll]ocation: *//p')
path=$(echo "$location" | sed 's#^https\{0,1\}://[^/]*##')
as alice "$BASE$path" | grep -q '"status":"CONFIRMED"' || fail "alice GET $path"
ok "POST /api/orders 201, own order $path CONFIRMED (Location: $location)"
as alice "$BASE/api/orders/availability/ANVIL-01" | grep -q '"available":8' || fail "availability after order"
ok "availability 10 -> 8 (Ordering asked Inventory with its service token)"
headers=$(order alice '{"lines":[{"sku":"TONGS-01","quantity":1}]}')
[ "$(status "$headers")" = "409" ] || fail "out of stock must be 409"
grep -q '"sku":"TONGS-01"' "$BODY" || fail "409 must name the SKU"
ok "out of stock: 409 insufficient-stock"
headers=$(order alice '{"lines":[{"sku":"NOPE-01","quantity":1}]}')
[ "$(status "$headers")" = "422" ] || fail "unknown SKU must be 422"
ok "unknown SKU: 422"
reserved=$(as alice -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -H "X-XSRF-TOKEN: $(xsrf alice)" -d '{}' "$BASE/api/reservations")
[ "$reserved" = "404" ] || fail "internal /api/reservations must not be routed, got $reserved"
ok "internal reservation API: 404 at the gateway"

echo "==> roles and ownership"
headers=$(order bob '{"lines":[{"sku":"ANVIL-01","quantity":1}]}')
[ "$(status "$headers")" = "403" ] || fail "bob (smith) must not place orders, got $(status "$headers")"
ok "bob (smith) POST order: 403"
[ "$(as bob -o /dev/null -w '%{http_code}' "$BASE$path")" = "404" ] || fail "bob must not see alice's order"
ok "bob GET alice's order: 404"
[ "$(as admin -o /dev/null -w '%{http_code}' "$BASE$path")" = "200" ] || fail "admin must see any order"
ok "admin GET alice's order: 200"
headers=$(order admin '{"lines":[{"sku":"ANVIL-01","quantity":1}]}')
[ "$(status "$headers")" = "403" ] || fail "admin is no customer: POST must be 403"
ok "admin POST order: 403"

echo "==> services directly (inside the Compose network, bypassing the gateway)"
token_endpoint=http://keycloak:8080/realms/hephaestus/protocol/openid-connect/token
access_token() { sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'; }
reservation='{"orderId":"00000000-0000-4000-8000-0000000000e2","lines":[{"sku":"HAMMER-01","quantity":1}]}'
[ "$(incluster -o /dev/null -w '%{http_code}' http://inventory:8080/api/stock/ANVIL-01)" = "401" ] \
    || fail "Inventory without token must be 401"
ok "Inventory without token: 401"
alice_token=$(incluster -d grant_type=password -d client_id=shop-cli -d username=alice -d password=alice \
    "$token_endpoint" | access_token)
[ -n "$alice_token" ] || fail "no token for alice from shop-cli"
reserved=$(incluster -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $alice_token" \
    -H 'Content-Type: application/json' -d "$reservation" http://inventory:8080/api/reservations)
[ "$reserved" = "403" ] || fail "a user token must not reserve stock directly, got $reserved"
ok "alice's token at Inventory /api/reservations: 403"
service_token=$(incluster -u ordering:ordering-secret-local-only -d grant_type=client_credentials \
    "$token_endpoint" | access_token)
reserved=$(incluster -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $service_token" \
    -H 'Content-Type: application/json' -d "$reservation" http://inventory:8080/api/reservations)
[ "$reserved" = "201" ] || fail "the Ordering service token must reserve, got $reserved"
ok "Ordering's service token (client credentials) at /api/reservations: 201"
# The service token is meant for Inventory only (aud = inventory): Ordering rejects it before looking at roles
[ "$(incluster -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer $service_token" \
    -H 'Content-Type: application/json' -d '{"lines":[{"sku":"ANVIL-01","quantity":1}]}' \
    http://ordering:8080/api/orders)" = "401" ] || fail "a token for another audience must be 401 at Ordering"
ok "service token (aud inventory) at Ordering POST /api/orders: 401"

echo "==> Inventory outage"
compose stop inventory >/dev/null
started=$(date +%s)
headers=$(order alice '{"lines":[{"sku":"HAMMER-01","quantity":1}]}')
elapsed=$(($(date +%s) - started))
[ "$(status "$headers")" = "503" ] || fail "outage must be 503: $(status "$headers")"
echo "$headers" | grep -qi '^Retry-After: 5' || fail "503 must carry Retry-After"
[ "$elapsed" -le 5 ] || fail "outage answer took ${elapsed}s; timeouts must keep it short"
ok "503 inventory-unavailable with Retry-After in ${elapsed}s"
compose start inventory >/dev/null

echo "==> logout"
[ "$(as alice -o /dev/null -w '%{http_code}' -X POST -H "X-XSRF-TOKEN: $(xsrf alice)" "$BASE/logout")" = "200" ] \
    || fail "logout must be 200"
[ "$(as alice -o /dev/null -w '%{http_code}' "$BASE/api/stock/ANVIL-01")" = "401" ] || fail "after logout: 401"
ok "logout: session gone, API 401"

echo "E2E OK"

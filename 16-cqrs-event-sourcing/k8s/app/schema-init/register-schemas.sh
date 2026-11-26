#!/bin/sh
# Registers the Avro schemas in Apicurio Registry (v3 REST API) with the BACKWARD compatibility rule.
# Services never auto-register: the pipeline (here, compose's schema-init) owns the schemas.
# Running it again is harmless: an already registered version is kept, the rule is set again.
# Usage: register-schemas.sh <registry-url> <avro-dir> <version>   e.g. http://apicurio:8080 /avro 1.1.0
# <version> is the contract version (contracts/pom.xml): producers write with exactly that registered version.
# POSIX sh on purpose: it runs in the curl image, which has no bash and no jq.
set -eu

REGISTRY="${1:?registry url}"
AVRO_DIR="${2:?avro dir}"
VERSION="${3:?contract version}"
API="$REGISTRY/apis/registry/v3/groups/default/artifacts"

# Escapes a schema file as a JSON string (the schemas contain no backslashes).
json_string() {
    printf '"%s"' "$(tr -d '\n\r' < "$1" | sed 's/"/\\"/g')"
}

register() {
    artifact="$1"
    file="$AVRO_DIR/$2"
    content="{\"content\":$(json_string "$file"),\"contentType\":\"application/json\"}"
    status=$(curl -s -o /dev/null -w '%{http_code}' "$API/$artifact")
    # Idempotent: a re-run (compose run starts schema-init again, every Argo sync runs the hook) keeps the version
    if [ "$status" = "200" ] && [ "$(curl -s -o /dev/null -w '%{http_code}' "$API/$artifact/versions/$VERSION")" = "200" ]; then
        echo "$artifact $VERSION already registered"
        status=200
    elif [ "$status" = "404" ]; then
        status=$(curl -s -o /tmp/register.out -w '%{http_code}' -X POST "$API" -H 'Content-Type: application/json' \
            -d "{\"artifactId\":\"$artifact\",\"artifactType\":\"AVRO\",\"firstVersion\":{\"version\":\"$VERSION\",\"content\":$content}}")
    else
        status=$(curl -s -o /tmp/register.out -w '%{http_code}' -X POST "$API/$artifact/versions" \
            -H 'Content-Type: application/json' -d "{\"version\":\"$VERSION\",\"content\":$content}")
    fi
    if [ "$status" != "200" ]; then
        echo "register $artifact from $2: HTTP $status" >&2
        cat /tmp/register.out >&2
        exit 1
    fi
    # The rule may already exist (409): then update it.
    rule='{"ruleType":"COMPATIBILITY","config":"BACKWARD"}'
    status=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$API/$artifact/rules" -H 'Content-Type: application/json' -d "$rule")
    if [ "$status" = "409" ]; then
        status=$(curl -s -o /dev/null -w '%{http_code}' -X PUT "$API/$artifact/rules/COMPATIBILITY" \
            -H 'Content-Type: application/json' -d "$rule")
    fi
    case "$status" in
        200 | 204) echo "registered $artifact ($2) as $VERSION, rule BACKWARD" ;;
        *) echo "rule for $artifact: HTTP $status" >&2 && exit 1 ;;
    esac
}

register es.forge.requests-value work_order_request.avsc
register es.forge.events-value forge_event.avsc

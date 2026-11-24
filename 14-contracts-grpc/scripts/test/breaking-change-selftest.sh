#!/usr/bin/env bash
# breaking-change.sh must refuse a scenario whose patch no longer applies (exit 2), never report it as caught.
# Needs no Docker: the patch check runs before any build.
set -euo pipefail

SCRIPTS="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Scenario 1 with a context line that is not in quote.proto any more
sed 's/^ message QuoteReply {$/ message QuoteAnswer {/' "$SCRIPTS/scenarios/1-proto-type-change.patch" \
    > "$TMP/1-proto-type-change.patch"
grep -q 'QuoteAnswer' "$TMP/1-proto-type-change.patch" || { echo "selftest setup: context line not found" >&2; exit 1; }

set +e
SCENARIO_DIR="$TMP" "$SCRIPTS/breaking-change.sh" 1 > "$TMP/out.log" 2>&1
status=$?
set -e

if [[ $status -eq 2 ]] && grep -q "SCENARIO DRIFTED" "$TMP/out.log"; then
    echo "selftest OK: a drifted scenario exits 2"
else
    echo "selftest FAILED: exit $status" >&2
    cat "$TMP/out.log" >&2
    exit 1
fi

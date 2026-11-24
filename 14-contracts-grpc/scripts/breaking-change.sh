#!/usr/bin/env bash
# Proves that every kind of contract break is caught, and by which step. Each scenario applies its patch
# (scripts/scenarios/<n>-*.patch) to a throwaway copy of the repository root files and this subproject, gives the
# changed contract its own version (1.2.0-scenario) and runs the one check that must catch it.
#
#   breaking-change.sh baseline      all checks on the unchanged copy: must pass
#   breaking-change.sh 1..5          one scenario: exit 0 only when its check failed for the expected reason
#   breaking-change.sh all           baseline, then 1..5
#
# Exit codes: 0 caught (or baseline green), 1 not caught / baseline red, 2 the patch no longer applies (the code
# drifted: regenerate the scenario — never count it as caught). Needs Docker (buf, AsyncAPI CLI, Testcontainers).
# SCENARIO_DIR overrides the patch folder (used by test/breaking-change-selftest.sh).
set -euo pipefail

SUB="$(cd "$(dirname "$0")/.." && pwd)"
REPO="$(cd "$SUB/.." && pwd)"
NAME="$(basename "$SUB")"
SCENARIO_DIR="${SCENARIO_DIR:-$SUB/scripts/scenarios}"
MVN_FLAGS=(-B -q -Dspotless.check.skip=true -Djacoco.skip=true)

# A copy of what a build needs: root POM and wrapper, this subproject without build output.
copy() {
    local work="$1"
    cp "$REPO/pom.xml" "$REPO/mvnw" "$work/"
    cp -r "$REPO/.mvn" "$work/"
    (cd "$SUB" && tar --exclude='./*/target' --exclude='./target' -cf - .) | (mkdir -p "$work/$NAME" && cd "$work/$NAME" && tar -xf -)
}

# The changed contract is a new version; the provider is rebuilt against it, the pact stays the consumer's.
bump() {
    local dir="$1"
    sed -i '0,/<version>1.1.0<\/version>/s//<version>1.2.0-scenario<\/version>/' "$dir/contracts/pom.xml"
    sed -i 's|<contracts.version>1.1.0</contracts.version>|<contracts.version>1.2.0-scenario</contracts.version>|' \
        "$dir/forge-service/pom.xml"
}

check_proto() { "$1/scripts/check-proto.sh" > "$2" 2>&1; }
check_asyncapi() { "$1/scripts/check-asyncapi.sh" > "$2" 2>&1; }
check_registry() {
    (cd "$1/.." && ./mvnw "${MVN_FLAGS[@]}" -f "$NAME/pom.xml" -pl contracts verify \
        -Dtest=None -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=SchemaCompatibilityIT) > "$2" 2>&1
}
check_pact() {
    (cd "$1/.." && ./mvnw "${MVN_FLAGS[@]}" -f "$NAME/pom.xml" -pl contracts,forge-service verify \
        -Dtest=QuotePactProviderTest -Dsurefire.failIfNoSpecifiedTests=false -DskipITs) > "$2" 2>&1
}

baseline() {
    local work log
    work="$(mktemp -d)"
    log="$work/check.log"
    copy "$work"
    local dir="$work/$NAME" failed=0
    for check in check_proto check_asyncapi check_registry check_pact; do
        if $check "$dir" "$log"; then
            echo "baseline: $check OK"
        else
            echo "baseline: $check FAILED" >&2
            tail -40 "$log" >&2
            failed=1
        fi
    done
    rm -rf "$work"
    return $failed
}

# expect_caught <dir> <log> <check> <step name> <evidence>... — the check must fail, and its output must contain every
# piece of evidence: the failing test alone is not enough (a missing pact file or a dead Docker fails it too).
expect_caught() {
    local dir="$1" log="$2" check="$3" step="$4"
    shift 4
    if $check "$dir" "$log"; then
        echo "NOT CAUGHT: $check passed" >&2
        tail -40 "$log" >&2
        return 1
    fi
    local evidence
    for evidence in "$@"; do
        if ! grep -q -- "$evidence" "$log"; then
            echo "FAILED FOR ANOTHER REASON ($check failed, but '$evidence' is not in its output)" >&2
            tail -40 "$log" >&2
            return 1
        fi
    done
    if grep -q "COMPILATION ERROR" "$log"; then
        echo "FAILED FOR ANOTHER REASON (compilation error)" >&2
        tail -40 "$log" >&2
        return 1
    fi
    echo "CAUGHT by $step"
}

scenario() {
    local n="$1" patch work log dir result=0
    patch="$(ls "$SCENARIO_DIR"/"$n"-*.patch 2> /dev/null | head -1 || true)"
    [[ -n "$patch" ]] || { echo "no scenario $n in $SCENARIO_DIR" >&2; return 1; }
    work="$(mktemp -d)"
    log="$work/check.log"
    copy "$work"
    dir="$work/$NAME"
    echo "scenario $n: $(head -1 "$patch" | sed 's/^# Scenario [0-9]: //')"
    if ! (cd "$dir" && git apply --check "$patch" 2> "$log"); then
        echo "SCENARIO DRIFTED: $(basename "$patch") no longer applies" >&2
        cat "$log" >&2
        rm -rf "$work"
        return 2
    fi
    (cd "$dir" && git apply "$patch")
    bump "$dir"
    case "$n" in
        1) expect_caught "$dir" "$log" check_proto "buf breaking (WIRE), contracts build" "price_cents" || result=$? ;;
        2)
            if check_proto "$dir" "$log"; then
                echo "  buf breaking: passes (wire-compatible, number reserved)"
                expect_caught "$dir" "$log" check_pact "Pact provider verification, forge-service build" \
                    "QuotePactProviderTest" "lead_time_days" || result=$?
            else
                echo "UNEXPECTED: buf breaking failed — the scenario is meant to be wire-compatible" >&2
                tail -20 "$log" >&2
                result=1
            fi
            ;;
        3 | 4) expect_caught "$dir" "$log" check_registry "Apicurio BACKWARD rule, schema registration" \
            "currentSchemasAreBackwardCompatibleWithReleased" "409" || result=$? ;;
        5) expect_caught "$dir" "$log" check_asyncapi "AsyncAPI validation, CI" "forge_event_v2.avsc" || result=$? ;;
        *) echo "unknown scenario $n" >&2 && result=1 ;;
    esac
    rm -rf "$work"
    return $result
}

case "${1:-}" in
    baseline) baseline ;;
    [1-5]) scenario "$1" ;;
    all)
        baseline
        failed=0
        for n in 1 2 3 4 5; do
            scenario "$n" || failed=1
        done
        exit $failed
        ;;
    *)
        echo "usage: $0 baseline|1|2|3|4|5|all" >&2
        exit 64
        ;;
esac

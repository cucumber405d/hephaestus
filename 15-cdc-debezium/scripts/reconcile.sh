#!/usr/bin/env bash
# Every accepted custom order (it got a quote, so a work order request was written) has its work order in Forge.
# Usage: reconcile.sh <orders.txt>   (one order id per line, as the drill placed them)
# Prints "accepted=<n> present=<n> missing=<n>"; exit 0 only when missing=0.
# RECONCILE_FAKE_DIR: read ordering.txt / forge.txt from there instead of the databases (test/reconcile-fixture.sh).
set -euo pipefail
# Git Bash: do not rewrite container paths such as /opt/kafka/bin into C:/...
export MSYS_NO_PATHCONV=1

ORDERS="${1:?orders file}"
cd "$(dirname "$0")/.."   # 15-cdc-debezium
compose() { docker compose -f compose.yaml -p hephaestus-15 "$@"; }

ids=$(grep -E '^[0-9a-f-]{36}$' "$ORDERS" | sed "s/.*/'&'/" | paste -sd, - || true)
if [[ -n "${RECONCILE_FAKE_DIR:-}" ]]; then
    accepted=$(cat "$RECONCILE_FAKE_DIR/ordering.txt")
    present=$(cat "$RECONCILE_FAKE_DIR/forge.txt")
elif [[ -z "$ids" ]]; then
    accepted=""
    present=""
else
    accepted=$(compose exec -T postgres-ordering psql -U ordering -d ordering -Atc \
        "select id from ordering.customer_order where quote_id is not null and id in ($ids)")
    present=$(compose exec -T postgres-forge psql -U forge -d forge -Atc \
        "select order_id from work_order where order_id in ($ids)")
fi

python3 - "$accepted" "$present" <<'PY'
import sys
accepted = {line.strip() for line in sys.argv[1].splitlines() if line.strip()}
present = {line.strip() for line in sys.argv[2].splitlines() if line.strip()}
missing = accepted - present
print(f"accepted={len(accepted)} present={len(accepted & present)} missing={len(missing)}")
sys.exit(1 if missing else 0)
PY

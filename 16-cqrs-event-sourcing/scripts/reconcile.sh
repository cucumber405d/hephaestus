#!/usr/bin/env bash
# Every accepted custom order (it got a quote, so a work order request was written) has exactly one work order in
# Forge: one WorkOrderRequested in the journal (forge.event) — none is a lost request, two a broken idempotency.
# Usage: reconcile.sh <orders.txt>   (one order id per line, as the drill placed them)
# Prints "accepted=<n> present=<n> missing=<n> duplicated=<n>"; exit 0 only when missing=0 and duplicated=0.
# RECONCILE_FAKE_DIR: read ordering.txt / forge.txt from there instead of the databases (test/reconcile-fixture.sh).
set -euo pipefail

ORDERS="${1:?orders file}"
cd "$(dirname "$0")/.."   # 16-cqrs-event-sourcing
# Git Bash: MSYS_NO_PATHCONV keeps container paths (/opt/kafka/bin) from becoming C:/... (only here: curl
# on the host needs /dev/null translated)
compose() { MSYS_NO_PATHCONV=1 docker compose -f compose.yaml -p hephaestus-16 "$@"; }

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
        "select payload->>'orderId' from forge.event where type = 'WorkOrderRequested'
         and payload->>'orderId' in ($ids)")
fi

python3 - "$accepted" "$present" <<'PY2'
import collections
import sys
accepted = {line.strip() for line in sys.argv[1].splitlines() if line.strip()}
requests = collections.Counter(line.strip() for line in sys.argv[2].splitlines() if line.strip())
missing = accepted - set(requests)
duplicated = {order for order in accepted if requests[order] > 1}
print(f"accepted={len(accepted)} present={len(accepted & set(requests))} missing={len(missing)} "
      f"duplicated={len(duplicated)}")
for order in sorted(missing):
    print(f"missing    {order}")
for order in sorted(duplicated):
    print(f"duplicated {order} ({requests[order]} requests)")
sys.exit(1 if missing or duplicated else 0)
PY2

#!/usr/bin/env bash
# Builds and measures hephaestus/catalog:<flavor> images; prints one Markdown table to stdout.
#   03-containers-ci/scripts/compare-images.sh [buildpacks] [jib] [dockerfile] [dockerfile-jlink]
# Columns: image size, layer count, build time, rebuild time after a one-class change and how many layers changed,
# time from "docker run" to readiness UP, Spring's own "Started ... in N seconds", container memory at idle.
# Base images and Maven dependencies are expected to be cached already (run build-images.sh once): the build time is
# a warm build, not a cold one. Progress goes to stderr, the table to stdout.
set -euo pipefail

cd "$(dirname "$0")/.."   # 03-containers-ci
if [ $# -eq 0 ]; then
    set -- buildpacks jib dockerfile dockerfile-jlink
fi

PROJECT=hephaestus-compare
NETWORK="${PROJECT}_default"
RUN_NAME=catalog-compare
MARKER=catalog/src/main/java/de/ibikus/appa/hephaestus/containersci/catalog/BuildMarker.java

log() { echo "$*" >&2; }
now() { date +%s.%N; }
elapsed() { awk -v a="$1" -v b="$2" 'BEGIN { printf "%.1f", b - a }'; }
layers() { docker image inspect -f '{{range .RootFS.Layers}}{{println .}}{{end}}' "$1"; }

cleanup() {
    rm -f "$MARKER"
    docker rm -f "$RUN_NAME" >/dev/null 2>&1 || true
    docker compose -p "$PROJECT" down >/dev/null 2>&1 || true
}
trap cleanup EXIT

build() { scripts/build-images.sh "$1" >&2; }

measure_run() {
    local image=$1 start port ready_s started_s memory
    start=$(now)
    docker run -d --name "$RUN_NAME" --network "$NETWORK" --memory 768m --cpus 1 -p 8080 \
        -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/catalog \
        -e SPRING_DATASOURCE_USERNAME=catalog -e SPRING_DATASOURCE_PASSWORD=catalog \
        "$image" >/dev/null
    port=$(docker port "$RUN_NAME" 8080/tcp | head -1 | sed 's/.*://')
    for _ in $(seq 1 600); do
        if curl -fs "http://localhost:$port/actuator/health/readiness" >/dev/null 2>&1; then
            break
        fi
        sleep 0.2
    done
    ready_s=$(elapsed "$start" "$(now)")
    started_s=$(docker logs "$RUN_NAME" 2>&1 \
        | sed -n 's/.*Started CatalogApplication in \([0-9.]*\) seconds.*/\1/p' | head -1)
    sleep 10
    memory=$(docker stats --no-stream --format '{{.MemUsage}}' "$RUN_NAME" | awk '{ print $1 }')
    docker rm -f "$RUN_NAME" >/dev/null
    echo "$ready_s|${started_s:-?}|$memory"
}

log "==> PostgreSQL ($PROJECT)"
docker compose -p "$PROJECT" up -d --wait postgres >&2

echo "| image | size MB | layers | build s | rebuild s | rebuilt layers | ready s | started in s | memory |"
echo "|---|---|---|---|---|---|---|---|---|"
for flavor in "$@"; do
    image="hephaestus/catalog:$flavor"
    log "==> $image: build"
    start=$(now); build "$flavor"; build_s=$(elapsed "$start" "$(now)")
    size_mb=$(docker image inspect -f '{{.Size}}' "$image" | awk '{ printf "%.0f", $1 / 1000000 }')
    before=$(layers "$image")
    layer_count=$(echo "$before" | grep -c .)

    log "==> $image: rebuild after a one-class change"
    printf 'package de.ibikus.appa.hephaestus.containersci.catalog;\n\n/** Temporary class written by compare-images.sh. */\nfinal class BuildMarker {\n    static final long VALUE = %sL;\n\n    private BuildMarker() {}\n}\n' \
        "$RANDOM$RANDOM" > "$MARKER"
    start=$(now); build "$flavor"; rebuild_s=$(elapsed "$start" "$(now)")
    after=$(layers "$image")
    changed=$(diff <(echo "$before") <(echo "$after") | grep -c '^>' || true)

    log "==> $image: restore the clean image"
    rm -f "$MARKER"
    build "$flavor"

    log "==> $image: run"
    IFS='|' read -r ready_s started_s memory <<<"$(measure_run "$image")"
    echo "| $flavor | $size_mb | $layer_count | $build_s | $rebuild_s | $changed | $ready_s | $started_s | $memory |"
done

#!/usr/bin/env bash
# Builds hephaestus/catalog:<flavor> for the given flavors (default: all four).
#   03-containers-ci/scripts/build-images.sh [buildpacks] [jib] [dockerfile] [dockerfile-jlink]
# Needs JDK 25 and a running Docker daemon. IMAGE_REVISION sets the revision label (default: git HEAD).
set -euo pipefail

cd "$(dirname "$0")/.."   # 03-containers-ci
# Format checks belong to verify; image builds (and the temporary class of compare-images.sh) skip them
MVNW=(../mvnw -B -ntp -f pom.xml -Dspotless.check.skip=true)
REVISION="${IMAGE_REVISION:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"

# Base images come from the reactor POM, the single source of truth for versions
prop() { sed -n "s:.*<$1>\(.*\)</$1>.*:\1:p" pom.xml | head -1; }

if [ $# -eq 0 ]; then
    set -- buildpacks jib dockerfile dockerfile-jlink
fi

jar_built=false
ensure_jar() {
    if [ "$jar_built" = false ]; then
        "${MVNW[@]}" package -DskipTests
        jar_built=true
    fi
}

for flavor in "$@"; do
    echo "==> hephaestus/catalog:$flavor"
    case "$flavor" in
        buildpacks)
            "${MVNW[@]}" -pl catalog spring-boot:build-image -DskipTests -Dimage.revision="$REVISION"
            ;;
        jib)
            "${MVNW[@]}" -pl catalog package jib:dockerBuild -DskipTests -Dimage.revision="$REVISION"
            ;;
        dockerfile)
            ensure_jar
            docker build --build-arg JRE_IMAGE="$(prop jre.image)" --build-arg REVISION="$REVISION" \
                -t hephaestus/catalog:dockerfile catalog
            ;;
        dockerfile-jlink)
            ensure_jar
            docker build -f catalog/Dockerfile.jlink \
                --build-arg JDK_IMAGE="$(prop jdk.image)" --build-arg RUNTIME_IMAGE="$(prop jlink.runtime.image)" \
                --build-arg REVISION="$REVISION" -t hephaestus/catalog:dockerfile-jlink catalog
            ;;
        *)
            echo "Unknown flavor: $flavor (expected buildpacks, jib, dockerfile, dockerfile-jlink)" >&2
            exit 2
            ;;
    esac
done

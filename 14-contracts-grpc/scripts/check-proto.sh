#!/usr/bin/env bash
# buf breaking (WIRE) between the current quote.proto and the released 1.0.0 copy. Needs Docker.
set -euo pipefail

BUF_VERSION="${BUF_VERSION:-1.74.0}"
cd "$(dirname "$0")/../contracts"
docker run --rm -v "$PWD:/workspace" -w /workspace "bufbuild/buf:$BUF_VERSION" \
    breaking src/main/proto --against released/1.0.0/proto
echo "buf breaking: OK"

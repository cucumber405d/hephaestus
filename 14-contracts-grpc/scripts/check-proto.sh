#!/usr/bin/env bash
# buf breaking (WIRE) between the current quote.proto and the released 1.0.0 copy. Needs Docker.
set -euo pipefail
# Git Bash (MINGW) rewrites /paths in docker arguments into C:/…/Git/… — keep them as written
export MSYS_NO_PATHCONV=1

BUF_VERSION="${BUF_VERSION:-1.74.0}"
cd "$(dirname "$0")/../contracts"
# Docker Desktop on Windows needs C:/… for a bind mount; pwd -W gives it in Git Bash, plain pwd elsewhere
HOST_DIR="$(pwd -W 2> /dev/null || pwd)"
# format=dir: never read as a remote module reference
docker run --rm -v "$HOST_DIR:/workspace" -w /workspace "bufbuild/buf:$BUF_VERSION" \
    breaking 'src/main/proto#format=dir' --against 'released/1.0.0/proto#format=dir'
echo "buf breaking: OK"

#!/usr/bin/env bash
# Deletes the kind cluster "hephaestus-04" with everything in it (including the PostgreSQL volume).
set -euo pipefail
kind delete cluster --name hephaestus-04

#!/usr/bin/env bash
# Deletes the shared platform cluster "hephaestus" with everything in it.
set -euo pipefail
kind delete cluster --name hephaestus

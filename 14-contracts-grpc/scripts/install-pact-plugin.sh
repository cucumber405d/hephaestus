#!/usr/bin/env bash
# Installs the Pact protobuf/gRPC plugin (a native binary from GitHub, not from Maven Central) where pact-jvm looks
# for it: ~/.pact/plugins/protobuf-<version>/. No-op when that version is present.
# PACT_PROTOBUF_PLUGIN_VERSION overrides the pinned version; "latest" takes the newest GitHub release.
set -euo pipefail

REPO=pactflow/pact-protobuf-plugin
# Pinned (2026-11-24): pact-jvm records the plugin version in the pact file, so a new release would change it
VERSION="${PACT_PROTOBUF_PLUGIN_VERSION:-0.8.0}"
# GH_TOKEN (CI): authenticated API calls, no anonymous rate limit
AUTH=()
[[ -n "${GH_TOKEN:-}" ]] && AUTH=(-H "Authorization: Bearer $GH_TOKEN")
if [[ "$VERSION" == latest ]]; then
    VERSION=$(curl -fsSL "${AUTH[@]}" "https://api.github.com/repos/$REPO/releases/latest" \
        | sed -n 's/.*"tag_name": *"v-\([^"]*\)".*/\1/p' | head -1)
    [[ -n "$VERSION" ]] || { echo "could not resolve the latest $REPO release" >&2; exit 1; }
fi

DIR="$HOME/.pact/plugins/protobuf-$VERSION"
if [[ -x "$DIR/pact-protobuf-plugin" && -f "$DIR/pact-plugin.json" ]]; then
    echo "pact protobuf plugin $VERSION already installed in $DIR"
    exit 0
fi

case "$(uname -s)" in
    Linux) OS=linux ;;
    Darwin) OS=osx ;;
    *) echo "unsupported OS $(uname -s); use WSL2 or install with pact-plugin-cli" >&2; exit 1 ;;
esac
case "$(uname -m)" in
    x86_64 | amd64) ARCH=x86_64 ;;
    arm64 | aarch64) ARCH=aarch64 ;;
    *) echo "unsupported architecture $(uname -m)" >&2; exit 1 ;;
esac

BASE="https://github.com/$REPO/releases/download/v-$VERSION"
mkdir -p "$DIR"
curl -fsSL -o "$DIR/pact-plugin.json" "$BASE/pact-plugin.json"
curl -fsSL "$BASE/pact-protobuf-plugin-$OS-$ARCH.gz" | gunzip > "$DIR/pact-protobuf-plugin"
chmod +x "$DIR/pact-protobuf-plugin"
echo "pact protobuf plugin $VERSION installed in $DIR"

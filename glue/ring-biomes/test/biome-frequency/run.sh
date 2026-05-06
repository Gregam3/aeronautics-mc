#!/usr/bin/env bash
# Sample biome frequency at world spawn against the live mod stack.
# Reuses the staged NeoForge server in $STAGING (default /tmp/aero-audit-server).
# Run ../ore-density/scripts/bootstrap-server.sh first if it doesn't exist.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

STAGING="${STAGING:-/tmp/aero-audit-server}"
PRISM_INSTANCE="${PRISM_INSTANCE:-$HOME/.local/share/PrismLauncher/instances/1.21.1/minecraft}"
NEOFORGE_VERSION="${NEOFORGE_VERSION:-21.1.227}"
SEED="${SEED:-3735928559}"

bash "$DIR/../ore-density/scripts/bootstrap-server.sh" "$STAGING" "$NEOFORGE_VERSION"

python3 "$DIR/scripts/verify.py" \
    --staging "$STAGING" \
    --instance "$PRISM_INSTANCE" \
    --seed "$SEED" \
    "$@"

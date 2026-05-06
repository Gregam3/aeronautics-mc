#!/usr/bin/env bash
# Boot a NeoForge dedicated server with our mod stack, force-load chunks at a
# dozen sample points, and verify each one's biome resolves to the tier the
# Voronoi+jitter math predicts.
#
# Reuses the bootstrap-server.sh from ../ore-density/scripts/, since it's the
# same NeoForge install regardless of what we're testing.
#
# Exit code: 0 = all sample points match expected tier (or landed in
# unclassified ocean), 1 = at least one point resolved to the wrong tier.
#
# Env overrides:
#   STAGING            staging dir for the NeoForge server (default /tmp/aero-audit-server)
#   PRISM_INSTANCE     Prism MC instance dir (default ~/.local/share/.../1.21.1/minecraft)
#   NEOFORGE_VERSION   NeoForge dedicated server version (default 21.1.227)
#   SEED               level-seed (default 0xDEADBEEF = 3735928559)

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

STAGING="${STAGING:-/tmp/aero-audit-server}"
PRISM_INSTANCE="${PRISM_INSTANCE:-$HOME/.local/share/PrismLauncher/instances/1.21.1/minecraft}"
NEOFORGE_VERSION="${NEOFORGE_VERSION:-21.1.227}"
SEED="${SEED:-3735928559}"

echo "[run] === bootstrap NeoForge server ==="
bash "$DIR/../ore-density/scripts/bootstrap-server.sh" "$STAGING" "$NEOFORGE_VERSION"

echo
echo "[run] === verify biome tiers ==="
python3 "$DIR/scripts/verify.py" \
    --staging "$STAGING" \
    --instance "$PRISM_INSTANCE" \
    --seed "$SEED"

#!/usr/bin/env bash
# Top-level entry point for the ore-density test suite.
#
# Pipeline:
#   1. Ensure NeoForge server is installed (bootstrap if needed).
#   2. Run pregen-driver.py to generate a deterministic test world.
#   3. Run analyze.py to assert per-tier ore ratios match expected/tier-ratios.yaml.
#
# Exit code: passes through analyze.py's (0=pass, 1=fail, 2=config error).
#
# Env overrides:
#   STAGING            staging dir for the NeoForge server (default /tmp/aero-audit-server)
#   PRISM_INSTANCE     Prism MC instance dir (default ~/.local/share/.../aeronautics-1.21.1/minecraft)
#   NEOFORGE_VERSION   NeoForge dedicated server version (default 21.1.227)
#   SEED               level-seed (default 0xDEADBEEF = 3735928559)
#   RADIUS             per-tier pregen radius in blocks (default 256)
#   SKIP_PREGEN=1      skip pregen, only run analyze.py against an existing world
#                      (useful when iterating on the analyzer)

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

STAGING="${STAGING:-/tmp/aero-audit-server}"
PRISM_INSTANCE="${PRISM_INSTANCE:-$HOME/.local/share/PrismLauncher/instances/1.21.1/minecraft}"
NEOFORGE_VERSION="${NEOFORGE_VERSION:-21.1.227}"
SEED="${SEED:-3735928559}"
RADIUS="${RADIUS:-256}"
SKIP_PREGEN="${SKIP_PREGEN:-0}"

WORLD="$STAGING/audit-world"

if [[ "$SKIP_PREGEN" != "1" ]]; then
    echo "[run] === bootstrap NeoForge server ==="
    bash "$DIR/scripts/bootstrap-server.sh" "$STAGING" "$NEOFORGE_VERSION"

    echo
    echo "[run] === pregen test world ==="
    python3 "$DIR/scripts/pregen-driver.py" \
        --staging "$STAGING" \
        --instance "$PRISM_INSTANCE" \
        --seed "$SEED" \
        --radius "$RADIUS"
fi

if [[ ! -d "$WORLD" ]]; then
    echo "[run] !! no world at $WORLD — pregen failed or was skipped without prior run" >&2
    exit 2
fi

echo
echo "[run] === analyze ore distribution ==="
python3 "$DIR/scripts/analyze.py" --world "$WORLD"

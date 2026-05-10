#!/usr/bin/env bash
# Stage mods + datapacks from Prism into the test harness, boot fresh world,
# force-load multiple patches across radii, and run world_audit.py.
#
# Usage:
#   bash run-audit.sh           — full cycle: stage, boot, audit
#   bash run-audit.sh --replay  — just re-audit whatever's already there
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$DIR/../../.." && pwd)"
PRISM="$HOME/.local/share/PrismLauncher/instances/1.21.1/minecraft"
STAGING="${STAGING:-/tmp/aero-s3-karos-test}"

if [[ "${1:-}" == "--replay" ]]; then
    python3 "$DIR/scripts/world_audit.py" --staging "$STAGING" --replay
    exit $?
fi

echo "[run-audit] killing prior procs"
pkill -9 -f neoforge-21 2>/dev/null || true
sleep 4

# Bootstrap NeoForge if needed (idempotent)
bash "$REPO/glue/ring-biomes/test/ore-density/scripts/bootstrap-server.sh" "$STAGING" 21.1.227

echo "[run-audit] staging mods from Prism (server-safe filter)"
mkdir -p "$STAGING/mods"
rm -f "$STAGING/mods/"*.jar
shopt -s nullglob
for jar in "$PRISM/mods/"*.jar; do
    name=$(basename "$jar")
    case "$name" in
        # Client-only mods that crash a dedicated server
        iris-*|sodium-*|ImmediatelyFast-*|xaero*|bobberdetector*|jei-*|\
        Chunky-*|*\.disabled) continue ;;
    esac
    cp "$jar" "$STAGING/mods/"
done
shopt -u nullglob
echo "[run-audit] staged $(ls "$STAGING/mods/" | wc -l) mods"

echo "[run-audit] staging Tectonic config"
mkdir -p "$STAGING/config"
cp "$PRISM/config/tectonic.json" "$STAGING/config/"

echo "[run-audit] staging Paxi datapacks (from config/paxi/datapacks; the karos"
echo "             biome mask + caero_disable_loops live here)"
rm -rf "$STAGING/config/paxi"
mkdir -p "$STAGING/config/paxi"
cp -r "$PRISM/config/paxi/datapacks" "$STAGING/config/paxi/datapacks"
# Paxi load order if present, else default empty.
[[ -f "$PRISM/config/paxi/datapack_load_order.json" ]] && \
    cp "$PRISM/config/paxi/datapack_load_order.json" "$STAGING/config/paxi/"
echo "[run-audit] staged paxi datapacks: $(ls "$STAGING/config/paxi/datapacks/")"

echo "[run-audit] staging Prism config dir for any other mod configs"
# Caero glue mods may have configs that affect their behaviour
for cfg in "$PRISM/config/"*.toml "$PRISM/config/"*.json; do
    [[ -f "$cfg" ]] && cp "$cfg" "$STAGING/config/" || true
done

echo "[run-audit] running world_audit (--boot)"
python3 "$DIR/scripts/world_audit.py" --staging "$STAGING" --boot --gen-time 180

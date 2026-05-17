#!/usr/bin/env bash
# Stage mods + datapacks from Prism into the test harness, boot fresh world,
# and verify the themed-region biome layout via in-game `/execute if biome`
# + `/locate biome` predicates (no chunk forceload needed).
#
# Usage:
#   bash run-audit.sh
#
# Runs against fixed seed 12345 so results are reproducible across runs.
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$DIR/../../.." && pwd)"
PRISM="$HOME/.local/share/PrismLauncher/instances/1.21.1/minecraft"
STAGING="${STAGING:-/tmp/aero-s3-karos-test}"

echo "[run-audit] killing prior procs"
pkill -9 -f "neoforged/neoforge/" 2>/dev/null || true
# Belt-and-braces: kill any java whose cwd is our staging dir.
for pid in $(pgrep -f "java .*unix_args" 2>/dev/null); do
    cwd=$(readlink "/proc/$pid/cwd" 2>/dev/null) || continue
    case "$cwd" in /tmp/aero-s3-karos-test*) kill -9 "$pid" 2>/dev/null;; esac
done
sleep 4

# Bootstrap NeoForge if needed (idempotent).
bash "$REPO/glue/ring-biomes/test/ore-density/scripts/bootstrap-server.sh" "$STAGING" 21.1.227

# Server.properties tuning. We don't forceload chunks anymore (biome predicates
# query the biome source directly), so default view/sim distances are fine —
# but pin seed + disable tick watchdog for safety.
if [[ -f "$STAGING/server.properties" ]]; then
    sed -i 's/^max-tick-time=.*/max-tick-time=-1/' "$STAGING/server.properties"
    sed -i 's/^level-seed=.*/level-seed=12345/' "$STAGING/server.properties"
    sed -i 's/^simulation-distance=.*/simulation-distance=2/' "$STAGING/server.properties"
    sed -i 's/^view-distance=.*/view-distance=2/' "$STAGING/server.properties"
    sed -i 's/^spawn-protection=.*/spawn-protection=0/' "$STAGING/server.properties"
fi

echo "[run-audit] staging mods from Prism (denylist of client/perf-only)"
mkdir -p "$STAGING/mods"
rm -f "$STAGING/mods/"*.jar
shopt -s nullglob
for jar in "$PRISM/mods/"*.jar; do
    name=$(basename "$jar")
    case "$name" in
        iris-*|sodium-*|ImmediatelyFast-*|xaero*|bobberdetector*|jei-*|\
        Chunky-*|DistantHorizons-*|*\.disabled) continue ;;
    esac
    cp "$jar" "$STAGING/mods/"
done
shopt -u nullglob
echo "[run-audit] staged $(ls "$STAGING/mods/" | wc -l) mods"

echo "[run-audit] staging Tectonic config"
mkdir -p "$STAGING/config"
[[ -f "$PRISM/config/tectonic.json" ]] && cp "$PRISM/config/tectonic.json" "$STAGING/config/"

echo "[run-audit] staging Paxi datapacks"
rm -rf "$STAGING/config/paxi"
mkdir -p "$STAGING/config/paxi"
cp -r "$PRISM/config/paxi/datapacks" "$STAGING/config/paxi/datapacks"
[[ -f "$PRISM/config/paxi/datapack_load_order.json" ]] && \
    cp "$PRISM/config/paxi/datapack_load_order.json" "$STAGING/config/paxi/"
echo "[run-audit] paxi datapacks: $(ls "$STAGING/config/paxi/datapacks/")"

echo "[run-audit] staging miscellaneous mod configs"
for cfg in "$PRISM/config/"*.toml "$PRISM/config/"*.json; do
    [[ -f "$cfg" ]] && cp "$cfg" "$STAGING/config/" || true
done

echo "[run-audit] launching audit"
python3 "$DIR/scripts/audit.py" --staging "$STAGING"

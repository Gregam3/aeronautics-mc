#!/usr/bin/env bash
# Sync the season3 datapacks from the repo into Paxi's datapack folder in
# the active PrismLauncher instance.
#
# 2026-05-16: the painted-PNG karos approach was scrapped. karos-datapack
# now only ships the keepers (caero_karos:ancient_jungle custom biome +
# Tree Giant scoping of giant_jungle_tree to that biome). All worldgen
# region logic lives in the caero_rings mod jar — there is no
# karos-terrain-overrides datapack anymore.
#
# Paxi loads datapacks at server start and copies them per-world; it does
# not symlink to or live-watch the source. So any edit to the repo's
# `season3/karos-datapack/` requires re-running this script before
# generating new chunks, otherwise Minecraft will keep using the stale
# copy from the last sync.
#
# Idempotent. Uses `rsync --delete` so removed files in the repo are
# reflected in paxi, and explicitly purges retired datapacks (e.g.
# karos-terrain-overrides) that may still be sitting in the Paxi dir
# from a previous sync.

set -euo pipefail
cd "$(dirname "$0")"

PRISM_INSTANCE="${PRISM_INSTANCE:-1.21.1}"
PAXI="$HOME/.local/share/PrismLauncher/instances/$PRISM_INSTANCE/minecraft/config/paxi/datapacks"

if [ ! -d "$PAXI" ]; then
    echo "❌ Paxi datapack dir not found: $PAXI"
    echo "   Override with PRISM_INSTANCE=<name>."
    exit 1
fi

# Datapacks we currently ship.
# - karos-datapack: ancient_jungle biome + Tree Giant scoping
# - karos-terrain-overrides: Lithostitched surface_rule + final_density wraps
#   that render nether/end biomes correctly when caero_rings places them
#   in the overworld.
ACTIVE=(karos-datapack karos-terrain-overrides)
for pack in "${ACTIVE[@]}"; do
    if [ ! -d "$pack" ]; then
        echo "⚠️  skipping $pack — not found in $(pwd)"
        continue
    fi
    echo "→ $pack"
    rsync -a --delete "$pack/" "$PAXI/$pack/"
done

echo "✅ datapacks synced to $PAXI"
echo "   Restart the server / quit-to-title before generating new chunks."

#!/usr/bin/env bash
# Boot a NeoForge 1.21.1 dedicated server with our caero_atlas fork (jar
# built from glue/caero_atlas/) + karos-datapack and verify the worldgen
# end-to-end via /locate biome and block-stack region NBT inspection.
#
# Env overrides:
#   STAGING            staging dir for the test server (default /tmp/aero-s3-karos-test)
#   NEOFORGE_VERSION   NeoForge dedicated server version (default 21.1.227)
#   SEED               level-seed (default 12345)

set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DIR/../../.." && pwd)"

STAGING="${STAGING:-/tmp/aero-s3-karos-test}"
NEOFORGE_VERSION="${NEOFORGE_VERSION:-21.1.227}"
SEED="${SEED:-12345}"

# Our fork. Build first if missing: cd glue/caero_atlas && ./gradlew :neoforge:build
FORK_JAR_GLOB="$REPO_ROOT/glue/caero_atlas/neoforge/build/libs/novoatlas-karos-neoforge-*[!sources][!shadow].jar"
FORK_JAR="$(ls $FORK_JAR_GLOB 2>/dev/null | grep -vE 'sources|shadow' | head -1 || true)"

LITHOSTITCHED_URL="https://cdn.modrinth.com/data/XaDC71GB/versions/3yrFEAmj/lithostitched-1.7.3-neoforge-21.1.jar"
LITHOSTITCHED_JAR="lithostitched-1.7.3-neoforge-21.1.jar"

DATAPACK_BIOME="$REPO_ROOT/season3/karos-datapack"
DATAPACK_TERRAIN="$REPO_ROOT/season3/karos-terrain-overrides"

echo "[run] === bootstrap NeoForge server ==="
bash "$REPO_ROOT/glue/ring-biomes/test/ore-density/scripts/bootstrap-server.sh" \
    "$STAGING" "$NEOFORGE_VERSION"

echo
echo "[run] === stage mods (caero_atlas fork) ==="
mkdir -p "$STAGING/mods"
# Wipe any stale upstream / fork jars so each run uses the freshest build
rm -f "$STAGING/mods/"novoatlas-*.jar "$STAGING/mods/"lithostitched-*.jar
if [[ -z "$FORK_JAR" || ! -f "$FORK_JAR" ]]; then
    echo "[run] ERROR: fork jar not found at $FORK_JAR_GLOB"
    echo "[run]        build it first:  (cd glue/caero_atlas && ./gradlew :neoforge:build)"
    exit 2
fi
cp "$FORK_JAR" "$STAGING/mods/"
echo "[run]   $FORK_JAR -> $STAGING/mods/"
# Lithostitched for surface-rule injection (block palette swap per biome)
if [[ ! -f "$STAGING/mods/$LITHOSTITCHED_JAR" ]]; then
    curl -fsSL -o "$STAGING/mods/$LITHOSTITCHED_JAR" "$LITHOSTITCHED_URL"
fi
ls "$STAGING/mods/"

echo
echo "[run] === stage datapacks ==="
mkdir -p "$STAGING/world/datapacks"
for src in "$DATAPACK_BIOME" "$DATAPACK_TERRAIN"; do
    name="$(basename "$src")"
    rm -rf "$STAGING/world/datapacks/$name"
    cp -r "$src" "$STAGING/world/datapacks/$name"
    echo "[run]   $src -> $STAGING/world/datapacks/$name"
done

echo
echo "[run] === verify worldgen ==="
python3 "$DIR/scripts/verify.py" \
    --staging "$STAGING" \
    --seed "$SEED"

#!/bin/bash
# Deploy caero_disable_loops datapack into the PrismLauncher instance via Paxi.
# Mirrors the pattern of glue/caero_specialization/deploy.sh.

set -euo pipefail
cd "$(dirname "$0")"

PACK_NAME="caero_disable_loops"
PRISM_INSTANCE="${PRISM_INSTANCE:-1.21.1}"
PAXI_DIR="$HOME/.local/share/PrismLauncher/instances/$PRISM_INSTANCE/minecraft/config/paxi/datapacks"
TARGET="$PAXI_DIR/$PACK_NAME"

if [ ! -d "$PAXI_DIR" ]; then
  echo "❌ Paxi datapack dir not found: $PAXI_DIR"
  echo "   Override with PRISM_INSTANCE=<name>"
  exit 1
fi

echo "📋 Syncing $PACK_NAME → $TARGET"
rm -rf "$TARGET"
mkdir -p "$TARGET"
cp -r pack.mcmeta data "$TARGET/"
echo "✅ Datapack deployed. In-game: /reload"

#!/bin/bash
# Build caero_vitality and deploy the jar into a PrismLauncher 1.21.1 NeoForge
# instance's mods folder. Mirrors glue/caero_claims/deploy.sh.

set -euo pipefail
cd "$(dirname "$0")"

MOD_ID="caero_vitality"
MOD_VERSION="$(awk -F= '/^mod_version=/ {print $2}' gradle.properties)"
PRISM_INSTANCE="${PRISM_INSTANCE:-1.21.1}"
MOD_DIR="$HOME/.local/share/PrismLauncher/instances/$PRISM_INSTANCE/minecraft/mods"
BUILD_JAR="build/libs/${MOD_ID}-${MOD_VERSION}.jar"
TARGET_JAR="$MOD_DIR/${MOD_ID}.jar"

echo "🔨 Building ${MOD_ID} ${MOD_VERSION}..."
./gradlew build

if [ ! -f "$BUILD_JAR" ]; then
    echo "❌ Expected jar not found at $BUILD_JAR"
    ls -la build/libs/ || true
    exit 1
fi

echo "✅ Build successful: $BUILD_JAR"

if [ ! -d "$MOD_DIR" ]; then
    echo "❌ PrismLauncher instance mods dir not found: $MOD_DIR"
    echo "   Override with PRISM_INSTANCE=<name>. Existing instances:"
    ls "$HOME/.local/share/PrismLauncher/instances/" 2>/dev/null || echo "   (none)"
    exit 1
fi

if [ -f "$TARGET_JAR" ]; then
    echo "🗑️  Removing old ${MOD_ID}.jar..."
    rm "$TARGET_JAR"
fi

echo "📋 Copying ${MOD_ID}.jar to $MOD_DIR..."
cp "$BUILD_JAR" "$TARGET_JAR"
echo "✅ Deployed to $TARGET_JAR"

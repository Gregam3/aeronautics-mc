#!/bin/bash

# Build caero_nether_atmosphere and deploy the jar into the running PrismLauncher
# 1.21.1 NeoForge instance.

set -euo pipefail

cd "$(dirname "$0")"

MOD_ID="caero_nether_atmosphere"
MOD_VERSION="$(awk -F= '/^mod_version=/ {print $2}' gradle.properties)"
PRISM_INSTANCE="${PRISM_INSTANCE:-1.21.1}"
MOD_DIR="$HOME/.local/share/PrismLauncher/instances/$PRISM_INSTANCE/minecraft/mods"
BUILD_JAR="build/libs/${MOD_ID}-${MOD_VERSION}.jar"
TARGET_JAR="$MOD_DIR/${MOD_ID}.jar"

echo "Building ${MOD_ID} ${MOD_VERSION}..."
./gradlew build

if [ ! -f "$BUILD_JAR" ]; then
    echo "Expected jar not found at $BUILD_JAR"
    ls -la build/libs/ || true
    exit 1
fi

if [ ! -d "$MOD_DIR" ]; then
    echo "PrismLauncher mods dir not found: $MOD_DIR"
    echo "Existing instances:"
    ls "$HOME/.local/share/PrismLauncher/instances/" 2>/dev/null || true
    exit 1
fi

[ -f "$TARGET_JAR" ] && rm "$TARGET_JAR"
cp "$BUILD_JAR" "$TARGET_JAR"

echo "Deployed to $TARGET_JAR"

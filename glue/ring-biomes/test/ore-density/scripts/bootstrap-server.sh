#!/usr/bin/env bash
# One-time setup: install a NeoForge dedicated server into <staging-dir>.
#
# Usage:
#   ./bootstrap-server.sh [staging-dir] [neoforge-version]
#
# Defaults:
#   staging-dir       = /tmp/aero-audit-server
#   neoforge-version  = 21.1.227   (matches the live server's runbook.md)
#
# Idempotent: if the run script already exists in <staging-dir>, exits 0.
# Network access required on first run.
set -euo pipefail

STAGING="${1:-/tmp/aero-audit-server}"
NEOFORGE_VERSION="${2:-21.1.227}"
INSTALLER_URL="https://maven.neoforged.net/releases/net/neoforged/neoforge/${NEOFORGE_VERSION}/neoforge-${NEOFORGE_VERSION}-installer.jar"

mkdir -p "$STAGING"

if [[ -f "$STAGING/run.sh" && -d "$STAGING/libraries" ]]; then
    echo "[bootstrap] NeoForge ${NEOFORGE_VERSION} already installed at $STAGING"
    exit 0
fi

INSTALLER="$STAGING/neoforge-${NEOFORGE_VERSION}-installer.jar"
if [[ ! -f "$INSTALLER" ]]; then
    echo "[bootstrap] downloading $INSTALLER_URL"
    curl -fsSL -o "$INSTALLER" "$INSTALLER_URL"
fi

echo "[bootstrap] installing NeoForge dedicated server -> $STAGING"
cd "$STAGING"
java -jar "$INSTALLER" --installServer "$STAGING"

# eula must be agreed manually in production; the test world is throwaway.
echo "eula=true" > "$STAGING/eula.txt"

echo "[bootstrap] done. run with: bash $STAGING/run.sh nogui"

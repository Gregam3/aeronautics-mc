#!/usr/bin/env bash
# Build the MkDocs site and deploy to the Hetzner box.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SERVER="${SERVER:-greg@46.225.17.145}"
REMOTE_ROOT="${REMOTE_ROOT:-/var/www/aero-wiki}"

# 1. Build (uses local venv if present, else system mkdocs)
if [[ -x .venv/bin/mkdocs ]]; then
  MKDOCS=.venv/bin/mkdocs
else
  MKDOCS=mkdocs
fi
"$MKDOCS" build --clean --strict 2>&1 | tail -5

# 2. Stage on the server, then sudo-rsync into the webroot
rsync -az --delete site/ "$SERVER:/tmp/aero-wiki-staging/"
ssh "$SERVER" "
  sudo rsync -a --delete /tmp/aero-wiki-staging/ $REMOTE_ROOT/
  sudo chown -R caddy:caddy $REMOTE_ROOT
  rm -rf /tmp/aero-wiki-staging
"

# 3. Smoke-test
echo "---"
curl -sS -o /dev/null -w 'GET / → HTTP %{http_code} | %{size_download}B\n' http://46.225.17.145/
echo "Deployed. Wiki is at http://46.225.17.145/"

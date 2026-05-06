# wiki/

Source for the public player-facing wiki at **http://46.225.17.145/**.

- **Generator:** [MkDocs Material](https://squidfunk.github.io/mkdocs-material/)
- **Source:** `docs/*.md` — plain GitHub-flavored markdown
- **Build output:** `site/` (gitignored)
- **Webserver:** Caddy on the Hetzner box. Setup + recreate steps live in
  `../runbook.md §12`.

## Edit & preview

```sh
# One-time: install mkdocs-material in a local venv
python3 -m venv .venv && .venv/bin/pip install mkdocs-material

# Live preview at http://127.0.0.1:8000
.venv/bin/mkdocs serve
```

Edit any `docs/*.md` and the preview reloads on save.

## Deploy

```sh
./deploy.sh
```

Builds, rsyncs to `/var/www/aero-wiki` on the server, smoke-tests with curl.
No Caddy restart needed — it serves static files.

## Pages

Listed in `mkdocs.yml`'s `nav:` — flat structure, one page per concept:

- `index.md` — landing
- `joining.md` — how to connect
- `world.md` — border, biome rings, travel rules
- `survival.md` — Encumbered, TAN, Nutritional Balance
- `claims.md` — `caero_claims` (wand + cost)
- `specialization.md` — `caero_specialization` (Refiners, quality, XP)
- `vitality.md` — `caero_vitality` (death penalty + restoration foods)
- `biome-tiers.md` — `caero_rings` (Voronoi tier system)
- `disabled.md` — what's removed (elytra, mending, infinite loops, etc.)
- `mods.md` — full mod list

## Scope

**Player-facing only.** Document what items/blocks/commands/mechanics *do*,
not why we picked them. Design rationale, decisions, and the four pillars
live in `../PLAN.md` and `../heuristics.md` and stay there.

## When to update

Any time a glue mod ships a player-visible change: new item, command,
quality multiplier, etc. Edit the relevant `docs/*.md`, run `./deploy.sh`.

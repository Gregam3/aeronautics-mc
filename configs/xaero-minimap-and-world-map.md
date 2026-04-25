# Xaero's Minimap + World Map server-enforced overrides

Replaces the planned **Glue Mod #3 — Navigator's Log** for v1: instead of
shipping a custom craftable item that toggles minimap visibility per-player,
we use Xaero's own built-in `minimap_item` / `map_item` gating to require a
held filled map for any map UI to render. Plus we kill the radar / player
tracking entirely so the map shows terrain only.

## What's enforced

- **No entity radar.** No mob dots, no item dots, no player dots.
- **No tracked-player markers** on either minimap or world map. Other players'
  positions are invisible regardless of party/ally status.
- **Map gated on holding `minecraft:filled_map`** in your inventory (any slot,
  not just hotbar). Without one, the minimap doesn't render and the world map
  (M key) won't open. Empty `minecraft:map` does NOT count — must be filled.

Pillar trace: heuristic #2 (transport matters — without a free always-on
minimap, navigation is a real cost; you have to make the maps you need),
heuristic #1 (trade — cartographers have a real product to sell because
filled maps are the gate to fast nav).

## Files written

For the local PrismLauncher instance the canonical paths are:

```
config/xaero/minimap/profiles/default.cfg          ← client default
config/xaero/minimap/server_profiles/default.cfg   ← what server enforces on connecting clients
config/xaero/world-map/profiles/default.cfg
config/xaero/world-map/server_profiles/default.cfg
```

The values to set in **all four** files (client profile and server profile
are kept aligned so a player who somehow bypasses enforcement still gets
the intended defaults):

### minimap (`profiles/default.cfg` *and* `server_profiles/default.cfg`)

```
minimap_item = minecraft:filled_map
display_radar = false
tracked_players_on_minimap = false
tracked_players_in_world = false
```

### world-map (`profiles/default.cfg` *and* `server_profiles/default.cfg`)

```
map_item = minecraft:filled_map
display_tracked_players = false
```

## Server enforcement notes

Xaero's enforces the server-profile values on connecting clients via the
`xaero.minimap.enforced_server_profile` permission node (configured in
`config/xaero/minimap/common.cfg` → `enforced_profile_permission_node`).
By default this enforces the "default" server profile on everyone. If a
permission system later grants a player the bypass node, they'll be
exempt — keep an eye on that when wiring up FTB Ranks / LuckPerms.

## Verifying

After server boot:
- Empty inventory → minimap doesn't render, M key doesn't open world map.
- Pick up a filled map → both render normally.
- Drop / inventory-out the map → both disappear within a tick.
- No mob/item/player dots in either UI regardless of inventory.

## Related decisions

- Xaero's chosen 2026-04-20 over JourneyMap because of native OPAC integration.
  See `mods.md`.
- Glue Mod #3 (Navigator's Log) deferred — `minimap_item` covers the same
  intent without needing a custom item. If we later want a *different* item
  (a craftable "Navigator's Log" gated on outer-biome mats), we just change
  the four config values. No glue mod needed unless we want behaviour Xaero's
  config can't express.

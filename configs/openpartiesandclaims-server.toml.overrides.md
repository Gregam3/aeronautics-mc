# OPAC server config overrides

OPAC's full server config is generated at first launch into:
`<instance>/config/openpartiesandclaims-server.toml`

Don't try to ship a sparse override file in `defaultconfigs/` —
NeoForge's ModConfigSpec validation rejects partial structures. OPAC's
real section nesting is `[serverConfig.claims]`, **not** the flat
`[claims]` you might guess; if you put the wrong header in, OPAC
silently regenerates the full config from defaults and renames your
file to `.bak`.

When provisioning a fresh server, do **either**:
1. Boot once with OPAC, then patch the generated TOML in place.
2. Pre-place a complete generated TOML (copy from a working instance)
   with our overrides applied.

## Required overrides

```toml
[serverConfig]
    [serverConfig.claims]
        # Default base allowance per player — was 500. Bonus chunks
        # granted via /caero_addplayerclaim or the future
        # Numismatics→OPAC bridge stack on top.
        # Pillar trace: heuristic #1 (reason to trade — coins as a
        # land-acquisition sink) and #4 (specialization — bigger
        # bases are coin-paid).
        maxPlayerClaims = 1
```

## Verifying the override took

After server boot, check `<instance>/config/openpartiesandclaims-server.toml`
contains the overridden value (`grep "maxPlayerClaims " ...`). If OPAC
regenerated defaults the line will read `maxPlayerClaims = 500` and
your override didn't apply — section header was probably wrong.

## Per-world side-effects

Some OPAC versions also write `<world>/serverconfig/openpartiesandclaims-server.toml`
mirroring the instance-level file. If both exist, keep them aligned to
avoid surprises. The instance-level is authoritative on initial boot.

## Granting more claim quota at runtime

Use the glue command:

```
/caero_addplayerclaim <player> <amount>
```

This bumps the player's `BONUS_CHUNK_CLAIMS` (per-player, additive on
top of `maxPlayerClaims`). Permission level 2; command-block-runnable
via the standard command-block + button + `@p` pattern.

## Related decisions

- Chosen 2026-04-20 in PLAN.md §3 — OPAC over FTB Chunks for native
  Xaero's minimap integration.
- `maxPlayerClaims = 1` chosen 2026-04-25 to enforce land as a
  coin-paid resource (heuristic #1 + #4).
- Numismatics→OPAC bridge planned in `glue/numismatics-opac-bridge/PLAN.md`;
  for v1, command blocks at the hub run `/caero_addplayerclaim` in
  response to coin deposits — full block-based bridge mod deferred.

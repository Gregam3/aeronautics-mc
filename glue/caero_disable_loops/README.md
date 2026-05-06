# caero_disable_loops

Datapack enforcing economy-principles principle #1 (resources are finite) and
#2 (economic primitives stay relevant).

Disables:
- Iron golem iron drops (poppies still drop)
- Born in Chaos craftable items (recipes only — mob drops kept intact)
- Mending sources (vanilla loot tables)
- Suspect Create cobble→iron chains (TODO — needs investigation)

Loaded via [Paxi](https://www.curseforge.com/minecraft/mc-mods/paxi) global
datapack folder.

## Deploy

```
./deploy.sh
```

Copies this datapack into the live PrismLauncher instance at
`config/paxi/datapacks/caero_disable_loops/`. Reload via `/reload` in-game.

## Status

See `TASKS.md` at project root for what's covered and what's pending.

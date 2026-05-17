# Welcome to Aeronautics

A small modded Minecraft server built around one big idea:
**get on a flying machine and go somewhere it actually matters to go.**

The world is bounded, the danger gets worse the further you travel, and the only
way to move serious cargo is to build something that flies. Self-sufficiency is
*possible* but inefficient on purpose — specialise in something, trade for the
rest, and the economy clicks into place.

---

## How to join

| | |
|---|---|
| **Server address** | `46.225.17.145` |
| **Voice chat** | included in the modpack — connects automatically |
| **Minecraft version** | 1.21.1 |
| **Loader** | NeoForge **21.1.227** |
| **Whitelist** | off — just connect |

You need to be running **the same mod folder** as the server (45 jars). All
mods are listed in §"Mod list" below. Easiest path: copy the modpack folder
Greg shares and drop it in your launcher's `mods/` and `config/` directories.

> **Heads up:** without all 45 mods on your client, the server will refuse the
> connection or you'll see "ghost holes" in builds where modded blocks should
> be. If you join and something looks empty/broken, you're missing a mod.

---

## The four pillars

Every rule on this server traces back to one of these four ideas. If something
on the server feels weird, this is usually why.

1. **There must be a reason to trade.** Resources are biome-locked or
   region-biased. Going solo on everything is slow on purpose. Specialise.
2. **There must be a reason for transport and infra.** The server is *about*
   airships. Anything that lets you skip travel (elytra, free portals,
   teleports) is removed or gated.
3. **The game gets harder the further out you go.** Spawn is safe and
   resource-poor. The edges of the world are lethal and lucrative. You feel the
   danger curve through *geography*, not just hours played.
4. **There must be a reason to specialise.** Generalists get outcompeted.
   Cooks, farmers, freighters, engineers — pick a lane.

---

## The world

- **Size:** 10,000 × 10,000 (a hard border at radius 5,000). You will *not* run
  out of map quickly, but you also can't fly forever.
- **Spawn / hub** at `0, 0` (overworld coords). Current world spawn point is
  `-136, 73, -162`.
- **Safe zone:** within 100 blocks of the spawn point, hostile mobs are
  silently removed. Build, trade, sleep, do business — nothing kills you here.
- **Biome rings (rough):**
  - 0–1,600 from origin: easy. Vanilla mob threats only.
  - 1,600–3,200: medium. Mowzie's regulars + Born-in-Chaos lighter variants.
  - 3,200–4,800: hard. Heavier modded hostiles, statted up by distance.
  - 4,800–5,000: border buffer.
- **Bosses are NOT ambient.** Cataclysm/Mowzie's bosses live inside their own
  dungeons (Ignis Altar, Wroughtnaut Chamber, Frozen Tomb, etc.). You walk
  into a fight on purpose.

---

## Restrictions / house rules

The "this is on purpose" list:

- **Elytra is unobtainable.** End-city loot tables overridden to empty pools.
  No crafting recipe exists. If you find one through some bug, give it to an
  admin.
- **You cannot make new nether portals.** Player-initiated portal formations
  are cancelled. Admins hand-place 3–5 large portals across the world; find
  them by exploration. (Existing portal blocks still work.)
- **Item weight matters.** The Encumbered mod gives you three encumbrance
  tiers — too much in your pockets and you stop sprinting; too much more and
  you barely move. This is why airships exist.
- **Survival pressure.** Tough as Nails adds thirst and body temperature.
  Outer biomes punish unprepared travel. Bring water, dress for the climate.
- **Food variety matters.** Nutritional Balance penalises eating the same
  thing repeatedly and rewards a varied diet. Trading for food categories you
  don't produce is part of the loop.
- **Currency and claims.** Numismatics provides coins; coin-buying claim
  quota is on the roadmap (glue mod #1). Until that's in, claims are
  admin-managed at the hub.
- **PvP is on by default**, but the spawn safe-zone keeps the hub neutral.
- **Distant Horizons is on.** Render distance feels enormous because the
  server pre-streams LOD data — you'll see mountains 2–3 km away on a clear
  day. This is a feature, not a bug.

---

## Mod list

Full versions and links live in `mods.md`. Short version, grouped by what
each mod is for:

### Core (the pillars)
- **Create** + **Create: Aeronautics** — the headliner. Build airships.
- **Create: Numismatics** — printable currency, native Create integration.
- **Tectonic** — dramatic terrain, mountains, canyons.
- **Regions Unexplored** — biome variety on top of Tectonic's land shape.
- **Distant Horizons** — server-side LOD system. View 2–3 km out.
- **World Border** — enforces the 10k×10k boundary.

### Danger
- **Born in Chaos** — apocalyptic zombie/cultist variants for outer rings.
- **Hybrid Aquatic** — sharks, deep-sea threats.
- **Alex's Mobs** — mostly neutral fauna, plus a few hostiles.

### Survival pressure
- **Tough as Nails** — thirst + body temperature.
- **Encumbered** — item weight and movement penalties.
- **Nutritional Balance** — diet variety matters.

### Food + cooking
- **Farmer's Delight** + **Create: Confectionery** — the cooking specialisation
  loop.
- **Create: Food**, **Display Delight**, **Delightful Creators** — supporting
  recipes and integration.

### Building
- **Dawn of Time** — 400+ themed decoration blocks.
- **MrCrayfish's Refurbished Furniture** — chairs, tables, crates, lights.
- **Fusion** — connected textures.

### Maps + intel
- **Xaero's Minimap** + **Xaero's World Map** — minimap with native Open
  Parties and Claims integration. Waypoint sharing.
- **Open Parties and Claims** — territory claiming.

### Voice
- **Simple Voice Chat** — proximity audio, group channels. Ship as part of
  the modpack; opens automatically when you join.

### Performance / quality of life
- **Sodium**, **Iris**, **ImmediatelyFast**, **FerriteCore** — client-side
  rendering and memory wins.
- **JEI** — recipe lookup.
- **GeckoLib**, **GlitchCore**, **Citadel**, **YungsAPI**, **TerraBlender**,
  **Lithostitched**, **Architectury**, **Cloth Config**, **Framework**,
  **Kotlin For Forge** — library mods that other content mods depend on.

### Admin / world tooling
- **Paxi** — server datapack loading.
- **Worldborder** — enforces the 5,000-radius boundary.

### Niche
- **Radioactive** — radiation mechanics. On trial; may be removed if it
  fights the geographic difficulty curve.

### In-house glue
- **caero_rings** — biome-tier spawn handler + nether-hub round-trip
  enforcement. Custom for this server.
- **sable** — sub-level multi-world handling.

---

## Tips for your first few hours

- The hub is safe; don't expect to find good loot near it. Iron drops to
  ~40% of vanilla in the inner ring.
- Walk a bit before you commit to a base location. Each biome has a random
  resource bonus (e.g., "+50% redstone, +20% diamond") — pick one that suits
  your specialism.
- You will get cold or thirsty before you get killed by a mob, in the early
  game. Bring leather, pack a cup, drink from rivers (not the sea).
- Build a small ground vehicle before you commit to a flying one. Aeronautics
  rewards iteration.
- Talk to other players. If you walked 2 km to find bamboo and someone else
  has a bamboo farm at the hub, *trade*. The economy is the point.

---

## When something breaks

- **You can't connect.** Likely a missing mod on your client. Compare your
  `mods/` folder to the modpack zip Greg shared.
- **Voice chat doesn't work.** Confirm port 24454/UDP is open on your end
  (most home routers are fine; some workplace networks block it).
- **You see ghost holes in builds.** Specific mod missing client-side. Tell
  Greg what the structure looked like and he'll figure out which mod.
- **Lag.** Tell Greg the time and where you were. The server records TPS and
  he can correlate.

Server up time, technical state, and ops history live in `runbook.md` —
mostly Greg's territory but anyone is welcome to read it.

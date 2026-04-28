# Glue Mod #1 — Numismatics → OPAC Claim Bridge

**Mod ID:** `caero_claims` *(tentative — "create aeronautics claims")*
**Target:** Minecraft 1.21.1 · NeoForge
**Status:** ⛔ **SUPERSEDED 2026-04-28** — see `glue/caero_claims/PLAN.md`.

> This design bought OPAC chunk-claim quota with coins. The replacement design
> drops OPAC entirely, claims arbitrary 3D block volumes (not chunks) directly,
> and keeps Numismatics as the funding source. Mod ID `caero_claims` is reused
> by the new design. Kept here for historical record only — do not implement.

---

## 1. Purpose

Let players spend Numismatics coins to earn bonus chunk claims in Open Parties and
Claims. Players deposit coins into a **Claim Treasury** block; the block consumes
them and increases the owner's OPAC `BONUS_CHUNK_CLAIMS` quota at a configurable
spurs-per-claim-block rate.

This is the **economic sink** that makes coins matter for land acquisition, per
PLAN.md §6.

---

## 2. Player-facing flow

1. Player crafts a **Claim Treasury** (recipe gated to need outer-biome mats so it
   can't be spammed at spawn — TBD).
2. Places it. On placement, block entity records `ownerUUID = placer`.
3. Player (or a friend delivering via a Create belt — this is the point) inserts
   coin items into the block's input slot.
4. Coins are consumed immediately. Running spur total accumulates in the BE's NBT.
5. Every time the running total crosses `spursPerClaimBlock`, the block calls OPAC
   to increment the owner's `BONUS_CHUNK_CLAIMS` by the earned amount and
   subtracts the spent spurs. Remainder stays in the running total for next time.
6. Player sees a title/actionbar message: *"+3 claim chunks granted to <name>"*.

No UI screen for MVP — it's a hopper-style interface. Create belt/chute pipelines
"just work" because NeoForge's capability system handles item insertion.

---

## 3. Architecture (bounded: 1 block + 1 handler + 1 API call)

```
  Coin item (from Numismatics)
          │
          ▼
┌─────────────────────────────┐
│  ClaimTreasuryBlock         │   registered via DeferredRegister
│  (1 block)                  │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐   owns ItemStackHandler (1 input slot)
│  ClaimTreasuryBlockEntity   │   on insert: validate COINS tag, compute spurs,
│  (1 block entity)           │   accumulate total, call grant logic
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────┐   OpenPACServerAPI.get(server)
│  ClaimGrantService          │     .getPlayerConfigManager()
│  (static helper)            │     .getLoadedConfig(ownerUUID)
│                             │     .tryToSet(BONUS_CHUNK_CLAIMS, new)
└─────────────────────────────┘
```

The glue is narrow: one block, its block entity, and a static helper that makes
the single OPAC API call. No networking. No custom UI. No capability system of
our own.

---

## 4. Upstream API reference (verified 2026-04-20 from cloned source)

### OPAC
- Entry point: `xaero.pac.common.server.api.OpenPACServerAPI#get(MinecraftServer)`.
- Config manager: `OpenPACServerAPI#getPlayerConfigManager()` returns
  `xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI`.
- Load player config: `IPlayerConfigManagerAPI#getLoadedConfig(UUID)` returns
  `IPlayerConfigAPI`.
- The option spec: `xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions#BONUS_CHUNK_CLAIMS`
  — typed `IPlayerConfigOptionSpecAPI<Integer>`. Default 0. Player-settable.
- Set value:
  ```java
  IPlayerConfigAPI.SetResult r = playerConfig.tryToSet(
      PlayerConfigOptions.BONUS_CHUNK_CLAIMS, currentValue + earned);
  ```
  Handle `SetResult.SUCCESS` vs other values.

Evidence: `.research/repos/opac/Common/src/main/java/xaero/pac/common/server/`.

### Numismatics
- Coin items are tagged with `NumismaticsTags.AllItemTags.COINS` → use the tag,
  **not** hardcoded item IDs. Future-proof against added coin denominations.
- The `Coin` enum: `dev.ithundxr.createnumismatics.content.backend.Coin`.
  Each enum value has a public `int value` field = spur-equivalent (SPUR=1, BEVEL=8,
  SPROCKET=16, COG=64, CROWN=512, SUN=4096).
- `CoinItem`: `dev.ithundxr.createnumismatics.content.coins.CoinItem` — gives us the
  `Coin` instance from an `ItemStack`.

Evidence: `.research/repos/numismatics/common/src/main/java/dev/ithundxr/createnumismatics/`.

---

## 5. Config

File: `config/caero_claims-common.toml` (NeoForge config spec).

```toml
[claims]
# Spurs needed to earn 1 additional chunk claim.
# Numismatics: 1 cog = 64 spurs, 1 crown = 512 spurs.
# Default 100 = roughly "2 cogs per chunk claim" — ~adjust after playtesting.
spursPerClaimBlock = 100

# Upper bound on BONUS_CHUNK_CLAIMS that this treasury can grant.
# Prevents one player claiming the whole map. OPAC default base is ~500.
maxBonusClaimsPerPlayer = 500

# If true, broadcast grants as server messages (so people know the economy is flowing).
# If false, only the owner sees a title.
broadcastGrants = false
```

---

## 6. Test plan (self-runnable)

All tests run via `./gradlew` without Greg's involvement.

### 6.1 Build
- `./gradlew build` — compiles, runs datagen, runs JUnit, packages jar.

### 6.2 Unit (JUnit — pure logic, no MC)
- `CoinSpurConversionTest`: given a stack of each coin denom × N, compute spur total.
- `ClaimCalculationTest`: given `spursPerClaimBlock = 100`, running total 150 + new
  1 Bevel (8 spurs) → should grant `(150+8)/100 = 1` and leave remainder 58.

### 6.3 GameTest (@GameTest — headless MC world)
Via `./gradlew runGameTestServer`:
- `gametest_treasury_accepts_coins`: place treasury in test structure, insert 100
  spurs via `CoinItem` stack, assert BE's running-total NBT reads expected value.
- `gametest_treasury_rejects_non_coins`: insert a vanilla item, assert insertion
  refused (remains in source inventory).
- `gametest_treasury_grants_claim_quota`: place treasury owned by a fake player,
  insert 200 spurs' worth, tick, then read OPAC's `BONUS_CHUNK_CLAIMS` for that
  UUID — expect 2 (assuming 100 spurs/block).
- `gametest_cap_is_enforced`: set cap to 5, try to grant 10, expect 5 granted and
  remaining spurs returned or stored.

### 6.4 What I cannot self-verify (Greg gate)
- Real UX feel (where to put the Treasury in the hub, how obvious the interaction is).
- Create belt item-piping into the Treasury — will work in theory but worth an eye test.
- Balance of `spursPerClaimBlock` vs. gameplay feel.

---

## 7. Build + verification sequence

1. Install Java 21 (`sdkman` or `apt`).
2. Create NeoForge 1.21.1 MDK skeleton under `glue/numismatics-opac-bridge/`.
3. Add Maven deps:
   - NeoForge (their Maven).
   - OPAC API — grab from Modrinth Maven (`https://api.modrinth.com/maven/`). Scope:
     `compileOnly` (we don't ship it; require it at runtime).
   - Numismatics — Modrinth Maven. Also `compileOnly`.
   - Create — same; OPAC and Numismatics both transitively depend on it.
4. Implement: `ClaimTreasuryBlock`, `ClaimTreasuryBlockEntity`, `ClaimGrantService`,
   item/block registries, datagen for loot/recipes, config spec, the
   `Mod.EventBusSubscriber` wiring.
5. Write the tests in 6.2 and 6.3.
6. `./gradlew build` → must pass.
7. `./gradlew runGameTestServer` → all gametests must pass.
8. Report status to Greg with the test summary.

---

## 8. Explicitly out of scope (v1)

- Custom GUI/screen for the Treasury. Use hopper-style capability insertion only.
- Refunds. Coins are consumed, no take-backs.
- Revoking claim chunks. OPAC has its own commands for that.
- Display of "how many spurs until the next claim" — nice-to-have; defer.
- A corresponding *bank* block that also lets players buy back coins. Out of scope.
- Permission tiers (e.g., staff can grant at a different rate). Out of scope.

---

## 9. Open questions

- **Treasury recipe.** Should the Treasury need rare outer-biome mats (so you have
  to trek before you can claim land), or be cheap at spawn? Recommend: moderately
  gated (e.g., a brass ingot from Create + a crown coin from Numismatics + an iron
  block), so it's not free but craftable before the first deep expedition.
- **Ownership on block pickup.** If the owner breaks the Treasury, do we refund
  undistributed spurs as coins? MVP: no. Break = lose. Reconsider after playtest.
- **OPAC's `tryToSet` can fail** (`ILLEGAL_OPTION`, etc.). MVP behavior: log,
  drop the coin stack back at the Treasury's feet so nothing is lost. Document
  the failure modes in the mod log.

# Mod — Custom Block-Volume Claims (`caero_claims`)

**Mod ID:** `caero_claims`
**Target:** Minecraft 1.21.1 · NeoForge · Kotlin (KFF)
**Status:** **v1 shipped 2026-04-28.** Verified end-to-end on the dev
instance (claim, render, foreigner-test toggle, chat-command confirmation).
Next: v2 mixin protection (piston / fluid / fire / dispenser / projectile),
then v3 Create-compat, then OPAC removal.

> **Supersedes** the design-only `glue/numismatics-opac-bridge/` plan. That design
> bought OPAC chunk-claim quota with coins; this design replaces OPAC entirely
> with a native, block-resolution claim system funded by Numismatics.
>
> **Replaces OPAC.** Once `caero_claims` v2 ships and is verified, OPAC is
> uninstalled from the instance and `mods.md`. Both mods cancel the same break/place
> events; running them concurrently for long produces compounding interaction
> conflicts.

---

## 1. Purpose

Players spend Numismatics spurs to claim arbitrary 3D **block volumes** (not chunks).
A claim is a union of axis-aligned cuboids, allowing custom shapes (3×2×6, L-shapes,
multi-room buildings, etc.). Inside a claim, only the owner can break, place, or
interact. Volumes render as colored outlines in-world while the player holds the
**Claim Wand**, hidden otherwise.

**Pillars served (heuristics.md):**
- Coins as economic sink (every claimed block burns 1 spur permanently).
- Granular land use (no wasted chunk-edges).
- Player commitment (claims are permanent — no unclaim, no refund).

---

## 2. Player-facing flow

The selection is a **three-step gesture** with the second corner *arming* an
explicit confirmation step. This exists because spurs are non-refundable — a
single misclick on a 1000-block volume burns 1000 spurs irreversibly. The
two-step click + chat-command confirm is deliberate friction.

1. Player crafts the **Claim Wand** (recipe TBD — likely brass ingot + crown
   coin).
2. **First right-click** on block A → corner A locks. Wand starts following
   the player's cursor with a yellow preview AABB. Action-bar shows live
   `Volume: N blocks · Cost: N spurs · Balance: N · INSUFFICIENT` if applicable.
3. **Second right-click** on block B → corner B locks too; preview turns
   **orange** ("armed"). The client sends a `ClaimArmPacket(a, b)` to the server
   which validates reach + balance and stores the corners as a per-player
   *pending* claim. **No spurs are spent yet.** Action-bar in gold:
   `Run /caero-claim yes to confirm · /caero-claim cancel to abort · NOT REFUNDABLE`.
4. **Player runs `/caero-claim yes`** in chat → server commits the pending
   claim: deducts spurs via `Numismatics.BANK.deduct`, appends the AABB to the
   player's claim, broadcasts `ClaimUpdatePacket`, and sends the originating
   client a `ClaimPendingClearPacket` so the orange visualization clears. New
   green volume appears in-world.
5. **Or player runs `/caero-claim cancel`** (or **right-clicks again** with the
   wand) → server clears the pending state, S2C `ClaimPendingClearPacket` clears
   the client-side orange box. No spurs spent.
6. **Permanent claims.** No player-side unclaim. Spurs are burned. Admin
   override: `/caeroclaims delete <claim-id> [volume-index]`.

### Why chat commands, not Y/N keypresses

Earlier prototype used Y to confirm and N to cancel. Issues:
- Conflicts with future keybind ergonomics (Y is unbound by vanilla but is a
  natural slot for other mods to grab).
- Easy to fat-finger — a stray Y press while typing in chat would be
  intercepted (we can guard against open GUIs but not single-key intent).
- Less explicit than typing a command. Spurs are non-refundable; the friction
  of typing seven characters is the entire point.

Chat commands also give us tab-completion and discoverability. `/caero-claim`
is a separate command from the admin `/caeroclaims` (plural) so player commands
need no permission gate.

### State ownership

- **Client state** (in `ClaimWandClientHandler`): `firstPos`, `secondPos`,
  `hoveredPos`. Used purely for rendering the preview/armed boxes and the
  action-bar text. Cleared on packet from server (after commit/cancel) or when
  the player puts the wand away.
- **Server state** (in `ServerClaimService.pending`): per-player UUID →
  `Pending(a, b, dim, costSpurs)`. Set on `ClaimArmPacket`, cleared on
  `/caero-claim yes` (after commit) or `/caero-claim cancel` (no-op). Cleared
  on logout, dimension change, or after a 5-minute timeout (player walked away
  with an armed selection).

The server is the truth. The client's orange box is purely cosmetic — even if a
client desyncs, only the server can spend the player's spurs.

---

## 3. Architecture

```
                          ┌───────────────────────────┐
                          │ ClaimWand (Item, client   │
   right-click ──────────▶│ singleton holds firstPos) │
                          └─────────────┬─────────────┘
                                        │ ClaimSelectionPacket(a, b)
                                        ▼
                          ┌───────────────────────────┐
                          │ ServerClaimService        │
                          │   1. validate reach       │
                          │   2. compute volume·cost  │
                          │   3. Numismatics.BANK     │
                          │      .getAccount(p)       │
                          │      .deduct(spurs)       │
                          │   4. add AABB to claim    │
                          │   5. persist SavedData    │
                          │   6. broadcast S2C update │
                          └─────────────┬─────────────┘
                                        ▼
                          ┌───────────────────────────┐
                          │ ClaimDimensionData        │
                          │   (per-dim SavedData)     │
                          │   Map<UUID, Claim>        │
                          │   Long2ObjectMap<List>    │ chunk → claim ids
                          └─────────────┬─────────────┘
                                        │ queries
                                        ▼
                          ┌───────────────────────────┐
                          │ ProtectionHandlers        │
                          │   (event subscribers +    │
                          │    mixins, see §6)        │
                          └───────────────────────────┘
```

Three logical components: **wand** (UX), **claim service + storage** (state), and
**protection** (enforcement). Render is a fourth, client-only.

---

## 4. Data model

```kotlin
data class Claim(
    val id: UUID,            // not the player UUID — claims are addressable
    val owner: UUID,
    val dimension: ResourceKey<Level>,
    val volumes: MutableList<AABB>,   // each AABB inclusive of both corners
)

class ClaimDimensionData(val dim: ResourceKey<Level>) : SavedData() {
    val claims = HashMap<UUID, Claim>()
    val byOwner = HashMap<UUID, UUID>()  // owner → claim id (one claim per player per dim)
    val chunkIndex = Long2ObjectOpenHashMap<MutableList<UUID>>()  // chunkPos → claim ids overlapping it

    fun claimAt(pos: BlockPos): Claim? { /* O(claims-in-chunk) lookup */ }
    fun isProtected(pos: BlockPos, actor: UUID?): Boolean
}
```

Lookup hot path: `chunkIndex[ChunkPos.asLong(pos)] → iterate claim ids → AABB.contains(pos)`.
At ~5 claims per chunk this is sub-microsecond and runs once per `BlockEvent.BreakEvent`.

Persistence: vanilla `SavedData` per dimension, file `caero_claims.dat` in the
dimension's data folder. NBT layout:

```
{
  Claims: [
    { Id: <uuid>, Owner: <uuid>, Volumes: [{X1,Y1,Z1,X2,Y2,Z2}, ...] }
  ]
}
```

**One claim per player per dimension.** Volumes accumulate into that single claim;
no per-region naming in v1. Multi-claim ownership and naming deferred to a future
revision (see §13).

---

## 5. Upstream API reference (verified 2026-04-28)

### Numismatics — `BankAccount.deduct`
- `dev.ithundxr.createnumismatics.Numismatics.BANK` — `GlobalBankManager` singleton.
- `Numismatics.BANK.getAccount(serverPlayer)` auto-creates a `PLAYER`-type account.
- `BankAccount.deduct(int spurs)` — atomic, returns `false` on insufficient balance.
  Persists via `BankSavedData` automatically. Server-thread-only.
- 1 spur = 1 block. Cost = `(x2-x1+1) * (y2-y1+1) * (z2-z1+1)`.
- Coin item-tag `numismatics:coins` and `Coin` enum (SPUR=1 … SUN=4096) — not
  needed for v1; bank balance only.

Evidence: `.research/repos/CreateNumismatics/common/src/main/java/dev/ithundxr/createnumismatics/`.

### Create — Super Glue UX pattern (reference, not dependency)
- `com.simibubi.create.content.contraptions.glue.SuperGlueSelectionHandler` —
  client-side singleton, `firstPos`/`hoveredPos` plain fields, ticked from
  `ClientTickEvent.Post`, polled `mc.hitResult` for cursor block.
- Confirmation packet: two `BlockPos` fields, validated server-side for
  `canInteractWithBlock` + ≤25-block reach.
- In-world rendering: `RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS` with
  line-box draws. Catnip's `Outliner` is a convenient shortcut (transitively
  available via Create); we'll use it for v1.

We **reimplement** the pattern; no Create API dependency for the wand itself.

Evidence: `.research/repos/Create/src/main/java/com/simibubi/create/content/contraptions/glue/`.

### OPAC — protection-coverage reference (we are replacing OPAC)
The full hook list lives in
`.research/repos/open-parties-and-claims/Common/src/main/java/xaero/pac/common/server/`
(see `event/CommonEvents.java` for events and `mixin/Mixin*.java` for the rest).
Coverage is broken into v1/v2/v3 protection waves in §6.

### Xaero map integration — **NOT possible without invasive coupling**
Xaero is closed-source. There is a `xaero.common.minimap.highlight.HighlighterRegistry`
inside the jar with `public` classes, but it's not advertised as an API and Xaero
does not look up third-party adapters. OPAC works only because Xaero ships an
OPAC-specific adapter inside Xaero's own jar.

**Map rendering is deferred indefinitely.** Even if we hacked into Xaero, its
highlighter is per-chunk-column and color-only — our 3D AABBs would render as
coarse 16×16 blobs anyway.

---

## 6. Protection scope — three waves

### v1 — Foundation (events only, ships first)
The 80% of grief vectors with zero mixins. Each handler queries
`ClaimDimensionData.claimAt(pos)` and cancels if the actor is not the owner.

| NeoForge event | Gates |
|---|---|
| `BlockEvent.BreakEvent` | break |
| `BlockEvent.EntityPlaceEvent` | placement (single) |
| `BlockEvent.EntityMultiPlaceEvent` | bed/door placement |
| `PlayerInteractEvent.LeftClickBlock` | fire extinguish, etc. |
| `PlayerInteractEvent.RightClickBlock` | container, button, lever, sign, anvil |
| `PlayerInteractEvent.RightClickItem` | item-use in air over claim |
| `PlayerInteractEvent.EntityInteract`, `EntityInteractSpecific` | mob/armor-stand/itemframe interact |
| `AttackEntityEvent` | melee on entity in claim |
| `LivingAttackEvent` (with projectile-shooter resolve) | indirect damage |
| `ExplosionEvent.Detonate` | strip protected blocks/entities from `getAffected*()` |
| `EntityMobGriefingEvent` | creeper/enderman/zombie-on-door master switch |
| `BlockEvent.FarmlandTrampleEvent` | crop trample |
| `MobSpawnEvent.FinalizeSpawn` | suppress hostile spawns inside claims |
| `FillBucketEvent` | bucket-place + scoop |
| `ItemEntityPickupEvent` | foreign players picking up your dropped items |

Adequate for "no one can break or interact". Ships first.

### v2 — Mixin closures (closes well-known holes)
Patterns lifted from OPAC's `MixinPistonBaseBlock`, `MixinFlowingFluid`,
`MixinFireBlock`, `MixinDispenserBlock`, `MixinFishingHook`, `MixinFrostWalkerEnchantment`,
plus an `EntityJoinLevelEvent` handler that tags TNT / falling blocks / projectiles
with their owner UUID at spawn so v1's `ExplosionEvent`/`LivingAttackEvent` can
attribute them.

| Mixin / hook | Gates |
|---|---|
| `MixinPistonBaseBlock.moveBlocks` | piston push from outside into claim |
| `MixinFlowingFluid` | water/lava flow across claim border |
| `MixinFireBlock` | fire spread into claim |
| `MixinDispenserBlock` | dispenser firing into claim |
| `MixinFishingHook` | hook latching items/entities through wall |
| `MixinFrostWalkerEnchantment` | frost-walker turning water to ice in claim |
| `EntityJoinLevelEvent` | tag TNT / projectiles / falling blocks with owner UUID |
| `MixinAbstractArrow` etc. | redirect projectile hits inside claim to "miss" |
| `EntityTeleportEvent.ChorusFruit` | block chorus teleport in |

Required before OPAC removal — without this, foreign pistons/fluids/dispensers
trivially defeat v1. v2 ships when v1 is GameTest-clean.

### v3 — Create compat (required because this is a Create server)
Create's deployers, contraptions, mechanical arms, cannons, train relocation, and
super-glue selections trivially defeat any claim system that doesn't account for
them. OPAC has a dedicated 300-line `ServerCore` block for Create; we copy it
narrowly.

| Hook | Gates |
|---|---|
| `isCreateModAllowed(level, pos, contraption)` | contraption assembly may not include foreign-claim blocks |
| `replaceBlockFetchOnCreateModBreak` | drills/saws/deployers see "air" at protected positions |
| `isCreateGlueSelectionAllowed` | super-glue selection box must lie inside owner's claim |
| `isCreateDeployerBlockInteractionAllowed` | deployer right-click into foreign claim |
| `onCreateCollideEntities` | strip foreign entities from contraption collision |
| `canCreateCannonPlaceBlock`, `canCreatePloughPos`, `canCreatePipeAffectBlock` | Create cannon / plough / pipe interactions |

v3 is **the gate for OPAC removal**. Until v3 lands, OPAC stays installed for the
Create-vector coverage.

---

## 7. Networking

| Packet | Direction | Payload | Purpose |
|---|---|---|---|
| `ClaimArmPacket` | C2S | `BlockPos a, BlockPos b` | second right-click — stages pending claim, no spurs spent |
| `ClaimCancelPacket` | C2S | (empty) | armed-state right-click cancel |
| `ClaimSyncPacket` | S2C | `List<ClaimSummary>` | full snapshot on join / dim change |
| `ClaimUpdatePacket` | S2C | `ClaimSummary` | one claim added or modified — broadcast to dim |
| `ClaimRemovePacket` | S2C | `UUID claimId` | admin delete |
| `ClaimPendingClearPacket` | S2C | (empty) | server tells client to drop its locally-armed visualization (after `/caero-claim yes` commits, after `/caero-claim cancel`, or after a `ClaimCancelPacket`) |

Client maintains a `Map<UUID, ClaimSummary>` for render. v1 sends all claims in
the player's current dimension on join — fine until claim count is in the
thousands. View-distance filtering is a v2 polish.

---

## 8. Rendering (client-only)

Delegated to **Catnip's `Outliner`** (transitive dep via Create / Ponder). On
each `ClientTickEvent.Post` while the player holds the wand:

1. For each known `ClaimSummary` in the current dimension, for each AABB:
   - Color = green (`0x68C586`) if `claim.owner == localPlayer.uuid`, red
     (`0xC55858`) otherwise.
   - Call `Outliner.getInstance().showAABB(slot, aabb).colored(color).keep(slot)`.
2. While selecting:
   - Yellow (`0xC5B548`) box from `firstPos` to live cursor (CORNER_A_SET).
   - Orange (`0xFF8800`) box from `firstPos` to locked `secondPos` (ARMED).

### Why Catnip's Outliner, not a hand-rolled `VertexConsumer`

Original implementation used `LevelRenderer.renderLineBox` writing into
`buffers.getBuffer(RenderType.lines())`. Vanilla `RenderType.lines()` has depth
test enabled, so the wireframe edges that pass *through* terrain were culled —
visually the player only saw the corner blocks poking above the surface, not a
spanning box. The depth-disabled lines RenderType requires access to the
`protected` `RenderStateShard$LineStateShard` inner class, not directly
constructable from a mod without access transformers.

Catnip's `Outliner` ships its own through-walls render type (same one Create's
Super Glue uses), and the implementation is robust + well-tested. Promoted
Create from `optional` to `required` dep in `mods.toml` to use it — a no-op
in practice since Numismatics already requires Create transitively.

**Render cost** is bounded by claim-count × 12 line-edges per AABB. Outliner
auto-fades entries not re-`keep`-ed, so when the wand is put away outlines
fade and stop being processed.

---

## 9. Config (`config/caero_claims-common.toml`)

```toml
[claims]
spursPerBlock = 1                  # cost per claimed block. 1 spur = 1 block.
maxReach = 25                      # max blocks between corners A and B
maxVolumePerClaim = 100000         # safety cap on a single claim's total block count
broadcastNewClaims = false         # if true, "Player X claimed N blocks" goes to chat
allowOverlap = false               # claims may not overlap each other
```

Cost is a single global rate in v1. Per-dimension rates and progressive pricing
(cost scales with claim size) deferred — easy to add without schema breakage.

---

## 10. Test plan

### 10.1 Unit (JUnit, no MC)
- `AABBVolumeTest` — corner pairs → expected block count (inclusive endpoints).
- `ClaimDimensionDataTest` — chunk-index correctness on insert / remove / lookup.
- `OverlapDetectorTest` — given existing claims, does a new AABB overlap.

### 10.2 GameTest (`./gradlew runGameTestServer`) — **deferred**
GameTests need NBT structure templates that this repo has no existing tooling
to author headlessly. The JUnit suite already covers the v1 logic (volume math,
overlap, NBT round-trip, chunk-index correctness). Leaving real-world event
flow to Greg's eyeball test on the deployed jar.

When we add v2 mixins (piston push, fluid flow, dispenser firing) — those *do*
require world simulation and aren't testable with JUnit. At that point:
- Capture a single empty 1×1×1 structure in-game, save as
  `src/main/resources/data/caero_claims/structures/empty.nbt`.
- Add `@GameTestHolder` + `@GameTest(template = "caero_claims:empty")` tests:
  `gametest_piston_blocked`, `gametest_fluid_blocked`, etc.

### 10.2 GameTest deferred — to add with v2:
- `gametest_explosion_filtered` — TNT inside claim destroys no claimed blocks.
- `gametest_piston_blocked` — piston outside pushes into claim → push cancelled.
- `gametest_create_deployer_blocked` — Create deployer in foreign claim → fails. (v3)

### 10.3 Greg-gated
- Real wand UX feel (does the action-bar cost preview read clearly).
- Live Numismatics → bank flow with Create depositors.
- v3 Create compat against actual contraptions (best run on the test server).

### 10.4 In-game diagnostic tools (admin only, op level 2)

Two debug commands ship in v1 for solo verification of protection without
spinning up an alt account:

- **`/caeroclaims test [pos]`** — diagnostic dump. Prints: dimension, runner
  UUID, runner feet, tested position, every claim in this dim with full AABB,
  marks the volume containing the tested position with `→ HERE`, then runs
  the protection predicate against (a) a random foreign UUID and (b) the
  runner's real UUID. The full claim list is the load-bearing detail — most
  "I'm in but it says I'm out" reports turn out to be the tested Y falling
  outside a 3D-bounded volume.

- **`/caeroclaims test foreigner`** — toggle. While ON, the runner's UUID
  is swapped for a stand-in non-owner UUID before any protection check.
  Effect: the runner is blocked from breaking, placing, or interacting in
  *every* claim, including their own. Lets a single tester verify the
  break/place/interact paths actually fire and cancel without an alt.
  Auto-clears on logout. The wand still works in this mode (the wand path
  doesn't ownership-check the holder), so claim-and-test cycles need only
  one account.

Implementation: `ServerClaimService.foreignTestMode: MutableSet<UUID>` plus
`effectiveActorUuid(uuid)` swapping. Every UUID flowing into a protection
predicate in `V1ProtectionHandlers` goes through that swap.

### 10.5 Real-multi-client testing (gated on infra)

Two Microsoft accounts or one offline-mode-PrismLauncher-instance + Open-to-LAN.
The diagnostic tools above validate the *predicate*, but only a real second
client can prove the NeoForge events actually fire and cancel end-to-end.

---

## 11. Build sequence

1. NeoForge 1.21.1 MDK skeleton at `glue/caero_claims/`.
2. Maven deps (all `compileOnly`): NeoForge, Numismatics (Modrinth Maven), Create
   (Modrinth Maven). KFF runtime as it already is in `ring-biomes`.
3. Implement v1: wand item, ClaimDimensionData, ClaimService, packets, render,
   v1 protection handlers, admin commands.
4. Unit + GameTest pass → `./glue/caero_claims/deploy.sh` to PrismLauncher
   instance. Greg eyeball-tests UX.
5. Implement v2 mixins (piston, fluid, fire, dispenser, fishing, projectile).
   GameTest pass → deploy.
6. Implement v3 Create compat. GameTest pass against gametest contraption setup
   → deploy → Greg validates on test server.
7. **Remove OPAC from `mods.md`, uninstall from instance, drop the OPAC compat
   research repo from `.research/repos/`.** Keep its claim data in the world for
   one server-version cycle in case migration is needed.

---

## 12. Out of scope (v1)

- Per-claim ACL (allow friend X to break, allow Y to use chests). v2 polish.
- Multi-claim ownership (one named claim per dimension is the v1 model).
- Claim transfer / sale between players. Admin command suffices.
- Player-facing unclaim. Permanent. Spurs are burned.
- Map rendering (Xaero / minimap / world map). Closed-source blocker. See §5.
- Migration from OPAC chunk claims to caero_claims volumes. Manual conversion;
  document for Greg, do not automate.
- Refund of any kind. Misclick = lost spurs. Mitigated by live cost preview.
- Per-dimension claim quotas. Bank balance is the only quota.

---

## 13. Open questions

- **Wand recipe.** Brass + crown + iron block — moderately gated. Same as the
  retired Treasury recipe. Confirm at scaffolding time.
- **Multi-claim per player per dimension.** v1 forces one accumulating claim per
  player per dim — simpler protection lookup, no claim-naming UI. If players
  request named regions (e.g., "my house" vs "my farm") this becomes the first
  v1.x ask. Reconsider after playtest.
- **Claim deletion on player departure.** A player leaves the server; their
  claim persists forever. v1: admin command. v1.x: optional auto-expire after
  configurable inactivity (`/caeroclaims gc`). Track in `runbook.md`.
- **Cross-dimension claims.** Each dim is its own `ClaimDimensionData`. A claim
  in the Nether is unrelated to one in the Overworld. Confirm this matches Greg's
  mental model.

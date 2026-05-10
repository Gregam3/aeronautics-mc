# Season 3 ("Karos") — Planning & Open Questions

**Status:** Research / pre-design. Nothing decided. Successor to season retrospective notes scattered across `PLAN.md`, `economy-principles.md`, `company-integration-proposal.md`, `TASKS.md`. Once Greg answers the §5 questions, decisions roll into `PLAN.md` v0.9 and `heuristics.md` v2.

---

## 1. Season 1 → 2 retrospective (organized from voice dump)

### 1.1 Season 1 — "the failure"
- **Setup:** OPAC claims, basic Numismatics, no quality system, no refining, no auction house.
- **What broke:** No forcing function for trade. Players self-sufficient by default. Currency had no faucet that fed organic demand. Coins were decorative.
- **Root cause (in retrospect):** "There were *reasons* to trade, but it wasn't *inconvenient enough not to*." Soft incentives lose to inertia.

### 1.2 Season 2 — "trading happened, but the economy is fucked"
- **Added:** caero_specialization (7 industries, per-stack quality, refiner blocks, per-skill XP), caero_auction (player-to-player AH), caero_claims (block-volume claims, spurs sink), caero_vitality (death penalty + restoration foods).
- **What worked:**
  - Trading **did** kick off — refining-as-service is real demand.
  - Auction house mints money in a controlled way.
  - Quality tiers + byproducts produce actual cross-profession dependencies.
- **What broke:**
  - **Early-game friction without payoff.** Stone/iron tool quality made the first hours awkward without driving meaningful trade — the social cost of trade is highest exactly when players are isolated and just trying to bootstrap.
  - **One player made ~5× others by selling raw logs.** Raw resource sales are an exploit; only processed goods should mint money.
  - **Infinite-scaling exploit.** A tree farm scales linearly. Even if raw logs are banned, refined charcoal sales at scale → infinite money. Same for cobble gens, fan-smelting shortcuts, etc.
  - **No clear phase structure.** No "you achieved X, now phase 2 unlocks." Just an open-ended grind.
  - **No win condition.** Nothing for players to actually *compete for*.

---

## 2. Season 3 vision (organized from voice dump)

### 2.1 Topology
- **Per-player spawn island** in an actual ocean (not skyblock void). Small, basic resources only.
- **Dangerous sea** — naked swimming should be lethal. Boat is mandatory to leave.
- **Central island** — shared infrastructure: dock, auction house, mid-tier refineries, possibly the only place to sell.
- **Outer / harder islands** — rarer resources, gated by vehicle capability.

### 2.2 Phase progression (illustrative, not decided)
1. **Phase 1 — Bootstrap.** Get basic resources from your spawn island. Build a small boat.
2. **Phase 2 — Reach the central island.** Trade. Specialize. Build a bigger boat / cart.
3. **Phase 3 — Outer-island runs.** Mid-tier resources. Ship that can carry a cart. Start producing surplus.
4. **Phase 4 — Aircraft / late game.** Tiny planes, balloons, far-island package runs (ship-carries-car-carries-package pattern). Push for the win condition.

### 2.3 Win condition (illustrative)
- "First to 1M spurs / cogs" or similar single-axis money goal. Decisive, visible, naturally drives every other behavior (specialize → produce → trade → escalate vehicles).

### 2.4 Specialization fit
- Profession choice **deferred** — early game lets you do anything at low yield. Specialization gates only kick in at mid/high tier.
- Tier 2+ resources (the "Create stuff" tier) require trade chains.

---

## 3. Map to current state

### 3.1 Already built and aligned with S3 vision
| S3 need | What we have |
|---|---|
| Currency + AH | Numismatics + `caero_auction` (live, multiplayer-verified). |
| Quality + refining | `caero_specialization` v1.7 — 7 industries, byproducts, catalysts. |
| Death penalty / cook role | `caero_vitality` v0.1 — graduated MAX_HEALTH penalty + restoration foods. |
| Land sink | `caero_claims` v1 — non-refundable spurs/block. |
| Vehicle weight matters | Encumbered + Sable mass mixins (chest contents, players-on-contraption). |
| Bounded world | World Border, currently 10k×10k. |
| Auto-disabled exploits | `caero_disable_loops` datapack — Mending/Unbreaking inert, vanilla 2-grid repair off, iron golems give poppies, 160 BIC recipes off. |

### 3.2 Conflicts with S3 — must be revised or thrown out
| Current design | S3 conflict |
|---|---|
| **`caero_rings`** = concentric difficulty rings on a continent. | S3 wants **islands across an ocean**, not concentric land rings. Topology mismatch. Either rework `caero_rings` to "ocean with tier-tagged islands" or replace it entirely. |
| **Heuristic 3** ("game gets harder the further out you go") implemented as radial mob/ore tiering. | S3 difficulty is per-island, not radial. Pillar may survive in spirit but its *implementation rules* don't. |
| **OPAC marked for removal**, `caero_claims` v1 in place. | Still relevant; need to confirm claim semantics on small spawn islands (do you even need to claim your own island? Auto-claim?). |
| **Refining is currently global** — anyone can build a refiner anywhere. | S3 may want central-island-only mid/high-tier refining to anchor the hub. Open question. |
| **Quality on stone/iron tools.** | Greg called this out as the worst early-game friction. Likely cut or deferred to mid-game tools. |

### 3.3 Missing — must be designed/built
| S3 need | Status | Closest options (from research) |
|---|---|---|
| Per-player spawn island placement | None | **Sky Archipelago** (NeoForge 1.21.1, has ocean-with-islands mode) + **Random and Set First Spawn**. Or: trivial glue mod that picks deterministic `(x,z)` per UUID and `setspawn`s. |
| Lethal swimming | Partial (TaN installed, vanilla drowning) | Tag ocean biomes cold via datapack so naked swimmers freeze (TaN handles the rest). Optionally: Cold Sweat for richer water-on-skin mechanics. |
| Dangerous sea creatures | Partial — Hybrid Aquatic installed | Add **Sea Myths** (deep-ocean bosses), **Upgrade Aquatic** (Thrashers), **Aquaculture 2** (hostile piranhas). |
| Vehicle tier curve | None — Create Aeronautics has no baked tiers | **Create Propulsion: Simulated** (community port, 1.21.1 NeoForge) adds tiered engines/propellers. Plus glue-mod "Captain's License" item issued at dock. |
| Vehicle-gated regions | None | Per-player WorldBorder (already installed; supports per-player borders) that expands when player holds a license item. |
| Anti-infinite-scaling | Partial (`caero_disable_loops`) | **Dynamic AH pricing** (sell price drops with recent volume) + **per-player daily mint cap** are the highest-leverage glue additions per Q2 research. |
| Win condition | None | Pure glue: monitor Numismatics balances, fire end-of-season event at threshold. |
| Phase progression | None | Could be implicit (vehicle tier acts as phase gate) or explicit (advancement-driven). Open question. |
| **Resource locality** for forced trade | Partial (caero_rings tier biases) | High tiers only on specific outer islands. Datapack-level worldgen change. |

### 3.4 The wipe question
Everything in S3 implies a **fresh world**: per-player spawn islands can't be retrofitted; ocean-topology worldgen can't be retrofitted; a "win condition" only makes sense on a fresh map; the current S2 economy is too far gone to rebalance without a reset. **Confirm explicitly that S3 = wipe.**

---

## 4. Heuristics — current vs. proposed for S3

### 4.1 Current pillars (`heuristics.md`, verbatim names)
1. **There must be a reason to trade.** ✅ Survives. Strengthen wording: *trade must be mandatory, not optional*.
2. **There must be a reason for transport and infra.** ✅ Survives. Tighten: vehicles must escalate in size to access new content.
3. **The game gets harder the further out you go.** ⚠️ Survives in spirit, but implementation flips from "radial" to "per-island difficulty." Rewording needed.
4. **There must be a reason to specialize.** ✅ Survives. Add: *specialization choice should be deferred out of early game*.

### 4.2 Greg's three new heuristics from the dump
- **H5 — Every player is competing to win.** The server has a single-axis win condition (e.g. first to N spurs). Every system serves that race.
- **H6 — Trade is mandatory, not incentivized.** It must be impossible to play self-sufficiently to the win condition. (Strict version of H1.)
- **H7 — Vehicle scale tracks progress.** Each phase requires a strictly bigger vehicle than the last. Bigger vehicles unlock new content. Smaller vehicles are eventually obsolete.

### 4.3 Proposed additional heuristic from the "early-game stone tools" lesson
- **H8 — Inconvenience must drive interaction.** Friction is acceptable only when it forces players into the economy. Friction without that payoff is just bad UX. (Concrete corollary: don't gate the first 30 minutes behind systems that require trade — players are still isolated.)

### 4.4 Existing economy principles (`economy-principles.md`) — already partly aligned
1. Resources are finite — no infinite generators. ✅ Directly addresses scaling exploit.
2. Economic primitives stay relevant. ✅
3. Multi-party trade by design. ✅
4. Excess drives trade — leveling forces over-production. ✅
5. Sinks balance sources. ✅
6. Catalysts are bonuses, never gates. ✅ — note this *contradicts* a strict reading of H6 ("trade must be mandatory"). Reconcile.

---

## 5. Where the vision is weak (critique, not decisions)

These are pressure points that need design work, not yes/no answers.

### 5.1 "First to N spurs" undermines H6 and H7
A pure-currency win condition rewards the cheapest minting loop, which is by definition the activity that needs the *least* trade and the *least* vehicle. The winner is whoever finds the highest-yield exploit and grinds it — they didn't have to depend on anyone or build a real airship. If the win condition is supposed to *require* the systems we've built, it has to be structured around them. Options:
- **Currency scoreboard + structural gate.** Minting only happens at the central island, only by docking a ship with tier-N cargo. Currency stays the visible scoreboard; the act of minting requires the systems.
- **Contract chain.** Win = first to complete the escalating delivery contract chain. Currency becomes incidental to progression rather than the goal itself.
- **Multi-axis.** Currency + N island claims + reach island Z. Forces breadth.

### 5.2 Spawn islands vs. "refine anywhere" — pick one
`caero_specialization` currently lets anyone build a refiner anywhere. If every spawn island has its own refiners, the central hub is optional, and H6 (mandatory trade) is dead the moment it lands. Options (mutually incompatible):
- **(a)** Mid/high-tier refining physically only happens on the central island. Player refiners cap at tier 1.
- **(b)** Spawn islands are deliberately resource-poor. Even tier-1 self-sufficiency is painful enough that you leave fast.
- **(c)** Industries are geographically split — some are island-only (e.g. forestry), others hub-only (e.g. armourer). Forces structural cross-trade.

### 5.3 Vehicle tier progression is not natural in Create Aeronautics
Bigger ships are more expensive but not *more capable in gating ways* — a small airship goes wherever a large one does, just slower with less cargo. "You need a bigger boat to reach island X" requires either:
- Installing **Create Propulsion: Simulated** and accepting its tier curve (less control, less work).
- Building gates ourselves (per-player WorldBorder + license items issued at the dock when you turn in tier-N goods). More control, but the narrative needs to make "Captain's License Tier 3" feel earned, not arbitrary.

### 5.4 "Deferred specialization" is the right lesson from the wrong-sized problem
The S2 early-game pain was specifically quality on stone/iron tools. That's a one-line config change (cap quality stamping at copper-tier and above). The XP/industry/quality model itself is sound and shouldn't be redesigned around this one UX miss.

### 5.5 Anti-scaling is the load-bearing problem and is currently under-weighted
Banning specific loops (`caero_disable_loops`) is whack-a-mole — the next exploit is always one Create gadget away. The structural fixes are:
- **Dynamic dock pricing** — sell price decays with recent volume per item. Doubling tree-farm output stops doubling income. Highest-leverage single change. Pure glue over Numismatics + `caero_auction`.
- **Per-player daily mint cap** — brutal but caps the leader by design.
- **Demand contracts (Albion model)** — the dock posts buy orders for specific goods; only filling those pays full; surplus glut sells at floor. Actively shapes what's worth producing instead of reacting to exploits.

Without one of these, every other system is downstream of "whoever picks the right loop wins by hour 30."

### 5.6 Scope vs. player count
7 industries × 4 quality tiers × 7 byproducts × catalyst chains, with 10 players max (probably 6–8 active). Some industries will have no buyers. Worth considering whether S3 *cuts* to 4–5 industries with deeper interdependence instead of broad sprawl. Also reduces "every player has to learn 7 systems" cognitive load.

### 5.7 Sunk-cost check
S2 already has a lot built. S3 throws out `caero_rings` (concentric → islands), partially neuters `caero_specialization` (early-game), keeps `caero_auction` / `caero_vitality` / `caero_claims` mostly intact, and adds 5+ new mods + several new glue components. Worth being explicit about what survives and what's rewritten — and whether the volume of new work is realistic on the implied timeline.

---

## 6. Open questions — answer these before planning lands

### 5.1 Scope / commitment
- **Q1.** Is S3 a full wipe? (Strongly implied by the topology change, but confirm.)
- **Q2.** Same player count target (10 max), same hosting (CPX42)?
- **Q3.** Is the seasonal nature explicit? (Server resets at win, or runs forever post-win?)

### 5.2 Win condition
- **Q4.** Single axis (first to N spurs) or multi-axis (first to N + own X claims + reach island Y)?
- **Q5.** What happens after someone wins? Wipe? Trophy + free play? Next season starts?
- **Q6.** Is there a soft win-rate cap (e.g. mint caps slow the leader) or do we let runaway wealth happen?

### 5.3 Topology
- **Q7.** **Sky Archipelago vs. custom worldgen.** Option A: install Sky Archipelago + replace `caero_rings`. Option B: rework `caero_rings` to seed islands in an ocean (more work, more control). Option C: skip Sky Archipelago and hand-build a few large islands at fixed coordinates.
- **Q8.** How big is a spawn island? Enough for shelter + 1 farm + a tree, or fully self-sufficient bootstrap (food, basic ores)?
- **Q9.** Is the central island a single island or a hub continent? Player-built, admin-built, or worldgen-placed?
- **Q10.** Are there *named* outer islands (like dungeon zones) or procedural?

### 5.4 Vehicle progression
- **Q11.** Install **Create Propulsion: Simulated** for natural tier curve, or build a custom license-item gating system?
- **Q12.** What does each vehicle tier physically *do* differently? (Speed? Cargo? Range? Or just "needed to cross the deeper sea"?)
- **Q13.** How do we enforce "you need a bigger boat to reach island X"? Per-player WorldBorder + license? Sea-monster density that scales out? Cold/storm zones?

### 5.5 Trade & specialization
- **Q14.** Is **central-island refining** a thing (i.e. mid/high tiers can only happen at the hub), or do refiners stay player-built anywhere? Central-only would force traffic through the hub.
- **Q15.** Is profession choice **truly deferred** (no skill XP awarded before some milestone) or **soft-deferred** (XP is awarded but tier-1 stuff is open to all)?
- **Q16.** Should the **dock be the only mint** (auction-house = pure player-to-player, dock = NPC sink that mints money), or can players still mint by selling to each other?
- **Q17.** Does **resource locality** ("T4 wood only on island Z") replace or augment specialization gates?

### 5.6 Anti-scaling
- **Q18.** Adopt **dynamic dock pricing** (sell price decays with recent volume per item)? This is the single highest-leverage anti-tree-farm fix.
- **Q19.** Adopt **per-player daily mint cap**? Caps the leader naturally; can be raised per phase.
- **Q20.** Adopt **wealth-scaled tax** on dock sales (the rich pay a higher %)? Slows the runaway leader.
- **Q21.** Are infinite generators **banned by rule** (Greg DMs offenders) or **prevented by mod**? `caero_disable_loops` is partial; Create fan-smelting still open.

### 5.7 Early-game friction
- **Q22.** Drop quality from stone/iron tier entirely?
- **Q23.** Drop the early-game refining requirement (let stone/iron be crafted naked)?
- **Q24.** Is Tough As Nails / temperature still on, or relaxed for spawn islands specifically?

### 5.8 Heuristics
- **Q25.** Adopt H5/H6/H7/H8 as written, or revise?
- **Q26.** Resolve the H6 ("trade mandatory") vs. economy-principle 6 ("catalysts are bonuses, never gates") tension — which wins?
- **Q27.** Cull or restate H3 (radial difficulty doesn't fit island topology).

### 5.9 Sequencing
- **Q28.** When do we want to ship S3? (Affects whether we adopt Sky Archipelago vs. custom worldgen, etc.)
- **Q29.** What's the minimum viable S3 — i.e. what can we cut and still call it Season 3?

---

## 7. Confirmed so far (2026-05-09)

- **Q1 — Wipe:** Yes, S3 is a full wipe.
- **Q4 — Win condition:** Pure single-axis "first to N spurs". No structural mint gate. **Implication:** §5.5 anti-scaling becomes load-bearing — it alone must channel players toward airships/trade/specialization, since the win condition no longer requires them.
- **§5.5 — Anti-scaling stack:** Three mechanisms, expected to compose:
  - **Dynamic dock pricing** — sell price decays with recent per-item volume.
  - **Demand contracts** (Albion model) — dock posts buy-orders; filling orders pays full, glut sells at floor.
  - **Wealth-scaled tax** — higher % the richer you are.
- **§5.6 — Industry count:** Cut from 7 to 4–5. Specific cuts TBD; Greg's lean: keep FUELER + MINING + ARMOURER + HUSBANDRY (foundational), keep HUNTER as connective tissue, drop ALCHEMIST + JEWELERY.

## 8. New idea: bounty pool (Greg, 2026-05-09)

The wealth-scaled tax should not disappear — it should **fund the demand contracts**. Rich players' tax pays for the buy-orders the dock posts, which lower-wealth players fulfill. Net effect: the rich literally fund the work that lets the poor catch up, without it feeling like charity (the receiver just sees "the dock has good buy prices today"). Pairs with the demand-contracts mechanism so cleanly that they should be designed as one system, not two.

Variants worth comparing:
- **Bounty pool → contract funding** (proposed, strongest).
- **Lagging-player stipend** (direct payout to bottom-half by wealth).
- **Public-goods fund** (shared infra: dock upkeep, warp service, etc.).

## 8b. Confirmed design rules (2026-05-09 user-story walkthrough)

- **Onboarding:** Visible beacon / pillar on the central island, seen from any spawn island.
- **First crossing:** Vanilla boats are out. Players must build a Create-Aeronautics-powered vehicle even for the first crossing.
- **Spawn island resource budget:** "Limited but not tiny" — enough for one bootstrap arc (basic ores in moderate quantity), not enough to run a permanent factory.
- **Kinetic power:** **No water wheels, no windmills.** Hand cranks and fuel-burning engines only.
- **Nether access:** Portals are on **T2 islands**, not spawn islands. Spawn-island players therefore cannot reach Blazes → cannot build Steam Engines. Spawn-island bootstrap is hand-crank only. This is intentional — slow and painful, makes leaving urgent.
- **Implied phase structure (emerging):**
  1. Spawn island, hand-crank everything, assemble first vehicle (slow, solo, painful).
  2. Cross to central hub, sell surplus, start specializing, meet other players.
  3. T2 island runs → nether access → Blaze → Steam Engine → real Create automation.
  4. Mid/late game: outer islands, bigger vehicles, push for the win.
- **Vehicle progression:** **No vehicle tiers.** All vehicles are just vehicles — emergent scale, not categories. Bigger = more cargo, more HP, survives worse sea. No license items, no per-player borders, no "T2 vehicle" classification. The gate to further/harder islands is purely: your vehicle has to be big/tough enough to survive the trip.
- **Hub island design:** Small island, **no mineable resources**. Just shops + a per-player modest claim for refineries / personal infrastructure. This **resolves §5.2**: refining is hub-only by structural fact (you only have a claim there). Spawn islands cannot have refiners. Solo self-sufficient refining is impossible.

## 9. Still open

- **§5.2 — Spawn islands vs. refine anywhere:** Unresolved. Three mutually-incompatible options on the table:
  - Central-only mid/high-tier refining (forces hub use).
  - Resource-poor islands (soft incentive — risk: S1 showed soft incentives lose).
  - Industries geographically split (forces structural cross-trade, but adds geographic complexity).
- **§5.3 — Vehicle tier gating:** Create Propulsion: Simulated vs. custom license-item gates. No decision yet.
- **§5.6 — Which specific industries to cut.** Greg's lean is JEWELERY + ALCHEMIST, but not committed.

## 10. Suggested next focus

§5.2 (refining locality) is now the biggest open structural decision. Picking it determines what spawn islands actually feel like, whether the central island is mandatory or optional, and how the topology and vehicle-gating systems get designed downstream.

---

## 11. Topology pivot: image-driven map (Greg, 2026-05-10)

**This section supersedes §2.1, §2.2, and parts of §8b on topology.** The "per-player spawn island archipelago" model is replaced by a single shared continent whose biome layout is **drawn by hand as a PNG image** and read at world generation. All references below to "spawn island", "central island", and "outer islands" should be read as obsolete — the new primitives are **spawn zone**, **barrier zones**, and **outer zones** on a single contiguous map.

### 11.1 Core mechanic — image as worldgen source of truth

- The admin draws a **PNG map** of the world. Each pixel's RGB value selects a **zone**.
- A **zone** is *not* a single biome. It is a **theme** that maps to a small weighted set of eligible biomes (e.g. green-forest zone → `oak_forest | birch_forest | dark_forest`, picked locally by noise). One pixel does *not* need to equal one chunk; the pixel-to-block scale is a knob (likely 32–128 blocks/pixel — pin in §11.7).
- "Zone" replaces "biome" as the primary design vocabulary in S3 docs. The wiki and player-facing language can still say *biome* where it makes sense, but design discussion uses *zone* to avoid the ambiguity that one zone = many biomes.
- The image is the **single source of truth** for biome placement. No procedural ring layout, no `caero_rings`-style radial tiering. If the map says it, the world is it.

### 11.2 Spawn zone — shared, resource-poor, the new "hub"

- Single ~1000×1000-block area at world center. All players spawn here (no per-player islands).
- "Decent space to set up close by, but not super-super-close" — implies natural soft-clustering of bases without forcing literal coexistence.
- **Banned in spawn zone:** diamond, lapis, gold, iron (the vanilla iron). These ores do not generate inside the spawn-zone biomes.
- **What spawn does have:** a new resource, **slag iron** (§11.3), wood, stone, basic food, coal (probably).
- The spawn zone takes the structural role that §8b's "central hub island" played: it's where everyone starts, where players bump into each other, where early trade is forced because nobody has the things they need to leave.

### 11.3 Slag iron — the bootstrap material

A new ore + ingot, deliberately weaker than vanilla iron. **You cannot make tools or armor from slag iron.** It exists to unblock the early Create chain without giving spawn-zone players a path to self-sufficient tooling.

**Slag iron *can* craft:**
- Map (vanilla iron ingredient → slag iron ingredient).
- Propeller (Create Aeronautics).
- Andesite alloy (Create's foundational mid-component — currently iron + andesite).
- **Mechanical Press** (the Create press that produces iron sheets — the "thing that makes iron sheets"). Confirm this is the Mechanical Press, not Crushing Wheels; voice dump uses fuzzy language.

**Slag iron *cannot* craft:**
- Iron tools / iron armor (vanilla recipes for these become real-iron-only).
- Anything past mid-tier Create (steel-ish progression, Steam Engine, etc.).
- Anything that's currently a "real iron" recipe in `caero_specialization` quality stamping at iron tier or above — these stay real-iron-locked.

**Real iron** exists only outside the spawn zone, gated behind whichever barrier the admin draws between spawn and the iron-bearing zones. Same deal for gold, lapis, diamond — locked behind barriers.

**Open detail:** exact slag-iron-permitted recipe list needs to be enumerated as a datapack. First-pass list above is a starter; treat as proposal, not committed scope.

### 11.4 Barrier-gated outer zones

The new gating model is **environmental barriers between zones**, not vehicle tiers and not per-player WorldBorders. Each cardinal direction gets a different kind of barrier requiring a different kind of preparation:

- **North — cold mountain range.** Climate gate. Players need quality woolen gear (Tough As Nails) to ascend / cross. Behind it: presumably first source of real iron + the iron progression chain.
- **South — hot zone** (desert? volcanic? unspecified). Climate gate, opposite polarity. Needs cooling/hydration prep.
- **West — dangerous ocean.** Vehicle gate (boat / Aeronautics water vessel). Sea creatures (Hybrid Aquatic, Sea Myths, Upgrade Aquatic — all already on the radar in §3.3) make naked swimming lethal.
- **East — open. Candidates from voice dump:** big desert, sky islands, or a massive vertical pit / cavern-lake. Pick one and commit; this is the most under-specified barrier.

This is a real shift: §8b said *no vehicle tiers, no license items, no per-player borders, gates are emergent (your vehicle must be tough enough)*. The new model keeps "no license items, no per-player borders" but **replaces "emergent vehicle scale" with "specific environmental hazards per direction."** Vehicle scale still matters (you still need a boat for the sea), but it's no longer the *only* axis of progression — climate + terrain + monster density share the load.

**This puts pressure on H3** ("vehicles must scale"). If the north gate is purely woolens-and-walking, vehicles add nothing there. We either accept that some directions don't reward vehicle scale, or we ensure every direction has *some* vehicle-relevance (a small airship trivializes the cold mountain crossing; a bigger one is needed for the deeper-sea zones; etc.). Default position: **most barriers should reward but not require vehicles, except the sea (which requires one).** Revisit at §11.7.

### 11.5 Nether and End as overworld zones

**No portals.** The Nether and the End are no longer separate dimensions reached via portals — they are large zones drawn directly onto the overworld map. Inside a Nether zone, sub-zones for the various Nether biomes (crimson forest, soul valley, basalt deltas, warped forest, nether wastes) are drawn as distinct pixel colors. Same model for the End (end highlands, end midlands, end barrens, small islands, central island, dragon area).

This **supersedes** the §8b decision that nether portals live on T2 islands. Greg explicitly removed portals from the model in this dump.

**Implementation implications — non-trivial:**
- Nether and End biomes don't ordinarily generate in the overworld. They'd need to be re-bound to the overworld biome registry, or we'd need a custom biome source that explicitly *can* place them.
- Nether terrain features (lava seas, soul sand valleys, ghasts spawning, Nether-only mob spawn rules) need to either follow the biome into the overworld (likely, if the biome-driven mob spawn lists carry over) or be replicated. Test this empirically — vanilla biome → mob-spawn-list mapping is biome-resident, so nether mobs *should* spawn in an overworld-placed nether biome, but it needs verifying.
- The Ender Dragon as content is a separate problem. Not solving here. Note as a §11.7 open item.
- Lighting / sky: nether biomes in the overworld will still have an overworld sky and day/night cycle. This may or may not be acceptable. Vanilla "fog" is biome-effect-driven; nether fog may follow correctly. End sky / void floor cannot be reproduced without the dimension. **Accept as a known cosmetic compromise** unless a mod resolves it cheaply.

### 11.6 Conflicts with prior confirmed decisions — what gets retracted

| Earlier decision | Status under §11 |
|---|---|
| §2.1 "per-player spawn island in an actual ocean" | **Retracted.** Replaced by shared spawn zone (§11.2). |
| §2.1 "central island — shared infrastructure" | **Retracted as a separate island.** The spawn zone *is* the central hub now. Its resource-poor nature still funnels players outward. |
| §2.1 "outer / harder islands, gated by vehicle capability" | **Retracted as islands.** Replaced by outer zones gated by environmental barriers (§11.4). |
| §2.2 phase progression (boat → bigger boat → cargo ship → airship) | **Retracted in its current form.** Phases now read as: spawn zone bootstrap → first barrier crossing → real-iron tier → outer zones → Nether/End zones → late game. Vehicle scale is still *a* lever, not *the* lever. |
| §8b "Visible beacon on the central island, seen from any spawn island" | **Retracted.** No spawn islands; no isolated central island. Onboarding cue needed for *which direction to head first* — could be a literal in-world signpost / pillar / quest-style hint at spawn. Open. |
| §8b "Vanilla boats are out, must build an Aeronautics vehicle for the first crossing" | **Partially retracted.** The first crossing is no longer water — depending on which barrier you tackle first, it might be a mountain hike, a desert trek, or a sea voyage. Aeronautics-vehicle-mandatory only applies to barriers that actually require one. Sea barrier still does. Cold mountain barrier doesn't (yet). |
| §8b "Nether portals on T2 islands" | **Retracted.** Nether is an overworld zone; no portals exist (§11.5). |
| §8b "Hub island has the per-player claims for refineries" | **Survives in spirit.** The spawn zone now plays this role — small per-player claim allowance for refineries / personal infra inside spawn. **Resolves §5.2 tentatively:** mid/high-tier refining is structurally restricted to wherever the admin draws the spawn zone's claim allowance, since spawn zone is the only place with stable shared infrastructure. Confirm. |
| §3.2 `caero_rings` (concentric difficulty rings) | **Already flagged as a conflict; now firmly thrown out.** Image-driven worldgen replaces it entirely. The work invested in `caero_rings` for ocean grading / tier blending does not carry over directly, but the *biome-to-tier* mappings (which ores generate in which biomes, which mobs spawn where) do — those become the basis of the zone-color → biome-set mappings in the new system. |

### 11.7 Open questions raised by §11

These need answers before implementation can commit:

- **Q30 — Pixel scale.** Blocks per pixel? (32 → 312×312 px image for a 10k×10k world; 64 → 156×156; 128 → 78×78.) Higher resolution = more drawing precision but more work; lower = blockier zone edges. Default proposal: **64 blocks/pixel** (1 chunk = 4 blocks, so 1 px ≈ 1 zoomed-out chunk-of-chunks).
- **Q31 — World size.** Is the world still 10k×10k (the current border)? Larger? A larger world makes Nether/End zones less crammed but more empty travel.
- **Q32 — Zone-to-biome mapping format.** Probably JSON: `{ "#3a8e5c": { "biomes": [{ "id": "minecraft:forest", "weight": 3 }, { "id": "byg:autumnal_forest", "weight": 1 }] } }`. Per-zone noise-driven sub-selection or strict-deterministic? Lean: weighted-noise (so adjacent same-color pixels can resolve to different specific biomes, breaking up monotony).
- **Q33 — Slag-iron recipe ledger.** Enumerate the exact recipes that switch iron → slag iron (Map, Propeller, Andesite Alloy, Mechanical Press confirmed; what else? Tracks? Crafting Table iron parts? Hopper? Bucket?). Single datapack producing all overrides.
- **Q34 — East barrier identity.** Desert? Sky islands? Vertical pit? Pick one.
- **Q35 — Real-iron locality.** Real iron lives in which zones specifically? Behind north barrier? Behind every barrier? Behind any one barrier? This sets the early-mid game progression curve.
- **Q36 — Nether/End mob behaviour.** Confirm via test that overworld-placed nether/end biomes still spawn nether/end mobs from the vanilla biome→mob mapping. If not, custom spawn rules required.
- **Q37 — End game.** Is the Ender Dragon part of the design (fight in the end zone)? Or removed as a S2-style boss-irrelevance carryover?
- **Q38 — H3 (vehicles must scale).** Does §11 weaken H3? If only the sea barrier requires a vehicle, three of four cardinal directions reward vehicle-skill but don't require one. Either accept that and revise H3, or design more vehicle-mandatory barriers.

### 11.8 Implementation scoping

The voice dump ends with: "let's get working on this, basically, both in terms of the reading of the map and in terms of these drastic changes that we feel like we need to make, and then we can worry about the specific making specific changes into the biomes later on." Concrete pieces of work, smallest to largest:

1. **Slag iron datapack + tiny glue mod.** New ore (`caero:slag_iron_ore` + `caero:raw_slag_iron` + `caero:slag_iron_ingot`), recipe overrides for the ~6 starter recipes (§11.3), worldgen placement rule restricted to spawn-zone biomes. Standard glue-mod scope, no risk. Can ship before the worldgen system is finalized — slag iron just needs a "spawn zone biome tag" to attach to, even if the biome is provisional.
2. **Resource-banlist datapack.** Override vanilla iron / gold / lapis / diamond ore generation rules so they don't appear in spawn-zone biomes (or the inverse — only generate in tagged outer biomes). Pure datapack.
3. **Climate barriers via existing mods.** Tough As Nails already installed — north/south barriers are *configuration* (right cold/hot biome temperatures + woolen gear thermal values), not new code. Lowest-cost barrier path.
4. **Image-driven biome source — likely "buy" not "build".** Research returned a strong candidate: **NovoAtlas** (Modrinth: `novoatlas`, NeoForge `1.1.0+1.21.1` published 2025-07-19, server-side-required / client-side-unsupported, derived from itsmiir's CC0 `Atlas` mod). It does precisely what the design asks: admin draws a grayscale heightmap PNG + an RGB biome-map PNG, ships them as a datapack, the worldgen reads RGB → biome via JSON config. Server-side-only means players don't install anything. Evidence: `.research/novoatlas/project.json`, `.research/novoatlas/versions.json`. **Next action — install on a test instance and confirm:** (a) RGB → weighted biome-set mapping (zone concept), not just RGB → single biome; (b) Nether/End biomes are placeable from the overworld biome map (or whether NovoAtlas restricts to overworld biomes); (c) interaction with our existing Terralith/biome-tagging stack — does NovoAtlas *replace* the biome source entirely or layer on top? If (a) is single-only or (b) is restricted, we either fork NovoAtlas (CC0-derived, permissive) or fall back to a custom BiomeSource. Custom path is documented at `BiomeSource#getNoiseBiome(x,y,z, sampler)` (4×4×4 cell granularity) — kept as fallback only.
5. **Zone → biome-set JSON config.** Either NovoAtlas's native config format, or our own if we end up custom. Loaded once at world creation.
6. **Nether/End-in-overworld biome registration.** Ensures these biomes are *eligible* for placement by the BiomeSource. Registry-binding work; small but needs care to not break vanilla nether/end (which still exist as dimensions even if we don't use portals — just leave them alone).
7. **Specific biome polish.** Per-biome ore tables, per-biome mob lists, per-biome features. Explicitly deferred per voice dump.

**Sequencing recommendation:** items 1–3 are low-risk and can land before the worldgen system is settled, building player-facing material that survives any worldgen approach. Item 4 is the load-bearing decision and gates 5–7. Hold off on 4 until §11.7 Q30, Q32 are answered and the research agent returns its build-vs-buy recommendation.

---

## 12. Karos map v1 — zone-to-biome mapping (Greg, 2026-05-10)

Greg painted the first iteration of the Karos overworld mask. Saved at `season3/karos-map-v1.png` (raw) and `season3/karos-map-v1-numbered.png` (with the 13 dominant zones numbered for reference). Image is 754×742 px; final pixel-to-block scale TBD (§11.7 Q30) but for a 10k×10k world that's ≈13.3 blocks/px — much finer than the 64-bpp default proposed in §11.7. Either accept the higher resolution or upscale the world to match.

### 12.1 Confirmed zone semantics

Pulled from voice dump 2026-05-10. **Color → zone identity** is now committed; the **biome candidates** column is a first-pass mapping against vanilla + Regions Unexplored (the only modded biome source currently installed) — those are still tunable.

| # | Hex | Zone identity (Greg) | Biome candidate(s) — vanilla baseline | Notes / dependencies |
|---|---|---|---|---|
| 1 | `#000C24` | Cold ocean (provisional — Greg floated "leave empty / void" but defaulting to cold ocean for Q41) | `minecraft:cold_ocean`, `minecraft:deep_cold_ocean` | Resolves Q41 below. |
| 2 | `#014A01` | Cold deep forest with massive trees ("really deep, dangerous forest") | `minecraft:old_growth_spruce_taiga`, `minecraft:old_growth_pine_taiga` (RU options exist) | Greg's first read was "dark oak forest"; corrected mid-dump to cold/dangerous. |
| 3 | `#0003B3` | Deep ocean | `minecraft:deep_ocean`, `minecraft:deep_lukewarm_ocean` | |
| 4 | `#444444` | High mountains | `minecraft:jagged_peaks`, `minecraft:frozen_peaks` | The cold-mountain barrier; pairs with TaN cold-gear gating. |
| 5 | `#EC0101` | The Nether — placed in the overworld (no portals) | `minecraft:nether_wastes`, `minecraft:crimson_forest`, `minecraft:warped_forest`, `minecraft:soul_sand_valley`, `minecraft:basalt_deltas` | **Gated by Q39:** does NovoAtlas accept Nether biomes for overworld placement? Research in flight. |
| 6 | `#016F01` | Cold forest (multi-type, doesn't have to be one biome) | `minecraft:taiga`, `minecraft:snowy_taiga`, `minecraft:grove` | Easy zone — vanilla covers it. |
| 7 | `#888888` | Stony, mining-rich biome (frames the high mountains) | `minecraft:stony_peaks`, `minecraft:windswept_hills`, `minecraft:windswept_gravelly_hills` | "Stony biome rich in mining" — implies our ore-density datapack adjusts ratios here, not just biome choice. |
| 8 | `#E2F700` | Desert | `minecraft:desert` | Hot-side TaN gate lives here. |
| 9 | `#ECA901` | Badlands | `minecraft:badlands`, `minecraft:eroded_badlands`, `minecraft:wooded_badlands` | |
| 10 | `#DF08EE` | The End — placed in the overworld (no portals) | `minecraft:end_highlands`, `minecraft:end_midlands`, `minecraft:end_barrens`, `minecraft:small_end_islands` | **Gated by Q39** (same as Nether). The Ender Dragon question (§11.7 Q37) is a separate open item. |
| 11 | `#00AAB3` | Warm ocean | `minecraft:warm_ocean`, `minecraft:lukewarm_ocean` | |
| 12 | `#FF025A` | Floating sky islands ("from another mod") — small scattered blobs | TBD — needs mod or vanilla void-biome trick | **Gated by Q40:** is there a maintained 1.21.1 NeoForge sky-island mod, or do we improvise from vanilla? Research in flight. |
| 13 | `#0FB300` | Plainsy spawn biome — the lime sliver at center (≈0.9% area, ~950×950 blocks at the current world size — matches Greg's 1000×1000 spawn-zone target) | `minecraft:plains`, `minecraft:sunflower_plains`, `minecraft:meadow` | This is the spawn zone. Slag-iron-only ore generation rules attach to this biome tag. |

### 12.2 Nested-color question — answered

| Pair | Decision |
|---|---|
| 4 (dark gray) + 7 (light gray) | **Two distinct zones.** 4 = high mountains (the cold barrier); 7 = stony mining-rich biome that wraps the mountains. Different mob/ore profiles. |
| 8 (yellow) + 9 (orange) | **Two distinct zones.** 8 = desert (hot-side climate gate); 9 = badlands (different resource profile, different feel). |
| 6 (medium green) + 13 (lime) | **Two distinct zones.** 6 = cold forest (outer); 13 = plains spawn biome (center). The spatial nesting is the topology — spawn is *inside* the cold-forest belt. |

### 12.3 Conflicts with §11.4 — actual map supersedes the cardinal-barrier framing

§11.4 sketched four cardinal barriers (cold north / hot south / sea west / TBD east). The actual painted map is messier and better:

- **North of spawn:** cyan warm ocean → blue deep ocean → magenta End at the far north.
- **South of spawn:** yellow desert + orange badlands → red Nether at the far south.
- **East of spawn:** medium-green cold forests forming a dense belt; deep-green dangerous-forest pockets scattered.
- **West of spawn:** medium green forest → cyan ocean → light-gray stony belt → dark-gray high-mountain interior.
- **Sky:** scattered hot-pink sky islands at varying compass positions.

Implications:
- The **cold barrier** is now west (gray mountains) rather than north — and reaching it requires crossing warm ocean first (so it's a *combined* sea-then-cold gate, not pure-cold).
- The **hot barrier** runs south through desert + badlands before reaching the Nether biomes.
- The **Nether and End** are at the cardinal extremes (south / north respectively), making them effectively the latest-game zones by travel distance — consistent with their resource value.
- §11.4 Q34 ("east barrier identity") **closes** — the east is a long cold-forest belt + scattered deep-forest pockets, not a single named barrier.

### 12.4 New open questions raised by the painted map

- **Q39 — NovoAtlas Nether/End acceptance.** ✅ **Answered: YES, no restriction.** Source confirms `ColorMapBiomeProvider.getBiome()` (`.research/repos/novoatlas/common/src/main/java/com/thedeathlycow/novoatlas/world/gen/biome/provider/ColorMapBiomeProvider.java` lines 52–69) does a flat `color → Holder<Biome>` lookup with **zero dimension filtering**. Any biome registry id — including `minecraft:crimson_forest`, `minecraft:end_highlands`, etc. — will be placed wherever the biome map calls for it. **Zones 5 (Nether) and 10 (End) are unblocked at the biome layer.** Caveat: this only proves the *biome* gets placed. Whether Nether-mob spawn lists, Nether ambient effects (lava seas as biome features, ghasts, etc.) all carry over correctly into an overworld-placed Nether biome is a separate empirical question — needs in-world testing once NovoAtlas is installed.
- **Q40 — Sky island mod.** ⚠️ **Answered with a caveat — sky islands are harder than the other zones and likely deferred from v1.** Findings:
  - **The Aether** (Modrinth `aether`, 1.21.1 NeoForge, 5.1M downloads, last update 2025-10-03) is the strong general-purpose option. Evidence: `.research/sky-islands/aether.json`. **But**: Aether's floating-island *terrain* is generated by the Aether dimension's noise settings, not by its biomes. NovoAtlas places biomes onto the existing overworld noise settings; it does **not** swap out the overworld terrain generator. So referencing an Aether biome from our biome map would produce overworld-shaped terrain decorated as Aether — *not* actual floating islands with void underneath.
  - **Vanilla "high min_y + void floor"** approach has the same problem in reverse: NovoAtlas reads a heightmap PNG, so we could paint *very tall mountains* in zone 12 — but tall mountains aren't floating islands either; they're connected to the ground.
  - **What's actually required** for true floating islands is **density-function-level changes to the overworld noise settings** (carve out the under-side, leaving a terrain shelf at high Y with void below). That's a Lithostitched / data-driven worldgen task, not a biome task — substantially heavier than the rest of §12.
  - **Recommendation: defer floating sky islands from Karos v1.** Either (a) repaint zone 12 as a different biome (mushroom fields? cherry grove? something visually distinctive) and revisit floating-terrain in a v2 polish pass, or (b) keep the magenta dots as ordinary tall mountains via the heightmap PNG (still distinctive, no void). Greg's call.
  - ✅ **Decided 2026-05-10 (Greg):** Defer entirely. Greg will paint a separate floating-island mask later. For Karos v1, **map `#FF025A` to `minecraft:cold_ocean`** (same as the surrounding navy) so the dots disappear into the surrounding terrain — except for the one in zone 6 (right-side green forest) which we map to the same cold-forest biome set as zone 6. The magenta pixels become invisible until the v2 floating-island mask overrides them.

### 12.4.1 Closed questions (decisions 2026-05-10)

- ✅ **Q39 — Nether/End acceptance.** Yes, no restriction (source-confirmed).
- ✅ **Q40 — Sky islands.** Deferred to v2; placeholder mapping above.
- ✅ **Q41 — Navy.** Cold ocean. (`minecraft:cold_ocean` + `minecraft:deep_cold_ocean` weighted set.)
- ✅ **Q42 — Map scale.** **5k×5k testing world.** 754×742 px image at 5000-block world = ~6.6 blocks/pixel. Border set to 5000. Greg wants the scale to be a **swappable knob** so the same map can drive 5k (test), 10k, or larger (prod) without redrawing — verify NovoAtlas exposes a `pixel_size` / `blocks_per_pixel` config field, document it as the swap point.
- ✅ **Q43 — Spawn biome tag.** `caero:spawn_zone` confirmed. Slag-iron worldgen rules and resource-banlist datapack will key on this tag.
- **Q41 — Navy = void or cold ocean?** Greg floated "leave it empty" for `#000C24` but acknowledged uncertainty about whether that's possible. **Provisional answer: cold ocean.** True void at the world edge would create a hard "world ends mid-air" cliff that's likely worse UX than a finite cold sea + WorldBorder. Confirm.
- **Q42 — Map resolution mismatch.** 754×742 px at 64 blocks/pixel = ~48k×47k world (5× the current 10k border). Either (a) shrink resolution to the 10k border (each px = ~13 blocks, finer than designed), (b) keep 64 bpp and grow the world to match (huge — likely impractical for a 10-player server with the existing CPX42 host), or (c) pick an intermediate scale. Lean: **(a) — keep the 10k world, treat px ≈ 13 blocks; the painting precision is fine at that scale.**
- **Q43 — Slag iron biome tag.** Slag iron ore generation gates on biome tag `caero:spawn_zone` or similar. Tag should attach to whatever biome(s) end up in zone 13 (lime plains) — confirm before we start the slag-iron datapack.

### 12.5 Closes / supersedes

- **Closes Q34** (east barrier) — answered by the painted map.
- **Supersedes §11.4** cardinal-barrier framing — the painted map is the source of truth; §11.4 is preserved for the *design intent* (climate gates, sea gates, etc.) but the specific compass directions in §11.4 are wrong.
- **Tightens §11.2 spawn zone** — the spawn zone is concretely zone 13 (lime), not an abstract "1000×1000 area." Per Q42 resolution it's ~950×950 blocks at the current border, close enough.

### 12.6 Karos v1 datapack — **verified end-to-end 2026-05-10** ✅

Boot test against a dedicated NeoForge 1.21.1 server with NovoAtlas 1.1.0 + the `karos-datapack` from `season3/karos-datapack/`. **22/22 /locate biome probes passed.** Test harness at `season3/test/karos-mapgen/`, runnable with `bash season3/test/karos-mapgen/run.sh`.

**What's confirmed working:**
- Image-driven biome placement: each painted color resolves to its mapped biome.
- **Nether biomes in the overworld** (no portals): all 5 — `nether_wastes`, `crimson_forest`, `warped_forest`, `soul_sand_valley`, `basalt_deltas` — found at the painted Nether zone (world z ≈ +1400 to +1800).
- **End biomes in the overworld**: all 4 — `end_highlands`, `end_midlands`, `end_barrens`, `small_end_islands` — found at the painted End zone (z ≈ -2000).
- Spawn zone correctly contains all three plains variants (`plains`, `sunflower_plains`, `meadow`) within 100 blocks of origin.
- Mountains, stony belt, desert, badlands, warm/deep/cold ocean, taiga, old_growth_spruce_taiga all land in their painted positions.

**Known caveats / outstanding follow-ups:**
- Caveat is empirical-only: this proves **biomes** are placed correctly. It does **not** prove that Nether-resident features (lava seas, ghasts spawning, soul-fire interactions, ambient fog) all carry over correctly into an overworld-placed Nether biome. That's a player-experience test — boot the world, walk into the painted Nether zone, see if it feels Nether-y.
- Heightmap is procedurally generated from per-zone target Y levels (script-driven). Greg can paint a custom heightmap PNG later if the procedural one doesn't shape the world the way he wants.
- A handful of stray pixels exist where the source mask had transitional anti-aliased colors that snapped to unexpected zones (e.g., one frozen_peaks pixel at world (1184, 1024) far from the painted mountains). These are quantization artifacts — invisible at world scale but worth a cleanup pass on the source mask if it bothers anyone.

### 12.7 Implementation findings — what we learned the hard way

Five non-obvious things uncovered while making this work:

1. **NovoAtlas's `horizontal_scale` config doesn't exist on 1.21.1 NeoForge.** It was added in NovoAtlas 1.2.0, but only the 1.21.4+ Minecraft line got that release. The 1.21.1 builds (`1.0.1+1.21.1`, `1.1.0+1.21.1`) silently ignore the field via `optionalFieldOf("scaling")`'s parse-error swallowing. **Workaround:** Greg's "swap world size" knob (Q42) lives in the `generate-karos-datapack.py` script, not in the JSON. Re-run with `--world-size N` to upsample the painted mask to N pixels (NEAREST_NEIGHBOR, preserving exact RGBs), and the world becomes N×M blocks at NovoAtlas's default scale of 1.

2. **At default scale, 1 image pixel = 1 BLOCK, not 1 biome cell.** `ColorMapBiomeSource.getNoiseBiome` calls `QuartPos.toBlock` on the biome-cell coords before sampling, so `MapImage.sample` operates in block-space. So a 5000-block world needs a 5000-pixel-wide image, not 1250.

3. **NovoAtlas places the painted-image center at world (0, 0).** A painted spawn zone won't actually contain the spawn block unless its centroid is at the image center. We auto-center by computing zone 13's centroid and translating the image so it lands at world (0, 0). Greg can re-paint a centered mask for v2 if the auto-shift produces ugly results.

4. **NovoAtlas's `ColorMapBiomeProvider.getClosest` fallback has an operator-precedence bug** — `color & 0xFF0000 >> 16` parses as `color & 0xFF`, so all three "channel" extractors return the blue byte. Non-exact-match colors collapse to "the biome with the closest blue channel," which is meaningless. **Workaround:** set `"strict": true` on the surface_biomes block to disable the fallback and route unknown colors to `default_biome` instead. (Our quantized PNG only has the 33 known colors anyway, so the fallback should never fire — but strict is defense in depth.)

5. **`/execute if biome` doesn't reliably fire on freshly-generated chunks** in NeoForge 1.21.1 + NovoAtlas 1.1.0 — the marker say-output never shows up in stdout even when /locate biome confirms the biome IS at the queried position. Use `/locate biome <id>` instead for harness verification. Possibly a chunk-load-state race; not worth digging into for the test harness.

**For Greg, in-game:** install NovoAtlas + this datapack into the live PrismLauncher instance, create a new world, fly around. Headline checks: do the spawn coords land in plains? Walk far south — does it look and feel like the Nether? Walk far north — does it feel like the End? Do mob spawns make sense in those zones? That's the next gate; everything mod-mechanical is verified.

### 12.8 Nothing deployed yet — next actions

1. **Visual-soak test in PrismLauncher.** Install NovoAtlas 1.1.0 and the karos-datapack, create a fresh world, and verify the player experience matches the design intent. Specifically: do Nether biomes spawn ghasts and have lava seas in the overworld zone, or do they look like normal land with red textures?
2. **Tune the source mask** if visual issues need fixing — for example, add a separate floating-island mask (deferred zone 12), repaint zone 13 larger if 0.94% spawn area feels too small.
3. **Then** ship §11.8 items 1–3 (slag iron datapack, resource banlist, climate barriers) — these are independent of the worldgen mod choice.

### 12.7 NovoAtlas config schema (verified against `.research/repos/novoatlas/` source)

**Datapack layout** (one namespace per world; we'll use `caero_karos`):
```
data/caero_karos/
├── novoatlas/
│   ├── biome_map/karos.png      # the painted RGB mask
│   ├── heightmap/karos.png      # grayscale terrain shape
│   └── map_info/karos.json      # ties images + biomes together
data/minecraft/
└── dimension/overworld.json      # overrides vanilla overworld with novoatlas:image_map
```

**`map_info/karos.json` shape:**
```json
{
  "starting_y": 6,
  "scaling": { "horizontal_scale": 6.63, "vertical_scale": 1.0 },
  "height_map": "caero_karos:karos",
  "surface_biomes": {
    "map": "caero_karos:karos",
    "biomes": [ { "biome": "minecraft:cold_ocean", "color": "#000C24" }, ... ]
  }
}
```

**`dimension/overworld.json` shape** (overrides vanilla overworld):
```json
{
  "type": "minecraft:overworld",
  "generator": {
    "type": "novoatlas:image_map",
    "map_info": "caero_karos:karos",
    "settings": "minecraft:overworld",
    "underground_density_function": "novoatlas:caves",
    "biome_source": {
      "type": "novoatlas:color_map",
      "map_info": "caero_karos:karos",
      "default_biome": "minecraft:cold_ocean"
    }
  }
}
```

**Key technical findings:**

- **`horizontal_scale` is a positive float** (`MapScaleConfig.java:14`). Default 1.0 = 1 px → 1 block. For Karos at the 5k×5k test world: `5000 / 754 ≈ 6.63`. Swap to `13.26` for 10k, `26.52` for 20k, etc. **This is the Q42 swap knob.**
- **`vertical_scale`** also a float, default 1.0. Compresses or expands the heightmap's Y range.
- **`starting_y`** is the base Y level the heightmap scales from. Avila uses 6 — we'll match it for now.
- **Heightmap is a separate grayscale PNG** at `data/<ns>/novoatlas/heightmap/<name>.png`. Required, not optional. Greg painted only the biome map, so we need to **generate a heightmap** procedurally — simplest first pass: per-zone target Y level + Gaussian smoothing across boundaries to avoid cliffs.
- **`underground_density_function`** controls cave generation. NovoAtlas ships three options (`novoatlas:caves`, `novoatlas:no_caves`, `novoatlas:no_cave_entrances`). Default to `novoatlas:caves` (vanilla-ish cave system).
- **`default_biome`** fills any RGB color not in the explicit list. Setting to `minecraft:cold_ocean` means stray pixels (anti-aliased edges) blend into ocean rather than producing visible artifacts.

**⚠️ Important correction — single biome per color, not weighted sets.**

Earlier (§11.8 / first NovoAtlas research) I described NovoAtlas as supporting "weighted biome pools per color" matching Greg's *zone* concept (one zone color → multiple thematically-related biomes picked locally). **The source disproves that.** `BiomeColorEntry` is a flat record of `{ biome, color }` — one biome, one color, no weights. Verified at `common/src/main/java/com/thedeathlycow/novoatlas/world/gen/biome/provider/ColorMapBiomeProvider.java`.

**Implications for Greg's zones-as-themes design:**
- For **v1**, each painted color resolves to **one specific biome**. Multi-biome zones (e.g., "color 6 cold forest can be taiga *or* snowy_taiga *or* grove") need to be expressed by **painting multiple shades** per zone (e.g., shade 6a → taiga, shade 6b → snowy_taiga, shade 6c → grove), not by a single color.
- **Two paths forward:**
  - **(a)** Accept single-biome-per-color for v1 and pick the strongest single biome per zone (12 colors, one biome each — see proposed mapping in §12.8 below). Repaint to add shade variants in v2.
  - **(b)** Repaint the v1 mask now to add shade variants per zone. Doubles painting effort but unlocks the "multi-biome zone" intent immediately.
  - **(c)** Fork NovoAtlas to add weighted-pool support. CC0-derived, permissive, but real Java work.
- **My recommendation: (a).** The single-biome-per-color limit is fine for proving the system end-to-end. Visual richness from multi-biome zones is a polish concern — addressable in v2 by repainting (b), which is cheaper than (c).

### 12.8 Proposed v1 single-biome-per-color mapping

Pending Greg's ack on path (a) above. One biome per zone, picked as the strongest single representative. All vanilla — no Regions Unexplored references in v1 to keep the test clean.

| # | Hex | Biome (v1) |
|---|---|---|
| 1 | `#000C24` | `minecraft:cold_ocean` |
| 2 | `#014A01` | `minecraft:old_growth_spruce_taiga` |
| 3 | `#0003B3` | `minecraft:deep_ocean` |
| 4 | `#444444` | `minecraft:jagged_peaks` |
| 5 | `#EC0101` | `minecraft:nether_wastes` |
| 6 | `#016F01` | `minecraft:taiga` |
| 7 | `#888888` | `minecraft:stony_peaks` |
| 8 | `#E2F700` | `minecraft:desert` |
| 9 | `#ECA901` | `minecraft:badlands` |
| 10 | `#DF08EE` | `minecraft:end_highlands` |
| 11 | `#00AAB3` | `minecraft:warm_ocean` |
| 12 | `#FF025A` | `minecraft:cold_ocean` (placeholder, sky-island deferred) |
| 13 | `#0FB300` | `minecraft:plains` |

Default biome (any color not exactly matching above) → `minecraft:cold_ocean`.

Caveat: the painted PNG has anti-aliased edges (hundreds of in-between RGBs along zone borders). Before shipping, **quantize the mask to exactly these 13 RGB values** (snap each pixel to the nearest of the 13 by Euclidean distance in RGB space). Otherwise ~1–5% of the world along zone borders falls through to the default biome — visible as ribbons of ocean between zones. Quantization is a one-shot script.

---

## 14. Resource tier system & barrier taxonomy (proposal, 2026-05-10)

**Status:** Proposal — captures the brainstorm above so the design has one place to argue against. Most of this is **not committed**; treat each subsection as a decision waiting on Greg's redline. Where we explicitly decided something verbally, it's marked **DECIDED**.

### 14.1 Two-axis progression frame

Karos progression resolves on two independent axes that compose:

- **Vertical — Create-tier:** `slag iron → real iron → brass → (steel?)`. Determines *what you can build*. Independent of where you live in the world.
- **Horizontal — axis-tier:** per-direction ladder of barriers and resources, increasing as you push deeper along an axis. Determines *what materials you can reach to feed the vertical*.

A "destination" in Karos is a `(vertical demand, axis depth)` pair. The Mechanical Press (low vertical, no horizontal) is reachable at spawn. Brass casings (mid vertical, requires hot-axis tier-3 for blaze) require traversing one specific axis. End-tier loot (high vertical + deepest horizontal) requires both.

This frame matters because it lets us answer placement questions consistently: "where does X go?" becomes "what vertical and horizontal tier is X meant to gate?"

### 14.2 Slag iron — recipe ledger (proposal)

Greg's §11.3 draft is `Map / Propeller / Andesite Alloy / Mechanical Press`. Expanded ledger below — principle: **slag iron is enough to *leave* the spawn zone; real iron is required to *thrive* (combat, automation scale, repair, mid-tier Create).**

**Allow with slag iron — bootstrap critical path:**
- Andesite Alloy, Shaft, Cogwheel, Hand Crank
- Mechanical Press (the *machine* — but iron sheets, its **output**, require real iron, so the press doesn't skip the gate)
- Encased Fan, Mechanical Bearing, Mechanical Mixer, Basin
- Bucket (water-on-lava generators already disabled by `caero_disable_loops`; lack of bucket is a hidden friction tax with no upside)
- Shears (wool → sails → propellers; gating this kills H2 from spawn)
- Flint & Steel, Map, Compass
- Belt, Chute, Andesite Funnel
- Physics Assembler (Aeronautics) — non-negotiable
- Propeller, Symmetric Sail, Hot Air Burner, Hot Air Envelope — spawn zone *must* be able to build one viable vehicle

**Real-iron only — the actual gate:**
- **Iron sheets** ← load-bearing rule. Gates engine assembly, brass progression, most of mid-Create for free
- **Engine Assembly / Portable Engine** ← belt-and-suspenders gate via the same chain
- Anvil, Hopper — both are huge QoL/automation crutches; should feel like a milestone
- Iron tools and armor
- Crossbow
- Mechanical Drill, Mechanical Saw, Mechanical Harvester, Deployer, Mechanical Arm, Mechanical Crafter — every "real automation" Create block
- Brass and downstream (brass casing, brass funnel, brass tunnel) — naturally gated by Blaze (south Nether zone) but worth being explicit so no side door appears

**Q44 — undecided edge cases:** Iron Bars, Cauldron, Smoker, Blast Furnace, Lantern (uses iron nugget). Need a one-pass review of the Create + vanilla recipe set to catch back doors.

### 14.3 Forestry — biome-tagged log quality cap

Don't build a separate "potent charcoal" mechanic. `caero_specialization` already has per-stack quality on the fueler chain. Implement as: **biome tag → log species → quality cap clamped on refiner output.**

- Spawn-zone trees (oak / birch / spruce) cap at ★1 charcoal regardless of refiner skill or catalyst
- Outer-zone tree species cap at ★2/★3/★4 depending on axis depth
- Same refiner block, same skill ladder, same catalysts — only the *log* carries the cap

Keeps the profession unified and tags "where this log was harvested" as the gating fact, not "what station processed it." A skilled fueler player who never leaves spawn maxes out at ★1 throughput; the same player gains real value by going to fetch (or buying) better logs.

**Q49:** which specific outer-zone tree species map to which cap tiers? (Lean: vanilla species in spawn → low; modded big-tree species in outer zones → high. Will need a per-biome-modifier datapack pass.)

### 14.4 Husbandry — biome-locked species

Two implementation paths:

- **(a) Biome-locked breeding** — glue mod hooks `BabyEntitySpawnEvent`, cancels if parent biome doesn't match. Works for any vanilla mob. Hard to telegraph ("why won't my cows breed?" is invisible until support kicks in).
- **(b) Different *species* per zone** ← **DECIDED 2026-05-10 (verbal).** Vanilla cows breed anywhere, produce low-tier output. Modded mobs (Alex's Mobs, Untamed Wilds — confirm 1.21.1 NeoForge availability) provide naturally biome-locked species: cold-highland mobs, hot-low mobs, deep-forest mobs. Players can't catch what doesn't spawn there. Drops feed husbandry refiner inputs at higher tiers.

The biome-species split also extends to non-mammals naturally: outer-zone chickens drop different feathers, outer-zone fish in the Hybrid Aquatic / Sea Myths zones carry premium drops, etc.

**Pairs with demand-contracts (§5.5 / §8):** the dock posts rotating buy-orders for *specific* species' output. "20 highland-goat wool today, 15 desert-camel hide tomorrow." Forces the husbander to maintain *multiple* outer-zone farms instead of optimizing one. Directly serves H6 (adaptive economy).

**Q50 — mod confirmation:** verify Alex's Mobs and Untamed Wilds 1.21.1 NeoForge build availability. Both are major mods, expected fine, but `.research/` evidence needed before committing the design to them.

### 14.5 Barrier taxonomy — the vocabulary

Each zone is gated by **one or more barriers** from the list below. Compositional: a single zone can demand heat tolerance + a heat-rated airship + ranged-combat capability simultaneously. The richer the composition, the harder the zone, and the more cross-profession trade required to crack it.

This is the design vocabulary — when we say "zone X is barrier-tier 3," we mean "needs roughly 3 barriers cleared to thrive there."

#### Climate barriers
- **Heat** — TaN-driven; cooling gear / shaded contraptions / hydration
- **Cold** — TaN-driven; woolen layered gear / heated contraptions / hot food
- **Wet-cold** — water-on-skin freeze (TaN already supports). Crossing oceans without insulation is lethal
- **Atmospheric hostility** — Nether's sulfurous air, sky-island thin air. Could implement as TaN extreme-temperature analog or a custom debuff

#### Vehicle-scale barriers (Greg's explicit list)
- **Need a boat** — any floating contraption clears it
- **Need a bigger boat** — cargo capacity / hull HP / sea resistance to survive deeper-sea mobs and waves
- **Need an airship** — vertical lift capability (Hot Air Envelope minimum)
- **Need a bigger airship** — range + weight capacity for long-haul or weighted cargo
- **Need a specialized vehicle** — heat-rated (heat-resistant block layer), cold-rated (insulated), pressurized (sealed cabin), fuel-extended (large tank), or amphibious (boat-that-carries-cart, the §2.2 "ship-carries-car" pattern)
- **Need a vehicle that can carry sub-vehicles** — mothership pattern. Endgame: airship with a cart inside that carries a package. Native to Create Aeronautics' physics

#### Combat barriers
- **Ambient hostile density** — modded common-but-deadly mob at high spawn rate (the no-vanilla-hostiles preference applies — pick from Alex's Mobs / Mowzie's / etc.)
- **Specialized mob threat** — mob immune to most weapons, requires specific counter (silver, fire, ranged-only)
- **Apex / mini-boss zone** — single rare encounter that gates an area outright. Sea Myths, Mowzie's bosses, etc.
- **Ranged-combat-required** — ghasts, phantom-style; melee builds can't cope

#### Atmospheric / sensory barriers
- **Oxygen-required** — deep ocean diving, high altitude, Nether sulfur. Strong fit for **Create backtanks** (already in the mod) — repurpose as breathing-air supply
- **Visibility** — sculk-style darkness fog, blindness biomes. Need strong-light source (Create-powered floodlight contraption?)

#### Resource / consumable barriers
- **Fuel range** — vehicle can only carry so much fuel; some destinations are simply too far without a refuel waypoint or larger tank. Forces engineering trade-offs and creates a market for **outpost depots** (small player-built fuel stations on the route)
- **Food stockpile** — long expedition with no in-zone food source. Couples to caero_vitality (death penalty makes running out of food doubly painful)

#### Terrain barriers
- **Sheer / vertical** — can't walk up; need flight or climbing. Sky islands are pure verticality + nothing below
- **Deep submersion** — pressure + dark + oxygen, all at once. Deep-sea trenches
- **Hostile terrain** — magma flows, quicksand, soul sand drag, slime fields. Slow you to a crawl, expose you to other barriers longer
- **Voidfall risk** — End-style "no ground." One slip and you're dead, regardless of gear

#### Knowledge / social barriers (design space, defer to v2)
- **Requires recipe taught by another player** — research items as tradable goods
- **Requires escort by a specialist** — a destination only safely reachable in a 2+ player party with complementary capabilities

#### Time / world-state barriers (design space, defer to v2)
- **Time-of-day or weather gated** — zone only accessible at night, or during storms
- **Server-state gated** — opens after someone clears a milestone (e.g. first to defeat the Nether boss unlocks the End)

### 14.6 How barriers compose — sample zone audit

First-pass barrier composition for the v1 painted map. Each line is a proposal; numbers in parens are barrier counts (rough difficulty proxy).

| Zone | Barriers |
|---|---|
| 13 — spawn plains | none (0) |
| 6 — cold forest | mild cold (1) |
| 11 — warm ocean | boat (1) |
| 8 — desert | heat + fuel range *or* boat-around (2) |
| 7 — stony mining belt | hostile density (1) — mining mobs |
| 2 — old-growth spruce taiga | moderate cold + dangerous-forest mobs (2) |
| 3 — deep ocean | bigger boat + dangerous sea mobs (2) |
| 9 — badlands | heat + verticality + apex mob (3) |
| 4 — jagged peaks | strong cold + verticality + sheer terrain (3) |
| 1 — cold ocean | bigger boat + cold + freeze risk (3) |
| 5 — Nether | extreme heat + ranged combat + atmospheric hostility + specialized mobs (4) |
| 10 — End | voidfall + ranged combat + hostile density + strong cold(?) (4) |

### 14.7 The strategic implication — barriers as roles

Because barriers compose, **specializing in defeating one barrier-type becomes a viable player role.** This is what makes H4 (unique strategies) actually work — different barriers reward different specialties:

- **Shipwright** — builds heat-rated, cold-rated, sea-rated hulls; sells voyages and ferry service
- **Combatant** — clears outer-zone mobs for parties; sells escort
- **Hub merchant / refiner** — operates the spawn-zone refinery, never leaves spawn
- **Cartographer / explorer** — pioneers new zone routes, sells maps and waypoints
- **Engineer** — builds the vehicles others use; sells turn-key contraptions
- **Husbander** — operates outer-zone farms; sells refined drops

This also closes the H4 concern from earlier in the brainstorm: H4 is satisfied by *role differentiation across players* (the iron-runner sells to everyone), not *path differentiation per player*. Worth folding into the heuristics as a corollary on H4.

### 14.8 Open questions

- **Q44 — slag iron edge cases.** Iron Bars, Cauldron, Smoker, Blast Furnace, Lantern. Full Create + vanilla recipe audit needed.
- **Q45 — cold axis tier-3 endpoint.** End placement helps, but the *land* cold axis (jagged peaks) tops out short. Add a deeper cold-land tier or accept that cold ends at sea?
- **Q46 — sky islands as a 4th axis.** Big worldgen lift (density-function changes per §12.4 Q40). Worth it iff sky islands have unique tier-3 resources, not just visuals. Decide before committing the floating-terrain work.
- **Q47 — atmospheric / oxygen barriers.** Repurpose Create backtanks as breathing-air, or skip the mechanic entirely?
- **Q48 — fuel range as a real gate.** Cumulative fuel capacity per vehicle as a hard limit, or ignored (current Create default = effectively unlimited via auto-feed)?
- **Q49 — log species → quality cap mapping.** Per-tier list of which outer-zone species maps to which charcoal tier.
- **Q50 — Alex's Mobs / Untamed Wilds 1.21.1 NeoForge confirmation.** Need `.research/` evidence before committing the husbandry redesign to either.
- **Q51 — barrier composition for axes vs. zones.** Should each *axis* (a sequence of zones) have a guaranteed mix of barrier types, so no axis is "just heat all the way" or "just sea all the way"? Or accept some axes being one-flavor?

### 14.9 Implementation footprint (pre-decision)

Concrete work this section implies, smallest to largest:

1. **Slag iron datapack + glue mod** (already scoped at §11.8 item 1) — adds the new ore + ingot, recipe overrides for the §14.2 allow-list, real-iron lockouts for the deny-list. Data + ~1 file of Kotlin.
2. **Forestry log-quality-cap layer** — new tag `caero:log_quality_tier_{1..4}`, biome-modifier datapack to tag log items by source biome, small change to `caero_specialization` to clamp output quality to incoming log tag. Mostly tag + config.
3. **Husbandry mod additions** — add Alex's Mobs and/or Untamed Wilds (pending Q50), biome-modifier datapack to bind species to outer-zone biomes, glue layer to map their drops to husbandry refiner inputs. New mods + tag + small config; no new code unless the drops need adapter recipes.
4. **Barrier composition data** — per-zone barrier tags as a datapack manifest (informational at first; later, can drive client-side warnings, demand-contract generation, etc.). Pure data, no code in v1.

Items 1–2 are independent of everything else in §11–§13 and can ship now against the spawn biome. Items 3–4 wait on world-deployment of the karos datapack.

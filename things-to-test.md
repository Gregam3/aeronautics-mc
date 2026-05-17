# Things to test in-game

Greg manually deletes entries after testing. Claude appends entries on every
runtime-affecting change. Format: `- [date] mod — what changed → what to verify`.

- 2026-05-04 caero_specialization — lava buckets disabled as fuel (burn time
  forced to 0 in any vanilla furnace/blast/smoker) → put a lava bucket in a
  furnace; should not light.
- 2026-05-04 caero_specialization — all 11 vanilla log types added to FORESTRY
  XP weights at 1.0 (oak/birch/spruce/jungle/acacia/dark_oak/mangrove/cherry/
  crimson_stem/warped_stem/bamboo_block) → refine each type in a forestry
  refiner; verify XP is granted and the log gets quality-stamped.
- 2026-05-04 caero_specialization — 7 new byproduct items registered:
  `slag, filings, tallow, beast_ember, beast_carapace, beast_hide,
  beast_essence` (placeholder vanilla-texture borrows: gunpowder, iron_nugget,
  honey_bottle, blaze_powder, prismarine_shard, leather, ender_eye) → check
  creative tab "caero_specialization" tab shows all 7 with names; textures
  visible.
- 2026-05-04 caero_specialization — MINING refiner now emits `slag` ~20%
  base + level-scaled (45% at lvl 100) → refine raw_iron/raw_copper/raw_gold
  in a mining refiner; expect occasional slag drops alongside the ingot.
- 2026-05-04 caero_specialization — ARMOURER refiner now emits `filings`
  ~20% base + level-scaled → refine any tool/armour at an armourer refiner;
  expect occasional filings drops.
- 2026-05-04 caero_specialization — HUSBANDRY refiner now emits `tallow`
  ~30% base + level-scaled, **meat inputs only** (beef/porkchop/chicken/
  mutton/rabbit + cooked variants) → refine a beef stack; expect tallow
  drops. Refining wheat/eggs/leather should NOT produce tallow.
- 2026-05-04 caero_specialization — HUNTER refiner now accepts vanilla
  hostile-mob loot and routes it to one of four `beast_*` byproducts:
  • ember (forestry catalyst): gunpowder, blaze_powder, blaze_rod ×2,
    magma_cream, ghast_tear ×2
  • carapace (armourer catalyst): bone, spider_eye, phantom_membrane ×2,
    prismarine_shard, prismarine_crystals
  • hide (husbandry catalyst): rotten_flesh, slime_ball, string
  • essence (jewelery catalyst): ender_pearl ×2, glowstone_dust, redstone
  → put each input into a hunter refiner; verify the right beast_* item
  comes out at the listed count, and check that placing the result in the
  matching consumer refiner's catalyst slot is accepted.
- 2026-05-04 caero_specialization — wool now refinable at HUSBANDRY refiner
  (all 16 colors via `#minecraft:wool` tag) → put white_wool into a
  husbandry refiner; expect quality-stamped white_wool out + XP. Test that
  the quality-stamped wool tooltip shows the tier.
- 2026-05-04 caero_specialization — `create:white_sail` recipe gated to
  MEDIUM-quality wool; vanilla unrefined/low wool will not craft → try
  crafting a white sail with raw wool (should fail), then with quality-
  stamped MEDIUM/HIGH wool (should yield 2 sails). Sail color conversions
  (right-click white sail with dye in-world) still work as vanilla.
- 2026-05-04 caero_specialization — all 16 `aeronautics:<color>_envelope`
  shaped recipes AND 16 `aeronautics:deploying/deploying_envelope_<color>`
  Create-deployer recipes gated to HIGH-quality wool → try crafting any
  envelope: raw/medium wool should NOT match. Only HIGH-quality wool of the
  matching color should craft 4 envelopes (or 3 in the deployer path).
- 2026-05-04 caero_specialization — catalyst tag updates:
  `catalyst_amplifier_fueler` adds beast_ember;
  `catalyst_quality_armourer` adds beast_carapace;
  `catalyst_quality_husbandry` adds beast_hide;
  `catalyst_quality_jewelery` adds beast_essence → catalyst slots in
  the named consumer refiners should accept these items (tooltip shows the
  catalyst kind via existing CatalystTooltip).
- 2026-05-04 caero_specialization — `create:water_wheel` and
  `create:large_water_wheel` recipes now require Treated Planks
  (`caero_specialization:light_planks`) instead of any vanilla plank → try
  crafting a water wheel with oak planks (should fail) and with Treated
  Planks (should yield 1). Same for large water wheel (uses water_wheel +
  Treated Planks).
- 2026-05-04 ring-biomes — encumbered weights: `minecraft:coal` 0.25 → 1.5
  (6×, matches iron_ingot); `minecraft:coal_block` 3.0 → 13.5 (9× coal) →
  pick up ~33 coal; should hit threshold1 (50.0) and lose sprint. A full
  stack of 64 coal = 96.0 weight (overencumbered). Verify charcoal weight
  unchanged at 0.2.
- 2026-05-04 caero_specialization — torch recipe (`minecraft:torch`) now
  scales output by coal/charcoal quality via new `quality_shaped` recipe
  type → vanilla unrefined coal/charcoal yields **1** torch; LOW yields
  **3**; MEDIUM yields **5**; HIGH yields **10**. Refine coal at a
  forestry/fueler refiner to bump quality, then craft torch with stick +
  the quality-stamped coal/charcoal. Mixing both works (whichever the
  pattern matches). Soul torch and redstone torch unaffected.
- 2026-05-04 caero_specialization — refiner empty-hand right-click now shows
  info panel (owner, owner's skill level, fee, your level) instead of opening
  the menu. Owner sneak+empty-hand still opens the fee chooser but now also
  dumps the info panel first. → Verify: (a) right-click your own refiner with
  empty hand and no sneak → see info incl. owner level. (b) sneak + empty
  hand on your refiner → see info AND fee buttons. (c) non-owner right-clicks
  with empty hand on someone's refiner → sees fee. (d) right-click with a
  refinable item still opens the menu.
- 2026-05-04 caero_specialization — Jeweler reworked: input is now any stone
  (cobble/stone/granite/diorite/andesite/tuff + polished, deepslate variants,
  blackstone/basalt/end_stone) instead of raw ore. Per refine rolls
  (Nothing | Gem | DiamondShard) keyed by stone-tier × jeweler level.
  COMMON L1=5%/L100=25%; DEEP 12%/50%; EXOTIC 25%/75%. New item
  `caero_specialization:diamond_shard` — 9 shards craft to 1 vanilla diamond
  (shapeless). Side `ore_dust` only on a successful drop (not on Nothing).
  → Verify: (a) put cobble in jeweler menu, refine → mostly Nothing at L1
  with rare topaz; (b) put deepslate in → noticeably more drops; (c) end_stone
  → ~25% drop rate at L1, often shards/gems; (d) raw_iron is rejected by the
  input slot now; (e) get 9 diamond_shards, craft 3x3 shapeless → 1 diamond;
  (f) salvage path on iron_pickaxe still works unchanged.
- 2026-05-04 caero_specialization — XP weights retiered. FORESTRY: bamboo 0.5,
  logs 1.0, nether stems 1.5, charcoal 1.5, coal 2.0, coal_block 18.0
  (was all 1.0). MINING: added ancient_debris=10.0. HUSBANDRY: raw meat
  dropped to 1.2-1.5, cooked meat added at 2.0-3.0. JEWELERY: stone-tier
  weights (T1=0.2, T2=0.5, T3=1.0). → Verify: (a) refining cobble at jeweler
  gives ~10 XP per refine vs ~100 for raw_iron at mining; (b) charcoal at
  fueler gives noticeably more XP than oak_log; (c) cooked_beef at husbandry
  gives more XP than raw beef. Note: existing TOML config at
  `serverconfig/caero_specialization-common.toml` has the OLD weights baked
  in — delete it (or edit) so defaults regenerate.
- 2026-05-04 mods/copper-age-backport — installed Smallinger's Backport Copper
  Age 0.1.4 (NeoForge 1.21.1) into the Prism instance → server boots cleanly
  with the mod in mods/; in-world: craft a Copper Golem (carved pumpkin on
  copper blocks?), spawn copper bars/chains/lanterns/grates/bulbs from creative,
  verify oxidation cycle and waxing works.
- 2026-05-04 caero_specialization — vanilla `minecraft:emerald` is now
  refinable at the Jewelery refiner → produces 1 Cut Emerald (`emerald_gem`)
  with rolled quality, always succeeds. Quality catalyst should still bump
  tier; amplifier still doubles output. Verify: feed an emerald → get a
  Cut Emerald with a quality stamp; refining 20 should average ~57% HIGH
  at lvl 100.
- 2026-05-04 caero_vitality v0.2.0 — food quality envelope: nutrition/saturation
  delta on every tier, HP restore + banquet scaled by quality, q<30 cliff (no
  restore/banquet from Unrefined). Tier tags reshuffled (basic/cooked/prepared/
  banquet). → Verify: (a) eat unrefined bread → food bar shows -1 hunger vs vanilla;
  (b) eat husbandry-refined HIGH bread → noticeably more hunger filled than vanilla;
  (c) die a few times then eat Unrefined FD beef stew → no "Restored X hearts" message;
  (d) eat MEDIUM/HIGH FD beef stew (tier 3) → restores hearts proportional to quality;
  (e) eat HIGH/Prime stuffed_pumpkin_block (tier 4) → bigger banquet bonus than
  Medium; (f) Unrefined banquet → no banquet message at all.
- 2026-05-04 caero_specialization — JEWELERY refiner now also rolls vanilla
  redstone dust as an outcome (~7/10/11 % at COMMON/DEEP/EXOTIC tiers, scaled
  off jeweler level via the existing any-drop curve). → Verify: feed cobble /
  deepslate / blackstone into a jewelery refiner and confirm redstone dust
  shows up alongside topaz/sapphire/ruby/emerald/diamond_shard.
- 2026-05-04 caero_specialization — copper tools/armour (copperagebackport)
  now refinable at ARMOURER refiner and salvageable at JEWELERY refiner
  (output: raw_copper). → Verify: refine a copper pickaxe at armourer (gets
  quality+score+durability scaling); salvage it at jewelery (returns
  raw_copper count proportional to recipe cost × durability × quality).
- 2026-05-04 caero_specialization — diamond_shard and redstone_dust outputs
  from JEWELERY refiner now carry quality stamps (rolled off jeweler level,
  bumped by quality catalyst). → Verify: feed cobble repeatedly into a high-
  level jeweler; some shards / redstone should appear with [L]/[M]/[H] tier
  colour in their tooltip.
- 2026-05-04 caero_vitality — refined food (q≥30) now bumps every Nutritional
  Balance macronutrient bar by tier×quality scaled amount (tier-1 baseline
  0.5, tier-4 baseline 2.0; envelope-scaled). NB integration is reflective
  with no compile-time dep — silently skips if NB missing. → Verify: eat a
  refined high-quality bowl/feast and watch the NB nutrient GUI bars rise
  beyond what the same dish at q<30 gives.
- 2026-05-04 caero_vitality — refinable foods now show their quality-driven
  buffs in hover tooltip: hunger/saturation delta, restoration HP, banquet
  bonus, macronutrient bonus. Lines hide when their value is zero; foods at
  q<30 show a "refine to q≥30 to unlock restoration / macros" hint. →
  Verify: hover an unrefined cooked beef vs a HIGH-quality FD bowl; numbers
  should match the q-anchor table in VitalityMath.
- 2026-05-04 createstockexchange — upgraded to v1.0.0 build with new Trade Post block (recipe: 3 chest / iron+sign / 3 oak planks). Right-click auto-links to your company; sells items from a hard-coded catalog (cobble/logs/ores/food/mob drops) at base prices with rolling-window dynamic discount. Pays from company bank → seller's account, drops a paper receipt. → verify (a) recipe crafts, (b) GUI opens and lists items with prices, (c) selling debits the company bank and credits player, (d) receipt is correct, (e) repeated selling pushes effective price down to the 10% floor, (f) old companies/stock data load fine after jar swap.

- 2026-05-04 createstockexchange — Trade Post block now has a custom texture (wood frame + cream sign panel + gold T) instead of the placeholder oak_planks. Patched directly into the installed jar (model+png). → verify the placed block actually shows the new sprite on every face; if it's still oak planks, MC was launched before the patch — restart again.

- 2026-05-04 createstockexchange — fixed Trade Post recipe (upstream shipped `minecraft:sign` which isn't a 1.21.1 item; replaced with `#minecraft:signs` tag so any wood-type sign works). → verify it now crafts in JEI (3 chest / iron + any sign / 3 oak planks).

- 2026-05-04 SERVER — deployed full mods set to live (46.225.17.145). New createstockexchange jar with Trade Post + custom texture + recipe fix. Two restarts (initial deploy, then recipe patch). → verify multiplayer flow: greg + Beth/Corey can join, Trade Post can be placed, GUI opens, sell flow debits company bank correctly.

- 2026-05-04 caero_rings/datapack — leaf and wool armour (toughasnails:{leaf,wool}_{boots,helmet,chestplate,leggings}) lowered from 5.0 → 2.0 each in the encumbered_weights datapack. Reloaded live via tmux `reload`. → verify in-game: equip a full set, check the encumbrance overlay shows ~8 weight contribution from the armour rather than ~20.

- 2026-05-04 caero_rings — DeathPreserve floor: respawn-after-death now clamps food and thirst into [2, 3] instead of [0, 3], so dying empty no longer leaves you instantly starving/dehydrated. → verify: die at 0 food / 0 thirst (TAN), respawn shows 2 hunger drumsticks and 2 thirst droplets, sprint works.

- 2026-05-04 caero_claims — FireBlock mixin disables fire spread inside claims (mixes into getIgniteOdds and checkBurnOut). New mixin config + first mixin on this mod. → verify: (a) lay a wood floor inside a claim, light a fire on top, fire ages out without consuming neighbouring planks or jumping to new positions; (b) lay flammables outside the claim border and fire still spreads normally there; (c) flint+steel still ignites a single fire block (only spread is gated, not initial ignition by a player).

- 2026-05-05 caero_auction — NEW MOD: player-to-player auction house. Place an Auction House block (Caero: Auction House creative tab; gold-top, bookshelf-side cube), right-click to open the GUI. Put a stack in the deposit slot, type a price (spurs), click List → stack disappears, listing appears for everyone. Other players click Buy → listing's full price is deducted from buyer's Numismatics account, deposited to seller's account (works even if seller offline), buyer gets the stack. Seller-only Cancel button returns the stack. → verify: (a) listing persists across server restart (block keeps its listings on /reload-or-restart); (b) breaking the block drops all listed items at the block pos (no money lost — listings simply void); (c) you can't buy your own listing (server rejects with red message); (d) buying with insufficient spurs fails cleanly with red message; (e) two players viewing the same block see listings update live for each other; (f) closing the GUI with items still in the deposit slot returns them to inventory (no item loss).
- 2026-05-05 caero_auction — block now uses bespoke 16×16 "AH" texture (warm gold + coin-bronze, sibling to the business vendor's "V"). Generated by `tools/generate_auction_house.py`. → verify: place the block, all 6 faces show the AH glyph, item icon in inventory matches.
- 2026-05-05 createstockexchange — Trade Post block decoupled from companies. Imported the mod source into `glue/createstockexchange/` and stripped: company auto-link in `TradePostBlock.useWithoutItem`, `companyId`/`companyName` state on `TradePostBlockEntity`, header company-name/bank-balance display in `TradePostScreen`, and the company-bank debit / company-membership credit-routing in `ServerPacketHandler.onTradePostTrade`. Sales now credit the seller's Numismatics player account directly (NPC-style, server-issued payment); receipt label is "Trade Post". Catalog + dynamic pricing + the 1.0.0 jar name are unchanged. → verify: (a) right-clicking the Trade Post opens the GUI for any player without needing a company, (b) selecting an item, entering qty, clicking Sell debits items and credits seller's Numismatics balance with no company involvement, (c) GUI header shows just "Trade Post" (no "(unlinked)" / no balance), (d) receipt book reads "Trade Post" instead of a company name, (e) old worlds with previously-linked Trade Posts still load (the saved `companyId` / `companyName` NBT is now ignored), (f) the other createstockexchange features (Company Desk, IPO Desk, Stock Exchange, Business Vendor) all still work as before.
- 2026-05-05 caero_auction — fixed crash on List: ListingsSyncPacket field-order mismatch (toNetwork wrote price before stack, fromNetwork's named-arg ctor evaluated stack before price → tried to parse "price" bytes as an ItemStack → "Invalid tag id: -121" → client disconnect). Rewrote fromNetwork to read into locals in write order. → verify: list an item, no crash; second player sees the listing populate live.
- 2026-05-05 createstockexchange — Trade Post block now has a custom 16×16 texture (cream sign panel + coin-bronze border, "TP" glyph in the same border colour) instead of the placeholder oak planks. Generated by `glue/createstockexchange/tools/generate_trade_post.py`. → verify: place a Trade Post, all 6 faces show the TP glyph; item icon in inventory matches; if it's still oak planks, MC was launched before the deploy — restart the client.
- 2026-05-05 caero_auction — switched to per-item pricing with partial buys. Listing's price is now interpreted as spurs-per-item (label reads "X ea" in the GUI). Click Buy → buys 1 unit, deducts 1×price from buyer / deposits to seller, listing's stack count decrements; Shift+Click Buy → server buys as many as the buyer can afford up to the remaining stack. Listing is removed when its stack hits zero. → verify: (a) list 64 dirt at 10 → second player clicks Buy once, gets 1 dirt for 10 spurs, listing now shows "63 left"; (b) shift-click with limited spurs caps purchase to balance/price; (c) attempting to buy more than remaining auto-caps to remaining; (d) listing disappears once fully bought; (e) no double-charge if two players race to buy the last item (refund path).
- 2026-05-05 caero_vitality — fixed restoration-food bug: tier-2/3 foods (sandwiches, soups, pumpkin pie, etc.) crafted in a vanilla crafting table had no quality component, defaulted to q=0, and silently failed the q≥30 restoration gate so eating them never gave max-HP back. `QualityLookup.effectiveScore` now defaults *unstamped* items to q=60 (vanilla baseline) instead of q=0; items that were explicitly refined and rolled UNREFINED still read q=0 as before. Also: `RestorationFood.applyPermanentRestoration` now surfaces a gray chat message when a tier-2/3 food is gated out by low quality, instead of failing silently. → verify: (a) die at least once so you have a max-HP penalty (the ☠ red message in chat); (b) eat a vanilla pumpkin pie / FD sandwich / FD vegetable soup; (c) chat shows the green "♥ Restored X.XX max hearts (tier N)" message and your max heart count goes up by the listed amount; (d) eating a stamped Unrefined item (q<30) shows the new gray "quality too low to restore" message instead of doing nothing silently; (e) eating refined-medium / high foods still works as before (no regression on already-stamped food).
- 2026-05-05 caero_auction — added admin debug commands for testing without a 2nd client. Look at an Auction House block, then: `/caero-auction listings` prints all listings with their indices; `/caero-auction test-buy <index> [quantity]` simulates a purchase by a virtual buyer (UUID 00000000-0000-0000-0000-000000000001), auto-funded — bypasses the self-buy check, decrements the listing, deposits to seller, drops items at the block. `/caero-auction test-balance` shows the test buyer's spur balance. Permission level 2 (op). → verify: list 64 dirt at 10 ea; `/caero-auction listings` shows the entry; `/caero-auction test-buy 0 5` drops 5 dirt at the block, your `/numismatics view` (or whatever Numismatics' view command is) shows +50 spurs, listing now reads 59 left in the GUI.
- 2026-05-10 NovoAtlas + karos-datapack (S3 worldgen testing) — installed
  NovoAtlas 1.1.0 jar in mods/, karos-datapack in paxi/datapacks/. Active on
  ANY new world. **DO NOT load the existing S2 world while these are
  present** — datapack's dimension override may interact unpredictably.
  → Create a fresh test world ("karos-test"). Verify: spawn lands in a
  plains-like biome (should be plains/sunflower/meadow per /locate). Fly
  ~1500 blocks south → painted Nether zone — check ghasts spawn, lava seas
  form, ambient is Nether-y. Fly ~2000 blocks north → painted End zone —
  check end-stone terrain, endermen spawning. Fly west → high mountains +
  stony belt. Fly east → cold-forest belt. To revert: delete
  paxi/datapacks/karos-datapack (datapack off) and/or
  mods/novoatlas-neoforge-1.1.0+1.21.1.jar (mod off).
- 2026-05-10 caero_atlas (Karos worldgen v2) — fork no longer overrides
  the noise router, so vanilla overworld terrain (with Tectonic +
  Lithostitched + Regions Unexplored stack) drives terrain shape. New
  Lithostitched modifier `karos-terrain-overrides/.../final_density_swap.json`
  wraps the registry's overworld final_density with `select_by_biome_color`
  → in painted Nether/End color zones the inlined Nether/End final density
  takes over; everywhere else the wrapped (Tectonic-shaped) overworld density
  flows through. Per-biome block palette swap (netherrack / end_stone / lava)
  still applies in doFill. **Make a new world** to test (dimension config
  is frozen at world creation). Verify: spawn should now have rolling
  Tectonic-shaped terrain (mountains, ridges, etc.) instead of flat
  heightmap-driven landscape. Nether and End zones still render correctly
  with their respective terrain shape and block palette. Same /tp commands
  as before to test the zones.
- 2026-05-10 caero_atlas (Karos worldgen v2.1) — shifted the inlined Nether
  final_density Y values down by 65 (NETHER_Y_SHIFT in
  generate-karos-datapack.py). Result: Nether's solid roof band, vanilla
  Y=104..128, now sits at Y=39..63 (overworld sea level). Walk into a
  painted Nether zone — netherrack ceiling should be at sea-level horizon,
  overworld sky visible above. Lava-floor band lives at Y=-73..-41 (deep
  underground). **New world required** (frozen at creation). Test by
  teleporting to /tp @s -842 100 1555 — land on the netherrack roof at
  ~Y=63, mine down to find the cavern + lava.
- 2026-05-10 caero_atlas v2.2 — fork: ColorMapBiomeProvider now supports
  optional `overhead` config (above_y / biome / colors). Above the Y
  threshold, columns whose painted color is in the color set return the
  overhead biome instead of the painted one. Wired up so Y > 64 in painted
  Nether/End columns returns minecraft:plains — overworld sky/fog
  atmosphere above the closed roof, no more "Nether goes into the skybox".
  Lithostitched wrap priority bumped to 10000 so our nether_final density
  wraps OUTSIDE Tectonic's modifications (otherwise Tectonic's amp scales
  it to mountain heights). Adds 17 RU biomes across deep_forest,
  high_mountains, cold_forest, stony_mining, desert, spawn_plains zones.
  **New world required.** Test: stand near a Nether/End zone edge, look up
  — sky should be normal blue, not Nether-red. Look down — Nether terrain
  fully visible below sea level with closed netherrack roof at Y=63ish.
- 2026-05-10 Tectonic config tuning — toned down for more "alive" feel:
  * biomes.temperature_scale 0.25→0.5 (smaller temperature regions =
    more variety per area; old setting made biomes feel monotonous)
  * biomes.vegetation_scale 0.25→0.5 (same — smaller humidity regions)
  * continents.continents_scale 0.13→0.25 (smaller continents, less
    giant empty ocean expanses between them)
  * continents.ocean_offset -0.65→-0.45 (less ocean dominance; per
    Tectonic tooltip "Lower values = more oceans" so going up = less)
  * continents.flat_terrain_skew 0.1→0.0 (don't over-favor flat plateaus)
  * global_terrain.vertical_scale 1.125→1.05 (mild pullback on peak
    exaggeration)
  Backup at config/tectonic.json.bak-2026-05-10. Restart MC + create a
  new world to test (vertical_scale + ultrasmooth need restart per
  Tectonic tooltips). If still "empty" we can push biome scales higher
  (0.75) and/or ocean_offset further (-0.3).
- 2026-05-10 caero_atlas v2.3 — nether density now wrapped in min(..)
  with a y_clamped_gradient that forces strongly-negative density above
  Y=64. Root cause of the Y=350 stone pillars: vanilla
  minecraft:nether/final_density formula returns ~+0.6 (solid) at
  arbitrary Y above the roof gradient end; vanilla nether dimension
  hides this by setting max_y=128. Our overworld max_y=320 means the
  formula generated solid blocks all the way up. The min() cap forces
  density to -64 at Y>=65, guaranteed air. **New world required.**
  Test: stand in Nether zone, look up — should see open sky from Y=65
  upward, not stone walls. Nether interior visible below: closed
  netherrack roof at Y~63, then porous cavern with lava sea at Y~-30.
  If overworld water reaches a gap in the roof, it should flow into
  the cavern.
- 2026-05-10 caero_atlas v2.4 — replaced inlined-vanilla nether density
  with a much simpler formula: y_clamped_gradient (solid below Y=-15,
  air above Y=30) added to vanilla nether_3d_noise scaled to ±0.6.
  Result band:
    Y < -15: fully solid netherrack mass (deep underground)
    Y = -15 to 30: porous cavern (50/50 solid/air modulated by nether
        noise — the "the cavern feel" Greg asked for)
    Y >= 30: pure air, nothing renders
  At sea level (Y=65) overworld water flows down through any natural
  gap in the cavern's upper surface (Y~25-30). Lava lakes in the cavern
  via overworld aquifer + biome water→lava remap. **New world.** Test:
  /tp @s -842 100 1555 then look down — should see open air above ~Y=30,
  porous netherrack-and-lava cavern below, solid mass deeper. Should
  match the "porous, open like the real Nether" target more than the
  vanilla-formula-shifted v2.3 did.
- 2026-05-10 caero_atlas v2.5 — End redesigned. New end_final density:
  * Y < -30: solid floor (seafloor stone)
  * Y = -30 to -10: shoreline transition
  * Y = -10 to 50: deep water column (aquifer fills with water; remap
    no longer wipes water in End biomes)
  * Y = 50 to 85: floating end_stone island band, sloped_cheese-driven
  * Y > 95: pure air (overworld sky)
  Sea level Y=65 lands inside the island zone, so islands stick out above
  the waterline. Fork updated to PRESERVE water in End biomes (was
  remapping water→air). **New world.** Test: /tp @s -1038 150 -2253 →
  should fall through air, see giant end_stone islands floating between
  ~Y=65-85 above a wide deep-water ocean, seafloor far below.
- 2026-05-10 caero_atlas v3.0 — Nether redesigned for "default rendering at lower level".
  Approach: VANILLA minecraft:nether/final_density inlined verbatim, then
  Y-shifted by 63 (vanilla Y=128 → our Y=65 = sea level peak), then
  augmented with a porosity Y-bias (0 below Y=35, ramps to -2.8 by Y=65)
  that opens the closed roof so sea spills in. Block remap split:
    Y < 0 (lower nether): water → lava (forms lava sea on netherrack floor)
    Y >= 0 (upper cavern): water → air (keeps walking level dry)
  Headless iteration analysis (4 rounds via iter_nether.py) confirms:
    Y=64+: 100% air (overworld sky, plains biome via overhead config)
    Y=36-62: 100% air (open cavern below porous roof)
    Y=28-36: porous netherrack with magma_block features (transition)
    Y=14-28: cavern walls, blackstone, gravel, basalt
    Y=0-12: 80-100% air (iconic mid-cavern walking level)
    Y=-6 down: solid netherrack mass with quartz/gold ore, tuff, fire
  Biomes at Y=0: nether_wastes (61%) + crimson_forest (39%). Features
  generating naturally: gravel, blackstone, magma_block, fire, ores —
  default vanilla nether decoration is firing.
  Known polish: lava sea is sparse (1 block at Y=-50). Aquifer doesn't
  fill the deep cavern much; the iconic "continuous lava floor" doesn't
  form. Acceptable for now.
  **New world.** Test: /tp @s -842 80 1555 → fall through Y=80 air → hit
  porous transition Y=30-65 (sea may be spilling in if zone borders an
  ocean) → land in open cavern around Y=10 → mid-walking level Y=0-8 →
  iconic nether feel.
- 2026-05-10 Karos atmosphere overrides — 9 vanilla biome JSONs overridden
  in the karos-datapack at data/minecraft/worldgen/biome/*.json. Each
  copies vanilla effects then patches:
    sky_color: very dark (0x0A0202 nether_wastes, 0x140404 crimson_forest,
        0x041014 warped_forest, 0x06080A soul_sand_valley, 0x0A0A0A
        basalt_deltas, 0x000000 all end biomes)
    fog_color: aggressive (deeper red for nether, deep purple for end)
    particle: bumped probabilities + scarier particle types where
        applicable (soul_fire_flame in nether_wastes, doubled crimson_spore
        in crimson_forest, doubled ash in soul_sand_valley)
  Sounds and music preserved from vanilla — already nether-themed.
  KNOWN LIMIT: sun + clouds remain visible because dimension type is
  overworld, and dimension type controls sun/cloud rendering not biome
  effects. The dark sky_color tints what's visible but doesn't disable
  sun/clouds entirely. Truly removing them needs a custom dimension type
  (which would affect the whole world, not just nether zones) or a
  client mod with mixins. **New world.** Test: enter painted Nether,
  look around — should feel much darker/redder/eerie compared to before.
  Sun visible faintly through dark fog.
- 2026-05-10 caero_atlas v3.1 — End fixes:
  * Island band shifted up: was Y=50-85 (mostly underwater), now Y=85-110
    (clearly above water surface Y=65 with ~20 block air gap)
  * sloped_cheese amplifier 2.5→3.0 for chunkier "giant" islands
  * Top cap raised from Y=95 → Y=125 so the island zone has more height
  * deep_water_bias extended to Y=75 (was Y=10) so no stragglers form
    below the intended island band
  * Overhead biome dispatch dropped for End columns — end_highlands etc.
    extend up to the islands themselves, so atmosphere on the islands is
    end-themed (dark purple/black sky from our atmosphere overrides) not
    plains-blue. Nether overhead unchanged.
  **New world.** /tp @s -1038 150 -2253 → fall through purple sky to
  open air above the deep ocean → land on giant end_stone islands at
  Y=85-110 → look down to see ocean surface Y=65 with seafloor far
  below.
- 2026-05-10 New mask v2 deployed. Greg repainted the world. Major shifts:
    background: cold_ocean 24%→0.4%, deep_ocean 10%→39% (bulk of map is
        now deep ocean, not cold ocean)
    sky_placeholder removed entirely (was 1.6% scattered pink dots)
    Nether much more isolated at the south, bordered by deep ocean
    End at the top, cleanly bordered by deep ocean
    Spawn lime in the center, surrounded by warm ocean ring
    Image: 804x806 (was 754x742). Generator handles any size; world size
    auto-derived from --world-size flag.
  Datapack regenerated, numbered overlay updated, deployed to Prism
  config/paxi/datapacks/karos-datapack. **New world** to test.
- 2026-05-10 Tectonic config retuned for dramatic peaks. Greg said the
  world felt "less interesting" / "regions should be a guideline not a
  hard border" / "mountains should be very drastic". Reverted my earlier
  conservative tuning back toward Tectonic defaults for the noise-scale
  knobs (so painted regions feel less like rigid blocks of biome) and
  bumped the vertical drama way up:
    biomes.temperature_scale:     0.5  → 0.25 (LARGER biome regions)
    biomes.vegetation_scale:      0.5  → 0.25 (LARGER biome regions)
    continents.continents_scale:  0.25 → 0.13 (LARGER continents)
    global_terrain.vertical_scale:  1.05 → 1.5
    global_terrain.elevation_boost: 0.0  → 2.0  ← biggest unlock; per
        Tectonic's tooltip this scales MOUNTAINS faster than lowlands,
        so spawn/plains stay normal-tall while painted gray (high
        mountain) zones become truly drastic.
  Tectonic tooltip notes vertical_scale "Game restart required on 1.20!"
  — likely true on 1.21 too. Restart MC + create new world to see.
  If still not dramatic enough at the gray mountain zones specifically,
  next step is fork-level: select_by_biome_color with a positive density
  bias in mountain colors (=> per-painted-zone terrain shape).
- 2026-05-10 caero_nether_atmosphere (NEW client-only mod) — mixin on
  LevelRenderer.renderClouds cancels at HEAD when local player is in a biome
  tagged minecraft:is_nether. Restart MC, walk into a painted Nether biome
  (nether_wastes / crimson_forest / warped_forest / soul_sand_valley /
  basalt_deltas) → clouds should disappear; walk back into an Overworld
  biome → clouds reappear. Sun is NOT touched in this pass — say so if
  you want it hidden too. Sodium is installed; if clouds still show in
  Nether biomes the inject probably lost a priority fight with sodium and
  we'll need an additional sodium-targeted mixin.
- 2026-05-10 karos terrain-overrides — re-activated
  (`config/paxi/datapacks/karos-terrain-overrides`, was parked). With
  this, lithostitched wraps the overworld `final_density` so
  `caero_karos:nether_final` / `:end_final` actually replace the visible
  terrain in painted zones, not just the underground caves. Density
  edits this turn drop the nether ceiling to ~Y=35 (caves above) and
  push end islands up to Y=222 with void to Y=194 → /tp coords:
    nether (caves):       /tp -272 36 2176
    end (floating island): /tp -192 224 -2192
  Note: there's still water at Y=62 below the end islands because
  the overworld noise settings have sea_level=63 and we don't override
  it; visible as "ocean of water below the void". Flag if it bothers
  you.
- 2026-05-10 karos mountain_final v16 — vanilla amplified final_density +
  Y-bias floor (val 10→0 from Y=64..150 — guarantees wall floor at Y=150)
  + abs(cube(ridges_folded))×12 + abs(cube(jaggedness))×60 (selective peak
  lift, only ridge cores spike) + cap Y=210..260. Distribution: 67% wall
  Y=150-159, 27% peaks Y=210+, max Y=223. Player has to climb 86 blocks
  to enter the band anywhere; once in it, peaks rise another 60+ blocks.
- 2026-05-10 karos mountain_final v10 — abandoned hand-rolled noise math
  (which produced alien spike towers) and dropped vanilla
  `minecraft:overworld/noise_settings/amplified` final_density verbatim into
  `caero_karos:mountain_final`. lithostitched select_by_biome_color still
  routes only mountain PNG colors through it. Distribution across 4096
  sample columns: P50=92, P90=122, P99=154, max=162 — actual amplified
  mountain shape, no plateau, no towers. → walk it; if peaks feel too short
  ("massive peaks"), I can stack a positive Y bias to push max to Y=200+
  while keeping the amplified ridge shape.
- 2026-05-10 karos mountain_final v9 — rewrote to use abs(cube(ridges_folded)) × 35
  + abs(cube(jaggedness)) × 120. The cube curve crushes mid-range noise, so
  only ridge-core columns lift dramatically; most of the band stays at vanilla
  Y=70-100 plains/foothills. Histogram across 4096 sample columns: P50=85,
  P90=203, P99=212, max=224. Only ~23% of the band has peaks above Y=200;
  ~75% is at Y=60-130. → fly the mountain band; should see scattered massive
  peaks rising 100+ blocks above rolling foothills, NOT a flat plateau wall.
- 2026-05-10 karos mountain_final density — added a third
  `lithostitched:wrap_noise_router` (`mountain_density_boost.json`,
  priority 9000) that swaps overworld `final_density` for mountain
  greys (#444..48 jagged/frozen, #888..8D stony/windswept) to a custom
  `caero_karos:mountain_final`. The function adds: gentle Y bias
  (+1.5 by Y=100), a hard cap above Y=190, and 12 × abs(ridges_folded)
  for shape variation. Net result in a 4×4-chunk patch sample:
  jagged_peaks Y=193..225 (mean 207), frozen_peaks Y=209..215. Plains
  outside the band stays at Y≈70 → a ~130-block cliff at the band
  edge. → /tp into the band: `/tp -1968 200 0` (jagged_peaks);
  fly to `/tp -1700 100 0` and look back west — should see a long
  N-S wall of peaks. Within-chunk variance is small (vanilla
  4×8-block density interpolation smooths it); chunk-to-chunk
  variance is what gives the ridge feel.
- 2026-05-10 caero_nether_atmosphere v3 — added a server-side mixin
  (`NetherFortressStructureMixin`, `@ModifyArg` on the BlockPos `<init>`
  call inside `findGenerationPoint`) that overrides vanilla's hardcoded
  Y=64 fortress anchor down to Y=-10. Mod's mods.toml flipped from
  client-only to BOTH so the mixin loads on the integrated server.
  → enter a painted nether zone (e.g. /tp -200 50 2200), explore /
  pre-generate the area, then `/locate structure minecraft:fortress`.
  Anchor should report Y=-10ish; dig down from the plains lid to reach
  fortress pieces buried in netherrack. Existing fortress chunks in
  already-generated saves won't move — only newly-generated ones use
  the new Y.
- 2026-05-10 caero_nether_atmosphere v2 — mixin now also cancels
  `LevelRenderer.renderSky` (sun+moon+stars+sky dome) in addition to
  `renderClouds`, AND triggers when player is in OR above a painted
  nether biome (checks biome at Y=20 and Y=-20 in the same column,
  catching the case where overhead.above_y=64 paints plains over the
  nether band). → fly above a nether-painted zone (e.g. /tp -272 100 2200);
  sky should go nether-fog red, sun gone, no clouds. Walk away into a
  non-nether biome → sky/sun/clouds reappear immediately.
- 2026-05-10 karos mask — restored. Moved
  `mods/disabled-karos-painted-mask/novoatlas-karos-*.jar` to `mods/`
  root (NeoForge ignores subfolders; that's why density-function types
  weren't registering and the world crashed last session). Renamed
  `config/paxi/_disabled-datapacks/.disabled-karos-datapack` → active
  `config/paxi/datapacks/karos-datapack`. Left
  `.disabled-karos-terrain-overrides` parked. → enter a fresh world
  (or your existing save 13); /tp to:
    nether:    /tp -188 80 2224  /tp -288 80 2160
    end:       /tp -204 80 -2156  /tp -128 80 -2172
    mountains: /tp -1992 120 76  /tp -1896 120 52
  Verify nether biomes show netherrack surface, end biomes show
  end_stone, mountains show jagged/frozen peaks. Surface Y will differ
  from these coords because terrain seed is per-world; come in flying.
- 2026-05-10 worldgen — Tectonic disabled in PrismLauncher mods/
  (`tectonic-3.0.22-neoforge-21.1.jar.disabled`); also dropped from staging
  by run-audit's existing `*\.disabled` filter → start a brand-new
  singleplayer world; player spawn Y should be 60–160 (was Y=200+ with
  Tectonic). If it's still in the sky on a fresh seed, the residual
  cause is caero_rings voronoi mislabeling mountain-shape as plains, not
  Tectonic — flag for follow-up.
- 2026-05-10 karos-mapgen audit — `bash season3/test/karos-mapgen/run-audit.sh`
  now passes 9/0/4 (was failing on a 272-chunk forceload limit, plus
  several thresholds calibrated to a Tectonic-on world). Patches now span
  all three ring tiers (9 × 4×4 patches over caero_rings seeds);
  `spawn_height` reads level.dat instead of avg-Y near origin → just
  re-run the audit; no in-game test needed beyond confirming a fresh
  world spawns sanely as above.
- 2026-05-10 paxi — moved `.disabled-karos-datapack` and
  `.disabled-karos-terrain-overrides` out of `config/paxi/datapacks/` into
  sibling `config/paxi/_disabled-datapacks/`; emptied `loadOrder` in
  `datapack_load_order.json` (orphan `zzz_caero_rings_wrap.zip` ref) →
  enter the singleplayer world; should no longer crash with
  "Failed to load registries" / "Unknown registry key
  novoatlas:select_by_biome_color", and `[paxi/ERROR]` line about the
  missing wrap zip should be gone from `latest.log`.
- 2026-05-16 seaeater (Sea Myths) — installed `seaeater-1.0.0.jar` and added
  biome modifiers in `season3/karos-datapack/data/seaeater/neoforge/biome_modifier/`
  (Sea Eater weight 6, El Gran Maja weight 4, all four vanilla deep-ocean
  biomes) → boot a karos world, sail/fly across a deep-ocean patch, expect
  to spot a Sea Eater or El Gran Maja **fairly often but not every time**
  (rough heuristic: one within ~30s of cruising near Y=40 in a deep biome).
  Verify neither spawns in shallow ocean / lakes / rivers. Confirm scale
  drops on kill.
- 2026-05-16 karos worldgen — overworld dimension JSON switched from
  `novoatlas:image_map` (heightmap-PNG-driven terrain) to vanilla
  `minecraft:noise` generator. Biomes still painted via the karos.png
  mask (`novoatlas:color_map` biome source); `karos-terrain-overrides`
  Lithostitched modifier now activates and supplies custom density for
  Nether-color and End-color painted zones. Heightmap PNG is no longer
  read — `map_info/karos.json` still references it for codec reasons →
  start a brand-new world with the karos-datapack. Verify: (a) ocean
  patches in the mask render as actual water at sea-level, not green
  hills; (b) jagged_peaks / frozen_peaks zones rise to vanilla-mountain
  heights; (c) painted End zones produce floating-island terrain via
  `caero_karos:end_final`; (d) painted Nether zones get nether-style
  terrain via `caero_karos:nether_final`. Re-run
  `bash season3/test/karos-mapgen/run-audit.sh` after — heightmap-related
  thresholds (deep-ocean coverage, spawn-Y) should still pass.
- 2026-05-16 chunky — enabled `Chunky-NeoForge-1.4.23.jar` (was
  `.disabled`) → boot a world, run `/chunky` in chat, confirm the
  command registers and `/chunky help` lists subcommands. Use
  `/chunky start <world> <x> <z> <radius>` to pre-gen a chunk area
  near spawn; DH should noticeably stop stuttering on chunk-gen.
- 2026-05-16 karos worldgen audit note — `run-audit.sh` now hits the 60s
  tick watchdog during forceload because `karos-terrain-overrides`'s
  `select_by_biome_color` density wrapper is newly active (was a no-op
  when the generator was `novoatlas:image_map`). PNG sampling per
  density-function call × 8 simultaneous forceload patches = chunk-gen
  blowup. Normal gradual chunk loading near a player should be fine; we
  may need to either (a) cache the biome PNG sample more aggressively
  in NovoAtlas's color_map provider, or (b) downsample/quartize the
  mask before the lithostitched wrapper runs. → flag if in-game terrain
  near the painted Nether/End zones causes player-side stutter.
- 2026-05-16 caero_atlas — added per-thread last-column cache to
  `BiomeColorSelectDensityFunction.compute()` so density-function calls
  along the same (x,z) column reuse the resolved selection index instead
  of resampling the biome PNG and walking the color list every time.
  Hot path is now ~1 PNG sample + 1 color match per column (≈256 per
  chunk) instead of per (x,y,z) eval (≈98k per chunk). Audit passes
  11/0/2 with the new `minecraft:noise` overworld generator and
  `karos-terrain-overrides` actively wrapping the noise router → no
  in-game test strictly required for the perf change, but boot a karos
  world, walk into a painted Nether or End zone, and confirm chunks
  load smoothly without per-chunk stutter.
- 2026-05-16 datapack deploy — repo karos-datapack edits weren't reaching
  Paxi until now (Paxi has its own copy under `config/paxi/datapacks/`).
  Added `season3/deploy-datapacks.sh` that rsyncs karos-datapack and
  karos-terrain-overrides into Paxi. Today's `minecraft:noise` generator
  switch only takes effect in worlds created AFTER this sync → spin up
  ANOTHER fresh singleplayer world (the earlier "fresh world" still used
  the stale image_map dimension JSON). Verify the painted deep-ocean
  zones now generate as actual water at sea level.

- 2026-05-16 tree-giant + karos-datapack — installed Tree Giant (taxtg
  2.0.1-neoforge-1.21.1) and added new biome `caero_karos:ancient_jungle`
  painted onto the deep-green zone of the Karos mask (color `#054E05`).
  Vanilla `minecraft:trees_jungle` stripped from this biome — only
  `taxtg:giant_jungle_tree` should spawn there. Spacing tightened to
  `18/10` for a dense canopy. The other 4 giant species (oak/birch/spruce/
  cherryblossom) remain at Tree Giant defaults and only spawn in their
  vanilla biomes. → in-game: (a) `/locate biome caero_karos:ancient_jungle`
  should resolve; tp there and confirm only giant jungle trees on the
  ground, no normal jungle trees. (b) `/locate biome minecraft:jungle`
  separately; tp there and confirm normal jungle trees only, NO giant
  jungle trees (the "no leak" requirement). (c) The other vanilla biomes
  with giant tree mappings — forest, birch_forest, taiga, cherry_grove —
  should show their respective giants where the mask paints them.

- 2026-05-16 kraken-mod — installed Kraken Mod 1.0.0 (`lairhisson_boss`,
  `.research/kraken-mod/kraken_mod-1.1neoforge-1.21.1.jar`). Adds a single
  jigsaw "Kraken Lair" structure spawning in `deep_ocean`, `deep_cold_ocean`,
  `deep_lukewarm_ocean` at default spacing 20/15. Running at mod defaults —
  no datapack overrides. → in-game: (a) `/locate structure
  lairhisson_boss:kraken_structure` should resolve; tp there and check the
  lair art + the kraken's behaviour and animation quality (this is the
  visual-quality decision point — Sea Eater currently coexists, so you can
  compare). (b) Confirm the kraken does NOT spawn outside the lair structure.
  (c) Kill it, pick up the key, find the treasure block inside the lair, open it.

- 2026-05-16 seaeater — **removed** (jar deleted from Prism instance,
  biome_modifier overrides at `season3/karos-datapack/data/seaeater/`
  deleted). → in-game: load the world; confirm no `seaeater:*` entities
  appear (try `/summon seaeater:sea_eater` — should fail "Unknown entity").
  No save corruption expected since vanilla just drops unknown entity IDs
  on load.

- 2026-05-16 caero_rings — painted-PNG mask dropped; themed Voronoi seeds
  added for mountain wall (-4000, 0), jungle pair (3500/-1000, 4000/300,
  4500/1500) and cursed wastes (0, -4500). Tectonic re-enabled. NovoAtlas
  + caero_nether_atmosphere disabled in Prism. Fresh world from seed 12345
  (or any seed — themed seeds are coord-anchored, not seed-anchored).
  → Verify:
    (a) Spawn (0, 0) is vanilla forest/plains/meadow, no painted weirdness.
    (b) Travel west ~3500 blocks → tall snowy/stony peaks (jagged_peaks,
        frozen_peaks, RU spires/towering_cliffs). Tectonic terrain shape.
    (c) Travel east ~3500 blocks → standard jungle, then ~500 blocks
        further east → mangrove/bayou swamp moat, then deeper east →
        ancient_jungle with Tree Giant trees.
    (d) Travel north ~4500 blocks → badlands/joshua_desert/ashen mix
        (the "Nether very far out" — NOT literal nether terrain).
    (e) The transition between the easy core and any themed cell has a
        ~400-block band of plain medium biomes (no abrupt cliffs).
    (f) Render preview: `glue/ring-biomes/renders/tier_voronoi_annotated.png`
        shows the theme layout. Compare in-world against this map.

- 2026-05-17 caero_rings — Nether/End rendering restored after over-correction.
  Layout: nether_core seed at (0, -4500) with 3 ashen cursed_wastes wrap
  seeds at (0, -3000), (-2000, -4500), (2000, -4500). End enclave at
  (4000, 2800). Lithostitched surface rule paints netherrack/nylium/
  soul_sand/basalt in nether biomes, end_stone in end biomes. Sky
  suppression via caero_nether_atmosphere re-enabled.
  → Verify in-game with a FRESH world (seed 12345 to match audit):
    (a) Travel north ~3000 blocks → enter ashen woodland / badlands.
    (b) Continue north past z≈-3750 → cross into netherrack territory
        with crimson_forest pockets etc. Sky should turn dark/red
        (caero_nether_atmosphere mixin should kick in standing on
        is_nether-tagged biomes).
    (c) Travel SE ~4900 blocks to (4000, 2800) → end_stone surface,
        end biome effects (dark sky from biome JSON). Terrain shape is
        currently vanilla Tectonic (NOT floating islands yet — see
        KAROS_NEXT_PLAN.md for follow-up notes).
    (d) Confirm spawn area is still vanilla forest/plains (no nether/end
        leakage near origin). Audit confirms ≥3500b distance from spawn
        but in-world inspection is the real test.

- 2026-05-17 caero_rings — biome diversity + Nether pit + End floating islands.
  Three changes:
  (1) themed cells now ALWAYS substitute (including oceans/rivers/beaches),
      fixing the "only one nether biome shows" issue. (2) BIOME_PATCH_SCALE
      dropped 1024 → 512 so each themed cell holds ~16 patches → ~all 5
      nether biomes / 4 end biomes visible. (3) new caero_rings:select_by_seed_theme
      density function dispatches final_density per Voronoi seed; nether_core
      cell uses nether_pit_final.json (pit Y=-30 to Y=70 with cavernous nether
      density), end_islands cell uses end_floating_final.json (floating islands
      Y=180-260 above the vanilla ground at Y<140 with an air gap between).
  → Verify (fresh world, seed 12345):
    (a) NETHER: travel north past z=-3750 → drop into a deep pit. Walls of
        netherrack with caverns; floor at Y≈-40 with crimson_forest /
        soul_sand_valley / basalt_deltas / warped_forest / nether_wastes
        patches scattered, not just one biome. Sky should be dark/red from
        the caero_nether_atmosphere mixin.
    (b) END: travel SE to (4000, 2800). Stand on regular Tectonic ground at
        Y≈80. Look up: should see giant end_stone islands floating at
        Y=180-260. Air gap between. End biomes (highlands/midlands/barrens/
        small_islands) variety visible across the cell. Below the islands is
        normal overworld terrain (you can walk under them).
    (c) The pit boundary at the cell edge is a SHARP cliff — player should
        approach carefully. (If this looks too jarring, smoothing the
        boundary requires a 5-way blend density wrap; not implemented.)
    (d) Biome diversity check in non-nether/end themed cells too: the
      mountain region should show multiple peak biomes; the jungle complex
      should show jungle, sparse_jungle, bamboo_jungle, RU tropics etc.

- 2026-05-17 caero_nether_atmosphere — extended dark-atmosphere effect to End biomes and generalised the predicate to a biome tag (`caero_nether_atmosphere:dark_atmosphere`, currently includes `#minecraft:is_nether` and `#minecraft:is_end`). Added LightTextureMixin that zeroes skylight contribution in dark biomes so the ground renders as vanilla midnight even at noon. Added MonsterMixin that makes hostile spawn rules treat dark biomes as night (block-light/torch check still applies — lit bases stay safe).
  → Verify in nether-painted region: at noon the world is as dark as midnight, no sun/moon/clouds/stars visible, only the red fog horizon. Place a torch — area around it stays safe. Wander unlit areas — vanilla hostiles (zombies/skeletons) should spawn during the day. Compare with prior behaviour where nether biomes still rendered bright at noon.
  → Verify in end-island region (around 4000, 2800 on seed 12345): same darkness/no-sky effect, but with End's dark-purple fog instead of red. Endermen/Phantoms/other vanilla hostiles spawning during day in unlit areas.
  → Sanity: standing in a normal overworld biome (plains, forest, mountains) the sun, sky, clouds should be unchanged and mobs should NOT spawn at noon. Confirms the tag predicate is gating correctly.

- 2026-05-17 caero_nether_atmosphere — Iris-shader compatibility fix. Added LevelDayTimeMixin (client-side) that forces Level.getDayTime() to return 18000 (midnight) when player is in a dark_atmosphere biome. Shaders' sunAngle uniform derives from time-of-day → they render night sky/lighting naturally; vanilla rendering also goes dark via getSkyDarken being clamped near 0 by the time override. Works with a shader pack loaded.
  → Verify with shaders ON (Complementary/BSL/SEUS-class pack): walk into nether biome painted region. Sky should snap to night-sky, sun should drop below horizon, world should darken via shader lighting. Same in End biome region.
  → Verify with shaders OFF: same dark effect via the pre-existing void-sky cancel + LightTextureMixin.
  → Sanity: stand in a normal overworld biome — sun/clouds/daylight unchanged. Daylight sensors (server-side) should still show real time-of-day power. Beds should sleep at real night, not biome-induced "night".

- 2026-05-17 caero_nether_atmosphere — added `caero_karos:ancient_jungle` to the `dark_atmosphere` biome tag. The existing ancient_jungle setup (giant_jungle_tree densified to 12/6 spacing, jungle_deep ring-biomes theme) is now also dark — sky/sun/clouds suppressed, hostile mobs spawn during day. Restart MC, fly to the jungle_deep cell (around 4500, 1500 on seed 12345), confirm: dark sky, no sun/clouds (without shaders), giant trees visible, mobs spawning at noon in unlit spots.

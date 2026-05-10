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

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

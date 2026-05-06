package com.caero.specialization.skill

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-input XP-weight registry. Loaded from
 * [com.caero.specialization.config.CaeroSpecializationConfig] at startup
 * and reparsed on every config reload, so live-edits to the toml take
 * effect on the next refine without a restart.
 *
 * Final XP awarded per refine =
 *   `xpPerRefineFor(skill)` × `weightFor(skill, input)`.
 *
 * Weight defaults reflect crafting cost / rarity in vanilla:
 * - Forestry: bamboo 0.5, overworld logs 1.0, nether stems 1.5, charcoal 1.5
 *   (one extra smelt step over a log), coal 2.0, coal_block 18.0.
 * - Mining: raw_copper 1.0, raw_iron 2.0, raw_gold 3.0, raw_zinc 1.5,
 *   ancient_debris 10.0 (endgame).
 * - Armourer: material × type — wood 0.3, stone 0.5, leather 0.8,
 *   chainmail 1.5, iron 2.0, golden 1.5, diamond 4.0, netherite 8.0;
 *   shovel 0.6, sword/hoe 1.0, pickaxe/axe 1.4, helmet 2.0, boots 1.7,
 *   leggings 2.5, chestplate 3.0. Final = mat × type.
 * - Husbandry: trivial (egg/berry/melon) 0.6-0.8, crops 1.0, premium crops 1.5,
 *   raw meat 1.5, cooked meat 2.5 (extra smelt step), leather 3.0, honey 4.0.
 * - Alchemist: 2.0 per potion (placeholder — refine until potion-tier
 *   detection lands).
 * - Jewelery: stone-input ladder - common 0.2, deep 0.5, exotic 1.0. Stone is
 *   abundant so per-refine XP stays low even at high jeweler level. Salvaged
 *   tools always read the armourer table.
 * - Fishing: cod/salmon 1.0, tropical 1.5, pufferfish 2.0.
 *
 * Items absent from the configured list fall back to [DEFAULT_WEIGHT]
 * (1.0). The [parseEntry] format is `"namespace:item_id=weight"`.
 */
object XpWeights {

    private val LOG = LoggerFactory.getLogger("caero_specialization.XpWeights")

    const val DEFAULT_WEIGHT: Double = 1.0

    /**
     * Stored per-skill weight maps. Volatile-friendly via ConcurrentHashMap
     * since config reloads happen on the modloading-sync-worker thread but
     * refines fire from the server tick thread.
     */
    private val weights: MutableMap<SkillKind, Map<ResourceLocation, Double>> = ConcurrentHashMap()

    fun weightFor(skill: SkillKind, item: Item): Double {
        val key = BuiltInRegistries.ITEM.getKey(item)
        return weights[skill]?.get(key) ?: DEFAULT_WEIGHT
    }

    /** Replace the active weight table for [skill] with [parsed]. Called
     *  from the config-load / config-reload listeners. */
    fun setWeights(skill: SkillKind, parsed: Map<ResourceLocation, Double>) {
        weights[skill] = parsed
        LOG.info("XP weights for {} reloaded — {} entries", skill, parsed.size)
    }

    /**
     * Parse a list of `"namespace:item=weight"` strings into a map.
     * Malformed entries are dropped with a warning (so a typo in one
     * line doesn't blow up the whole skill).
     */
    fun parseList(skill: SkillKind, entries: List<String>): Map<ResourceLocation, Double> {
        val out = HashMap<ResourceLocation, Double>(entries.size)
        for (raw in entries) {
            val entry = raw.trim()
            if (entry.isEmpty() || entry.startsWith("#")) continue
            val eq = entry.indexOf('=')
            if (eq <= 0 || eq == entry.length - 1) {
                LOG.warn("XP weight for {} skipped — no '=' separator: '{}'", skill, raw)
                continue
            }
            val idPart = entry.substring(0, eq).trim()
            val weightPart = entry.substring(eq + 1).trim()
            val rl = ResourceLocation.tryParse(idPart) ?: run {
                LOG.warn("XP weight for {} skipped — unparseable id '{}'", skill, idPart)
                continue
            }
            val w = weightPart.toDoubleOrNull()?.takeIf { it >= 0.0 } ?: run {
                LOG.warn("XP weight for {} skipped — invalid weight '{}'", skill, weightPart)
                continue
            }
            out[rl] = w
        }
        return out
    }

    // ---------------------------------------------------------------------
    // Default tables — exported so the config defaults stay in one place
    // and tests can assert the shape.
    // ---------------------------------------------------------------------

    val FORESTRY_DEFAULTS: List<String> = listOf(
        // Cheapest renewable fuel — bamboo grows in minutes.
        "minecraft:bamboo_block=0.5",
        // Overworld logs — baseline.
        "minecraft:oak_log=1.0",
        "minecraft:birch_log=1.0",
        "minecraft:spruce_log=1.0",
        "minecraft:jungle_log=1.0",
        "minecraft:acacia_log=1.0",
        "minecraft:dark_oak_log=1.0",
        "minecraft:mangrove_log=1.0",
        "minecraft:cherry_log=1.0",
        // Nether stems — requires a nether trip.
        "minecraft:crimson_stem=1.5",
        "minecraft:warped_stem=1.5",
        // Charcoal: one log + smelt step. Higher than logs by Greg's rule
        // (extra processing step) — raw log = 1.0, smelt to charcoal = 1.5.
        "minecraft:charcoal=1.5",
        // Coal: mined, depletable. Slightly above charcoal.
        "minecraft:coal=2.0",
        // Coal block: 9× coal compactness — preserve the incentive.
        "minecraft:coal_block=18.0",
    )

    val MINING_DEFAULTS: List<String> = listOf(
        "minecraft:raw_copper=1.0",
        "minecraft:raw_iron=2.0",
        "minecraft:raw_gold=3.0",
        "create:raw_zinc=1.5",
        // Endgame — locked behind nether exploration + Y-level patience.
        "minecraft:ancient_debris=10.0",
    )

    /**
     * Stone-family inputs. Cobble is abundant so the weights are intentionally
     * low — per-refine XP for cobble at xpPerRefine=50 is 50 × 0.2 = 10 XP,
     * vs 100 XP for raw_iron at the mining refiner. Tier mirrors the depth
     * ladder enforced in [com.caero.specialization.jewelery.JewelryRolls].
     */
    val JEWELERY_DEFAULTS: List<String> = listOf(
        // T1 common (overworld surface).
        "minecraft:cobblestone=0.2",
        "minecraft:stone=0.2",
        "minecraft:granite=0.2",
        "minecraft:diorite=0.2",
        "minecraft:andesite=0.2",
        "minecraft:tuff=0.2",
        "minecraft:polished_granite=0.2",
        "minecraft:polished_diorite=0.2",
        "minecraft:polished_andesite=0.2",
        "minecraft:polished_tuff=0.2",
        // T2 deep (Y<0).
        "minecraft:deepslate=0.5",
        "minecraft:cobbled_deepslate=0.5",
        "minecraft:polished_deepslate=0.5",
        // T3 exotic (nether/end).
        "minecraft:blackstone=1.0",
        "minecraft:basalt=1.0",
        "minecraft:end_stone=1.0",
        // Direct gem-cut: vanilla emerald → cut emerald. Emeralds are
        // expensive in trade and rare in the world; weight reflects that.
        "minecraft:emerald=4.0",
    )

    val HUSBANDRY_DEFAULTS: List<String> = listOf(
        // Crops — abundant once farm is set up.
        "minecraft:wheat=1.0",
        "minecraft:carrot=1.0",
        "minecraft:potato=1.0",
        "minecraft:beetroot=1.0",
        "minecraft:melon_slice=0.6",
        "minecraft:apple=1.5",
        "minecraft:sweet_berries=0.7",
        "minecraft:glow_berries=1.2",
        "minecraft:pumpkin=1.5",
        // Cooked / processed grains.
        "minecraft:bread=2.0",
        "minecraft:cookie=1.5",
        "minecraft:pumpkin_pie=2.5",
        // Animal proteins — raw. Below cooked by one tier (extra smelt step
        // earns XP; matches "logs < charcoal" rule on the forestry side).
        "minecraft:beef=1.5",
        "minecraft:porkchop=1.5",
        "minecraft:chicken=1.2",
        "minecraft:mutton=1.5",
        "minecraft:rabbit=2.0",
        "minecraft:cod=1.2",
        "minecraft:salmon=1.2",
        "minecraft:tropical_fish=2.0",
        // Cooked meats — explicit tier above raw.
        "minecraft:cooked_beef=2.5",
        "minecraft:cooked_porkchop=2.5",
        "minecraft:cooked_chicken=2.0",
        "minecraft:cooked_mutton=2.5",
        "minecraft:cooked_rabbit=3.0",
        "minecraft:cooked_cod=2.0",
        "minecraft:cooked_salmon=2.0",
        // Animal byproducts.
        "minecraft:leather=3.0",
        "minecraft:rabbit_hide=2.0",
        "minecraft:feather=1.0",
        "minecraft:egg=0.8",
        "minecraft:milk_bucket=2.0",
        "minecraft:honeycomb=4.0",
        "minecraft:honey_bottle=3.0",
        // Wool — flat 1.0 across all 16 colors. Quality-gated by sail/envelope
        // recipes (see refiner-graph.html note 2026-05-04).
        "minecraft:white_wool=1.0",
        "minecraft:orange_wool=1.0",
        "minecraft:magenta_wool=1.0",
        "minecraft:light_blue_wool=1.0",
        "minecraft:yellow_wool=1.0",
        "minecraft:lime_wool=1.0",
        "minecraft:pink_wool=1.0",
        "minecraft:gray_wool=1.0",
        "minecraft:light_gray_wool=1.0",
        "minecraft:cyan_wool=1.0",
        "minecraft:purple_wool=1.0",
        "minecraft:blue_wool=1.0",
        "minecraft:brown_wool=1.0",
        "minecraft:green_wool=1.0",
        "minecraft:red_wool=1.0",
        "minecraft:black_wool=1.0",
        // Farmer's Delight crops.
        "farmersdelight:tomato=1.0",
        "farmersdelight:onion=1.0",
        "farmersdelight:cabbage=1.0",
        "farmersdelight:rice=1.0",
        "farmersdelight:rice_panicle=1.0",
        "farmersdelight:cabbage_leaf=0.5",
    )

    val ALCHEMIST_DEFAULTS: List<String> = listOf(
        "minecraft:potion=2.0",
        "minecraft:splash_potion=2.0",
        "minecraft:lingering_potion=2.5",
    )

    val FISHING_DEFAULTS: List<String> = listOf(
        "minecraft:cod=1.0",
        "minecraft:salmon=1.0",
        "minecraft:tropical_fish=1.5",
        "minecraft:pufferfish=2.0",
    )

    private val MATERIAL_WEIGHT: Map<String, Double> = linkedMapOf(
        "wooden" to 0.3,
        "stone" to 0.5,
        "leather" to 0.8,
        "chainmail" to 1.5,
        "iron" to 2.0,
        "golden" to 1.5,
        "diamond" to 4.0,
        "netherite" to 8.0,
    )

    private val TOOL_TYPE_WEIGHT: Map<String, Double> = linkedMapOf(
        "shovel" to 0.6,
        "sword" to 1.0,
        "hoe" to 1.0,
        "pickaxe" to 1.4,
        "axe" to 1.4,
    )

    private val ARMOR_TYPE_WEIGHT: Map<String, Double> = linkedMapOf(
        "boots" to 1.7,
        "helmet" to 2.0,
        "leggings" to 2.5,
        "chestplate" to 3.0,
    )

    private val MATERIALS_WITH_TOOLS = setOf("wooden", "stone", "iron", "golden", "diamond", "netherite")
    private val MATERIALS_WITH_ARMOR = setOf("leather", "chainmail", "iron", "golden", "diamond", "netherite")

    /**
     * Armourer defaults computed from material × type. Updating either
     * table here re-bakes every entry — useful when bulk-rebalancing.
     * Declared after MATERIAL_WEIGHT/TYPE_WEIGHT so initialization order works.
     */
    val ARMOURER_DEFAULTS: List<String> = computeArmourerDefaults()

    private fun computeArmourerDefaults(): List<String> {
        val out = mutableListOf<String>()
        for ((mat, mw) in MATERIAL_WEIGHT) {
            if (mat in MATERIALS_WITH_TOOLS) {
                for ((type, tw) in TOOL_TYPE_WEIGHT) {
                    out += "minecraft:${mat}_$type=${formatWeight(mw * tw)}"
                }
            }
            if (mat in MATERIALS_WITH_ARMOR) {
                for ((type, tw) in ARMOR_TYPE_WEIGHT) {
                    out += "minecraft:${mat}_$type=${formatWeight(mw * tw)}"
                }
            }
        }
        // Specials.
        out += "minecraft:turtle_helmet=2.5"  // niche, scute-gated
        return out
    }

    /** Format a weight to at most 2 decimal places, dropping trailing zeros. */
    private fun formatWeight(w: Double): String {
        val rounded = Math.round(w * 100.0) / 100.0
        return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
        else rounded.toString()
    }
}

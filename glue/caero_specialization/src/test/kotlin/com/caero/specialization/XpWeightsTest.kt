package com.caero.specialization

import com.caero.specialization.skill.SkillKind
import com.caero.specialization.skill.XpWeights
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XpWeightsTest {

    @Test
    fun parseListBasicEntries() {
        val parsed = XpWeights.parseList(
            SkillKind.MINING,
            listOf("minecraft:raw_iron=2.0", "minecraft:raw_gold=3.5"),
        )
        assertEquals(2.0, parsed[ResourceLocation.parse("minecraft:raw_iron")])
        assertEquals(3.5, parsed[ResourceLocation.parse("minecraft:raw_gold")])
    }

    @Test
    fun parseListSkipsBlankAndComments() {
        val parsed = XpWeights.parseList(
            SkillKind.MINING,
            listOf("", "  ", "# comment", "minecraft:raw_iron=2.0"),
        )
        assertEquals(1, parsed.size)
        assertEquals(2.0, parsed[ResourceLocation.parse("minecraft:raw_iron")])
    }

    @Test
    fun parseListSkipsMalformedEntries() {
        val parsed = XpWeights.parseList(
            SkillKind.MINING,
            listOf(
                "no_equals_here",
                "minecraft:raw_iron=",
                "=3.5",
                "minecraft:raw_iron=not_a_number",
                "minecraft:raw_iron=-1.0",  // negative weights rejected
                "minecraft:raw_gold=3.5",  // valid, should be the only entry
            ),
        )
        assertEquals(1, parsed.size)
        assertEquals(3.5, parsed[ResourceLocation.parse("minecraft:raw_gold")])
    }

    @Test
    fun armourerDefaultsCoverMaterialAndType() {
        val list = XpWeights.ARMOURER_DEFAULTS
        // Spot-check anchors. wooden sword (mat 0.3 × type 1.0) = 0.3
        assertTrue(list.any { it == "minecraft:wooden_sword=0.3" }, "wooden_sword=0.3 missing")
        // iron pickaxe (2.0 × 1.4) = 2.8
        assertTrue(list.any { it == "minecraft:iron_pickaxe=2.8" }, "iron_pickaxe=2.8 missing")
        // diamond chestplate (4.0 × 3.0) = 12.0
        assertTrue(list.any { it == "minecraft:diamond_chestplate=12" }, "diamond_chestplate=12 missing")
        // netherite leggings (8.0 × 2.5) = 20.0
        assertTrue(list.any { it == "minecraft:netherite_leggings=20" }, "netherite_leggings=20 missing")
        // turtle helmet specialty
        assertTrue(list.any { it == "minecraft:turtle_helmet=2.5" }, "turtle_helmet=2.5 missing")
        // No tools for leather/chainmail (no leather_pickaxe etc.)
        assertTrue(list.none { it.contains("leather_pickaxe") }, "leather_pickaxe should not exist")
        assertTrue(list.none { it.contains("chainmail_sword") }, "chainmail_sword should not exist")
    }

    @Test
    fun armourerDefaultsAreInternallyConsistent() {
        // Every default parses cleanly (no malformed entries make it into the
        // baked list).
        val parsed = XpWeights.parseList(SkillKind.ARMOURER, XpWeights.ARMOURER_DEFAULTS)
        assertEquals(XpWeights.ARMOURER_DEFAULTS.size, parsed.size,
            "${XpWeights.ARMOURER_DEFAULTS.size - parsed.size} armourer defaults failed to parse")
    }

    @Test
    fun materialCostOrderingHolds() {
        // For the same tool type, weight should rise: wood < stone < iron < diamond < netherite.
        val parsed = XpWeights.parseList(SkillKind.ARMOURER, XpWeights.ARMOURER_DEFAULTS)
        val pickaxes = listOf("wooden", "stone", "iron", "diamond", "netherite")
            .map { parsed[ResourceLocation.parse("minecraft:${it}_pickaxe")]!! }
        for (i in 1 until pickaxes.size) {
            assertTrue(pickaxes[i] > pickaxes[i - 1],
                "pickaxe order broken: ${pickaxes[i - 1]} >= ${pickaxes[i]} at index $i")
        }
    }

    @Test
    fun typeCostOrderingHoldsForArmor() {
        // For the same armour material, weight should rise: boots < helmet < leggings < chestplate.
        val parsed = XpWeights.parseList(SkillKind.ARMOURER, XpWeights.ARMOURER_DEFAULTS)
        val ironArmor = listOf("boots", "helmet", "leggings", "chestplate")
            .map { parsed[ResourceLocation.parse("minecraft:iron_$it")]!! }
        for (i in 1 until ironArmor.size) {
            assertTrue(ironArmor[i] > ironArmor[i - 1],
                "iron armour order broken at index $i: ${ironArmor[i - 1]} >= ${ironArmor[i]}")
        }
    }

    @Test
    fun miningDefaultsRespectRarity() {
        val parsed = XpWeights.parseList(SkillKind.MINING, XpWeights.MINING_DEFAULTS)
        val copper = parsed[ResourceLocation.parse("minecraft:raw_copper")]!!
        val iron = parsed[ResourceLocation.parse("minecraft:raw_iron")]!!
        val gold = parsed[ResourceLocation.parse("minecraft:raw_gold")]!!
        assertTrue(copper < iron && iron < gold,
            "rarity order broken: copper=$copper iron=$iron gold=$gold")
    }
}

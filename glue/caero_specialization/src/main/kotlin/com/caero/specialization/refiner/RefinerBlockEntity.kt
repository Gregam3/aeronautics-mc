package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.skill.SkillKind
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.items.ItemStackHandler
import java.util.UUID

class RefinerBlockEntity(
    pos: BlockPos,
    state: BlockState,
    val skill: SkillKind,
) : BlockEntity(typeFor(skill), pos, state) {

    var ownerUuid: UUID? = null
        private set
    var ownerName: String = "<unowned>"
        private set
    var feeSpurs: Int = CaeroSpecializationConfig.DEFAULT_REFINER_FEE.get()
        private set
    var coffer: Long = 0L
        private set

    /**
     * 3-slot inventory: input · quality catalyst · amplifier catalyst.
     *
     * - **Slot 0 (input):** must match the consumer skill's `REFINABLE_*` tag.
     * - **Slot 1 (quality catalyst):** must be in `catalyst_quality_<skill>` tag.
     * - **Slot 2 (amplifier catalyst):** must be in `catalyst_amplifier_<skill>` tag.
     *
     * Refined output goes **directly to the customer's inventory** (overflow
     * drops at the player's feet) — no output slot. Removes the take-it-out
     * step and avoids the "output full → can't refine" failure mode.
     *
     * Hopper insert/extract is restricted so automation can't drive refining —
     * the social loop requires a real player at the GUI. See [isItemValid].
     */
    val items: ItemStackHandler = object : ItemStackHandler(SLOT_COUNT) {
        override fun onContentsChanged(slot: Int) {
            super.onContentsChanged(slot)
            setChanged()
        }

        override fun isItemValid(slot: Int, stack: ItemStack): Boolean = when (slot) {
            SLOT_INPUT -> RefineExecution.acceptsAsInput(skill, stack)
            SLOT_QUALITY_CATALYST -> CatalystRegistry.classify(skill, stack) == CatalystKind.QUALITY
            SLOT_AMPLIFIER_CATALYST -> CatalystRegistry.classify(skill, stack) == CatalystKind.AMPLIFIER
            else -> false
        }
    }

    fun bindOwner(uuid: UUID, name: String) {
        if (ownerUuid != null) return
        ownerUuid = uuid
        ownerName = name
        feeSpurs = CaeroSpecializationConfig.DEFAULT_REFINER_FEE.get()
        setChanged()
    }

    fun forceSetOwner(uuid: UUID, name: String) {
        ownerUuid = uuid
        ownerName = name
        setChanged()
    }

    fun setFee(value: Int): Int {
        val cap = CaeroSpecializationConfig.MAX_REFINER_FEE.get()
        feeSpurs = value.coerceIn(0, cap)
        setChanged()
        return feeSpurs
    }

    fun depositCoffer(amount: Int) {
        if (amount <= 0) return
        coffer = (coffer + amount).coerceAtMost(Long.MAX_VALUE / 2)
        setChanged()
    }

    fun drainCoffer(): Long {
        val out = coffer
        coffer = 0L
        if (out > 0L) setChanged()
        return out
    }

    /**
     * Drop all four inventory slots when the block is broken so players don't
     * lose loaded inputs / catalysts / output. Caller (Block.onRemove) should
     * pass them to `Containers.dropContents` or similar.
     */
    fun dropInventoryContents(): List<ItemStack> = (0 until SLOT_COUNT)
        .map { items.extractItem(it, Int.MAX_VALUE, false) }
        .filterNot { it.isEmpty }

    override fun loadAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.loadAdditional(tag, registries)
        ownerUuid = if (tag.hasUUID("OwnerUuid")) tag.getUUID("OwnerUuid") else null
        ownerName = if (tag.contains("OwnerName")) tag.getString("OwnerName") else "<unowned>"
        feeSpurs = if (tag.contains("FeeSpurs")) {
            tag.getInt("FeeSpurs").coerceIn(0, CaeroSpecializationConfig.MAX_REFINER_FEE.get())
        } else {
            CaeroSpecializationConfig.DEFAULT_REFINER_FEE.get()
        }
        coffer = if (tag.contains("Coffer")) tag.getLong("Coffer").coerceAtLeast(0L) else 0L
        if (tag.contains("Inventory")) {
            items.deserializeNBT(registries, tag.getCompound("Inventory"))
        }
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        ownerUuid?.let { tag.putUUID("OwnerUuid", it) }
        tag.putString("OwnerName", ownerName)
        tag.putInt("FeeSpurs", feeSpurs)
        tag.putLong("Coffer", coffer)
        tag.put("Inventory", items.serializeNBT(registries))
    }

    companion object {
        const val SLOT_INPUT = 0
        const val SLOT_QUALITY_CATALYST = 1
        const val SLOT_AMPLIFIER_CATALYST = 2
        const val SLOT_COUNT = 3

        private fun typeFor(skill: SkillKind): BlockEntityType<RefinerBlockEntity> = when (skill) {
            SkillKind.FORESTRY -> CaeroSpecialization.FORESTRY_REFINER_BE_TYPE.get()
            SkillKind.MINING -> CaeroSpecialization.MINING_REFINER_BE_TYPE.get()
            SkillKind.ARMOURER -> CaeroSpecialization.ARMOURER_REFINER_BE_TYPE.get()
            SkillKind.HUSBANDRY -> CaeroSpecialization.HUSBANDRY_REFINER_BE_TYPE.get()
            SkillKind.ALCHEMIST -> CaeroSpecialization.ALCHEMIST_REFINER_BE_TYPE.get()
            SkillKind.JEWELERY -> CaeroSpecialization.JEWELERY_REFINER_BE_TYPE.get()
            SkillKind.FISHING -> CaeroSpecialization.FISHING_REFINER_BE_TYPE.get()
        }
    }
}

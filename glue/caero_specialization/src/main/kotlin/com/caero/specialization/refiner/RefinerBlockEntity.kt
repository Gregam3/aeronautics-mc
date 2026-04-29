package com.caero.specialization.refiner

import com.caero.specialization.CaeroSpecialization
import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.skill.SkillKind
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
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
    }

    override fun saveAdditional(tag: CompoundTag, registries: HolderLookup.Provider) {
        super.saveAdditional(tag, registries)
        ownerUuid?.let { tag.putUUID("OwnerUuid", it) }
        tag.putString("OwnerName", ownerName)
        tag.putInt("FeeSpurs", feeSpurs)
        tag.putLong("Coffer", coffer)
    }

    companion object {
        private fun typeFor(skill: SkillKind): BlockEntityType<RefinerBlockEntity> = when (skill) {
            SkillKind.FORESTRY -> CaeroSpecialization.FORESTRY_REFINER_BE_TYPE.get()
            SkillKind.MINING -> CaeroSpecialization.MINING_REFINER_BE_TYPE.get()
            SkillKind.ARMOURER -> CaeroSpecialization.ARMOURER_REFINER_BE_TYPE.get()
        }
    }
}

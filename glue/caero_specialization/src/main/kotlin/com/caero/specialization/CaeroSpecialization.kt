package com.caero.specialization

import com.caero.specialization.byproduct.AshItem
import com.caero.specialization.command.SpecializationCommands
import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.fuel.FuelBurnTime
import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.caero.specialization.quality.QualityTooltip
import com.caero.specialization.recipe.QualitySmeltingRecipe
import com.caero.specialization.refiner.RefinerBlock
import com.caero.specialization.refiner.RefinerBlockEntity
import com.caero.specialization.refiner.RefinerInteraction
import com.caero.specialization.skill.SkillAttachment
import com.caero.specialization.skill.SkillKind
import com.caero.specialization.skill.SkillLoginListener
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.DeferredRegister
import org.slf4j.LoggerFactory
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.function.Supplier

@Mod(CaeroSpecialization.MOD_ID)
object CaeroSpecialization {
    const val MOD_ID = "caero_specialization"
    val LOG = LoggerFactory.getLogger(MOD_ID)!!

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(MOD_ID)
    val BLOCKS: DeferredRegister.Blocks = DeferredRegister.createBlocks(MOD_ID)
    val BLOCK_ENTITY_TYPES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID)
    val RECIPE_SERIALIZERS: DeferredRegister<RecipeSerializer<*>> =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID)
    val CREATIVE_TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

    private val refinerBlockProps = BlockBehaviour.Properties.of()
        .mapColor(MapColor.STONE)
        .strength(2.0f)
        .requiresCorrectToolForDrops()

    val FORESTRY_REFINER = BLOCKS.registerBlock("forestry_refiner",
        { props -> RefinerBlock(props, SkillKind.FORESTRY) },
        refinerBlockProps,
    )
    val MINING_REFINER = BLOCKS.registerBlock("mining_refiner",
        { props -> RefinerBlock(props, SkillKind.MINING) },
        refinerBlockProps,
    )

    val FORESTRY_REFINER_ITEM = ITEMS.registerItem("forestry_refiner") { props ->
        BlockItem(FORESTRY_REFINER.get(), props)
    }
    val MINING_REFINER_ITEM = ITEMS.registerItem("mining_refiner") { props ->
        BlockItem(MINING_REFINER.get(), props)
    }

    val ASH_ITEM = ITEMS.registerItem("ash") { props -> AshItem(props) }

    val FORESTRY_REFINER_BE_TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<RefinerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("forestry_refiner") { ->
            @Suppress("DEPRECATION")
            BlockEntityType.Builder.of(
                { pos, state -> RefinerBlockEntity(pos, state, SkillKind.FORESTRY) },
                FORESTRY_REFINER.get(),
            ).build(null)
        }
    val MINING_REFINER_BE_TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<RefinerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("mining_refiner") { ->
            @Suppress("DEPRECATION")
            BlockEntityType.Builder.of(
                { pos, state -> RefinerBlockEntity(pos, state, SkillKind.MINING) },
                MINING_REFINER.get(),
            ).build(null)
        }

    val QUALITY_SMELTING_SERIALIZER: DeferredHolder<RecipeSerializer<*>, QualitySmeltingRecipe.Serializer> =
        RECIPE_SERIALIZERS.register("quality_smelting") { -> QualitySmeltingRecipe.Serializer }

    @Suppress("unused")
    val CREATIVE_TAB = CREATIVE_TABS.register("main", Supplier {
        CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.caero_specialization"))
            .icon { ItemStack(FORESTRY_REFINER_ITEM.get()) }
            .displayItems { _, output ->
                output.accept(FORESTRY_REFINER_ITEM.get())
                output.accept(MINING_REFINER_ITEM.get())
                output.accept(ASH_ITEM.get())
                val sampleBases = arrayOf(
                    Items.CHARCOAL, Items.COAL,
                    Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER,
                )
                for (base in sampleBases) {
                    for (q in arrayOf(Quality.LOW, Quality.MEDIUM, Quality.HIGH)) {
                        val sample = ItemStack(base)
                        sample.set(QualityComponent.QUALITY.get(), q)
                        output.accept(sample)
                    }
                }
            }
            .build()
    })

    init {
        LOG.info("caero_specialization init starting")

        ModList.get().getModContainerById(MOD_ID).orElseThrow()
            .registerConfig(ModConfig.Type.COMMON, CaeroSpecializationConfig.SPEC)

        ITEMS.register(MOD_BUS)
        BLOCKS.register(MOD_BUS)
        BLOCK_ENTITY_TYPES.register(MOD_BUS)
        RECIPE_SERIALIZERS.register(MOD_BUS)
        CREATIVE_TABS.register(MOD_BUS)
        QualityComponent.COMPONENTS.register(MOD_BUS)
        SkillAttachment.ATTACHMENTS.register(MOD_BUS)

        MOD_BUS.addListener(::onCommonSetup)
        if (FMLEnvironment.dist.isClient) {
            MOD_BUS.addListener(::onClientSetup)
        }

        NeoForge.EVENT_BUS.register(FuelBurnTime)
        NeoForge.EVENT_BUS.register(QualityTooltip)
        NeoForge.EVENT_BUS.register(RefinerInteraction)
        NeoForge.EVENT_BUS.register(SkillLoginListener)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOG.info("caero_specialization common setup complete")
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOG.info("caero_specialization client setup — registering quality item-model property")
        event.enqueueWork {
            com.caero.specialization.quality.QualityClientProperty.register()
        }
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        SpecializationCommands.register(event.dispatcher)
    }
}

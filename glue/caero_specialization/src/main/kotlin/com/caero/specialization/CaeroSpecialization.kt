package com.caero.specialization

import com.caero.specialization.byproduct.AshItem
import com.caero.specialization.command.SpecializationCommands
import com.caero.specialization.config.CaeroSpecializationConfig
import com.caero.specialization.disable.AnvilDisable
import com.caero.specialization.disable.VillagerTradeDisable
import com.caero.specialization.fuel.FuelBurnTime
import com.caero.specialization.gem.GemItem
import com.caero.specialization.gem.GemKind
import com.caero.specialization.gem.GemSocketsHandler
import com.caero.specialization.gem.GemSocketsRegistry
import com.caero.specialization.gem.SocketingTableBlock
import com.caero.specialization.gem.SocketingTableScreen
import com.caero.specialization.quality.Quality
import com.caero.specialization.quality.QualityComponent
import com.caero.specialization.quality.QualityTooltip
import com.caero.specialization.recipe.QualitySmeltingRecipe
import com.caero.specialization.refiner.AttributeQualityScaler
import com.caero.specialization.refiner.DurabilityNerfTicker
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
    val MENU_TYPES: DeferredRegister<net.minecraft.world.inventory.MenuType<*>> =
        DeferredRegister.create(Registries.MENU, MOD_ID)

    val INGREDIENT_TYPES: DeferredRegister<net.neoforged.neoforge.common.crafting.IngredientType<*>> =
        DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.INGREDIENT_TYPES, MOD_ID)

    val QUALITY_GATED_INGREDIENT: net.neoforged.neoforge.registries.DeferredHolder<
        net.neoforged.neoforge.common.crafting.IngredientType<*>,
        net.neoforged.neoforge.common.crafting.IngredientType<com.caero.specialization.recipe.QualityGatedIngredient>
    > = INGREDIENT_TYPES.register("quality_gated") { ->
        net.neoforged.neoforge.common.crafting.IngredientType(
            com.caero.specialization.recipe.QualityGatedIngredient.CODEC,
            com.caero.specialization.recipe.QualityGatedIngredient.STREAM_CODEC,
        )
    }

    val REFINER_MENU: DeferredHolder<net.minecraft.world.inventory.MenuType<*>, net.minecraft.world.inventory.MenuType<com.caero.specialization.refiner.RefinerMenu>> =
        MENU_TYPES.register("refiner") { ->
            net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create<com.caero.specialization.refiner.RefinerMenu> { id, inv, buf ->
                com.caero.specialization.refiner.RefinerMenu.fromBuffer(id, inv,
                    buf as net.minecraft.network.RegistryFriendlyByteBuf)
            }
        }

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
    val ARMOURER_REFINER = BLOCKS.registerBlock("armourer_refiner",
        { props -> RefinerBlock(props, SkillKind.ARMOURER) },
        refinerBlockProps,
    )
    val HUSBANDRY_REFINER = BLOCKS.registerBlock("husbandry_refiner",
        { props -> RefinerBlock(props, SkillKind.HUSBANDRY) },
        refinerBlockProps,
    )
    val ALCHEMIST_REFINER = BLOCKS.registerBlock("alchemist_refiner",
        { props -> RefinerBlock(props, SkillKind.ALCHEMIST) },
        refinerBlockProps,
    )
    val JEWELERY_REFINER = BLOCKS.registerBlock("jewelery_refiner",
        { props -> RefinerBlock(props, SkillKind.JEWELERY) },
        refinerBlockProps,
    )
    val FISHING_REFINER = BLOCKS.registerBlock("fishing_refiner",
        { props -> RefinerBlock(props, SkillKind.FISHING) },
        refinerBlockProps,
    )

    private val socketingTableProps = BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(2.0f)
        .requiresCorrectToolForDrops()
    val SOCKETING_TABLE = BLOCKS.registerBlock("socketing_table",
        { props -> SocketingTableBlock(props) },
        socketingTableProps,
    )

    val FORESTRY_REFINER_ITEM = ITEMS.registerItem("forestry_refiner") { props ->
        BlockItem(FORESTRY_REFINER.get(), props)
    }
    val MINING_REFINER_ITEM = ITEMS.registerItem("mining_refiner") { props ->
        BlockItem(MINING_REFINER.get(), props)
    }
    val ARMOURER_REFINER_ITEM = ITEMS.registerItem("armourer_refiner") { props ->
        BlockItem(ARMOURER_REFINER.get(), props)
    }
    val HUSBANDRY_REFINER_ITEM = ITEMS.registerItem("husbandry_refiner") { props ->
        BlockItem(HUSBANDRY_REFINER.get(), props)
    }
    val ALCHEMIST_REFINER_ITEM = ITEMS.registerItem("alchemist_refiner") { props ->
        BlockItem(ALCHEMIST_REFINER.get(), props)
    }
    val JEWELERY_REFINER_ITEM = ITEMS.registerItem("jewelery_refiner") { props ->
        BlockItem(JEWELERY_REFINER.get(), props)
    }
    val FISHING_REFINER_ITEM = ITEMS.registerItem("fishing_refiner") { props ->
        BlockItem(FISHING_REFINER.get(), props)
    }
    val SOCKETING_TABLE_ITEM = ITEMS.registerItem("socketing_table") { props ->
        BlockItem(SOCKETING_TABLE.get(), props)
    }

    val ASH_ITEM = ITEMS.registerItem("ash") { props -> AshItem(props) }

    /**
     * Pulverised ore residue produced as a side-output every JEWELERY gem-crack.
     * Goes to MINING refiner as the AMPLIFIER catalyst — chance of +1 ore per
     * smelt-yield. Closes the JEWELERY ↔ MINING loop tightly: mining feeds gems,
     * gems feed mining. (T25 — design 2026-05-03.)
     */
    val ORE_DUST_ITEM = ITEMS.registerItem("ore_dust") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }

    /**
     * Two new plank blocks for Create Aeronautics weight integration. Vanilla
     * planks become medium weight (default mass 1.0 — datapack removes them
     * from sable:light). The two new variants below are tagged sable:light /
     * sable:super_light so they get mass 0.5 and 0.25 respectively. Refining a
     * log at FUELER yields these blocks at MEDIUM / HIGH quality (vanilla
     * planks at UNREFINED / LOW).
     *
     * Generic — no per-wood-type variants for v1. Refined wood loses its
     * source-wood character; trade-off is texture variety vs simplicity.
     */
    private val plankBlockProps = BlockBehaviour.Properties.of()
        .mapColor(MapColor.WOOD)
        .strength(2.0f, 3.0f)
        .sound(net.minecraft.world.level.block.SoundType.WOOD)
        .ignitedByLava()

    val LIGHT_PLANKS_BLOCK = BLOCKS.registerSimpleBlock("light_planks", plankBlockProps)
    val SUPER_LIGHT_PLANKS_BLOCK = BLOCKS.registerSimpleBlock("super_light_planks", plankBlockProps)
    val LIGHT_PLANKS_ITEM = ITEMS.registerItem("light_planks") { props ->
        BlockItem(LIGHT_PLANKS_BLOCK.get(), props)
    }
    val SUPER_LIGHT_PLANKS_ITEM = ITEMS.registerItem("super_light_planks") { props ->
        BlockItem(SUPER_LIGHT_PLANKS_BLOCK.get(), props)
    }

    val FISH_EYE_ITEM = ITEMS.registerItem("fish_eye") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }
    val FISH_SCALE_ITEM = ITEMS.registerItem("fish_scale") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }
    val FISH_OIL_ITEM = ITEMS.registerItem("fish_oil") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }

    // Producer-refiner byproducts (mining/armourer/husbandry). Each is
    // emitted as a small-chance side output during a refine and acts as
    // a catalyst at a downstream consumer (see catalyst_*.json tags).
    val SLAG_ITEM = ITEMS.registerItem("slag") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }
    val FILINGS_ITEM = ITEMS.registerItem("filings") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }
    val TALLOW_ITEM = ITEMS.registerItem("tallow") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }

    // HUNTER refiner byproducts. Vanilla hostile-mob drops fed into the
    // hunter refiner roll one of these four items, partitioned by reagent
    // affinity (fire/structure/organic/rare). Each is wired as the catalyst
    // for one consumer industry — beast_ember → forestry, _carapace →
    // armourer, _hide → husbandry, _essence → jewelery.
    val BEAST_EMBER_ITEM = ITEMS.registerItem("beast_ember") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }
    val BEAST_CARAPACE_ITEM = ITEMS.registerItem("beast_carapace") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }
    val BEAST_HIDE_ITEM = ITEMS.registerItem("beast_hide") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }
    val BEAST_ESSENCE_ITEM = ITEMS.registerItem("beast_essence") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }

    val TOPAZ_ITEM = ITEMS.registerItem("topaz") { props -> GemItem(GemKind.TOPAZ, props) }
    val SAPPHIRE_ITEM = ITEMS.registerItem("sapphire") { props -> GemItem(GemKind.SAPPHIRE, props) }
    val RUBY_ITEM = ITEMS.registerItem("ruby") { props -> GemItem(GemKind.RUBY, props) }
    val EMERALD_GEM_ITEM = ITEMS.registerItem("emerald_gem") { props -> GemItem(GemKind.EMERALD, props) }

    /**
     * Rare jewelery output. 9 shards craft into 1 vanilla diamond (shapeless
     * recipe in `data/caero_specialization/recipe/diamond_from_shards.json`).
     * Drop chance scales with jeweler level × stone-input tier.
     */
    val DIAMOND_SHARD_ITEM = ITEMS.registerItem("diamond_shard") { props ->
        net.minecraft.world.item.Item(props.stacksTo(64))
    }

    fun gemItem(kind: GemKind): net.minecraft.world.item.Item = when (kind) {
        GemKind.TOPAZ -> TOPAZ_ITEM.get()
        GemKind.SAPPHIRE -> SAPPHIRE_ITEM.get()
        GemKind.RUBY -> RUBY_ITEM.get()
        GemKind.EMERALD -> EMERALD_GEM_ITEM.get()
    }

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
    val ARMOURER_REFINER_BE_TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<RefinerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("armourer_refiner") { ->
            @Suppress("DEPRECATION")
            BlockEntityType.Builder.of(
                { pos, state -> RefinerBlockEntity(pos, state, SkillKind.ARMOURER) },
                ARMOURER_REFINER.get(),
            ).build(null)
        }
    val HUSBANDRY_REFINER_BE_TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<RefinerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("husbandry_refiner") { ->
            @Suppress("DEPRECATION")
            BlockEntityType.Builder.of(
                { pos, state -> RefinerBlockEntity(pos, state, SkillKind.HUSBANDRY) },
                HUSBANDRY_REFINER.get(),
            ).build(null)
        }
    val ALCHEMIST_REFINER_BE_TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<RefinerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("alchemist_refiner") { ->
            @Suppress("DEPRECATION")
            BlockEntityType.Builder.of(
                { pos, state -> RefinerBlockEntity(pos, state, SkillKind.ALCHEMIST) },
                ALCHEMIST_REFINER.get(),
            ).build(null)
        }
    val JEWELERY_REFINER_BE_TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<RefinerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("jewelery_refiner") { ->
            @Suppress("DEPRECATION")
            BlockEntityType.Builder.of(
                { pos, state -> RefinerBlockEntity(pos, state, SkillKind.JEWELERY) },
                JEWELERY_REFINER.get(),
            ).build(null)
        }
    val FISHING_REFINER_BE_TYPE: DeferredHolder<BlockEntityType<*>, BlockEntityType<RefinerBlockEntity>> =
        BLOCK_ENTITY_TYPES.register("fishing_refiner") { ->
            @Suppress("DEPRECATION")
            BlockEntityType.Builder.of(
                { pos, state -> RefinerBlockEntity(pos, state, SkillKind.FISHING) },
                FISHING_REFINER.get(),
            ).build(null)
        }

    val QUALITY_SMELTING_SERIALIZER: DeferredHolder<RecipeSerializer<*>, QualitySmeltingRecipe.Serializer> =
        RECIPE_SERIALIZERS.register("quality_smelting") { -> QualitySmeltingRecipe.Serializer }

    val QUALITY_SHAPELESS_SERIALIZER: DeferredHolder<RecipeSerializer<*>, com.caero.specialization.recipe.QualityShapelessRecipe.Serializer> =
        RECIPE_SERIALIZERS.register("quality_shapeless") { -> com.caero.specialization.recipe.QualityShapelessRecipe.Serializer }

    val QUALITY_SHAPED_SERIALIZER: DeferredHolder<RecipeSerializer<*>, com.caero.specialization.recipe.QualityShapedRecipe.Serializer> =
        RECIPE_SERIALIZERS.register("quality_shaped") { -> com.caero.specialization.recipe.QualityShapedRecipe.Serializer }

    @Suppress("unused")
    val CREATIVE_TAB = CREATIVE_TABS.register("main", Supplier {
        CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.caero_specialization"))
            .icon { ItemStack(FORESTRY_REFINER_ITEM.get()) }
            .displayItems { _, output ->
                output.accept(FORESTRY_REFINER_ITEM.get())
                output.accept(MINING_REFINER_ITEM.get())
                output.accept(ARMOURER_REFINER_ITEM.get())
                output.accept(HUSBANDRY_REFINER_ITEM.get())
                output.accept(ALCHEMIST_REFINER_ITEM.get())
                output.accept(JEWELERY_REFINER_ITEM.get())
                output.accept(FISHING_REFINER_ITEM.get())
                output.accept(SOCKETING_TABLE_ITEM.get())
                output.accept(ASH_ITEM.get())
                output.accept(ORE_DUST_ITEM.get())
                output.accept(LIGHT_PLANKS_ITEM.get())
                output.accept(SUPER_LIGHT_PLANKS_ITEM.get())
                output.accept(FISH_EYE_ITEM.get())
                output.accept(FISH_SCALE_ITEM.get())
                output.accept(FISH_OIL_ITEM.get())
                output.accept(SLAG_ITEM.get())
                output.accept(FILINGS_ITEM.get())
                output.accept(TALLOW_ITEM.get())
                output.accept(BEAST_EMBER_ITEM.get())
                output.accept(BEAST_CARAPACE_ITEM.get())
                output.accept(BEAST_HIDE_ITEM.get())
                output.accept(BEAST_ESSENCE_ITEM.get())
                output.accept(TOPAZ_ITEM.get())
                output.accept(SAPPHIRE_ITEM.get())
                output.accept(RUBY_ITEM.get())
                output.accept(EMERALD_GEM_ITEM.get())
                output.accept(DIAMOND_SHARD_ITEM.get())
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
        MENU_TYPES.register(MOD_BUS)
        INGREDIENT_TYPES.register(MOD_BUS)
        QualityComponent.COMPONENTS.register(MOD_BUS)
        GemSocketsRegistry.DATA_COMPONENTS.register(MOD_BUS)
        GemSocketsRegistry.MENU_TYPES.register(MOD_BUS)
        SkillAttachment.ATTACHMENTS.register(MOD_BUS)

        MOD_BUS.addListener(::onCommonSetup)
        MOD_BUS.addListener(::onConfigLoad)
        MOD_BUS.addListener(::onConfigReload)
        if (FMLEnvironment.dist.isClient) {
            MOD_BUS.addListener(::onClientSetup)
            MOD_BUS.addListener(::onRegisterMenuScreens)
            MOD_BUS.addListener(::onRegisterItemColors)
            MOD_BUS.addListener(::onRegisterItemDecorations)
        }

        NeoForge.EVENT_BUS.register(FuelBurnTime)
        NeoForge.EVENT_BUS.register(QualityTooltip)
        NeoForge.EVENT_BUS.register(RefinerInteraction)
        NeoForge.EVENT_BUS.register(AttributeQualityScaler)
        NeoForge.EVENT_BUS.register(DurabilityNerfTicker)
        NeoForge.EVENT_BUS.register(SkillLoginListener)
        NeoForge.EVENT_BUS.register(GemSocketsHandler)
        NeoForge.EVENT_BUS.register(AnvilDisable)
        NeoForge.EVENT_BUS.register(VillagerTradeDisable)
        NeoForge.EVENT_BUS.register(com.caero.specialization.disable.GraveDespawn)
        NeoForge.EVENT_BUS.register(com.caero.specialization.refiner.CatalystTooltip)
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

    private fun onRegisterItemColors(event: net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item) {
        com.caero.specialization.quality.QualityClientProperty.registerColors(event.itemColors)
    }

    private fun onRegisterItemDecorations(event: net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent) {
        com.caero.specialization.quality.QualityClientProperty.registerDecorations(event)
    }

    private fun onRegisterMenuScreens(event: net.neoforged.neoforge.client.event.RegisterMenuScreensEvent) {
        event.register(GemSocketsRegistry.SOCKETING_TABLE_MENU.get(), ::SocketingTableScreen)
        event.register(REFINER_MENU.get()) { menu, inv, title ->
            com.caero.specialization.refiner.RefinerScreen(menu, inv, title)
        }
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        SpecializationCommands.register(event.dispatcher)
    }

    /**
     * NeoForge fires `ModConfigEvent.Loading` once per config when it first loads
     * and `ModConfigEvent.Reloading` every time the toml file is edited on disk.
     * Both push the relevant values into [com.caero.specialization.skill.SkillMath]
     * so the curve coefficient and level cap stay live without a server restart.
     *
     * Per-skill XP and per-refine fees are read at the call site every refine,
     * so they pick up reloads automatically — no event handler needed for those.
     */
    private fun onConfigLoad(event: net.neoforged.fml.event.config.ModConfigEvent.Loading) {
        if (event.config.spec === CaeroSpecializationConfig.SPEC) syncSkillMathFromConfig("loaded")
    }

    private fun onConfigReload(event: net.neoforged.fml.event.config.ModConfigEvent.Reloading) {
        if (event.config.spec === CaeroSpecializationConfig.SPEC) syncSkillMathFromConfig("reloaded")
    }

    private fun syncSkillMathFromConfig(reason: String) {
        com.caero.specialization.skill.SkillMath.xpCurveCoefficient =
            CaeroSpecializationConfig.XP_CURVE_COEFFICIENT.get().toLong()
        com.caero.specialization.skill.SkillMath.maxLevelCap =
            CaeroSpecializationConfig.MAX_SKILL_LEVEL.get()
        for (skill in com.caero.specialization.skill.SkillKind.values()) {
            val raw = CaeroSpecializationConfig.xpWeightsFor(skill).get()
            // ConfigValue surface is List<? extends String>; safe cast since
            // the spec validator only accepts String entries.
            @Suppress("UNCHECKED_CAST")
            val parsed = com.caero.specialization.skill.XpWeights.parseList(skill, raw as List<String>)
            com.caero.specialization.skill.XpWeights.setWeights(skill, parsed)
        }
        LOG.info(
            "Config $reason — xpCurveCoefficient={}, maxLevelCap={}, xpWeights reloaded for all skills",
            com.caero.specialization.skill.SkillMath.xpCurveCoefficient,
            com.caero.specialization.skill.SkillMath.maxLevelCap,
        )
    }
}

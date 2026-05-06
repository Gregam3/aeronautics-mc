package com.caero.auction

import com.caero.auction.block.AuctionHouseBlock
import com.caero.auction.block.AuctionHouseBlockEntity
import com.caero.auction.client.AuctionHouseScreen
import com.caero.auction.command.AuctionCommands
import com.caero.auction.menu.AuctionHouseMenu
import com.caero.auction.net.AuctionNet
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.network.IContainerFactory
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.registries.DeferredRegister
import org.slf4j.LoggerFactory
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.function.Supplier

@Mod(CaeroAuction.MOD_ID)
object CaeroAuction {
    const val MOD_ID = "caero_auction"
    val LOG = LoggerFactory.getLogger(MOD_ID)!!

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    val BLOCKS = DeferredRegister.createBlocks(MOD_ID)
    val ITEMS = DeferredRegister.createItems(MOD_ID)
    val BLOCK_ENTITIES: DeferredRegister<BlockEntityType<*>> =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID)
    val MENU_TYPES: DeferredRegister<MenuType<*>> =
        DeferredRegister.create(Registries.MENU, MOD_ID)
    val CREATIVE_TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

    val AUCTION_HOUSE_BLOCK = BLOCKS.registerBlock(
        "auction_house",
        ::AuctionHouseBlock,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.GOLD)
            .strength(3.0f, 8.0f)
            .sound(SoundType.WOOD)
            .requiresCorrectToolForDrops(),
    )

    val AUCTION_HOUSE_ITEM = ITEMS.registerSimpleBlockItem(AUCTION_HOUSE_BLOCK)

    @Suppress("UNCHECKED_CAST")
    val AUCTION_HOUSE_BE: Supplier<BlockEntityType<AuctionHouseBlockEntity>> =
        BLOCK_ENTITIES.register("auction_house", Supplier {
            BlockEntityType.Builder
                .of(::AuctionHouseBlockEntity, AUCTION_HOUSE_BLOCK.get())
                .build(null) as BlockEntityType<AuctionHouseBlockEntity>
        })

    val AUCTION_HOUSE_MENU: Supplier<MenuType<AuctionHouseMenu>> =
        MENU_TYPES.register("auction_house", Supplier {
            val factory = IContainerFactory<AuctionHouseMenu> { id, inv, buf ->
                AuctionHouseMenu.clientSide(id, inv, buf)
            }
            net.neoforged.neoforge.common.extensions.IMenuTypeExtension.create(factory)
        })

    @Suppress("unused")
    val CREATIVE_TAB = CREATIVE_TABS.register("main", Supplier {
        CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.caero_auction"))
            .icon { ItemStack(AUCTION_HOUSE_ITEM.get()) }
            .displayItems { _, output ->
                output.accept(AUCTION_HOUSE_ITEM.get())
            }
            .build()
    })

    init {
        LOG.info("caero_auction init starting")

        BLOCKS.register(MOD_BUS)
        ITEMS.register(MOD_BUS)
        BLOCK_ENTITIES.register(MOD_BUS)
        MENU_TYPES.register(MOD_BUS)
        CREATIVE_TABS.register(MOD_BUS)

        MOD_BUS.addListener(::onCommonSetup)
        MOD_BUS.addListener(::onPayloadHandlers)
        if (FMLEnvironment.dist.isClient) {
            MOD_BUS.addListener(::onRegisterMenuScreens)
        }

        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        AuctionCommands.register(event.dispatcher)
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOG.info("caero_auction common setup")
    }

    private fun onRegisterMenuScreens(event: RegisterMenuScreensEvent) {
        event.register(AUCTION_HOUSE_MENU.get(), ::AuctionHouseScreen)
    }

    private fun onPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        AuctionNet.register(event)
    }
}

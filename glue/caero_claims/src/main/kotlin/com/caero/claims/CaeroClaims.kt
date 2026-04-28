package com.caero.claims

import com.caero.claims.client.ClaimRenderer
import com.caero.claims.client.ClaimWandClientHandler
import com.caero.claims.client.ClientClaimStore
import com.caero.claims.command.ClaimCommands
import com.caero.claims.config.CaeroClaimsConfig
import com.caero.claims.net.ClaimNet
import com.caero.claims.protect.V1ProtectionHandlers
import com.caero.claims.service.ServerClaimService
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.neoforged.fml.ModList
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.registries.DeferredRegister
import org.slf4j.LoggerFactory
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import java.util.function.Supplier

@Mod(CaeroClaims.MOD_ID)
object CaeroClaims {
    const val MOD_ID = "caero_claims"
    val LOG = LoggerFactory.getLogger(MOD_ID)!!

    fun id(path: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, path)

    val ITEMS: DeferredRegister.Items = DeferredRegister.createItems(MOD_ID)
    val CREATIVE_TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

    val CLAIM_WAND = ITEMS.registerItem("claim_wand") { props ->
        ClaimWandItem(props.stacksTo(1))
    }

    @Suppress("unused")
    val CREATIVE_TAB = CREATIVE_TABS.register("main", Supplier {
        CreativeModeTab.builder()
            .title(net.minecraft.network.chat.Component.translatable("itemGroup.caero_claims"))
            .icon { ItemStack(CLAIM_WAND.get()) }
            .displayItems { _, output ->
                output.accept(CLAIM_WAND.get())
            }
            .build()
    })

    init {
        LOG.info("caero_claims init starting")

        ModList.get().getModContainerById(MOD_ID).orElseThrow()
            .registerConfig(ModConfig.Type.COMMON, CaeroClaimsConfig.SPEC)

        ITEMS.register(MOD_BUS)
        CREATIVE_TABS.register(MOD_BUS)

        MOD_BUS.addListener(::onCommonSetup)
        MOD_BUS.addListener(::onPayloadHandlers)
        if (FMLEnvironment.dist.isClient) {
            MOD_BUS.addListener(::onClientSetup)
        }

        // Server-side bus subscriptions.
        NeoForge.EVENT_BUS.register(V1ProtectionHandlers)
        NeoForge.EVENT_BUS.addListener(::onRegisterCommands)
        NeoForge.EVENT_BUS.addListener(::onPlayerJoin)
        NeoForge.EVENT_BUS.addListener(::onPlayerLogout)
        NeoForge.EVENT_BUS.addListener(::onPlayerChangedDimension)
    }

    private fun onCommonSetup(event: FMLCommonSetupEvent) {
        LOG.info("caero_claims common setup")
    }

    private fun onClientSetup(event: FMLClientSetupEvent) {
        LOG.info("caero_claims client setup")
        NeoForge.EVENT_BUS.register(ClaimWandClientHandler)
        NeoForge.EVENT_BUS.register(ClientClaimStore)
        NeoForge.EVENT_BUS.register(ClaimRenderer)
    }

    private fun onPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        ClaimNet.register(event)
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        ClaimCommands.register(event.dispatcher)
    }

    private fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        ServerClaimService.onPlayerJoin(event.entity)
    }

    private fun onPlayerLogout(event: PlayerEvent.PlayerLoggedOutEvent) {
        ServerClaimService.onPlayerLogout(event.entity)
    }

    private fun onPlayerChangedDimension(event: PlayerEvent.PlayerChangedDimensionEvent) {
        ServerClaimService.onPlayerChangedDimension(event.entity)
    }
}

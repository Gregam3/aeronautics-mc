package com.example.createstockexchange.registry;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.block.BusinessVendorBlockEntity;
import com.example.createstockexchange.block.CompanyDeskBlockEntity;
import com.example.createstockexchange.block.TradePostBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CSEBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateStockExchange.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BusinessVendorBlockEntity>> BUSINESS_VENDOR =
            BLOCK_ENTITIES.register("business_vendor", () ->
                    BlockEntityType.Builder.of(BusinessVendorBlockEntity::new,
                            CSEBlocks.BUSINESS_VENDOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CompanyDeskBlockEntity>> COMPANY_DESK =
            BLOCK_ENTITIES.register("company_desk", () ->
                    BlockEntityType.Builder.of(CompanyDeskBlockEntity::new,
                            CSEBlocks.COMPANY_DESK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TradePostBlockEntity>> TRADE_POST =
            BLOCK_ENTITIES.register("trade_post", () ->
                    BlockEntityType.Builder.of(TradePostBlockEntity::new,
                            CSEBlocks.TRADE_POST.get()).build(null));
}

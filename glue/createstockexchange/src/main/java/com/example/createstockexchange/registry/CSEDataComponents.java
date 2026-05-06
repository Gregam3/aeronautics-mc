package com.example.createstockexchange.registry;

import com.example.createstockexchange.CreateStockExchange;
import com.example.createstockexchange.component.StockCertificateData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CSEDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CreateStockExchange.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StockCertificateData>> STOCK_CERTIFICATE_DATA =
            DATA_COMPONENTS.register("stock_certificate_data", () ->
                    DataComponentType.<StockCertificateData>builder()
                            .persistent(StockCertificateData.CODEC)
                            .networkSynchronized(StockCertificateData.STREAM_CODEC)
                            .build()
            );
}

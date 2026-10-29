package com.valoris.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.valoris.valoris2;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(modid = valoris2.MODID, value = Dist.CLIENT)
public final class ClientModEvents {
    public static final KeyMapping OPEN_GUI =
            new KeyMapping("key.valoris2.open_gui", InputConstants.KEY_K, "key.categories.valoris2");

    private ClientModEvents() {}

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BearModel.LAYER, BearModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(com.valoris.registry.ModEntities.BEAR.get(), BearRenderer::new);
    }
}

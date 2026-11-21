package com.murong.stcraft;

import com.murong.stcraft.client.CompanionRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = STCraft.MODID, dist = Dist.CLIENT)
public class STCraftClient {

    public STCraftClient(IEventBus modBus) {
        modBus.addListener(STCraftClient::onRenderers);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(STCraft.COMPANION.get(), CompanionRenderer::new);
    }
}

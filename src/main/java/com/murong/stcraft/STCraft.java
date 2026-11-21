package com.murong.stcraft;

import com.murong.stcraft.client.ClientPayloads;
import com.murong.stcraft.entity.CompanionEntity;
import com.murong.stcraft.network.*;
import com.murong.stcraft.tavern.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(STCraft.MODID)
public class STCraft
{
    public static final String MODID = "stcraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CompanionEntity>> COMPANION =
            ENTITIES.register("companion", () ->
                    EntityType.Builder.of(CompanionEntity::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(10)
                            .build(MODID + ":companion"));

    public STCraft(IEventBus modEventBus, ModContainer modContainer)
    {
        modEventBus.addListener(this::commonSetup);
        ENTITIES.register(modEventBus);
        modEventBus.addListener(STCraft::onAttributes);

        modEventBus.addListener((RegisterPayloadHandlersEvent e) -> {
            var r = e.registrar("1");
            r.playToServer(SetSkinPayload.TYPE, SetSkinPayload.CODEC, SetSkinPayload::handle);
            r.playToServer(ChatSendPayload.TYPE, ChatSendPayload.CODEC, ChatService::handleSend);
            r.playToClient(OpenChatPayload.TYPE, OpenChatPayload.CODEC, ClientPayloads::handleOpen);
            r.playToClient(ChatReplyPayload.TYPE, ChatReplyPayload.CODEC, ClientPayloads::handleReply);
            r.playToClient(ChatDeltaPayload.TYPE, ChatDeltaPayload.CODEC, ClientPayloads::handleDelta);
        });

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> {
            TavernCommands.register(e.getDispatcher());
            CharacterCommands.register(e.getDispatcher());
            PersonaCommands.register(e.getDispatcher());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.Clone e) -> {
            String v = e.getOriginal().getPersistentData().getString(PersonaService.KEY);
            if (!v.isEmpty()) e.getEntity().getPersistentData().putString(PersonaService.KEY, v);
        });

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(COMPANION.get(), CompanionEntity.createAttributes().build());
    }

    public static ResourceLocation id(String path)
    {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event)
    {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("HELLO from server starting");
    }
}

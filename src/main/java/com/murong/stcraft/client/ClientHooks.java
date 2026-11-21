package com.murong.stcraft.client;

import com.murong.stcraft.entity.CompanionEntity;
import net.minecraft.client.Minecraft;

public final class ClientHooks {
    private ClientHooks() {}

    public static void openSkinScreen(CompanionEntity e) {
        Minecraft.getInstance().setScreen(new CompanionSkinScreen(e));
    }
}
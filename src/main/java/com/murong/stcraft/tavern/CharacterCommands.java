package com.murong.stcraft.tavern;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.murong.stcraft.STCraft;
import com.murong.stcraft.entity.CompanionEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class CharacterCommands {
    private CharacterCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("character").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("summon")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> summon(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"))))));
    }

    private static int list(CommandSourceStack src) {
        TavernCommands.CLIENT.listCharacters().whenComplete((list, err) -> src.getServer().execute(() -> {
            if (err != null) { TavernCommands.fail(src, err); return; }
            String names = String.join(", ", list.stream().map(TavernCharacter::name).toList());
            src.sendSystemMessage(Component.translatable("stcraft.command.character.list", list.size(), names));
        }));
        return 1;
    }

    private static int summon(CommandSourceStack src, String name) {
        String wanted = name.trim();
        Vec3 pos = src.getPosition();
        float yaw = src.getRotation().y;
        TavernCommands.CLIENT.listCharacters().whenComplete((list, err) -> src.getServer().execute(() -> {
            if (err != null) { TavernCommands.fail(src, err); return; }
            var ch = list.stream().filter(c -> c.name().equalsIgnoreCase(wanted)).findFirst();
            if (ch.isEmpty()) {
                src.sendFailure(Component.translatable("stcraft.command.character.not_found", wanted));
                return;
            }
            var level = src.getLevel();
            CompanionEntity e = STCraft.COMPANION.get().create(level);
            e.moveTo(pos.x, pos.y, pos.z, yaw, 0);
            e.bind(ch.get().avatar(), ch.get().name());
            level.addFreshEntity(e);
        }));
        return 1;
    }
}
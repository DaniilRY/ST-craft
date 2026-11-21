package com.murong.stcraft.tavern;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.murong.stcraft.STCraft;
import com.murong.stcraft.entity.CompanionEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class TavernCommands
{
    public static final TavernClient CLIENT = new TavernClient();

    public static void register(CommandDispatcher<CommandSourceStack> d)
    {
        LiteralCommandNode<CommandSourceStack> register = d.register(Commands.literal("tavern").requires(s -> s.hasPermission(2))
                .then(Commands.literal("ping").executes(ctx -> {
                    var src = ctx.getSource();
                    CLIENT.ping().whenComplete((ok, err) -> src.getServer().execute(() ->
                            src.sendSystemMessage(err == null
                                    ? Component.literal("Таверна на связи")
                                    : Component.literal("Ошибка: " + root(err).getMessage()))));
                    return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    var src = ctx.getSource();
                    CLIENT.listCharacters().whenComplete((list, err) -> src.getServer().execute(() ->
                            src.sendSystemMessage(err == null
                                    ? Component.literal("Персонажи (" + list.size() + "): "
                                    + String.join(", ", list.stream().map(TavernCharacter::name).toList()))
                                    : Component.literal("Ошибка: " + root(err).getMessage()))));
                    return 1;
                }))
                .then(Commands.literal("summon")
                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
                            var src = ctx.getSource();
                            String wanted = StringArgumentType.getString(ctx, "name");
                            Vec3 pos = src.getPosition();
                            CLIENT.listCharacters().whenComplete((list, err) -> src.getServer().execute(() -> {
                                if (err != null) {
                                    src.sendSystemMessage(Component.literal("Ошибка: " + root(err).getMessage()));
                                    return;
                                }
                                var ch = list.stream().filter(c -> c.name().equalsIgnoreCase(wanted)).findFirst();
                                if (ch.isEmpty()) {
                                    src.sendSystemMessage(Component.literal("Нет такого персонажа: " + wanted));
                                    return;
                                }

                                var level = src.getLevel();
                                CompanionEntity e = STCraft.COMPANION.get().create(level);
                                e.moveTo(pos.x, pos.y, pos.z, src.getRotation().y, 0);
                                e.bind(ch.get().avatar(), ch.get().name());
                                level.addFreshEntity(e);
                            }));
                            return 1;
                        })))
        );
    }

    private static Throwable root(Throwable t)
    {
        while (t.getCause() != null) t = t.getCause();
        return t;
    }
}
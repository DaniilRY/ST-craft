package com.murong.stcraft.tavern;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PersonaCommands {
    private PersonaCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("persona")
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(ctx -> show(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))
                        .then(Commands.literal("reset")
                                .executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> set(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player"),
                                                StringArgumentType.getString(ctx, "name")))))));
    }

    /** Себе может менять любой игрок, другому только оператор. */
    private static boolean allowed(CommandSourceStack src, ServerPlayer target) {
        ServerPlayer self = src.getPlayer();
        boolean own = self != null && self.getUUID().equals(target.getUUID());
        if (own || src.hasPermission(2)) return true;
        src.sendFailure(Component.translatable("stcraft.command.persona.denied"));
        return false;
    }

    private static int list(CommandSourceStack src) {
        TavernCommands.CLIENT.getSettings().whenComplete((st, err) -> src.getServer().execute(() -> {
            if (err != null) { TavernCommands.fail(src, err); return; }
            String all = String.join(", ", st.personas().stream().map(Persona::name).toList());
            src.sendSystemMessage(Component.translatable("stcraft.command.persona.list", all));
        }));
        return 1;
    }

    private static int show(CommandSourceStack src, ServerPlayer target) {
        String sel = PersonaService.selected(target);
        String nick = target.getGameProfile().getName();
        TavernCommands.CLIENT.getSettings().whenComplete((st, err) -> src.getServer().execute(() -> {
            if (err != null) { TavernCommands.fail(src, err); return; }
            Component current = st.persona(sel).<Component>map(p -> Component.literal(p.name()))
                    .orElseGet(() -> Component.translatable("stcraft.command.persona.nick_suffix", nick));
            src.sendSystemMessage(Component.translatable("stcraft.command.persona.show", nick, current));
        }));
        return 1;
    }

    private static int set(CommandSourceStack src, ServerPlayer target, String name) {
        if (!allowed(src, target)) return 0;
        String wanted = name.trim();
        String nick = target.getGameProfile().getName();
        TavernCommands.CLIENT.getSettings().whenComplete((st, err) -> src.getServer().execute(() -> {
            if (err != null) { TavernCommands.fail(src, err); return; }
            var found = st.personas().stream().filter(p -> p.name().equalsIgnoreCase(wanted)).findFirst();
            if (found.isEmpty()) {
                src.sendFailure(Component.translatable("stcraft.command.persona.not_found", wanted));
                return;
            }
            target.getPersistentData().putString(PersonaService.KEY, found.get().avatar());
            src.sendSuccess(() -> Component.translatable("stcraft.command.persona.set",
                    nick, found.get().name()), false);
        }));
        return 1;
    }

    private static int reset(CommandSourceStack src, ServerPlayer target) {
        if (!allowed(src, target)) return 0;
        target.getPersistentData().remove(PersonaService.KEY);
        String nick = target.getGameProfile().getName();
        src.sendSuccess(() -> Component.translatable("stcraft.command.persona.reset", nick), false);
        return 1;
    }
}
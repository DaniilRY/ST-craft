package com.murong.stcraft.tavern;

import com.mojang.brigadier.CommandDispatcher;
import com.murong.stcraft.Tuning;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class TavernCommands {
    private TavernCommands() {}

    public static final TavernClient CLIENT = new TavernClient();

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("tavern").requires(s -> s.hasPermission(2))
                .then(Commands.literal("ping").executes(ctx -> {
                    var src = ctx.getSource();
                    CLIENT.ping().whenComplete((ok, err) -> src.getServer().execute(() -> {
                        if (err == null) {
                            src.sendSuccess(() -> Component.translatable("stcraft.command.ping.ok",
                                    Tuning.TAVERN_URL).withStyle(ChatFormatting.GREEN), false);
                        } else {
                            src.sendFailure(Component.translatable("stcraft.command.ping.fail",
                                            Tuning.TAVERN_URL, TavernException.of(err).component())
                                    .withStyle(ChatFormatting.RED));
                        }
                    }));
                    return 1;
                })));
    }

    static void fail(CommandSourceStack src, Throwable t) {
        src.sendFailure(Component.translatable("stcraft.chat.error", TavernException.of(t).component()));
    }
}
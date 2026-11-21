package com.murong.stcraft.tavern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.murong.stcraft.Config;
import com.murong.stcraft.Tuning;
import com.murong.stcraft.entity.CompanionEntity;
import com.murong.stcraft.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ChatService {
    private ChatService() {}

    private record Ctx(TavernCharacter ch, TavernClient.Settings st) {}

    private static CompletableFuture<Ctx> load(String avatar) {
        TavernClient t = TavernCommands.CLIENT;
        return t.getSettings().thenCompose(st -> t.getCharacter(avatar).thenApply(ch -> new Ctx(ch, st)));
    }

    public static void open(ServerPlayer sp, CompanionEntity c) {
        UUID id = sp.getUUID();
        String sel = PersonaService.selected(sp);
        String nick = sp.getGameProfile().getName();
        load(c.getAvatar()).whenComplete((ctx, err) -> sp.getServer().execute(() -> {
            Persona pe = err == null ? PersonaService.pick(ctx.st(), sel, nick) : new Persona("", nick, "");
            if (err == null && c.history(id).isEmpty() && !ctx.ch().firstMes().isBlank()) {
                c.addHistory(id, new ChatLine(ChatLine.COMPANION,
                        mac(ctx.ch().firstMes(), ctx.ch().name(), pe.name())));
            }
            PacketDistributor.sendToPlayer(sp, new OpenChatPayload(
                    c.getId(), c.getName().getString(), pe.name(), List.copyOf(c.history(id))));
            if (err != null) tell(sp, c, errorLine(err));
        }));
    }

    public static void handleSend(ChatSendPayload p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!(sp.level().getEntity(p.entityId()) instanceof CompanionEntity c)
                    || c.distanceToSqr(sp) > 64) return;
            String text = p.text().trim();
            if (text.isEmpty() || text.length() > Tuning.CHAT_INPUT_MAX || c.isBusy()) return;

            UUID id = sp.getUUID();
            String sel = PersonaService.selected(sp);
            String nick = sp.getGameProfile().getName();
            boolean stream = Config.STREAMING.get();

            ChatLine mine = new ChatLine(ChatLine.PLAYER, text);
            c.addHistory(id, mine);
            c.setBusy(true);
            List<ChatLine> snapshot = List.copyOf(c.history(id));

            StringBuffer partial = new StringBuffer();
            Consumer<String> onDelta = stream ? d -> {
                partial.append(d);
                sp.getServer().execute(() ->
                        PacketDistributor.sendToPlayer(sp, new ChatDeltaPayload(c.getId(), d)));
            } : null;

            load(c.getAvatar())
                    .thenCompose(x -> {
                        Persona pe = PersonaService.pick(x.st(), sel, nick);
                        return TavernCommands.CLIENT.generate(buildMessages(x.ch(), x.st(), pe, snapshot),
                                x.ch().name(), pe.name(), x.st(), stream, onDelta);
                    })
                    .whenComplete((reply, err) -> sp.getServer().execute(() -> {
                        c.setBusy(false);
                        String out = err == null ? reply.trim() : partial.toString().trim();
                        if (!out.isEmpty()) {
                            c.addHistory(id, new ChatLine(ChatLine.COMPANION, out));
                            tell(sp, c, new ChatLine(ChatLine.COMPANION, out));
                        } else {
                            List<ChatLine> h = c.history(id);
                            if (!h.isEmpty() && h.get(h.size() - 1) == mine) h.remove(h.size() - 1);
                        }
                        if (err != null) tell(sp, c, errorLine(err));
                        else if (out.isEmpty()) tell(sp, c, new ChatLine(ChatLine.ERROR, "stcraft.chat.empty_reply"));
                    }));
        });
    }

    private static ChatLine errorLine(Throwable t) {
        TavernException te = TavernException.of(t);
        return new ChatLine(ChatLine.ERROR, te.key(), te.args());
    }

    private static JsonArray buildMessages(TavernCharacter ch, TavernClient.Settings st,
                                           Persona pe, List<ChatLine> history) {
        String user = pe.name();
        JsonArray arr = new JsonArray();
        arr.add(msg("system", mac(st.mainPrompt(), ch.name(), user)));

        StringBuilder card = new StringBuilder();
        if (!ch.description().isBlank()) card.append(ch.description()).append('\n');
        if (!ch.personality().isBlank()) card.append("{{char}}'s personality: ").append(ch.personality()).append('\n');
        if (!ch.scenario().isBlank()) card.append("Scenario: ").append(ch.scenario()).append('\n');
        if (!pe.description().isBlank()) card.append(pe.description()).append('\n');
        if (!card.isEmpty()) arr.add(msg("system", mac(card.toString().trim(), ch.name(), user)));

        arr.add(msg("system", mac(Tuning.CHAT_HINT, ch.name(), user)));
        arr.add(msg("system", st.newChatPrompt()));

        int from = Math.max(0, history.size() - Tuning.PROMPT_HISTORY);
        for (ChatLine l : history.subList(from, history.size())) {
            if (l.role() != ChatLine.PLAYER && l.role() != ChatLine.COMPANION) continue;
            arr.add(msg(l.role() == ChatLine.PLAYER ? "user" : "assistant", l.text()));
        }
        return arr;
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static String mac(String s, String ch, String user) {
        return s.replace("{{char}}", ch).replace("{{user}}", user);
    }

    private static void tell(ServerPlayer sp, CompanionEntity c, ChatLine line) {
        PacketDistributor.sendToPlayer(sp, new ChatReplyPayload(c.getId(), line));
    }
}
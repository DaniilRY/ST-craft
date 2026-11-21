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

import java.util.regex.Pattern;

public final class ChatService
{
    private ChatService() {}

    private static final Pattern STOP = Pattern.compile("(?iu)(?<!\\p{L})(стой|стоять|останься|оставайся|жди|подожди|ждать|stay|stop|wait)(?!\\p{L})");

    private record Ctx(TavernCharacter ch, TavernClient.Settings st) {}

    private static CompletableFuture<Ctx> load(String avatar)
    {
        TavernClient t = TavernCommands.CLIENT;
        return t.getSettings().thenCompose(st -> t.getCharacter(avatar).thenApply(ch -> new Ctx(ch, st)));
    }

    private static void note(ServerPlayer sp, CompanionEntity c, ChatLine line)
    {
        c.addHistory(sp.getUUID(), line);
        tell(sp, c, line);
    }

    public static void open(ServerPlayer sp, CompanionEntity c)
    {
        UUID id = sp.getUUID();
        String sel = PersonaService.selected(sp);
        String nick = sp.getGameProfile().getName();
        load(c.getAvatar()).whenComplete((ctx, err) -> sp.getServer().execute(() -> {
            Persona pe = err == null ? PersonaService.pick(ctx.st(), sel, nick) : new Persona("", nick, "");
            if (err == null && c.history(id).isEmpty() && !ctx.ch().firstMes().isBlank())
            {
                c.addHistory(id, new ChatLine(ChatLine.COMPANION,
                        mac(ctx.ch().firstMes(), ctx.ch().name(), pe.name())));
            }
            PacketDistributor.sendToPlayer(sp, new OpenChatPayload(c.getId(), c.getName().getString(), pe.name(), List.copyOf(c.history(id))));
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

            if (c.isFollowing() && STOP.matcher(text).find())
            {
                c.setFollowTarget(null);
                note(sp, c, new ChatLine(ChatLine.SYSTEM, "stcraft.chat.now_staying", List.of(c.getName().getString())));
            }

            boolean following = c.isFollowing();
            List<ChatLine> snapshot = List.copyOf(c.history(id));

            StringBuffer raw = new StringBuffer();
            MarkerFilter live = new MarkerFilter();
            Consumer<String> onDelta = stream ? d -> {
                raw.append(d);
                String safe = live.accept(d);
                if (!safe.isEmpty()) {
                    sp.getServer().execute(() ->
                            PacketDistributor.sendToPlayer(sp, new ChatDeltaPayload(c.getId(), safe)));
                }
            } : null;

            load(c.getAvatar())
                    .thenCompose(x -> {
                        Persona pe = PersonaService.pick(x.st(), sel, nick);
                        return TavernCommands.CLIENT.generate(
                                buildMessages(x.ch(), x.st(), pe, snapshot, following),
                                x.ch().name(), pe.name(), x.st(), stream, onDelta);
                    })
                    .whenComplete((reply, err) -> sp.getServer().execute(() -> {
                        c.setBusy(false);
                        MarkerFilter f = new MarkerFilter();
                        String out = ((f.accept(err == null ? reply : raw.toString())) + f.finish()).trim();
                        com.murong.stcraft.STCraft.LOGGER.info("Reply raw: {}", err == null ? reply : raw.toString());
                        if (!out.isEmpty()) {
                            c.addHistory(id, new ChatLine(ChatLine.COMPANION, out));
                            tell(sp, c, new ChatLine(ChatLine.COMPANION, out));
                        } else {
                            //List<ChatLine> h = c.history(id);
                            //if (!h.isEmpty() && h.get(h.size() - 1) == mine) h.remove(h.size() - 1);
                            c.history(id).removeIf(x -> x == mine);
                        }
                        if (err != null) tell(sp, c, errorLine(err));
                        else if (out.isEmpty() && f.marker() == null) {
                            tell(sp, c, new ChatLine(ChatLine.ERROR, "stcraft.chat.empty_reply"));
                        }
                        applyMarker(sp, c, f.marker());
                    }));
        });
    }

    private static void applyMarker(ServerPlayer sp, CompanionEntity c, String marker)
    {
        if (marker == null) return;
        List<String> name = List.of(c.getName().getString());
        if (marker.equals("[FOLLOW]") && !sp.getUUID().equals(c.getFollowTarget()))
        {
            c.setFollowTarget(sp.getUUID());
            note(sp, c, new ChatLine(ChatLine.SYSTEM, "stcraft.chat.now_following", name));
        } else if (marker.equals("[STAY]") && c.isFollowing())
        {
            c.setFollowTarget(null);
            note(sp, c, new ChatLine(ChatLine.SYSTEM, "stcraft.chat.now_staying", name));
        }
    }

    private static ChatLine errorLine(Throwable t)
    {
        TavernException te = TavernException.of(t);
        return new ChatLine(ChatLine.ERROR, te.key(), te.args());
    }

    private static JsonArray buildMessages(TavernCharacter ch, TavernClient.Settings st, Persona pe, List<ChatLine> history, boolean following)
    {
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
        arr.add(msg("system", mac(Tuning.MARKER_HINT + " " + (following ? Tuning.STATE_FOLLOWING : Tuning.STATE_STAYING), ch.name(), user)));
        arr.add(msg("system", st.newChatPrompt()));

        List<ChatLine> talk = history.stream().filter(l -> l.role() == ChatLine.PLAYER || l.role() == ChatLine.COMPANION).toList();
        for (ChatLine l : talk.subList(Math.max(0, talk.size() - Tuning.PROMPT_HISTORY), talk.size()))
        {
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
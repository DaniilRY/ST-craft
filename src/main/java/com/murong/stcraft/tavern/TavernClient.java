package com.murong.stcraft.tavern;

import com.google.gson.*;
import com.murong.stcraft.Tuning;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public class TavernClient {
    private static final Gson GSON = new Gson();

    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private volatile String csrf;
    private final Map<String, TavernCharacter> cache = new ConcurrentHashMap<>();

    public record Settings(JsonObject oai, JsonObject power) {
        public List<Persona> personas() {
            List<Persona> list = new ArrayList<>();
            JsonObject descs = obj(power, "persona_descriptions");
            for (var e : obj(power, "personas").entrySet()) {
                list.add(new Persona(e.getKey(), e.getValue().getAsString(),
                        str(obj(descs, e.getKey()), "description")));
            }
            return list;
        }

        public Optional<Persona> persona(String avatar) {
            return personas().stream().filter(p -> p.avatar().equals(avatar)).findFirst();
        }

        public String mainPrompt() {
            JsonElement ps = oai.get("prompts");
            if (ps != null && ps.isJsonArray()) {
                for (JsonElement e : ps.getAsJsonArray()) {
                    JsonObject p = e.getAsJsonObject();
                    if ("main".equals(str(p, "identifier")) && !str(p, "content").isBlank()) {
                        return str(p, "content");
                    }
                }
            }
            return "Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.";
        }

        public String newChatPrompt() {
            String s = str(oai, "new_chat_prompt");
            return s.isBlank() ? "[Start a new Chat]" : s;
        }
    }

    // ---------- соединение ----------

    private CompletableFuture<Void> ensureToken() {
        if (csrf != null) return CompletableFuture.completedFuture(null);
        HttpRequest req = HttpRequest.newBuilder(URI.create(Tuning.TAVERN_URL + "/csrf-token"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        return http.sendAsync(req, HttpResponse.BodyHandlers.ofString()).thenAccept(r -> {
            if (r.statusCode() != 200) throw new CompletionException(
                    new IllegalStateException("csrf-token: HTTP " + r.statusCode()));
            csrf = JsonParser.parseString(r.body()).getAsJsonObject().get("token").getAsString();
        });
    }

    public CompletableFuture<JsonElement> post(String path, JsonElement body) {
        return ensureToken().thenCompose(v -> {
            HttpRequest req = HttpRequest.newBuilder(URI.create(Tuning.TAVERN_URL + path))
                    .timeout(Duration.ofSeconds(Tuning.TAVERN_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .header("x-csrf-token", csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
            return http.sendAsync(req, HttpResponse.BodyHandlers.ofString());
        }).thenApply(r -> {
            if (r.statusCode() == 403) csrf = null;
            if (r.statusCode() / 100 != 2) throw new CompletionException(
                    new IllegalStateException("HTTP " + r.statusCode() + ": " + trim(r.body())));
            return r.body().isBlank() ? JsonNull.INSTANCE : JsonParser.parseString(r.body());
        });
    }

    public CompletableFuture<Boolean> ping() {
        csrf = null;
        return ensureToken().thenApply(v -> true);
    }

    // ---------- персонажи ----------

    public CompletableFuture<List<TavernCharacter>> listCharacters() {
        return post("/api/characters/all", new JsonObject()).thenApply(json -> {
            List<TavernCharacter> list = new ArrayList<>();
            cache.clear();
            for (JsonElement e : json.getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                TavernCharacter c = new TavernCharacter(
                        str(o, "name"), str(o, "avatar"), str(o, "description"),
                        str(o, "personality"), str(o, "scenario"), str(o, "first_mes"));
                list.add(c);
                cache.put(c.avatar(), c);
            }
            return list;
        });
    }

    /** Из кэша; кэш обновляется командой /tavern list. */
    public CompletableFuture<TavernCharacter> getCharacter(String avatar) {
        TavernCharacter c = cache.get(avatar);
        if (c != null) return CompletableFuture.completedFuture(c);
        return listCharacters().thenApply(list -> {
            TavernCharacter f = cache.get(avatar);
            if (f == null) throw new CompletionException(
                    new IllegalStateException("Карточка не найдена: " + avatar));
            return f;
        });
    }

    // ---------- настройки и генерация ----------

    public CompletableFuture<Settings> getSettings() {
        return post("/api/settings/get", new JsonObject()).thenApply(json -> {
            JsonObject res = json.getAsJsonObject();
            JsonElement s = res.get("settings");
            if (s == null) throw new IllegalStateException(
                    "settings/get: нет поля settings, есть: " + res.keySet());
            JsonObject root = s.isJsonPrimitive()
                    ? JsonParser.parseString(s.getAsString()).getAsJsonObject()
                    : s.getAsJsonObject();
            return new Settings(obj(root, "oai_settings"), obj(root, "power_user"));
        });
    }

    public CompletableFuture<String> generate(JsonArray messages, String charName, String userName, Settings st) {
        JsonObject oai = st.oai();
        String source = str(oai, "chat_completion_source");
        String model = str(oai, source.equals("makersuite") ? "google_model" : source + "_model");
        if (source.isBlank() || model.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "В Таверне не выбран источник или модель (Chat Completion)"));
        }

        JsonObject b = new JsonObject();
        b.addProperty("type", "normal");
        b.add("messages", messages);
        b.addProperty("model", model);
        b.addProperty("temperature", num(oai, "temp_openai", 1));
        b.addProperty("frequency_penalty", num(oai, "freq_pen_openai", 0));
        b.addProperty("presence_penalty", num(oai, "pres_pen_openai", 0));
        b.addProperty("top_p", num(oai, "top_p_openai", 1));
        b.addProperty("top_k", (int) num(oai, "top_k_openai", 0));
        b.addProperty("max_tokens", (int) num(oai, "openai_max_tokens", 300));
        b.addProperty("stream", false);
        b.addProperty("chat_completion_source", source);
        b.addProperty("user_name", userName);
        b.addProperty("char_name", charName);
        b.add("group_names", new JsonArray());
        b.addProperty("include_reasoning", oai.has("show_thoughts") && oai.get("show_thoughts").getAsBoolean());
        //b.addProperty("reasoning_effort", str(oai, "reasoning_effort"));
        String effort = effort(source, str(oai, "reasoning_effort"));
        if (!effort.isEmpty()) b.addProperty("reasoning_effort", effort);
        b.addProperty("enable_web_search", false);
        b.addProperty("request_images", false);
        b.addProperty("custom_prompt_post_processing", str(oai, "custom_prompt_post_processing"));
        b.add("stop", new JsonArray());
        b.addProperty("use_sysprompt", oai.has("use_sysprompt") && oai.get("use_sysprompt").getAsBoolean());
        for (String k : List.of("custom_url", "custom_include_body", "custom_exclude_body",
                "custom_include_headers", "reverse_proxy", "proxy_password")) {
            if (oai.has(k) && oai.get(k).isJsonPrimitive()) b.addProperty(k, oai.get(k).getAsString());
        }
        return post("/api/backends/chat-completions/generate", b)
                .thenApply(TavernClient::extractText)
                .exceptionallyCompose(e -> {
                    // источник отверг запрос с reasoning_effort: повторяем без него
                    String msg = String.valueOf(rootOf(e).getMessage());
                    if (b.has("reasoning_effort") && msg.contains("HTTP ")) {
                        com.murong.stcraft.STCraft.LOGGER.warn("Повтор без reasoning_effort: {}", msg);
                        b.remove("reasoning_effort");
                        return post("/api/backends/chat-completions/generate", b)
                                .thenApply(TavernClient::extractText);
                    }
                    return CompletableFuture.failedFuture(e);
                });
    }

    private static String effort(String source, String v)
    {
        if (v.isBlank() || v.equals("auto")) return "";
        if (source.equals("makersuite")) return v;
        return v.equals("min") ? "minimal" : v;
    }

    private static Throwable rootOf(Throwable t)
    {
        while (t.getCause() != null) t = t.getCause();
        return t;
    }

    private static String extractText(JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        if (o.has("choices")) {
            JsonObject msg = o.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            return msg.get("content").getAsString();
        }
        if (o.has("candidates")) {
            JsonArray parts = o.getAsJsonArray("candidates").get(0).getAsJsonObject()
                    .getAsJsonObject("content").getAsJsonArray("parts");
            StringBuilder sb = new StringBuilder();
            for (JsonElement p : parts) {
                JsonObject po = p.getAsJsonObject();
                boolean thought = po.has("thought") && po.get("thought").getAsBoolean();
                if (!thought && po.has("text")) sb.append(po.get("text").getAsString());
            }
            return sb.toString();
        }
        throw new IllegalStateException("Неожиданный формат ответа: " + trim(json.toString()));
    }

    // ---------- помощники ----------

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        if ((e == null || !e.isJsonPrimitive()) && o.has("data") && o.get("data").isJsonObject()) {
            e = o.getAsJsonObject("data").get(k);
        }
        return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
    }

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    private static double num(JsonObject o, String k, double def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
    }

    private static String trim(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
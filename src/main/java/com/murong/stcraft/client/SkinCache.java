package com.murong.stcraft.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.murong.stcraft.STCraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public final class SkinCache {
    private SkinCache() {}

    //public static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("stcraft").resolve("skins");
    public static final Path DIR = FMLPaths.GAMEDIR.get().resolve("stcraft-skins");

    private static final Map<String, ResourceLocation> READY = new HashMap<>();
    private static final Set<String> TRIED = new HashSet<>();

    public static ResourceLocation get(String name, ResourceLocation fallback) {
        if (name.isEmpty()) return fallback;
        ResourceLocation r = READY.get(name);
        if (r != null) return r;
        if (TRIED.add(name)) load(name);
        return READY.getOrDefault(name, fallback);
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        try {
            Files.createDirectories(DIR);
            try (Stream<Path> s = Files.list(DIR)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.toLowerCase(Locale.ROOT).endsWith(".png"))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(names::add);
            }
        } catch (Exception e) {
            STCraft.LOGGER.warn("Cannot list skins: {}", e.toString());
        }
        return names;
    }

    private static void load(String name) {
        try {
            Path p = DIR.resolve(name).normalize();
            if (!p.startsWith(DIR)) throw new IllegalArgumentException("вне папки скинов");
            NativeImage img;
            try (InputStream in = Files.newInputStream(p)) {
                img = NativeImage.read(in);
            }
            if (img.getWidth() != 64 || img.getHeight() != 64) {
                String size = img.getWidth() + "x" + img.getHeight();
                img.close();
                throw new IllegalStateException("нужен скин 64x64, получен " + size);
            }
            ResourceLocation id = STCraft.id("skin/" + Integer.toHexString(name.hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
            READY.put(name, id);
        } catch (Exception e) {
            STCraft.LOGGER.warn("Skin '{}' failed: {}", name, e.toString());
        }
    }
}
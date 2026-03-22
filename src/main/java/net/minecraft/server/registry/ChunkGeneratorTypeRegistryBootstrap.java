package net.minecraft.server.registry;

import net.minecraft.server.*;
import net.minecraft.server.util.ResourceLocation;

public final class ChunkGeneratorTypeRegistryBootstrap {
    private static boolean initialized = false;

    private ChunkGeneratorTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        // Register known generator/provider classes with simple keys
        register("default", ChunkProviderGenerate.class);
        register("nether", ChunkProviderHell.class);
        register("flat", ChunkProviderFlat.class);
        register("sky", ChunkProviderSky.class);
        // Classic providers
        try {
            Class<?> classicOver = Class.forName("net.minecraft.server.Classic.ChunkProviderClassic");
            register("classic", classicOver);
        } catch (Throwable ignored) {}
        try {
            Class<?> classicHell = Class.forName("net.minecraft.server.Classic.ChunkProviderHellClassic");
            register("classic_nether", classicHell);
        } catch (Throwable ignored) {}
        // Alpha provider
        try {
            Class<?> alpha = Class.forName("net.minecraft.server.Alpha.AlphaChunkProvider");
            register("alpha", alpha);
        } catch (Throwable ignored) {}
        try {
            Class<?> infdev = Class.forName("net.minecraft.server.Infdev.InfdevChunkProvider");
            register("infdev", infdev);
        } catch (Throwable ignored) {}
    }

    private static void register(String path, Class<?> provider) {
        if (provider == null) return;
        ChunkGeneratorTypeRegistryApi.register(new ResourceLocation("minecraft", path), provider);
    }
}

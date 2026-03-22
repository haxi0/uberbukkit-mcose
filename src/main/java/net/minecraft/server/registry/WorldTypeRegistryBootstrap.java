package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class WorldTypeRegistryBootstrap {
    private static boolean initialized = false;

    private WorldTypeRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        // IDs follow server's world type mapping used in MinecraftServer/World
        register("default", 0);
        register("alpha", 1);
        register("flat", 2);
        register("sky", 3);
        register("alpha_snow", 5);
        register("classic", 6);
        register("infdev", 7);
    }

    private static void register(String path, int id) {
        WorldTypeRegistryApi.register(new ResourceLocation("minecraft", path), Integer.valueOf(id));
    }
}

package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

/**
 * Registers default schedules for common entity types.
 */
public final class ScheduleRegistryBootstrap {
    private static boolean initialized = false;

    private ScheduleRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        // Hostile overworld mobs:
        // Creepers/skeletons/zombies should remain combat-active during daytime if alive,
        // while spiders keep their day-passive behavior.
        reg("spider", Schedule.nightOnly());
        reg("zombie", Schedule.always());
        reg("skeleton", Schedule.always());
        reg("creeper", Schedule.always());
        reg("enderman", Schedule.nightOnly());

        // Passive animals: active during day
        reg("pig", Schedule.dayOnly());
        reg("sheep", Schedule.dayOnly());
        reg("cow", Schedule.dayOnly());
        reg("chicken", Schedule.dayOnly());
        reg("squid", Schedule.dayOnly());

        // Nether mobs: always active (no sky)
        reg("ghast", Schedule.always());
        reg("pig_zombie", Schedule.always());

        // Utility entities: always
        reg("snowman", Schedule.always());
        reg("wolf", Schedule.always());
    }

    private static void reg(String path, Schedule schedule) {
        try {
            ResourceLocation key = new ResourceLocation("minecraft", path);
            ScheduleRegistryApi.register(key, schedule);
        } catch (Throwable ignored) {}
    }

    /**
     * Helper used by entity AI to query whether the given entity class should be active now.
     */
    public static boolean isActiveFor(java.lang.Class<?> entityClass, net.minecraft.server.World world) {
        try {
            net.minecraft.server.registry.EntityTypeRegistry.bootstrapFromEntityTypes();
            net.minecraft.server.util.ResourceLocation key = net.minecraft.server.registry.EntityTypeRegistry.getKey(entityClass);
            if (key == null) return true;
            Schedule s = Registries.SCHEDULE.get(key);
            if (s == null) return true;
            return s.isActive(world);
        } catch (Throwable ignored) { return true; }
    }
}

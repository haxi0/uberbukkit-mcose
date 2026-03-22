package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

public final class WorldTypeRegistryApi {
    private static final RegistryAliasIndex ALIASES = new RegistryAliasIndex();

    static {
        ALIASES.addAlias("minecraft:default", "default");
        ALIASES.addAlias("minecraft:default", "normal");
        ALIASES.addAlias("minecraft:alpha", "alpha");
        ALIASES.addAlias("minecraft:flat", "flat");
        ALIASES.addAlias("minecraft:sky", "sky");
        ALIASES.addAlias("minecraft:alpha_snow", "alpha_snow");
        ALIASES.addAlias("minecraft:classic", "classic");
        ALIASES.addAlias("minecraft:infdev", "infdev");
    }

    private WorldTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, Integer value) {
        if (RegistryApiSupport.register(Registries.WORLD_TYPE, key, value)) {
            ALIASES.addAlias(key.toString(), key.toString());
            return true;
        }
        return false;
    }

    public static Integer get(ResourceLocation key) {
        return RegistryApiSupport.get(Registries.WORLD_TYPE, key);
    }

    public static Integer getByIdentifier(String any) {
        ResourceLocation resolved = resolve(any);
        return resolved == null ? null : get(resolved);
    }

    public static ResourceLocation getKey(Integer value) {
        return RegistryApiSupport.getKey(Registries.WORLD_TYPE, value);
    }

    public static Set<ResourceLocation> keys() {
        return RegistryApiSupport.keys(Registries.WORLD_TYPE);
    }

    public static Collection<Integer> values() {
        return RegistryApiSupport.values(Registries.WORLD_TYPE);
    }

    public static int size() {
        return RegistryApiSupport.size(Registries.WORLD_TYPE);
    }

    public static String normalizeInputIdentifier(String any) {
        ResourceLocation key = resolve(any);
        return key == null ? null : key.toString();
    }

    public static String canonicalizeIdentifier(String any) {
        ResourceLocation key = resolve(any);
        if (key == null) {
            return null;
        }

        Integer value = get(key);
        if (value == null) {
            return null;
        }

        ResourceLocation canonical = getKey(value);
        return canonical == null ? null : canonical.toString();
    }

    public static ResourceLocation getByLegacyId(int id) {
        Collection<ResourceLocation> keys = keys();
        for (ResourceLocation key : keys) {
            Integer value = get(key);
            if (value != null && value.intValue() == id) {
                return key;
            }
        }
        return null;
    }

    public static int getLegacyId(String key) {
        Integer value = getByIdentifier(key);
        return value == null ? -1 : value.intValue();
    }

    private static ResourceLocation resolve(String any) {
        ResourceLocation resolved = RegistryApiSupport.resolveIdentifier(Registries.WORLD_TYPE, any);
        if (resolved != null) {
            return resolved;
        }

        String alias = ALIASES.resolve(any);
        if (alias == null) {
            return null;
        }

        return RegistryApiSupport.resolveIdentifier(Registries.WORLD_TYPE, alias);
    }
}

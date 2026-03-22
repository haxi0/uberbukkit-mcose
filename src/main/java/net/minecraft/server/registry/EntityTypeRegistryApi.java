package net.minecraft.server.registry;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityTypeDef;
import net.minecraft.server.Holder;
import net.minecraft.server.World;
import net.minecraft.server.util.ResourceLocation;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Set;

/**
 * Public API facade for entity type registration and entity creation.
 */
public final class EntityTypeRegistryApi {
    private EntityTypeRegistryApi() {}

    public static boolean register(ResourceLocation key, Class<?> value) {
        return EntityTypeRegistry.register(key, value);
    }

    public static boolean register(ResourceLocation key, Class<?> value, int defaultVariant) {
        return EntityTypeRegistry.register(key, value, defaultVariant);
    }

    public static void registerAlias(ResourceLocation alias, Class<?> value) {
        EntityTypeRegistry.registerAlias(alias, value);
    }

    public static Class<?> get(ResourceLocation key) {
        return EntityTypeRegistry.get(key);
    }

    public static Class<?> getByIdentifier(String any) {
        return EntityTypeRegistry.getByIdentifier(any);
    }

    public static ResourceLocation getKey(Class<?> value) {
        return EntityTypeRegistry.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        return EntityTypeRegistry.keys();
    }

    public static Collection<Class<?>> values() {
        return EntityTypeRegistry.values();
    }

    public static int size() {
        return EntityTypeRegistry.size();
    }

    public static String normalizeInputIdentifier(String any) {
        return EntityTypeRegistry.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return EntityTypeRegistry.canonicalizeIdentifier(any);
    }

    public static int getDefaultVariant(String identifier) {
        return EntityTypeRegistry.getDefaultVariant(identifier);
    }

    public static Integer getLegacyId(String identifier) {
        if (identifier == null) {
            return null;
        }
        String normalized = normalizeInputIdentifier(identifier);
        if (normalized == null) {
            return null;
        }
        return EntityTypeRegistry.getLegacyId(new ResourceLocation(normalized));
    }

    public static ResourceLocation getKeyByLegacyId(int legacyId) {
        return EntityTypeRegistry.getKeyByLegacyId(legacyId);
    }

    public static String getLegacyName(ResourceLocation key) {
        return EntityTypeRegistry.getLegacyName(key);
    }

    public static EntityTypeDef<?> getType(ResourceLocation key) {
        return EntityTypeRegistry.getType(key);
    }

    public static Holder<EntityTypeDef<?>> getTypeHolder(ResourceLocation key) {
        return EntityTypeRegistry.getTypeHolder(key);
    }

    public static Holder<EntityTypeDef<?>> getTypeHolder(Class<?> entityClass) {
        return EntityTypeRegistry.getTypeHolder(entityClass);
    }

    public static Holder<EntityTypeDef<?>> getTypeHolder(Entity entity) {
        return EntityTypeRegistry.getTypeHolder(entity);
    }

    public static EntityTypeDef<?> getTypeByRuntimeId(int runtimeId) {
        return EntityTypeRegistry.getTypeByRuntimeId(runtimeId);
    }

    public static Entity createEntity(ResourceLocation key, World world) {
        if (key == null || world == null) {
            return null;
        }

        Class<?> entityClass = get(key);
        if (entityClass == null) {
            return null;
        }

        try {
            Constructor<?> ctor = entityClass.getConstructor(new Class[]{World.class});
            Object out = ctor.newInstance(new Object[]{world});
            return out instanceof Entity ? (Entity) out : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Entity createEntity(String identifier, World world) {
        if (identifier == null || world == null) {
            return null;
        }

        String normalized = normalizeInputIdentifier(identifier);
        if (normalized == null) {
            return null;
        }

        return createEntity(new ResourceLocation(normalized), world);
    }
}

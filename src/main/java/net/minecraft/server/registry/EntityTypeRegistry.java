package net.minecraft.server.registry;

import net.minecraft.server.Entity;
import net.minecraft.server.EntityTypes;
import net.minecraft.server.EntityTypeDef;
import net.minecraft.server.Holder;
import net.minecraft.server.MappedRegistry;
import net.minecraft.server.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server-side registry for entity classes, keyed by namespaced identifiers (e.g., minecraft:creeper).
 */
public final class EntityTypeRegistry {
    private static final Map<ResourceLocation, Class<?>> byKey = new LinkedHashMap<ResourceLocation, Class<?>>();
    private static final Map<Class<?>, ResourceLocation> keyOf = new IdentityHashMap<Class<?>, ResourceLocation>();
    private static final Map<ResourceLocation, Integer> keyToVariant = new HashMap<ResourceLocation, Integer>(); // e.g., minecart type
    private static final Map<ResourceLocation, Integer> keyToLegacyId = new HashMap<ResourceLocation, Integer>();
    private static final Map<Integer, ResourceLocation> legacyIdToKey = new HashMap<Integer, ResourceLocation>();
    private static final Map<ResourceLocation, String> keyToLegacyName = new HashMap<ResourceLocation, String>();
    private static final Map<Class<?>, String> legacyNameByClass = new IdentityHashMap<Class<?>, String>();
    private static final Map<ResourceLocation, EntityTypeDef<?>> defByKey = new LinkedHashMap<ResourceLocation, EntityTypeDef<?>>();
    private static final Map<Class<?>, EntityTypeDef<?>> defByClass = new IdentityHashMap<Class<?>, EntityTypeDef<?>>();
    private static final MappedRegistry<EntityTypeDef<?>> runtimeTypeRegistry = new MappedRegistry<EntityTypeDef<?>>();
    private static boolean scanned = false;

    private EntityTypeRegistry() {}

    public static synchronized boolean register(ResourceLocation key, Class<?> entityClass) {
        if (key == null || entityClass == null) return false;
        Class<?> cur = byKey.get(key);
        if (cur != null && cur != entityClass) return false;
        byKey.put(key, entityClass);
        if (!keyOf.containsKey(entityClass)) keyOf.put(entityClass, key);
        cacheLegacyMapping(key, entityClass);
        cacheLegacyNameMapping(key, entityClass);
        registerTypeDefinition(key, entityClass);
        try { Registries.ENTITY_TYPE.registerIfAbsent(key, entityClass); } catch (Throwable ignored) {}
        return true;
    }

    public static synchronized boolean register(ResourceLocation key, Class<?> entityClass, int defaultVariant) {
        if (!register(key, entityClass)) {
            return false;
        }
        keyToVariant.put(key, Integer.valueOf(defaultVariant));
        return true;
    }

    public static synchronized void registerAlias(ResourceLocation alias, Class<?> entityClass) {
        if (alias == null || entityClass == null) return;
        if (!byKey.containsKey(alias)) {
            byKey.put(alias, entityClass);
            cacheLegacyMapping(alias, entityClass);
            cacheLegacyNameMapping(alias, entityClass);
            registerTypeDefinition(alias, entityClass);
        }
    }

    public static Class<?> get(ResourceLocation key) { ensureScanned(); return byKey.get(key); }
    public static ResourceLocation getKey(Class<?> c) { ensureScanned(); return keyOf.get(c); }
    public static Set<ResourceLocation> keys() { ensureScanned(); return Collections.unmodifiableSet(byKey.keySet()); }
    public static Collection<Class<?>> values() { ensureScanned(); return Collections.unmodifiableCollection(byKey.values()); }
    public static Collection<ResourceLocation> primaryKeys() { ensureScanned(); return Collections.unmodifiableCollection(keyOf.values()); }
    public static int size() { ensureScanned(); return byKey.size(); }

    public static Class<?> getByIdentifier(String any) {
        String normalized = normalizeInputIdentifier(any);
        if (normalized == null) return null;
        return get(new ResourceLocation(normalized));
    }

    public static int getDefaultVariant(String identifier) {
        if (identifier == null) return -1;
        ensureScanned();
        try {
            ResourceLocation rl = new ResourceLocation(identifier);
            Integer v = keyToVariant.get(rl);
            return v != null ? v.intValue() : -1;
        } catch (Throwable ignored) { return -1; }
    }

    public static Integer getLegacyId(ResourceLocation key) {
        if (key == null) return null;
        ensureScanned();
        return keyToLegacyId.get(key);
    }

    public static ResourceLocation getKeyByLegacyId(int legacyId) {
        ensureScanned();
        return legacyIdToKey.get(Integer.valueOf(legacyId));
    }

    public static String getLegacyName(ResourceLocation key) {
        if (key == null) return null;
        ensureScanned();
        return keyToLegacyName.get(key);
    }

    public static EntityTypeDef<?> getType(ResourceLocation key) {
        if (key == null) return null;
        ensureScanned();
        return defByKey.get(key);
    }

    public static EntityTypeDef<?> getType(Class<?> entityClass) {
        if (entityClass == null) return null;
        ensureScanned();
        return defByClass.get(entityClass);
    }

    public static Holder<EntityTypeDef<?>> getTypeHolder(ResourceLocation key) {
        if (key == null) return null;
        ensureScanned();
        return runtimeTypeRegistry.getHolder(key);
    }

    public static Holder<EntityTypeDef<?>> getTypeHolder(Class<?> entityClass) {
        if (entityClass == null) return null;
        ensureScanned();
        EntityTypeDef<?> def = defByClass.get(entityClass);
        return def == null ? null : runtimeTypeRegistry.getHolder(def);
    }

    public static Holder<EntityTypeDef<?>> getTypeHolder(Entity entity) {
        if (entity == null) return null;
        return getTypeHolder(entity.getClass());
    }

    public static EntityTypeDef<?> getTypeByRuntimeId(int runtimeId) {
        ensureScanned();
        return runtimeTypeRegistry.byId(runtimeId);
    }

    public static String normalizeInputIdentifier(String any) {
        if (any == null) return null;
        ensureScanned();
        try {
            ResourceLocation in = new ResourceLocation(any);
            if (byKey.containsKey(in)) return in.toString();
            if (any.indexOf(':') < 0) {
                ResourceLocation in2 = new ResourceLocation("minecraft", any);
                if (byKey.containsKey(in2)) return in2.toString();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String canonicalizeIdentifier(String any) {
        if (any == null) return null;
        ensureScanned();
        try {
            ResourceLocation in = new ResourceLocation(any);
            Class<?> c = byKey.get(in);
            if (c != null) {
                ResourceLocation key = keyOf.get(c);
                return key != null ? key.toString() : null;
            }
            if (any.indexOf(':') < 0) {
                ResourceLocation in2 = new ResourceLocation("minecraft", any);
                c = byKey.get(in2);
                if (c != null) {
                    ResourceLocation key = keyOf.get(c);
                    return key != null ? key.toString() : null;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static synchronized void bootstrapFromEntityTypes() {
        if (scanned) return;
        scanned = true;
        try {
            Field f = EntityTypes.class.getDeclaredField("a"); // string->class
            f.setAccessible(true);
            Map map = (Map) f.get(null);
            if (map != null) {
                for (Object e : map.entrySet()) {
                    Map.Entry entry = (Map.Entry) e;
                    Object k = entry.getKey();
                    Object v = entry.getValue();
                    if (!(k instanceof String) || !(v instanceof Class)) continue;
                    String name = (String) k;
                    Class clazz = (Class) v;
                    registerCanonicalFor(name, clazz);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void registerCanonicalFor(String raw, Class clazz) {
        if (clazz == null || raw == null) return;
        if (clazz == Entity.class) return;
        String snake = toSnakeCase(raw);
        ResourceLocation primary = new ResourceLocation("minecraft", snake);
        register(primary, clazz);
        // aliases for legacy names
        try { registerAlias(new ResourceLocation("minecraft", raw), clazz); } catch (Throwable ignored) {}
        try { registerAlias(new ResourceLocation("minecraft", raw.toLowerCase()), clazz); } catch (Throwable ignored) {}

        String path = primary.getPath();
        if ("minecart".equals(path)) {
            register(new ResourceLocation("minecraft", "storage_minecart"), clazz, 1);
            registerAlias(new ResourceLocation("minecraft", "chest_minecart"), clazz);
            register(new ResourceLocation("minecraft", "powered_minecart"), clazz, 2);
            registerAlias(new ResourceLocation("minecraft", "furnace_minecart"), clazz);
            keyToVariant.put(primary, Integer.valueOf(0));
        }
        if ("falling_sand".equals(path)) {
            registerAlias(new ResourceLocation("minecraft", "falling_gravel"), clazz);
        }
        if ("snow_man".equals(path) || "snowman".equals(path) || "snow_golem".equals(path)) {
            registerAlias(new ResourceLocation("minecraft", "snowman"), clazz);
            registerAlias(new ResourceLocation("minecraft", "snow_golem"), clazz);
            ResourceLocation pref = new ResourceLocation("minecraft", "snowman");
            byKey.put(pref, clazz);
            keyOf.put(clazz, pref);
            cacheLegacyMapping(pref, clazz);
            cacheLegacyNameMapping(pref, clazz);
            registerTypeDefinition(pref, clazz);
        }
    }

    private static void ensureScanned() {
        if (!scanned) bootstrapFromEntityTypes();
    }

    private static void cacheLegacyMapping(ResourceLocation key, Class<?> entityClass) {
        Integer legacyId = resolveLegacyIdForClass(entityClass);
        if (legacyId == null) {
            return;
        }
        keyToLegacyId.put(key, legacyId);
        if (!legacyIdToKey.containsKey(legacyId)) {
            legacyIdToKey.put(legacyId, key);
        }
    }

    private static void cacheLegacyNameMapping(ResourceLocation key, Class<?> entityClass) {
        String legacyName = resolveLegacyNameForClass(entityClass);
        if (legacyName == null || legacyName.length() == 0) {
            return;
        }
        keyToLegacyName.put(key, legacyName);
        if (!legacyNameByClass.containsKey(entityClass)) {
            legacyNameByClass.put(entityClass, legacyName);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerTypeDefinition(ResourceLocation key, Class<?> entityClass) {
        if (!Entity.class.isAssignableFrom(entityClass)) {
            return;
        }

        EntityTypeDef<?> existingByClass = defByClass.get(entityClass);
        if (existingByClass != null) {
            defByKey.put(key, existingByClass);
            Integer legacyId = keyToLegacyId.get(key);
            int preferredId = legacyId == null ? -1 : legacyId.intValue();
            runtimeTypeRegistry.registerIfAbsent(key, existingByClass, preferredId);
            return;
        }

        Integer legacyId = keyToLegacyId.get(key);
        int legacy = legacyId == null ? -1 : legacyId.intValue();
        EntityTypeDef<?> def = new EntityTypeDef<Entity>(key, (Class<Entity>)entityClass, legacy);
        defByClass.put(entityClass, def);
        defByKey.put(key, def);
        runtimeTypeRegistry.registerIfAbsent(key, def, legacy);
    }

    private static Integer resolveLegacyIdForClass(Class<?> entityClass) {
        if (entityClass == null) {
            return null;
        }

        try {
            Field classToId = EntityTypes.class.getDeclaredField("d"); // class -> id
            classToId.setAccessible(true);
            Map map = (Map) classToId.get(null);
            if (map == null) {
                return null;
            }
            Object value = map.get(entityClass);
            return value instanceof Integer ? (Integer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveLegacyNameForClass(Class<?> entityClass) {
        if (entityClass == null) {
            return null;
        }

        try {
            Field classToName = EntityTypes.class.getDeclaredField("b"); // class -> string
            classToName.setAccessible(true);
            Map map = (Map)classToName.get(null);
            if (map == null) {
                return null;
            }
            Object value = map.get(entityClass);
            return value instanceof String ? (String)value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String toSnakeCase(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) out.append('_');
                out.append(Character.toLowerCase(c));
            } else if (c == '.' || c == ' ' || c == '/') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return out.toString();
    }
}

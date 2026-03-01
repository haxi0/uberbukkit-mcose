package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.Item;
import net.minecraft.server.ItemBlock;
import net.minecraft.server.ItemCloth;
import net.minecraft.server.ItemCoal;
import net.minecraft.server.ItemDye;
import net.minecraft.server.ItemLog;
import net.minecraft.server.ItemRecord;
import net.minecraft.server.ItemSapling;
import net.minecraft.server.ItemStack;
import net.minecraft.server.ItemStoneBrick;
import net.minecraft.server.util.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side namespaced item registry with explicit legacy-id translation.
 */
public final class ItemRegistry {
    private static final Map<ResourceLocation, Item> byKey = new HashMap<ResourceLocation, Item>();
    private static final Map<ResourceLocation, Integer> keyToDamage = new HashMap<ResourceLocation, Integer>();
    private static final Map<Item, ResourceLocation> keyOf = new IdentityHashMap<Item, ResourceLocation>();
    private static final List<Listener> listeners = new ArrayList<Listener>();
    private static boolean scanned = false;

    public interface Listener {
        void onRegistered(ResourceLocation key, Item item);
    }

    private ItemRegistry() {}

    public static synchronized void register(ResourceLocation key, Item item, int legacyId) {
        if (key == null || item == null) return;
        Item existing = byKey.get(key);
        if (existing != null && existing != item) {
            return;
        }
        byKey.put(key, item);
        if (!keyOf.containsKey(item)) {
            keyOf.put(item, key);
        }
        try { Registries.ITEM.registerIfAbsent(key, item); } catch (Throwable ignored) {}
        for (int i = 0; i < listeners.size(); i++) {
            try { listeners.get(i).onRegistered(key, item); } catch (Throwable ignored) {}
        }
    }

    public static Item get(ResourceLocation key) {
        ensureScanned();
        return byKey.get(key);
    }

    public static ResourceLocation getKey(Item item) {
        ensureScanned();
        return keyOf.get(item);
    }

    public static ResourceLocation getKeyForStack(ItemStack stack) {
        if (stack == null) return null;
        Item item = stack.getItem();
        if (item == null) return null;

        ensureScanned();
        if (stack.usesData() && stack.getData() >= 0) {
            int damage = stack.getData();
            for (Map.Entry<ResourceLocation, Integer> entry : keyToDamage.entrySet()) {
                if (entry.getValue().intValue() == damage && byKey.get(entry.getKey()) == item) {
                    return entry.getKey();
                }
            }
        }

        return getKey(item);
    }

    public static Item getByLegacyId(int legacyId) {
        if (legacyId < 0 || legacyId >= Item.byId.length) return null;
        return Item.byId[legacyId];
    }

    public static int getLegacyId(Item item) {
        return item == null ? -1 : item.id;
    }

    public static Collection<Item> values() {
        ensureScanned();
        return Collections.unmodifiableCollection(byKey.values());
    }

    public static Set<ResourceLocation> keys() {
        ensureScanned();
        return Collections.unmodifiableSet(byKey.keySet());
    }

    public static Collection<ResourceLocation> primaryKeys() {
        ensureScanned();
        return Collections.unmodifiableCollection(keyOf.values());
    }

    public static Collection<ResourceLocation> displayKeys() {
        ensureScanned();
        java.util.LinkedHashSet<ResourceLocation> out = new java.util.LinkedHashSet<ResourceLocation>();
        out.addAll(keyOf.values());
        out.addAll(keyToDamage.keySet());
        return out;
    }

    public static void addListener(Listener listener) {
        if (listener == null) return;
        listeners.add(listener);
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public static synchronized void registerAlias(ResourceLocation alias, Item item) {
        registerAliasIfFree(alias, item);
    }

    public static Integer getId(String identifier) {
        if (identifier == null) return null;
        String normalized = normalizeInputIdentifier(identifier);
        if (normalized == null) return null;
        Item item = get(new ResourceLocation(normalized));
        return item == null ? null : Integer.valueOf(getLegacyId(item));
    }

    public static String getIdentifier(int legacyId) {
        ensureScanned();
        Item item = getByLegacyId(legacyId);
        if (item == null) return null;
        ResourceLocation key = getKey(item);
        return key != null ? key.toString() : ("legacy:" + legacyId);
    }

    public static String canonicalizeIdentifier(String any) {
        if (any == null) return null;
        ensureScanned();
        String normalized = normalizeInputIdentifier(any);
        if (normalized == null) return null;
        Item item = byKey.get(new ResourceLocation(normalized));
        if (item == null) return null;
        ResourceLocation key = keyOf.get(item);
        return key != null ? key.toString() : null;
    }

    public static String normalizeInputIdentifier(String any) {
        if (any == null) return null;
        ensureScanned();
        try {
            String normalized = RegistryKeyPolicy.normalizeIdentifier(any);
            if (normalized == null) return null;
            ResourceLocation in = new ResourceLocation(normalized);
            if (byKey.containsKey(in)) return in.toString();
        } catch (Throwable ignored) {}
        return null;
    }

    public static int getDefaultDamage(String identifier) {
        if (identifier == null) return -1;
        ensureScanned();
        try {
            String normalized = normalizeInputIdentifier(identifier);
            if (normalized == null) return -1;
            ResourceLocation rl = new ResourceLocation(normalized);
            Integer dmg = keyToDamage.get(rl);
            return dmg != null ? dmg.intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static synchronized void bootstrapFromItemsList() {
        if (!scanned) {
            scanned = true;
        }
        syncFromItemsList();
    }

    public static String keysetFingerprint() {
        ensureScanned();
        return fingerprint(keys());
    }

    public static String canonicalFingerprint() {
        ensureScanned();
        return fingerprint(primaryKeys());
    }

    public static boolean runSanityChecks() {
        ensureScanned();
        String[] required = new String[]{
                "minecraft:iron_ingot",
                "minecraft:golden_apple",
                "minecraft:record_13",
                "minecraft:redstone_torch"
        };
        boolean ok = true;
        for (int i = 0; i < required.length; i++) {
            if (normalizeInputIdentifier(required[i]) == null) {
                ok = false;
                System.err.println("[ItemRegistry] Missing critical key: " + required[i]);
            }
        }
        Item redstoneTorch = get(new ResourceLocation("minecraft", "redstone_torch"));
        if (!isLitRedstoneTorchBlockItem(redstoneTorch)) {
            ok = false;
            System.err.println("[ItemRegistry] Canonical minecraft:redstone_torch must map to lit redstone torch item");
        }
        return ok;
    }

    private static void registerCanonicalFor(int id, Item item) {
        try {
            String internal = item.a();
            if (internal == null || internal.length() == 0) {
                internal = "legacy_" + id;
            }

            String stripped = RegistryKeyPolicy.stripKnownPrefix(internal);
            String snake = RegistryKeyPolicy.toSnakeCase(stripped);
            String canonicalPath = RegistryKeyPolicy.canonicalizePath(snake);
            if (item instanceof ItemBlock) {
                canonicalPath = RegistryKeyPolicy.canonicalizeBlockPath(snake, item.id);
            }
            canonicalPath = canonicalRedstoneTorchItemPath(item, canonicalPath);

            ResourceLocation key = new ResourceLocation("minecraft", canonicalPath);
            Item existingForKey = byKey.get(key);
            if (existingForKey != null && existingForKey != item) {
                if (item instanceof ItemRecord) {
                    String rn = ((ItemRecord)item).a;
                    if (rn != null && rn.length() > 0) {
                        String sanitized = rn.toLowerCase().replace(' ', '_').replaceAll("[^a-z0-9_]+", "");
                        key = new ResourceLocation("minecraft", "record_" + sanitized);
                    }
                }
                if (byKey.containsKey(key) && byKey.get(key) != item) {
                    String path = key.getPath();
                    boolean isBlockItem = (item instanceof ItemBlock) || path.startsWith("tile_") || path.endsWith("_block");
                    if (isBlockItem && isRedstoneTorchBlockItem(item)) {
                        key = new ResourceLocation("minecraft", canonicalRedstoneTorchItemPath(item, path));
                    } else if (isBlockItem && !path.endsWith("_block")) {
                        key = new ResourceLocation("minecraft", path + "_block");
                    } else {
                        key = new ResourceLocation("minecraft", RegistryKeyPolicy.collisionLegacySuffix(path, id));
                    }
                }
            }

            register(key, item, id);
            keyOf.put(item, key);
            registerMetaAliasesFor(key, item);

            String[] policyAliases = RegistryKeyPolicy.aliasesForCanonical(key.getPath());
            for (int i = 0; i < policyAliases.length; i++) {
                try { registerAliasIfFree(new ResourceLocation("minecraft", policyAliases[i]), item); } catch (Throwable ignored) {}
            }

            String snakeAlias = RegistryKeyPolicy.toSnakeCase(stripped);
            registerAliasIfFree(new ResourceLocation("minecraft", snakeAlias), item);

            String legacyOrder = RegistryKeyPolicy.materialLastIfKnown(snakeAlias);
            if (legacyOrder != null && legacyOrder.length() > 0) {
                registerAliasIfFree(new ResourceLocation("minecraft", legacyOrder), item);
            }

            String altMatFirst = RegistryKeyPolicy.materialFirstWithAltSynonyms(snakeAlias);
            if (altMatFirst != null && altMatFirst.length() > 0) {
                registerAliasIfFree(new ResourceLocation("minecraft", altMatFirst), item);
            }

            if (item instanceof ItemRecord) {
                String rn = ((ItemRecord)item).a;
                if (rn != null && rn.length() > 0) {
                    String sanitized = rn.toLowerCase().replace(' ', '_').replaceAll("[^a-z0-9_]+", "");
                    registerAliasIfFree(new ResourceLocation("minecraft", "record_" + sanitized), item);
                    registerAliasIfFree(new ResourceLocation("minecraft", "music_disc_" + sanitized), item);
                    if ("tsuku_no_koibumi".equals(sanitized)) {
                        registerAliasIfFree(new ResourceLocation("minecraft", "record_tsuki_no_koibumi"), item);
                        registerAliasIfFree(new ResourceLocation("minecraft", "music_disc_tsuki_no_koibumi"), item);
                    }
                }
            }

            if (item instanceof ItemDye) {
                String[] colors = ItemDye.a;
                if (colors != null) {
                    for (int dm = 0; dm < colors.length; dm++) {
                        String color = RegistryKeyPolicy.toSnakeCase(colors[dm]);
                        if ("lightblue".equals(color)) color = "light_blue";
                        if ("silver".equals(color)) color = "light_gray";
                        registerColorMeta(color + "_dye", item, dm);
                    }
                }
                registerColorMeta("cocoa_beans", item, 3);
                registerColorMeta("ink_sac", item, 0);
                registerColorMeta("lapis_lazuli", item, 4);
                registerColorMeta("bone_meal", item, 15);
            }

            if (item instanceof ItemCloth) {
                String[] woolColors = new String[]{
                        "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                        "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
                };
                for (int dm = 0; dm < woolColors.length; dm++) {
                    registerColorMeta(woolColors[dm] + "_wool", item, dm);
                }
            }

            if (item instanceof ItemCoal) {
                registerColorMeta("coal", item, 0);
                registerColorMeta("charcoal", item, 1);
            }
        } catch (Throwable ignored) {}
    }

    private static void registerMetaAliasesFor(ResourceLocation primaryKey, Item item) {
        if (primaryKey == null || item == null) return;
        String path = primaryKey.getPath();

        if ("redstone_torch".equals(path) && isLitRedstoneTorchBlockItem(item)) {
            registerAliasIfFree(new ResourceLocation("minecraft", "lit_redstone_torch"), item);
            registerAliasIfFree(new ResourceLocation("minecraft", "redstone_torch_on"), item);
        }

        if ("redstone_torch_off".equals(path) && isRedstoneTorchBlockItem(item)) {
            registerAliasIfFree(new ResourceLocation("minecraft", "unlit_redstone_torch"), item);
            registerAliasIfFree(new ResourceLocation("minecraft", "redstone_torch_idle"), item);
        }

        if (item instanceof ItemLog || "log".equals(path)) {
            registerColorMeta("oak_log", item, 0);
            registerColorMeta("spruce_log", item, 1);
            registerColorMeta("birch_log", item, 2);
        }

        if (item instanceof ItemSapling || "sapling".equals(path)) {
            registerColorMeta("oak_sapling", item, 0);
            registerColorMeta("spruce_sapling", item, 1);
            registerColorMeta("birch_sapling", item, 2);
        }

        if (item instanceof ItemStoneBrick || "stone_bricks".equals(path) || "stonebrick".equals(path) || "stone_brick".equals(path)) {
            registerColorMeta("stone_brick", item, 0);
            registerColorMeta("mossy_stone_brick", item, 1);
            registerColorMeta("cracked_stone_brick", item, 2);
            registerColorMeta("chiseled_stone_brick", item, 3);
        }

        if ("stone_slab".equals(path) || "stone_slab_top".equals(path) || "stone_slab_half".equals(path) || "stairsingle".equals(path)) {
            registerColorMeta("stone_slab", item, 0);
            registerColorMeta("sand_slab", item, 1);
            registerColorMeta("sandstone_slab", item, 1);
            registerColorMeta("wooden_slab", item, 2);
            registerColorMeta("cobblestone_slab", item, 3);
            registerColorMeta("brick_slab", item, 4);
            registerColorMeta("stone_brick_slab", item, 5);
        }

        if ("fencegate".equals(path)) {
            registerAliasIfFree(new ResourceLocation("minecraft", "fence_gate"), item);
        }

        if (path.endsWith("_stairs") || path.startsWith("stairs_")) {
            String canonical = RegistryKeyPolicy.materialFirstIfKnown(path);
            if (!canonical.equals(path)) {
                ResourceLocation canonicalKey = new ResourceLocation("minecraft", canonical);
                registerAliasIfFree(canonicalKey, item);
                keyOf.put(item, canonicalKey);
            }
            if ("wood_stairs".equals(path)) {
                ResourceLocation wooden = new ResourceLocation("minecraft", "wooden_stairs");
                registerAliasIfFree(wooden, item);
                keyOf.put(item, wooden);
            }
            if ("stone_brick_smooth_stairs".equals(path) || "smooth_stone_brick_stairs".equals(path)) {
                ResourceLocation fixed = new ResourceLocation("minecraft", "stone_brick_stairs");
                registerAliasIfFree(fixed, item);
                keyOf.put(item, fixed);
            }
        }
    }

    private static void registerColorMeta(String name, Item item, int damage) {
        try {
            ResourceLocation rl = new ResourceLocation("minecraft", name);
            byKey.put(rl, item);
            keyToDamage.put(rl, Integer.valueOf(damage));
            VariantDefaults.put(rl, damage);
            try { Registries.ITEM.registerIfAbsent(rl, item); } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static void registerAliasIfFree(ResourceLocation alias, Item item) {
        if (alias == null || item == null) return;
        Item existing = byKey.get(alias);
        if (existing == null) {
            byKey.put(alias, item);
            try { Registries.ITEM.registerIfAbsent(alias, item); } catch (Throwable ignored) {}
        }
    }

    private static boolean isLitRedstoneTorchBlockItem(Item item) {
        return item instanceof ItemBlock && item.id == 76;
    }

    private static String canonicalRedstoneTorchItemPath(Item item, String fallback) {
        if (!isRedstoneTorchBlockItem(item)) {
            return fallback;
        }
        return isLitRedstoneTorchBlockItem(item) ? "redstone_torch" : "redstone_torch_off";
    }

    private static boolean isRedstoneTorchBlockItem(Item item) {
        return item instanceof ItemBlock && (item.id == 75 || item.id == 76);
    }

    private static synchronized void ensureScanned() {
        if (!scanned) {
            bootstrapFromItemsList();
            return;
        }
        syncFromItemsList();
    }

    private static void syncFromItemsList() {
        boolean changed = false;
        for (int id = 0; id < Item.byId.length; id++) {
            Item item = Item.byId[id];
            if (item == null) continue;
            if (!keyOf.containsKey(item)) {
                registerCanonicalFor(id, item);
                changed = true;
            }
        }
        if (changed) {
            try { LegacyIdBridge.refresh(); } catch (Throwable ignored) {}
        }
    }

    private static String fingerprint(Collection<ResourceLocation> values) {
        try {
            ArrayList<String> sorted = new ArrayList<String>();
            for (ResourceLocation key : values) {
                sorted.add(key.toString());
            }
            Collections.sort(sorted);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < sorted.size(); i++) {
                digest.update(sorted.get(i).getBytes(StandardCharsets.UTF_8));
                digest.update((byte)'\n');
            }
            byte[] hash = digest.digest();
            StringBuilder out = new StringBuilder(hash.length * 2);
            for (int i = 0; i < hash.length; i++) {
                int v = hash[i] & 0xFF;
                if (v < 16) out.append('0');
                out.append(Integer.toHexString(v));
            }
            return out.toString();
        } catch (Throwable t) {
            return "unavailable";
        }
    }
}

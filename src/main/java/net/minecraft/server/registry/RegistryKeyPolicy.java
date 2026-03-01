package net.minecraft.server.registry;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Shared key normalization and legacy-name policy for item/block registries.
 */
public final class RegistryKeyPolicy {
    public static final String DEFAULT_NAMESPACE = "minecraft";

    private static final Map<String, String> CANONICAL_PATH_OVERRIDES = new HashMap<String, String>();
    private static final Map<String, String[]> CANONICAL_ALIASES = new HashMap<String, String[]>();
    private static final Map<Integer, String> BLOCK_ID_OVERRIDES = new HashMap<Integer, String>();

    private static final String[] MATERIALS = new String[]{
            "wood", "wooden", "stone", "iron", "steel", "gold", "golden", "diamond", "leather", "cloth", "chain", "chainmail",
            "redstone", "coal", "lapis"
    };

    private static final String[] KINDS = new String[]{
            "sword", "shovel", "pickaxe", "axe", "hoe", "helmet", "chestplate", "leggings", "boots", "door", "ingot", "hatchet", "stairs", "slab", "ore", "block"
    };

    static {
        override("stonebrick", "cobblestone");
        override("stone_moss", "mossy_cobblestone");
        override("stairs_stone", "cobblestone_stairs");
        override("stairs_wood", "wooden_stairs");
        override("stone_bricksmooth", "stone_bricks");
        override("stairs_stone_brick_smooth", "stone_brick_stairs");
        override("smooth_stone_brick_stairs", "stone_brick_stairs");
        override("golden_rail", "powered_rail");
        override("door_wood", "wooden_door");
        override("door_iron", "iron_door");
        override("not_gate", "redstone_torch");
        override("fencegate", "fence_gate");
        override("hellrock", "netherrack");
        override("hellsand", "soul_sand");
        override("lightgem", "glowstone");
        override("litpumpkin", "jack_o_lantern");

        override("apple_gold", "golden_apple");
        override("yellow_dust", "glowstone_dust");
        override("minecart_chest", "chest_minecart");
        override("minecart_furnace", "furnace_minecart");
        override("emerald", "diamond");

        alias("netherrack", "hellrock");
        alias("glowstone", "lightgem");
        alias("soul_sand", "hellsand");
        alias("golden_apple", "apple_gold", "gold_apple");
        alias("stone_brick_stairs", "stone_brick_smooth_stairs", "smooth_stone_brick_stairs", "stairs_stone_brick_smooth");
        alias("wooden_stairs", "wood_stairs", "stairs_wood");
        alias("cobblestone_stairs", "stairs_stone", "stone_stairs");
        alias("fence_gate", "fencegate");
        alias("chest_minecart", "minecart_chest", "storage_minecart");
        alias("sulphur", "gunpowder");
        alias("seeds", "wheat_seeds", "wheat_seed");
        alias("reeds", "sugar_cane");

        BLOCK_ID_OVERRIDES.put(Integer.valueOf(8), "water");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(9), "water_still");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(10), "lava");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(11), "lava_still");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(61), "furnace");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(62), "lit_furnace");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(63), "standing_sign");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(68), "wall_sign");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(70), "stone_pressure_plate");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(72), "wooden_pressure_plate");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(73), "redstone_ore");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(74), "lit_redstone_ore");
        // Canonical redstone torch should resolve to the lit placeable variant.
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(75), "redstone_torch_off");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(76), "redstone_torch");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(93), "repeater");
        BLOCK_ID_OVERRIDES.put(Integer.valueOf(94), "lit_repeater");
    }

    private RegistryKeyPolicy() {}

    private static void override(String raw, String canonical) {
        CANONICAL_PATH_OVERRIDES.put(raw, canonical);
    }

    private static void alias(String canonical, String... aliases) {
        CANONICAL_ALIASES.put(canonical, aliases);
    }

    public static boolean looksNumeric(String token) {
        if (token == null || token.length() == 0) return false;
        int start = (token.charAt(0) == '-' || token.charAt(0) == '+') ? 1 : 0;
        if (start >= token.length()) return false;
        for (int i = start; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) return false;
        }
        return true;
    }

    public static String stripKnownPrefix(String name) {
        if (name == null) return "";
        if (name.startsWith("item.")) return name.substring(5);
        if (name.startsWith("tile.")) return name.substring(5);
        return name;
    }

    public static String normalizeIdentifier(String any) {
        if (any == null) return null;
        String trimmed = any.trim();
        if (trimmed.length() == 0) return null;

        String namespace = DEFAULT_NAMESPACE;
        String path = trimmed;
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            namespace = normalizeNamespace(trimmed.substring(0, colon));
            path = trimmed.substring(colon + 1);
        }

        String normalizedPath = normalizePath(path);
        if (normalizedPath.length() == 0) return null;
        return namespace + ":" + normalizedPath;
    }

    public static String normalizeNamespace(String namespace) {
        if (namespace == null || namespace.trim().length() == 0) return DEFAULT_NAMESPACE;
        return namespace.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizePath(String path) {
        if (path == null) return "";
        String normalized = path.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replace(' ', '_');
        normalized = normalized.replace('\\', '/');
        normalized = normalized.replace(':', '/');
        while (normalized.startsWith("/")) normalized = normalized.substring(1);
        while (normalized.indexOf("//") >= 0) normalized = normalized.replace("//", "/");
        return normalized;
    }

    public static String toSnakeCase(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) out.append('_');
                out.append(Character.toLowerCase(c));
            } else if (c == '.' || c == ' ' || c == '-') {
                out.append('_');
            } else {
                out.append(Character.toLowerCase(c));
            }
        }
        return normalizePath(out.toString());
    }

    public static String canonicalizePath(String inputPath) {
        String snake = normalizePath(inputPath);
        if (snake.length() == 0) return snake;
        String materialFirst = materialFirstIfKnown(snake);
        String override = CANONICAL_PATH_OVERRIDES.get(materialFirst);
        return override != null ? override : materialFirst;
    }

    public static String canonicalizeBlockPath(String inputPath, int legacyId) {
        String byId = BLOCK_ID_OVERRIDES.get(Integer.valueOf(legacyId));
        if (byId != null) return byId;
        return canonicalizePath(inputPath);
    }

    public static String[] aliasesForCanonical(String canonicalPath) {
        String[] aliases = CANONICAL_ALIASES.get(canonicalPath);
        return aliases != null ? aliases : new String[0];
    }

    public static String materialFirstIfKnown(String snake) {
        if (snake == null) return "";
        String[] parts = snake.split("_");
        if (parts.length == 2 && isKind(parts[0]) && isMaterial(parts[1])) {
            return canonicalMaterial(parts[1]) + "_" + canonicalKind(parts[0]);
        }
        if (parts.length >= 2 && ("stairs".equals(parts[0]) || "slab".equals(parts[0]))) {
            StringBuilder mat = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                if (i > 1) mat.append('_');
                mat.append(parts[i]);
            }
            return mat.toString() + "_" + canonicalKind(parts[0]);
        }
        if (parts.length == 2 && "ore".equals(parts[0])) {
            return parts[1] + "_ore";
        }
        if (parts.length == 2 && "block".equals(parts[0])) {
            return parts[1] + "_block";
        }
        return snake;
    }

    public static String materialLastIfKnown(String snake) {
        if (snake == null) return null;
        String[] parts = snake.split("_");
        if (parts.length == 2 && isKind(parts[0]) && isMaterial(parts[1])) {
            return snake;
        }
        if (parts.length == 2 && isMaterial(parts[0]) && isKind(parts[1])) {
            return canonicalKind(parts[1]) + "_" + canonicalMaterial(parts[0]);
        }
        return null;
    }

    public static String materialFirstWithAltSynonyms(String snake) {
        if (snake == null) return null;
        String[] parts = snake.split("_");
        if (parts.length == 2 && isKind(parts[0]) && isMaterial(parts[1])) {
            return altMaterial(canonicalMaterial(parts[1])) + "_" + canonicalKind(parts[0]);
        }
        if (parts.length == 2 && isMaterial(parts[0]) && isKind(parts[1])) {
            return altMaterial(canonicalMaterial(parts[0])) + "_" + canonicalKind(parts[1]);
        }
        return null;
    }

    public static String collisionLegacySuffix(String basePath, int legacyId) {
        return basePath + "_legacy_" + legacyId;
    }

    private static boolean isMaterial(String s) {
        for (int i = 0; i < MATERIALS.length; i++) if (MATERIALS[i].equals(s)) return true;
        return false;
    }

    private static boolean isKind(String s) {
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(s)) return true;
        return false;
    }

    private static String canonicalMaterial(String m) {
        if ("steel".equals(m)) return "iron";
        if ("golden".equals(m)) return "gold";
        if ("cloth".equals(m)) return "leather";
        if ("chain".equals(m)) return "chainmail";
        if ("wooden".equals(m)) return "wood";
        return m;
    }

    private static String altMaterial(String m) {
        if ("gold".equals(m)) return "golden";
        if ("wood".equals(m)) return "wooden";
        if ("iron".equals(m)) return "steel";
        if ("leather".equals(m)) return "cloth";
        if ("chainmail".equals(m)) return "chain";
        return m;
    }

    private static String canonicalKind(String k) {
        if ("hatchet".equals(k)) return "axe";
        return k;
    }
}

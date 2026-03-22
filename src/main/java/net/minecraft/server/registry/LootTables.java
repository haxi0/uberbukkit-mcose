package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collection;
import java.util.Set;

/**
 * Bootstrap class that registers all vanilla loot tables.
 * Loot tables are registered to Registries.LOOT_TABLE.
 */
public final class LootTables {
    // Dungeon loot tables (separate loot for simple vs monster dungeon variants)
    public static final ResourceLocation DUNGEON = new ResourceLocation("minecraft", "chests/dungeon");
    public static final ResourceLocation SIMPLE_DUNGEON_LOOT = new ResourceLocation("minecraft", "chests/simple_dungeon");
    public static final ResourceLocation MONSTER_DUNGEON_LOOT = new ResourceLocation("minecraft", "chests/monster_dungeon");

    private static boolean initialized = false;

    private LootTables() {}

    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        registerDungeon();
        registerSimpleDungeonLoot();
        registerMonsterDungeonLoot();

        System.out.println("[LootTables] Registered " + Registries.LOOT_TABLE.keys().size() + " loot tables");
    }

    private static void registerDungeon() {
        // Default dungeon loot table (kept identical to simple dungeon loot).
        LootTable table = buildSimpleDungeonTable(DUNGEON);

        LootTableRegistryApi.register(DUNGEON, table);
    }

    private static void registerSimpleDungeonLoot() {
        LootTable table = buildSimpleDungeonTable(SIMPLE_DUNGEON_LOOT);
        LootTableRegistryApi.register(SIMPLE_DUNGEON_LOOT, table);
    }

    private static LootTable buildSimpleDungeonTable(ResourceLocation id) {
        // Classic dungeon loot - saddles, iron, bread, wheat, gunpowder, string, buckets, redstone, cocoa beans
        // Rare: golden apples and music discs
        return LootTable.builder(id)
            .pool(LootPool.builder("main")
                .rolls(1, 3)
                // Common items
                .add(LootEntry.builder("saddle").weight(10))
                .add(LootEntry.builder("iron_ingot").weight(10).count(1, 4))
                .add(LootEntry.builder("bread").weight(10))
                .add(LootEntry.builder("wheat").weight(10).count(1, 4))
                .add(LootEntry.builder("gunpowder").weight(10).count(1, 4))
                .add(LootEntry.builder("string").weight(10).count(1, 4))
                .add(LootEntry.builder("bucket").weight(10))
                .add(LootEntry.builder("wet_sponge").weight(6).count(1, 2))
                .add(LootEntry.builder("redstone").weight(5).count(1, 4))
                .add(LootEntry.builder("cocoa_beans").weight(10).metadata(3)) // Cocoa beans are dye metadata 3
                .add(LootEntry.builder("pumpkin_seed").weight(8).count(1, 3))
                .add(LootEntry.builder("melon_seed").weight(8).count(1, 3))
                .build())
            .pool(LootPool.builder("rare")
                .rolls(0, 1)
                // Rare items with low weight
                .add(LootEntry.builder("golden_apple").weight(1))
                // Classic discs (most common of the discs)
                .add(LootEntry.builder("music_disc_13").weight(2))
                .add(LootEntry.builder("music_disc_cat").weight(2))
                // Empty entry to make rare items actually rare
                .add(LootEntry.builder("air").weight(95)) // 95% chance of nothing from rare pool
                .build())
            .pool(LootPool.builder("music_discs")
                .rolls(0, 1)
                // Standard discs - uncommon
                .add(LootEntry.builder("music_disc_blocks").weight(1))
                .add(LootEntry.builder("music_disc_chirp").weight(1))
                .add(LootEntry.builder("music_disc_far").weight(1))
                .add(LootEntry.builder("music_disc_mall").weight(1))
                .add(LootEntry.builder("music_disc_mellohi").weight(1))
                .add(LootEntry.builder("music_disc_stal").weight(1))
                .add(LootEntry.builder("music_disc_strad").weight(1))
                .add(LootEntry.builder("music_disc_ward").weight(1))
                .add(LootEntry.builder("music_disc_11").weight(1))
                .add(LootEntry.builder("music_disc_wait").weight(1))
                // Empty entry - very low chance of getting a disc from this pool
                .add(LootEntry.builder("air").weight(190)) // ~95% chance of nothing
                .build())
            .build();
    }

    private static void registerMonsterDungeonLoot() {
        // Stone brick dungeon variant - slightly better loot
        LootTable table = LootTable.builder(MONSTER_DUNGEON_LOOT)
            .pool(LootPool.builder("main")
                .rolls(2, 4) // More rolls than simple dungeon
                // Common items (same as simple dungeon)
                .add(LootEntry.builder("saddle").weight(8))
                .add(LootEntry.builder("iron_ingot").weight(10).count(1, 4))
                .add(LootEntry.builder("bread").weight(8))
                .add(LootEntry.builder("wheat").weight(8).count(1, 4))
                .add(LootEntry.builder("gunpowder").weight(10).count(1, 4))
                .add(LootEntry.builder("string").weight(10).count(1, 4))
                .add(LootEntry.builder("bucket").weight(8))
                .add(LootEntry.builder("wet_sponge").weight(8).count(1, 3))
                .add(LootEntry.builder("redstone").weight(6).count(1, 4))
                .add(LootEntry.builder("cocoa_beans").weight(8).metadata(3))
                .add(LootEntry.builder("pumpkin_seed").weight(8).count(1, 3))
                .add(LootEntry.builder("melon_seed").weight(8).count(1, 3))
                // Additional items for monster dungeon
                .add(LootEntry.builder("gold_ingot").weight(5).count(1, 3))
                .add(LootEntry.builder("iron_sword").weight(3))
                .add(LootEntry.builder("iron_chestplate").weight(2))
                .build())
            .pool(LootPool.builder("rare")
                .rolls(0, 2) // Slightly better rare chance
                .add(LootEntry.builder("golden_apple").weight(2))
                .add(LootEntry.builder("diamond").weight(1).count(1, 2))
                // Classic discs
                .add(LootEntry.builder("music_disc_13").weight(3))
                .add(LootEntry.builder("music_disc_cat").weight(3))
                // Empty entry
                .add(LootEntry.builder("air").weight(91))
                .build())
            .pool(LootPool.builder("music_discs")
                .rolls(0, 1)
                // Standard discs - better chance in monster dungeon
                .add(LootEntry.builder("music_disc_blocks").weight(2))
                .add(LootEntry.builder("music_disc_chirp").weight(2))
                .add(LootEntry.builder("music_disc_far").weight(2))
                .add(LootEntry.builder("music_disc_mall").weight(2))
                .add(LootEntry.builder("music_disc_mellohi").weight(2))
                .add(LootEntry.builder("music_disc_stal").weight(2))
                .add(LootEntry.builder("music_disc_strad").weight(2))
                .add(LootEntry.builder("music_disc_ward").weight(2))
                .add(LootEntry.builder("music_disc_11").weight(2))
                .add(LootEntry.builder("music_disc_wait").weight(2))
                // Empty entry - ~80% chance of nothing
                .add(LootEntry.builder("air").weight(80))
                .build())
            .pool(LootPool.builder("rare_discs")
                .rolls(0, 1)
                // Rare/custom discs - only in monster dungeons
                .add(LootEntry.builder("music_disc_aria_math").weight(1))
                .add(LootEntry.builder("music_disc_dog").weight(1))
                .add(LootEntry.builder("music_disc_certitudes").weight(1))
                .add(LootEntry.builder("music_disc_tsuki_no_koibumi").weight(1))
                // Empty entry - very rare (~96% chance of nothing)
                .add(LootEntry.builder("air").weight(96))
                .build())
            .build();

        LootTableRegistryApi.register(MONSTER_DUNGEON_LOOT, table);
    }

    /**
     * Gets a loot table by its resource location.
     */
    public static LootTable get(ResourceLocation id) {
        return LootTableRegistryApi.get(id);
    }

    public static LootTable getByIdentifier(String any) {
        return LootTableRegistryApi.getByIdentifier(any);
    }

    public static ResourceLocation getKey(LootTable value) {
        return LootTableRegistryApi.getKey(value);
    }

    public static Set<ResourceLocation> keys() {
        return LootTableRegistryApi.keys();
    }

    public static Collection<LootTable> values() {
        return LootTableRegistryApi.values();
    }

    public static int size() {
        return LootTableRegistryApi.size();
    }

    public static String normalizeInputIdentifier(String any) {
        return LootTableRegistryApi.normalizeInputIdentifier(any);
    }

    public static String canonicalizeIdentifier(String any) {
        return LootTableRegistryApi.canonicalizeIdentifier(any);
    }
}

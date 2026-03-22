package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.BlockStairs;
import net.minecraft.server.Material;

/**
 * Bootstrap for mining rule defaults.
 */
public final class BlockMiningRegistryBootstrap {
    private static final BlockMiningRule RULE_AXE_15 = new BlockMiningRule(MiningToolType.AXE, false, 1.5F);
    private static final BlockMiningRule RULE_AXE_15_ENFORCED_ALLOW_ANY_DROP = new BlockMiningRule(MiningToolType.AXE, true, 1.5F, true);
    private static final BlockMiningRule RULE_PICKAXE_6 = new BlockMiningRule(MiningToolType.PICKAXE, false, 6.0F);
    private static final BlockMiningRule RULE_PICKAXE_2 = new BlockMiningRule(MiningToolType.PICKAXE, false, 2.0F);
    private static final BlockMiningRule RULE_PICKAXE_175 = new BlockMiningRule(MiningToolType.PICKAXE, false, 1.75F);
    private static final BlockMiningRule RULE_SHOVEL_14 = new BlockMiningRule(MiningToolType.SHOVEL, false, 1.4F);
    private static final BlockMiningRule RULE_SHOVEL_15 = new BlockMiningRule(MiningToolType.SHOVEL, false, 1.5F);

    private static boolean initialized = false;

    private BlockMiningRegistryBootstrap() {}

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        BlockMiningRegistryApi.bootstrapDefaults();
        registerDefaults();

        System.out.println("[BlockMiningRegistryBootstrap] Registered " + BlockMiningRegistryApi.size() + " block mining rules");
    }

    private static void registerDefaults() {
        register(Block.OBSIDIAN, RULE_PICKAXE_6);

        registerAll(RULE_PICKAXE_2, new Block[]{
                Block.COAL_ORE,
                Block.IRON_ORE,
                Block.GOLD_ORE,
                Block.DIAMOND_ORE,
                Block.LAPIS_ORE,
                Block.REDSTONE_ORE,
                Block.GLOWING_REDSTONE_ORE
        });

        registerAll(RULE_PICKAXE_175, new Block[]{
                Block.GOLD_BLOCK,
                Block.IRON_BLOCK,
                Block.DIAMOND_BLOCK,
                Block.LAPIS_BLOCK,
                Block.REDSTONE_BLOCK,
                Block.COAL_BLOCK,
                Block.STONE,
                Block.COBBLESTONE,
                Block.MOSSY_COBBLESTONE,
                Block.SANDSTONE,
                Block.BRICK,
                Block.DISPENSER,
                Block.PISTON,
                Block.PISTON_STICKY,
                Block.PISTON_EXTENSION,
                Block.PISTON_MOVING,
                Block.NETHERRACK
        });

        registerAll(RULE_AXE_15, new Block[]{
                Block.WOOD,
                Block.LOG,
                Block.BOOKSHELF,
                Block.CHEST,
                Block.FENCE,
                Block.FENCE_GATE,
                Block.JUKEBOX,
                Block.NOTE_BLOCK,
                Block.TRAP_DOOR,
                Block.LADDER,
                Block.PUMPKIN,
                Block.MELON,
                Block.JACK_O_LANTERN,
                Block.BROWN_MUSHROOM_CAP,
                Block.RED_MUSHROOM_CAP
        });

        registerAll(RULE_SHOVEL_14, new Block[]{
                Block.DIRT,
                Block.GRASS,
                Block.SAND,
                Block.GRAVEL,
                Block.CLAY,
                Block.SNOW,
                Block.SNOW_BLOCK,
                Block.SOIL
        });
        register(Block.SOUL_SAND, RULE_SHOVEL_15);

        register(Block.WORKBENCH, RULE_AXE_15);
        register(Block.FURNACE, RULE_PICKAXE_175);
        register(Block.BURNING_FURNACE, RULE_PICKAXE_175);
        register(Block.STONE_BRICK, RULE_PICKAXE_175);

        registerStairRules();
        registerSlabRules();
    }

    private static void registerStairRules() {
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (!(block instanceof BlockStairs)) {
                continue;
            }
            if (block.material == Material.WOOD) {
                register(block, RULE_AXE_15);
            } else {
                register(block, RULE_PICKAXE_175);
            }
        }
    }

    private static void registerSlabRules() {
        register(Block.STEP, RULE_PICKAXE_175);
        register(Block.DOUBLE_STEP, RULE_PICKAXE_175);

        int[] stoneLikeMetadata = new int[]{0, 1, 3, 4, 5};
        for (int i = 0; i < stoneLikeMetadata.length; i++) {
            int metadata = stoneLikeMetadata[i];
            BlockMiningRegistryApi.registerMetadataRule(Block.STEP, metadata, RULE_PICKAXE_175);
            BlockMiningRegistryApi.registerMetadataRule(Block.DOUBLE_STEP, metadata, RULE_PICKAXE_175);
        }

        BlockMiningRegistryApi.registerMetadataRule(Block.STEP, 2, RULE_AXE_15_ENFORCED_ALLOW_ANY_DROP);
        BlockMiningRegistryApi.registerMetadataRule(Block.DOUBLE_STEP, 2, RULE_AXE_15_ENFORCED_ALLOW_ANY_DROP);
    }

    private static void register(Block block, BlockMiningRule rule) {
        if (block == null || rule == null) {
            return;
        }
        BlockMiningRegistryApi.register(block, rule);
    }

    private static void registerAll(BlockMiningRule rule, Block[] blocks) {
        if (blocks == null) {
            return;
        }
        for (int i = 0; i < blocks.length; i++) {
            register(blocks[i], rule);
        }
    }
}

package net.minecraft.server.registry;

import net.minecraft.server.Block;
import net.minecraft.server.EntityHuman;
import net.minecraft.server.IBlockAccess;
import net.minecraft.server.Item;
import net.minecraft.server.ItemAxe;
import net.minecraft.server.ItemHoe;
import net.minecraft.server.ItemPickaxe;
import net.minecraft.server.ItemShears;
import net.minecraft.server.ItemSpade;
import net.minecraft.server.ItemStack;
import net.minecraft.server.ItemSword;
import net.minecraft.server.World;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * API-facing registry for block mining behavior.
 */
public final class BlockMiningRegistryApi {
    private static final Map<ResourceLocation, BlockMiningRule> byKey = new LinkedHashMap<ResourceLocation, BlockMiningRule>();
    private static final Map<Block, BlockMiningRule> byBlock = new IdentityHashMap<Block, BlockMiningRule>();
    private static final Map<Block, Map<Integer, BlockMiningRule>> byBlockMetadata = new IdentityHashMap<Block, Map<Integer, BlockMiningRule>>();

    private BlockMiningRegistryApi() {}

    public static synchronized boolean register(ResourceLocation key, BlockMiningRule rule) {
        if (key == null || rule == null) {
            return false;
        }

        Block block = BlockRegistry.get(key);
        if (block == null) {
            return false;
        }

        ResourceLocation canonical = BlockRegistry.getKey(block);
        if (canonical == null) {
            canonical = key;
        }

        byKey.put(canonical, rule);
        byBlock.put(block, rule);
        return true;
    }

    public static boolean register(String identifier, BlockMiningRule rule) {
        if (identifier == null || rule == null) {
            return false;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(identifier);
        if (normalized == null) {
            return false;
        }
        return register(new ResourceLocation(normalized), rule);
    }

    public static synchronized boolean register(Block block, BlockMiningRule rule) {
        if (block == null || rule == null) {
            return false;
        }
        ResourceLocation key = BlockRegistry.getKey(block);
        if (key == null) {
            return false;
        }
        byKey.put(key, rule);
        byBlock.put(block, rule);
        return true;
    }

    public static synchronized boolean registerMetadataRule(ResourceLocation key, int metadata, BlockMiningRule rule) {
        if (key == null || rule == null) {
            return false;
        }
        Block block = BlockRegistry.get(key);
        if (block == null) {
            return false;
        }
        return registerMetadataRule(block, metadata, rule);
    }

    public static boolean registerMetadataRule(String identifier, int metadata, BlockMiningRule rule) {
        if (identifier == null || rule == null) {
            return false;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(identifier);
        if (normalized == null) {
            return false;
        }
        return registerMetadataRule(new ResourceLocation(normalized), metadata, rule);
    }

    public static synchronized boolean registerMetadataRule(Block block, int metadata, BlockMiningRule rule) {
        if (block == null || rule == null) {
            return false;
        }

        if (!byBlock.containsKey(block)) {
            register(block, BlockMiningRule.VANILLA);
        }

        Map<Integer, BlockMiningRule> metadataRules = byBlockMetadata.get(block);
        if (metadataRules == null) {
            metadataRules = new HashMap<Integer, BlockMiningRule>();
            byBlockMetadata.put(block, metadataRules);
        }
        metadataRules.put(Integer.valueOf(metadata & 15), rule);
        return true;
    }

    public static synchronized BlockMiningRule get(ResourceLocation key) {
        if (key == null) {
            return BlockMiningRule.VANILLA;
        }

        Block block = BlockRegistry.get(key);
        if (block != null) {
            BlockMiningRule rule = byBlock.get(block);
            if (rule != null) {
                return rule;
            }
        }

        BlockMiningRule direct = byKey.get(key);
        return direct != null ? direct : BlockMiningRule.VANILLA;
    }

    public static BlockMiningRule get(String identifier) {
        if (identifier == null) {
            return BlockMiningRule.VANILLA;
        }
        String normalized = BlockRegistry.normalizeInputIdentifier(identifier);
        if (normalized == null) {
            return BlockMiningRule.VANILLA;
        }
        return get(new ResourceLocation(normalized));
    }

    public static synchronized BlockMiningRule get(Block block) {
        if (block == null) {
            return BlockMiningRule.VANILLA;
        }
        BlockMiningRule rule = byBlock.get(block);
        return rule != null ? rule : BlockMiningRule.VANILLA;
    }

    public static BlockMiningRule get(IBlockAccess access, int x, int y, int z) {
        if (access == null) {
            return BlockMiningRule.VANILLA;
        }

        int blockId = access.getTypeId(x, y, z);
        if (blockId <= 0 || blockId >= Block.byId.length) {
            return BlockMiningRule.VANILLA;
        }

        Block block = Block.byId[blockId];
        if (block == null) {
            return BlockMiningRule.VANILLA;
        }

        int metadata = access.getData(x, y, z);
        BlockMiningRule metadataRule = getMetadataRule(block, metadata);
        if (metadataRule != null) {
            return metadataRule;
        }

        return get(block);
    }

    public static BlockMiningRule get(World world, int x, int y, int z) {
        return get((IBlockAccess) world, x, y, z);
    }

    public static float getBreakProgressPerTick(EntityHuman player, World world, int x, int y, int z) {
        Block block = blockFromWorld(world, x, y, z);
        if (block == null) {
            return 0.0F;
        }
        BlockMiningRule rule = get(world, x, y, z);
        return computeBreakProgressPerTick(player, block, rule);
    }

    public static float getBreakProgressPerTick(EntityHuman player, Block block) {
        return computeBreakProgressPerTick(player, block, get(block));
    }

    public static boolean canHarvestForDrops(EntityHuman player, World world, int x, int y, int z) {
        Block block = blockFromWorld(world, x, y, z);
        if (player == null || block == null) {
            return false;
        }

        BlockMiningRule rule = get(world, x, y, z);
        if (rule.allowsDropsWithoutPreferredTool()) {
            return true;
        }
        if (!rule.isEnforcePreferredTool() || rule.getPreferredTool() == MiningToolType.NONE) {
            return player.b(block);
        }

        return hasPreferredTool(player, rule.getPreferredTool());
    }

    public static synchronized Set<ResourceLocation> keys() {
        return Collections.unmodifiableSet(byKey.keySet());
    }

    public static synchronized int size() {
        return byKey.size();
    }

    static synchronized int bootstrapDefaults() {
        int registered = 0;
        for (int i = 0; i < Block.byId.length; i++) {
            Block block = Block.byId[i];
            if (block == null) {
                continue;
            }
            if (register(block, BlockMiningRule.VANILLA)) {
                registered++;
            }
        }
        return registered;
    }

    static synchronized boolean hasExplicitRule(Block block) {
        return block != null && byBlock.containsKey(block);
    }

    private static Block blockFromWorld(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        int blockId = world.getTypeId(x, y, z);
        if (blockId <= 0 || blockId >= Block.byId.length) {
            return null;
        }
        return Block.byId[blockId];
    }

    private static synchronized BlockMiningRule getMetadataRule(Block block, int metadata) {
        Map<Integer, BlockMiningRule> metadataRules = byBlockMetadata.get(block);
        if (metadataRules == null) {
            return null;
        }
        return metadataRules.get(Integer.valueOf(metadata & 15));
    }

    private static float computeBreakProgressPerTick(EntityHuman player, Block block, BlockMiningRule rule) {
        if (player == null || block == null) {
            return 0.0F;
        }

        if (player.gameMode == 1) {
            return 1.0F;
        }

        if (block.j() < 0.0F) {
            return 0.0F;
        }

        boolean preferredMatch = hasPreferredTool(player, rule.getPreferredTool());
        boolean emptyHandOrNonTool = getHeldToolType(player) == MiningToolType.NONE;
        boolean canHarvest = player.b(block);
        if (rule.isEnforcePreferredTool() && rule.getPreferredTool() != MiningToolType.NONE) {
            canHarvest = preferredMatch || (rule.allowsDropsWithoutPreferredTool() && emptyHandOrNonTool);
        }

        float effectivePlayerStrength = player.a(block);
        if (preferredMatch) {
            float preferredToolStrength = getPreferredToolStrength(player, rule.getPreferredTool());
            if (preferredToolStrength > effectivePlayerStrength) {
                effectivePlayerStrength = preferredToolStrength;
            }
        }

        float base = !canHarvest
                ? 1.0F / block.j() / 100.0F
                : effectivePlayerStrength / block.j() / 30.0F;

        float multiplier = 1.0F;
        if (rule.getPreferredTool() == MiningToolType.NONE || preferredMatch) {
            multiplier = rule.getSpeedMultiplier();
        }

        return base * multiplier;
    }

    private static boolean hasPreferredTool(EntityHuman player, MiningToolType preferredTool) {
        if (preferredTool == null || preferredTool == MiningToolType.NONE) {
            return true;
        }
        return getHeldToolType(player) == preferredTool;
    }

    private static float getPreferredToolStrength(EntityHuman player, MiningToolType preferredTool) {
        if (player == null) {
            return 0.0F;
        }
        Block referenceBlock = getPreferredToolReferenceBlock(preferredTool);
        if (referenceBlock == null) {
            return 0.0F;
        }
        return player.a(referenceBlock);
    }

    private static Block getPreferredToolReferenceBlock(MiningToolType preferredTool) {
        if (preferredTool == null) {
            return null;
        }

        switch (preferredTool) {
            case PICKAXE:
                return Block.COBBLESTONE;
            case AXE:
                return Block.WOOD;
            case SHOVEL:
                return Block.DIRT;
            case HOE:
                return Block.SOIL;
            case SWORD:
                return Block.WEB;
            case SHEARS:
                return Block.LEAVES;
            case NONE:
            default:
                return null;
        }
    }

    private static MiningToolType getHeldToolType(EntityHuman player) {
        if (player == null || player.inventory == null) {
            return MiningToolType.NONE;
        }

        ItemStack stack = player.G();
        if (stack == null) {
            return MiningToolType.NONE;
        }

        Item item = stack.getItem();
        if (item instanceof ItemPickaxe) {
            return MiningToolType.PICKAXE;
        }
        if (item instanceof ItemAxe) {
            return MiningToolType.AXE;
        }
        if (item instanceof ItemSpade) {
            return MiningToolType.SHOVEL;
        }
        if (item instanceof ItemHoe) {
            return MiningToolType.HOE;
        }
        if (item instanceof ItemSword) {
            return MiningToolType.SWORD;
        }
        if (item instanceof ItemShears) {
            return MiningToolType.SHEARS;
        }

        return MiningToolType.NONE;
    }
}

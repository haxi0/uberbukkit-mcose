package net.minecraft.server.registry;

import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.util.ResourceLocation;

import java.util.Random;

/**
 * Represents a single entry in a loot pool.
 * Each entry has an item, weight, and count range.
 */
public class LootEntry {
    private final ResourceLocation itemId;
    private final int weight;
    private final int minCount;
    private final int maxCount;
    private final int metadata;
    
    /**
     * Creates a loot entry with a single item.
     * @param itemId The registry name of the item
     * @param weight The weight for random selection (higher = more likely)
     */
    public LootEntry(ResourceLocation itemId, int weight) {
        this(itemId, weight, 1, 1, 0);
    }
    
    /**
     * Creates a loot entry with a count range.
     * @param itemId The registry name of the item
     * @param weight The weight for random selection
     * @param minCount Minimum stack size
     * @param maxCount Maximum stack size
     */
    public LootEntry(ResourceLocation itemId, int weight, int minCount, int maxCount) {
        this(itemId, weight, minCount, maxCount, 0);
    }
    
    /**
     * Creates a loot entry with metadata (for items with subtypes).
     * @param itemId The registry name of the item
     * @param weight The weight for random selection
     * @param minCount Minimum stack size
     * @param maxCount Maximum stack size
     * @param metadata The item metadata/damage value
     */
    public LootEntry(ResourceLocation itemId, int weight, int minCount, int maxCount, int metadata) {
        this.itemId = itemId;
        this.weight = weight;
        this.minCount = minCount;
        this.maxCount = maxCount;
        this.metadata = metadata;
    }
    
    public ResourceLocation getItemId() {
        return itemId;
    }
    
    public int getWeight() {
        return weight;
    }
    
    public int getMinCount() {
        return minCount;
    }
    
    public int getMaxCount() {
        return maxCount;
    }
    
    public int getMetadata() {
        return metadata;
    }
    
    /**
     * Generates an ItemStack from this entry.
     * @param random The random source
     * @return The generated ItemStack, or null if the item doesn't exist
     */
    public ItemStack generateStack(Random random) {
        Item item = Registries.ITEM.get(itemId);
        if (item == null) {
            // MCOSE: Don't silently fail for missing items as this leads to empty chest reports.
            // We skip "air" as it's a valid intentional "nothing" entry in many loot tables.
            if (!itemId.getPath().equals("air")) {
                System.err.println("[LootEntry] Failed to resolve item: " + itemId + ". Skipping entry.");
            }
            return null;
        }
        
        int count = minCount;
        if (maxCount > minCount) {
            count = minCount + random.nextInt(maxCount - minCount + 1);
        }
        
        return new ItemStack(item, count, metadata);
    }
    
    // Builder pattern for convenience
    public static Builder builder(String itemName) {
        return new Builder(new ResourceLocation("minecraft", itemName));
    }
    
    public static Builder builder(ResourceLocation itemId) {
        return new Builder(itemId);
    }
    
    public static class Builder {
        private final ResourceLocation itemId;
        private int weight = 1;
        private int minCount = 1;
        private int maxCount = 1;
        private int metadata = 0;
        
        public Builder(ResourceLocation itemId) {
            this.itemId = itemId;
        }
        
        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }
        
        public Builder count(int count) {
            this.minCount = count;
            this.maxCount = count;
            return this;
        }
        
        public Builder count(int min, int max) {
            this.minCount = min;
            this.maxCount = max;
            return this;
        }
        
        public Builder metadata(int metadata) {
            this.metadata = metadata;
            return this;
        }
        
        public LootEntry build() {
            return new LootEntry(itemId, weight, minCount, maxCount, metadata);
        }
    }
}


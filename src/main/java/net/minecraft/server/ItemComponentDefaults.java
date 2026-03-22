package net.minecraft.server;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Default component prototypes derived from legacy item properties.
 */
public final class ItemComponentDefaults {
    private static final Map<Item, DataComponentMap> CACHE = new IdentityHashMap<Item, DataComponentMap>();

    private ItemComponentDefaults() {}

    public static synchronized DataComponentMap defaultsFor(Item item) {
        if (item == null) {
            return DataComponentMap.EMPTY;
        }

        DataComponentMap cached = CACHE.get(item);
        if (cached != null) {
            return cached;
        }

        DataComponentMap.Builder builder = DataComponentMap.builder();
        builder.set(DataComponents.MAX_STACK_SIZE, Integer.valueOf(item.getMaxStackSize()));
        if (item.e() > 0) {
            builder.set(DataComponents.MAX_DAMAGE, Integer.valueOf(item.e()));
        }

        DataComponentMap defaults = builder.build();
        CACHE.put(item, defaults);
        return defaults;
    }

    public static synchronized void clear() {
        CACHE.clear();
    }
}

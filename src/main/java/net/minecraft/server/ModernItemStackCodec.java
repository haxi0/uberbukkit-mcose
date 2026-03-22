package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;

/**
 * Modern save codec: namespaced item identity + component patch payload.
 */
public final class ModernItemStackCodec {
    private static final int FORMAT_VERSION = 2;
    private static final String KEY_FORMAT = "mcose_stack_format";
    private static final String KEY_ITEM = "item";
    private static final String KEY_COUNT = "count";
    private static final String KEY_COMPONENTS = "components";

    private ModernItemStackCodec() {}

    public static boolean isModernFormat(NBTTagCompound nbt) {
        return nbt != null && nbt.hasKey(KEY_ITEM);
    }

    public static NBTTagCompound write(ItemStack stack, NBTTagCompound out) {
        if (stack == null || out == null) {
            return out;
        }

        Holder<Item> holder = stack.getItemHolder();
        ResourceLocation key = holder == null ? null : holder.key();
        if (key == null) {
            Item item = stack.getItem();
            if (item != null) {
                key = ItemRegistry.getKey(item);
            }
        }
        if (key == null) {
            String bridged = LegacyIdBridge.itemKeyFromId(stack.id);
            if (bridged != null) {
                key = new ResourceLocation(bridged);
            }
        }

        out.a(KEY_FORMAT, FORMAT_VERSION);
        if (key != null) {
            out.setString(KEY_ITEM, key.toString());
        }
        out.a(KEY_COUNT, stack.count);

        DataComponentPatch patch = stack.getComponents();
        if (patch != null && !patch.isEmpty()) {
            out.a(KEY_COMPONENTS, patch.toNbt());
        }

        // Keep legacy shadow fields so explicit McRegion 1 conversion remains possible.
        LegacyItemStackCodec.writeLegacyShadow(stack, out);
        return out;
    }

    public static ItemStack read(NBTTagCompound in) {
        if (in == null) {
            return null;
        }

        String keyString = in.getString(KEY_ITEM);
        ResourceLocation key = null;
        if (keyString != null && keyString.length() > 0) {
            try {
                key = new ResourceLocation(keyString);
            } catch (Throwable ignored) {}
        }

        Item item = null;
        int legacyId = -1;
        if (key != null) {
            item = ItemRegistry.get(key);
            if (item != null) {
                legacyId = ItemRegistry.getLegacyId(item);
            }
            if (legacyId < 0) {
                Integer bridged = LegacyIdBridge.itemIdFromKey(key.toString());
                if (bridged != null) {
                    legacyId = bridged.intValue();
                    item = ItemRegistry.getByLegacyId(legacyId);
                }
            }
        }

        if (legacyId < 0 && in.hasKey("id")) {
            legacyId = in.d("id");
            item = ItemRegistry.getByLegacyId(legacyId);
        }

        if (legacyId < 0 || legacyId >= Item.byId.length || Item.byId[legacyId] == null) {
            System.err.println("[ModernItemStackCodec] Could not resolve legacy fallback ID for item key: " + keyString);
            return null;
        }

        int count = in.hasKey(KEY_COUNT) ? in.e(KEY_COUNT) : in.c("Count");
        int damage = in.hasKey("Damage") ? in.d("Damage") : 0;

        ItemStack stack = new ItemStack(legacyId, count, damage);

        if (item != null) {
            Holder<Item> holder = ItemRegistry.getHolder(item);
            if (holder != null) {
                stack.setItemHolder(holder);
            }
        } else if (key != null) {
            stack.setItemHolder(Holder.direct(key, null, legacyId));
        }

        if (in.hasKey(KEY_COMPONENTS)) {
            DataComponentPatch patch = DataComponentPatch.fromNbt(in.k(KEY_COMPONENTS));
            stack.applyComponents(patch);
        } else if (in.hasKey("tag")) {
            stack.setTag(in.k("tag"));
        }

        if (in.hasKey("tag") && stack.getTag() == null) {
            stack.setTag(in.k("tag"));
        }

        if (!in.hasKey(KEY_COMPONENTS) && in.hasKey("Damage") && (stack.getComponents() == null || stack.getComponents().get(DataComponents.DAMAGE) == null)) {
            stack.setItemDamage(in.d("Damage"));
        }

        return stack;
    }
}

package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;

import java.util.HashSet;
import java.util.Set;

/**
 * Legacy packet/nbt bridge codec: legacy id/meta/tag <-> holder/components.
 */
public final class LegacyItemStackCodec {
    private static final Set<String> LOGGED_UNMAPPABLE_VANILLA = new HashSet<String>();

    private LegacyItemStackCodec() {}

    public static ItemStack decode(int legacyId, int count, int damage, NBTTagCompound legacyTag) {
        if (legacyId < 0) {
            return null;
        }
        ItemStack stack = new ItemStack(legacyId, count, damage);
        if (legacyTag != null) {
            stack.setTag(legacyTag);
        }
        return stack;
    }

    public static LegacyStackData encode(ItemStack stack, boolean strictVanilla) {
        if (stack == null) {
            return null;
        }

        int legacyId = resolveLegacyId(stack);
        if (legacyId < 0) {
            if (strictVanilla) {
                logUnmappableVanilla(stack);
            }
            return null;
        }

        return new LegacyStackData(
                legacyId,
                stack.count,
                stack.getItemDamage(),
                stack.hasTag() ? stack.getTag() : null);
    }

    public static ItemStack readFromLegacyNbt(NBTTagCompound nbt) {
        if (nbt == null) {
            return null;
        }

        int count = nbt.hasKey("Count") ? nbt.c("Count") : nbt.e("count");
        int damage = nbt.hasKey("Damage") ? nbt.d("Damage") : 0;

        int legacyId = -1;
        if (nbt.hasKey("id")) {
            legacyId = nbt.d("id");
        }

        if (legacyId < 0 && nbt.hasKey("name")) {
            legacyId = resolveLegacyIdFromKey(nbt.getString("name"));
        }

        if (legacyId < 0) {
            return null;
        }

        NBTTagCompound legacyTag = nbt.hasKey("tag") ? nbt.k("tag") : null;
        return decode(legacyId, count, damage, legacyTag);
    }

    public static void writeLegacyShadow(ItemStack stack, NBTTagCompound out) {
        if (stack == null || out == null) {
            return;
        }
        LegacyStackData encoded = encode(stack, false);
        if (encoded == null) {
            return;
        }

        out.a("id", (short)encoded.legacyId);
        out.a("Count", (byte)encoded.count);
        out.a("Damage", (short)encoded.damage);

        ResourceLocation key = null;
        Holder<Item> holder = stack.getItemHolder();
        if (holder != null) {
            key = holder.key();
        }
        if (key == null) {
            Item item = stack.getItem();
            if (item != null) {
                key = ItemRegistry.getKey(item);
            }
        }
        if (key != null) {
            out.setString("name", key.toString());
        }

        if (encoded.tag != null) {
            out.a("tag", encoded.tag);
        }
    }

    private static int resolveLegacyId(ItemStack stack) {
        int legacyId = stack.id;
        if (isValidLegacyId(legacyId)) {
            return legacyId;
        }

        Holder<Item> holder = stack.getItemHolder();
        if (holder != null && holder.value() != null) {
            legacyId = ItemRegistry.getLegacyId(holder.value());
            if (isValidLegacyId(legacyId)) {
                return legacyId;
            }
        }

        if (holder != null && holder.key() != null) {
            Integer bridged = LegacyIdBridge.itemIdFromKey(holder.key().toString());
            if (bridged != null && isValidLegacyId(bridged.intValue())) {
                return bridged.intValue();
            }
        }

        return -1;
    }

    private static int resolveLegacyIdFromKey(String key) {
        if (key == null || key.length() == 0) {
            return -1;
        }

        try {
            Item item = ItemRegistry.get(new ResourceLocation(key));
            if (item != null) {
                int legacyId = ItemRegistry.getLegacyId(item);
                if (isValidLegacyId(legacyId)) {
                    return legacyId;
                }
            }
        } catch (Throwable ignored) {}

        Integer bridged = LegacyIdBridge.itemIdFromKey(key);
        return bridged == null ? -1 : bridged.intValue();
    }

    private static boolean isValidLegacyId(int id) {
        return id >= 0 && id < Item.byId.length && Item.byId[id] != null;
    }

    private static void logUnmappableVanilla(ItemStack stack) {
        String key = "legacy:" + stack.id;
        Holder<Item> holder = null;
        try {
            holder = stack.getItemHolder();
            if (holder != null && holder.key() != null) {
                key = holder.key().toString();
            }
        } catch (Throwable ignored) {}

        synchronized (LOGGED_UNMAPPABLE_VANILLA) {
            if (LOGGED_UNMAPPABLE_VANILLA.contains(key)) {
                return;
            }
            LOGGED_UNMAPPABLE_VANILLA.add(key);
        }

        System.err.println("[LegacyItemStackCodec] Unmappable item stack for vanilla connection; dropping item payload: " + key);
    }

    public static final class LegacyStackData {
        public final int legacyId;
        public final int count;
        public final int damage;
        public final NBTTagCompound tag;

        LegacyStackData(int legacyId, int count, int damage, NBTTagCompound tag) {
            this.legacyId = legacyId;
            this.count = count;
            this.damage = damage;
            this.tag = tag;
        }
    }
}

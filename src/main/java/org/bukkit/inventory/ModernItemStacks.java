package org.bukkit.inventory;

import net.minecraft.server.DataComponentPatch;
import net.minecraft.server.Holder;
import net.minecraft.server.Item;
import net.minecraft.server.ItemStack;
import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;

/**
 * Additive utility bridge for modern item key/component access from plugins.
 */
public final class ModernItemStacks {
    private ModernItemStacks() {}

    public static String getItemKey(org.bukkit.inventory.ItemStack stack) {
        ItemStack nms = CraftItemStack.asNMSCopy(stack);
        if (nms == null) {
            return null;
        }

        Holder<Item> holder = nms.getItemHolder();
        if (holder != null && holder.key() != null) {
            return holder.key().toString();
        }

        Item item = nms.getItem();
        if (item != null) {
            ResourceLocation key = ItemRegistry.getKey(item);
            if (key != null) {
                return key.toString();
            }
        }

        return LegacyIdBridge.itemKeyFromId(nms.id);
    }

    public static net.minecraft.server.NBTTagCompound getComponentPatch(org.bukkit.inventory.ItemStack stack) {
        ItemStack nms = CraftItemStack.asNMSCopy(stack);
        if (nms == null) {
            return null;
        }
        DataComponentPatch patch = nms.getComponents();
        return patch == null ? null : patch.toNbt();
    }

    public static void applyComponentPatch(org.bukkit.inventory.ItemStack stack, net.minecraft.server.NBTTagCompound patchTag) {
        if (stack == null || patchTag == null) {
            return;
        }

        ItemStack nms = CraftItemStack.asNMSCopy(stack);
        if (nms == null) {
            return;
        }

        nms.applyComponents(DataComponentPatch.fromNbt(patchTag));

        stack.setTypeId(nms.id);
        stack.setAmount(nms.count);
        stack.setDurability((short) nms.damage);
    }

    public static org.bukkit.inventory.ItemStack create(String itemKey, int amount) {
        if (itemKey == null || itemKey.length() == 0 || amount <= 0) {
            return new org.bukkit.inventory.ItemStack(Material.AIR, 0);
        }

        int id = -1;
        try {
            Item item = ItemRegistry.get(new ResourceLocation(itemKey));
            if (item != null) {
                id = ItemRegistry.getLegacyId(item);
            }
        } catch (Throwable ignored) {}

        if (id < 0) {
            Integer bridged = LegacyIdBridge.itemIdFromKey(itemKey);
            if (bridged != null) {
                id = bridged.intValue();
            }
        }

        if (id < 0 || id >= Item.byId.length || Item.byId[id] == null) {
            return new org.bukkit.inventory.ItemStack(Material.AIR, 0);
        }

        return new CraftItemStack(new ItemStack(id, amount, 0));
    }
}

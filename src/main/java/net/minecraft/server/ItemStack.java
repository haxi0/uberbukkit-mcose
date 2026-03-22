package net.minecraft.server;

import net.minecraft.server.registry.ItemRegistry;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemDamageEvent;

public final class ItemStack {

    public int count;
    public int b;
    public int id;
    public int damage; // CraftBukkit - private -> public
    /** NBT tag compound for extra item data (books, enchantments, etc.) */
    public NBTTagCompound tag;

    /** 1.21-style runtime item reference */
    private Holder<Item> itemHolder;
    /** 1.21-style component delta */
    private DataComponentPatch componentPatch = DataComponentPatch.empty();
    /** Resolved default+patch component view */
    private PatchedDataComponentMap patchedComponents = new PatchedDataComponentMap(DataComponentMap.EMPTY, DataComponentPatch.empty());
    /** Legacy-to-modern cache coherence markers */
    private int cachedLegacyId = Integer.MIN_VALUE;
    private int cachedLegacyDamage = Integer.MIN_VALUE;
    private NBTTagCompound cachedLegacyTag = null;

    public ItemStack(Block block) {
        this(block, 1);
    }

    public ItemStack(Block block, int i) {
        this(block.id, i, 0);
    }

    public ItemStack(Block block, int i, int j) {
        this(block.id, i, j);
    }

    public ItemStack(Item item) {
        this(item.id, 1, 0);
    }

    public ItemStack(Item item, int i) {
        this(item.id, i, 0);
    }

    public ItemStack(Item item, int i, int j) {
        this(item.id, i, j);
    }

    public ItemStack(int i, int j, int k) {
        this.count = 0;
        this.id = i;
        this.count = j;
        this.damage = k;
        normalizeLegacyInventoryItemStates();
        invalidateModernState();
    }

    public ItemStack(NBTTagCompound nbttagcompound) {
        this.count = 0;
        this.b(nbttagcompound);
    }

    public ItemStack a(int i) {
        this.count -= i;
        ItemStack newStack = new ItemStack(this.id, i, this.damage);
        if (this.tag != null) {
            newStack.tag = this.tag;
        }
        newStack.setItemHolder(this.getItemHolder());
        newStack.applyComponents(this.getComponents());
        return newStack;
    }

    public Item getItem() {
        if (this.id < 0 || this.id >= Item.byId.length) {
            return null;
        }
        return Item.byId[this.id];
    }

    public boolean placeItem(EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        boolean flag = this.getItem().a(this, entityhuman, world, i, j, k, l);

        if (flag) {
            entityhuman.a(StatisticList.E[this.id], 1);
        }

        return flag;
    }

    public float a(Block block) {
        return this.getItem().a(this, block);
    }

    public ItemStack a(World world, EntityHuman entityhuman) {
        return this.getItem().a(this, world, entityhuman);
    }

    public NBTTagCompound a(NBTTagCompound nbttagcompound) {
        ensureModernState();
        return ModernItemStackCodec.write(this, nbttagcompound);
    }

    public void b(NBTTagCompound nbttagcompound) {
        ItemStack decoded = ModernItemStackCodec.isModernFormat(nbttagcompound)
                ? ModernItemStackCodec.read(nbttagcompound)
                : LegacyItemStackCodec.readFromLegacyNbt(nbttagcompound);

        if (decoded == null) {
            this.id = 0;
            this.count = 0;
            this.damage = 0;
            this.tag = null;
            this.itemHolder = null;
            this.componentPatch = DataComponentPatch.empty();
            this.patchedComponents = new PatchedDataComponentMap(DataComponentMap.EMPTY, this.componentPatch);
            invalidateModernState();
            return;
        }

        this.count = decoded.count;
        this.id = decoded.id;
        this.damage = decoded.damage;
        this.tag = decoded.tag;
        this.itemHolder = decoded.itemHolder;
        this.componentPatch = decoded.componentPatch == null ? DataComponentPatch.empty() : decoded.componentPatch.copy();
        Item item = this.getItem();
        DataComponentMap defaults = item == null ? DataComponentMap.EMPTY : ItemComponentDefaults.defaultsFor(item);
        this.patchedComponents = new PatchedDataComponentMap(defaults, this.componentPatch);
        normalizeLegacyInventoryItemStates();
        markModernStateFresh();
    }

    private void normalizeLegacyInventoryItemStates() {
        // Unlit redstone torch (id 75) is a block state, not a legal inventory item.
        if (Block.REDSTONE_TORCH_OFF != null && Block.REDSTONE_TORCH_ON != null && this.id == Block.REDSTONE_TORCH_OFF.id) {
            this.id = Block.REDSTONE_TORCH_ON.id;
        }
    }

    /**
     * Returns true if this item stack has an NBT tag compound.
     */
    public boolean hasTag() {
        return this.tag != null;
    }

    public boolean hasTagCompound() {
        return this.hasTag();
    }

    /**
     * Gets the NBT tag compound for this item stack.
     */
    public NBTTagCompound getTag() {
        return this.tag;
    }

    public NBTTagCompound getTagCompound() {
        return this.getTag();
    }

    /**
     * Sets the NBT tag compound for this item stack.
     */
    public void setTag(NBTTagCompound nbt) {
        this.tag = nbt;
        invalidateModernState();
    }

    public void setTagCompound(NBTTagCompound nbt) {
        this.setTag(nbt);
    }

    public int getMaxStackSize() {
        return this.getItem().getMaxStackSize();
    }

    public boolean isStackable() {
        return this.getMaxStackSize() > 1 && (!this.d() || !this.f());
    }

    public boolean d() {
        return Item.byId[this.id].e() > 0;
    }

    public boolean usesData() {
        return Item.byId[this.id].d();
    }

    public boolean f() {
        return this.d() && this.damage > 0;
    }

    public int g() {
        return this.damage;
    }

    public int getData() {
        return this.damage;
    }

    public int getItemDamage() {
        return this.damage;
    }

    public void b(int i) {
        this.damage = i;
        invalidateModernState();
    }

    public void setItemDamage(int i) {
        this.b(i);
    }

    public int i() {
        return Item.byId[this.id].e();
    }

    @SuppressWarnings("deprecation")
    public void damage(int i, Entity entity) {
        if (this.d()) {
            if (entity instanceof EntityPlayer) {
                PlayerItemDamageEvent event = new PlayerItemDamageEvent((Player) entity.getBukkitEntity(), new CraftItemStack(this), i);
                event.getPlayer().getServer().getPluginManager().callEvent(event);
                if (i != event.getDamage() || event.isCancelled()) event.getPlayer().updateInventory();
                if (event.isCancelled()) return;
                i = event.getDamage();
            }
            this.damage += i;
            if (this.damage > this.i()) {
                if (entity instanceof EntityHuman) {
                    ((EntityHuman) entity).a(StatisticList.F[this.id], 1);
                }

                --this.count;
                if (this.count < 0) {
                    this.count = 0;
                }

                this.damage = 0;
            }
            invalidateModernState();
        }
    }

    public void a(EntityLiving entityliving, EntityHuman entityhuman) {
        boolean flag = Item.byId[this.id].a(this, entityliving, (EntityLiving) entityhuman);

        if (flag) {
            entityhuman.a(StatisticList.E[this.id], 1);
        }
    }

    public void a(int i, int j, int k, int l, EntityHuman entityhuman) {
        boolean flag = Item.byId[this.id].a(this, i, j, k, l, entityhuman);

        if (flag) {
            entityhuman.a(StatisticList.E[this.id], 1);
        }
    }

    public int a(Entity entity) {
        return Item.byId[this.id].a(entity);
    }

    public boolean b(Block block) {
        return Item.byId[this.id].a(block);
    }

    public void a(EntityHuman entityhuman) {
    }

    public void a(EntityLiving entityliving) {
        Item.byId[this.id].a(this, entityliving);
    }

    public ItemStack cloneItemStack() {
        ItemStack clone = new ItemStack(this.id, this.count, this.damage);
        clone.tag = this.tag;
        clone.setItemHolder(this.getItemHolder());
        clone.applyComponents(this.getComponents());
        return clone;
    }

    public static boolean equals(ItemStack itemstack, ItemStack itemstack1) {
        return itemstack == null && itemstack1 == null ? true : (itemstack != null && itemstack1 != null ? itemstack.d(itemstack1) : false);
    }

    private boolean d(ItemStack itemstack) {
        if (this.count != itemstack.count) return false;
        if (this.id != itemstack.id) return false;
        if (this.damage != itemstack.damage) return false;
        if (this.tag == null && itemstack.tag == null) return true;
        if (this.tag == null || itemstack.tag == null) return false;
        return this.tag.equals(itemstack.tag);
    }

    public boolean doMaterialsMatch(ItemStack itemstack) {
        return this.id == itemstack.id && this.damage == itemstack.damage;
    }

    public static ItemStack b(ItemStack itemstack) {
        return itemstack == null ? null : itemstack.cloneItemStack();
    }

    public String toString() {
        return this.count + "x" + Item.byId[this.id].a() + "@" + this.damage;
    }

    public void a(World world, Entity entity, int i, boolean flag) {
        if (this.b > 0) {
            --this.b;
        }

        Item.byId[this.id].a(this, world, entity, i, flag);
    }

    public void b(World world, EntityHuman entityhuman) {
        entityhuman.a(StatisticList.D[this.id], this.count);
        Item.byId[this.id].c(this, world, entityhuman);
    }

    public boolean c(ItemStack itemstack) {
        return this.id == itemstack.id && this.count == itemstack.count && this.damage == itemstack.damage;
    }

    public Holder<Item> getItemHolder() {
        ensureModernState();
        return this.itemHolder;
    }

    public void setItemHolder(Holder<Item> holder) {
        if (holder == null) {
            this.itemHolder = null;
            invalidateModernState();
            return;
        }

        this.itemHolder = holder;
        Item heldItem = holder.value();
        if (heldItem != null) {
            this.id = ItemRegistry.getLegacyId(heldItem);
        } else if (holder.key() != null) {
            Integer bridged = LegacyIdBridge.itemIdFromKey(holder.key().toString());
            if (bridged != null) {
                this.id = bridged.intValue();
            }
        }
        normalizeLegacyInventoryItemStates();

        // Keep component state in sync with legacy fields while preserving explicit holder identity.
        rebuildModernStateFromLegacy();
        this.itemHolder = holder;
        markModernStateFresh();
    }

    public DataComponentPatch getComponents() {
        ensureModernState();
        return this.componentPatch;
    }

    public PatchedDataComponentMap getPatchedComponents() {
        ensureModernState();
        return this.patchedComponents;
    }

    public void applyComponents(DataComponentPatch patch) {
        if (patch == null || patch.isEmpty()) {
            return;
        }

        ensureModernState();
        this.componentPatch = DataComponentPatch.merge(this.componentPatch, patch);
        Item item = this.getItem();
        DataComponentMap defaults = item == null ? DataComponentMap.EMPTY : ItemComponentDefaults.defaultsFor(item);
        this.patchedComponents = new PatchedDataComponentMap(defaults, this.componentPatch);
        projectLegacyStateFromComponents();
        normalizeLegacyInventoryItemStates();
        markModernStateFresh();
    }

    public NBTTagCompound save(NBTTagCompound nbt) {
        return this.a(nbt);
    }

    public static ItemStack parse(NBTTagCompound nbt) {
        if (nbt == null) {
            return null;
        }
        return ModernItemStackCodec.isModernFormat(nbt) ? ModernItemStackCodec.read(nbt) : LegacyItemStackCodec.readFromLegacyNbt(nbt);
    }

    private void ensureModernState() {
        if (this.cachedLegacyId == this.id
                && this.cachedLegacyDamage == this.damage
                && this.cachedLegacyTag == this.tag
                && this.itemHolder != null
                && this.componentPatch != null
                && this.patchedComponents != null) {
            return;
        }
        rebuildModernStateFromLegacy();
    }

    private void rebuildModernStateFromLegacy() {
        Item item = this.getItem();
        Holder<Item> holder = item == null ? null : ItemRegistry.getHolder(item);
        if (holder == null) {
            ResourceLocation key = null;
            if (item != null) {
                key = ItemRegistry.getKey(item);
            }
            if (key == null) {
                String bridged = LegacyIdBridge.itemKeyFromId(this.id);
                if (bridged != null) {
                    try {
                        key = new ResourceLocation(bridged);
                    } catch (Throwable ignored) {}
                }
            }
            if (key == null) {
                key = new ResourceLocation("legacy", "item_" + this.id);
            }
            holder = Holder.direct(key, item, this.id);
        }
        this.itemHolder = holder;

        DataComponentPatch.Builder patchBuilder = DataComponentPatch.builder();
        if (this.damage != 0) {
            patchBuilder.set(DataComponents.DAMAGE, Integer.valueOf(this.damage));
        }
        if (this.tag != null) {
            patchBuilder.set(DataComponents.CUSTOM_DATA, this.tag);
            if (this.tag.hasKey("display")) {
                NBTTagCompound display = this.tag.k("display");
                if (display != null && display.hasKey("Name")) {
                    patchBuilder.set(DataComponents.CUSTOM_NAME, display.getString("Name"));
                }
            }
            if (this.tag.hasKey("ench")) {
                patchBuilder.set(DataComponents.ENCHANTMENTS, this.tag.l("ench"));
            }
            if (this.tag.hasKey("Items")) {
                patchBuilder.set(DataComponents.CONTAINER, this.tag.l("Items"));
            }
        }

        this.componentPatch = patchBuilder.build();
        DataComponentMap defaults = item == null ? DataComponentMap.EMPTY : ItemComponentDefaults.defaultsFor(item);
        this.patchedComponents = new PatchedDataComponentMap(defaults, this.componentPatch);
        markModernStateFresh();
    }

    private void projectLegacyStateFromComponents() {
        if (this.itemHolder != null && this.itemHolder.value() != null) {
            this.id = ItemRegistry.getLegacyId(this.itemHolder.value());
        } else if (this.itemHolder != null && this.itemHolder.key() != null) {
            Integer bridged = LegacyIdBridge.itemIdFromKey(this.itemHolder.key().toString());
            if (bridged != null) {
                this.id = bridged.intValue();
            }
        }

        Integer patchedDamage = this.patchedComponents.get(DataComponents.DAMAGE);
        this.damage = patchedDamage == null ? 0 : patchedDamage.intValue();

        this.tag = this.patchedComponents.get(DataComponents.CUSTOM_DATA);

        String customName = this.patchedComponents.get(DataComponents.CUSTOM_NAME);
        if (customName != null && customName.length() > 0) {
            if (this.tag == null) {
                this.tag = new NBTTagCompound();
            }
            NBTTagCompound display = this.tag.hasKey("display") ? this.tag.k("display") : new NBTTagCompound();
            display.setString("Name", customName);
            this.tag.a("display", display);
        }

        NBTTagList enchantments = this.patchedComponents.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            if (this.tag == null) {
                this.tag = new NBTTagCompound();
            }
            this.tag.a("ench", (NBTBase)enchantments);
        }

        NBTTagList container = this.patchedComponents.get(DataComponents.CONTAINER);
        if (container != null) {
            if (this.tag == null) {
                this.tag = new NBTTagCompound();
            }
            this.tag.a("Items", (NBTBase)container);
        }
    }

    private void invalidateModernState() {
        this.cachedLegacyId = Integer.MIN_VALUE;
        this.cachedLegacyDamage = Integer.MIN_VALUE;
        this.cachedLegacyTag = null;
    }

    private void markModernStateFresh() {
        this.cachedLegacyId = this.id;
        this.cachedLegacyDamage = this.damage;
        this.cachedLegacyTag = this.tag;
    }
}

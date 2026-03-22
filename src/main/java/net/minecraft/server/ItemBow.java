package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

public class ItemBow extends Item {

    public ItemBow(int i) {
        super(i);
        this.maxStackSize = 1;
    }

    public ItemStack a(ItemStack itemstack, World world, EntityHuman entityhuman) {
        boolean isCreative = entityhuman != null && entityhuman.gameMode == 1;
        boolean canFire = isCreative || entityhuman.inventory.hasItem(Item.ARROW.id);

        if (canFire) {
            if (!isCreative) {
                entityhuman.inventory.b(Item.ARROW.id);
            }

            if (entityhuman instanceof EntityPlayer) {
                ((EntityPlayer) entityhuman).triggerBowPose();
            }

            world.makeSound(entityhuman, "random.bow", 1.0F, 1.0F / (b.nextFloat() * 0.4F + 0.8F));
            if ((boolean) PoseidonConfig.getInstance().getProperty("world.settings.skeleton-shooting-sound-fix.enabled")) {
                world.a(entityhuman, 1002, MathHelper.floor(entityhuman.locX), MathHelper.floor(entityhuman.locY - (double) entityhuman.height), MathHelper.floor(entityhuman.locZ), 0); // Poseidon - fix player bow sounds (Strultz)
            }

            if (!world.isStatic) {
                EntityArrow entityarrow = new EntityArrow(world, entityhuman);
                world.addEntity(entityarrow);
            }
        }

        return itemstack;
    }
}

package net.minecraft.server;

// CraftBukkit start

import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockPlaceEvent;
// CraftBukkit end

public class ItemWallClock extends Item {

    private final BlockWallClock clockBlock;

    public ItemWallClock(int i, BlockWallClock blockwallclock) {
        super(i);
        this.clockBlock = blockwallclock;
    }

    public boolean a(ItemStack itemstack, EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        int clickedX = i, clickedY = j, clickedZ = k; // CraftBukkit

        if (world.getTypeId(i, j, k) == Block.SNOW.id) {
            l = 0;
        } else {
            if (entityhuman != null && l >= 2 && l <= 5) {
                int expectedSide = expectedHorizontalSide(entityhuman, i, k);
                if (isOppositeHorizontalSide(l, expectedSide)) {
                    l = expectedSide;
                }
            }

            if (l == 0) {
                --j;
            }

            if (l == 1) {
                ++j;
            }

            if (l == 2) {
                --k;
            }

            if (l == 3) {
                ++k;
            }

            if (l == 4) {
                --i;
            }

            if (l == 5) {
                ++i;
            }
        }

        if (itemstack.count == 0) {
            return false;
        }

        boolean allowGlassPlacement = entityhuman != null && entityhuman.isSneaking();
        int metadata = this.clockBlock.resolvePlacementMetadata(world, i, j, k, l, allowGlassPlacement);
        if (metadata == 0) {
            return false;
        }

        if (!world.a(this.clockBlock.id, i, j, k, false, l)) {
            return false;
        }

        Block block = this.clockBlock;

        // CraftBukkit start - This executes the placement of the block
        CraftBlockState replacedBlockState = CraftBlockState.getBlockState(world, i, j, k); // CraftBukkit
        /**
         * @see net.minecraft.server.World#setTypeId(int i, int j, int k, int l)
         *
         * This replaces world.setTypeId(IIII), we're doing this because we need to
         * hook between the 'placement' and the informing to 'world' so we can
         * sanely undo this.
         *
         * Whenever the call to 'world.setTypeId' changes we need to figure out again what to
         * replace this with.
         */
        if (!world.setRawTypeId(i, j, k, this.clockBlock.id)) { // <-- world.e does this to place the block
            return false;
        }

        BlockPlaceEvent event = CraftEventFactory.callBlockPlaceEvent(world, entityhuman, replacedBlockState, clickedX, clickedY, clickedZ, block);

        if (event.isCancelled() || !event.canBuild()) {
            world.setTypeIdAndData(i, j, k, replacedBlockState.getTypeId(), replacedBlockState.getRawData());
            return true;
        }

        world.update(i, j, k, this.clockBlock.id); // <-- world.setTypeId does this on success (tell the world)
        // CraftBukkit end

        this.clockBlock.applyPlacement(world, i, j, k, metadata);
        block.postPlace(world, i, j, k, entityhuman);
        world.makeSound(entityhuman, (double) ((float) i + 0.5F), (double) ((float) j + 0.5F), (double) ((float) k + 0.5F), block.stepSound.getName(), (block.stepSound.getVolume1() + 1.0F) / 2.0F, block.stepSound.getVolume2() * 0.8F);
        --itemstack.count;

        return true;
    }

    private static int expectedHorizontalSide(EntityHuman entityhuman, int i, int k) {
        double dx = entityhuman.locX - ((double) i + 0.5D);
        double dz = entityhuman.locZ - ((double) k + 0.5D);

        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0.0D ? 5 : 4;
        }

        return dz > 0.0D ? 3 : 2;
    }

    private static boolean isOppositeHorizontalSide(int left, int right) {
        return (left == 2 && right == 3) || (left == 3 && right == 2) || (left == 4 && right == 5) || (left == 5 && right == 4);
    }
}

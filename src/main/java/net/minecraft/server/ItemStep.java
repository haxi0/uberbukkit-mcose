package net.minecraft.server;

import org.bukkit.craftbukkit.block.CraftBlockState;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockPlaceEvent;

public class ItemStep extends ItemBlock {

    public ItemStep(int i) {
        super(i);
        this.d(0);
        this.a(true);
    }

    public boolean a(ItemStack itemstack, EntityHuman entityhuman, World world, int i, int j, int k, int l) {
        if (itemstack.count == 0) {
            return false;
        }

        int x = i;
        int y = j;
        int z = k;
        if (l == 0) {
            --y;
        }

        if (l == 1) {
            ++y;
        }

        if (l == 2) {
            --z;
        }

        if (l == 3) {
            ++z;
        }

        if (l == 4) {
            --x;
        }

        if (l == 5) {
            ++x;
        }

        // Match client ItemSlab merge priority:
        // - Top-face clicks merge clicked slab first.
        // - Other faces try adjacent placement/merge first, with clicked-slab fallback only
        //   when adjacent placement is blocked.
        if (l == 1) {
            if (this.tryMergeStep(itemstack, entityhuman, world, i, j, k, i, j, k)) {
                return true;
            }
            if (this.tryMergeStep(itemstack, entityhuman, world, x, y, z, i, j, k)) {
                return true;
            }
        } else {
            if (this.tryMergeStep(itemstack, entityhuman, world, x, y, z, i, j, k)) {
                return true;
            }
            if (!world.a(Block.STEP.id, x, y, z, false, l)
                && this.tryMergeStep(itemstack, entityhuman, world, i, j, k, i, j, k)) {
                return true;
            }
        }

        return super.a(itemstack, entityhuman, world, i, j, k, l);
    }

    public int filterData(int i) {
        return i;
    }

    private boolean tryMergeStep(ItemStack itemstack, EntityHuman entityhuman, World world, int x, int y, int z, int clickedX, int clickedY, int clickedZ) {
        if (world.getTypeId(x, y, z) != Block.STEP.id) {
            return false;
        }

        int data = itemstack.getData();
        if (world.getData(x, y, z) != data) {
            return false;
        }

        AxisAlignedBB axisalignedbb = Block.DOUBLE_STEP.e(world, x, y, z);
        if (axisalignedbb != null && !world.containsEntity(axisalignedbb)) {
            return false;
        }

        CraftBlockState replacedBlockState = CraftBlockState.getBlockState(world, x, y, z);
        if (!world.setRawTypeIdAndData(x, y, z, Block.DOUBLE_STEP.id, data)) {
            return false;
        }

        BlockPlaceEvent event = CraftEventFactory.callBlockPlaceEvent(world, entityhuman, replacedBlockState, clickedX, clickedY, clickedZ, Block.DOUBLE_STEP);
        if (event.isCancelled() || !event.canBuild()) {
            world.setTypeIdAndData(x, y, z, replacedBlockState.getTypeId(), replacedBlockState.getRawData());
            return true;
        }

        world.update(x, y, z, Block.DOUBLE_STEP.id);
        world.makeSound(entityhuman, (double) ((float) x + 0.5F), (double) ((float) y + 0.5F), (double) ((float) z + 0.5F), Block.DOUBLE_STEP.stepSound.getName(), (Block.DOUBLE_STEP.stepSound.getVolume1() + 1.0F) / 2.0F, Block.DOUBLE_STEP.stepSound.getVolume2() * 0.8F);
        --itemstack.count;
        return true;
    }
}

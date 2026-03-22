package net.minecraft.server;

import me.devcody.uberbukkit.math.Vec3i;

import java.util.Random;

public class BlockChest extends BlockContainer {

    private static final int FACING_NORTH = 2;
    private static final int FACING_SOUTH = 3;
    private static final int FACING_WEST = 4;
    private static final int FACING_EAST = 5;
    private Random a = new Random();

    protected BlockChest(int i) {
        super(i, Material.WOOD);
        this.textureId = 26;
    }

    public int a(int i) {
        return i == 1 ? this.textureId - 1 : (i == 0 ? this.textureId - 1 : (i == 3 ? this.textureId + 1 : this.textureId));
    }

    private static boolean isSolidForChestOrientation(int blockId) {
        if (blockId < 0 || blockId >= Block.o.length || !Block.o[blockId]) {
            return false;
        }

        if (blockId == Block.FURNACE.id || blockId == Block.BURNING_FURNACE.id) {
            return false;
        }

        if (blockId == Block.WORKBENCH.id) {
            return false;
        }

        if (blockId == Block.JUKEBOX.id) {
            return false;
        }

        if (blockId == Block.PUMPKIN.id || blockId == Block.JACK_O_LANTERN.id) {
            return false;
        }

        if (blockId == Block.DISPENSER.id) {
            return false;
        }

        return blockId != Block.NOTE_BLOCK.id;
    }

    private static boolean isValidHorizontalFacing(int metadata) {
        return metadata == FACING_NORTH || metadata == FACING_SOUTH || metadata == FACING_WEST || metadata == FACING_EAST;
    }

    private static int normalizeHorizontalFacing(int metadata, int fallback) {
        return isValidHorizontalFacing(metadata) ? metadata : fallback;
    }

    private int inferFacingFromSurroundings(IBlockAccess blockAccess, int i, int j, int k) {
        int northBlockId = blockAccess.getTypeId(i, j, k - 1);
        int southBlockId = blockAccess.getTypeId(i, j, k + 1);
        int westBlockId = blockAccess.getTypeId(i - 1, j, k);
        int eastBlockId = blockAccess.getTypeId(i + 1, j, k);
        int facing = FACING_SOUTH;

        if (isSolidForChestOrientation(northBlockId) && !isSolidForChestOrientation(southBlockId)) {
            facing = FACING_SOUTH;
        }

        if (isSolidForChestOrientation(southBlockId) && !isSolidForChestOrientation(northBlockId)) {
            facing = FACING_NORTH;
        }

        if (isSolidForChestOrientation(westBlockId) && !isSolidForChestOrientation(eastBlockId)) {
            facing = FACING_EAST;
        }

        if (isSolidForChestOrientation(eastBlockId) && !isSolidForChestOrientation(westBlockId)) {
            facing = FACING_WEST;
        }

        return facing;
    }

    private int facingFromPlacer(EntityLiving entityliving) {
        int direction = MathHelper.floor((double) (entityliving.yaw * 4.0F / 360.0F) + 0.5D) & 3;
        if (direction == 0) {
            return FACING_NORTH;
        } else if (direction == 1) {
            return FACING_EAST;
        } else if (direction == 2) {
            return FACING_SOUTH;
        } else {
            return FACING_WEST;
        }
    }

    private int[] findAdjacentChest(IBlockAccess blockAccess, int i, int j, int k) {
        if (blockAccess.getTypeId(i - 1, j, k) == this.id) return new int[] { i - 1, j, k };
        if (blockAccess.getTypeId(i + 1, j, k) == this.id) return new int[] { i + 1, j, k };
        if (blockAccess.getTypeId(i, j, k - 1) == this.id) return new int[] { i, j, k - 1 };
        if (blockAccess.getTypeId(i, j, k + 1) == this.id) return new int[] { i, j, k + 1 };
        return null;
    }

    private int resolveMergedFacing(int preferredFacing, int fallbackFacing, int dx, int dz) {
        if (dx != 0) {
            if (preferredFacing == FACING_NORTH || preferredFacing == FACING_SOUTH) {
                return preferredFacing;
            }

            if (fallbackFacing == FACING_NORTH || fallbackFacing == FACING_SOUTH) {
                return fallbackFacing;
            }

            return FACING_SOUTH;
        }

        if (dz != 0) {
            if (preferredFacing == FACING_WEST || preferredFacing == FACING_EAST) {
                return preferredFacing;
            }

            if (fallbackFacing == FACING_WEST || fallbackFacing == FACING_EAST) {
                return fallbackFacing;
            }

            return FACING_EAST;
        }

        return preferredFacing;
    }

    public void c(World world, int i, int j, int k) {
        super.c(world, i, j, k);
        int currentMeta = world.getData(i, j, k);
        if (!isValidHorizontalFacing(currentMeta)) {
            int facing = this.inferFacingFromSurroundings(world, i, j, k);
            world.setData(i, j, k, facing);
        }
    }

    public void postPlace(World world, int i, int j, int k, EntityLiving entityliving) {
        int preferredFacing = this.facingFromPlacer(entityliving);
        int[] adjacent = this.findAdjacentChest(world, i, j, k);
        int facing = preferredFacing;

        if (adjacent != null) {
            int neighborFacing = normalizeHorizontalFacing(world.getData(adjacent[0], adjacent[1], adjacent[2]), FACING_SOUTH);
            facing = this.resolveMergedFacing(preferredFacing, neighborFacing, adjacent[0] - i, adjacent[2] - k);
            world.setData(adjacent[0], adjacent[1], adjacent[2], facing);
        }

        world.setData(i, j, k, normalizeHorizontalFacing(facing, FACING_SOUTH));
    }

    public boolean canPlace(World world, int i, int j, int k) {
        int l = 0;

        if (world.getTypeId(i - 1, j, k) == this.id) {
            ++l;
        }

        if (world.getTypeId(i + 1, j, k) == this.id) {
            ++l;
        }

        if (world.getTypeId(i, j, k - 1) == this.id) {
            ++l;
        }

        if (world.getTypeId(i, j, k + 1) == this.id) {
            ++l;
        }

        return l > 1 ? false : (this.g(world, i - 1, j, k) ? false : (this.g(world, i + 1, j, k) ? false : (this.g(world, i, j, k - 1) ? false : !this.g(world, i, j, k + 1))));
    }

    private boolean g(World world, int i, int j, int k) {
        return world.getTypeId(i, j, k) != this.id ? false : (world.getTypeId(i - 1, j, k) == this.id ? true : (world.getTypeId(i + 1, j, k) == this.id ? true : (world.getTypeId(i, j, k - 1) == this.id ? true : world.getTypeId(i, j, k + 1) == this.id)));
    }

    public void remove(World world, int i, int j, int k) {
        TileEntityChest tileentitychest = (TileEntityChest) world.getTileEntity(i, j, k);

        for (int l = 0; l < tileentitychest.getSize(); ++l) {
            ItemStack itemstack = tileentitychest.getItem(l);

            if (itemstack != null) {
                float f = this.a.nextFloat() * 0.8F + 0.1F;
                float f1 = this.a.nextFloat() * 0.8F + 0.1F;
                float f2 = this.a.nextFloat() * 0.8F + 0.1F;

                while (itemstack.count > 0) {
                    int i1 = this.a.nextInt(21) + 10;

                    if (i1 > itemstack.count) {
                        i1 = itemstack.count;
                    }

                    itemstack.count -= i1;
                    EntityItem entityitem = new EntityItem(world, (double) ((float) i + f), (double) ((float) j + f1), (double) ((float) k + f2), new ItemStack(itemstack.id, i1, itemstack.getData()));
                    float f3 = 0.05F;

                    entityitem.motX = (double) ((float) this.a.nextGaussian() * f3);
                    entityitem.motY = (double) ((float) this.a.nextGaussian() * f3 + 0.2F);
                    entityitem.motZ = (double) ((float) this.a.nextGaussian() * f3);
                    world.addEntity(entityitem);
                }
                tileentitychest.setItem(l, null);
            }
        }

        super.remove(world, i, j, k);
    }

    public boolean interact(World world, int i, int j, int k, EntityHuman entityhuman) {
        Object object = (TileEntityChest) world.getTileEntity(i, j, k);

        if (world.e(i, j + 1, k)) {
            return true;
        } else if (world.getTypeId(i - 1, j, k) == this.id && world.e(i - 1, j + 1, k)) {
            return true;
        } else if (world.getTypeId(i + 1, j, k) == this.id && world.e(i + 1, j + 1, k)) {
            return true;
        } else if (world.getTypeId(i, j, k - 1) == this.id && world.e(i, j + 1, k - 1)) {
            return true;
        } else if (world.getTypeId(i, j, k + 1) == this.id && world.e(i, j + 1, k + 1)) {
            return true;
        } else {
            if (world.getTypeId(i - 1, j, k) == this.id) {
                object = new InventoryLargeChest("Large chest", (TileEntityChest) world.getTileEntity(i - 1, j, k), (IInventory) object);
            }

            if (world.getTypeId(i + 1, j, k) == this.id) {
                object = new InventoryLargeChest("Large chest", (IInventory) object, (TileEntityChest) world.getTileEntity(i + 1, j, k));
            }

            if (world.getTypeId(i, j, k - 1) == this.id) {
                object = new InventoryLargeChest("Large chest", (TileEntityChest) world.getTileEntity(i, j, k - 1), (IInventory) object);
            }

            if (world.getTypeId(i, j, k + 1) == this.id) {
                object = new InventoryLargeChest("Large chest", (IInventory) object, (TileEntityChest) world.getTileEntity(i, j, k + 1));
            }

            if (!world.isStatic) {
                entityhuman.a((IInventory) object, new Vec3i(i, j, k));
            }

            return true;
        }
    }

    protected TileEntity a_() {
        return new TileEntityChest();
    }
}

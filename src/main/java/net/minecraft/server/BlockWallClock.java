package net.minecraft.server;

import java.util.Random;

public class BlockWallClock extends Block {

    private static final float THICKNESS = 1.0F / 16.0F;
    private static final float FACE_INSET = 0.14F;
    private static final int UPDATE_INTERVAL_TICKS = 1;

    protected BlockWallClock(int i, int j) {
        super(i, j, Material.ORIENTABLE);
    }

    public AxisAlignedBB e(World world, int i, int j, int k) {
        this.setBoundsForData(CriticalBlockStateAccess.getWallClockMetadata(world, i, j, k));
        return super.e(world, i, j, k);
    }

    public void a(IBlockAccess iblockaccess, int i, int j, int k) {
        this.setBoundsForData(CriticalBlockStateAccess.getWallClockMetadata(iblockaccess, i, j, k));
    }

    public boolean a() {
        return false;
    }

    public boolean b() {
        return false;
    }

    public int c() {
        return UPDATE_INTERVAL_TICKS;
    }

    public boolean isPowerSource() {
        return true;
    }

    public boolean d(World world, int i, int j, int k, int l) {
        return getPowerLevel(world) > 0;
    }

    public boolean a(IBlockAccess iblockaccess, int i, int j, int k, int l) {
        return getPowerLevel(iblockaccess) > 0;
    }

    public boolean canPlace(World world, int i, int j, int k) {
        return canAttachTo(world, i - 1, j, k, true)
                || canAttachTo(world, i + 1, j, k, true)
                || canAttachTo(world, i, j, k - 1, true)
                || canAttachTo(world, i, j, k + 1, true);
    }

    public boolean canPlace(World world, int i, int j, int k, int l) {
        if (l == 2 && canAttachTo(world, i, j, k + 1, true)) {
            return true;
        }
        if (l == 3 && canAttachTo(world, i, j, k - 1, true)) {
            return true;
        }
        if (l == 4 && canAttachTo(world, i + 1, j, k, true)) {
            return true;
        }
        if (l == 5 && canAttachTo(world, i - 1, j, k, true)) {
            return true;
        }
        return this.canPlace(world, i, j, k);
    }

    public void c(World world, int i, int j, int k) {
        super.c(world, i, j, k);
        if (!world.isStatic) {
            world.c(i, j, k, this.id, this.c());
        }
    }

    public void postPlace(World world, int i, int j, int k, int l) {
        int metadata = this.resolvePlacementMetadata(world, i, j, k, l, true);
        this.applyPlacement(world, i, j, k, metadata);
    }

    public void doPhysics(World world, int i, int j, int k, int l) {
        if (!this.canClockStay(world, i, j, k)) {
            this.g(world, i, j, k, CriticalBlockStateAccess.getWallClockMetadata(world, i, j, k));
            world.setTypeId(i, j, k, 0);
            return;
        }

        super.doPhysics(world, i, j, k, l);
    }

    public void remove(World world, int i, int j, int k) {
        this.notifySignalNeighbors(world, i, j, k);
        super.remove(world, i, j, k);
    }

    public void a(World world, int i, int j, int k, Random random) {
        if (world.isStatic) {
            return;
        }

        if (!this.canClockStay(world, i, j, k)) {
            this.g(world, i, j, k, CriticalBlockStateAccess.getWallClockMetadata(world, i, j, k));
            world.setTypeId(i, j, k, 0);
            return;
        }

        // Keep redstone output continuously synchronized with world time changes.
        this.notifySignalNeighbors(world, i, j, k);
        world.c(i, j, k, this.id, this.c());
    }

    public int a(int i, Random random) {
        return Item.WATCH != null ? Item.WATCH.id : 0;
    }

    public static int getPowerLevel(IBlockAccess iblockaccess) {
        if (iblockaccess instanceof World) {
            return getPowerLevel((World) iblockaccess);
        }
        return 0;
    }

    public static int getPowerLevel(World world) {
        if (world == null) {
            return 0;
        }
        return getPowerLevelForWorldTime(world.getTime());
    }

    public static int getPowerLevelForWorldTime(long worldTime) {
        long dayTime = worldTime % 24000L;
        if (dayTime < 0L) {
            dayTime += 24000L;
        }

        double phase = ((double) (dayTime - 6000L) / 24000.0D) * (Math.PI * 2.0D);
        double sunExposure = Math.cos(phase);
        // Keep a weak twilight signal so clocks begin powering around sunrise, not only after full day.
        double twilightBias = 0.12D;
        double daylightFactor = (sunExposure + twilightBias) / (1.0D + twilightBias);
        if (daylightFactor < 0.0D) {
            daylightFactor = 0.0D;
        } else if (daylightFactor > 1.0D) {
            daylightFactor = 1.0D;
        }

        int power = (int) Math.round(daylightFactor * 15.0D);
        if (power < 0) {
            return 0;
        }

        if (power > 15) {
            return 15;
        }

        return power;
    }

    public int resolvePlacementMetadata(World world, int i, int j, int k, int l, boolean allowGlass) {
        int metadata = 0;
        if ((metadata == 0 || l == 2) && canAttachTo(world, i, j, k + 1, allowGlass)) {
            metadata = 2;
        }

        if ((metadata == 0 || l == 3) && canAttachTo(world, i, j, k - 1, allowGlass)) {
            metadata = 3;
        }

        if ((metadata == 0 || l == 4) && canAttachTo(world, i + 1, j, k, allowGlass)) {
            metadata = 4;
        }

        if ((metadata == 0 || l == 5) && canAttachTo(world, i - 1, j, k, allowGlass)) {
            metadata = 5;
        }

        return metadata;
    }

    public void applyPlacement(World world, int i, int j, int k, int metadata) {
        CriticalBlockStateAccess.setMetadata(world, i, j, k, metadata, true);
        if (!world.isStatic) {
            world.c(i, j, k, this.id, this.c());
            this.notifySignalNeighbors(world, i, j, k);
        }
    }

    private boolean canClockStay(World world, int i, int j, int k) {
        int l = CriticalBlockStateAccess.getWallClockMetadata(world, i, j, k);
        if (l == 0) {
            return this.canPlace(world, i, j, k);
        }

        if (l == 2) {
            return canAttachTo(world, i, j, k + 1, true);
        }

        if (l == 3) {
            return canAttachTo(world, i, j, k - 1, true);
        }

        if (l == 4) {
            return canAttachTo(world, i + 1, j, k, true);
        }

        if (l == 5) {
            return canAttachTo(world, i - 1, j, k, true);
        }

        return false;
    }

    private void notifySignalNeighbors(World world, int i, int j, int k) {
        world.applyPhysics(i, j, k, this.id);
        world.applyPhysics(i, j - 1, k, this.id);
        world.applyPhysics(i, j + 1, k, this.id);
        world.applyPhysics(i - 1, j, k, this.id);
        world.applyPhysics(i + 1, j, k, this.id);
        world.applyPhysics(i, j, k - 1, this.id);
        world.applyPhysics(i, j, k + 1, this.id);
    }

    private void setBoundsForData(int data) {
        if (data == 2) {
            this.a(FACE_INSET, FACE_INSET, 1.0F - THICKNESS, 1.0F - FACE_INSET, 1.0F - FACE_INSET, 1.0F);
            return;
        }

        if (data == 3) {
            this.a(FACE_INSET, FACE_INSET, 0.0F, 1.0F - FACE_INSET, 1.0F - FACE_INSET, THICKNESS);
            return;
        }

        if (data == 4) {
            this.a(1.0F - THICKNESS, FACE_INSET, FACE_INSET, 1.0F, 1.0F - FACE_INSET, 1.0F - FACE_INSET);
            return;
        }

        if (data == 5) {
            this.a(0.0F, FACE_INSET, FACE_INSET, THICKNESS, 1.0F - FACE_INSET, 1.0F - FACE_INSET);
            return;
        }

        this.a(FACE_INSET, FACE_INSET, 1.0F - THICKNESS, 1.0F - FACE_INSET, 1.0F - FACE_INSET, 1.0F);
    }

    private static boolean canAttachTo(World world, int i, int j, int k, boolean allowGlass) {
        if (world.e(i, j, k)) {
            return true;
        }
        return allowGlass && Block.GLASS != null && world.getTypeId(i, j, k) == Block.GLASS.id;
    }
}

package net.minecraft.server;

import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.EntityInteractEvent;

import java.util.Random;

// CraftBukkit start
// CraftBukkit end

public class BlockSoil extends Block {

    protected BlockSoil(int i) {
        super(i, Material.EARTH);
        this.textureId = 87;
        this.a(true);
        this.a(0.0F, 0.0F, 0.0F, 1.0F, 0.9375F, 1.0F);
        this.f(255);
    }

    public AxisAlignedBB e(World world, int i, int j, int k) {
        return AxisAlignedBB.b((double) (i + 0), (double) (j + 0), (double) (k + 0), (double) (i + 1), (double) (j + 1), (double) (k + 1));
    }

    public boolean a() {
        return false;
    }

    public boolean b() {
        return false;
    }

    public int a(int i, int j) {
        return i == 1 && j > 0 ? this.textureId - 1 : (i == 1 ? this.textureId : 2);
    }

    public void a(World world, int i, int j, int k, Entity entity) {
        if (entity instanceof EntityPlayer && uk.betacraft.uberbukkit.UberbukkitConfig.getInstance().getBoolean("mechanics.modern_farmland", true)) {
            ((EntityPlayer) entity).netServerHandler.sendPacket(new Packet53BlockChange(i, j, k, world));
        }
    }

    public void a(World world, int i, int j, int k, Random random) {
        if (random.nextInt(5) == 0) {
            if (!this.h(world, i, j, k) && !world.s(i, j + 1, k)) {
                int l = world.getData(i, j, k);

                if (l > 0) {
                    world.setData(i, j, k, l - 1);
                } else if (!this.g(world, i, j, k)) {
                    // Modern Farmland: Don't revert if modern_farmland is enabled
                    if (!uk.betacraft.uberbukkit.UberbukkitConfig.getInstance().getBoolean("mechanics.modern_farmland", true)) {
                         world.setTypeId(i, j, k, Block.DIRT.id);
                    } else {
                        // Force client resync even on random tick to prevent local prediction bugs
                        for (Object obj : world.players) {
                            if (obj instanceof EntityPlayer) {
                                EntityPlayer ep = (EntityPlayer) obj;
                                double dx = ep.locX - i;
                                double dy = ep.locY - j;
                                double dz = ep.locZ - k;
                                if (dx * dx + dy * dy + dz * dz < 1024) { // 32 blocks away
                                    ep.netServerHandler.sendPacket(new Packet53BlockChange(i, j, k, world));
                                }
                            }
                        }
                    }
                }
            } else {
                world.setData(i, j, k, 7);
            }
        }
    }

    public void b(World world, int i, int j, int k, Entity entity) {
        if (entity instanceof EntityPlayer && uk.betacraft.uberbukkit.UberbukkitConfig.getInstance().getBoolean("mechanics.modern_farmland", true)) {
            ((EntityPlayer) entity).netServerHandler.sendPacket(new Packet53BlockChange(i, j, k, world));
            if (world.getTypeId(i, j + 1, k) > 0) {
                ((EntityPlayer) entity).netServerHandler.sendPacket(new Packet53BlockChange(i, j + 1, k, world));
            }
        }
    }

    private boolean g(World world, int i, int j, int k) {
        byte b0 = 0;

        for (int l = i - b0; l <= i + b0; ++l) {
            for (int i1 = k - b0; i1 <= k + b0; ++i1) {
                int j1 = world.getTypeId(l, j + 1, i1);
                if (j1 == Block.CROPS.id || j1 == Block.PUMPKIN_STEM.id || j1 == Block.MELON_STEM.id) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean h(World world, int i, int j, int k) {
        for (int l = i - 4; l <= i + 4; ++l) {
            for (int i1 = j; i1 <= j + 1; ++i1) {
                for (int j1 = k - 4; j1 <= k + 4; ++j1) {
                    if (world.getMaterial(l, i1, j1) == Material.WATER) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public void doPhysics(World world, int i, int j, int k, int l) {
        super.doPhysics(world, i, j, k, l);
        int blockAboveId = world.getTypeId(i, j + 1, k);

        if (blockAboveId > 0) {
            boolean shouldRevert = false;
            if (uk.betacraft.uberbukkit.UberbukkitConfig.getInstance().getBoolean("mechanics.modern_farmland", true)) {
                // Modern check: only revert if block is opaque
                if (Block.byId[blockAboveId].a()) {
                    shouldRevert = true;
                }
            } else {
                // Vanilla legacy check
                Material material = world.getMaterial(i, j + 1, k);
                if (material.isBuildable()) {
                    shouldRevert = true;
                }
            }

            if (shouldRevert) {
                world.setTypeId(i, j, k, Block.DIRT.id);
            }
        }
    }

    public int a(int i, Random random) {
        return Block.DIRT.a(0, random);
    }
}

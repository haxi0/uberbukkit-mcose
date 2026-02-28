package net.minecraft.server;

import net.minecraft.server.Alpha.AlphaWorldGenBigTree;
import net.minecraft.server.Alpha.AlphaWorldGenTrees;
import org.bukkit.BlockChangeDelegate;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.material.MaterialData;

import uk.betacraft.uberbukkit.UberbukkitConfig;

import java.util.Random;

public class BlockSapling extends BlockFlower {

    protected BlockSapling(int i, int j) {
        super(i, j);
        float f = 0.4F;

        this.a(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, f * 2.0F, 0.5F + f);
    }

    public void a(World world, int i, int j, int k, Random random) {
        if (!world.isStatic) {
            super.a(world, i, j, k, random);

            if (this.isAlphaTerrainWorld(world)) {
                if (world.getLightLevel(i, j + 1, k) >= 9 && random.nextInt(5) == 0) {
                    int l = world.getData(i, j, k);
                    int species = l & 3;
                    int stage = l & 12;

                    if (stage < 12) {
                        world.setData(i, j, k, species | (stage + 4));
                    } else {
                        this.b(world, i, j, k, random);
                    }
                }
            } else if (world.getLightLevel(i, j + 1, k) >= 9 && random.nextInt(30) == 0) {
                int l = world.getData(i, j, k);

                if ((l & 8) == 0) {
                    world.setData(i, j, k, l | 8);
                } else {
                    this.b(world, i, j, k, random);
                }
            }
        }
    }

    private boolean isAlphaTerrainWorld(World world) {
        return world != null && world.worldData != null && (world.worldData.getTerrainType() == 1 || world.worldData.getTerrainType() == 5);
    }

    public int a(int i, int j) {
        j &= 3;
        return j == 1 ? 63 : (j == 2 ? 79 : super.a(i, j));
    }

    public void b(World world, int i, int j, int k, Random random) {
        int l = world.getData(i, j, k) & 3;

        if (this.isAlphaTerrainWorld(world)) {
            world.setRawTypeId(i, j, k, 0);
            boolean grownTree;
            if (l == 1) {
                grownTree = new WorldGenTaiga2().a(world, random, i, j, k);
            } else if (l == 2) {
                grownTree = new WorldGenForest().a(world, random, i, j, k);
            } else {
                grownTree = random.nextInt(10) == 0
                        ? new AlphaWorldGenBigTree().a(world, random, i, j, k)
                        : new AlphaWorldGenTrees().a(world, random, i, j, k);
            }

            if (!grownTree) {
                world.setRawTypeIdAndData(i, j, k, this.id, l);
            }

            return;
        }

        world.setRawTypeId(i, j, k, 0);

        // CraftBukkit start - fixes client updates on recently grown trees
        boolean grownTree;
        BlockChangeWithNotify delegate = new BlockChangeWithNotify(world, i, j, k);

        // uberbukkit
        if (l == 1) {
            grownTree = new WorldGenTaiga2().generate(delegate, random, i, j, k);
        } else if (l == 2) {
            grownTree = new WorldGenForest().generate(delegate, random, i, j, k);
        } else {
            if (random.nextInt(10) == 0) {
                grownTree = new WorldGenBigTree().generate(delegate, random, i, j, k);
            } else {
                grownTree = new WorldGenTrees().generate(delegate, random, i, j, k);
            }
        }

        if (!grownTree) {
            world.setRawTypeIdAndData(i, j, k, this.id, l);
        }
        // CraftBukkit end
    }

    protected int a_(int i) {
        // uberbukkit
        if (!UberbukkitConfig.getInstance().getBoolean("mechanics.drop_saplings_of_leaf_type", true)) {
            return 0;
        } else {
            return i & 3;
        }
    }

    // CraftBukkit start
    private class BlockChangeWithNotify implements BlockChangeDelegate {
        private final World world;
        private final int sourceX;
        private final int sourceY;
        private final int sourceZ;

        BlockChangeWithNotify(World world, int sourceX, int sourceY, int sourceZ) {
            this.world = world;
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceZ = sourceZ;
        }

        public boolean setRawTypeId(int x, int y, int z, int type) {
            return this.applyGrowthBlock(x, y, z, type, 0, false);
        }

        public boolean setRawTypeIdAndData(int x, int y, int z, int type, int data) {
            return this.applyGrowthBlock(x, y, z, type, data, true);
        }

        public int getTypeId(int x, int y, int z) {
            return this.world.getTypeId(x, y, z);
        }

        private boolean applyGrowthBlock(int x, int y, int z, int type, int data, boolean hasData) {
            if (type == 0) {
                if (hasData) {
                    return this.world.setTypeIdAndData(x, y, z, type, data);
                }
                return this.world.setTypeId(x, y, z, type);
            }

            if (this.world.getTypeId(x, y, z) == type && (!hasData || this.world.getData(x, y, z) == data)) {
                return true;
            }

            org.bukkit.World bukkitWorld = this.world.getWorld();
            org.bukkit.block.Block block = bukkitWorld.getBlockAt(x, y, z);
            org.bukkit.block.BlockState blockState = block.getState();
            blockState.setTypeId(type);
            blockState.setData(new MaterialData(type, (byte) (hasData ? data : 0)));

            BlockSpreadEvent event = new BlockSpreadEvent(blockState.getBlock(), bukkitWorld.getBlockAt(this.sourceX, this.sourceY, this.sourceZ), blockState);
            this.world.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return false;
            }

            return blockState.update(true);
        }
    }
    // CraftBukkit end
}

package net.minecraft.server.Infdev;

import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.BlockSand;
import net.minecraft.server.Chunk;
import net.minecraft.server.registry.Features;
import net.minecraft.server.IChunkProvider;
import net.minecraft.server.IProgressUpdate;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenCactus;
import net.minecraft.server.WorldGenClay;
import net.minecraft.server.WorldGenFlowers;
import net.minecraft.server.WorldGenLiquids;
import net.minecraft.server.WorldGenPumpkin;
import net.minecraft.server.WorldGenReed;
import net.minecraft.server.WorldGenerator;

public final class InfdevChunkProvider implements IChunkProvider {
    private final Random rand;
    private final InfdevNoiseGeneratorOctaves noiseGen1;
    private final InfdevNoiseGeneratorOctaves noiseGen2;
    private final InfdevNoiseGeneratorOctaves noiseGen3;
    private final InfdevNoiseGeneratorOctaves noiseGen4;
    private final InfdevNoiseGeneratorOctaves noiseGen5;
    private final InfdevNoiseGeneratorOctaves mobSpawnerNoise;
    private final World worldObj;

    private final InfdevWorldGenMinable coalGen = new InfdevWorldGenMinable(Block.COAL_ORE.id);
    private final InfdevWorldGenMinable ironGen = new InfdevWorldGenMinable(Block.IRON_ORE.id);
    private final InfdevWorldGenMinable goldGen = new InfdevWorldGenMinable(Block.GOLD_ORE.id);
    private final InfdevWorldGenMinable diamondGen = new InfdevWorldGenMinable(Block.DIAMOND_ORE.id);
    private final InfdevWorldGenMinable redstoneGen = new InfdevWorldGenMinable(Block.REDSTONE_ORE.id);
    private final InfdevWorldGenMinable lapisGen = new InfdevWorldGenMinable(Block.LAPIS_ORE.id);
    private final InfdevWorldGenMinable dirtGen = new InfdevWorldGenMinable(Block.DIRT.id);
    private final InfdevWorldGenMinable gravelGen = new InfdevWorldGenMinable(Block.GRAVEL.id);
    private final InfdevWorldGenBigTree bigTreeGen = new InfdevWorldGenBigTree();
    private final WorldGenerator dungeonGen = Features.create("minecraft:dungeon");
    private final WorldGenClay clayGen = new WorldGenClay(32);
    private final WorldGenFlowers yellowFlowerGen = new WorldGenFlowers(Block.YELLOW_FLOWER.id);
    private final WorldGenFlowers redFlowerGen = new WorldGenFlowers(Block.RED_ROSE.id);
    private final WorldGenFlowers brownMushroomGen = new WorldGenFlowers(Block.BROWN_MUSHROOM.id);
    private final WorldGenFlowers redMushroomGen = new WorldGenFlowers(Block.RED_MUSHROOM.id);
    private final WorldGenReed reedGen = new WorldGenReed();
    private final WorldGenCactus cactusGen = new WorldGenCactus();
    private final WorldGenPumpkin pumpkinGen = new WorldGenPumpkin();
    private final WorldGenLiquids waterSpringGen = new WorldGenLiquids(Block.WATER.id);
    private final WorldGenLiquids lavaSpringGen = new WorldGenLiquids(Block.LAVA.id);

    public InfdevChunkProvider(World world, long seed) {
        this.worldObj = world;
        this.rand = new Random(seed);
        new Random(seed);
        this.noiseGen1 = new InfdevNoiseGeneratorOctaves(this.rand, 16);
        this.noiseGen2 = new InfdevNoiseGeneratorOctaves(this.rand, 16);
        this.noiseGen3 = new InfdevNoiseGeneratorOctaves(this.rand, 8);
        this.noiseGen4 = new InfdevNoiseGeneratorOctaves(this.rand, 4);
        this.noiseGen5 = new InfdevNoiseGeneratorOctaves(this.rand, 4);
        new InfdevNoiseGeneratorOctaves(this.rand, 5);
        this.mobSpawnerNoise = new InfdevNoiseGeneratorOctaves(this.rand, 5);
    }

    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        this.rand.setSeed((long)chunkX * 341873128712L + (long)chunkZ * 132897987541L);
        byte[] blocks = new byte[32768];
        Chunk chunk = new Chunk(this.worldObj, blocks, chunkX, chunkZ);

        for (int cellX = 0; cellX < 4; ++cellX) {
            for (int cellZ = 0; cellZ < 4; ++cellZ) {
                double[][] noiseSlice = new double[33][4];
                int noiseX = (chunkX << 2) + cellX;
                int noiseZ = (chunkZ << 2) + cellZ;

                for (int yCell = 0; yCell < noiseSlice.length; ++yCell) {
                    noiseSlice[yCell][0] = this.initializeNoiseField((double)noiseX, (double)yCell, (double)noiseZ);
                    noiseSlice[yCell][1] = this.initializeNoiseField((double)noiseX, (double)yCell, (double)(noiseZ + 1));
                    noiseSlice[yCell][2] = this.initializeNoiseField((double)(noiseX + 1), (double)yCell, (double)noiseZ);
                    noiseSlice[yCell][3] = this.initializeNoiseField((double)(noiseX + 1), (double)yCell, (double)(noiseZ + 1));
                }

                for (noiseX = 0; noiseX < 32; ++noiseX) {
                    double n00 = noiseSlice[noiseX][0];
                    double n01 = noiseSlice[noiseX][1];
                    double n10 = noiseSlice[noiseX][2];
                    double n11 = noiseSlice[noiseX][3];
                    double n00u = noiseSlice[noiseX + 1][0];
                    double n01u = noiseSlice[noiseX + 1][1];
                    double n10u = noiseSlice[noiseX + 1][2];
                    double n11u = noiseSlice[noiseX + 1][3];

                    for (int subY = 0; subY < 4; ++subY) {
                        double yLerp = (double)subY / 4.0D;
                        double ix0 = n00 + (n00u - n00) * yLerp;
                        double ix1 = n01 + (n01u - n01) * yLerp;
                        double ix2 = n10 + (n10u - n10) * yLerp;
                        double ix3 = n11 + (n11u - n11) * yLerp;

                        for (int subX = 0; subX < 4; ++subX) {
                            double xLerp = (double)subX / 4.0D;
                            double iz0 = ix0 + (ix2 - ix0) * xLerp;
                            double iz1 = ix1 + (ix3 - ix1) * xLerp;
                            int index = ((subX + (cellX << 2)) << 11) | (((cellZ << 2)) << 7) | ((noiseX << 2) + subY);

                            for (int subZ = 0; subZ < 4; ++subZ) {
                                double zLerp = (double)subZ / 4.0D;
                                double density = iz0 + (iz1 - iz0) * zLerp;
                                int blockId = 0;
                                if ((noiseX << 2) + subY < 64) {
                                    blockId = Block.STATIONARY_WATER.id;
                                }

                                if (density > 0.0D) {
                                    blockId = Block.STONE.id;
                                }

                                blocks[index] = (byte)blockId;
                                index += 128;
                            }
                        }
                    }
                }
            }
        }

        for (int localX = 0; localX < 16; ++localX) {
            for (int localZ = 0; localZ < 16; ++localZ) {
                double worldX = (double)((chunkX << 4) + localX);
                double worldZ = (double)((chunkZ << 4) + localZ);
                boolean useSand = this.noiseGen4.generateNoiseOctaves(worldX * (1.0D / 32.0D), worldZ * (1.0D / 32.0D), 0.0D)
                    + this.rand.nextDouble() * 0.2D > 0.0D;
                boolean useGravel = this.noiseGen4.generateNoiseOctaves(worldZ * (1.0D / 32.0D), 109.0134D, worldX * (1.0D / 32.0D))
                    + this.rand.nextDouble() * 0.2D > 3.0D;
                int dirtDepth = (int)(this.noiseGen5.noiseGenerator(worldX * (1.0D / 32.0D) * 2.0D, worldZ * (1.0D / 32.0D) * 2.0D)
                    / 3.0D + 3.0D + this.rand.nextDouble() * 0.25D);
                int index = localX << 11 | localZ << 7 | 127;
                int surfaceDepth = -1;
                int topBlock = Block.GRASS.id;
                int fillerBlock = Block.DIRT.id;

                for (int y = 127; y >= 0; --y) {
                    if (blocks[index] == 0) {
                        surfaceDepth = -1;
                    } else if (blocks[index] == Block.STONE.id) {
                        if (surfaceDepth == -1) {
                            if (dirtDepth <= 0) {
                                topBlock = 0;
                                fillerBlock = Block.STONE.id;
                            } else if (y >= 60 && y <= 65) {
                                topBlock = Block.GRASS.id;
                                fillerBlock = Block.DIRT.id;
                                if (useGravel) {
                                    topBlock = 0;
                                    fillerBlock = Block.GRAVEL.id;
                                }

                                if (useSand) {
                                    topBlock = Block.SAND.id;
                                    fillerBlock = Block.SAND.id;
                                }
                            }

                            if (y < 64 && topBlock == 0) {
                                topBlock = Block.STATIONARY_WATER.id;
                            }

                            surfaceDepth = dirtDepth;
                            if (y >= 63) {
                                blocks[index] = (byte)topBlock;
                            } else {
                                blocks[index] = (byte)fillerBlock;
                            }
                        } else if (surfaceDepth > 0) {
                            --surfaceDepth;
                            blocks[index] = (byte)fillerBlock;
                        }
                    }

                    --index;
                }
            }
        }

        chunk.initLighting();
        return chunk;
    }

    private double initializeNoiseField(double x, double y, double z) {
        double heightOffset = y * 4.0D - 64.0D;
        if (heightOffset < 0.0D) {
            heightOffset *= 3.0D;
        }

        double blend = this.noiseGen3.generateNoiseOctaves(x * 684.412D / 80.0D, y * 684.412D / 400.0D, z * 684.412D / 80.0D) / 2.0D;
        double result;

        if (blend < -1.0D) {
            double n1 = this.noiseGen1.generateNoiseOctaves(x * 684.412D, y * 984.412D, z * 684.412D) / 512.0D;
            result = n1 - heightOffset;
            if (result < -10.0D) {
                result = -10.0D;
            }

            if (result > 10.0D) {
                result = 10.0D;
            }
        } else if (blend > 1.0D) {
            double n2 = this.noiseGen2.generateNoiseOctaves(x * 684.412D, y * 984.412D, z * 684.412D) / 512.0D;
            result = n2 - heightOffset;
            if (result < -10.0D) {
                result = -10.0D;
            }

            if (result > 10.0D) {
                result = 10.0D;
            }
        } else {
            double n1 = this.noiseGen1.generateNoiseOctaves(x * 684.412D, y * 984.412D, z * 684.412D) / 512.0D - heightOffset;
            double n2 = this.noiseGen2.generateNoiseOctaves(x * 684.412D, y * 984.412D, z * 684.412D) / 512.0D - heightOffset;
            if (n1 < -10.0D) {
                n1 = -10.0D;
            }

            if (n1 > 10.0D) {
                n1 = 10.0D;
            }

            if (n2 < -10.0D) {
                n2 = -10.0D;
            }

            if (n2 > 10.0D) {
                n2 = 10.0D;
            }

            double t = (blend + 1.0D) / 2.0D;
            result = n1 + (n2 - n1) * t;
        }

        return result;
    }

    public boolean isChunkLoaded(int x, int z) {
        return true;
    }

    public Chunk getChunkAt(int x, int z) {
        return this.getOrCreateChunk(x, z);
    }

    public void getChunkAt(IChunkProvider provider, int chunkX, int chunkZ) {
        BlockSand.instaFall = true;

        try {
            int x = chunkX << 4;
            int z = chunkZ << 4;

            // Keep population seeding aligned with the overworld providers.
            this.rand.setSeed(this.worldObj.getSeed());
            long oddX = this.rand.nextLong() / 2L * 2L + 1L;
            long oddZ = this.rand.nextLong() / 2L * 2L + 1L;
            this.rand.setSeed((long)chunkX * oddX + (long)chunkZ * oddZ ^ this.worldObj.getSeed());

            for (int i = 0; i < 8; ++i) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16) + 8;
                this.dungeonGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            // Keep shrine determinism identical to the overworld formula, but allow INFDEV terrain.
            long shrineSeed = (long)chunkX * 341873128712L + (long)chunkZ * 132897987541L + this.worldObj.getSeed() + 777777777L;
            Random shrineRand = new Random(shrineSeed);
            if (shrineRand.nextInt(750000) == 0) {
                int xx = x + shrineRand.nextInt(16) + 8;
                int zz = z + shrineRand.nextInt(16) + 8;
                new net.minecraft.server.WorldGenHerobrineShrine().a(this.worldObj, shrineRand, xx, 64, zz);
            }

            for (int i = 0; i < 10; ++i) {
                int xx = x + this.rand.nextInt(16);
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16);
                this.clayGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            for (int i = 0; i < 20; ++i) {
                int xx = x + this.rand.nextInt(16);
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16);
                this.dirtGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            for (int i = 0; i < 10; ++i) {
                int xx = x + this.rand.nextInt(16);
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16);
                this.gravelGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            for (int i = 0; i < 20; ++i) {
                int xx = x + this.rand.nextInt(16);
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16);
                this.coalGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            for (int i = 0; i < 10; ++i) {
                int xx = x + this.rand.nextInt(16);
                int yy = this.rand.nextInt(64);
                int zz = z + this.rand.nextInt(16);
                this.ironGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            if (this.rand.nextInt(2) == 0) {
                int xx = x + this.rand.nextInt(16);
                int yy = this.rand.nextInt(32);
                int zz = z + this.rand.nextInt(16);
                this.goldGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            for (int i = 0; i < 8; ++i) {
                int xx = x + this.rand.nextInt(16);
                int yy = this.rand.nextInt(16);
                int zz = z + this.rand.nextInt(16);
                this.redstoneGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            if (this.rand.nextInt(8) == 0) {
                int xx = x + this.rand.nextInt(16);
                int yy = this.rand.nextInt(16);
                int zz = z + this.rand.nextInt(16);
                this.diamondGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            int lapisX = x + this.rand.nextInt(16);
            int lapisY = this.rand.nextInt(16) + this.rand.nextInt(16);
            int lapisZ = z + this.rand.nextInt(16);
            this.lapisGen.a(this.worldObj, this.rand, lapisX, lapisY, lapisZ);

            int treeCount = ((int)this.mobSpawnerNoise.noiseGenerator((double)x * 0.25D, (double)z * 0.25D)) << 3;
            for (int i = 0; i < treeCount; ++i) {
                int xx = x + this.rand.nextInt(16) + 8;
                int zz = z + this.rand.nextInt(16) + 8;
                this.bigTreeGen.a(1.0D, 1.0D, 1.0D);
                this.bigTreeGen.a(this.worldObj, this.rand, xx, this.worldObj.getHighestBlockYAt(xx, zz), zz);
            }

            for (int i = 0; i < 2; ++i) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16) + 8;
                this.yellowFlowerGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            if (this.rand.nextInt(2) == 0) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16) + 8;
                this.redFlowerGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            if (this.rand.nextInt(4) == 0) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16) + 8;
                this.brownMushroomGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            if (this.rand.nextInt(8) == 0) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16) + 8;
                this.redMushroomGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            for (int i = 0; i < 10; ++i) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16) + 8;
                this.reedGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            int cactusX = x + this.rand.nextInt(16) + 8;
            int cactusY = this.rand.nextInt(128);
            int cactusZ = z + this.rand.nextInt(16) + 8;
            this.cactusGen.a(this.worldObj, this.rand, cactusX, cactusY, cactusZ);

            if (this.rand.nextInt(32) == 0) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(128);
                int zz = z + this.rand.nextInt(16) + 8;
                this.pumpkinGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            for (int i = 0; i < 50; ++i) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(this.rand.nextInt(120) + 8);
                int zz = z + this.rand.nextInt(16) + 8;
                this.waterSpringGen.a(this.worldObj, this.rand, xx, yy, zz);
            }

            for (int i = 0; i < 20; ++i) {
                int xx = x + this.rand.nextInt(16) + 8;
                int yy = this.rand.nextInt(this.rand.nextInt(this.rand.nextInt(112) + 8) + 8);
                int zz = z + this.rand.nextInt(16) + 8;
                this.lavaSpringGen.a(this.worldObj, this.rand, xx, yy, zz);
            }
        } finally {
            BlockSand.instaFall = false;
        }
    }

    public boolean saveChunks(boolean saveAll, IProgressUpdate progress) {
        return true;
    }

    public boolean unloadChunks() {
        return false;
    }

    public boolean canSave() {
        return true;
    }

    public String makeString() {
        return "InfdevLevelSource";
    }
}

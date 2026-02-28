package net.minecraft.server.Alpha;

import net.minecraft.server.Block;
import net.minecraft.server.BlockSand;
import net.minecraft.server.Chunk;
import net.minecraft.server.IChunkProvider;
import net.minecraft.server.IProgressUpdate;
import net.minecraft.server.MapGenBase;
import net.minecraft.server.MapGenCaves;
import net.minecraft.server.Material;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenerator;

import java.util.Random;

public class AlphaChunkProvider implements IChunkProvider {
    private final Random rand;
    private final NoiseGeneratorOctaves noiseGen1;
    private final NoiseGeneratorOctaves noiseGen2;
    private final NoiseGeneratorOctaves noiseGen3;
    private final NoiseGeneratorOctaves noiseGen4;
    private final NoiseGeneratorOctaves noiseGen5;
    public final NoiseGeneratorOctaves noiseGen6;
    public final NoiseGeneratorOctaves noiseGen7;
    public final NoiseGeneratorOctaves mobSpawnerNoise;
    private final World worldObj;
    private double[] noiseArray;
    private double[] sandNoise = new double[256];
    private double[] gravelNoise = new double[256];
    private double[] stoneNoise = new double[256];
    private final MapGenBase caveGenerator = new MapGenCaves();
    private double[] noise3;
    private double[] noise1;
    private double[] noise2;
    private double[] noise6;
    private double[] noise7;
    private final int[][] unused = new int[32][32];

    public AlphaChunkProvider(World world, long seed, boolean mapFeaturesEnabled) {
        this(world, seed);
    }

    public AlphaChunkProvider(World world, long seed) {
        this.worldObj = world;
        this.rand = new Random(seed);
        this.noiseGen1 = new NoiseGeneratorOctaves(this.rand, 16);
        this.noiseGen2 = new NoiseGeneratorOctaves(this.rand, 16);
        this.noiseGen3 = new NoiseGeneratorOctaves(this.rand, 8);
        this.noiseGen4 = new NoiseGeneratorOctaves(this.rand, 4);
        this.noiseGen5 = new NoiseGeneratorOctaves(this.rand, 4);
        this.noiseGen6 = new NoiseGeneratorOctaves(this.rand, 10);
        this.noiseGen7 = new NoiseGeneratorOctaves(this.rand, 16);
        this.mobSpawnerNoise = new NoiseGeneratorOctaves(this.rand, 8);
    }

    public void generateTerrain(int chunkX, int chunkZ, byte[] blocks) {
        byte noiseHRes = 4;
        byte seaLevel = 64;
        int xSize = noiseHRes + 1;
        byte ySize = 17;
        int zSize = noiseHRes + 1;
        boolean snowWorld = this.worldObj.worldData.isSnowWorld();

        this.noiseArray = this.initializeNoiseField(this.noiseArray, chunkX * noiseHRes, 0, chunkZ * noiseHRes, xSize, ySize, zSize);

        for (int cellX = 0; cellX < noiseHRes; ++cellX) {
            for (int cellZ = 0; cellZ < noiseHRes; ++cellZ) {
                for (int cellY = 0; cellY < 16; ++cellY) {
                    double stepY = 0.125D;
                    double nW = this.noiseArray[((cellX + 0) * zSize + cellZ + 0) * ySize + cellY + 0];
                    double nE = this.noiseArray[((cellX + 0) * zSize + cellZ + 1) * ySize + cellY + 0];
                    double nS = this.noiseArray[((cellX + 1) * zSize + cellZ + 0) * ySize + cellY + 0];
                    double nSE = this.noiseArray[((cellX + 1) * zSize + cellZ + 1) * ySize + cellY + 0];
                    double nWUp = (this.noiseArray[((cellX + 0) * zSize + cellZ + 0) * ySize + cellY + 1] - nW) * stepY;
                    double nEUp = (this.noiseArray[((cellX + 0) * zSize + cellZ + 1) * ySize + cellY + 1] - nE) * stepY;
                    double nSUp = (this.noiseArray[((cellX + 1) * zSize + cellZ + 0) * ySize + cellY + 1] - nS) * stepY;
                    double nSEUp = (this.noiseArray[((cellX + 1) * zSize + cellZ + 1) * ySize + cellY + 1] - nSE) * stepY;

                    for (int subY = 0; subY < 8; ++subY) {
                        double stepX = 0.25D;
                        double iNW = nW;
                        double iNE = nE;
                        double dNW = (nS - nW) * stepX;
                        double dNE = (nSE - nE) * stepX;

                        for (int subX = 0; subX < 4; ++subX) {
                            int index = (subX + cellX * 4) << 11 | (0 + cellZ * 4) << 7 | cellY * 8 + subY;
                            short heightStep = 128;
                            double stepZ = 0.25D;
                            double noiseVal = iNW;
                            double dNoiseZ = (iNE - iNW) * stepZ;

                            for (int subZ = 0; subZ < 4; ++subZ) {
                                int blockID = 0;
                                if (cellY * 8 + subY < seaLevel) {
                                    if (snowWorld && cellY * 8 + subY >= seaLevel - 1) {
                                        blockID = Block.ICE.id;
                                    } else {
                                        blockID = Block.STATIONARY_WATER.id;
                                    }
                                }

                                if (noiseVal > 0.0D) {
                                    blockID = Block.STONE.id;
                                }

                                blocks[index] = (byte) blockID;
                                index += heightStep;
                                noiseVal += dNoiseZ;
                            }

                            iNW += dNW;
                            iNE += dNE;
                        }

                        nW += nWUp;
                        nE += nEUp;
                        nS += nSUp;
                        nSE += nSEUp;
                    }
                }
            }
        }
    }

    public void replaceSurfaceBlocks(int chunkX, int chunkZ, byte[] blocks) {
        byte seaLevel = 64;
        double noiseScale = 1.0D / 32.0D;

        this.sandNoise = this.noiseGen4.generateNoiseOctaves(this.sandNoise, (double) (chunkX * 16), (double) (chunkZ * 16), 0.0D, 16, 16, 1, noiseScale, noiseScale, 1.0D);
        this.gravelNoise = this.noiseGen4.generateNoiseOctaves(this.gravelNoise, (double) (chunkZ * 16), 109.0134D, (double) (chunkX * 16), 16, 1, 16, noiseScale, 1.0D, noiseScale);
        this.stoneNoise = this.noiseGen5.generateNoiseOctaves(this.stoneNoise, (double) (chunkX * 16), (double) (chunkZ * 16), 0.0D, 16, 16, 1, noiseScale * 2.0D, noiseScale * 2.0D, noiseScale * 2.0D);

        for (int localX = 0; localX < 16; ++localX) {
            for (int localZ = 0; localZ < 16; ++localZ) {
                boolean useSand = this.sandNoise[localX + localZ * 16] + this.rand.nextDouble() * 0.2D > 0.0D;
                boolean useGravel = this.gravelNoise[localX + localZ * 16] + this.rand.nextDouble() * 0.2D > 3.0D;
                int dirtDepth = (int) (this.stoneNoise[localX + localZ * 16] / 3.0D + 3.0D + this.rand.nextDouble() * 0.25D);
                int surfaceTracker = -1;
                byte topBlock = (byte) Block.GRASS.id;
                byte fillerBlock = (byte) Block.DIRT.id;

                for (int y = 127; y >= 0; --y) {
                    int index = (localX * 16 + localZ) * 128 + y;
                    if (y <= 0 + this.rand.nextInt(6) - 1) {
                        blocks[index] = (byte) Block.BEDROCK.id;
                        continue;
                    }

                    byte current = blocks[index];
                    if (current == 0) {
                        surfaceTracker = -1;
                    } else if (current == Block.STONE.id) {
                        if (surfaceTracker == -1) {
                            if (dirtDepth <= 0) {
                                topBlock = 0;
                                fillerBlock = (byte) Block.STONE.id;
                            } else if (y >= seaLevel - 4 && y <= seaLevel + 1) {
                                topBlock = (byte) Block.GRASS.id;
                                fillerBlock = (byte) Block.DIRT.id;
                                if (useGravel) {
                                    topBlock = 0;
                                    fillerBlock = (byte) Block.GRAVEL.id;
                                }
                                if (useSand) {
                                    topBlock = (byte) Block.SAND.id;
                                    fillerBlock = (byte) Block.SAND.id;
                                }
                            }

                            if (y < seaLevel && topBlock == 0) {
                                topBlock = (byte) Block.STATIONARY_WATER.id;
                            }

                            surfaceTracker = dirtDepth;
                            if (y >= seaLevel - 1) {
                                blocks[index] = topBlock;
                            } else {
                                blocks[index] = fillerBlock;
                            }
                        } else if (surfaceTracker > 0) {
                            --surfaceTracker;
                            blocks[index] = fillerBlock;
                        }
                    }
                }
            }
        }
    }

    public Chunk getOrCreateChunk(int chunkX, int chunkZ) {
        this.rand.setSeed((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L);
        byte[] blocks = new byte[32768];
        Chunk chunk = new Chunk(this.worldObj, blocks, chunkX, chunkZ);

        this.generateTerrain(chunkX, chunkZ, blocks);
        this.replaceSurfaceBlocks(chunkX, chunkZ, blocks);
        this.caveGenerator.a(this, this.worldObj, chunkX, chunkZ, blocks);

        chunk.initLighting();
        return chunk;
    }

    private double[] initializeNoiseField(double[] noise, int xPos, int yPos, int zPos, int xSize, int ySize, int zSize) {
        if (noise == null) {
            noise = new double[xSize * ySize * zSize];
        }

        double xzScale = 684.412D;
        double yScale = 684.412D;

        this.noise6 = this.noiseGen6.generateNoiseOctaves(this.noise6, xPos, yPos, zPos, xSize, 1, zSize, 1.0D, 0.0D, 1.0D);
        this.noise7 = this.noiseGen7.generateNoiseOctaves(this.noise7, xPos, yPos, zPos, xSize, 1, zSize, 100.0D, 0.0D, 100.0D);
        this.noise3 = this.noiseGen3.generateNoiseOctaves(this.noise3, xPos, yPos, zPos, xSize, ySize, zSize, xzScale / 80.0D, yScale / 160.0D, xzScale / 80.0D);
        this.noise1 = this.noiseGen1.generateNoiseOctaves(this.noise1, xPos, yPos, zPos, xSize, ySize, zSize, xzScale, yScale, xzScale);
        this.noise2 = this.noiseGen2.generateNoiseOctaves(this.noise2, xPos, yPos, zPos, xSize, ySize, zSize, xzScale, yScale, xzScale);

        int idx = 0;
        int idx7 = 0;

        for (int x = 0; x < xSize; ++x) {
            for (int z = 0; z < zSize; ++z) {
                double d6 = (this.noise6[idx7] + 256.0D) / 512.0D;
                if (d6 > 1.0D) {
                    d6 = 1.0D;
                }

                double d7 = 0.0D;
                double d8 = this.noise7[idx7] / 8000.0D;
                if (d8 < 0.0D) {
                    d8 = -d8;
                }

                d8 = d8 * 3.0D - 3.0D;
                if (d8 < 0.0D) {
                    d8 /= 2.0D;
                    if (d8 < -1.0D) {
                        d8 = -1.0D;
                    }

                    d8 /= 1.4D;
                    d8 /= 2.0D;
                    d6 = 0.0D;
                } else {
                    if (d8 > 1.0D) {
                        d8 = 1.0D;
                    }

                    d8 /= 6.0D;
                }

                d6 += 0.5D;
                d8 = d8 * ySize / 16.0D;
                double yCenter = ySize / 2.0D + d8 * 4.0D;
                ++idx7;

                for (int y = 0; y < ySize; ++y) {
                    double d10;
                    double yDist = (y - yCenter) * 12.0D / d6;
                    if (yDist < 0.0D) {
                        yDist *= 4.0D;
                    }

                    double n1 = this.noise1[idx] / 512.0D;
                    double n2 = this.noise2[idx] / 512.0D;
                    double n3 = (this.noise3[idx] / 10.0D + 1.0D) / 2.0D;
                    if (n3 < 0.0D) {
                        d10 = n1;
                    } else if (n3 > 1.0D) {
                        d10 = n2;
                    } else {
                        d10 = n1 + (n2 - n1) * n3;
                    }

                    d10 -= yDist;
                    if (y > ySize - 4) {
                        double d13 = (y - (ySize - 4)) / 3.0F;
                        d10 = d10 * (1.0D - d13) + -10.0D * d13;
                    }

                    if (y < d7) {
                        double d14 = (d7 - y) / 4.0D;
                        if (d14 < 0.0D) {
                            d14 = 0.0D;
                        }
                        if (d14 > 1.0D) {
                            d14 = 1.0D;
                        }

                        d10 = d10 * (1.0D - d14) + -10.0D * d14;
                    }

                    noise[idx] = d10;
                    ++idx;
                }
            }
        }

        return noise;
    }

    public void populate(IChunkProvider chunkProvider, int chunkX, int chunkZ) {
        BlockSand.instaFall = true;
        int x = chunkX * 16;
        int z = chunkZ * 16;

        this.rand.setSeed(this.worldObj.getSeed());
        long l1 = this.rand.nextLong() / 2L * 2L + 1L;
        long l2 = this.rand.nextLong() / 2L * 2L + 1L;
        this.rand.setSeed((long) chunkX * l1 + (long) chunkZ * l2 ^ this.worldObj.getSeed());

        double d = 0.25D;

        for (int i = 0; i < 8; ++i) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenDungeons()).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 10; ++i) {
            int xx = x + this.rand.nextInt(16);
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16);
            (new AlphaWorldGenClay(32)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 20; ++i) {
            int xx = x + this.rand.nextInt(16);
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16);
            (new AlphaWorldGenMinable(Block.DIRT.id, 32)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 10; ++i) {
            int xx = x + this.rand.nextInt(16);
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16);
            (new AlphaWorldGenMinable(Block.GRAVEL.id, 32)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 20; ++i) {
            int xx = x + this.rand.nextInt(16);
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16);
            (new AlphaWorldGenMinable(Block.COAL_ORE.id, 16)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 20; ++i) {
            int xx = x + this.rand.nextInt(16);
            int yy = this.rand.nextInt(64);
            int zz = z + this.rand.nextInt(16);
            (new AlphaWorldGenMinable(Block.IRON_ORE.id, 8)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 2; ++i) {
            int xx = x + this.rand.nextInt(16);
            int yy = this.rand.nextInt(32);
            int zz = z + this.rand.nextInt(16);
            (new AlphaWorldGenMinable(Block.GOLD_ORE.id, 8)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 8; ++i) {
            int xx = x + this.rand.nextInt(16);
            int yy = this.rand.nextInt(16);
            int zz = z + this.rand.nextInt(16);
            (new AlphaWorldGenMinable(Block.REDSTONE_ORE.id, 7)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 1; ++i) {
            int xx = x + this.rand.nextInt(16);
            int yy = this.rand.nextInt(16);
            int zz = z + this.rand.nextInt(16);
            (new AlphaWorldGenMinable(Block.DIAMOND_ORE.id, 7)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        d = 0.5D;
        int treeCount = (int) ((this.mobSpawnerNoise.generateNoiseOctaves((double) x * d, (double) z * d) / 8.0D + this.rand.nextDouble() * 4.0D + 4.0D) / 3.0D);
        if (treeCount < 0) {
            treeCount = 0;
        }

        if (this.rand.nextInt(10) == 0) {
            ++treeCount;
        }

        Object treeGen = new AlphaWorldGenTrees();
        if (this.rand.nextInt(10) == 0) {
            treeGen = new AlphaWorldGenBigTree();
        }

        for (int i = 0; i < treeCount; ++i) {
            int xx = x + this.rand.nextInt(16) + 8;
            int zz = z + this.rand.nextInt(16) + 8;
            ((WorldGenerator) treeGen).a(1.0D, 1.0D, 1.0D);
            ((WorldGenerator) treeGen).a(this.worldObj, this.rand, xx, this.worldObj.getHighestBlockYAt(xx, zz), zz);
        }

        for (int i = 0; i < 2; ++i) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenFlowers(Block.YELLOW_FLOWER.id)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        if (this.rand.nextInt(2) == 0) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenFlowers(Block.RED_ROSE.id)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        if (this.rand.nextInt(4) == 0) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenFlowers(Block.BROWN_MUSHROOM.id)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        if (this.rand.nextInt(8) == 0) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenFlowers(Block.RED_MUSHROOM.id)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 10; ++i) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenReed()).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 1; ++i) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(128);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenCactus()).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 50; ++i) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(this.rand.nextInt(120) + 8);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenLiquids(Block.WATER.id)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int i = 0; i < 20; ++i) {
            int xx = x + this.rand.nextInt(16) + 8;
            int yy = this.rand.nextInt(this.rand.nextInt(this.rand.nextInt(112) + 8) + 8);
            int zz = z + this.rand.nextInt(16) + 8;
            (new AlphaWorldGenLiquids(Block.LAVA.id)).a(this.worldObj, this.rand, xx, yy, zz);
        }

        for (int snowX = x + 8; snowX < x + 8 + 16; ++snowX) {
            for (int snowZ = z + 8; snowZ < z + 8 + 16; ++snowZ) {
                int topY = this.worldObj.e(snowX, snowZ);
                if (this.worldObj.worldData.isSnowWorld() && topY > 0 && topY < 128 && this.worldObj.getTypeId(snowX, topY, snowZ) == 0 && this.worldObj.getMaterial(snowX, topY - 1, snowZ).isSolid() && this.worldObj.getMaterial(snowX, topY - 1, snowZ) != Material.ICE) {
                    this.worldObj.setTypeId(snowX, topY, snowZ, Block.SNOW.id);
                }
            }
        }

        BlockSand.instaFall = false;
    }

    public boolean isChunkLoaded(int i, int j) {
        return true;
    }

    public Chunk getChunkAt(int i, int j) {
        return this.getOrCreateChunk(i, j);
    }

    public void getChunkAt(IChunkProvider chunkProvider, int i, int j) {
        this.populate(chunkProvider, i, j);
    }

    public boolean saveChunks(boolean flag, IProgressUpdate progress) {
        return true;
    }

    public boolean unloadChunks() {
        return false;
    }

    public boolean canSave() {
        return true;
    }
}

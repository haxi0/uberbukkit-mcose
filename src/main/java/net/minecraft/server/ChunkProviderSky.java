package net.minecraft.server;

import java.util.Random;

public class ChunkProviderSky implements IChunkProvider {
    private static final int SKY_EXTRA_TREE_ATTEMPTS = 6;
    private static final int SKY_COLD_EXTRA_TREE_ATTEMPTS = 2;
    private static final int SKY_BIRCH_TREE_CHANCE = 8;
    private static final int SKY_SANDSTONE_SUPPORT_BASE = 4;
    private static final int SKY_SANDSTONE_SUPPORT_VARIATION = 4;
    private static final double SKY_BASE_STONE_DENSITY = 0.6D;
    private static final double SKY_MIN_STONE_DENSITY = 0.15D;
    private static final double SKY_MAX_ORE_SCALE = 4.0D;
    private static final double SKY_RARE_ORE_EXTRA_MULTIPLIER = 1.35D;
    private static final double SKY_MAX_RARE_ORE_SCALE = 8.0D;
    private static final int SKY_GOLD_MIN_Y = 8;
    private static final int SKY_GOLD_Y_SPAN = 32;
    private static final int SKY_REDSTONE_MIN_Y = 8;
    private static final int SKY_REDSTONE_Y_SPAN = 24;
    private static final int SKY_DIAMOND_MIN_Y = 10;
    private static final int SKY_DIAMOND_Y_SPAN = 24;
    private static final int SKY_LAPIS_MIN_Y = 8;
    private static final int SKY_LAPIS_Y_SPAN = 24;
    private static final int SKY_ORE_SAMPLE_XZ_STEP = 2;
    private static final int SKY_ORE_SAMPLE_Y_STEP = 8;
    private static final int SKY_FOREST_EXTRA_POND_ATTEMPTS_MIN = 1;
    private static final int SKY_FOREST_EXTRA_POND_ATTEMPTS_VARIATION = 2;
    private static final int SKY_FOREST_EXTRA_WATERFALL_ATTEMPTS = 28;
    private static final int SKY_FOREST_POND_SURFACE_OFFSET = 3;
    private static final int SKY_FOREST_WATERFALL_DEPTH_RANGE = 20;
    private static final int SKY_FOREST_MIN_WATER_FEATURE_Y = 24;
    private static final int SKY_FOREST_EXTRA_REED_ATTEMPTS = 8;
    private static final int SKY_FOREST_CASCADE_CHANCE = 3;
    private static final int SKY_FOREST_CASCADE_ATTEMPTS = 3;
    private static final int SKY_FOREST_CASCADE_MIN_DROP = 12;
    private static final int SKY_FOREST_CASCADE_LOWER_OFFSET = 7;
    private static final double SKY_ISLAND_RADIUS_BIAS = 0.2D;
    private static final double SKY_ISLAND_DENSITY_BIAS = 1.25D;
    private static final double SKY_VERTICAL_PINCH_STRENGTH = 0.38D;
    private static final double SKY_UNDERSIDE_CARVE_BASE = 1.05D;
    private static final double SKY_UNDERSIDE_CARVE_MASK_SCALE = 1.5D;
    private static final double SKY_UNDERSIDE_TAPER_STRENGTH = 40.0D;
    private static final double SKY_UNDERSIDE_SPIKE_STRENGTH = 52.0D;
    private static final double SKY_UNDERSIDE_NECK_STRENGTH = 14.0D;
    private static final double SKY_TOP_SHRINK_STRENGTH = 3.5D;
    private static final double SKY_SPIKE_MASK_MIN = 0.1D;

    private Random j;
    private NoiseGeneratorOctaves k;
    private NoiseGeneratorOctaves l;
    private NoiseGeneratorOctaves m;
    private NoiseGeneratorOctaves n;
    private NoiseGeneratorOctaves o;
    public NoiseGeneratorOctaves a;
    public NoiseGeneratorOctaves b;
    public NoiseGeneratorOctaves c;
    private World p;
    private double[] q;
    private double[] r = new double[256];
    private double[] s = new double[256];
    private double[] t = new double[256];
    private MapGenBase u = net.minecraft.server.registry.Carvers.create(new net.minecraft.server.util.ResourceLocation("minecraft","sky_cave"));
    private BiomeBase[] v;
    double[] d;
    double[] e;
    double[] f;
    double[] g;
    double[] h;
    int[][] i = new int[32][32];
    private double[] x = new double[256];
    private double[] w;

    public ChunkProviderSky(World world, long i) {
        this.p = world;
        this.j = new Random(i);
        this.k = new NoiseGeneratorOctaves(this.j, 16);
        this.l = new NoiseGeneratorOctaves(this.j, 16);
        this.m = new NoiseGeneratorOctaves(this.j, 8);
        this.n = new NoiseGeneratorOctaves(this.j, 4);
        this.o = new NoiseGeneratorOctaves(this.j, 4);
        this.a = new NoiseGeneratorOctaves(this.j, 10);
        this.b = new NoiseGeneratorOctaves(this.j, 16);
        this.c = new NoiseGeneratorOctaves(this.j, 8);
    }

    public void a(int i, int j, byte[] abyte, BiomeBase[] abiomebase, double[] adouble) {
        byte b0 = 2;
        int k = b0 + 1;
        byte b1 = 33;
        int l = b0 + 1;
        double d17 = 1.0D / 18.0D;

        this.x = this.c.a(this.x, (double) (i * 16), (double) (j * 16), 0.0D, 16, 16, 1, d17, d17, 1.0D);

        this.q = this.a(this.q, i * b0, 0, j * b0, k, b1, l);

        for (int i1 = 0; i1 < b0; ++i1) {
            for (int j1 = 0; j1 < b0; ++j1) {
                for (int k1 = 0; k1 < 32; ++k1) {
                    double d0 = 0.25D;
                    double d1 = this.q[((i1 + 0) * l + j1 + 0) * b1 + k1 + 0];
                    double d2 = this.q[((i1 + 0) * l + j1 + 1) * b1 + k1 + 0];
                    double d3 = this.q[((i1 + 1) * l + j1 + 0) * b1 + k1 + 0];
                    double d4 = this.q[((i1 + 1) * l + j1 + 1) * b1 + k1 + 0];
                    double d5 = (this.q[((i1 + 0) * l + j1 + 0) * b1 + k1 + 1] - d1) * d0;
                    double d6 = (this.q[((i1 + 0) * l + j1 + 1) * b1 + k1 + 1] - d2) * d0;
                    double d7 = (this.q[((i1 + 1) * l + j1 + 0) * b1 + k1 + 1] - d3) * d0;
                    double d8 = (this.q[((i1 + 1) * l + j1 + 1) * b1 + k1 + 1] - d4) * d0;

                    for (int l1 = 0; l1 < 4; ++l1) {
                        double d9 = 0.125D;
                        double d10 = d1;
                        double d11 = d2;
                        double d12 = (d3 - d1) * d9;
                        double d13 = (d4 - d2) * d9;

                        for (int i2 = 0; i2 < 8; ++i2) {
                            int j2 = i2 + i1 * 8 << 11 | 0 + j1 * 8 << 7 | k1 * 4 + l1;
                            short short1 = 128;
                            double d14 = 0.125D;
                            double d15 = d10;
                            double d16 = (d11 - d10) * d14;

                            for (int k2 = 0; k2 < 8; ++k2) {
                                int localX = i1 * 8 + i2;
                                int localY = k1 * 4 + l1;
                                int localZ = j1 * 8 + k2;
                                double spikeMask = this.x[localX + localZ * 16] * 0.5D + 0.5D;
                                if (spikeMask < 0.0D) {
                                    spikeMask = 0.0D;
                                } else if (spikeMask > 1.0D) {
                                    spikeMask = 1.0D;
                                }

                                double stoneThreshold = 0.0D;
                                if (localY < 64) {
                                    double undersideDepth = (64.0D - (double) localY) / 64.0D;
                                    stoneThreshold = undersideDepth * (SKY_UNDERSIDE_CARVE_BASE - spikeMask * SKY_UNDERSIDE_CARVE_MASK_SCALE);
                                }

                                int l2 = 0;
                                if (d15 > stoneThreshold) {
                                    l2 = Block.STONE.id;
                                }

                                abyte[j2] = (byte) l2;
                                j2 += short1;
                                d15 += d16;
                            }

                            d10 += d12;
                            d11 += d13;
                        }

                        d1 += d5;
                        d2 += d6;
                        d3 += d7;
                        d4 += d8;
                    }
                }
            }
        }
    }

    public void a(int i, int j, byte[] abyte, BiomeBase[] abiomebase) {
        double d0 = 0.03125D;

        this.r = this.n.a(this.r, (double) (i * 16), (double) (j * 16), 0.0D, 16, 16, 1, d0, d0, 1.0D);
        this.s = this.n.a(this.s, (double) (i * 16), 109.0134D, (double) (j * 16), 16, 1, 16, d0, 1.0D, d0);
        this.t = this.o.a(this.t, (double) (i * 16), (double) (j * 16), 0.0D, 16, 16, 1, d0 * 2.0D, d0 * 2.0D, d0 * 2.0D);

        for (int k = 0; k < 16; ++k) {
            for (int l = 0; l < 16; ++l) {
                BiomeBase biomebase = abiomebase[k + l * 16];
                int i1 = (int) (this.t[k + l * 16] / 3.0D + 3.0D + this.j.nextDouble() * 0.25D);
                int j1 = -1;
                byte b0 = biomebase.p;
                byte b1 = biomebase.q;
                if (biomebase == BiomeBase.DESERT) {
                    b1 = (byte) Block.STONE.id;
                }

                for (int k1 = 127; k1 >= 0; --k1) {
                    int l1 = (l * 16 + k) * 128 + k1;
                    byte b2 = abyte[l1];

                    if (b2 == 0) {
                        j1 = -1;
                    } else if (b2 == Block.STONE.id) {
                        if (j1 == -1) {
                            if (i1 <= 0) {
                                b0 = 0;
                                b1 = (byte) Block.STONE.id;
                            }

                            j1 = i1;
                            if (k1 >= 0) {
                                abyte[l1] = b0;
                            } else {
                                abyte[l1] = b1;
                            }
                        } else if (j1 > 0) {
                            --j1;
                            abyte[l1] = b1;
                            if (j1 == 0 && b1 == Block.SAND.id) {
                                j1 = SKY_SANDSTONE_SUPPORT_BASE + this.j.nextInt(SKY_SANDSTONE_SUPPORT_VARIATION);
                                b1 = (byte) Block.SANDSTONE.id;
                            }
                        }
                    }
                }
            }
        }
    }

    public Chunk getChunkAt(int i, int j) {
        return this.getOrCreateChunk(i, j);
    }

    public Chunk getOrCreateChunk(int i, int j) {
        this.j.setSeed((long) i * 341873128712L + (long) j * 132897987541L);
        byte[] abyte = new byte['\u8000'];
        Chunk chunk = new Chunk(this.p, abyte, i, j);

        this.v = this.p.getWorldChunkManager().a(this.v, i * 16, j * 16, 16, 16);
        double[] adouble = this.p.getWorldChunkManager().temperature;

        this.a(i, j, abyte, this.v, adouble);
        net.minecraft.server.registry.SurfaceBuilders.applySky(this, i, j, abyte, this.v);
        this.u.a(this, this.p, i, j, abyte);
        chunk.initLighting();
        return chunk;
    }

    private double[] a(double[] adouble, int i, int j, int k, int l, int i1, int j1) {
        if (adouble == null) {
            adouble = new double[l * i1 * j1];
        }

        double d0 = 684.412D;
        double d1 = 684.412D;
        double[] adouble1 = this.p.getWorldChunkManager().temperature;
        double[] adouble2 = this.p.getWorldChunkManager().rain;

        this.g = this.a.a(this.g, i, k, l, j1, 1.121D, 1.121D, 0.5D);
        this.h = this.b.a(this.h, i, k, l, j1, 200.0D, 200.0D, 0.5D);
        d0 *= 2.0D;
        this.d = this.m.a(this.d, (double) i, (double) j, (double) k, l, i1, j1, d0 / 80.0D, d1 / 160.0D, d0 / 80.0D);
        this.e = this.k.a(this.e, (double) i, (double) j, (double) k, l, i1, j1, d0, d1, d0);
        this.f = this.l.a(this.f, (double) i, (double) j, (double) k, l, i1, j1, d0, d1, d0);
        int k1 = 0;
        int l1 = 0;
        int i2 = 16 / l;

        for (int j2 = 0; j2 < l; ++j2) {
            int k2 = j2 * i2 + i2 / 2;

            for (int l2 = 0; l2 < j1; ++l2) {
                int i3 = l2 * i2 + i2 / 2;
                double d2 = adouble1[k2 * 16 + i3];
                double d3 = adouble2[k2 * 16 + i3] * d2;
                double d4 = 1.0D - d3;

                d4 *= d4;
                d4 *= d4;
                d4 = 1.0D - d4;
                double d5 = (this.g[l1] + 256.0D) / 512.0D;

                d5 *= d4;
                if (d5 > 1.0D) {
                    d5 = 1.0D;
                }

                double d6 = this.h[l1] / 8000.0D;

                if (d6 < 0.0D) {
                    d6 = -d6 * 0.3D;
                }

                d6 = d6 * 3.0D - 2.0D;
                if (d6 < 0.0D) {
                    d6 /= 2.0D;
                    if (d6 < -1.0D) {
                        d6 = -1.0D;
                    }

                    d6 /= 1.4D;
                    d6 /= 2.0D;
                } else {
                    if (d6 > 1.0D) {
                        d6 = 1.0D;
                    }

                    d6 /= 8.0D;
                }

                if (d5 < 0.0D) {
                    d5 = 0.0D;
                }

                d5 += 0.5D + SKY_ISLAND_RADIUS_BIAS;
                d6 = d6 * (double) i1 / 16.0D;
                ++l1;
                double d7 = (double) i1 / 2.0D + d6 * 4.0D;

                for (int j3 = 0; j3 < i1; ++j3) {
                    double d8 = 0.0D;
                    double d9 = ((double) j3 - d7) * 8.0D / d5;

                    if (d9 < 0.0D) {
                        d9 *= -1.0D;
                    }

                    double d10 = this.e[k1] / 512.0D;
                    double d11 = this.f[k1] / 512.0D;
                    double d12 = (this.d[k1] / 10.0D + 1.0D) / 2.0D;

                    if (d12 < 0.0D) {
                        d8 = d10;
                    } else if (d12 > 1.0D) {
                        d8 = d11;
                    } else {
                        d8 = d10 + (d11 - d10) * d12;
                    }

                    d8 -= 8.0D;
                    d8 += SKY_ISLAND_DENSITY_BIAS;
                    d8 -= d9 * SKY_VERTICAL_PINCH_STRENGTH;
                    if ((double) j3 < d7) {
                        double d14 = (d7 - (double) j3) / d7;
                        double d15 = 1.15D - d12;
                        if (d15 < SKY_SPIKE_MASK_MIN) {
                            d15 = SKY_SPIKE_MASK_MIN;
                        }

                        d8 -= d14 * d14 * SKY_UNDERSIDE_TAPER_STRENGTH * d15;
                        d8 -= d14 * SKY_UNDERSIDE_NECK_STRENGTH;
                        d8 += d14 * d14 * d14 * d14 * SKY_UNDERSIDE_SPIKE_STRENGTH * d12 * d12;
                    } else {
                        double d14 = ((double) j3 - d7) / ((double) i1 - d7);
                        d8 -= d14 * d14 * SKY_TOP_SHRINK_STRENGTH;
                    }

                    byte b0 = 32;
                    double d13;

                    if (j3 > i1 - b0) {
                        d13 = (double) ((float) (j3 - (i1 - b0)) / ((float) b0 - 1.0F));
                        d8 = d8 * (1.0D - d13) + -30.0D * d13;
                    }

                    b0 = 8;
                    if (j3 < b0) {
                        d13 = (double) ((float) (b0 - j3) / ((float) b0 - 1.0F));
                        d8 = d8 * (1.0D - d13) + -30.0D * d13;
                    }

                    adouble[k1] = d8;
                    ++k1;
                }
            }
        }

        return adouble;
    }

    public boolean isChunkLoaded(int i, int j) {
        return true;
    }

    private boolean isTundraLikeSkyBiome(BiomeBase biomebase) {
        return biomebase == BiomeBase.TUNDRA || biomebase == BiomeBase.ICE_DESERT;
    }

    private boolean isForestySkyBiome(BiomeBase biomebase) {
        return biomebase == BiomeBase.FOREST
                || biomebase == BiomeBase.RAINFOREST
                || biomebase == BiomeBase.SEASONAL_FOREST
                || biomebase == BiomeBase.TAIGA;
    }

    private boolean isSolidBlock(int i, int j, int k) {
        int l = this.p.getTypeId(i, j, k);
        if (l <= 0 || l >= Block.byId.length) {
            return false;
        }

        Block block = Block.byId[l];
        return block != null && block.material.isSolid();
    }

    private int findSkySurfaceY(int i, int j, int k) {
        int l = Math.min(k, 126);
        for (int i1 = l; i1 >= 2; --i1) {
            if (this.isSolidBlock(i, i1, j) && this.p.isEmpty(i, i1 + 1, j) && this.p.isEmpty(i, i1 + 2, j)) {
                return i1;
            }
        }

        return -1;
    }

    private boolean generateForestySkyCascadeFeature(int i, int j) {
        int k = i + this.j.nextInt(16) + 8;
        int l = j + this.j.nextInt(16) + 8;
        int i1 = this.findSkySurfaceY(k, l, 126);
        if (i1 < SKY_FOREST_MIN_WATER_FEATURE_Y + SKY_FOREST_CASCADE_MIN_DROP + 6) {
            return false;
        }

        int j1 = this.findSkySurfaceY(k, l, i1 - SKY_FOREST_CASCADE_MIN_DROP);
        if (j1 < SKY_FOREST_MIN_WATER_FEATURE_Y) {
            return false;
        }

        int k1 = k + this.j.nextInt(SKY_FOREST_CASCADE_LOWER_OFFSET) - this.j.nextInt(SKY_FOREST_CASCADE_LOWER_OFFSET);
        int l1 = l + this.j.nextInt(SKY_FOREST_CASCADE_LOWER_OFFSET) - this.j.nextInt(SKY_FOREST_CASCADE_LOWER_OFFSET);
        int i2 = this.findSkySurfaceY(k1, l1, i1 - SKY_FOREST_CASCADE_MIN_DROP);
        if (i2 >= SKY_FOREST_MIN_WATER_FEATURE_Y && i1 - i2 >= SKY_FOREST_CASCADE_MIN_DROP) {
            j1 = i2;
        } else {
            k1 = k;
            l1 = l;
        }

        if (i1 - j1 < SKY_FOREST_CASCADE_MIN_DROP) {
            return false;
        }

        int j2 = i1 - 1 - this.j.nextInt(2);
        int k2 = j1 - 1;
        if (j2 <= k2 + 5) {
            return false;
        }

        (new WorldGenLakes(Block.STATIONARY_WATER.id)).a(this.p, this.j, k, j2, l);
        (new WorldGenLakes(Block.STATIONARY_WATER.id)).a(this.p, this.j, k1, k2, l1);

        int l2 = Integer.compare(k1, k);
        int i3 = Integer.compare(l1, l);
        int j3 = k + l2;
        int k3 = l + i3;
        int l3 = i1 + 1;
        if (!this.p.isEmpty(j3, l3, k3)) {
            j3 = k;
            k3 = l;
        }

        if (this.p.isEmpty(j3, l3, k3)) {
            this.p.setTypeId(j3, l3, k3, Block.WATER.id);
        }

        return true;
    }

    private void generateForestySkyWaterFeatures(int i, int j) {
        int k = SKY_FOREST_EXTRA_POND_ATTEMPTS_MIN + this.j.nextInt(SKY_FOREST_EXTRA_POND_ATTEMPTS_VARIATION);

        int l;
        int i1;
        int j1;
        int k1;
        for (l = 0; l < k; ++l) {
            i1 = i + this.j.nextInt(16) + 8;
            j1 = j + this.j.nextInt(16) + 8;
            k1 = this.p.getHighestBlockYAt(i1, j1) - this.j.nextInt(SKY_FOREST_POND_SURFACE_OFFSET) - 1;
            if (k1 > SKY_FOREST_MIN_WATER_FEATURE_Y && k1 < 124) {
                (new WorldGenLakes(Block.STATIONARY_WATER.id)).a(this.p, this.j, i1, k1, j1);
            }
        }

        for (l = 0; l < SKY_FOREST_EXTRA_WATERFALL_ATTEMPTS; ++l) {
            i1 = i + this.j.nextInt(16) + 8;
            j1 = j + this.j.nextInt(16) + 8;
            k1 = this.p.getHighestBlockYAt(i1, j1) - this.j.nextInt(SKY_FOREST_WATERFALL_DEPTH_RANGE);
            if (k1 > SKY_FOREST_MIN_WATER_FEATURE_Y) {
                (new WorldGenLiquids(Block.WATER.id)).a(this.p, this.j, i1, k1, j1);
            }
        }

        if (this.j.nextInt(SKY_FOREST_CASCADE_CHANCE) == 0) {
            for (l = 0; l < SKY_FOREST_CASCADE_ATTEMPTS; ++l) {
                if (this.generateForestySkyCascadeFeature(i, j)) {
                    break;
                }
            }
        }
    }

    private int getSkyExtraTreeAttemptsForBiome(BiomeBase biomebase) {
        return this.isTundraLikeSkyBiome(biomebase) ? SKY_COLD_EXTRA_TREE_ATTEMPTS : SKY_EXTRA_TREE_ATTEMPTS;
    }

    private WorldGenerator getSpruceTreeGenerator() {
        return (WorldGenerator) (this.j.nextInt(3) == 0 ? new WorldGenTaiga1() : new WorldGenTaiga2());
    }

    private WorldGenerator getSkyTreeGeneratorForBiome(BiomeBase biomebase) {
        if (biomebase == BiomeBase.TAIGA || this.isTundraLikeSkyBiome(biomebase)) {
            return this.getSpruceTreeGenerator();
        }

        return this.applySkyBirchVariation(biomebase.a(this.j));
    }

    private WorldGenerator applySkyBirchVariation(WorldGenerator worldgenerator) {
        return worldgenerator instanceof WorldGenTrees && this.j.nextInt(SKY_BIRCH_TREE_CHANCE) == 0 ? new WorldGenForest() : worldgenerator;
    }

    private double getSkyStoneDensityForChunk(int i, int j) {
        int k = 0;
        int l = 0;

        for (int i1 = 0; i1 < 16; i1 += SKY_ORE_SAMPLE_XZ_STEP) {
            for (int j1 = 0; j1 < 16; j1 += SKY_ORE_SAMPLE_XZ_STEP) {
                for (int k1 = 0; k1 < 128; k1 += SKY_ORE_SAMPLE_Y_STEP) {
                    ++l;
                    if (this.p.getTypeId(i + i1, k1, j + j1) == Block.STONE.id) {
                        ++k;
                    }
                }
            }
        }

        return l == 0 ? 1.0D : (double) k / (double) l;
    }

    private int getSkyScaledOreAttempts(int i, double d0) {
        double d1 = SKY_BASE_STONE_DENSITY / Math.max(d0, SKY_MIN_STONE_DENSITY);

        if (d1 < 1.0D) {
            d1 = 1.0D;
        } else if (d1 > SKY_MAX_ORE_SCALE) {
            d1 = SKY_MAX_ORE_SCALE;
        }

        return Math.max(i, (int) Math.round((double) i * d1));
    }

    private int getSkyScaledRareOreAttempts(int i, double d0) {
        double d1 = SKY_BASE_STONE_DENSITY / Math.max(d0, SKY_MIN_STONE_DENSITY);
        d1 *= SKY_RARE_ORE_EXTRA_MULTIPLIER;

        if (d1 < 1.0D) {
            d1 = 1.0D;
        } else if (d1 > SKY_MAX_RARE_ORE_SCALE) {
            d1 = SKY_MAX_RARE_ORE_SCALE;
        }

        return Math.max(i, (int) Math.round((double) i * d1));
    }

    public void getChunkAt(IChunkProvider ichunkprovider, int i, int j) {
        BlockSand.instaFall = true;
        int k = i * 16;
        int l = j * 16;
        BiomeBase biomebase = this.p.getWorldChunkManager().getBiome(k + 16, l + 16);

        this.j.setSeed(this.p.getSeed());
        long i1 = this.j.nextLong() / 2L * 2L + 1L;
        long j1 = this.j.nextLong() / 2L * 2L + 1L;

        this.j.setSeed((long) i * i1 + (long) j * j1 ^ this.p.getSeed());
        double d0 = 0.25D;
        int k1;
        int l1;
        int i2;

        if (this.j.nextInt(4) == 0) {
            k1 = k + this.j.nextInt(16) + 8;
            l1 = this.j.nextInt(128);
            i2 = l + this.j.nextInt(16) + 8;
            (new WorldGenLakes(Block.STATIONARY_WATER.id)).a(this.p, this.j, k1, l1, i2);
        }

        if (this.j.nextInt(8) == 0) {
            k1 = k + this.j.nextInt(16) + 8;
            l1 = this.j.nextInt(this.j.nextInt(120) + 8);
            i2 = l + this.j.nextInt(16) + 8;
            if (l1 < 64 || this.j.nextInt(10) == 0) {
                (new WorldGenLakes(Block.STATIONARY_LAVA.id)).a(this.p, this.j, k1, l1, i2);
            }
        }

        if (this.isForestySkyBiome(biomebase)) {
            this.generateForestySkyWaterFeatures(k, l);
        }

        double skyStoneDensity = this.getSkyStoneDensityForChunk(k, l);

        int j2;

        net.minecraft.server.WorldGenerator dungeonGen = net.minecraft.server.registry.Features.create("minecraft:dungeon");
        for (k1 = 0; k1 < 8; ++k1) {
            l1 = k + this.j.nextInt(16) + 8;
            i2 = this.j.nextInt(128);
            j2 = l + this.j.nextInt(16) + 8;
            if (dungeonGen != null) dungeonGen.a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < 10; ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = this.j.nextInt(128);
            j2 = l + this.j.nextInt(16);
            (new WorldGenClay(32)).a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < 20; ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = this.j.nextInt(128);
            j2 = l + this.j.nextInt(16);
            (new WorldGenMinable(Block.DIRT.id, 32)).a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < 10; ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = this.j.nextInt(128);
            j2 = l + this.j.nextInt(16);
            (new WorldGenMinable(Block.GRAVEL.id, 32)).a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < this.getSkyScaledOreAttempts(20, skyStoneDensity); ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = this.j.nextInt(128);
            j2 = l + this.j.nextInt(16);
            (new WorldGenMinable(Block.COAL_ORE.id, 16)).a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < this.getSkyScaledOreAttempts(20, skyStoneDensity); ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = this.j.nextInt(64);
            j2 = l + this.j.nextInt(16);
            (new WorldGenMinable(Block.IRON_ORE.id, 8)).a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < this.getSkyScaledRareOreAttempts(2, skyStoneDensity); ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = SKY_GOLD_MIN_Y + this.j.nextInt(SKY_GOLD_Y_SPAN);
            j2 = l + this.j.nextInt(16);
            (new WorldGenMinable(Block.GOLD_ORE.id, 8)).a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < this.getSkyScaledOreAttempts(8, skyStoneDensity); ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = SKY_REDSTONE_MIN_Y + this.j.nextInt(SKY_REDSTONE_Y_SPAN);
            j2 = l + this.j.nextInt(16);
            (new WorldGenMinable(Block.REDSTONE_ORE.id, 7)).a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < this.getSkyScaledRareOreAttempts(1, skyStoneDensity); ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = SKY_DIAMOND_MIN_Y + this.j.nextInt(SKY_DIAMOND_Y_SPAN);
            j2 = l + this.j.nextInt(16);
            (new WorldGenMinable(Block.DIAMOND_ORE.id, 7)).a(this.p, this.j, l1, i2, j2);
        }

        for (k1 = 0; k1 < this.getSkyScaledRareOreAttempts(1, skyStoneDensity); ++k1) {
            l1 = k + this.j.nextInt(16);
            i2 = SKY_LAPIS_MIN_Y + this.j.nextInt(SKY_LAPIS_Y_SPAN) + this.j.nextInt(SKY_LAPIS_Y_SPAN);
            j2 = l + this.j.nextInt(16);
            (new WorldGenMinable(Block.LAPIS_ORE.id, 6)).a(this.p, this.j, l1, i2, j2);
        }

        d0 = 0.5D;
        k1 = (int) ((this.c.a((double) k * d0, (double) l * d0) / 8.0D + this.j.nextDouble() * 4.0D + 4.0D) / 3.0D);
        l1 = 0;
        if (this.j.nextInt(10) == 0) {
            ++l1;
        }

        if (biomebase == BiomeBase.FOREST) {
            l1 += k1 + 5;
        }

        if (biomebase == BiomeBase.RAINFOREST) {
            l1 += k1 + 5;
        }

        if (biomebase == BiomeBase.SEASONAL_FOREST) {
            l1 += k1 + 2;
        }

        if (biomebase == BiomeBase.TAIGA) {
            l1 += k1 + 5;
        }

        if (biomebase == BiomeBase.DESERT) {
            l1 -= 20;
        }

        if (biomebase == BiomeBase.TUNDRA) {
            l1 -= 20;
        }

        if (biomebase == BiomeBase.PLAINS) {
            l1 -= 20;
        }

        int k2;

        for (i2 = 0; i2 < l1; ++i2) {
            j2 = k + this.j.nextInt(16) + 8;
            k2 = l + this.j.nextInt(16) + 8;
            WorldGenerator worldgenerator = this.getSkyTreeGeneratorForBiome(biomebase);

            worldgenerator.a(1.0D, 1.0D, 1.0D);
            worldgenerator.a(this.p, this.j, j2, this.p.getHighestBlockYAt(j2, k2), k2);
        }

        for (i2 = 0; i2 < this.getSkyExtraTreeAttemptsForBiome(biomebase); ++i2) {
            j2 = k + this.j.nextInt(16) + 8;
            k2 = l + this.j.nextInt(16) + 8;
            int i3 = this.p.getHighestBlockYAt(j2, k2);

            while (i3 > 1 && !this.p.getMaterial(j2, i3 - 1, k2).isSolid()) {
                --i3;
            }

            if (i3 > 1) {
                int j3 = this.p.getTypeId(j2, i3 - 1, k2);

                if (j3 == Block.GRASS.id || j3 == Block.DIRT.id) {
                    WorldGenerator worldgenerator = this.getSkyTreeGeneratorForBiome(biomebase);

                    worldgenerator.a(1.0D, 1.0D, 1.0D);
                    worldgenerator.a(this.p, this.j, j2, i3, k2);
                }
            }
        }

        int l2;

        for (i2 = 0; i2 < 2; ++i2) {
            j2 = k + this.j.nextInt(16) + 8;
            k2 = this.j.nextInt(128);
            l2 = l + this.j.nextInt(16) + 8;
            (new WorldGenFlowers(Block.YELLOW_FLOWER.id)).a(this.p, this.j, j2, k2, l2);
        }

        if (this.j.nextInt(2) == 0) {
            i2 = k + this.j.nextInt(16) + 8;
            j2 = this.j.nextInt(128);
            k2 = l + this.j.nextInt(16) + 8;
            (new WorldGenFlowers(Block.RED_ROSE.id)).a(this.p, this.j, i2, j2, k2);
        }

        if (this.j.nextInt(4) == 0) {
            i2 = k + this.j.nextInt(16) + 8;
            j2 = this.j.nextInt(128);
            k2 = l + this.j.nextInt(16) + 8;
            (new WorldGenFlowers(Block.BROWN_MUSHROOM.id)).a(this.p, this.j, i2, j2, k2);
        }

        if (this.j.nextInt(8) == 0) {
            i2 = k + this.j.nextInt(16) + 8;
            j2 = this.j.nextInt(128);
            k2 = l + this.j.nextInt(16) + 8;
            (new WorldGenFlowers(Block.RED_MUSHROOM.id)).a(this.p, this.j, i2, j2, k2);
        }

        for (i2 = 0; i2 < 10; ++i2) {
            j2 = k + this.j.nextInt(16) + 8;
            k2 = this.j.nextInt(128);
            l2 = l + this.j.nextInt(16) + 8;
            (new WorldGenReed()).a(this.p, this.j, j2, k2, l2);
        }

        if (this.isForestySkyBiome(biomebase)) {
            for (i2 = 0; i2 < SKY_FOREST_EXTRA_REED_ATTEMPTS; ++i2) {
                j2 = k + this.j.nextInt(16) + 8;
                l2 = l + this.j.nextInt(16) + 8;
                k2 = this.p.getHighestBlockYAt(j2, l2) - this.j.nextInt(8);
                (new WorldGenReed()).a(this.p, this.j, j2, k2, l2);
            }
        }

        if (this.j.nextInt(32) == 0) {
            i2 = k + this.j.nextInt(16) + 8;
            j2 = this.j.nextInt(128);
            k2 = l + this.j.nextInt(16) + 8;
            (new WorldGenPumpkin()).a(this.p, this.j, i2, j2, k2);
        }

        i2 = 0;
        if (biomebase == BiomeBase.DESERT) {
            i2 += 10;
        }

        int i3;

        for (j2 = 0; j2 < i2; ++j2) {
            k2 = k + this.j.nextInt(16) + 8;
            l2 = this.j.nextInt(128);
            i3 = l + this.j.nextInt(16) + 8;
            (new WorldGenCactus()).a(this.p, this.j, k2, l2, i3);
        }

        for (j2 = 0; j2 < 50; ++j2) {
            k2 = k + this.j.nextInt(16) + 8;
            l2 = this.j.nextInt(this.j.nextInt(120) + 8);
            i3 = l + this.j.nextInt(16) + 8;
            (new WorldGenLiquids(Block.WATER.id)).a(this.p, this.j, k2, l2, i3);
        }

        for (j2 = 0; j2 < 20; ++j2) {
            k2 = k + this.j.nextInt(16) + 8;
            l2 = this.j.nextInt(this.j.nextInt(this.j.nextInt(112) + 8) + 8);
            i3 = l + this.j.nextInt(16) + 8;
            (new WorldGenLiquids(Block.LAVA.id)).a(this.p, this.j, k2, l2, i3);
        }

        this.w = this.p.getWorldChunkManager().a(this.w, k + 8, l + 8, 16, 16);

        for (j2 = k + 8; j2 < k + 8 + 16; ++j2) {
            for (k2 = l + 8; k2 < l + 8 + 16; ++k2) {
                l2 = j2 - (k + 8);
                i3 = k2 - (l + 8);
                int j3 = this.p.e(j2, k2);
                double d1 = this.w[l2 * 16 + i3] - (double) (j3 - 64) / 64.0D * 0.3D;

                if (d1 < 0.5D && j3 > 0 && j3 < 128 && this.p.isEmpty(j2, j3, k2) && this.p.getMaterial(j2, j3 - 1, k2).isSolid() && this.p.getMaterial(j2, j3 - 1, k2) != Material.ICE) {
                    this.p.setTypeId(j2, j3, k2, Block.SNOW.id);
                }
            }
        }

        SpawnerCreature.a(this.p, biomebase, k + 8, l + 8, 16, 16, this.j);
        BlockSand.instaFall = false;
    }

    public boolean saveChunks(boolean flag, IProgressUpdate iprogressupdate) {
        return true;
    }

    public boolean unloadChunks() {
        return false;
    }

    public boolean canSave() {
        return true;
    }
}

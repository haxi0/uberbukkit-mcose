package net.minecraft.server;

public class WorldChunkManagerSky extends WorldChunkManager {
    private static final int SKY_BIOME_REGION_SIZE = 64;
    private static final int DESERT_SELECTOR_MAX = 82;
    private static final int TUNDRA_SELECTOR_MAX = 205;
    private static final double PLAINS_TEMPERATURE = 0.5D;
    private static final double PLAINS_HUMIDITY = 0.0D;
    private static final double DESERT_TEMPERATURE = 1.0D;
    private static final double DESERT_HUMIDITY = 0.0D;
    private static final double TUNDRA_TEMPERATURE = 0.0D;
    private static final double TUNDRA_HUMIDITY = 0.5D;
    private final long seed;

    public WorldChunkManagerSky() {
        this(0L);
    }
    
    public WorldChunkManagerSky(long seed) {
        this.seed = seed;
    }

    @Override
    public BiomeBase getBiome(int i, int j) {
        return this.getSkyBiome(i, j);
    }

    @Override
    public BiomeBase[] getBiomeData(int i, int j, int k, int l) {
        this.d = this.a(this.d, i, j, k, l);
        return this.d;
    }

    @Override
    public BiomeBase[] a(BiomeBase[] abiomebase, int i, int j, int k, int l) {
        if (abiomebase == null || abiomebase.length < k * l) {
            abiomebase = new BiomeBase[k * l];
        }

        if (this.temperature == null || this.temperature.length < k * l) {
            this.temperature = new double[k * l];
        }

        if (this.rain == null || this.rain.length < k * l) {
            this.rain = new double[k * l];
        }

        for (int z = 0; z < l; ++z) {
            for (int x = 0; x < k; ++x) {
                int idx = x + z * k;
                BiomeBase biomebase = this.getSkyBiome(i + x, j + z);
                abiomebase[idx] = biomebase;
                this.temperature[idx] = this.getSkyTemperature(biomebase);
                this.rain[idx] = this.getSkyHumidity(biomebase);
            }
        }

        return abiomebase;
    }

    @Override
    public double[] a(double[] adouble, int i, int j, int k, int l) {
        if (adouble == null || adouble.length < k * l) {
            adouble = new double[k * l];
        }

        for (int z = 0; z < l; ++z) {
            for (int x = 0; x < k; ++x) {
                int idx = x + z * k;
                BiomeBase biomebase = this.getSkyBiome(i + x, j + z);
                adouble[idx] = this.getSkyTemperature(biomebase);
            }
        }

        return adouble;
    }

    public double[] getRainfall(double[] arr, int x, int z, int width, int depth) {
        if (arr == null || arr.length < width * depth) {
            arr = new double[width * depth];
        }

        for (int dz = 0; dz < depth; ++dz) {
            for (int dx = 0; dx < width; ++dx) {
                int idx = dx + dz * width;
                BiomeBase biomebase = this.getSkyBiome(x + dx, z + dz);
                arr[idx] = this.getSkyHumidity(biomebase);
            }
        }

        return arr;
    }

    @Override
    public double getHumidity(int x, int z) {
        return this.getSkyHumidity(this.getSkyBiome(x, z));
    }

    private BiomeBase getSkyBiome(int x, int z) {
        int regionX = floorDiv(x, SKY_BIOME_REGION_SIZE);
        int regionZ = floorDiv(z, SKY_BIOME_REGION_SIZE);
        long hash = this.seed;
        hash ^= (long) regionX * 341873128712L;
        hash ^= (long) regionZ * 132897987541L;
        hash = hash * 6364136223846793005L + 1442695040888963407L;
        int selector = (int) (hash >>> 24 & 1023L);
        if (selector < DESERT_SELECTOR_MAX) {
            return BiomeBase.DESERT;
        }

        return selector < TUNDRA_SELECTOR_MAX ? BiomeBase.TUNDRA : BiomeBase.PLAINS;
    }

    private double getSkyTemperature(BiomeBase biomebase) {
        if (biomebase == BiomeBase.DESERT) {
            return DESERT_TEMPERATURE;
        }

        return biomebase == BiomeBase.TUNDRA ? TUNDRA_TEMPERATURE : PLAINS_TEMPERATURE;
    }

    private double getSkyHumidity(BiomeBase biomebase) {
        if (biomebase == BiomeBase.DESERT) {
            return DESERT_HUMIDITY;
        }

        return biomebase == BiomeBase.TUNDRA ? TUNDRA_HUMIDITY : PLAINS_HUMIDITY;
    }

    private static int floorDiv(int value, int divisor) {
        int quotient = value / divisor;
        if ((value ^ divisor) < 0 && quotient * divisor != value) {
            --quotient;
        }

        return quotient;
    }
}

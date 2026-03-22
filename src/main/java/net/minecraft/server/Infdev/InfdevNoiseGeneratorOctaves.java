package net.minecraft.server.Infdev;

import java.util.Random;

public final class InfdevNoiseGeneratorOctaves extends InfdevNoiseGenerator {
    private final InfdevNoiseGeneratorPerlin[] generatorCollection;
    private final int octaves;

    public InfdevNoiseGeneratorOctaves(Random random, int octaves) {
        this.octaves = octaves;
        this.generatorCollection = new InfdevNoiseGeneratorPerlin[octaves];

        for (int i = 0; i < octaves; ++i) {
            this.generatorCollection[i] = new InfdevNoiseGeneratorPerlin(random);
        }
    }

    public double noiseGenerator(double x, double z) {
        double value = 0.0D;
        double scale = 1.0D;

        for (int i = 0; i < this.octaves; ++i) {
            value += this.generatorCollection[i].generateNoise(x / scale, z / scale) * scale;
            scale *= 2.0D;
        }

        return value;
    }

    public double generateNoiseOctaves(double x, double y, double z) {
        double value = 0.0D;
        double scale = 1.0D;

        for (int i = 0; i < this.octaves; ++i) {
            value += this.generatorCollection[i].generateNoiseD(x / scale, y / scale, z / scale) * scale;
            scale *= 2.0D;
        }

        return value;
    }
}

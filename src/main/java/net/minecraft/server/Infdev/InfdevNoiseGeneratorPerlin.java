package net.minecraft.server.Infdev;

import java.util.Random;
import net.minecraft.server.MathHelper;

public final class InfdevNoiseGeneratorPerlin extends InfdevNoiseGenerator {
    private final int[] permutations;
    private final double xCoord;
    private final double yCoord;
    private final double zCoord;

    public InfdevNoiseGeneratorPerlin() {
        this(new Random());
    }

    public InfdevNoiseGeneratorPerlin(Random random) {
        this.permutations = new int[512];
        this.xCoord = random.nextDouble() * 256.0D;
        this.yCoord = random.nextDouble() * 256.0D;
        this.zCoord = random.nextDouble() * 256.0D;

        for (int i = 0; i < 256; ++i) {
            this.permutations[i] = i;
        }

        for (int i = 0; i < 256; ++i) {
            int swap = random.nextInt(256 - i) + i;
            int value = this.permutations[i];
            this.permutations[i] = this.permutations[swap];
            this.permutations[swap] = value;
            this.permutations[i + 256] = this.permutations[i];
        }
    }

    private double generateNoiseInternal(double x, double y, double z) {
        double localX = x + this.xCoord;
        double localY = y + this.yCoord;
        double localZ = z + this.zCoord;

        int cellX = MathHelper.floor(localX) & 255;
        int cellY = MathHelper.floor(localY) & 255;
        int cellZ = MathHelper.floor(localZ) & 255;

        localX -= (double)MathHelper.floor(localX);
        localY -= (double)MathHelper.floor(localY);
        localZ -= (double)MathHelper.floor(localZ);

        double fadeX = fade(localX);
        double fadeY = fade(localY);
        double fadeZ = fade(localZ);

        int pXY = this.permutations[cellX] + cellY;
        int pXYZ = this.permutations[pXY] + cellZ;
        int pXY1Z = this.permutations[pXY + 1] + cellZ;

        int pX1Y = this.permutations[cellX + 1] + cellY;
        int pX1YZ = this.permutations[pX1Y] + cellZ;
        int pX1Y1Z = this.permutations[pX1Y + 1] + cellZ;

        return lerp(
            fadeZ,
            lerp(
                fadeY,
                lerp(fadeX,
                    grad(this.permutations[pXYZ], localX, localY, localZ),
                    grad(this.permutations[pX1YZ], localX - 1.0D, localY, localZ)
                ),
                lerp(fadeX,
                    grad(this.permutations[pXY1Z], localX, localY - 1.0D, localZ),
                    grad(this.permutations[pX1Y1Z], localX - 1.0D, localY - 1.0D, localZ)
                )
            ),
            lerp(
                fadeY,
                lerp(fadeX,
                    grad(this.permutations[pXYZ + 1], localX, localY, localZ - 1.0D),
                    grad(this.permutations[pX1YZ + 1], localX - 1.0D, localY, localZ - 1.0D)
                ),
                lerp(fadeX,
                    grad(this.permutations[pXY1Z + 1], localX, localY - 1.0D, localZ - 1.0D),
                    grad(this.permutations[pX1Y1Z + 1], localX - 1.0D, localY - 1.0D, localZ - 1.0D)
                )
            )
        );
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double y, double z) {
        hash &= 15;
        double u = hash < 8 ? x : y;
        double v = hash < 4 ? y : (hash != 12 && hash != 14 ? z : x);
        return ((hash & 1) == 0 ? u : -u) + ((hash & 2) == 0 ? v : -v);
    }

    public double generateNoise(double x, double z) {
        return this.generateNoiseInternal(x, z, 0.0D);
    }

    public double generateNoiseD(double x, double y, double z) {
        return this.generateNoiseInternal(x, y, z);
    }
}

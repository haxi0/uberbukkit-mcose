package net.minecraft.server.Infdev;

import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.MathHelper;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenerator;

public final class InfdevWorldGenMinable extends WorldGenerator {
    private final int minableBlockId;

    public InfdevWorldGenMinable(int blockId) {
        this.minableBlockId = blockId;
    }

    public boolean a(World world, Random random, int x, int y, int z) {
        float angle = random.nextFloat() * (float)Math.PI;
        double startX = (double)((float)(x + 8) + MathHelper.sin(angle) * 2.0F);
        double endX = (double)((float)(x + 8) - MathHelper.sin(angle) * 2.0F);
        double startZ = (double)((float)(z + 8) + MathHelper.cos(angle) * 2.0F);
        double endZ = (double)((float)(z + 8) - MathHelper.cos(angle) * 2.0F);
        double startY = (double)(y + random.nextInt(3) + 2);
        double endY = (double)(y + random.nextInt(3) + 2);

        for (int segment = 0; segment <= 16; ++segment) {
            double centerX = startX + (endX - startX) * (double)segment / 16.0D;
            double centerY = startY + (endY - startY) * (double)segment / 16.0D;
            double centerZ = startZ + (endZ - startZ) * (double)segment / 16.0D;
            double randomSize = random.nextDouble();
            double radiusXZ = (double)(MathHelper.sin((float)segment / 16.0F * (float)Math.PI) + 1.0F) * randomSize + 1.0D;
            double radiusY = (double)(MathHelper.sin((float)segment / 16.0F * (float)Math.PI) + 1.0F) * randomSize + 1.0D;

            int minX = (int)(centerX - radiusXZ / 2.0D);
            int maxX = (int)(centerX + radiusXZ / 2.0D);
            int minY = (int)(centerY - radiusY / 2.0D);
            int maxY = (int)(centerY + radiusY / 2.0D);
            int minZ = (int)(centerZ - radiusXZ / 2.0D);
            int maxZ = (int)(centerZ + radiusXZ / 2.0D);

            for (int oreX = minX; oreX <= maxX; ++oreX) {
                for (int oreY = minY; oreY <= maxY; ++oreY) {
                    for (int oreZ = minZ; oreZ <= maxZ; ++oreZ) {
                        double dx = ((double)oreX + 0.5D - centerX) / (radiusXZ / 2.0D);
                        double dy = ((double)oreY + 0.5D - centerY) / (radiusY / 2.0D);
                        double dz = ((double)oreZ + 0.5D - centerZ) / (radiusXZ / 2.0D);
                        if (dx * dx + dy * dy + dz * dz < 1.0D && world.getTypeId(oreX, oreY, oreZ) == Block.STONE.id) {
                            world.setRawTypeId(oreX, oreY, oreZ, this.minableBlockId);
                        }
                    }
                }
            }
        }

        return true;
    }
}

package net.minecraft.server.Infdev;

import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenerator;

public final class InfdevWorldGenTrees extends WorldGenerator {
    public boolean a(World world, Random random, int x, int y, int z) {
        int height = random.nextInt(3) + 4;
        boolean canGrow = true;

        if (y > 0 && y + height + 1 <= 128) {
            int checkY;
            int checkX;
            int checkZ;
            int blockId;
            for (checkY = y; checkY <= y + 1 + height; ++checkY) {
                byte radius = 1;
                if (checkY == y) {
                    radius = 0;
                }

                if (checkY >= y + 1 + height - 2) {
                    radius = 2;
                }

                for (checkX = x - radius; checkX <= x + radius && canGrow; ++checkX) {
                    for (checkZ = z - radius; checkZ <= z + radius && canGrow; ++checkZ) {
                        if (checkY >= 0 && checkY < 128) {
                            blockId = world.getTypeId(checkX, checkY, checkZ);
                            if (blockId != 0 && blockId != Block.LEAVES.id) {
                                canGrow = false;
                            }
                        } else {
                            canGrow = false;
                        }
                    }
                }
            }

            if (!canGrow) {
                return false;
            } else {
                checkY = world.getTypeId(x, y - 1, z);
                if ((checkY == Block.GRASS.id || checkY == Block.DIRT.id) && y < 128 - height - 1) {
                    world.setRawTypeId(x, y - 1, z, Block.DIRT.id);

                    int leafY;
                    for (leafY = y - 3 + height; leafY <= y + height; ++leafY) {
                        checkX = leafY - (y + height);
                        checkZ = 1 - checkX / 2;

                        for (blockId = x - checkZ; blockId <= x + checkZ; ++blockId) {
                            int offsetX = blockId - x;

                            for (int leafZ = z - checkZ; leafZ <= z + checkZ; ++leafZ) {
                                int offsetZ = leafZ - z;
                                if ((Math.abs(offsetX) != checkZ || Math.abs(offsetZ) != checkZ || random.nextInt(2) != 0 && checkX != 0)
                                    && !Block.o[world.getTypeId(blockId, leafY, leafZ)]) {
                                    world.setRawTypeId(blockId, leafY, leafZ, Block.LEAVES.id);
                                }
                            }
                        }
                    }

                    for (leafY = 0; leafY < height; ++leafY) {
                        if (!Block.o[world.getTypeId(x, y + leafY, z)]) {
                            world.setRawTypeId(x, y + leafY, z, Block.LOG.id);
                        }
                    }

                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }
}

package net.minecraft.server.Infdev;

import java.util.Random;
import net.minecraft.server.Block;
import net.minecraft.server.MathHelper;
import net.minecraft.server.World;
import net.minecraft.server.WorldGenerator;

public final class InfdevWorldGenBigTree extends WorldGenerator {
    private static final byte[] OTHER_COORD_PAIRS = new byte[]{(byte)2, (byte)0, (byte)0, (byte)1, (byte)2, (byte)1};
    private final Random rand = new Random();
    private World worldObj;
    private final int[] basePos = new int[]{0, 0, 0};
    private int heightLimit;
    private int height;
    private double heightAttenuation = 0.618D;
    private double branchSlope = 0.381D;
    private double scaleWidth = 1.0D;
    private double leafDensity = 1.0D;
    private int trunkSize = 1;
    private int heightLimitLimit = 12;
    private int leafDistanceLimit = 4;
    private int[][] leafNodes;

    private void placeBlockLine(int[] from, int[] to, int blockId) {
        int[] delta = new int[]{0, 0, 0};
        byte longestAxis = 0;

        for (byte axis = 0; axis < 3; ++axis) {
            delta[axis] = to[axis] - from[axis];
            if (Math.abs(delta[axis]) > Math.abs(delta[longestAxis])) {
                longestAxis = axis;
            }
        }

        if (delta[longestAxis] != 0) {
            byte axis1 = OTHER_COORD_PAIRS[longestAxis];
            byte axis2 = OTHER_COORD_PAIRS[longestAxis + 3];
            byte step = delta[longestAxis] > 0 ? (byte)1 : (byte)-1;
            double slope1 = (double)delta[axis1] / (double)delta[longestAxis];
            double slope2 = (double)delta[axis2] / (double)delta[longestAxis];
            int[] pos = new int[]{0, 0, 0};
            int progress = 0;

            for (int end = delta[longestAxis] + step; progress != end; progress += step) {
                pos[longestAxis] = MathHelper.floor((double)(from[longestAxis] + progress) + 0.5D);
                pos[axis1] = MathHelper.floor((double)from[axis1] + (double)progress * slope1 + 0.5D);
                pos[axis2] = MathHelper.floor((double)from[axis2] + (double)progress * slope2 + 0.5D);
                this.worldObj.setRawTypeId(pos[0], pos[1], pos[2], blockId);
            }
        }
    }

    private int checkBlockLine(int[] from, int[] to) {
        int[] delta = new int[]{0, 0, 0};
        byte longestAxis = 0;

        for (byte axis = 0; axis < 3; ++axis) {
            delta[axis] = to[axis] - from[axis];
            if (Math.abs(delta[axis]) > Math.abs(delta[longestAxis])) {
                longestAxis = axis;
            }
        }

        if (delta[longestAxis] == 0) {
            return -1;
        } else {
            byte axis1 = OTHER_COORD_PAIRS[longestAxis];
            byte axis2 = OTHER_COORD_PAIRS[longestAxis + 3];
            byte step = delta[longestAxis] > 0 ? (byte)1 : (byte)-1;
            double slope1 = (double)delta[axis1] / (double)delta[longestAxis];
            double slope2 = (double)delta[axis2] / (double)delta[longestAxis];
            int[] pos = new int[]{0, 0, 0};
            int progress = 0;

            int end;
            for (end = delta[longestAxis] + step; progress != end; progress += step) {
                pos[longestAxis] = from[longestAxis] + progress;
                pos[axis1] = (int)((double)from[axis1] + (double)progress * slope1);
                pos[axis2] = (int)((double)from[axis2] + (double)progress * slope2);
                int blockId = this.worldObj.getTypeId(pos[0], pos[1], pos[2]);
                if (blockId != 0 && blockId != Block.LEAVES.id) {
                    break;
                }
            }

            return progress == end ? -1 : Math.abs(progress);
        }
    }

    public void a(double var1, double var3, double var5) {
        this.heightLimitLimit = 12;
        this.leafDistanceLimit = 5;
        this.scaleWidth = 1.0D;
        this.leafDensity = 1.0D;
    }

    public boolean a(World world, Random random, int x, int y, int z) {
        this.worldObj = world;
        long seed = random.nextLong();
        this.rand.setSeed(seed);
        this.basePos[0] = x;
        this.basePos[1] = y;
        this.basePos[2] = z;
        if (this.heightLimit == 0) {
            this.heightLimit = 5 + this.rand.nextInt(this.heightLimitLimit);
        }

        int[] trunkBase = new int[]{this.basePos[0], this.basePos[1], this.basePos[2]};
        int[] trunkTop = new int[]{this.basePos[0], this.basePos[1] + this.heightLimit - 1, this.basePos[2]};
        int belowId = this.worldObj.getTypeId(this.basePos[0], this.basePos[1] - 1, this.basePos[2]);
        boolean canGrow;
        if (belowId != Block.GRASS.id && belowId != Block.DIRT.id) {
            canGrow = false;
        } else {
            int obstruction = this.checkBlockLine(trunkBase, trunkTop);
            if (obstruction == -1) {
                canGrow = true;
            } else if (obstruction < 6) {
                canGrow = false;
            } else {
                this.heightLimit = obstruction;
                canGrow = true;
            }
        }

        if (!canGrow) {
            return false;
        }

        this.height = (int)((double)this.heightLimit * this.heightAttenuation);
        if (this.height >= this.heightLimit) {
            this.height = this.heightLimit - 1;
        }

        int nodesPerLayer = (int)(1.382D + Math.pow(this.leafDensity * (double)this.heightLimit / 13.0D, 2.0D));
        if (nodesPerLayer <= 0) {
            nodesPerLayer = 1;
        }

        int[][] provisionalNodes = new int[nodesPerLayer * this.heightLimit][4];
        int leafY = this.basePos[1] + this.heightLimit - this.leafDistanceLimit;
        int nodeCount = 1;
        int branchBaseY = this.basePos[1] + this.height;
        int layerFromTop = leafY - this.basePos[1];
        provisionalNodes[0][0] = this.basePos[0];
        provisionalNodes[0][1] = leafY;
        provisionalNodes[0][2] = this.basePos[2];
        provisionalNodes[0][3] = branchBaseY;
        --leafY;

        while (layerFromTop >= 0) {
            int node = 0;
            float layerRadius;
            if ((double)layerFromTop < (double)((float)this.heightLimit) * 0.3D) {
                layerRadius = -1.618F;
            } else {
                float halfHeight = (float)this.heightLimit / 2.0F;
                float offset = (float)this.heightLimit / 2.0F - (float)layerFromTop;
                float radius;
                if (offset == 0.0F) {
                    radius = halfHeight;
                } else if (Math.abs(offset) >= halfHeight) {
                    radius = 0.0F;
                } else {
                    radius = (float)Math.sqrt(Math.pow((double)Math.abs(halfHeight), 2.0D) - Math.pow((double)Math.abs(offset), 2.0D));
                }

                radius *= 0.5F;
                layerRadius = radius;
            }

            if (layerRadius < 0.0F) {
                --leafY;
                --layerFromTop;
            } else {
                for (; node < nodesPerLayer; ++node) {
                    double dist = this.scaleWidth * (double)layerRadius * ((double)this.rand.nextFloat() + 0.328D);
                    double angle = (double)this.rand.nextFloat() * 2.0D * 3.14159D;
                    int nodeX = (int)(dist * Math.sin(angle) + (double)this.basePos[0] + 0.5D);
                    int nodeZ = (int)(dist * Math.cos(angle) + (double)this.basePos[2] + 0.5D);
                    int[] nodeBase = new int[]{nodeX, leafY, nodeZ};
                    int[] nodeTop = new int[]{nodeX, leafY + this.leafDistanceLimit, nodeZ};
                    if (this.checkBlockLine(nodeBase, nodeTop) == -1) {
                        int[] branchStart = new int[]{this.basePos[0], this.basePos[1], this.basePos[2]};
                        double horizDist = Math.sqrt(
                            Math.pow((double)Math.abs(this.basePos[0] - nodeBase[0]), 2.0D)
                            + Math.pow((double)Math.abs(this.basePos[2] - nodeBase[2]), 2.0D)
                        );
                        double branchDrop = horizDist * this.branchSlope;
                        if ((double)nodeBase[1] - branchDrop > (double)branchBaseY) {
                            branchStart[1] = branchBaseY;
                        } else {
                            branchStart[1] = (int)((double)nodeBase[1] - branchDrop);
                        }

                        if (this.checkBlockLine(branchStart, nodeBase) == -1) {
                            provisionalNodes[nodeCount][0] = nodeX;
                            provisionalNodes[nodeCount][1] = leafY;
                            provisionalNodes[nodeCount][2] = nodeZ;
                            provisionalNodes[nodeCount][3] = branchStart[1];
                            ++nodeCount;
                        }
                    }
                }

                --leafY;
                --layerFromTop;
            }
        }

        this.leafNodes = new int[nodeCount][4];
        System.arraycopy(provisionalNodes, 0, this.leafNodes, 0, nodeCount);

        for (int i = 0; i < this.leafNodes.length; ++i) {
            int nodeX = this.leafNodes[i][0];
            int nodeY = this.leafNodes[i][1];
            int nodeZ = this.leafNodes[i][2];

            for (int yOffset = nodeY; yOffset < nodeY + this.leafDistanceLimit; ++yOffset) {
                int localY = yOffset - nodeY;
                float radius = localY != 0 && localY != this.leafDistanceLimit - 1 ? 3.0F : 2.0F;
                int r = (int)((double)radius + 0.618D);
                byte axis1 = OTHER_COORD_PAIRS[1];
                byte axis2 = OTHER_COORD_PAIRS[4];
                int[] center = new int[]{nodeX, yOffset, nodeZ};
                int[] pos = new int[]{0, 0, 0};

                for (int off1 = -r; off1 <= r; ++off1) {
                    pos[axis1] = center[axis1] + off1;

                    for (int off2 = -r; off2 <= r; ++off2) {
                        double dist = Math.sqrt(
                            Math.pow((double)Math.abs(off1) + 0.5D, 2.0D)
                            + Math.pow((double)Math.abs(off2) + 0.5D, 2.0D)
                        );
                        if (dist > (double)radius) {
                            continue;
                        }

                        pos[axis2] = center[axis2] + off2;
                        pos[1] = center[1];
                        int existing = this.worldObj.getTypeId(pos[0], pos[1], pos[2]);
                        if (existing == 0 || existing == Block.LEAVES.id) {
                            this.worldObj.setRawTypeId(pos[0], pos[1], pos[2], Block.LEAVES.id);
                        }
                    }
                }
            }
        }

        int baseX = this.basePos[0];
        int baseY = this.basePos[1];
        int topY = this.basePos[1] + this.height;
        int baseZ = this.basePos[2];
        int[] trunkFrom = new int[]{baseX, baseY, baseZ};
        int[] trunkTo = new int[]{baseX, topY, baseZ};
        this.placeBlockLine(trunkFrom, trunkTo, Block.LOG.id);
        if (this.trunkSize == 2) {
            ++trunkFrom[0];
            ++trunkTo[0];
            this.placeBlockLine(trunkFrom, trunkTo, Block.LOG.id);
            ++trunkFrom[2];
            ++trunkTo[2];
            this.placeBlockLine(trunkFrom, trunkTo, Block.LOG.id);
            trunkFrom[0] += -1;
            trunkTo[0] += -1;
            this.placeBlockLine(trunkFrom, trunkTo, Block.LOG.id);
        }

        int[] branchFrom = new int[]{this.basePos[0], this.basePos[1], this.basePos[2]};
        for (int i = 0; i < this.leafNodes.length; ++i) {
            int[] node = this.leafNodes[i];
            int[] branchTo = new int[]{node[0], node[1], node[2]};
            branchFrom[1] = node[3];
            int relY = branchFrom[1] - this.basePos[1];
            if ((double)relY >= (double)this.heightLimit * 0.2D) {
                this.placeBlockLine(branchFrom, branchTo, Block.LOG.id);
            }
        }

        return true;
    }
}

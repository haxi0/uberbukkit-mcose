package net.minecraft.server;

public class ItemArmor extends Item {

    private static final int[] bo = new int[] { 11, 16, 15, 13 };
    private static final int[] bp = new int[] { 5, 15, 15, 33, 7 };
    private static final int[][] bn = new int[][]{
            { 1, 3, 2, 1 }, // Leather
            { 2, 5, 4, 1 }, // Chain
            { 2, 6, 5, 2 }, // Iron
            { 3, 8, 6, 3 }, // Diamond
            { 2, 5, 3, 1 }  // Gold
    };
    public final int a;
    public final int bk;
    public final int bl;
    public final int bm;

    public ItemArmor(int i, int j, int k, int l) {
        super(i);
        int materialIndex = a(j);
        this.a = materialIndex;
        this.bk = l;
        this.bm = k;
        this.bl = bn[materialIndex][l];
        this.d(bo[l] * bp[materialIndex]);
        this.maxStackSize = 1;
    }

    private static int a(int i) {
        return Math.max(0, Math.min(bn.length - 1, i));
    }
}

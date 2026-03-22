package net.minecraft.server;

/**
 * Runtime helpers for state-first access on critical redstone/piston/fluid blocks.
 * Legacy id/meta remains the compatibility projection at packet/plugin boundaries.
 */
public final class CriticalBlockStateAccess {
    private CriticalBlockStateAccess() {}

    public static boolean setLegacyState(World world, int x, int y, int z, int blockId, int metadata, boolean notify) {
        BlockStateKey state = BlockStateBridge.fromLegacy(blockId, metadata);
        return notify ? world.setBlockStateAndData(x, y, z, state) : world.setBlockState(x, y, z, state);
    }

    public static boolean setMetadata(World world, int x, int y, int z, int metadata, boolean notify) {
        if (notify) {
            int before = world.getData(x, y, z);
            world.setData(x, y, z, metadata);
            return before != world.getData(x, y, z);
        }

        return world.setRawData(x, y, z, metadata);
    }

    public static int getRedstoneWirePower(IBlockAccess access, int x, int y, int z) {
        BlockStateKey state = state(access, x, y, z);
        int fallback = access.getData(x, y, z) & 15;
        return clamp(parseInt(state.getProperty("power"), fallback), 0, 15);
    }

    public static int getRepeaterMetadata(IBlockAccess access, int x, int y, int z) {
        BlockStateKey state = state(access, x, y, z);
        int fallback = access.getData(x, y, z) & 15;
        int facing = repeaterMetaFromFacing(state.getProperty("facing"));
        int fallbackFacing = fallback & 3;
        if (facing < 0) {
            facing = fallbackFacing;
        }
        int fallbackDelay = ((fallback >> 2) & 3) + 1;
        int delay = clamp(parseInt(state.getProperty("delay"), fallbackDelay), 1, 4);
        return (facing & 3) | ((delay - 1) << 2);
    }

    public static int getTorchMetadata(IBlockAccess access, int x, int y, int z) {
        BlockStateKey state = state(access, x, y, z);
        int fallback = access.getData(x, y, z) & 7;
        int meta = torchMetaFromFacing(state.getProperty("facing"));
        return meta < 0 ? fallback : meta;
    }

    public static int getPistonMetadata(IBlockAccess access, int x, int y, int z) {
        BlockStateKey state = state(access, x, y, z);
        int fallback = access.getData(x, y, z) & 15;
        int facing = pistonMetaFromFacing(state.getProperty("facing"));
        if (facing < 0) {
            facing = fallback & 7;
        }
        String path = state.getBlockKey().getPath();
        boolean usesStickyBit = "piston_head".equals(path) || "moving_piston".equals(path) || "piston_moving".equals(path);
        boolean bit8 = usesStickyBit
                ? getBoolean(state.getProperty("sticky"), (fallback & 8) != 0)
                : getBoolean(state.getProperty("extended"), (fallback & 8) != 0);
        return (facing & 7) | (bit8 ? 8 : 0);
    }

    public static int getFluidMetadata(IBlockAccess access, int x, int y, int z) {
        BlockStateKey state = state(access, x, y, z);
        int fallback = access.getData(x, y, z) & 15;
        int level = clamp(parseInt(state.getProperty("level"), fallback), 0, 15);
        boolean falling = getBoolean(state.getProperty("falling"), (fallback & 8) != 0);
        return (level & 7) | (falling ? 8 : 0);
    }

    public static int getWallClockMetadata(IBlockAccess access, int x, int y, int z) {
        int fallback = access.getData(x, y, z) & 15;
        if (fallback >= 2 && fallback <= 5) {
            return fallback;
        }

        BlockStateKey state = state(access, x, y, z);
        int meta = wallClockMetaFromFacing(state.getProperty("facing"));
        return meta >= 2 && meta <= 5 ? meta : 0;
    }

    private static BlockStateKey state(IBlockAccess access, int x, int y, int z) {
        return BlockStateBridge.fromLegacy(access.getTypeId(x, y, z), access.getData(x, y, z));
    }

    private static boolean getBoolean(String value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.length() <= 0) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    private static int torchMetaFromFacing(String facing) {
        if ("west".equals(facing)) return 1;
        if ("east".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        if ("south".equals(facing)) return 4;
        if ("up".equals(facing)) return 5;
        return -1;
    }

    private static int repeaterMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 0;
        if ("east".equals(facing)) return 1;
        if ("south".equals(facing)) return 2;
        if ("west".equals(facing)) return 3;
        return -1;
    }

    private static int pistonMetaFromFacing(String facing) {
        if ("down".equals(facing)) return 0;
        if ("up".equals(facing)) return 1;
        if ("north".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("west".equals(facing)) return 4;
        if ("east".equals(facing)) return 5;
        return -1;
    }

    private static int wallClockMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("west".equals(facing)) return 4;
        if ("east".equals(facing)) return 5;
        return -1;
    }
}

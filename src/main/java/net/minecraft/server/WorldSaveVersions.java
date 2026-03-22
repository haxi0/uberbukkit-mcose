package net.minecraft.server;

/**
 * World save-version constants for explicit McRegion generations.
 */
public final class WorldSaveVersions {
    public static final int LEGACY_PRE_MCREGION = 0;
    public static final int MCREGION_1 = 19132;
    public static final int MCREGION_2 = 19133;

    private WorldSaveVersions() {}

    public static int currentWriteVersion() {
        return MCREGION_2;
    }

    public static boolean isLegacyPreMcRegion(int version) {
        return version == LEGACY_PRE_MCREGION;
    }

    public static boolean isMcRegion1(int version) {
        return version == MCREGION_1;
    }

    public static boolean requiresRegionCoreConversion(int version) {
        return version < MCREGION_2;
    }

    public static boolean isKnownMcRegionVersion(int version) {
        return version == MCREGION_1 || version == MCREGION_2;
    }

    public static String nameOf(int version) {
        if (version == MCREGION_2) {
            return "RegionCore";
        }
        if (version == MCREGION_1) {
            return "McRegion 1";
        }
        return "Legacy";
    }
}

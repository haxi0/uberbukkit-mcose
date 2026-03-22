package net.minecraft.server.registry;

/**
 * Immutable mining behavior definition for a block (or block metadata state).
 */
public final class BlockMiningRule {
    public static final BlockMiningRule VANILLA = new BlockMiningRule(MiningToolType.NONE, false, 1.0F);

    private final MiningToolType preferredTool;
    private final boolean enforcePreferredTool;
    private final float speedMultiplier;
    private final boolean allowDropsWithoutPreferredTool;

    public BlockMiningRule(MiningToolType preferredTool, boolean enforcePreferredTool, float speedMultiplier) {
        this(preferredTool, enforcePreferredTool, speedMultiplier, false);
    }

    public BlockMiningRule(MiningToolType preferredTool, boolean enforcePreferredTool, float speedMultiplier, boolean allowDropsWithoutPreferredTool) {
        this.preferredTool = preferredTool == null ? MiningToolType.NONE : preferredTool;
        this.enforcePreferredTool = enforcePreferredTool;
        this.speedMultiplier = speedMultiplier <= 0.0F ? 1.0F : speedMultiplier;
        this.allowDropsWithoutPreferredTool = allowDropsWithoutPreferredTool;
    }

    public MiningToolType getPreferredTool() {
        return this.preferredTool;
    }

    public boolean isEnforcePreferredTool() {
        return this.enforcePreferredTool;
    }

    public float getSpeedMultiplier() {
        return this.speedMultiplier;
    }

    public boolean allowsDropsWithoutPreferredTool() {
        return this.allowDropsWithoutPreferredTool;
    }
}

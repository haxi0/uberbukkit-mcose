package net.minecraft.server;

import net.minecraft.server.registry.BlockRegistry;
import net.minecraft.server.registry.LegacyIdBridge;
import net.minecraft.server.util.ResourceLocation;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic legacy <id,meta> <-> canonical block-state mapping.
 * Unknown or unmappable states fall back to nearest legacy projection.
 */
public final class BlockStateBridge {
    public static final String PROP_LEGACY_META = "legacy_meta";
    private static final String PROP_FACING = "facing";
    private static final String PROP_LIT = "lit";
    private static final String PROP_POWERED = "powered";
    private static final String PROP_DELAY = "delay";
    private static final String PROP_POWER = "power";
    private static final String PROP_LEVEL = "level";
    private static final String PROP_FALLING = "falling";
    private static final String PROP_FLUID = "fluid";
    private static final String PROP_VARIANT = "variant";
    private static final String PROP_EXTENDED = "extended";
    private static final String PROP_STICKY = "sticky";
    private static final String PROP_OPEN = "open";
    private static final String PROP_HALF = "half";
    private static final String PROP_ROTATION = "rotation";
    private static final String PROP_SHAPE = "shape";
    private static final String PROP_TYPE = "type";
    private static final String PROP_PART = "part";
    private static final String PROP_OCCUPIED = "occupied";
    private static final String PROP_MOUNT = "mount";

    private BlockStateBridge() {}

    public static BlockStateKey fromLegacy(int blockId, int metadata) {
        int safeMeta = metadata & 15;
        ResourceLocation blockKey = canonicalKeyForLegacy(blockId);
        BlockStateKey key = new BlockStateKey(blockKey);

        if (isRedstoneTorchId(blockId)) {
            return key.withProperty(PROP_FACING, torchFacingFromMeta(safeMeta))
                    .withProperty(PROP_LIT, boolString(blockId == getRedstoneTorchOnId()));
        }

        if (isRepeaterId(blockId)) {
            return key.withProperty(PROP_FACING, repeaterFacingFromMeta(safeMeta & 3))
                    .withProperty(PROP_DELAY, Integer.toString(((safeMeta >> 2) & 3) + 1))
                    .withProperty(PROP_POWERED, boolString(blockId == getRepeaterOnId()));
        }

        if (isPistonBaseId(blockId)) {
            return key.withProperty(PROP_FACING, pistonFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_EXTENDED, boolString((safeMeta & 8) != 0))
                    .withProperty(PROP_STICKY, boolString(blockId == getStickyPistonId()));
        }

        if (isPistonHeadId(blockId)) {
            return key.withProperty(PROP_FACING, pistonFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_STICKY, boolString((safeMeta & 8) != 0));
        }

        if (isMovingPistonId(blockId)) {
            return key.withProperty(PROP_FACING, pistonFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_STICKY, boolString((safeMeta & 8) != 0));
        }

        if (isFluidId(blockId)) {
            return key.withProperty(PROP_FLUID, isWaterId(blockId) ? "water" : "lava")
                    .withProperty(PROP_VARIANT, (blockId == getFlowingWaterId() || blockId == getFlowingLavaId()) ? "flowing" : "still")
                    .withProperty(PROP_LEVEL, Integer.toString(safeMeta))
                    .withProperty(PROP_FALLING, boolString((safeMeta & 8) != 0));
        }

        if (isRedstoneWireId(blockId)) {
            return key.withProperty(PROP_POWER, Integer.toString(clamp(safeMeta, 0, 15)))
                    .withProperty("north", "none")
                    .withProperty("east", "none")
                    .withProperty("south", "none")
                    .withProperty("west", "none");
        }

        if (isWallClockId(blockId)) {
            return key.withProperty(PROP_FACING, wallClockFacingFromMeta(safeMeta));
        }

        if (isTorchId(blockId)) {
            return key.withProperty(PROP_FACING, torchFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isFurnaceId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LIT, boolString(blockId == getLitFurnaceId()))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isDispenserId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isChestId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isLadderId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isWallSignId(blockId)) {
            return key.withProperty(PROP_FACING, horizontalFacingFromMeta(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isStandingSignId(blockId)) {
            return key.withProperty(PROP_ROTATION, Integer.toString(safeMeta))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isButtonId(blockId)) {
            return key.withProperty(PROP_ROTATION, Integer.toString(safeMeta & 7))
                    .withProperty(PROP_POWERED, boolString((safeMeta & 8) != 0))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isLeverId(blockId)) {
            return key.withProperty(PROP_ROTATION, Integer.toString(safeMeta & 7))
                    .withProperty(PROP_MOUNT, leverMountFromMeta(safeMeta & 7))
                    .withProperty(PROP_FACING, leverFacingFromMeta(safeMeta & 7))
                    .withProperty(PROP_POWERED, boolString((safeMeta & 8) != 0))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isPressurePlateId(blockId)) {
            return key.withProperty(PROP_POWERED, boolString(safeMeta > 0))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isPumpkinId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_ROTATION, Integer.toString(rotation))
                    .withProperty(PROP_FACING, pumpkinFacingFromMeta(rotation))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isTrapdoorId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_ROTATION, Integer.toString(rotation))
                    .withProperty(PROP_FACING, trapdoorFacingFromMeta(rotation))
                    .withProperty(PROP_OPEN, boolString((safeMeta & 4) != 0))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isFenceGateId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_ROTATION, Integer.toString(rotation))
                    .withProperty(PROP_FACING, fenceGateFacingFromMeta(rotation))
                    .withProperty(PROP_OPEN, boolString((safeMeta & 4) != 0))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isStairsId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_ROTATION, Integer.toString(rotation))
                    .withProperty(PROP_FACING, stairsFacingFromMeta(rotation))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isSlabId(blockId)) {
            return key.withProperty(PROP_TYPE, blockId == getDoubleSlabId() ? "double" : "bottom")
                    .withProperty(PROP_VARIANT, slabVariantFromMeta(safeMeta & 3))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isRailId(blockId)) {
            int shapeMeta = (blockId == getPoweredRailId() || blockId == getDetectorRailId()) ? (safeMeta & 7) : safeMeta;
            boolean powered = (blockId == getPoweredRailId() || blockId == getDetectorRailId()) && (safeMeta & 8) != 0;
            return key.withProperty(PROP_SHAPE, railShapeFromMeta(shapeMeta, blockId == getRailId()))
                    .withProperty(PROP_POWERED, boolString(powered))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isDoorId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_ROTATION, Integer.toString(rotation))
                    .withProperty(PROP_OPEN, boolString((safeMeta & 4) != 0))
                    .withProperty(PROP_HALF, (safeMeta & 8) != 0 ? "upper" : "lower")
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isBedId(blockId)) {
            int rotation = safeMeta & 3;
            return key.withProperty(PROP_ROTATION, Integer.toString(rotation))
                    .withProperty(PROP_PART, (safeMeta & 8) != 0 ? "foot" : "head")
                    .withProperty(PROP_OCCUPIED, boolString((safeMeta & 4) != 0))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        if (isRedstoneOreId(blockId)) {
            return key.withProperty(PROP_LIT, boolString(blockId == getLitRedstoneOreId()))
                    .withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
        }

        return key.withProperty(PROP_LEGACY_META, Integer.toString(safeMeta));
    }

    public static LegacyBlockData toLegacy(BlockStateKey key) {
        if (key == null) {
            return new LegacyBlockData(0, 0, true);
        }

        String namespace = key.getBlockKey().getNamespace().toLowerCase(Locale.ROOT);
        String path = key.getBlockKey().getPath().toLowerCase(Locale.ROOT);
        Map<String, String> props = key.getProperties();
        Integer bridged = LegacyIdBridge.blockIdFromKey(key.getBlockKey().toString());
        if (bridged == null) {
            Block block = BlockRegistry.get(key.getBlockKey());
            if (block != null) {
                bridged = Integer.valueOf(block.id);
            }
        }

        if ("minecraft".equals(namespace) && "air".equals(path)) {
            int meta = clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15);
            return new LegacyBlockData(0, meta, false);
        }

        if (isPath(path, "redstone_torch", "lit_redstone_torch", "redstone_torch_on", "redstone_torch_off", "redstone_torch_unlit")) {
            boolean lit = getBoolean(props, PROP_LIT, !isPath(path, "redstone_torch_off", "redstone_torch_unlit"));
            int id = lit ? getRedstoneTorchOnId() : getRedstoneTorchOffId();
            int meta = torchMetaFromFacing(props.get(PROP_FACING));
            return new LegacyBlockData(id, meta, false);
        }

        if (isPath(path, "repeater", "redstone_repeater", "redstone_repeater_on", "redstone_repeater_off", "diode")) {
            boolean powered = getBoolean(props, PROP_POWERED, isPath(path, "redstone_repeater_on"));
            int id = powered ? getRepeaterOnId() : getRepeaterOffId();
            int facing = repeaterMetaFromFacing(props.get(PROP_FACING));
            int delay = clamp(parseInt(props.get(PROP_DELAY), 1), 1, 4);
            int meta = (facing & 3) | ((delay - 1) << 2);
            return new LegacyBlockData(id, meta, false);
        }

        if (isPath(path, "piston", "sticky_piston")) {
            boolean sticky = getBoolean(props, PROP_STICKY, isPath(path, "sticky_piston"));
            int id = sticky ? getStickyPistonId() : getPistonId();
            int facing = pistonMetaFromFacing(props.get(PROP_FACING));
            boolean extended = getBoolean(props, PROP_EXTENDED, false);
            int meta = (facing & 7) | (extended ? 8 : 0);
            return new LegacyBlockData(id, meta, false);
        }

        if (isPath(path, "piston_head")) {
            int facing = pistonMetaFromFacing(props.get(PROP_FACING));
            boolean sticky = getBoolean(props, PROP_STICKY, false);
            int meta = (facing & 7) | (sticky ? 8 : 0);
            return new LegacyBlockData(getPistonHeadId(), meta, false);
        }

        if (isPath(path, "moving_piston", "piston_moving")) {
            int facing = pistonMetaFromFacing(props.get(PROP_FACING));
            boolean sticky = getBoolean(props, PROP_STICKY, false);
            int meta = (facing & 7) | (sticky ? 8 : 0);
            return new LegacyBlockData(getMovingPistonId(), meta, false);
        }

        if (isPath(path, "water", "flowing_water", "lava", "flowing_lava", "stationary_water", "stationary_lava")) {
            boolean water = isPath(path, "water", "flowing_water", "stationary_water")
                    || "water".equalsIgnoreCase(props.get(PROP_FLUID));
            int level = clamp(parseInt(props.get(PROP_LEVEL), 0), 0, 15);
            boolean falling = getBoolean(props, PROP_FALLING, (level & 8) != 0);
            String variant = props.get(PROP_VARIANT);
            boolean flowing = "flowing".equalsIgnoreCase(variant)
                    || isPath(path, "flowing_water", "flowing_lava")
                    || (!"still".equalsIgnoreCase(variant) && !isPath(path, "stationary_water", "stationary_lava") && ((level & 7) > 0 || falling));
            int meta = (level & 7) | (falling ? 8 : 0);
            int id;
            if (water) {
                id = flowing ? getFlowingWaterId() : getStillWaterId();
            } else {
                id = flowing ? getFlowingLavaId() : getStillLavaId();
            }
            return new LegacyBlockData(id, meta, false);
        }

        if (isPath(path, "redstone_wire", "redstone_dust")) {
            int power = clamp(parseInt(props.get(PROP_POWER), 0), 0, 15);
            return new LegacyBlockData(getRedstoneWireId(), power, false);
        }

        if (isPath(path, "clock_sensor", "wall_clock")) {
            return new LegacyBlockData(getWallClockId(), wallClockMetaFromFacing(props.get(PROP_FACING)), false);
        }

        if (isLegacyId(bridged, getTorchId()) || isPath(path, "torch", "wall_torch")) {
            int meta = resolveMeta(props, torchMetaFromFacing(props.get(PROP_FACING)));
            return new LegacyBlockData(getTorchId(), meta, false);
        }

        if (isLegacyId(bridged, getFurnaceId()) || isLegacyId(bridged, getLitFurnaceId()) || isPath(path, "furnace", "lit_furnace", "furnace_lit")) {
            boolean lit = getBoolean(props, PROP_LIT, isLegacyId(bridged, getLitFurnaceId()) || isPath(path, "lit_furnace", "furnace_lit"));
            int id = lit ? getLitFurnaceId() : getFurnaceId();
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 3));
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getDispenserId()) || isPath(path, "dispenser")) {
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 3));
            return new LegacyBlockData(getDispenserId(), meta, false);
        }

        if (isLegacyId(bridged, getChestId()) || isPath(path, "chest", "trapped_chest", "ender_chest")) {
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 3));
            return new LegacyBlockData(getChestId(), meta, false);
        }

        if (isLegacyId(bridged, getLadderId()) || isPath(path, "ladder")) {
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 2));
            return new LegacyBlockData(getLadderId(), meta, false);
        }

        if (isLegacyId(bridged, getWallSignId()) || isPath(path, "wall_sign", "sign_wall")) {
            int meta = resolveMeta(props, horizontalMetaFromFacing(props.get(PROP_FACING), 2));
            return new LegacyBlockData(getWallSignId(), meta, false);
        }

        if (isLegacyId(bridged, getStandingSignId()) || isPath(path, "standing_sign", "sign_post")) {
            int meta = resolveMeta(props, clamp(parseInt(props.get(PROP_ROTATION), 0), 0, 15));
            return new LegacyBlockData(getStandingSignId(), meta, false);
        }

        if (isLegacyId(bridged, getButtonId()) || isPath(path, "button", "stone_button", "wooden_button")) {
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), buttonMetaFromFacing(props.get(PROP_FACING))), 1, 4);
            int meta = resolveMeta(props, orientation | (getBoolean(props, PROP_POWERED, false) ? 8 : 0));
            return new LegacyBlockData(getButtonId(), meta, false);
        }

        if (isLegacyId(bridged, getLeverId()) || isPath(path, "lever")) {
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), leverMetaFromProperties(props)), 1, 6);
            int meta = resolveMeta(props, orientation | (getBoolean(props, PROP_POWERED, false) ? 8 : 0));
            return new LegacyBlockData(getLeverId(), meta, false);
        }

        if (isLegacyId(bridged, getStonePressurePlateId()) || isLegacyId(bridged, getWoodPressurePlateId()) || isPath(path, "pressure_plate", "stone_pressure_plate", "wooden_pressure_plate")) {
            int id = isLegacyId(bridged, getWoodPressurePlateId()) || isPath(path, "wooden_pressure_plate") ? getWoodPressurePlateId() : getStonePressurePlateId();
            int meta = resolveMeta(props, getBoolean(props, PROP_POWERED, false) ? 1 : 0);
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getPumpkinId()) || isLegacyId(bridged, getJackOLanternId()) || isPath(path, "pumpkin", "lit_pumpkin", "jack_o_lantern")) {
            int id = isLegacyId(bridged, getJackOLanternId()) || isPath(path, "lit_pumpkin", "jack_o_lantern") ? getJackOLanternId() : getPumpkinId();
            int fallback = pumpkinMetaFromFacing(props.get(PROP_FACING));
            int meta = resolveMeta(props, clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3));
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getTrapdoorId()) || isPath(path, "trapdoor", "wooden_trapdoor", "iron_trapdoor")) {
            int fallback = trapdoorMetaFromFacing(props.get(PROP_FACING));
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
            int baseMeta = orientation | (getBoolean(props, PROP_OPEN, false) ? 4 : 0);
            int meta = resolveMeta(props, baseMeta);
            return new LegacyBlockData(getTrapdoorId(), meta, false);
        }

        if (isLegacyId(bridged, getFenceGateId()) || isPath(path, "fence_gate", "fencegate")) {
            int fallback = fenceGateMetaFromFacing(props.get(PROP_FACING));
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
            int baseMeta = orientation | (getBoolean(props, PROP_OPEN, false) ? 4 : 0);
            int meta = resolveMeta(props, baseMeta);
            return new LegacyBlockData(getFenceGateId(), meta, false);
        }

        if (isLegacyId(bridged, getWoodStairsId()) || isLegacyId(bridged, getCobbleStairsId()) || isLegacyId(bridged, getBrickStairsId()) || isLegacyId(bridged, getStoneBrickStairsId())) {
            int id = bridged.intValue();
            int fallback = stairsMetaFromFacing(props.get(PROP_FACING));
            int meta = resolveMeta(props, clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3));
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getSlabId()) || isLegacyId(bridged, getDoubleSlabId())) {
            int id = bridged.intValue();
            int fallback = slabMetaFromVariant(props.get(PROP_VARIANT));
            int meta = resolveMeta(props, fallback);
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getRailId()) || isLegacyId(bridged, getPoweredRailId()) || isLegacyId(bridged, getDetectorRailId()) || isPath(path, "rail", "powered_rail", "golden_rail", "detector_rail")) {
            int id = isLegacyId(bridged, getPoweredRailId()) || isPath(path, "powered_rail", "golden_rail")
                    ? getPoweredRailId()
                    : (isLegacyId(bridged, getDetectorRailId()) || isPath(path, "detector_rail") ? getDetectorRailId() : getRailId());
            int fallback = railMetaFromShape(props.get(PROP_SHAPE), id == getRailId());
            int baseMeta = (id == getPoweredRailId() || id == getDetectorRailId())
                    ? ((fallback & 7) | (getBoolean(props, PROP_POWERED, false) ? 8 : 0))
                    : (fallback & 15);
            int meta = resolveMeta(props, baseMeta);
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getWoodDoorId()) || isLegacyId(bridged, getIronDoorId()) || isPath(path, "wooden_door", "wood_door", "iron_door")) {
            int id = isLegacyId(bridged, getIronDoorId()) || isPath(path, "iron_door") ? getIronDoorId() : getWoodDoorId();
            int fallback = clamp(parseInt(props.get(PROP_ROTATION), doorRotationFromFacing(props.get(PROP_FACING))), 0, 3);
            int baseMeta = fallback
                    | (getBoolean(props, PROP_OPEN, false) ? 4 : 0)
                    | ("upper".equalsIgnoreCase(props.get(PROP_HALF)) ? 8 : 0);
            int meta = resolveMeta(props, baseMeta);
            return new LegacyBlockData(id, meta, false);
        }

        if (isLegacyId(bridged, getBedId()) || isPath(path, "bed")) {
            int fallback = clamp(parseInt(props.get(PROP_ROTATION), bedRotationFromFacing(props.get(PROP_FACING))), 0, 3);
            int baseMeta = fallback
                    | (getBoolean(props, PROP_OCCUPIED, false) ? 4 : 0)
                    | ("foot".equalsIgnoreCase(props.get(PROP_PART)) ? 8 : 0);
            int meta = resolveMeta(props, baseMeta);
            return new LegacyBlockData(getBedId(), meta, false);
        }

        if (isLegacyId(bridged, getRedstoneOreId()) || isLegacyId(bridged, getLitRedstoneOreId()) || isPath(path, "redstone_ore", "lit_redstone_ore", "glowing_redstone_ore")) {
            int id = isLegacyId(bridged, getLitRedstoneOreId()) || isPath(path, "lit_redstone_ore", "glowing_redstone_ore") || getBoolean(props, PROP_LIT, false)
                    ? getLitRedstoneOreId()
                    : getRedstoneOreId();
            int meta = resolveMeta(props, 0);
            return new LegacyBlockData(id, meta, false);
        }

        if (bridged == null) {
            Integer legacySynthetic = parseLegacySyntheticId(namespace, path);
            if (legacySynthetic != null) {
                int meta = clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15);
                return new LegacyBlockData(legacySynthetic.intValue(), meta, false);
            }
        }
        int id = bridged == null ? 0 : bridged.intValue();
        int meta = props.containsKey(PROP_LEGACY_META)
                ? clamp(parseInt(props.get(PROP_LEGACY_META), 0), 0, 15)
                : inferMetadataFromProperties(bridged, path, props);
        boolean fallbackUsed = bridged == null;
        return new LegacyBlockData(id, meta, fallbackUsed);
    }

    public static final class LegacyBlockData {
        public final int blockId;
        public final int metadata;
        public final boolean fallbackUsed;

        public LegacyBlockData(int blockId, int metadata, boolean fallbackUsed) {
            this.blockId = blockId;
            this.metadata = metadata & 15;
            this.fallbackUsed = fallbackUsed;
        }
    }

    private static ResourceLocation canonicalKeyForLegacy(int blockId) {
        if (blockId == 0) {
            return new ResourceLocation("minecraft", "air");
        }
        if (isRedstoneTorchId(blockId)) {
            return new ResourceLocation("minecraft", "redstone_torch");
        }
        if (isRepeaterId(blockId)) {
            return new ResourceLocation("minecraft", "repeater");
        }
        if (isPistonBaseId(blockId)) {
            return new ResourceLocation("minecraft", "piston");
        }
        if (isPistonHeadId(blockId)) {
            return new ResourceLocation("minecraft", "piston_head");
        }
        if (isMovingPistonId(blockId)) {
            return new ResourceLocation("minecraft", "moving_piston");
        }
        if (isFluidId(blockId)) {
            return new ResourceLocation("minecraft", isWaterId(blockId) ? "water" : "lava");
        }

        Block block = blockId >= 0 && blockId < Block.byId.length ? Block.byId[blockId] : null;
        ResourceLocation key = block == null ? null : BlockRegistry.getKey(block);
        if (key == null) {
            String bridged = LegacyIdBridge.blockKeyFromId(blockId);
            if (bridged != null) {
                key = new ResourceLocation(bridged);
            }
        }
        if (key == null) {
            key = new ResourceLocation("legacy", "block_" + blockId);
        }
        return key;
    }

    private static Integer parseLegacySyntheticId(String namespace, String path) {
        if (!"legacy".equals(namespace) || path == null || !path.startsWith("block_")) {
            return null;
        }
        int parsed = parseInt(path.substring("block_".length()), -1);
        if (parsed < 0) {
            return null;
        }
        return Integer.valueOf(parsed & 255);
    }

    private static int resolveMeta(Map<String, String> props, int fallback) {
        return clamp(parseInt(props.get(PROP_LEGACY_META), fallback), 0, 15);
    }

    private static boolean isLegacyId(Integer bridged, int expected) {
        return bridged != null && bridged.intValue() == expected;
    }

    private static int inferMetadataFromProperties(Integer bridged, String path, Map<String, String> props) {
        int legacyId = bridged == null ? -1 : bridged.intValue();

        if (legacyId == getTorchId() || isPath(path, "torch", "wall_torch")) {
            return clamp(torchMetaFromFacing(props.get(PROP_FACING)), 0, 15);
        }

        if (legacyId == getFurnaceId() || legacyId == getLitFurnaceId() || isPath(path, "furnace", "lit_furnace", "furnace_lit")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 3), 0, 15);
        }

        if (legacyId == getDispenserId() || isPath(path, "dispenser")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 3), 0, 15);
        }

        if (legacyId == getChestId() || isPath(path, "chest")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 3), 0, 15);
        }

        if (legacyId == getLadderId() || isPath(path, "ladder")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 2), 0, 15);
        }

        if (legacyId == getWallSignId() || isPath(path, "wall_sign", "sign_wall")) {
            return clamp(horizontalMetaFromFacing(props.get(PROP_FACING), 2), 0, 15);
        }

        if (legacyId == getStandingSignId() || isPath(path, "standing_sign", "sign_post")) {
            return clamp(parseInt(props.get(PROP_ROTATION), 0), 0, 15);
        }

        if (legacyId == getButtonId() || isPath(path, "button", "stone_button", "wooden_button")) {
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), buttonMetaFromFacing(props.get(PROP_FACING))), 1, 4);
            return orientation | (getBoolean(props, PROP_POWERED, false) ? 8 : 0);
        }

        if (legacyId == getLeverId() || isPath(path, "lever")) {
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), leverMetaFromProperties(props)), 1, 6);
            return orientation | (getBoolean(props, PROP_POWERED, false) ? 8 : 0);
        }

        if (legacyId == getStonePressurePlateId() || legacyId == getWoodPressurePlateId() || isPath(path, "pressure_plate", "stone_pressure_plate", "wooden_pressure_plate")) {
            return getBoolean(props, PROP_POWERED, false) ? 1 : 0;
        }

        if (legacyId == getPumpkinId() || legacyId == getJackOLanternId() || isPath(path, "pumpkin", "lit_pumpkin", "jack_o_lantern")) {
            int fallback = pumpkinMetaFromFacing(props.get(PROP_FACING));
            return clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
        }

        if (legacyId == getTrapdoorId() || isPath(path, "trapdoor", "wooden_trapdoor", "iron_trapdoor")) {
            int fallback = trapdoorMetaFromFacing(props.get(PROP_FACING));
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
            return orientation | (getBoolean(props, PROP_OPEN, false) ? 4 : 0);
        }

        if (legacyId == getFenceGateId() || isPath(path, "fence_gate", "fencegate")) {
            int fallback = fenceGateMetaFromFacing(props.get(PROP_FACING));
            int orientation = clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
            return orientation | (getBoolean(props, PROP_OPEN, false) ? 4 : 0);
        }

        if (isStairsId(legacyId)) {
            int fallback = stairsMetaFromFacing(props.get(PROP_FACING));
            return clamp(parseInt(props.get(PROP_ROTATION), fallback), 0, 3);
        }

        if (legacyId == getSlabId() || legacyId == getDoubleSlabId()) {
            return clamp(slabMetaFromVariant(props.get(PROP_VARIANT)), 0, 3);
        }

        if (legacyId == getRailId() || legacyId == getPoweredRailId() || legacyId == getDetectorRailId() || isPath(path, "rail", "powered_rail", "golden_rail", "detector_rail")) {
            int fallback = railMetaFromShape(props.get(PROP_SHAPE), legacyId == getRailId());
            if (legacyId == getPoweredRailId() || legacyId == getDetectorRailId()) {
                return (fallback & 7) | (getBoolean(props, PROP_POWERED, false) ? 8 : 0);
            }
            return fallback & 15;
        }

        if (legacyId == getWoodDoorId() || legacyId == getIronDoorId() || isPath(path, "wooden_door", "wood_door", "iron_door")) {
            int rotation = clamp(parseInt(props.get(PROP_ROTATION), doorRotationFromFacing(props.get(PROP_FACING))), 0, 3);
            int meta = rotation;
            if (getBoolean(props, PROP_OPEN, false)) {
                meta |= 4;
            }
            if ("upper".equalsIgnoreCase(props.get(PROP_HALF))) {
                meta |= 8;
            }
            return meta;
        }

        if (legacyId == getBedId() || isPath(path, "bed")) {
            int rotation = clamp(parseInt(props.get(PROP_ROTATION), bedRotationFromFacing(props.get(PROP_FACING))), 0, 3);
            int meta = rotation;
            if (getBoolean(props, PROP_OCCUPIED, false)) {
                meta |= 4;
            }
            if ("foot".equalsIgnoreCase(props.get(PROP_PART))) {
                meta |= 8;
            }
            return meta;
        }

        return 0;
    }

    private static boolean isPath(String path, String... candidates) {
        for (int i = 0; i < candidates.length; i++) {
            if (candidates[i].equals(path)) {
                return true;
            }
        }
        return false;
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.length() == 0) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(Map<String, String> props, String name, boolean fallback) {
        String raw = props.get(name);
        if (raw == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(raw) || "1".equals(raw);
    }

    private static String boolString(boolean value) {
        return value ? "true" : "false";
    }

    private static String torchFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 1: return "west";
            case 2: return "east";
            case 3: return "north";
            case 4: return "south";
            case 5: return "up";
            default: return "up";
        }
    }

    private static int torchMetaFromFacing(String facing) {
        if ("west".equals(facing)) return 1;
        if ("east".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        if ("south".equals(facing)) return 4;
        if ("up".equals(facing)) return 5;
        return 5;
    }

    private static String repeaterFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "north";
            case 1: return "east";
            case 2: return "south";
            case 3: return "west";
            default: return "north";
        }
    }

    private static int repeaterMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 0;
        if ("east".equals(facing)) return 1;
        if ("south".equals(facing)) return 2;
        if ("west".equals(facing)) return 3;
        return 0;
    }

    private static String pistonFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 0: return "down";
            case 1: return "up";
            case 2: return "north";
            case 3: return "south";
            case 4: return "west";
            case 5: return "east";
            default: return "north";
        }
    }

    private static int pistonMetaFromFacing(String facing) {
        if ("down".equals(facing)) return 0;
        if ("up".equals(facing)) return 1;
        if ("north".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("west".equals(facing)) return 4;
        if ("east".equals(facing)) return 5;
        return 2;
    }

    private static String horizontalFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 2: return "north";
            case 3: return "south";
            case 4: return "west";
            case 5: return "east";
            default: return "north";
        }
    }

    private static int horizontalMetaFromFacing(String facing, int fallback) {
        if ("north".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("west".equals(facing)) return 4;
        if ("east".equals(facing)) return 5;
        return fallback;
    }

    private static String pumpkinFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "north";
            case 1: return "east";
            case 2: return "south";
            case 3: return "west";
            default: return "north";
        }
    }

    private static int pumpkinMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 0;
        if ("east".equals(facing)) return 1;
        if ("south".equals(facing)) return 2;
        if ("west".equals(facing)) return 3;
        return 0;
    }

    private static String trapdoorFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "south";
            case 1: return "north";
            case 2: return "east";
            case 3: return "west";
            default: return "south";
        }
    }

    private static int trapdoorMetaFromFacing(String facing) {
        if ("south".equals(facing)) return 0;
        if ("north".equals(facing)) return 1;
        if ("east".equals(facing)) return 2;
        if ("west".equals(facing)) return 3;
        return 0;
    }

    private static String fenceGateFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "south";
            case 1: return "west";
            case 2: return "north";
            case 3: return "east";
            default: return "south";
        }
    }

    private static int fenceGateMetaFromFacing(String facing) {
        if ("south".equals(facing)) return 0;
        if ("west".equals(facing)) return 1;
        if ("north".equals(facing)) return 2;
        if ("east".equals(facing)) return 3;
        return 0;
    }

    private static String stairsFacingFromMeta(int meta) {
        switch (meta & 3) {
            case 0: return "east";
            case 1: return "west";
            case 2: return "south";
            case 3: return "north";
            default: return "east";
        }
    }

    private static int stairsMetaFromFacing(String facing) {
        if ("east".equals(facing)) return 0;
        if ("west".equals(facing)) return 1;
        if ("south".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        return 2;
    }

    private static String slabVariantFromMeta(int meta) {
        switch (meta & 3) {
            case 1: return "sand";
            case 2: return "wood";
            case 3: return "cobble";
            default: return "stone";
        }
    }

    private static int slabMetaFromVariant(String variant) {
        if (variant == null) return 0;
        if ("1".equals(variant) || "sand".equalsIgnoreCase(variant) || "sandstone".equalsIgnoreCase(variant)) return 1;
        if ("2".equals(variant) || "wood".equalsIgnoreCase(variant) || "planks".equalsIgnoreCase(variant)) return 2;
        if ("3".equals(variant) || "cobble".equalsIgnoreCase(variant) || "cobblestone".equalsIgnoreCase(variant)) return 3;
        return 0;
    }

    private static String railShapeFromMeta(int meta, boolean allowCorners) {
        switch (meta & 15) {
            case 0: return "north_south";
            case 1: return "east_west";
            case 2: return "ascending_east";
            case 3: return "ascending_west";
            case 4: return "ascending_north";
            case 5: return "ascending_south";
            case 6: return allowCorners ? "south_east" : "north_south";
            case 7: return allowCorners ? "south_west" : "north_south";
            case 8: return allowCorners ? "north_west" : "east_west";
            case 9: return allowCorners ? "north_east" : "east_west";
            default: return "north_south";
        }
    }

    private static int railMetaFromShape(String shape, boolean allowCorners) {
        if ("east_west".equals(shape)) return 1;
        if ("ascending_east".equals(shape)) return 2;
        if ("ascending_west".equals(shape)) return 3;
        if ("ascending_north".equals(shape)) return 4;
        if ("ascending_south".equals(shape)) return 5;
        if (allowCorners) {
            if ("south_east".equals(shape)) return 6;
            if ("south_west".equals(shape)) return 7;
            if ("north_west".equals(shape)) return 8;
            if ("north_east".equals(shape)) return 9;
        }
        return 0;
    }

    private static int buttonMetaFromFacing(String facing) {
        if ("west".equals(facing)) return 1;
        if ("east".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        if ("south".equals(facing)) return 4;
        return 1;
    }

    private static String leverMountFromMeta(int meta) {
        int orientation = meta & 7;
        if (orientation == 5 || orientation == 6) {
            return "floor";
        }
        return "wall";
    }

    private static String leverFacingFromMeta(int meta) {
        switch (meta & 7) {
            case 1: return "west";
            case 2: return "east";
            case 3: return "north";
            case 4: return "south";
            case 5: return "north";
            case 6: return "east";
            default: return "north";
        }
    }

    private static int leverMetaFromProperties(Map<String, String> props) {
        String mount = props.get(PROP_MOUNT);
        String facing = props.get(PROP_FACING);
        if ("floor".equalsIgnoreCase(mount) || "ceiling".equalsIgnoreCase(mount)) {
            return ("east".equals(facing) || "west".equals(facing)) ? 6 : 5;
        }
        return buttonMetaFromFacing(facing);
    }

    private static int doorRotationFromFacing(String facing) {
        if ("east".equals(facing)) return 0;
        if ("south".equals(facing)) return 1;
        if ("west".equals(facing)) return 2;
        if ("north".equals(facing)) return 3;
        return 0;
    }

    private static int bedRotationFromFacing(String facing) {
        if ("south".equals(facing)) return 0;
        if ("west".equals(facing)) return 1;
        if ("north".equals(facing)) return 2;
        if ("east".equals(facing)) return 3;
        return 0;
    }

    private static String wallClockFacingFromMeta(int meta) {
        switch (meta) {
            case 2: return "north";
            case 3: return "south";
            case 4: return "west";
            case 5: return "east";
            default: return "north";
        }
    }

    private static int wallClockMetaFromFacing(String facing) {
        if ("north".equals(facing)) return 2;
        if ("south".equals(facing)) return 3;
        if ("west".equals(facing)) return 4;
        if ("east".equals(facing)) return 5;
        return 2;
    }

    private static boolean isRedstoneTorchId(int blockId) {
        return blockId == getRedstoneTorchOffId() || blockId == getRedstoneTorchOnId();
    }

    private static boolean isTorchId(int blockId) {
        return blockId == getTorchId();
    }

    private static boolean isRepeaterId(int blockId) {
        return blockId == getRepeaterOffId() || blockId == getRepeaterOnId();
    }

    private static boolean isPistonBaseId(int blockId) {
        return blockId == getPistonId() || blockId == getStickyPistonId();
    }

    private static boolean isPistonHeadId(int blockId) {
        return blockId == getPistonHeadId();
    }

    private static boolean isMovingPistonId(int blockId) {
        return blockId == getMovingPistonId();
    }

    private static boolean isFluidId(int blockId) {
        return isWaterId(blockId) || isLavaId(blockId);
    }

    private static boolean isWaterId(int blockId) {
        return blockId == getFlowingWaterId() || blockId == getStillWaterId();
    }

    private static boolean isLavaId(int blockId) {
        return blockId == getFlowingLavaId() || blockId == getStillLavaId();
    }

    private static boolean isRedstoneWireId(int blockId) {
        return blockId == getRedstoneWireId();
    }

    private static boolean isWallClockId(int blockId) {
        return blockId == getWallClockId();
    }

    private static boolean isFurnaceId(int blockId) {
        return blockId == getFurnaceId() || blockId == getLitFurnaceId();
    }

    private static boolean isDispenserId(int blockId) {
        return blockId == getDispenserId();
    }

    private static boolean isChestId(int blockId) {
        return blockId == getChestId();
    }

    private static boolean isLadderId(int blockId) {
        return blockId == getLadderId();
    }

    private static boolean isWallSignId(int blockId) {
        return blockId == getWallSignId();
    }

    private static boolean isStandingSignId(int blockId) {
        return blockId == getStandingSignId();
    }

    private static boolean isButtonId(int blockId) {
        return blockId == getButtonId();
    }

    private static boolean isLeverId(int blockId) {
        return blockId == getLeverId();
    }

    private static boolean isPressurePlateId(int blockId) {
        return blockId == getStonePressurePlateId() || blockId == getWoodPressurePlateId();
    }

    private static boolean isPumpkinId(int blockId) {
        return blockId == getPumpkinId() || blockId == getJackOLanternId();
    }

    private static boolean isTrapdoorId(int blockId) {
        return blockId == getTrapdoorId();
    }

    private static boolean isFenceGateId(int blockId) {
        return blockId == getFenceGateId();
    }

    private static boolean isRailId(int blockId) {
        return blockId == getRailId() || blockId == getPoweredRailId() || blockId == getDetectorRailId();
    }

    private static boolean isStairsId(int blockId) {
        return blockId == getWoodStairsId() || blockId == getCobbleStairsId() || blockId == getBrickStairsId() || blockId == getStoneBrickStairsId();
    }

    private static boolean isSlabId(int blockId) {
        return blockId == getSlabId() || blockId == getDoubleSlabId();
    }

    private static boolean isDoorId(int blockId) {
        return blockId == getWoodDoorId() || blockId == getIronDoorId();
    }

    private static boolean isBedId(int blockId) {
        return blockId == getBedId();
    }

    private static boolean isRedstoneOreId(int blockId) {
        return blockId == getRedstoneOreId() || blockId == getLitRedstoneOreId();
    }

    private static int getRedstoneTorchOffId() {
        return 75;
    }

    private static int getTorchId() {
        return 50;
    }

    private static int getRedstoneTorchOnId() {
        return 76;
    }

    private static int getRepeaterOffId() {
        return 93;
    }

    private static int getRepeaterOnId() {
        return 94;
    }

    private static int getPistonId() {
        return 33;
    }

    private static int getStickyPistonId() {
        return 29;
    }

    private static int getPistonHeadId() {
        return 34;
    }

    private static int getMovingPistonId() {
        return 36;
    }

    private static int getFlowingWaterId() {
        return 8;
    }

    private static int getStillWaterId() {
        return 9;
    }

    private static int getFlowingLavaId() {
        return 10;
    }

    private static int getStillLavaId() {
        return 11;
    }

    private static int getRedstoneWireId() {
        return 55;
    }

    private static int getWallClockId() {
        return 101;
    }

    private static int getFurnaceId() {
        return 61;
    }

    private static int getLitFurnaceId() {
        return 62;
    }

    private static int getDispenserId() {
        return 23;
    }

    private static int getChestId() {
        return 54;
    }

    private static int getLadderId() {
        return 65;
    }

    private static int getWallSignId() {
        return 68;
    }

    private static int getStandingSignId() {
        return 63;
    }

    private static int getButtonId() {
        return 77;
    }

    private static int getLeverId() {
        return 69;
    }

    private static int getStonePressurePlateId() {
        return 70;
    }

    private static int getWoodPressurePlateId() {
        return 72;
    }

    private static int getPumpkinId() {
        return 86;
    }

    private static int getJackOLanternId() {
        return 91;
    }

    private static int getTrapdoorId() {
        return 96;
    }

    private static int getFenceGateId() {
        return 188;
    }

    private static int getRailId() {
        return 66;
    }

    private static int getPoweredRailId() {
        return 27;
    }

    private static int getDetectorRailId() {
        return 28;
    }

    private static int getSlabId() {
        return 44;
    }

    private static int getDoubleSlabId() {
        return 43;
    }

    private static int getWoodStairsId() {
        return 53;
    }

    private static int getCobbleStairsId() {
        return 67;
    }

    private static int getBrickStairsId() {
        return 108;
    }

    private static int getStoneBrickStairsId() {
        return 109;
    }

    private static int getWoodDoorId() {
        return 64;
    }

    private static int getIronDoorId() {
        return 71;
    }

    private static int getBedId() {
        return 26;
    }

    private static int getRedstoneOreId() {
        return 73;
    }

    private static int getLitRedstoneOreId() {
        return 74;
    }
}

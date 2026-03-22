package net.minecraft.server;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * One-way upgrader that rewrites item-bearing world NBT to RegionCore stack format.
 */
public final class RegionCoreWorldUpgrader {
    private static final String[] ITEM_STACK_REWRITE_KEYS = new String[] {
            "mcose_stack_format",
            "item",
            "count",
            "components",
            "id",
            "name",
            "Count",
            "Damage",
            "tag"
    };
    private static final int CHEST_FACING_REPAIR_REV = 1;
    private static final String CHEST_FACING_REPAIR_REV_KEY = "RegionCoreChestFacingRepairRev";
    private static final String CHEST_FACING_REPAIR_MARKER_FILE = "regioncore-chest-facing-repair.rev";

    private RegionCoreWorldUpgrader() {}

    public static void upgradeWorldToRegionCore(File worldDir, Logger logger) {
        if (worldDir == null || !worldDir.exists() || !worldDir.isDirectory()) {
            return;
        }

        int worldVersion = readWorldVersion(worldDir);
        int chestFacingRepairRev = readChestFacingRepairRevision(worldDir);
        boolean needsFormatUpgrade = worldVersion < WorldSaveVersions.MCREGION_2;
        boolean needsChestFacingRepair = chestFacingRepairRev < CHEST_FACING_REPAIR_REV;

        if (!needsFormatUpgrade && !needsChestFacingRepair) {
            return;
        }

        Logger log = logger == null ? MinecraftServer.log : logger;
        if (needsFormatUpgrade) {
            log.info("[RegionCore] Upgrading world '" + worldDir.getName() + "' from "
                    + WorldSaveVersions.nameOf(worldVersion) + " to RegionCore...");
        } else {
            log.info("[RegionCore] Applying chest-facing parity repair revision " + CHEST_FACING_REPAIR_REV
                    + " for world '" + worldDir.getName() + "'...");
        }

        int changedDatFiles = 0;
        int changedChunks = 0;

        if (needsFormatUpgrade) {
            changedDatFiles += rewriteDatIfPresent(new File(worldDir, "level.dat"));
            changedDatFiles += rewriteDatIfPresent(new File(worldDir, "level.dat_old"));

            File playersDir = new File(worldDir, "players");
            File[] playerFiles = playersDir.listFiles();
            if (playerFiles != null) {
                for (int i = 0; i < playerFiles.length; i++) {
                    File f = playerFiles[i];
                    if (f != null && f.isFile() && f.getName().endsWith(".dat")) {
                        changedDatFiles += rewriteDatIfPresent(f);
                    }
                }
            }

            File dataDir = new File(worldDir, "data");
            File[] dataFiles = dataDir.listFiles();
            if (dataFiles != null) {
                for (int i = 0; i < dataFiles.length; i++) {
                    File f = dataFiles[i];
                    if (f != null && f.isFile() && f.getName().endsWith(".dat")) {
                        changedDatFiles += rewriteDatIfPresent(f);
                    }
                }
            }
        }

        boolean bulkModeEnabled = false;
        try {
            RegionFileCache.setBulkConversionMode(true);
            bulkModeEnabled = true;
            changedChunks += rewriteRegionFolder(new File(worldDir, "region"), log);
            changedChunks += rewriteRegionFolder(new File(new File(worldDir, "DIM-1"), "region"), log);
        } finally {
            if (bulkModeEnabled) {
                RegionFileCache.setBulkConversionMode(false);
            } else {
                RegionFileCache.a();
            }
        }

        if (needsFormatUpgrade) {
            updateLevelVersion(worldDir, WorldSaveVersions.MCREGION_2);
        }
        if (needsChestFacingRepair) {
            markChestFacingRepairRevision(worldDir, CHEST_FACING_REPAIR_REV);
        }

        log.info("[RegionCore] Upgrade complete for '" + worldDir.getName()
                + "' (datFiles=" + changedDatFiles
                + ", chunks=" + changedChunks + ").");
    }

    private static int readWorldVersion(File worldDir) {
        NBTTagCompound root = readCompressedNbt(new File(worldDir, "level.dat"));
        if (root == null) {
            root = readCompressedNbt(new File(worldDir, "level.dat_old"));
        }
        if (root == null) {
            return WorldSaveVersions.LEGACY_PRE_MCREGION;
        }
        if (root.hasKey("Data")) {
            return root.k("Data").e("version");
        }
        return root.e("version");
    }

    private static int readChestFacingRepairRevision(File worldDir) {
        int markerRevision = readChestFacingRepairRevisionMarker(worldDir);
        if (markerRevision >= 0) {
            return markerRevision;
        }

        NBTTagCompound root = readCompressedNbt(new File(worldDir, "level.dat"));
        if (root == null) {
            root = readCompressedNbt(new File(worldDir, "level.dat_old"));
        }
        if (root == null) {
            return 0;
        }

        if (root.hasKey("Data")) {
            return root.k("Data").e(CHEST_FACING_REPAIR_REV_KEY);
        }
        return root.e(CHEST_FACING_REPAIR_REV_KEY);
    }

    private static int rewriteDatIfPresent(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return 0;
        }

        NBTTagCompound root = readCompressedNbt(file);
        if (root == null) {
            return 0;
        }

        boolean changed = rewriteNbtTree(root);
        if (!changed) {
            return 0;
        }

        writeCompressedNbtAtomic(file, root);
        return 1;
    }

    private static int rewriteRegionFolder(File regionDir, Logger logger) {
        if (regionDir == null || !regionDir.exists() || !regionDir.isDirectory()) {
            return 0;
        }

        File[] files = regionDir.listFiles();
        if (files == null) {
            return 0;
        }

        int changedChunks = 0;
        File worldDir = regionDir.getParentFile();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            if (file == null || !file.isFile() || !file.getName().endsWith(".mcr")) {
                continue;
            }
            Map<Long, byte[]> chunkBlockCache = new HashMap<Long, byte[]>();
            int[] regionCoords = parseRegionCoordinates(file.getName());
            if (regionCoords == null) {
                continue;
            }
            int regionX = regionCoords[0];
            int regionZ = regionCoords[1];

            RegionFile regionFile = null;
            try {
                regionFile = new RegionFile(file);
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        if (!regionFile.c(x, z)) {
                            continue;
                        }

                        DataInputStream in = regionFile.a(x, z);
                        if (in == null) {
                            continue;
                        }

                        NBTTagCompound chunkNbt;
                        try {
                            chunkNbt = CompressedStreamTools.a((DataInput) in);
                        } finally {
                            in.close();
                        }

                        int chunkX = regionX * 32 + x;
                        int chunkZ = regionZ * 32 + z;
                        ChestItemSnapshot chestBefore = snapshotChestItems(chunkNbt);

                        // Keep parity with client SaveConverterMcRegion: item/container conversion,
                        // then chest-facing repair, then block-state rewrite.
                        boolean itemChanged = rewriteNbtTree(chunkNbt);
                        boolean chestFacingChanged = rewriteChestMetadataFromLegacyOrientation(worldDir, chunkX, chunkZ, chunkNbt, chunkBlockCache);
                        boolean blockStateChanged = rewriteChunkBlockStates(chunkNbt);
                        boolean changed = itemChanged || chestFacingChanged || blockStateChanged;
                        if (!changed) {
                            continue;
                        }

                        ChestItemSnapshot chestAfter = snapshotChestItems(chunkNbt);
                        if (!chestBefore.matches(chestAfter)) {
                            throw new RuntimeException("[RegionCore] Chest item entry mismatch after chunk rewrite at "
                                    + chunkX + "," + chunkZ + " (" + chestBefore + " -> " + chestAfter + ")");
                        }

                        DataOutputStream out = regionFile.b(x, z);
                        try {
                            CompressedStreamTools.a(chunkNbt, (DataOutput) out);
                        } finally {
                            out.close();
                        }
                        changedChunks++;
                    }
                }
            } catch (Throwable t) {
                throw new RuntimeException("[RegionCore] Failed rewriting region file: " + file.getAbsolutePath(), t);
            } finally {
                if (regionFile != null) {
                    try {
                        regionFile.b();
                    } catch (IOException ignored) {}
                }
            }
        }

        if (changedChunks > 0) {
            logger.info("[RegionCore] Rewrote " + changedChunks + " chunks in " + regionDir.getAbsolutePath());
        }

        RegionFileCache.a();
        return changedChunks;
    }

    private static boolean rewriteChestMetadataFromLegacyOrientation(
            File worldDir,
            int chunkX,
            int chunkZ,
            NBTTagCompound chunkNbt,
            Map<Long, byte[]> chunkBlockCache) {
        if (chunkNbt == null || !chunkNbt.hasKey("Level")) {
            return false;
        }

        NBTTagCompound level = chunkNbt.k("Level");
        byte[] blocks;
        byte[] data;
        boolean stateBackedChunk = false;

        if (level.hasKey("Blocks") && level.hasKey("Data")) {
            blocks = level.j("Blocks");
            data = level.j("Data");
        } else if (BlockStateCodec.hasStateData(level)) {
            BlockStateCodec.DecodedState decodedState = BlockStateCodec.readStateData(level);
            if (decodedState == null) {
                return false;
            }
            blocks = decodedState.blocks;
            data = decodedState.metadata;
            stateBackedChunk = true;
        } else {
            return false;
        }

        if (blocks == null || data == null || blocks.length < 32768 || data.length < 16384) {
            return false;
        }

        chunkBlockCache.put(Long.valueOf(chunkKey(chunkX, chunkZ)), blocks);
        boolean changed = false;

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int y = 0; y < 128; y++) {
                    int index = (localX << 11) | (localZ << 7) | y;
                    int blockId = blocks[index] & 255;
                    if (blockId != Block.CHEST.id) {
                        continue;
                    }

                    int currentMeta = getNibble(data, index);
                    int worldX = chunkX * 16 + localX;
                    int worldZ = chunkZ * 16 + localZ;
                    ChestFacingInference inferred = inferLegacyChestFacing(worldDir, worldX, y, worldZ, chunkBlockCache);
                    if (!isValidChestFacing(inferred.facing)) {
                        continue;
                    }

                    if (!shouldRewriteChestFacing(currentMeta, inferred) || inferred.facing == currentMeta) {
                        continue;
                    }

                    setNibble(data, index, inferred.facing);
                    changed = true;
                }
            }
        }

        if (changed) {
            if (stateBackedChunk) {
                BlockStateCodec.writeStateData(level, blocks, data);
                if (level.hasKey("Blocks")) {
                    level.remove("Blocks");
                }
                if (level.hasKey("Data")) {
                    level.remove("Data");
                }
            } else {
                level.a("Data", data);
            }
            chunkNbt.a("Level", level);
        }

        return changed;
    }

    private static boolean shouldRewriteChestFacing(int currentMeta, ChestFacingInference inferred) {
        if (!isValidChestFacing(currentMeta)) {
            return true;
        }

        if (inferred.axis == 1 && (currentMeta == 4 || currentMeta == 5)) {
            return true;
        }

        if (inferred.axis == 2 && (currentMeta == 2 || currentMeta == 3)) {
            return true;
        }

        return inferred.constrained && currentMeta != inferred.facing;
    }

    private static ChestFacingInference inferLegacyChestFacing(File worldDir, int x, int y, int z, Map<Long, byte[]> chunkBlockCache) {
        int north = getBlockIdAt(worldDir, x, y, z - 1, chunkBlockCache);
        int south = getBlockIdAt(worldDir, x, y, z + 1, chunkBlockCache);
        int west = getBlockIdAt(worldDir, x - 1, y, z, chunkBlockCache);
        int east = getBlockIdAt(worldDir, x + 1, y, z, chunkBlockCache);

        boolean westChest = west == Block.CHEST.id;
        boolean eastChest = east == Block.CHEST.id;
        boolean northChest = north == Block.CHEST.id;
        boolean southChest = south == Block.CHEST.id;

        if (!westChest && !eastChest && !northChest && !southChest) {
            int facing = 3;
            boolean northSolid = isSolidForLegacyChestOrientation(north);
            boolean southSolid = isSolidForLegacyChestOrientation(south);
            boolean westSolid = isSolidForLegacyChestOrientation(west);
            boolean eastSolid = isSolidForLegacyChestOrientation(east);
            boolean constrained = northSolid != southSolid || westSolid != eastSolid;
            if (northSolid && !southSolid) {
                facing = 3;
            }
            if (southSolid && !northSolid) {
                facing = 2;
            }
            if (westSolid && !eastSolid) {
                facing = 5;
            }
            if (eastSolid && !westSolid) {
                facing = 4;
            }
            return new ChestFacingInference(facing, 0, constrained);
        }

        if (westChest || eastChest) {
            int pairX = westChest ? x - 1 : x + 1;
            int pairNorth = getBlockIdAt(worldDir, pairX, y, z - 1, chunkBlockCache);
            int pairSouth = getBlockIdAt(worldDir, pairX, y, z + 1, chunkBlockCache);
            int facing = 3;
            boolean northSolid = isSolidForLegacyChestOrientation(north);
            boolean southSolid = isSolidForLegacyChestOrientation(south);
            boolean pairNorthSolid = isSolidForLegacyChestOrientation(pairNorth);
            boolean pairSouthSolid = isSolidForLegacyChestOrientation(pairSouth);
            if ((northSolid || pairNorthSolid) && !southSolid && !pairSouthSolid) {
                facing = 3;
            }
            if ((southSolid || pairSouthSolid) && !northSolid && !pairNorthSolid) {
                facing = 2;
            }
            return new ChestFacingInference(facing, 1, true);
        }

        int pairZ = northChest ? z - 1 : z + 1;
        int pairWest = getBlockIdAt(worldDir, x - 1, y, pairZ, chunkBlockCache);
        int pairEast = getBlockIdAt(worldDir, x + 1, y, pairZ, chunkBlockCache);
        int facing = 5;
        boolean westSolid = isSolidForLegacyChestOrientation(west);
        boolean eastSolid = isSolidForLegacyChestOrientation(east);
        boolean pairWestSolid = isSolidForLegacyChestOrientation(pairWest);
        boolean pairEastSolid = isSolidForLegacyChestOrientation(pairEast);
        if ((westSolid || pairWestSolid) && !eastSolid && !pairEastSolid) {
            facing = 5;
        }
        if ((eastSolid || pairEastSolid) && !westSolid && !pairWestSolid) {
            facing = 4;
        }
        return new ChestFacingInference(facing, 2, true);
    }

    private static int getBlockIdAt(File worldDir, int x, int y, int z, Map<Long, byte[]> chunkBlockCache) {
        if (y < 0 || y >= 128) {
            return 0;
        }

        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        byte[] blocks = loadChunkBlocks(worldDir, chunkX, chunkZ, chunkBlockCache);
        if (blocks == null || blocks.length < 32768) {
            return 0;
        }

        int localX = x & 15;
        int localZ = z & 15;
        int index = (localX << 11) | (localZ << 7) | y;
        return blocks[index] & 255;
    }

    private static byte[] loadChunkBlocks(File worldDir, int chunkX, int chunkZ, Map<Long, byte[]> chunkBlockCache) {
        Long key = Long.valueOf(chunkKey(chunkX, chunkZ));
        if (chunkBlockCache.containsKey(key)) {
            return chunkBlockCache.get(key);
        }

        DataInputStream in = RegionFileCache.c(worldDir, chunkX, chunkZ);
        if (in == null) {
            chunkBlockCache.put(key, null);
            return null;
        }

        NBTTagCompound root;
        try {
            root = CompressedStreamTools.a((DataInput) in);
        } catch (IOException e) {
            chunkBlockCache.put(key, null);
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {}
        }

        if (root == null || !root.hasKey("Level")) {
            chunkBlockCache.put(key, null);
            return null;
        }

        NBTTagCompound level = root.k("Level");
        byte[] blocks = null;
        if (level.hasKey("Blocks")) {
            blocks = level.j("Blocks");
        } else if (BlockStateCodec.hasStateData(level)) {
            BlockStateCodec.DecodedState decodedState = BlockStateCodec.readStateData(level);
            if (decodedState != null) {
                blocks = decodedState.blocks;
            }
        }

        if (blocks == null || blocks.length < 32768) {
            chunkBlockCache.put(key, null);
            return null;
        }

        chunkBlockCache.put(key, blocks);
        return blocks;
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long)chunkX & 4294967295L) << 32 | (long)chunkZ & 4294967295L;
    }

    private static final class ChestFacingInference {
        public final int facing;
        public final int axis;
        public final boolean constrained;

        public ChestFacingInference(int facing, int axis, boolean constrained) {
            this.facing = facing;
            this.axis = axis;
            this.constrained = constrained;
        }
    }

    private static int getNibble(byte[] data, int index) {
        int nibbleIndex = index >> 1;
        int value = data[nibbleIndex] & 255;
        return (index & 1) == 0 ? value & 15 : value >> 4 & 15;
    }

    private static void setNibble(byte[] data, int index, int nibble) {
        int nibbleIndex = index >> 1;
        int current = data[nibbleIndex] & 255;
        int value = nibble & 15;
        if ((index & 1) == 0) {
            current = current & 240 | value;
        } else {
            current = current & 15 | value << 4;
        }
        data[nibbleIndex] = (byte)current;
    }

    private static boolean isValidChestFacing(int metadata) {
        return metadata == 2 || metadata == 3 || metadata == 4 || metadata == 5;
    }

    private static boolean isSolidForLegacyChestOrientation(int blockId) {
        if (blockId <= 0 || blockId >= Block.o.length || !Block.o[blockId]) {
            return false;
        }
        if (blockId == Block.FURNACE.id || blockId == Block.BURNING_FURNACE.id) {
            return false;
        }
        if (blockId == Block.WORKBENCH.id) {
            return false;
        }
        if (blockId == Block.JUKEBOX.id) {
            return false;
        }
        if (blockId == Block.PUMPKIN.id || blockId == Block.JACK_O_LANTERN.id) {
            return false;
        }
        if (blockId == Block.DISPENSER.id) {
            return false;
        }
        if (blockId == Block.NOTE_BLOCK.id) {
            return false;
        }
        return true;
    }

    private static int[] parseRegionCoordinates(String fileName) {
        if (fileName == null || !fileName.startsWith("r.") || !fileName.endsWith(".mcr")) {
            return null;
        }

        String[] parts = fileName.split("\\.");
        if (parts.length != 4) {
            return null;
        }

        try {
            return new int[] { Integer.parseInt(parts[1]), Integer.parseInt(parts[2]) };
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean rewriteNbtTree(NBTBase tag) {
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            boolean changed = tryUpgradeItemCompound(compound);
            if (ModernEntityNbtCodec.rewriteEntityCompound(compound)) {
                changed = true;
            }
            if (LegacyEntityNbtCodec.ensureLegacyShadowFields(compound)) {
                changed = true;
            }

            Collection values = compound.c();
            for (Object obj : values) {
                if (obj instanceof NBTBase) {
                    if (rewriteNbtTree((NBTBase) obj)) {
                        changed = true;
                    }
                }
            }
            return changed;
        }

        if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            boolean changed = false;
            for (int i = 0; i < list.c(); i++) {
                NBTBase child = list.a(i);
                if (child != null && rewriteNbtTree(child)) {
                    changed = true;
                }
            }
            return changed;
        }

        return false;
    }

    private static boolean rewriteChunkBlockStates(NBTTagCompound chunkNbt) {
        if (chunkNbt == null || !chunkNbt.hasKey("Level")) {
            return false;
        }

        NBTTagCompound level = chunkNbt.k("Level");
        boolean changed = false;

        if (level.hasKey("Blocks") && level.hasKey("Data")) {
            byte[] blocks = level.j("Blocks");
            byte[] data = level.j("Data");
            if (blocks != null && data != null && blocks.length >= 32768 && data.length >= 16384) {
                if (BlockStateCodec.writeStateData(level, blocks, data)) {
                    changed = true;
                }
            }
        }

        if (BlockStateCodec.hasStateData(level)) {
            if (level.hasKey("Blocks")) {
                level.remove("Blocks");
                changed = true;
            }
            if (level.hasKey("Data")) {
                level.remove("Data");
                changed = true;
            }
            chunkNbt.a("Level", level);
        }

        return changed;
    }

    private static boolean tryUpgradeItemCompound(NBTTagCompound compound) {
        if (!looksLikeItemCompound(compound)) {
            return false;
        }

        ItemStack stack = ItemStack.parse(compound);
        if (stack == null) {
            return false;
        }

        NBTTagCompound rewritten = stack.a(new NBTTagCompound());
        overwriteItemStackDataPreservingWrapperFields(compound, rewritten);
        return true;
    }

    private static boolean looksLikeItemCompound(NBTTagCompound compound) {
        if (compound == null) {
            return false;
        }

        byte idType = getTagType(compound, "id");
        byte nameType = getTagType(compound, "name");
        byte itemType = getTagType(compound, "item");
        byte countType = getTagType(compound, "Count");
        byte modernCountType = getTagType(compound, "count");
        boolean countPresent = countType == 1 || countType == 3 || modernCountType == 1 || modernCountType == 3;
        boolean idPresent = idType == 2 || nameType == 8 || itemType == 8;
        return countPresent && idPresent;
    }

    private static byte getTagType(NBTTagCompound compound, String key) {
        NBTBase tag = compound.b(key);
        if (tag != null) {
            return tag.a();
        }
        return 0;
    }

    private static void overwriteItemStackDataPreservingWrapperFields(NBTTagCompound target, NBTTagCompound rewritten) {
        for (int i = 0; i < ITEM_STACK_REWRITE_KEYS.length; ++i) {
            target.remove(ITEM_STACK_REWRITE_KEYS[i]);
        }

        java.util.Set<String> rewrittenKeys = rewritten.getKeys();
        if (rewrittenKeys == null) {
            return;
        }

        for (String key : rewrittenKeys) {
            NBTBase value = rewritten.b(key);
            if (value != null) {
                target.a(key, value);
            }
        }
    }

    private static void updateLevelVersion(File worldDir, int version) {
        updateLevelVersionFile(new File(worldDir, "level.dat"), version, -1);
        updateLevelVersionFile(new File(worldDir, "level.dat_old"), version, -1);
    }

    private static void markChestFacingRepairRevision(File worldDir, int revision) {
        updateLevelVersionFile(new File(worldDir, "level.dat"), -1, revision);
        updateLevelVersionFile(new File(worldDir, "level.dat_old"), -1, revision);
        writeChestFacingRepairRevisionMarker(worldDir, revision);
    }

    private static int readChestFacingRepairRevisionMarker(File worldDir) {
        if (worldDir == null || !worldDir.exists()) {
            return -1;
        }

        File marker = new File(worldDir, CHEST_FACING_REPAIR_MARKER_FILE);
        if (!marker.exists() || !marker.isFile()) {
            return -1;
        }

        DataInputStream in = null;
        try {
            in = new DataInputStream(new FileInputStream(marker));
            return in.readInt();
        } catch (Throwable ignored) {
            return -1;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void writeChestFacingRepairRevisionMarker(File worldDir, int revision) {
        if (worldDir == null || revision < 0) {
            return;
        }

        File marker = new File(worldDir, CHEST_FACING_REPAIR_MARKER_FILE);
        DataOutputStream out = null;
        try {
            out = new DataOutputStream(new FileOutputStream(marker));
            out.writeInt(revision);
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void updateLevelVersionFile(File file, int version, int chestFacingRepairRevision) {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }

        NBTTagCompound root = readCompressedNbt(file);
        if (root == null) {
            return;
        }

        if (root.hasKey("Data")) {
            NBTTagCompound data = root.k("Data");
            if (version >= 0) {
                data.a("version", version);
            }
            if (chestFacingRepairRevision >= 0) {
                data.a(CHEST_FACING_REPAIR_REV_KEY, chestFacingRepairRevision);
            }
            root.a("Data", data);
        } else {
            if (version >= 0) {
                root.a("version", version);
            }
            if (chestFacingRepairRevision >= 0) {
                root.a(CHEST_FACING_REPAIR_REV_KEY, chestFacingRepairRevision);
            }
        }

        writeCompressedNbtAtomic(file, root);
    }

    private static ChestItemSnapshot snapshotChestItems(NBTTagCompound chunkNbt) {
        if (chunkNbt == null || !chunkNbt.hasKey("Level")) {
            return ChestItemSnapshot.EMPTY;
        }

        NBTTagCompound level = chunkNbt.k("Level");
        if (!level.hasKey("TileEntities")) {
            return ChestItemSnapshot.EMPTY;
        }

        NBTTagList tileEntities = level.l("TileEntities");
        if (tileEntities == null) {
            return ChestItemSnapshot.EMPTY;
        }

        int chestCount = 0;
        int itemEntries = 0;
        for (int i = 0; i < tileEntities.c(); ++i) {
            NBTBase raw = tileEntities.a(i);
            if (!(raw instanceof NBTTagCompound)) {
                continue;
            }

            NBTTagCompound tileEntity = (NBTTagCompound) raw;
            String id = tileEntity.getString("id");
            if (!"Chest".equals(id) && !"minecraft:chest".equals(id) && !"minecraft:trapped_chest".equals(id)) {
                continue;
            }

            chestCount++;
            if (tileEntity.hasKey("Items")) {
                NBTTagList items = tileEntity.l("Items");
                if (items != null) {
                    itemEntries += items.c();
                }
            }
        }

        return new ChestItemSnapshot(chestCount, itemEntries);
    }

    private static final class ChestItemSnapshot {
        private static final ChestItemSnapshot EMPTY = new ChestItemSnapshot(0, 0);
        private final int chestCount;
        private final int itemEntries;

        private ChestItemSnapshot(int chestCount, int itemEntries) {
            this.chestCount = chestCount;
            this.itemEntries = itemEntries;
        }

        private boolean matches(ChestItemSnapshot other) {
            return other != null && this.chestCount == other.chestCount && this.itemEntries == other.itemEntries;
        }

        public String toString() {
            return "chests=" + this.chestCount + ",items=" + this.itemEntries;
        }
    }

    private static NBTTagCompound readCompressedNbt(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }

        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            return CompressedStreamTools.a(in);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static void writeCompressedNbtAtomic(File file, NBTTagCompound root) {
        File tmp = new File(file.getParentFile(), file.getName() + ".regioncore.tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            CompressedStreamTools.a(root, out);
        } catch (Throwable t) {
            throw new RuntimeException("[RegionCore] Failed writing temp file: " + tmp.getAbsolutePath(), t);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {}
            }
        }

        if (file.exists() && !file.delete()) {
            throw new RuntimeException("[RegionCore] Failed replacing file: " + file.getAbsolutePath());
        }
        if (!tmp.renameTo(file)) {
            throw new RuntimeException("[RegionCore] Failed moving temp file into place: " + file.getAbsolutePath());
        }
    }
}

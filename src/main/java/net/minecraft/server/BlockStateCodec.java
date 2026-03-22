package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class BlockStateCodec {
    public static final String KEY_PALETTE = "StatePalette";
    public static final String KEY_DATA = "StateData";
    public static final String KEY_BITS = "StateBits";
    private static final String KEY_NAME = "Name";
    private static final String KEY_PROPERTIES = "Properties";
    private static final int BLOCK_COUNT = 16 * 16 * 128;
    private static final int MAX_FALLBACK_LOG_STATES = 16;

    private BlockStateCodec() {}

    public static boolean hasStateData(NBTTagCompound levelTag) {
        return levelTag != null && levelTag.hasKey(KEY_PALETTE) && levelTag.hasKey(KEY_DATA);
    }

    public static boolean writeStateData(NBTTagCompound levelTag, byte[] blocks, byte[] data) {
        if (levelTag == null || blocks == null || data == null || blocks.length < BLOCK_COUNT || data.length < BLOCK_COUNT / 2) {
            return false;
        }

        BlockStateIdMap palette = new BlockStateIdMap();
        int[] stateIndices = new int[BLOCK_COUNT];
        for (int i = 0; i < BLOCK_COUNT; i++) {
            int blockId = blocks[i] & 255;
            int meta = getNibble(data, i);
            BlockStateKey state = BlockStateBridge.fromLegacy(blockId, meta);
            stateIndices[i] = palette.getOrCreateId(state);
        }

        int bits = bitsForPalette(palette.size());
        byte[] packed = pack(stateIndices, bits);

        NBTTagList paletteTag = new NBTTagList();
        for (int i = 0; i < palette.size(); i++) {
            BlockStateKey key = palette.getState(i);
            paletteTag.a(serializeState(key));
        }

        levelTag.a(KEY_PALETTE, (NBTBase)paletteTag);
        levelTag.a(KEY_DATA, packed);
        levelTag.a(KEY_BITS, (byte)bits);
        return true;
    }

    public static DecodedState readStateData(NBTTagCompound levelTag) {
        if (!hasStateData(levelTag)) {
            return null;
        }

        NBTTagList paletteTag = levelTag.l(KEY_PALETTE);
        if (paletteTag == null || paletteTag.c() <= 0) {
            return null;
        }

        BlockStateKey[] palette = new BlockStateKey[paletteTag.c()];
        for (int i = 0; i < palette.length; i++) {
            NBTBase entry = paletteTag.a(i);
            if (entry instanceof NBTTagCompound) {
                palette[i] = deserializeState((NBTTagCompound)entry);
            } else {
                palette[i] = new BlockStateKey(new ResourceLocation("minecraft:air"));
            }
        }

        int bits = levelTag.hasKey(KEY_BITS) ? (levelTag.c(KEY_BITS) & 255) : bitsForPalette(palette.length);
        byte[] packed = levelTag.j(KEY_DATA);
        int[] stateIndices = unpack(packed, bits, BLOCK_COUNT);

        byte[] blocks = new byte[BLOCK_COUNT];
        byte[] data = new byte[BLOCK_COUNT / 2];
        boolean usedFallback = false;
        TreeMap<String, Integer> fallbackCounts = new TreeMap<String, Integer>();
        TreeMap<String, String> firstFallbackCoords = new TreeMap<String, String>();
        for (int i = 0; i < BLOCK_COUNT; i++) {
            int paletteIndex = stateIndices[i];
            BlockStateKey key = paletteIndex >= 0 && paletteIndex < palette.length ? palette[paletteIndex] : null;
            BlockStateBridge.LegacyBlockData legacy = BlockStateBridge.toLegacy(key);
            blocks[i] = (byte)(legacy.blockId & 255);
            setNibble(data, i, legacy.metadata);
            if (legacy.fallbackUsed) {
                usedFallback = true;
                int localX = i >> 11 & 15;
                int localZ = i >> 7 & 15;
                int localY = i & 127;
                String stateName = key == null ? "<null>" : key.toString();
                Integer existingCount = fallbackCounts.get(stateName);
                fallbackCounts.put(stateName, Integer.valueOf(existingCount == null ? 1 : existingCount.intValue() + 1));
                if (!firstFallbackCoords.containsKey(stateName)) {
                    firstFallbackCoords.put(stateName, localX + "," + localY + "," + localZ);
                }
            }
        }

        if (usedFallback) {
            int totalFallbacks = 0;
            for (Integer count : fallbackCounts.values()) {
                totalFallbacks += count.intValue();
            }
            System.err.println("[RegionCore] Nearest legacy state fallback summary: " + totalFallbacks + " blocks across " + fallbackCounts.size() + " state keys");
            int logged = 0;
            for (Map.Entry<String, Integer> entry : fallbackCounts.entrySet()) {
                if (logged >= MAX_FALLBACK_LOG_STATES) {
                    break;
                }
                String stateName = entry.getKey();
                String coords = firstFallbackCoords.get(stateName);
                System.err.println("[RegionCore]   " + stateName + " x" + entry.getValue() + " firstAt " + coords);
                logged++;
            }
            if (fallbackCounts.size() > MAX_FALLBACK_LOG_STATES) {
                System.err.println("[RegionCore]   ... " + (fallbackCounts.size() - MAX_FALLBACK_LOG_STATES) + " additional state keys omitted");
            }
        }

        return new DecodedState(blocks, data, usedFallback);
    }

    private static NBTTagCompound serializeState(BlockStateKey key) {
        NBTTagCompound out = new NBTTagCompound();
        out.setString(KEY_NAME, key.getBlockKey().toString());
        Map<String, String> props = key.getProperties();
        if (!props.isEmpty()) {
            NBTTagCompound propTag = new NBTTagCompound();
            for (Map.Entry<String, String> entry : props.entrySet()) {
                propTag.setString(entry.getKey(), entry.getValue());
            }
            out.a(KEY_PROPERTIES, propTag);
        }
        return out;
    }

    private static BlockStateKey deserializeState(NBTTagCompound tag) {
        String name = tag.getString(KEY_NAME);
        if (name == null || name.length() == 0) {
            name = "minecraft:air";
        }
        BlockStateKey key = new BlockStateKey(new ResourceLocation(name));
        if (!tag.hasKey(KEY_PROPERTIES)) {
            return key;
        }

        NBTTagCompound props = tag.k(KEY_PROPERTIES);
        Set<String> keys = props.getKeys();
        for (String propKey : keys) {
            key = key.withProperty(propKey, props.getString(propKey));
        }
        return key;
    }

    private static int bitsForPalette(int paletteSize) {
        int bits = 1;
        int value = Math.max(1, paletteSize - 1);
        while ((1 << bits) <= value && bits < 31) {
            bits++;
        }
        return bits;
    }

    private static byte[] pack(int[] values, int bitsPerValue) {
        long bitCount = (long)values.length * (long)bitsPerValue;
        byte[] out = new byte[(int)((bitCount + 7L) / 8L)];

        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            long startBit = (long)i * (long)bitsPerValue;
            for (int bit = 0; bit < bitsPerValue; bit++) {
                if (((value >> bit) & 1) != 0) {
                    int bitIndex = (int)(startBit + bit);
                    out[bitIndex >> 3] = (byte)(out[bitIndex >> 3] | (1 << (bitIndex & 7)));
                }
            }
        }

        return out;
    }

    private static int[] unpack(byte[] packed, int bitsPerValue, int valueCount) {
        int[] out = new int[valueCount];
        if (packed == null) {
            return out;
        }

        for (int i = 0; i < valueCount; i++) {
            long startBit = (long)i * (long)bitsPerValue;
            int value = 0;
            for (int bit = 0; bit < bitsPerValue; bit++) {
                int bitIndex = (int)(startBit + bit);
                int byteIndex = bitIndex >> 3;
                if (byteIndex < 0 || byteIndex >= packed.length) {
                    break;
                }
                int bitValue = (packed[byteIndex] >> (bitIndex & 7)) & 1;
                value |= bitValue << bit;
            }
            out[i] = value;
        }

        return out;
    }

    private static int getNibble(byte[] nibbles, int index) {
        int byteIndex = index >> 1;
        int value = nibbles[byteIndex] & 255;
        return (index & 1) == 0 ? value & 15 : (value >> 4) & 15;
    }

    private static void setNibble(byte[] nibbles, int index, int value) {
        int byteIndex = index >> 1;
        int current = nibbles[byteIndex] & 255;
        int clamped = value & 15;
        if ((index & 1) == 0) {
            current = (current & 240) | clamped;
        } else {
            current = (current & 15) | (clamped << 4);
        }
        nibbles[byteIndex] = (byte)current;
    }

    public static final class DecodedState {
        public final byte[] blocks;
        public final byte[] metadata;
        public final boolean usedNearestFallback;

        public DecodedState(byte[] blocks, byte[] metadata, boolean usedNearestFallback) {
            this.blocks = blocks;
            this.metadata = metadata;
            this.usedNearestFallback = usedNearestFallback;
        }
    }
}

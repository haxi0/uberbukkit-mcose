package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class BlockStateKey {
    private final ResourceLocation blockKey;
    private final TreeMap<String, String> properties;

    public BlockStateKey(ResourceLocation blockKey) {
        this(blockKey, null);
    }

    public BlockStateKey(ResourceLocation blockKey, Map<String, String> properties) {
        this.blockKey = blockKey == null ? new ResourceLocation("minecraft:air") : blockKey;
        this.properties = new TreeMap<String, String>();
        if (properties != null) {
            this.properties.putAll(properties);
        }
    }

    public ResourceLocation getBlockKey() {
        return this.blockKey;
    }

    public Map<String, String> getProperties() {
        return Collections.unmodifiableMap(this.properties);
    }

    public String getProperty(String name) {
        return this.properties.get(name);
    }

    public BlockStateKey withProperty(String name, String value) {
        TreeMap<String, String> copy = new TreeMap<String, String>(this.properties);
        if (value == null) {
            copy.remove(name);
        } else {
            copy.put(name, value);
        }
        return new BlockStateKey(this.blockKey, copy);
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockStateKey)) {
            return false;
        }
        BlockStateKey o = (BlockStateKey)other;
        return this.blockKey.equals(o.blockKey) && this.properties.equals(o.properties);
    }

    public int hashCode() {
        int out = this.blockKey.hashCode();
        out = 31 * out + this.properties.hashCode();
        return out;
    }

    public String toString() {
        if (this.properties.isEmpty()) {
            return this.blockKey.toString();
        }

        StringBuilder out = new StringBuilder();
        out.append(this.blockKey.toString()).append('[');
        boolean first = true;
        for (Map.Entry<String, String> entry : this.properties.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        out.append(']');
        return out.toString();
    }
}

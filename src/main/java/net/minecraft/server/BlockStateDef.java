package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BlockStateDef {
    private final ResourceLocation blockKey;
    private final List<BlockProperty> properties;

    public BlockStateDef(ResourceLocation blockKey, List<BlockProperty> properties) {
        this.blockKey = blockKey == null ? new ResourceLocation("minecraft:air") : blockKey;
        ArrayList<BlockProperty> copy = new ArrayList<BlockProperty>();
        if (properties != null) {
            copy.addAll(properties);
        }
        this.properties = Collections.unmodifiableList(copy);
    }

    public ResourceLocation getBlockKey() {
        return this.blockKey;
    }

    public List<BlockProperty> getProperties() {
        return this.properties;
    }
}

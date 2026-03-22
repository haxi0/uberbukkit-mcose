package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;

/**
 * Typed data component key + serializer.
 */
public final class DataComponentType<T> {
    private final ResourceLocation id;
    private final DataComponentSerializer<T> serializer;

    public DataComponentType(ResourceLocation id, DataComponentSerializer<T> serializer) {
        this.id = id;
        this.serializer = serializer;
    }

    public ResourceLocation id() {
        return this.id;
    }

    public NBTBase write(T value) {
        return this.serializer == null ? null : this.serializer.write(value);
    }

    public T read(NBTBase value) {
        return this.serializer == null ? null : this.serializer.read(value);
    }

    public String toString() {
        return "DataComponentType{" + this.id + "}";
    }
}

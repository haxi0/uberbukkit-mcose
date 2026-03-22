package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;

/**
 * Runtime registry holder modeled after modern Minecraft holder references.
 */
public final class Holder<T> {
    private final ResourceLocation key;
    private final T value;
    private final int runtimeId;

    private Holder(ResourceLocation key, T value, int runtimeId) {
        this.key = key;
        this.value = value;
        this.runtimeId = runtimeId;
    }

    public static <T> Holder<T> direct(ResourceLocation key, T value, int runtimeId) {
        return new Holder<T>(key, value, runtimeId);
    }

    public ResourceLocation key() {
        return this.key;
    }

    public T value() {
        return this.value;
    }

    public int runtimeId() {
        return this.runtimeId;
    }

    public String toString() {
        return "Holder{" + (this.key == null ? "<null>" : this.key.toString()) + ", id=" + this.runtimeId + "}";
    }
}

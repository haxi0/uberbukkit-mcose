package net.minecraft.server;

/**
 * Serializer contract for typed synched entity data values.
 */
public interface EntityDataSerializer<T> {
    T copy(T value);
}

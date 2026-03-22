package net.minecraft.server;

public interface DataComponentSerializer<T> {
    NBTBase write(T value);

    T read(NBTBase value);
}

package net.minecraft.server;

public interface IdMap<T> {
    int getId(T value);

    T byId(int id);

    int size();
}

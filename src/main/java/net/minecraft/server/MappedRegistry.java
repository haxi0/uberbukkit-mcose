package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Namespaced-key registry that also carries runtime integer IDs.
 */
public final class MappedRegistry<T> implements IdMap<T> {
    private final Map<ResourceLocation, Holder<T>> byKey = new LinkedHashMap<ResourceLocation, Holder<T>>();
    private final Map<T, Holder<T>> byValue = new IdentityHashMap<T, Holder<T>>();
    private final ArrayList<Holder<T>> byId = new ArrayList<Holder<T>>();

    public synchronized Holder<T> register(ResourceLocation key, T value) {
        return this.register(key, value, -1);
    }

    public synchronized Holder<T> register(ResourceLocation key, T value, int preferredRuntimeId) {
        if (key == null || value == null) {
            return null;
        }

        Holder<T> existingByValue = this.byValue.get(value);
        if (existingByValue != null) {
            return existingByValue;
        }

        Holder<T> existingByKey = this.byKey.get(key);
        if (existingByKey != null) {
            return existingByKey;
        }

        int runtimeId = preferredRuntimeId;
        if (runtimeId < 0) {
            runtimeId = nextFreeRuntimeId();
        }
        ensureSize(runtimeId + 1);
        if (this.byId.get(runtimeId) != null) {
            runtimeId = nextFreeRuntimeId();
            ensureSize(runtimeId + 1);
        }

        Holder<T> holder = Holder.direct(key, value, runtimeId);
        this.byKey.put(key, holder);
        this.byValue.put(value, holder);
        this.byId.set(runtimeId, holder);
        return holder;
    }

    public synchronized Holder<T> registerIfAbsent(ResourceLocation key, T value, int preferredRuntimeId) {
        Holder<T> existing = getHolder(value);
        if (existing != null) {
            return existing;
        }
        existing = getHolder(key);
        if (existing != null) {
            return existing;
        }
        return register(key, value, preferredRuntimeId);
    }

    public synchronized Holder<T> getHolder(ResourceLocation key) {
        return this.byKey.get(key);
    }

    public synchronized Holder<T> getHolder(T value) {
        return this.byValue.get(value);
    }

    public synchronized T get(ResourceLocation key) {
        Holder<T> holder = this.byKey.get(key);
        return holder == null ? null : holder.value();
    }

    public synchronized ResourceLocation getKey(T value) {
        Holder<T> holder = this.byValue.get(value);
        return holder == null ? null : holder.key();
    }

    public synchronized Set<ResourceLocation> keys() {
        return Collections.unmodifiableSet(this.byKey.keySet());
    }

    public synchronized Collection<T> values() {
        ArrayList<T> values = new ArrayList<T>(this.byValue.size());
        for (Holder<T> holder : this.byValue.values()) {
            values.add(holder.value());
        }
        return Collections.unmodifiableList(values);
    }

    public synchronized int getId(T value) {
        Holder<T> holder = this.byValue.get(value);
        return holder == null ? -1 : holder.runtimeId();
    }

    public synchronized T byId(int id) {
        if (id < 0 || id >= this.byId.size()) {
            return null;
        }
        Holder<T> holder = this.byId.get(id);
        return holder == null ? null : holder.value();
    }

    public synchronized Holder<T> holderById(int id) {
        if (id < 0 || id >= this.byId.size()) {
            return null;
        }
        return this.byId.get(id);
    }

    public synchronized int size() {
        return this.byValue.size();
    }

    private int nextFreeRuntimeId() {
        for (int i = 0; i < this.byId.size(); i++) {
            if (this.byId.get(i) == null) {
                return i;
            }
        }
        return this.byId.size();
    }

    private void ensureSize(int size) {
        while (this.byId.size() < size) {
            this.byId.add(null);
        }
    }
}

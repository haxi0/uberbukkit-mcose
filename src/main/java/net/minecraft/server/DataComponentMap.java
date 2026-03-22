package net.minecraft.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class DataComponentMap {
    public static final DataComponentMap EMPTY = new DataComponentMap(Collections.<DataComponentType<?>, Object>emptyMap());

    private final Map<DataComponentType<?>, Object> values;

    private DataComponentMap(Map<DataComponentType<?>, Object> values) {
        this.values = values;
    }

    public static Builder builder() {
        return new Builder();
    }

    @SuppressWarnings("unchecked")
    public <T> T get(DataComponentType<T> type) {
        return (T)this.values.get(type);
    }

    public boolean contains(DataComponentType<?> type) {
        return this.values.containsKey(type);
    }

    public boolean isEmpty() {
        return this.values.isEmpty();
    }

    public Map<DataComponentType<?>, Object> asMap() {
        return Collections.unmodifiableMap(this.values);
    }

    public static final class Builder {
        private final Map<DataComponentType<?>, Object> values = new HashMap<DataComponentType<?>, Object>();

        private Builder() {}

        public <T> Builder set(DataComponentType<T> type, T value) {
            if (type == null || value == null) {
                return this;
            }
            this.values.put(type, value);
            return this;
        }

        public DataComponentMap build() {
            if (this.values.isEmpty()) {
                return DataComponentMap.EMPTY;
            }
            return new DataComponentMap(new HashMap<DataComponentType<?>, Object>(this.values));
        }
    }
}

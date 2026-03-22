package net.minecraft.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 1.21-style typed synced data store with dirty packing semantics.
 */
public final class SynchedEntityData {
    private static final int MAX_ID_VALUE = 254;
    private static final Map<Class<?>, Integer> CLASS_TO_MAX_ID = new HashMap<Class<?>, Integer>();

    private final SyncedDataHolder entity;
    private final Map<Integer, DataItem<?>> itemsById = new TreeMap<Integer, DataItem<?>>();
    private boolean dirty;

    public SynchedEntityData(SyncedDataHolder entity) {
        this.entity = entity;
    }

    public static synchronized <T> EntityDataAccessor<T> defineId(
            Class<? extends SyncedDataHolder> ownerClass,
            EntityDataSerializer<T> serializer) {
        if (ownerClass == null) {
            throw new IllegalArgumentException("ownerClass");
        }
        if (serializer == null) {
            throw new IllegalArgumentException("serializer");
        }

        int nextId = resolveNextId(ownerClass);
        if (nextId > MAX_ID_VALUE) {
            throw new IllegalArgumentException("Data value id is too big with " + nextId + "! (Max is " + MAX_ID_VALUE + ")");
        }

        CLASS_TO_MAX_ID.put(ownerClass, Integer.valueOf(nextId));
        return new EntityDataAccessor<T>(nextId, serializer);
    }

    private static int resolveNextId(Class<?> ownerClass) {
        int max = -1;
        Class<?> cursor = ownerClass;
        while (cursor != null && SyncedDataHolder.class.isAssignableFrom(cursor)) {
            Integer id = CLASS_TO_MAX_ID.get(cursor);
            if (id != null && id.intValue() > max) {
                max = id.intValue();
            }
            cursor = cursor.getSuperclass();
        }
        return max + 1;
    }

    public synchronized <T> void define(EntityDataAccessor<T> accessor, T initialValue) {
        if (accessor == null) {
            throw new IllegalArgumentException("accessor");
        }
        if (accessor.id() > MAX_ID_VALUE) {
            throw new IllegalArgumentException("Data value id is too big with " + accessor.id() + "! (Max is " + MAX_ID_VALUE + ")");
        }
        if (this.itemsById.containsKey(Integer.valueOf(accessor.id()))) {
            throw new IllegalArgumentException("Duplicate id value for " + accessor.id() + "!");
        }
        this.itemsById.put(Integer.valueOf(accessor.id()), new DataItem<T>(accessor, initialValue));
    }

    public synchronized <T> T get(EntityDataAccessor<T> accessor) {
        DataItem<T> item = getItem(accessor);
        return item == null ? null : item.getValue();
    }

    public synchronized <T> void set(EntityDataAccessor<T> accessor, T value) {
        set(accessor, value, false);
    }

    public synchronized <T> void set(EntityDataAccessor<T> accessor, T value, boolean forceDirty) {
        DataItem<T> item = getItem(accessor);
        if (item == null) {
            return;
        }

        if (forceDirty || !equalsValue(item.getValue(), value)) {
            item.setValue(value);
            item.setDirty(true);
            this.dirty = true;
            if (this.entity != null) {
                this.entity.onSyncedDataUpdated(accessor);
            }
        }
    }

    public synchronized boolean isDirty() {
        return this.dirty;
    }

    public synchronized List<DataValue<?>> packDirty() {
        if (!this.dirty) {
            return null;
        }

        this.dirty = false;
        ArrayList<DataValue<?>> out = new ArrayList<DataValue<?>>();
        for (DataItem<?> item : this.itemsById.values()) {
            if (item.isDirty()) {
                item.setDirty(false);
                out.add(item.value());
            }
        }
        return out.isEmpty() ? null : out;
    }

    public synchronized List<DataValue<?>> getNonDefaultValues() {
        ArrayList<DataValue<?>> out = null;
        for (DataItem<?> item : this.itemsById.values()) {
            if (!item.isSetToDefault()) {
                if (out == null) {
                    out = new ArrayList<DataValue<?>>();
                }
                out.add(item.value());
            }
        }
        return out;
    }

    public synchronized List<DataValue<?>> getAllValues() {
        ArrayList<DataValue<?>> out = new ArrayList<DataValue<?>>();
        for (DataItem<?> item : this.itemsById.values()) {
            out.add(item.value());
        }
        return out;
    }

    public synchronized void assignValues(List<DataValue<?>> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        for (int i = 0; i < values.size(); i++) {
            DataValue<?> incoming = values.get(i);
            DataItem<?> current = this.itemsById.get(Integer.valueOf(incoming.id()));
            if (current == null) {
                continue;
            }
            assignValueUnchecked(current, incoming);
            if (this.entity != null) {
                this.entity.onSyncedDataUpdated(current.getAccessor());
            }
        }

        if (this.entity != null) {
            this.entity.onSyncedDataUpdated(values);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void assignValueUnchecked(DataItem<T> current, DataValue<?> incoming) {
        if (current == null || incoming == null) {
            return;
        }
        if (current.getAccessor().serializer() != incoming.serializer()) {
            throw new IllegalStateException("Invalid entity data serializer for field " + current.getAccessor().id());
        }
        current.setValue((T)incoming.value());
    }

    @SuppressWarnings("unchecked")
    private <T> DataItem<T> getItem(EntityDataAccessor<T> accessor) {
        return (DataItem<T>)this.itemsById.get(Integer.valueOf(accessor.id()));
    }

    private static boolean equalsValue(Object left, Object right) {
        return left == right || left != null && left.equals(right);
    }

    public static final class DataItem<T> {
        private final EntityDataAccessor<T> accessor;
        private final T initialValue;
        private T value;
        private boolean dirty;

        public DataItem(EntityDataAccessor<T> accessor, T initialValue) {
            this.accessor = accessor;
            T copied = accessor.serializer().copy(initialValue);
            this.initialValue = copied;
            this.value = accessor.serializer().copy(initialValue);
        }

        public EntityDataAccessor<T> getAccessor() {
            return this.accessor;
        }

        public T getValue() {
            return this.value;
        }

        public void setValue(T value) {
            this.value = this.accessor.serializer().copy(value);
        }

        public boolean isDirty() {
            return this.dirty;
        }

        public void setDirty(boolean dirty) {
            this.dirty = dirty;
        }

        public boolean isSetToDefault() {
            return equalsValue(this.initialValue, this.value);
        }

        public DataValue<T> value() {
            return DataValue.create(this.accessor, this.value);
        }
    }

    public static final class DataValue<T> {
        private final int id;
        private final EntityDataSerializer<T> serializer;
        private final T value;

        public DataValue(int id, EntityDataSerializer<T> serializer, T value) {
            this.id = id;
            this.serializer = serializer;
            this.value = serializer == null ? value : serializer.copy(value);
        }

        public static <T> DataValue<T> create(EntityDataAccessor<T> accessor, T value) {
            return new DataValue<T>(accessor.id(), accessor.serializer(), value);
        }

        public int id() {
            return this.id;
        }

        public EntityDataSerializer<T> serializer() {
            return this.serializer;
        }

        public T value() {
            return this.value;
        }
    }
}

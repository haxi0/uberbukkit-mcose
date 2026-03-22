package net.minecraft.server;

import java.util.HashMap;
import java.util.Map;

/**
 * Built-in serializers used by synched entity data.
 * Type ids 0..6 intentionally mirror legacy DataWatcher wire typing.
 */
public final class EntityDataSerializers {
    private static final Map<Integer, EntityDataSerializer<?>> BY_ID = new HashMap<Integer, EntityDataSerializer<?>>();
    private static final Map<EntityDataSerializer<?>, Integer> IDS = new HashMap<EntityDataSerializer<?>, Integer>();

    public static final EntityDataSerializer<Byte> BYTE = new EntityDataSerializer<Byte>() {
        public Byte copy(Byte value) {
            return value == null ? Byte.valueOf((byte)0) : Byte.valueOf(value.byteValue());
        }
    };

    public static final EntityDataSerializer<Short> SHORT = new EntityDataSerializer<Short>() {
        public Short copy(Short value) {
            return value == null ? Short.valueOf((short)0) : Short.valueOf(value.shortValue());
        }
    };

    public static final EntityDataSerializer<Integer> INT = new EntityDataSerializer<Integer>() {
        public Integer copy(Integer value) {
            return value == null ? Integer.valueOf(0) : Integer.valueOf(value.intValue());
        }
    };

    public static final EntityDataSerializer<Float> FLOAT = new EntityDataSerializer<Float>() {
        public Float copy(Float value) {
            return value == null ? Float.valueOf(0.0F) : Float.valueOf(value.floatValue());
        }
    };

    public static final EntityDataSerializer<String> STRING = new EntityDataSerializer<String>() {
        public String copy(String value) {
            return value == null ? "" : value;
        }
    };

    public static final EntityDataSerializer<ItemStack> ITEM_STACK = new EntityDataSerializer<ItemStack>() {
        public ItemStack copy(ItemStack value) {
            return value == null ? null : value.cloneItemStack();
        }
    };

    public static final EntityDataSerializer<ChunkCoordinates> CHUNK_COORDINATES = new EntityDataSerializer<ChunkCoordinates>() {
        public ChunkCoordinates copy(ChunkCoordinates value) {
            return value == null ? null : new ChunkCoordinates(value);
        }
    };

    public static final EntityDataSerializer<Boolean> BOOLEAN = new EntityDataSerializer<Boolean>() {
        public Boolean copy(Boolean value) {
            return value == null ? Boolean.FALSE : Boolean.valueOf(value.booleanValue());
        }
    };

    private EntityDataSerializers() {}

    public static synchronized void registerSerializer(int id, EntityDataSerializer<?> serializer) {
        if (serializer == null) {
            return;
        }
        BY_ID.put(Integer.valueOf(id), serializer);
        IDS.put(serializer, Integer.valueOf(id));
    }

    public static synchronized EntityDataSerializer<?> getSerializer(int id) {
        return BY_ID.get(Integer.valueOf(id));
    }

    public static synchronized int getSerializedId(EntityDataSerializer<?> serializer) {
        Integer id = IDS.get(serializer);
        return id == null ? -1 : id.intValue();
    }

    static {
        registerSerializer(0, BYTE);
        registerSerializer(1, SHORT);
        registerSerializer(2, INT);
        registerSerializer(3, FLOAT);
        registerSerializer(4, STRING);
        registerSerializer(5, ITEM_STACK);
        registerSerializer(6, CHUNK_COORDINATES);
        registerSerializer(7, BOOLEAN);
    }
}

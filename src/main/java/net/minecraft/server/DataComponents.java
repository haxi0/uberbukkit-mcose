package net.minecraft.server;

import net.minecraft.server.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Core data component registry used by ItemStack modernization.
 */
public final class DataComponents {
    private static final Map<ResourceLocation, DataComponentType<?>> BY_KEY = new HashMap<ResourceLocation, DataComponentType<?>>();

    public static final DataComponentType<Integer> MAX_STACK_SIZE = register("max_stack_size", new DataComponentSerializer<Integer>() {
        public NBTBase write(Integer value) {
            return value == null ? null : new NBTTagInt(value.intValue());
        }

        public Integer read(NBTBase value) {
            if (value instanceof NBTTagInt) {
                return Integer.valueOf(((NBTTagInt)value).a);
            }
            if (value instanceof NBTTagShort) {
                return Integer.valueOf(((NBTTagShort)value).a);
            }
            if (value instanceof NBTTagByte) {
                return Integer.valueOf(((NBTTagByte)value).a);
            }
            return null;
        }
    });

    public static final DataComponentType<Integer> MAX_DAMAGE = register("max_damage", new DataComponentSerializer<Integer>() {
        public NBTBase write(Integer value) {
            return value == null ? null : new NBTTagInt(value.intValue());
        }

        public Integer read(NBTBase value) {
            if (value instanceof NBTTagInt) {
                return Integer.valueOf(((NBTTagInt)value).a);
            }
            if (value instanceof NBTTagShort) {
                return Integer.valueOf(((NBTTagShort)value).a);
            }
            if (value instanceof NBTTagByte) {
                return Integer.valueOf(((NBTTagByte)value).a);
            }
            return null;
        }
    });

    public static final DataComponentType<Integer> DAMAGE = register("damage", new DataComponentSerializer<Integer>() {
        public NBTBase write(Integer value) {
            return value == null ? null : new NBTTagInt(value.intValue());
        }

        public Integer read(NBTBase value) {
            if (value instanceof NBTTagInt) {
                return Integer.valueOf(((NBTTagInt)value).a);
            }
            if (value instanceof NBTTagShort) {
                return Integer.valueOf(((NBTTagShort)value).a);
            }
            if (value instanceof NBTTagByte) {
                return Integer.valueOf(((NBTTagByte)value).a);
            }
            return null;
        }
    });

    public static final DataComponentType<String> CUSTOM_NAME = register("custom_name", new DataComponentSerializer<String>() {
        public NBTBase write(String value) {
            return value == null ? null : new NBTTagString(value);
        }

        public String read(NBTBase value) {
            return value instanceof NBTTagString ? ((NBTTagString)value).a : null;
        }
    });

    public static final DataComponentType<NBTTagList> ENCHANTMENTS = register("enchantments", new DataComponentSerializer<NBTTagList>() {
        public NBTBase write(NBTTagList value) {
            return value;
        }

        public NBTTagList read(NBTBase value) {
            return value instanceof NBTTagList ? (NBTTagList)value : null;
        }
    });

    public static final DataComponentType<NBTTagList> CONTAINER = register("container", new DataComponentSerializer<NBTTagList>() {
        public NBTBase write(NBTTagList value) {
            return value;
        }

        public NBTTagList read(NBTBase value) {
            return value instanceof NBTTagList ? (NBTTagList)value : null;
        }
    });

    public static final DataComponentType<NBTTagCompound> CUSTOM_DATA = register("custom_data", new DataComponentSerializer<NBTTagCompound>() {
        public NBTBase write(NBTTagCompound value) {
            return value;
        }

        public NBTTagCompound read(NBTBase value) {
            return value instanceof NBTTagCompound ? (NBTTagCompound)value : null;
        }
    });

    private DataComponents() {}

    public static DataComponentType<?> byKey(String key) {
        if (key == null || key.length() == 0) {
            return null;
        }
        try {
            return byKey(new ResourceLocation(key));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static DataComponentType<?> byKey(ResourceLocation key) {
        return key == null ? null : BY_KEY.get(key);
    }

    private static <T> DataComponentType<T> register(String path, DataComponentSerializer<T> serializer) {
        DataComponentType<T> type = new DataComponentType<T>(new ResourceLocation("minecraft", path), serializer);
        BY_KEY.put(type.id(), type);
        return type;
    }
}

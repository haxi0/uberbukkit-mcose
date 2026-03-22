package net.minecraft.server;

/**
 * Typed accessor key for a synched entity data slot.
 */
public final class EntityDataAccessor<T> {
    private final int id;
    private final EntityDataSerializer<T> serializer;

    public EntityDataAccessor(int id, EntityDataSerializer<T> serializer) {
        this.id = id;
        this.serializer = serializer;
    }

    public int id() {
        return this.id;
    }

    public EntityDataSerializer<T> serializer() {
        return this.serializer;
    }

    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EntityDataAccessor)) {
            return false;
        }
        return this.id == ((EntityDataAccessor)other).id;
    }

    public int hashCode() {
        return this.id;
    }

    public String toString() {
        return "<entity data: " + this.id + ">";
    }
}

package net.minecraft.server;

import java.lang.reflect.Constructor;
import net.minecraft.server.util.ResourceLocation;

/**
 * Runtime entity type definition with canonical key identity and legacy id bridge.
 */
public final class EntityTypeDef<T extends Entity> {
    private final ResourceLocation key;
    private final Class<T> entityClass;
    private final int legacyId;

    public EntityTypeDef(ResourceLocation key, Class<T> entityClass, int legacyId) {
        this.key = key;
        this.entityClass = entityClass;
        this.legacyId = legacyId;
    }

    public ResourceLocation key() {
        return this.key;
    }

    public Class<T> entityClass() {
        return this.entityClass;
    }

    public int legacyId() {
        return this.legacyId;
    }

    public T create(World world) {
        if (world == null || this.entityClass == null) {
            return null;
        }

        try {
            Constructor<T> ctor = this.entityClass.getConstructor(new Class[]{World.class});
            return ctor.newInstance(new Object[]{world});
        } catch (Throwable ignored) {
            return null;
        }
    }

    public String toString() {
        return "EntityTypeDef{" + this.key + ", legacy=" + this.legacyId + "}";
    }
}

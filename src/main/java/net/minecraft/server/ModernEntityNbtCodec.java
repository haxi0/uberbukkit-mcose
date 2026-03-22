package net.minecraft.server;

import java.util.UUID;
import net.minecraft.server.registry.EntityTypeRegistry;

/**
 * Writes modern entity identity/synced-data shadow fields into legacy entity compounds.
 */
public final class ModernEntityNbtCodec {
    private static final String KEY_ENTITY_TYPE = "entity_type";
    private static final String KEY_ENTITY_UUID = "entity_uuid";
    private static final String KEY_ENTITY_DATA = "entity_data";

    private ModernEntityNbtCodec() {}

    public static boolean rewriteEntityCompound(NBTTagCompound entityTag) {
        if (!looksLikeEntityCompound(entityTag)) {
            return false;
        }

        boolean changed = false;
        String canonicalType = resolveCanonicalType(entityTag);
        if (canonicalType != null) {
            if (!canonicalType.equals(entityTag.getString(KEY_ENTITY_TYPE))) {
                entityTag.setString(KEY_ENTITY_TYPE, canonicalType);
                changed = true;
            }
        }

        String uuidString = resolveUuidString(entityTag);
        if (uuidString != null && !uuidString.equals(entityTag.getString(KEY_ENTITY_UUID))) {
            entityTag.setString(KEY_ENTITY_UUID, uuidString);
            changed = true;
        }

        NBTTagCompound data = entityTag.hasKey(KEY_ENTITY_DATA) ? entityTag.k(KEY_ENTITY_DATA) : new NBTTagCompound();
        boolean dataChanged = false;
        dataChanged |= putIntIfDifferent(data, "air", entityTag.d("Air"));
        dataChanged |= putBooleanIfDifferent(data, "on_ground", entityTag.m("OnGround"));
        dataChanged |= putBooleanIfDifferent(data, "on_fire", entityTag.d("Fire") > 0);
        if (entityTag.hasKey("CustomName")) {
            dataChanged |= putStringIfDifferent(data, "custom_name", entityTag.getString("CustomName"));
        }
        if (entityTag.hasKey("NoGravity")) {
            dataChanged |= putBooleanIfDifferent(data, "no_gravity", entityTag.m("NoGravity"));
        }

        if (dataChanged || !entityTag.hasKey(KEY_ENTITY_DATA)) {
            entityTag.a(KEY_ENTITY_DATA, data);
            changed = true;
        }

        return changed;
    }

    public static boolean looksLikeEntityCompound(NBTTagCompound tag) {
        if (tag == null) {
            return false;
        }
        boolean hasType = tag.hasKey("id") || tag.hasKey(KEY_ENTITY_TYPE);
        boolean hasEntityShape = tag.hasKey("Pos") && tag.hasKey("Motion") && tag.hasKey("Rotation");
        return hasType && hasEntityShape;
    }

    private static String resolveCanonicalType(NBTTagCompound entityTag) {
        String type = entityTag.getString(KEY_ENTITY_TYPE);
        if (type != null && type.length() > 0) {
            String normalized = EntityTypeRegistry.normalizeInputIdentifier(type);
            if (normalized != null) {
                return normalized;
            }
        }

        String legacy = entityTag.getString("id");
        if (legacy == null || legacy.length() == 0) {
            return null;
        }
        return EntityTypeRegistry.canonicalizeIdentifier(legacy);
    }

    private static String resolveUuidString(NBTTagCompound entityTag) {
        if (entityTag.hasKey(KEY_ENTITY_UUID)) {
            String raw = entityTag.getString(KEY_ENTITY_UUID);
            if (raw != null && raw.length() > 0) {
                return raw;
            }
        }

        if (entityTag.hasKey("UUIDMost") && entityTag.hasKey("UUIDLeast")) {
            long most = entityTag.getLong("UUIDMost");
            long least = entityTag.getLong("UUIDLeast");
            if (most != 0L || least != 0L) {
                return new UUID(most, least).toString();
            }
        }

        return null;
    }

    private static boolean putStringIfDifferent(NBTTagCompound tag, String key, String value) {
        if (value == null) {
            return false;
        }
        if (value.equals(tag.getString(key))) {
            return false;
        }
        tag.setString(key, value);
        return true;
    }

    private static boolean putIntIfDifferent(NBTTagCompound tag, String key, int value) {
        if (tag.e(key) == value) {
            return false;
        }
        tag.a(key, value);
        return true;
    }

    private static boolean putBooleanIfDifferent(NBTTagCompound tag, String key, boolean value) {
        if (tag.m(key) == value) {
            return false;
        }
        tag.a(key, value);
        return true;
    }
}

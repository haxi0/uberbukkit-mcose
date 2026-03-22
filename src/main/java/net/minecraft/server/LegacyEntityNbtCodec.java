package net.minecraft.server;

import net.minecraft.server.registry.EntityTypeRegistry;
import net.minecraft.server.util.ResourceLocation;

/**
 * Ensures legacy entity fields remain available as a projection for old loaders/callers.
 */
public final class LegacyEntityNbtCodec {
    private LegacyEntityNbtCodec() {}

    public static boolean ensureLegacyShadowFields(NBTTagCompound entityTag) {
        if (entityTag == null) {
            return false;
        }
        if (entityTag.hasKey("id")) {
            return false;
        }

        String modernType = entityTag.getString("entity_type");
        if (modernType == null || modernType.length() == 0) {
            return false;
        }

        String normalized = EntityTypeRegistry.normalizeInputIdentifier(modernType);
        if (normalized == null) {
            return false;
        }

        String legacyName = EntityTypeRegistry.getLegacyName(new ResourceLocation(normalized));
        if (legacyName == null || legacyName.length() == 0) {
            return false;
        }

        entityTag.setString("id", legacyName);
        return true;
    }
}

package net.minecraft.server;

import java.util.List;

/**
 * Legacy vanilla entity packet compatibility bridge.
 */
public final class LegacyEntityPacketCodec {
    private LegacyEntityPacketCodec() {}

    public static int encodeLegacyTypeId(Entity entity) {
        return entity == null ? -1 : EntityTypes.a(entity);
    }

    public static List encodeLegacyMetadata(Entity entity) {
        if (entity == null || entity.getSynchedEntityData() == null) {
            return null;
        }
        return LegacyEntityMetadataCodec.encodeWatchableList(entity.getSynchedEntityData().getAllValues());
    }

    public static void applyLegacyMetadata(Entity entity, List metadata) {
        if (entity == null || metadata == null) {
            return;
        }
        entity.aa().a(metadata);
        if (entity.getSynchedEntityData() != null) {
            entity.getSynchedEntityData().assignValues(LegacyEntityMetadataCodec.decodeWatchableList(metadata));
        }
    }
}

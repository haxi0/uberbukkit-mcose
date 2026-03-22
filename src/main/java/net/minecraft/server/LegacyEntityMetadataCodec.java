package net.minecraft.server;

import java.util.ArrayList;
import java.util.List;

/**
 * Legacy DataWatcher <-> modern typed data bridge for packet boundaries.
 */
public final class LegacyEntityMetadataCodec {
    private LegacyEntityMetadataCodec() {}

    public static List<SynchedEntityData.DataValue<?>> decodeWatchableList(List watchableList) {
        ArrayList<SynchedEntityData.DataValue<?>> out = new ArrayList<SynchedEntityData.DataValue<?>>();
        if (watchableList == null) {
            return out;
        }

        for (int i = 0; i < watchableList.size(); i++) {
            Object raw = watchableList.get(i);
            if (!(raw instanceof WatchableObject)) {
                continue;
            }

            WatchableObject watchable = (WatchableObject)raw;
            EntityDataSerializer serializer = EntityDataSerializers.getSerializer(watchable.c());
            if (serializer == null) {
                continue;
            }

            Object value = serializer.copy(watchable.b());
            out.add(new SynchedEntityData.DataValue(watchable.a(), serializer, value));
        }

        return out;
    }

    public static List encodeWatchableList(List<SynchedEntityData.DataValue<?>> values) {
        ArrayList out = new ArrayList();
        if (values == null) {
            return out;
        }

        for (int i = 0; i < values.size(); i++) {
            SynchedEntityData.DataValue<?> value = values.get(i);
            int typeId = EntityDataSerializers.getSerializedId(value.serializer());
            if (typeId < 0 || typeId > 6) {
                continue;
            }
            out.add(new WatchableObject(typeId, value.id(), value.value()));
        }

        return out;
    }
}

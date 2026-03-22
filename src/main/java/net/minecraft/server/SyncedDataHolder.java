package net.minecraft.server;

import java.util.List;

/**
 * Consumer callbacks for synched entity data changes.
 */
public interface SyncedDataHolder {
    void onSyncedDataUpdated(EntityDataAccessor<?> accessor);

    void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> updates);
}

package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Modern mod-wire entity metadata update packet.
 */
public class Packet205SetEntityDataV2 extends Packet {
    public int entityId;
    private List<SynchedEntityData.DataValue<?>> entityData = Collections.emptyList();

    public Packet205SetEntityDataV2() {
    }

    public Packet205SetEntityDataV2(Entity entity) {
        this.entityId = entity == null ? -1 : entity.id;
        if (entity != null && entity.getSynchedEntityData() != null) {
            List<SynchedEntityData.DataValue<?>> values = entity.getSynchedEntityData().packDirty();
            this.entityData = values == null ? Collections.<SynchedEntityData.DataValue<?>>emptyList() : values;
        }
    }

    public Packet205SetEntityDataV2(Packet40EntityMetadata legacy) {
        this.entityId = legacy.a;
        List<SynchedEntityData.DataValue<?>> values = legacy.getTypedValues();
        if (values == null || values.isEmpty()) {
            values = LegacyEntityMetadataCodec.decodeWatchableList(legacy.getLegacyValues());
        }
        this.entityData = values == null ? Collections.<SynchedEntityData.DataValue<?>>emptyList() : values;
    }

    public void a(DataInputStream in) throws IOException {
        this.entityId = in.readInt();
        this.entityData = EntityDataStreamCodec.readValues(in);
    }

    public void a(DataOutputStream out) throws IOException {
        out.writeInt(this.entityId);
        EntityDataStreamCodec.writeValues(out, this.entityData);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 6 + (this.entityData == null ? 0 : this.entityData.size() * 8);
    }

    public List<SynchedEntityData.DataValue<?>> getEntityData() {
        return this.entityData;
    }
}

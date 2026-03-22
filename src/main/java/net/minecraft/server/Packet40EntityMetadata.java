package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Packet40EntityMetadata extends Packet {

    public int a;
    private List b;
    private List<SynchedEntityData.DataValue<?>> typedValues = Collections.emptyList();

    public Packet40EntityMetadata() {
    }

    public Packet40EntityMetadata(int i, DataWatcher datawatcher) {
        this.a = i;
        this.b = datawatcher == null ? null : datawatcher.b();
        this.typedValues = LegacyEntityMetadataCodec.decodeWatchableList(this.b);
    }

    public Packet40EntityMetadata(Entity entity) {
        this.a = entity == null ? -1 : entity.id;
        if (entity == null || entity.getSynchedEntityData() == null) {
            this.b = null;
            this.typedValues = Collections.emptyList();
            return;
        }
        List legacyDirty = entity.aa() == null ? null : entity.aa().b();
        List<SynchedEntityData.DataValue<?>> typedLegacy = LegacyEntityMetadataCodec.decodeWatchableList(legacyDirty);
        List<SynchedEntityData.DataValue<?>> typedSynched = entity.getSynchedEntityData().packDirty();
        this.typedValues = mergeTypedValues(typedLegacy, typedSynched);
        this.b = legacyDirty;
        if (this.b == null || this.b.isEmpty()) {
            this.b = LegacyEntityMetadataCodec.encodeWatchableList(this.typedValues);
        }
        if (this.b == null) {
            this.b = new java.util.ArrayList();
        }
        if (this.typedValues == null) {
            this.typedValues = Collections.emptyList();
        }
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.a = datainputstream.readInt();
        this.b = DataWatcher.a(datainputstream);
        this.typedValues = LegacyEntityMetadataCodec.decodeWatchableList(this.b);
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeInt(this.a);
        DataWatcher.a(this.b, dataoutputstream);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 5;
    }

    public List getLegacyValues() {
        return this.b;
    }

    public List<SynchedEntityData.DataValue<?>> getTypedValues() {
        return this.typedValues;
    }

    private static List<SynchedEntityData.DataValue<?>> mergeTypedValues(
            List<SynchedEntityData.DataValue<?>> legacyValues,
            List<SynchedEntityData.DataValue<?>> synchedValues) {
        if ((legacyValues == null || legacyValues.isEmpty()) && (synchedValues == null || synchedValues.isEmpty())) {
            return Collections.emptyList();
        }

        Map<Integer, SynchedEntityData.DataValue<?>> byId = new TreeMap<Integer, SynchedEntityData.DataValue<?>>();
        if (legacyValues != null) {
            for (int i = 0; i < legacyValues.size(); i++) {
                SynchedEntityData.DataValue<?> value = legacyValues.get(i);
                if (value != null) {
                    byId.put(Integer.valueOf(value.id()), value);
                }
            }
        }
        if (synchedValues != null) {
            for (int i = 0; i < synchedValues.size(); i++) {
                SynchedEntityData.DataValue<?> value = synchedValues.get(i);
                if (value != null) {
                    byId.put(Integer.valueOf(value.id()), value);
                }
            }
        }

        return new ArrayList<SynchedEntityData.DataValue<?>>(byId.values());
    }
}

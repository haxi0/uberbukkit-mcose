package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Packet24MobSpawn extends Packet {

    public int a;
    public byte b;
    public int c;
    public int d;
    public int e;
    public byte f;
    public byte g;
    private DataWatcher h;
    private List i;
    private List<SynchedEntityData.DataValue<?>> typedMetadata = Collections.emptyList();

    public Packet24MobSpawn() {
    }

    public Packet24MobSpawn(EntityLiving entityliving) {
        this.a = entityliving.id;
        this.b = (byte) LegacyEntityPacketCodec.encodeLegacyTypeId(entityliving);
        this.c = MathHelper.floor(entityliving.locX * 32.0D);
        this.d = MathHelper.floor(entityliving.locY * 32.0D);
        this.e = MathHelper.floor(entityliving.locZ * 32.0D);
        this.f = (byte) ((int) (entityliving.yaw * 256.0F / 360.0F));
        this.g = (byte) ((int) (entityliving.pitch * 256.0F / 360.0F));
        List modernProjection = LegacyEntityPacketCodec.encodeLegacyMetadata(entityliving);
        if (modernProjection != null && !modernProjection.isEmpty()) {
            LegacyEntityPacketCodec.applyLegacyMetadata(entityliving, modernProjection);
        }
        this.h = entityliving.aa();
        List<SynchedEntityData.DataValue<?>> watcherValues = LegacyEntityMetadataCodec.decodeWatchableList(this.h.getAllWatchedObjects());
        List<SynchedEntityData.DataValue<?>> synchedValues = entityliving.getSynchedEntityData() == null
                ? Collections.<SynchedEntityData.DataValue<?>>emptyList()
                : entityliving.getSynchedEntityData().getAllValues();
        this.typedMetadata = mergeTypedValues(watcherValues, synchedValues);
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.a = datainputstream.readInt();
        this.b = datainputstream.readByte();
        this.c = datainputstream.readInt();
        this.d = datainputstream.readInt();
        this.e = datainputstream.readInt();
        this.f = datainputstream.readByte();
        this.g = datainputstream.readByte();
        // uberbukkit
        if (this.pvn >= 8) {
            this.i = DataWatcher.a(datainputstream);
            this.typedMetadata = LegacyEntityMetadataCodec.decodeWatchableList(this.i);
        } else {
            this.i = null;
            this.typedMetadata = Collections.emptyList();
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeInt(this.a);

        byte entityType = this.b;
        // uberbukkit - a1.1.2_01 doesn't recognize cows and sheep
        if (this.pvn <= 2 && (this.b == 92 || this.b == 93)) entityType = 91;

        dataoutputstream.writeByte(entityType);
        dataoutputstream.writeInt(this.c);
        dataoutputstream.writeInt(this.d);
        dataoutputstream.writeInt(this.e);
        dataoutputstream.writeByte(this.f);
        dataoutputstream.writeByte(this.g);
        // uberbukkit
        if (this.pvn >= 8) {
            this.h.a(dataoutputstream);
        }
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        // uberbukkit
        return this.pvn >= 8 ? 20 : 19;
    }

    public int getLegacyTypeId() {
        return this.b & 255;
    }

    public List getLegacyMetadata() {
        return this.i;
    }

    public List<SynchedEntityData.DataValue<?>> getTypedMetadata() {
        return this.typedMetadata;
    }

    private static List<SynchedEntityData.DataValue<?>> mergeTypedValues(
            List<SynchedEntityData.DataValue<?>> watcherValues,
            List<SynchedEntityData.DataValue<?>> synchedValues) {
        if ((watcherValues == null || watcherValues.isEmpty()) && (synchedValues == null || synchedValues.isEmpty())) {
            return Collections.emptyList();
        }

        Map<Integer, SynchedEntityData.DataValue<?>> byId = new TreeMap<Integer, SynchedEntityData.DataValue<?>>();
        if (watcherValues != null) {
            for (int i = 0; i < watcherValues.size(); i++) {
                SynchedEntityData.DataValue<?> value = watcherValues.get(i);
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

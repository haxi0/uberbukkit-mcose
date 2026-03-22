package net.minecraft.server;

import net.minecraft.server.registry.EntityTypeRegistryApi;
import net.minecraft.server.util.ResourceLocation;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Modern mod-wire entity spawn packet (legacy vanilla packet boundary remains unchanged).
 */
public class Packet204AddEntityV2 extends Packet {
    public int entityId;
    public String entityTypeKey;
    public int legacyTypeId;
    public int xPosition;
    public int yPosition;
    public int zPosition;
    public byte yaw;
    public byte pitch;
    private List<SynchedEntityData.DataValue<?>> entityData = Collections.emptyList();

    public Packet204AddEntityV2() {
    }

    public Packet204AddEntityV2(EntityLiving entity) {
        this.entityId = entity.id;
        ResourceLocation key = EntityTypeRegistryApi.getKey(entity.getClass());
        this.entityTypeKey = key == null ? "" : key.toString();
        this.legacyTypeId = EntityTypes.a(entity);
        this.xPosition = MathHelper.floor(entity.locX * 32.0D);
        this.yPosition = MathHelper.floor(entity.locY * 32.0D);
        this.zPosition = MathHelper.floor(entity.locZ * 32.0D);
        this.yaw = (byte)((int)(entity.yaw * 256.0F / 360.0F));
        this.pitch = (byte)((int)(entity.pitch * 256.0F / 360.0F));
        if (entity.getSynchedEntityData() != null) {
            List<SynchedEntityData.DataValue<?>> values = entity.getSynchedEntityData().getAllValues();
            this.entityData = values == null ? Collections.<SynchedEntityData.DataValue<?>>emptyList() : values;
        }
    }

    public Packet204AddEntityV2(Packet24MobSpawn legacy) {
        this.entityId = legacy.a;
        this.legacyTypeId = legacy.getLegacyTypeId();
        ResourceLocation key = EntityTypeRegistryApi.getKeyByLegacyId(this.legacyTypeId);
        this.entityTypeKey = key == null ? "" : key.toString();
        this.xPosition = legacy.c;
        this.yPosition = legacy.d;
        this.zPosition = legacy.e;
        this.yaw = legacy.f;
        this.pitch = legacy.g;
        List<SynchedEntityData.DataValue<?>> values = legacy.getTypedMetadata();
        if (values == null || values.isEmpty()) {
            values = LegacyEntityMetadataCodec.decodeWatchableList(legacy.getLegacyMetadata());
        }
        this.entityData = values == null ? Collections.<SynchedEntityData.DataValue<?>>emptyList() : values;
    }

    public void a(DataInputStream in) throws IOException {
        this.entityId = in.readInt();
        this.entityTypeKey = Packet.a(in, 32767);
        this.legacyTypeId = in.readInt();
        this.xPosition = in.readInt();
        this.yPosition = in.readInt();
        this.zPosition = in.readInt();
        this.yaw = in.readByte();
        this.pitch = in.readByte();
        this.entityData = EntityDataStreamCodec.readValues(in);
    }

    public void a(DataOutputStream out) throws IOException {
        out.writeInt(this.entityId);
        Packet.a(this.entityTypeKey == null ? "" : this.entityTypeKey, out);
        out.writeInt(this.legacyTypeId);
        out.writeInt(this.xPosition);
        out.writeInt(this.yPosition);
        out.writeInt(this.zPosition);
        out.writeByte(this.yaw);
        out.writeByte(this.pitch);
        EntityDataStreamCodec.writeValues(out, this.entityData);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        int keySize = this.entityTypeKey == null ? 0 : this.entityTypeKey.length() * 2;
        int dataSize = this.entityData == null ? 0 : this.entityData.size() * 8;
        return 2 + keySize + 26 + dataSize;
    }

    public List<SynchedEntityData.DataValue<?>> getEntityData() {
        return this.entityData;
    }
}

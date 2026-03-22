package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Binary codec for SynchedEntityData value lists used by entity wire v2 packets.
 */
public final class EntityDataStreamCodec {
    private EntityDataStreamCodec() {}

    public static void writeValues(DataOutputStream out, List<SynchedEntityData.DataValue<?>> values) throws IOException {
        if (values == null || values.isEmpty()) {
            out.writeShort(0);
            return;
        }

        List<SynchedEntityData.DataValue<?>> sorted = new ArrayList<SynchedEntityData.DataValue<?>>(values.size());
        for (int i = 0; i < values.size(); i++) {
            SynchedEntityData.DataValue<?> value = values.get(i);
            if (value == null || value.serializer() == null) {
                continue;
            }
            int serializerId = EntityDataSerializers.getSerializedId(value.serializer());
            if (serializerId < 0) {
                continue;
            }
            sorted.add(value);
        }

        Collections.sort(sorted, new Comparator<SynchedEntityData.DataValue<?>>() {
            public int compare(SynchedEntityData.DataValue<?> left, SynchedEntityData.DataValue<?> right) {
                return left.id() - right.id();
            }
        });

        out.writeShort(sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            SynchedEntityData.DataValue<?> value = sorted.get(i);
            int serializerId = EntityDataSerializers.getSerializedId(value.serializer());
            out.writeByte(value.id() & 255);
            out.writeByte(serializerId & 255);
            writeValue(out, serializerId, value.value());
        }
    }

    @SuppressWarnings("unchecked")
    public static List<SynchedEntityData.DataValue<?>> readValues(DataInputStream in) throws IOException {
        int count = in.readUnsignedShort();
        if (count <= 0) {
            return Collections.emptyList();
        }

        ArrayList<SynchedEntityData.DataValue<?>> out = new ArrayList<SynchedEntityData.DataValue<?>>(count);
        for (int i = 0; i < count; i++) {
            int id = in.readUnsignedByte();
            int serializerId = in.readUnsignedByte();
            EntityDataSerializer serializer = EntityDataSerializers.getSerializer(serializerId);
            Object value = readValue(in, serializerId);
            if (serializer == null) {
                continue;
            }
            out.add(new SynchedEntityData.DataValue(id, serializer, value));
        }

        return out;
    }

    private static void writeValue(DataOutputStream out, int serializerId, Object value) throws IOException {
        switch (serializerId) {
            case 0:
                out.writeByte(value == null ? 0 : ((Byte)value).byteValue());
                return;
            case 1:
                out.writeShort(value == null ? 0 : ((Short)value).shortValue());
                return;
            case 2:
                out.writeInt(value == null ? 0 : ((Integer)value).intValue());
                return;
            case 3:
                out.writeFloat(value == null ? 0.0F : ((Float)value).floatValue());
                return;
            case 4:
                Packet.a(value == null ? "" : (String)value, out);
                return;
            case 5:
                writeItemStack(out, (ItemStack)value);
                return;
            case 6:
                ChunkCoordinates coords = (ChunkCoordinates)value;
                if (coords == null) {
                    out.writeInt(0);
                    out.writeInt(0);
                    out.writeInt(0);
                } else {
                    out.writeInt(coords.x);
                    out.writeInt(coords.y);
                    out.writeInt(coords.z);
                }
                return;
            case 7:
                out.writeBoolean(value != null && ((Boolean)value).booleanValue());
                return;
            default:
                throw new IOException("Unknown entity data serializer id: " + serializerId);
        }
    }

    private static Object readValue(DataInputStream in, int serializerId) throws IOException {
        switch (serializerId) {
            case 0:
                return Byte.valueOf(in.readByte());
            case 1:
                return Short.valueOf(in.readShort());
            case 2:
                return Integer.valueOf(in.readInt());
            case 3:
                return Float.valueOf(in.readFloat());
            case 4:
                return Packet.a(in, 32767);
            case 5:
                return readItemStack(in);
            case 6:
                return new ChunkCoordinates(in.readInt(), in.readInt(), in.readInt());
            case 7:
                return Boolean.valueOf(in.readBoolean());
            default:
                throw new IOException("Unknown entity data serializer id: " + serializerId);
        }
    }

    private static void writeItemStack(DataOutputStream out, ItemStack stack) throws IOException {
        LegacyItemStackCodec.LegacyStackData encoded = LegacyItemStackCodec.encode(stack, false);
        if (encoded == null) {
            out.writeShort(-1);
            return;
        }
        out.writeShort(encoded.legacyId);
        out.writeByte(encoded.count);
        out.writeShort(encoded.damage);
    }

    private static ItemStack readItemStack(DataInputStream in) throws IOException {
        short id = in.readShort();
        if (id < 0) {
            return null;
        }
        int count = in.readByte();
        short damage = in.readShort();
        return LegacyItemStackCodec.decode(id, count, damage, null);
    }
}

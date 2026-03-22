package net.minecraft.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Utility read/write helpers for ItemStack to match legacy Packet encoding. */
public final class PacketHelper {
    private PacketHelper() {}

    public static ItemStack readItemStack(DataInputStream in) throws IOException {
        short id = in.readShort();
        if (id < 0) return null;
        byte count = in.readByte();
        short damage = in.readShort();

        // Read NBT data if present
        NBTTagCompound legacyTag = null;
        short nbtLength = in.readShort();
        if (nbtLength > 0) {
            byte[] nbtBytes = new byte[nbtLength];
            in.readFully(nbtBytes);
            try {
                ByteArrayInputStream bais = new ByteArrayInputStream(nbtBytes);
                GZIPInputStream gzis = new GZIPInputStream(bais);
                DataInputStream nbtIn = new DataInputStream(gzis);
                NBTBase nbtBase = NBTBase.b(nbtIn);
                nbtIn.close();
                if (nbtBase instanceof NBTTagCompound) {
                    legacyTag = (NBTTagCompound) nbtBase;
                }
            } catch (Exception e) {
                System.err.println("[PacketHelper] Error reading item NBT: " + e.getMessage());
            }
        }

        return LegacyItemStackCodec.decode(id, count, damage, legacyTag);
    }

    public static void writeItemStack(ItemStack stack, DataOutputStream out) throws IOException {
        if (stack == null) {
            out.writeShort(-1);
            return;
        }

        LegacyItemStackCodec.LegacyStackData encoded = LegacyItemStackCodec.encode(stack, true);
        if (encoded == null) {
            out.writeShort(-1);
            return;
        }

        out.writeShort(encoded.legacyId);
        out.writeByte(encoded.count);
        out.writeShort(encoded.damage);

        // Write NBT data if present
        if (encoded.tag != null) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                GZIPOutputStream gzos = new GZIPOutputStream(baos);
                DataOutputStream nbtOut = new DataOutputStream(gzos);
                NBTBase.a(encoded.tag, nbtOut);
                nbtOut.close();
                byte[] nbtBytes = baos.toByteArray();
                out.writeShort(nbtBytes.length);
                out.write(nbtBytes);
            } catch (Exception e) {
                System.err.println("[PacketHelper] Error writing item NBT: " + e.getMessage());
                out.writeShort(-1);
            }
        } else {
            out.writeShort(-1);
        }
    }
}

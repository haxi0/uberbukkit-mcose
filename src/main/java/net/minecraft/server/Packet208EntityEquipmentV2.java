package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Modern mod-wire entity equipment projection packet.
 */
public class Packet208EntityEquipmentV2 extends Packet {
    public int entityId;
    public int slot;
    public int itemId;
    public int itemDamage;

    public Packet208EntityEquipmentV2() {
    }

    public Packet208EntityEquipmentV2(int entityId, int slot, int itemId, int itemDamage) {
        this.entityId = entityId;
        this.slot = slot;
        this.itemId = itemId;
        this.itemDamage = itemDamage;
    }

    public Packet208EntityEquipmentV2(Packet5EntityEquipment legacy) {
        this.entityId = legacy.a;
        this.slot = legacy.b;
        this.itemId = legacy.c;
        this.itemDamage = legacy.d;
    }

    public void a(DataInputStream in) throws IOException {
        this.entityId = in.readInt();
        this.slot = in.readShort();
        this.itemId = in.readShort();
        this.itemDamage = in.readShort();
    }

    public void a(DataOutputStream out) throws IOException {
        out.writeInt(this.entityId);
        out.writeShort(this.slot);
        out.writeShort(this.itemId);
        out.writeShort(this.itemDamage);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 10;
    }
}

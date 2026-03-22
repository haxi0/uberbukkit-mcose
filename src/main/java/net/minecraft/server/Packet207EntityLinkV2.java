package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Modern mod-wire entity attach/passenger link packet.
 */
public class Packet207EntityLinkV2 extends Packet {
    public int entityId;
    public int vehicleEntityId;

    public Packet207EntityLinkV2() {
    }

    public Packet207EntityLinkV2(int entityId, int vehicleEntityId) {
        this.entityId = entityId;
        this.vehicleEntityId = vehicleEntityId;
    }

    public Packet207EntityLinkV2(Packet39AttachEntity legacy) {
        this.entityId = legacy.a;
        this.vehicleEntityId = legacy.b;
    }

    public void a(DataInputStream in) throws IOException {
        this.entityId = in.readInt();
        this.vehicleEntityId = in.readInt();
    }

    public void a(DataOutputStream out) throws IOException {
        out.writeInt(this.entityId);
        out.writeInt(this.vehicleEntityId);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 8;
    }
}

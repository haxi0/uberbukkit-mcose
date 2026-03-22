package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Modern mod-wire movement packet covering relative move/look and absolute teleport.
 */
public class Packet206EntityMoveV2 extends Packet {
    private static final int FLAG_HAS_RELATIVE = 1;
    private static final int FLAG_HAS_ROTATION = 1 << 1;
    private static final int FLAG_TELEPORT = 1 << 2;

    public int entityId;
    public boolean hasRelativeMove;
    public byte relX;
    public byte relY;
    public byte relZ;
    public boolean hasRotation;
    public byte yaw;
    public byte pitch;
    public boolean teleport;
    public int xPosition;
    public int yPosition;
    public int zPosition;

    public Packet206EntityMoveV2() {
    }

    public static Packet206EntityMoveV2 fromLegacy(Packet30Entity legacy) {
        Packet206EntityMoveV2 out = new Packet206EntityMoveV2();
        out.entityId = legacy.a;
        if (legacy instanceof Packet31RelEntityMove) {
            out.hasRelativeMove = true;
            out.relX = legacy.b;
            out.relY = legacy.c;
            out.relZ = legacy.d;
        } else if (legacy instanceof Packet33RelEntityMoveLook) {
            out.hasRelativeMove = true;
            out.relX = legacy.b;
            out.relY = legacy.c;
            out.relZ = legacy.d;
            out.hasRotation = true;
            out.yaw = legacy.e;
            out.pitch = legacy.f;
        } else if (legacy instanceof Packet32EntityLook) {
            out.hasRotation = true;
            out.yaw = legacy.e;
            out.pitch = legacy.f;
        }
        return out;
    }

    public static Packet206EntityMoveV2 fromTeleport(Packet34EntityTeleport legacy) {
        Packet206EntityMoveV2 out = new Packet206EntityMoveV2();
        out.entityId = legacy.a;
        out.teleport = true;
        out.xPosition = legacy.b;
        out.yPosition = legacy.c;
        out.zPosition = legacy.d;
        out.hasRotation = true;
        out.yaw = legacy.e;
        out.pitch = legacy.f;
        return out;
    }

    public void a(DataInputStream in) throws IOException {
        this.entityId = in.readInt();
        int flags = in.readUnsignedByte();
        this.hasRelativeMove = (flags & FLAG_HAS_RELATIVE) != 0;
        this.hasRotation = (flags & FLAG_HAS_ROTATION) != 0;
        this.teleport = (flags & FLAG_TELEPORT) != 0;

        if (this.teleport) {
            this.xPosition = in.readInt();
            this.yPosition = in.readInt();
            this.zPosition = in.readInt();
        }

        if (this.hasRelativeMove) {
            this.relX = in.readByte();
            this.relY = in.readByte();
            this.relZ = in.readByte();
        }

        if (this.hasRotation) {
            this.yaw = in.readByte();
            this.pitch = in.readByte();
        }
    }

    public void a(DataOutputStream out) throws IOException {
        out.writeInt(this.entityId);
        int flags = 0;
        if (this.hasRelativeMove) {
            flags |= FLAG_HAS_RELATIVE;
        }
        if (this.hasRotation) {
            flags |= FLAG_HAS_ROTATION;
        }
        if (this.teleport) {
            flags |= FLAG_TELEPORT;
        }
        out.writeByte(flags);

        if (this.teleport) {
            out.writeInt(this.xPosition);
            out.writeInt(this.yPosition);
            out.writeInt(this.zPosition);
        }

        if (this.hasRelativeMove) {
            out.writeByte(this.relX);
            out.writeByte(this.relY);
            out.writeByte(this.relZ);
        }

        if (this.hasRotation) {
            out.writeByte(this.yaw);
            out.writeByte(this.pitch);
        }
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        int size = 5;
        if (this.teleport) {
            size += 12;
        }
        if (this.hasRelativeMove) {
            size += 3;
        }
        if (this.hasRotation) {
            size += 2;
        }
        return size;
    }
}

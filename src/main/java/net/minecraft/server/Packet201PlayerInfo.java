package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Player list info (Tab overlay)
 */
public class Packet201PlayerInfo extends Packet {

    public String playerName;
    public boolean connected;
    public int ping;

    public Packet201PlayerInfo() {}

    public Packet201PlayerInfo(String playerName, boolean connected, int ping) {
        this.playerName = sanitizePlayerName(playerName);
        this.connected = connected;
        this.ping = ping;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.playerName = Packet.a(datainputstream, 16);
        this.connected = datainputstream.readByte() != 0;
        this.ping = datainputstream.readShort();
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        Packet.a(this.playerName, dataoutputstream);
        dataoutputstream.writeByte(this.connected ? 1 : 0);
        dataoutputstream.writeShort(this.ping);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 5 + (this.playerName == null ? 0 : this.playerName.length() * 2);
    }

    private static String sanitizePlayerName(String playerName) {
        if (playerName == null) {
            return "";
        }

        if (playerName.length() > 16) {
            return playerName.substring(0, 16);
        }

        return playerName;
    }
}


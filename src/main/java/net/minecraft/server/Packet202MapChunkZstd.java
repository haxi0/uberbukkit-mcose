package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Packet202MapChunkZstd extends Packet {

    public int a;
    public int b;
    public int c;
    public int d;
    public int e;
    public int f;
    public byte[] g;
    public int h;
    public byte[] rawData;

    public Packet202MapChunkZstd() {
        this.k = true;
    }

    public Packet202MapChunkZstd(int i, int j, int k, int l, int i1, int j1, World world) {
        this(i, j, k, l, i1, j1, world.getMultiChunkData(i, j, k, l, i1, j1));
    }

    public Packet202MapChunkZstd(int i, int j, int k, int l, int i1, int j1, byte[] data) {
        this.k = true;
        this.a = i;
        this.b = j;
        this.c = k;
        this.d = l;
        this.e = i1;
        this.f = j1;
        this.rawData = data;
    }

    public void a(DataInputStream datainputstream) throws IOException {
        this.a = datainputstream.readInt();
        this.b = datainputstream.readShort();
        this.c = datainputstream.readInt();
        this.d = datainputstream.read() + 1;
        this.e = datainputstream.read() + 1;
        this.f = datainputstream.read() + 1;
        this.h = datainputstream.readInt();
        byte[] compressed = new byte[this.h];
        datainputstream.readFully(compressed);

        int expectedSize = this.d * this.e * this.f * 5 / 2;
        this.g = ZstdRuntime.decompressZstd(compressed, expectedSize);
        if (this.g == null) {
            throw new IOException("Bad zstd chunk data format");
        }
    }

    public void a(DataOutputStream dataoutputstream) throws IOException {
        dataoutputstream.writeInt(this.a);
        dataoutputstream.writeShort(this.b);
        dataoutputstream.writeInt(this.c);
        dataoutputstream.write(this.d - 1);
        dataoutputstream.write(this.e - 1);
        dataoutputstream.write(this.f - 1);
        dataoutputstream.writeInt(this.h);
        dataoutputstream.write(this.g, 0, this.h);
    }

    public void a(NetHandler nethandler) {
        nethandler.a(this);
    }

    public int a() {
        return 17 + this.h;
    }

    public Packet clone() {
        Packet202MapChunkZstd clone = new Packet202MapChunkZstd();
        clone.a = this.a;
        clone.b = this.b;
        clone.c = this.c;
        clone.d = this.d;
        clone.e = this.e;
        clone.f = this.f;
        clone.h = this.h;
        clone.k = this.k;
        if (this.g != null) {
            clone.g = new byte[this.g.length];
            System.arraycopy(this.g, 0, clone.g, 0, this.g.length);
        }
        if (this.rawData != null) {
            clone.rawData = new byte[this.rawData.length];
            System.arraycopy(this.rawData, 0, clone.rawData, 0, this.rawData.length);
        }
        return clone;
    }
}

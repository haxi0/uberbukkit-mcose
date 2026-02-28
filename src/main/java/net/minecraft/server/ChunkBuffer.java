package net.minecraft.server;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;

class ChunkBuffer extends ByteArrayOutputStream {

    private static final AtomicLong regionWriteZstdTotal = new AtomicLong(0L);
    private static final AtomicLong regionWriteZlibTotal = new AtomicLong(0L);
    private static final AtomicLong regionWriteZstdFallbackTotal = new AtomicLong(0L);
    private static final AtomicLong regionWriteZstdNanosTotal = new AtomicLong(0L);
    private static final AtomicLong regionWriteZlibNanosTotal = new AtomicLong(0L);

    private int b;
    private int c;
    private byte codec;

    final RegionFile a;

    public ChunkBuffer(RegionFile regionfile, int i, int j, byte codec) {
        super(8096);
        this.a = regionfile;
        this.b = i;
        this.c = j;
        this.codec = codec;
    }

    public void close() {
        byte[] payload = this.buf;
        int payloadLength = this.count;
        byte codecToWrite = this.codec;

        if (this.codec == 3) {
            long zstdStart = System.nanoTime();
            byte[] compressed = ZstdRuntime.compressZstd(this.buf, 0, this.count, 3);
            if (compressed != null) {
                long zstdDuration = System.nanoTime() - zstdStart;
                if (zstdDuration > 0L) {
                    regionWriteZstdNanosTotal.addAndGet(zstdDuration);
                }
                regionWriteZstdTotal.incrementAndGet();
                payload = compressed;
                payloadLength = compressed.length;
                codecToWrite = 3;
            } else {
                regionWriteZstdFallbackTotal.incrementAndGet();
                long zlibStart = System.nanoTime();
                byte[] fallback = compressDeflate(this.buf, this.count);
                if (fallback != null) {
                    long zlibDuration = System.nanoTime() - zlibStart;
                    if (zlibDuration > 0L) {
                        regionWriteZlibNanosTotal.addAndGet(zlibDuration);
                    }
                    regionWriteZlibTotal.incrementAndGet();
                    payload = fallback;
                    payloadLength = fallback.length;
                    codecToWrite = 2;
                }
            }
        } else if (this.codec == 2) {
            regionWriteZlibTotal.incrementAndGet();
        }

        this.a.a(this.b, this.c, payload, payloadLength, codecToWrite);
    }

    public static long getRegionWriteZstdTotal() {
        return regionWriteZstdTotal.get();
    }

    public static long getRegionWriteZlibTotal() {
        return regionWriteZlibTotal.get();
    }

    public static long getRegionWriteZstdFallbackTotal() {
        return regionWriteZstdFallbackTotal.get();
    }

    public static long getRegionWriteZstdNanosTotal() {
        return regionWriteZstdNanosTotal.get();
    }

    public static long getRegionWriteZlibNanosTotal() {
        return regionWriteZlibNanosTotal.get();
    }

    private byte[] compressDeflate(byte[] source, int length) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(source, 0, length);
            deflater.finish();
            byte[] out = new byte[length + 128];
            int written = deflater.deflate(out);
            if (written <= 0) {
                return null;
            }
            byte[] compressed = new byte[written];
            System.arraycopy(out, 0, compressed, 0, written);
            return compressed;
        } finally {
            deflater.end();
        }
    }
}

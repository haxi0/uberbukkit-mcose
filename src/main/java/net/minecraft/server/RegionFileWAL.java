package net.minecraft.server;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Locale;
import java.util.zip.CRC32;

final class RegionFileWAL {

    private static final int WAL_MAGIC = 1296251724;
    private static final int HEADER_BYTES = 20;
    private static final int MAX_CHUNK_BYTES = 4096 * 255 - 5;
    private static final int MAX_WAL_PAYLOAD_BYTES = MAX_CHUNK_BYTES + 1;
    private static final int WAL_LOG_LEVEL = getWalLogLevel();
    private final File walPath;
    private final RandomAccessFile walFile;

    public RegionFileWAL(File file1) throws IOException {
        this.walPath = new File(file1.getPath() + ".wal");
        this.walFile = new RandomAccessFile(this.walPath, "rw");
        this.logWal(1, "open " + this.walPath.getName());
    }

    public void appendPendingWrite(int i, int j, byte[] abyte, int k, byte codec, boolean flag) throws IOException {
        if (i >= 0 && i < 32 && j >= 0 && j < 32) {
            if (k >= 0 && k <= abyte.length && k <= MAX_CHUNK_BYTES) {
                byte[] walPayload = new byte[k + 1];
                walPayload[0] = codec;
                System.arraycopy(abyte, 0, walPayload, 1, k);
                int l = computeChecksum(walPayload, walPayload.length);

                this.walFile.seek(this.walFile.length());
                this.walFile.writeInt(WAL_MAGIC);
                this.walFile.writeInt(i);
                this.walFile.writeInt(j);
                this.walFile.writeInt(walPayload.length);
                this.walFile.writeInt(l);
                this.walFile.write(walPayload, 0, walPayload.length);
                if (flag) {
                    this.sync();
                }
            } else {
                throw new IOException("Invalid WAL payload length: " + k);
            }
        } else {
            throw new IOException("Chunk coordinates out of WAL range: " + i + "," + j);
        }
    }

    public ArrayList readPendingWrites() throws IOException {
        ArrayList arraylist = new ArrayList();
        long i = this.walFile.length();

        if (i == 0L) {
            return arraylist;
        } else {
            this.walFile.seek(0L);

            while (this.walFile.getFilePointer() + (long) HEADER_BYTES <= i) {
                long j = this.walFile.getFilePointer();
                int k = this.walFile.readInt();

                if (k != WAL_MAGIC) {
                    this.logWal(1, "bad magic in " + this.walPath.getName() + ", truncating at " + j);
                    break;
                }

                int l = this.walFile.readInt();
                int i1 = this.walFile.readInt();
                int j1 = this.walFile.readInt();
                int k1 = this.walFile.readInt();

                if (l >= 0 && l < 32 && i1 >= 0 && i1 < 32 && j1 >= 0 && j1 <= MAX_WAL_PAYLOAD_BYTES) {
                    long l1 = this.walFile.getFilePointer() + (long) j1;

                    if (l1 > i) {
                        this.logWal(1, "truncated payload in " + this.walPath.getName() + ", truncating at " + j);
                        break;
                    }

                    byte[] abyte = new byte[j1];

                    this.walFile.readFully(abyte);
                    int i2 = computeChecksum(abyte, j1);
                    if (i2 != k1) {
                        this.logWal(1, "checksum mismatch in " + this.walPath.getName() + ", truncating at " + j);
                        break;
                    }

                    byte codec = 2;
                    byte[] payload = abyte;
                    int payloadLength = j1;
                    if (j1 > 0) {
                        byte marker = abyte[0];
                        if (marker == 1 || marker == 2 || marker == 3) {
                            codec = marker;
                            payloadLength = j1 - 1;
                            payload = new byte[payloadLength];
                            if (payloadLength > 0) {
                                System.arraycopy(abyte, 1, payload, 0, payloadLength);
                            }
                        }
                    }

                    arraylist.add(new PendingEntry(l, i1, payload, payloadLength, codec));
                } else {
                    this.logWal(1, "invalid metadata in " + this.walPath.getName() + ", truncating at " + j);
                    break;
                }
            }

            if (arraylist.isEmpty() && i > 0L) {
                this.clear(true);
            }

            return arraylist;
        }
    }

    public void sync() throws IOException {
        this.walFile.getFD().sync();
    }

    public void clear(boolean flag) throws IOException {
        this.walFile.setLength(0L);
        this.walFile.seek(0L);
        if (flag) {
            this.sync();
        }
    }

    public void close() throws IOException {
        this.logWal(1, "close " + this.walPath.getName());
        this.walFile.close();
    }

    private static int getWalLogLevel() {
        String s = System.getProperty("mcregion.wal.log", "basic");
        String s1 = s.toLowerCase(Locale.ROOT);

        if (!"0".equals(s1) && !"false".equals(s1) && !"off".equals(s1)) {
            return !"2".equals(s1) && !"verbose".equals(s1) && !"debug".equals(s1) ? 1 : 2;
        } else {
            return 0;
        }
    }

    private void logWal(int i, String s) {
        if (WAL_LOG_LEVEL >= i) {
            System.out.println("[McRegion WAL] " + s);
        }
    }

    private static int computeChecksum(byte[] abyte, int i) {
        CRC32 crc32 = new CRC32();

        crc32.update(abyte, 0, i);
        return (int) crc32.getValue();
    }

    static final class PendingEntry {

        final int chunkX;
        final int chunkZ;
        final byte[] data;
        final int length;
        final byte codec;

        PendingEntry(int i, int j, byte[] abyte, int k, byte codec) {
            this.chunkX = i;
            this.chunkZ = j;
            this.data = abyte;
            this.length = k;
            this.codec = codec;
        }
    }
}

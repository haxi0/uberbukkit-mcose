package net.minecraft.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RegionFileCache {

    // MCOSE: Use strong references instead of SoftReference to prevent GC from
    // silently collecting RegionFile objects (and their unflushed WAL data)
    // between chunk saves and flush/close calls. With SoftReference, memory
    // pressure during a large save could cause the GC to clear references,
    // meaning RegionFile.b() (close/flush WAL) would be skipped entirely.
    private static final Map<File, RegionFile> a = new HashMap<File, RegionFile>();
    private static boolean bulkConversionMode = false;

    private RegionFileCache() {
    }

    public static synchronized RegionFile a(File file1, int i, int j) {
        File file2 = new File(file1, "region");
        File file3 = new File(file2, "r." + (i >> 5) + "." + (j >> 5) + ".mcr");
        RegionFile regionfile = a.get(file3);

        if (regionfile != null) {
            return regionfile;
        }

        if (!file2.exists()) {
            file2.mkdirs();
        }

        if (a.size() >= 256) {
            a();
        }

        regionfile = new RegionFile(file3);
        a.put(file3, regionfile);
        return regionfile;
    }

    public static synchronized void setBulkConversionMode(boolean flag) {
        if (bulkConversionMode == flag) {
            return;
        }

        a();
        bulkConversionMode = flag;
    }

    public static synchronized boolean isBulkConversionMode() {
        return bulkConversionMode;
    }

    public static synchronized void a() {
        Iterator<Map.Entry<File, RegionFile>> iterator = a.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<File, RegionFile> entry = iterator.next();

            try {
                RegionFile regionfile = entry.getValue();

                if (regionfile != null) {
                    regionfile.b();
                }
            } catch (IOException ioexception) {
                ioexception.printStackTrace();
            }
        }

        a.clear();
    }

    public static int b(File file1, int i, int j) {
        RegionFile regionfile = a(file1, i, j);

        return regionfile.a();
    }

    public static DataInputStream c(File file1, int i, int j) {
        RegionFile regionfile = a(file1, i, j);

        return regionfile.a(i & 31, j & 31);
    }

    public static DataOutputStream d(File file1, int i, int j) {
        RegionFile regionfile = a(file1, i, j);

        return regionfile.b(i & 31, j & 31);
    }
}

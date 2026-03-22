package net.minecraft.server;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

public class WorldLoaderServer extends WorldLoader {

    public WorldLoaderServer(File file1) {
        super(file1);
    }

    public IDataManager a(String s, boolean flag) {
        return new ServerNBTManager(this.a, s, flag);
    }

    public boolean isConvertable(String s) {
        WorldData worlddata = this.b(s);

        return worlddata != null && worlddata.i() == WorldSaveVersions.LEGACY_PRE_MCREGION;
    }

    public boolean hasLegacyChunkData(String s) {
        File file1 = new File(this.a, s);
        if (!file1.exists() || !file1.isDirectory()) {
            return false;
        }

        if (this.containsLegacyChunkFiles(file1)) {
            return true;
        }

        File file2 = new File(file1, "DIM-1");
        return this.containsLegacyChunkFiles(file2);
    }

    public boolean convert(String s, IProgressUpdate iprogressupdate) {
        iprogressupdate.a(0);
        ArrayList arraylist = new ArrayList();
        ArrayList arraylist1 = new ArrayList();
        ArrayList arraylist2 = new ArrayList();
        ArrayList arraylist3 = new ArrayList();
        File file1 = new File(this.a, s);
        File file2 = new File(file1, "DIM-1");
        if (!file1.exists() || !file1.isDirectory()) {
            return false;
        }

        System.out.println("Scanning folders...");
        this.a(file1, arraylist, arraylist1);
        if (file2.exists()) {
            this.a(file2, arraylist2, arraylist3);
        }

        int i = arraylist.size() + arraylist2.size() + arraylist1.size() + arraylist3.size();

        System.out.println("Total conversion count is " + i);
        this.a(file1, arraylist, 0, i, iprogressupdate);
        if (file2.exists()) {
            this.a(file2, arraylist2, arraylist.size(), i, iprogressupdate);
        }
        WorldData worlddata = this.b(s);
        if (worlddata == null) {
            throw new RuntimeException("Failed to load world metadata after legacy chunk conversion for '" + s + "'");
        }

        worlddata.a(WorldSaveVersions.MCREGION_1);
        IDataManager idatamanager = this.a(s, false);

        idatamanager.a(worlddata);
        this.a(arraylist1, arraylist.size() + arraylist2.size(), i, iprogressupdate);
        if (file2.exists()) {
            this.a(arraylist3, arraylist.size() + arraylist2.size() + arraylist1.size(), i, iprogressupdate);
        }
        try {
            // Keep conversion parity with client RegionCore upgrade logic so chest inventories/facing
            // and other legacy item-bearing NBT are rewritten in one deterministic pass.
            RegionCoreWorldUpgrader.upgradeWorldToRegionCore(file1, MinecraftServer.log);
        } catch (RuntimeException runtimeexception) {
            throw new RuntimeException("Failed to run RegionCore post-conversion upgrade for '" + s + "'", runtimeexception);
        }

        iprogressupdate.a(100);

        return true;
    }

    private boolean containsLegacyChunkFiles(File file1) {
        if (file1 == null || !file1.exists() || !file1.isDirectory()) {
            return false;
        }

        ChunkFileFilter chunkfilefilter = new ChunkFileFilter((EmptyClass2) null);
        ChunkFilenameFilter chunkfilenamefilter = new ChunkFilenameFilter((EmptyClass2) null);
        File[] afile = file1.listFiles(chunkfilefilter);
        if (afile == null) {
            return false;
        }

        for (int i = 0; i < afile.length; ++i) {
            File file2 = afile[i];
            if (file2 == null) {
                continue;
            }
            File[] afile2 = file2.listFiles(chunkfilefilter);
            if (afile2 == null) {
                continue;
            }

            for (int j = 0; j < afile2.length; ++j) {
                File file3 = afile2[j];
                if (file3 == null) {
                    continue;
                }
                File[] afile3 = file3.listFiles(chunkfilenamefilter);
                if (afile3 != null && afile3.length > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private void a(File file1, ArrayList arraylist, ArrayList arraylist1) {
        if (file1 == null || !file1.exists() || !file1.isDirectory()) {
            return;
        }
        ChunkFileFilter chunkfilefilter = new ChunkFileFilter((EmptyClass2) null);
        ChunkFilenameFilter chunkfilenamefilter = new ChunkFilenameFilter((EmptyClass2) null);
        File[] afile = file1.listFiles(chunkfilefilter);
        if (afile == null) {
            return;
        }
        File[] afile1 = afile;
        int i = afile.length;

        for (int j = 0; j < i; ++j) {
            File file2 = afile1[j];

            arraylist1.add(file2);
            File[] afile2 = file2.listFiles(chunkfilefilter);
            if (afile2 == null) {
                continue;
            }
            File[] afile3 = afile2;
            int k = afile2.length;

            for (int l = 0; l < k; ++l) {
                File file3 = afile3[l];
                File[] afile4 = file3.listFiles(chunkfilenamefilter);
                if (afile4 == null) {
                    continue;
                }
                File[] afile5 = afile4;
                int i1 = afile4.length;

                for (int j1 = 0; j1 < i1; ++j1) {
                    File file4 = afile5[j1];

                    arraylist.add(new ChunkFile(file4));
                }
            }
        }
    }

    private void a(File file1, ArrayList arraylist, int i, int j, IProgressUpdate iprogressupdate) {
        Collections.sort(arraylist);
        byte[] abyte = new byte[4096];
        Iterator iterator = arraylist.iterator();

        try {
            while (iterator.hasNext()) {
                ChunkFile chunkfile = (ChunkFile) iterator.next();
                int k = chunkfile.b();
                int l = chunkfile.c();
                RegionFile regionfile = RegionFileCache.a(file1, k, l);

                if (!regionfile.c(k & 31, l & 31)) {
                    DataInputStream datainputstream = null;
                    DataOutputStream dataoutputstream = null;

                    try {
                        datainputstream = new DataInputStream(new GZIPInputStream(new FileInputStream(chunkfile.a())));
                        dataoutputstream = regionfile.b(k & 31, l & 31);

                        int i1;
                        while ((i1 = datainputstream.read(abyte)) != -1) {
                            dataoutputstream.write(abyte, 0, i1);
                        }
                    } catch (IOException ioexception) {
                        throw new RuntimeException("Failed converting legacy chunk '" + chunkfile.a().getAbsolutePath()
                                + "' into region coordinates (" + k + "," + l + ")", ioexception);
                    } finally {
                        if (dataoutputstream != null) {
                            try {
                                dataoutputstream.close();
                            } catch (IOException ignored) {}
                        }
                        if (datainputstream != null) {
                            try {
                                datainputstream.close();
                            } catch (IOException ignored) {}
                        }
                    }
                }

                ++i;
                if (j > 0) {
                    int j1 = (int) Math.round(100.0D * (double) i / (double) j);

                    iprogressupdate.a(j1);
                }
            }
        } finally {
            RegionFileCache.a();
        }
    }

    private void a(ArrayList arraylist, int i, int j, IProgressUpdate iprogressupdate) {
        Iterator iterator = arraylist.iterator();

        while (iterator.hasNext()) {
            File file1 = (File) iterator.next();
            if (file1 == null) {
                continue;
            }
            File[] afile = file1.listFiles();

            if (afile != null) {
                a(afile);
            }
            file1.delete();
            ++i;
            if (j > 0) {
                int k = (int) Math.round(100.0D * (double) i / (double) j);

                iprogressupdate.a(k);
            }
        }
    }
}

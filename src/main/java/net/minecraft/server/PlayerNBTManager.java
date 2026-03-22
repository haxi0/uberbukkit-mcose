package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;
import com.projectposeidon.johnymuffin.UUIDManager;

import java.io.*;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class PlayerNBTManager implements PlayerFileData, IDataManager {

    private static final Logger a = Logger.getLogger("Minecraft");
    private final File b;
    private final File c;
    private final File d;
    private final long e = System.currentTimeMillis();
    private UUID uuid = null; // CraftBukkit

    public PlayerNBTManager(File file1, String s, boolean flag) {
        this.b = new File(file1, s);
        this.b.mkdirs();
        this.c = new File(this.b, "players");
        this.d = new File(this.b, "data");
        this.d.mkdirs();
        if (flag) {
            this.c.mkdirs();
        }

        this.f();
    }

    private void f() {
        try {
            File file1 = new File(this.b, "session.lock");
            DataOutputStream dataoutputstream = new DataOutputStream(new FileOutputStream(file1));

            try {
                dataoutputstream.writeLong(this.e);
            } finally {
                dataoutputstream.close();
            }
        } catch (IOException ioexception) {
            ioexception.printStackTrace();
            throw new RuntimeException("Failed to check session lock, aborting");
        }
    }

    protected File a() {
        return this.b;
    }

    public void b() {
        File lockFile = new File(this.b, "session.lock");
        IOException lastIoException = null;
        final int maxAttempts = 3;

        for (int attempt = 1; attempt <= maxAttempts; ++attempt) {
            DataInputStream lockInput = null;
            try {
                lockInput = new DataInputStream(new FileInputStream(lockFile));
                if (lockInput.readLong() != this.e) {
                    throw new MinecraftException("The save is being accessed from another location, aborting");
                }

                // Lock check succeeded; no need to keep retrying.
                return;
            } catch (IOException ioexception) {
                lastIoException = ioexception;

                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(50L * attempt);
                    } catch (InterruptedException interruptedexception) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                if (lockInput != null) {
                    try {
                        lockInput.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }

        String detail = "unknown I/O error";
        if (lastIoException != null) {
            String message = lastIoException.getMessage();
            detail = lastIoException.getClass().getSimpleName() + (message != null ? ": " + message : "");
        }

        throw new MinecraftException("Failed to check session lock at " + lockFile.getPath()
            + " after " + maxAttempts + " attempts (" + detail + "), aborting");
    }

    public IChunkLoader a(WorldProvider worldprovider) {
        if (worldprovider instanceof WorldProviderHell) {
            File file1 = new File(this.b, "DIM-1");

            file1.mkdirs();
            return new ChunkLoader(file1, true);
        } else {
            return new ChunkLoader(this.b, true);
        }
    }

    public WorldData c() {
        File file1 = new File(this.b, "level.dat");
        NBTTagCompound nbttagcompound;
        NBTTagCompound nbttagcompound1;

        if (file1.exists()) {
            try {
                nbttagcompound = CompressedStreamTools.a((InputStream) (new FileInputStream(file1)));
                nbttagcompound1 = nbttagcompound.k("Data");
                return new WorldData(nbttagcompound1);
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }

        file1 = new File(this.b, "level.dat_old");
        if (file1.exists()) {
            try {
                nbttagcompound = CompressedStreamTools.a((InputStream) (new FileInputStream(file1)));
                nbttagcompound1 = nbttagcompound.k("Data");
                return new WorldData(nbttagcompound1);
            } catch (Exception exception1) {
                exception1.printStackTrace();
            }
        }

        return null;
    }

    public void a(WorldData worlddata, List list) {
        NBTTagCompound nbttagcompound = worlddata.a(list);
        NBTTagCompound nbttagcompound1 = new NBTTagCompound();

        nbttagcompound1.a("Data", (NBTBase) nbttagcompound);

        try {
            File file1 = new File(this.b, "level.dat_new");
            File file2 = new File(this.b, "level.dat_old");
            File file3 = new File(this.b, "level.dat");

            CompressedStreamTools.a(nbttagcompound1, (OutputStream) (new FileOutputStream(file1)));
            if (file2.exists()) {
                file2.delete();
            }

            file3.renameTo(file2);
            if (file3.exists()) {
                file3.delete();
            }

            file1.renameTo(file3);
            if (file1.exists()) {
                file1.delete();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void a(WorldData worlddata) {
        NBTTagCompound nbttagcompound = worlddata.a();
        NBTTagCompound nbttagcompound1 = new NBTTagCompound();

        nbttagcompound1.a("Data", (NBTBase) nbttagcompound);

        try {
            File file1 = new File(this.b, "level.dat_new");
            File file2 = new File(this.b, "level.dat_old");
            File file3 = new File(this.b, "level.dat");

            CompressedStreamTools.a(nbttagcompound1, (OutputStream) (new FileOutputStream(file1)));
            if (file2.exists()) {
                file2.delete();
            }

            file3.renameTo(file2);
            if (file3.exists()) {
                file3.delete();
            }

            file1.renameTo(file3);
            if (file1.exists()) {
                file1.delete();
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void a(EntityHuman entityhuman) {
        if ((boolean) PoseidonConfig.getInstance().getConfigOption("settings.save-playerdata-by-uuid")) {
            try {
                NBTTagCompound nbttagcompound = new NBTTagCompound();

                entityhuman.d(nbttagcompound);
                UUID resolvedUuid = UUIDManager.getInstance().getUUIDGraceful(entityhuman.name);
                File file1 = new File(this.c, "_tmp_.dat");
                File file2 = new File(this.c, resolvedUuid + ".dat");
                File legacyNameFile = new File(this.c, entityhuman.name + ".dat");

                CompressedStreamTools.a(nbttagcompound, (OutputStream) (new FileOutputStream(file1)));
                if (file2.exists()) {
                    file2.delete();
                }

                file1.renameTo(file2);

                // Keep a username-based compatibility copy so data survives UUID source
                // transitions (online <-> graceful/offline) across restarts.
                if (!file2.equals(legacyNameFile)) {
                    try {
                        copyFileUsingStream(file2, legacyNameFile);
                    } catch (IOException copyEx) {
                        a.warning("Failed to write legacy username player data copy for " + entityhuman.name + ": " + copyEx.getMessage());
                    }
                }
            } catch (Exception exception) {
                a.warning("Failed to save player data for " + entityhuman.name);
            }
        } else {
            try {
                NBTTagCompound nbttagcompound = new NBTTagCompound();

                entityhuman.d(nbttagcompound);
                File file1 = new File(this.c, "_tmp_.dat");
                File file2 = new File(this.c, entityhuman.name + ".dat");

                CompressedStreamTools.a(nbttagcompound, (OutputStream) (new FileOutputStream(file1)));
                if (file2.exists()) {
                    file2.delete();
                }

                file1.renameTo(file2);
            } catch (Exception exception) {
                a.warning("Failed to save player data for " + entityhuman.name);
            }
        }
    }

    public void b(EntityHuman entityhuman) {
        NBTTagCompound nbttagcompound = this.a(entityhuman.name);

        if (nbttagcompound != null) {
            entityhuman.e(nbttagcompound);
        }
    }

    //Credit https://www.journaldev.com/861/java-copy-file
    private static void copyFileUsingStream(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }

    public NBTTagCompound a(String s) {
        if ((boolean) PoseidonConfig.getInstance().getConfigOption("settings.save-playerdata-by-uuid")) {
            UUIDManager uuidManager = UUIDManager.getInstance();
            UUID gracefulUuid = uuidManager.getUUIDGraceful(s);
            UUID onlineUuid = uuidManager.getUUIDFromUsername(s, true);
            UUID offlineUuid = UUIDManager.generateOfflineUUID(s);

            File primaryFile = new File(this.c, gracefulUuid + ".dat");

            List<File> candidates = new ArrayList<File>(4);
            Set<String> seenPaths = new HashSet<String>(4);

            addCandidate(candidates, seenPaths, primaryFile);
            addCandidate(candidates, seenPaths, onlineUuid == null ? null : new File(this.c, onlineUuid + ".dat"));
            addCandidate(candidates, seenPaths, offlineUuid == null ? null : new File(this.c, offlineUuid + ".dat"));
            addCandidate(candidates, seenPaths, new File(this.c, s + ".dat"));

            for (File candidate : candidates) {
                if (!candidate.exists()) {
                    continue;
                }

                try {
                    NBTTagCompound data = CompressedStreamTools.a((InputStream) (new FileInputStream(candidate)));

                    // If we loaded from a fallback file, migrate to the primary UUID filename
                    // to stabilize subsequent loads and preserve restart consistency.
                    if (!candidate.equals(primaryFile) && !primaryFile.exists()) {
                        try {
                            copyFileUsingStream(candidate, primaryFile);
                            System.out.println("Migrated playerdata for " + s + " from " + candidate.getName() + " to " + primaryFile.getName());
                        } catch (IOException migrationEx) {
                            a.warning("Failed to migrate player data for " + s + " to " + primaryFile.getName() + ": " + migrationEx.getMessage());
                        }
                    }

                    return data;
                } catch (Exception exception) {
                    a.warning("Failed to load player data for " + s + " from " + candidate.getName());
                }
            }

            return null;
        } else {
            try {
                File file1 = new File(this.c, s + ".dat");

                if (file1.isFile()) {
                    return CompressedStreamTools.a((InputStream) (new FileInputStream(file1)));
                }
            } catch (Exception exception) {
                a.warning("Failed to load player data for " + s);
            }

            return null;
        }
    }

    private static void addCandidate(List<File> candidates, Set<String> seenPaths, File candidate) {
        if (candidate == null) {
            return;
        }

        String path = candidate.getPath();
        if (seenPaths.add(path)) {
            candidates.add(candidate);
        }
    }

    public PlayerFileData d() {
        return this;
    }

    public void e() {
    }

    public File b(String s) {
        return new File(this.d, s + ".dat");
    }

    // CraftBukkit start
    public UUID getUUID() {
        if (uuid != null) return uuid;
        try {
            File file1 = new File(this.b, "uid.dat");
            if (!file1.exists()) {
                DataOutputStream dos = new DataOutputStream(new FileOutputStream(file1));
                uuid = UUID.randomUUID();
                dos.writeLong(uuid.getMostSignificantBits());
                dos.writeLong(uuid.getLeastSignificantBits());
                dos.close();
            } else {
                DataInputStream dis = new DataInputStream(new FileInputStream(file1));
                uuid = new UUID(dis.readLong(), dis.readLong());
                dis.close();
            }
            return uuid;
        } catch (IOException ex) {
            return null;
        }
    }
    // CraftBukkit end
}

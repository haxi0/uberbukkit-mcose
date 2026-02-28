package net.minecraft.server;

import java.util.List;

import uk.betacraft.uberbukkit.Uberbukkit;

public class WorldData {

    private long a;
    private int b;
    private int c;
    private int d;
    private long e;
    private long f;
    private long g;
    private NBTTagCompound h;
    private int i;
    public String name; // CraftBukkit - private -> public
    private int k;
    private boolean l;
    private int m;
    private boolean n;
    private int o;
    // UberBukkit: terrain type and alpha snow flag
    private int terrainType = 0; // 0=DEFAULT,1=ALPHA,2=FLAT,3=SKY,5=ALPHA_SNOW
    private boolean alphaSnow = false;
    // Hardcore mode
    private boolean hardcore = false;
    // Difficulty: 0=peaceful, 1=easy, 2=normal, 3=hard
    private int difficulty = 1;

    public WorldData(NBTTagCompound nbttagcompound) {
        this.a = nbttagcompound.getLong("RandomSeed");
        this.b = nbttagcompound.e("SpawnX");
        this.c = nbttagcompound.e("SpawnY");
        this.d = nbttagcompound.e("SpawnZ");
        this.e = nbttagcompound.getLong("Time");
        this.f = nbttagcompound.getLong("LastPlayed");
        this.g = nbttagcompound.getLong("SizeOnDisk");
        this.name = nbttagcompound.getString("LevelName");
        this.k = nbttagcompound.e("version");
        this.m = nbttagcompound.e("rainTime");
        this.l = nbttagcompound.m("raining");
        this.o = nbttagcompound.e("thunderTime");
        this.n = nbttagcompound.m("thundering");
        if (nbttagcompound.hasKey("Player")) {
            this.h = nbttagcompound.k("Player");
            this.i = this.h.e("Dimension");
        }
        if (nbttagcompound.hasKey("TerrainType")) {
            this.terrainType = nbttagcompound.e("TerrainType");
        }
        if (nbttagcompound.hasKey("AlphaSnow")) {
            this.alphaSnow = nbttagcompound.m("AlphaSnow");
        }
        if (nbttagcompound.hasKey("Hardcore")) {
            this.hardcore = nbttagcompound.m("Hardcore");
        }
        if (nbttagcompound.hasKey("Difficulty")) {
            this.difficulty = nbttagcompound.e("Difficulty");
        }
        // Load gamerules, defaulting to true if not present
        if (nbttagcompound.hasKey("DoDayNightCycle")) this.doDayNightCycle = nbttagcompound.m("DoDayNightCycle");
        if (nbttagcompound.hasKey("TNTExplodes")) this.tntexplodes = nbttagcompound.m("TNTExplodes");
        if (nbttagcompound.hasKey("MobGriefing")) this.mobGriefing = nbttagcompound.m("MobGriefing");
        if (nbttagcompound.hasKey("DoWeatherCycle")) this.doWeatherCycle = nbttagcompound.m("DoWeatherCycle");
        if (nbttagcompound.hasKey("DoFireTick")) this.doFireTick = nbttagcompound.m("DoFireTick");
        if (nbttagcompound.hasKey("ShowDeathMessages")) this.showDeathMessages = nbttagcompound.m("ShowDeathMessages");
        if (nbttagcompound.hasKey("SleepEnabled")) this.sleepEnabled = nbttagcompound.m("SleepEnabled");
        if (nbttagcompound.hasKey("AdvertiseAchievements")) this.advertiseAchievements = nbttagcompound.m("AdvertiseAchievements");
        if (nbttagcompound.hasKey("KeepInventory")) this.keepInventory = nbttagcompound.m("KeepInventory");
        // Integer gamerules
        if (nbttagcompound.hasKey("SpawnRadius")) this.spawnRadius = nbttagcompound.e("SpawnRadius");
    }

    public WorldData(long i, String s) {
        this.a = i;
        this.name = s;
    }

    public WorldData(WorldData worlddata) {
        this.a = worlddata.a;
        this.b = worlddata.b;
        this.c = worlddata.c;
        this.d = worlddata.d;
        this.e = worlddata.e;
        this.f = worlddata.f;
        this.g = worlddata.g;
        this.h = worlddata.h;
        this.i = worlddata.i;
        this.name = worlddata.name;
        this.k = worlddata.k;
        this.m = worlddata.m;
        this.l = worlddata.l;
        this.o = worlddata.o;
        this.n = worlddata.n;
        this.terrainType = worlddata.terrainType;
        this.alphaSnow = worlddata.alphaSnow;
        this.hardcore = worlddata.hardcore;
        this.difficulty = worlddata.difficulty;
        this.doDayNightCycle = worlddata.doDayNightCycle;
        this.tntexplodes = worlddata.tntexplodes;
        this.mobGriefing = worlddata.mobGriefing;
        this.doWeatherCycle = worlddata.doWeatherCycle;
        this.doFireTick = worlddata.doFireTick;
        this.showDeathMessages = worlddata.showDeathMessages;
        this.advertiseAchievements = worlddata.advertiseAchievements;
        this.sleepEnabled = worlddata.sleepEnabled;
        this.keepInventory = worlddata.keepInventory;
        this.spawnRadius = worlddata.spawnRadius;
    }

    public NBTTagCompound a() {
        NBTTagCompound nbttagcompound = new NBTTagCompound();

        this.a(nbttagcompound, this.h);
        return nbttagcompound;
    }

    public NBTTagCompound a(List list) {
        NBTTagCompound nbttagcompound = new NBTTagCompound();
        EntityHuman entityhuman = null;
        NBTTagCompound nbttagcompound1 = null;

        if (list.size() > 0) {
            entityhuman = (EntityHuman) list.get(0);
        }

        if (entityhuman != null) {
            nbttagcompound1 = new NBTTagCompound();
            entityhuman.d(nbttagcompound1);
        }

        this.a(nbttagcompound, nbttagcompound1);
        return nbttagcompound;
    }

    private void a(NBTTagCompound nbttagcompound, NBTTagCompound nbttagcompound1) {
        nbttagcompound.setLong("RandomSeed", this.a);
        nbttagcompound.a("SpawnX", this.b);
        nbttagcompound.a("SpawnY", this.c);
        nbttagcompound.a("SpawnZ", this.d);
        nbttagcompound.setLong("Time", this.e);
        nbttagcompound.setLong("SizeOnDisk", this.g);
        nbttagcompound.setLong("LastPlayed", System.currentTimeMillis());
        nbttagcompound.setString("LevelName", this.name);
        nbttagcompound.a("version", this.k);
        nbttagcompound.a("rainTime", this.m);
        nbttagcompound.a("raining", this.l);
        nbttagcompound.a("thunderTime", this.o);
        nbttagcompound.a("thundering", this.n);
        if (nbttagcompound1 != null) {
            nbttagcompound.a("Player", nbttagcompound1);
        }
        nbttagcompound.a("TerrainType", this.terrainType);
        nbttagcompound.a("AlphaSnow", this.alphaSnow);
        nbttagcompound.a("Hardcore", this.hardcore);
        nbttagcompound.a("Difficulty", this.difficulty);
        nbttagcompound.a("DoDayNightCycle", this.doDayNightCycle);
        nbttagcompound.a("TNTExplodes", this.tntexplodes);
        nbttagcompound.a("MobGriefing", this.mobGriefing);
        nbttagcompound.a("DoWeatherCycle", this.doWeatherCycle);
        nbttagcompound.a("DoFireTick", this.doFireTick);
        nbttagcompound.a("ShowDeathMessages", this.showDeathMessages);
        nbttagcompound.a("SleepEnabled", this.sleepEnabled);
        nbttagcompound.a("AdvertiseAchievements", this.advertiseAchievements);
        nbttagcompound.a("KeepInventory", this.keepInventory);
        // Integer gamerules
        nbttagcompound.a("SpawnRadius", this.spawnRadius);
    }

    public long getSeed() {
        return this.a;
    }

    public int c() {
        return this.b;
    }

    public int d() {
        return this.c;
    }

    public int e() {
        return this.d;
    }

    public long f() {
        return this.e;
    }

    public long g() {
        return this.g;
    }

    public int h() {
        return this.i;
    }

    public void a(long i) {
        this.e = i;
    }

    public void b(long i) {
        this.g = i;
    }

    public void setSpawn(int i, int j, int k) {
        this.b = i;
        this.c = j;
        this.d = k;
    }

    public void a(String s) {
        this.name = s;
    }

    public int i() {
        return this.k;
    }

    public void a(int i) {
        this.k = i;
    }

    public boolean isThundering() {
        // uberbukkit
        if (Uberbukkit.getTargetPVN() < 11) return false;

        return this.n;
    }

    public void setThundering(boolean flag) {
        this.n = flag;
    }

    public int getThunderDuration() {
        return this.o;
    }

    public void setThunderDuration(int i) {
        this.o = i;
    }

    public boolean hasStorm() {
        // uberbukkit
        if (Uberbukkit.getTargetPVN() < 11) return false;

        return this.l;
    }

    public void setStorm(boolean flag) {
        this.l = flag;
    }

    public int getWeatherDuration() {
        return this.m;
    }

    public void setWeatherDuration(int i) {
        this.m = i;
    }

    // UberBukkit: terrain type support
    public int getTerrainType() { return this.terrainType; }
    public void setTerrainType(int t) { this.terrainType = t; }
    public boolean isSnowWorld() { return this.alphaSnow; }
    public void setSnowWorld(boolean flag) { this.alphaSnow = flag; }

    // Hardcore mode support
    public boolean isHardcore() { return this.hardcore; }
    public void setHardcore(boolean flag) { this.hardcore = flag; }

    // Difficulty support: 0=peaceful, 1=easy, 2=normal, 3=hard
    public int getDifficulty() { return this.difficulty; }
    public void setDifficulty(int d) { this.difficulty = Math.max(0, Math.min(3, d)); }

    // Gamerules (boolean)
    private boolean doDayNightCycle = true;
    private boolean tntexplodes = true;
    private boolean mobGriefing = true;
    private boolean doWeatherCycle = true;
    private boolean doFireTick = true;
    private boolean showDeathMessages = true;
    private boolean advertiseAchievements = true; // Default to true - broadcast achievements to all players
    private boolean sleepEnabled = false; // If true, sleeping in beds is enabled
    private boolean keepInventory = false; // If true, players keep inventory on death
    
    // Gamerules (integer)
    private int spawnRadius = 10; // Vanilla default spawn randomization radius

    // Poseidon gamerule compatibility: default to true if absent
    public boolean getDoDayNightCycle() { return this.doDayNightCycle; }
    public void setDoDayNightCycle(boolean v) { this.doDayNightCycle = v; }
    public boolean getTntexplodes() { return this.tntexplodes; }
    public void setTntexplodes(boolean v) { this.tntexplodes = v; }
    public boolean getMobGriefing() { return this.mobGriefing; }
    public void setMobGriefing(boolean v) { this.mobGriefing = v; }
    public boolean getDoWeatherCycle() { return this.doWeatherCycle; }
    public void setDoWeatherCycle(boolean v) { this.doWeatherCycle = v; }
    public boolean getDoFireTick() { return this.doFireTick; }
    public void setDoFireTick(boolean v) { this.doFireTick = v; }
    public boolean getShowDeathMessages() { return this.showDeathMessages; }
    public void setShowDeathMessages(boolean v) { this.showDeathMessages = v; }
    public boolean getSleepEnabled() { return this.sleepEnabled; }
    public void setSleepEnabled(boolean v) { this.sleepEnabled = v; }
    public boolean getAdvertiseAchievements() { return this.advertiseAchievements; }
    public void setAdvertiseAchievements(boolean v) { this.advertiseAchievements = v; }
    public boolean getKeepInventory() { return this.keepInventory; }
    public void setKeepInventory(boolean v) { this.keepInventory = v; }
    
    // Integer gamerules
    public int getSpawnRadius() { return this.spawnRadius; }
    public void setSpawnRadius(int v) { this.spawnRadius = Math.max(0, v); }
}

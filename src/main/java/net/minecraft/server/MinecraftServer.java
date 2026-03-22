package net.minecraft.server;

import com.legacyminecraft.poseidon.Poseidon;
import com.legacyminecraft.poseidon.PoseidonConfig;
import com.legacyminecraft.poseidon.PoseidonPlugin;
import com.legacyminecraft.poseidon.util.ServerLogRotator;
// import com.legacyminecraft.poseidon.utility.PerformanceStatistic; // Not used in uberbukkit
import com.legacyminecraft.poseidon.utility.PoseidonVersionChecker;
import com.projectposeidon.johnymuffin.UUIDManager;
import com.legacyminecraft.poseidon.watchdog.WatchDogThread;
import jline.ConsoleReader;
import joptsimple.OptionSet;
import org.bukkit.Bukkit;
import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.ChunkCompressionThread;
import org.bukkit.craftbukkit.LoggerOutputStream;
import org.bukkit.craftbukkit.command.ColouredConsoleSender;
import org.bukkit.craftbukkit.scheduler.CraftScheduler;
import org.bukkit.craftbukkit.util.ServerShutdownThread;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.PluginLoadOrder;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.server.event.EventBus;
import net.minecraft.server.mod.ModLoader;

import net.minecraft.server.threading.ThreadingManager;

// CraftBukkit start
//import com.projectposeidon.johnymuffin.UUIDCacheFile;
// CraftBukkit end

public class MinecraftServer implements Runnable, ICommandListener {

    public enum StartupReadinessStatus {
        BOOTING,
        WARMING_UP,
        READY
    }

    public static Logger log = Logger.getLogger("Minecraft");
    public static HashMap trackerList = new HashMap();
    public NetworkListenThread networkListenThread;
    public PropertyManager propertyManager;
    // public WorldServer[] worldServer; // CraftBukkit - removed!
    public ServerConfigurationManager serverConfigurationManager;
    public ConsoleCommandHandler consoleCommandHandler; // CraftBukkit - made public
    private boolean isRunning = true;
    public boolean isStopped = false;
    int ticks = 0;
    public String i;
    public int j;
    private List r = new ArrayList();
    private List s = Collections.synchronizedList(new ArrayList());
    // public EntityTracker[] tracker = new EntityTracker[2]; // CraftBukkit - removed!
    public boolean onlineMode;
    public boolean spawnAnimals;
    public boolean pvpMode;
    public boolean allowFlight;
    public boolean voiceChatEnabled;
    public int voiceChatPort;
    public final VoiceChatRoomManager chatRoomManager;
    private static final double DEFAULT_VOICE_CHAT_RADIUS = 48.0D;
    private static final int DEFAULT_VOICE_CHAT_PORT = 24454;
    private static final int DEFAULT_VOICE_RATE_MAX_PACKETS_PER_SEC = 120;
    private static final int DEFAULT_VOICE_RATE_MAX_BYTES_PER_SEC = 131072;
    private static final int DEFAULT_VOICE_RATE_BURST_SECONDS = 2;
    private static final long VOICE_DEBUG_SUMMARY_INTERVAL_MS = 30000L;
    private double voiceChatBroadcastRadius = DEFAULT_VOICE_CHAT_RADIUS;
    private int voiceRateMaxPacketsPerSec = DEFAULT_VOICE_RATE_MAX_PACKETS_PER_SEC;
    private int voiceRateMaxBytesPerSec = DEFAULT_VOICE_RATE_MAX_BYTES_PER_SEC;
    private int voiceRateBurstSeconds = DEFAULT_VOICE_RATE_BURST_SECONDS;
    private boolean voiceRequireUdpBind = false;
    private VoiceChatUDPServer voiceChatUDPServer;
    private volatile boolean voiceUdpHealthy = false;
    private volatile String voiceUdpState = "not-started";
    private long lastVoiceDebugSummaryAt = 0L;
    private String lastVoiceDebugSummarySignature = null;
    private CommunicationDispatcher communicationDispatcher;

    // CraftBukkit start
    public List<WorldServer> worlds = new ArrayList<WorldServer>();
    public CraftServer server;
    public OptionSet options;
    public ColouredConsoleSender console;
    public ConsoleReader reader;
    public static int currentTick;
    public String configuredLevelType; // Added for server.properties level-type
    public int defaultGameMode = 0; // 0=survival, 1=creative, 2=hardcore
    // CraftBukkit end

    //Poseidon Start
//    private WatchDogThread watchDogThread;
    private boolean modLoaderSupport = false;
//    private PoseidonVersionChecker poseidonVersionChecker;
    //Poseidon End

    private volatile StartupReadinessStatus startupReadinessStatus = StartupReadinessStatus.BOOTING;
    private boolean tickCatchupEnabled = true;
    private long maxTickCatchupBacklogMs = 200L;
    private long tickCatchupWarnIntervalMs = 30000L;
    private long lastTickCatchupDropWarningMs = 0L;
    private volatile float debugTickRateTps = 20.0F;
    private volatile long tickIntervalMs = 50L;
    
    // GUI mode flag - when true, don't call System.exit() on stop
    public static boolean guiMode = false;
    
    // Friends verification handler for P2P verification on online-mode servers
    public final FriendsVerificationHandler friendsVerificationHandler;

    public MinecraftServer(OptionSet options) { // CraftBukkit - adds argument OptionSet
        new ThreadSleepForever(this);
        this.chatRoomManager = new VoiceChatRoomManager(this);
        this.friendsVerificationHandler = new FriendsVerificationHandler(this);

        // CraftBukkit start
        this.options = options;
        try {
            this.reader = new ConsoleReader();
        } catch (IOException ex) {
            Logger.getLogger(MinecraftServer.class.getName()).log(Level.SEVERE, null, ex);
        }
        Runtime.getRuntime().addShutdownHook(new ServerShutdownThread(this));
        // CraftBukkit end
    }

    private boolean init() throws UnknownHostException { // CraftBukkit - added throws UnknownHostException
        this.startupReadinessStatus = StartupReadinessStatus.BOOTING;
        this.consoleCommandHandler = new ConsoleCommandHandler(this);
        
        // Only start the console reader thread if NOT in GUI mode
        // In GUI mode, commands come from the GUI text field, not stdin
        if (!guiMode) {
            ThreadCommandReader threadcommandreader = new ThreadCommandReader(this);
            threadcommandreader.setDaemon(true);
            threadcommandreader.start();
        }
        ConsoleLogManager.init(this); // CraftBukkit

        // CraftBukkit start
        System.setOut(new PrintStream(new LoggerOutputStream(log, Level.INFO), true));
        System.setErr(new PrintStream(new LoggerOutputStream(log, Level.SEVERE), true));
        // CraftBukkit end

        //If Poseidon Config DEBUG is enabled, enable debug mode
        if (options.has("debug-config")) {
            log.info("[Poseidon] Configuration debug mode has been enabled. This will cause the poseidon.yml to be reloaded every time the server starts.");
            PoseidonConfig.getInstance().resetConfig();
        }

        modLoaderSupport = PoseidonConfig.getInstance().getBoolean("settings.support.modloader.enable", false);
        this.loadRuntimeTuningConfig();

        if (modLoaderSupport) {
            log.info("EXPERIMENTAL MODLOADERMP SUPPORT ENABLED.");
            if (!isModloaderPresent()) {
                log.severe("ModLoaderMP support is enabled, however, it isn't present. Please install it before enabling this setting");
                this.logStartupFailureContext("ModLoaderMP support requested but ModLoader is missing", null);
                return false;
            }
            try {
                Class.forName("net.minecraft.server.ModLoader");
                // Optional: invoke via reflection if present
            } catch (ClassNotFoundException ignore) {}
        }

        log.info("Starting Minecraft Oldschool Edition server version Beta 1.7.6");
        if (Runtime.getRuntime().maxMemory() / 1024L / 1024L < 512L) {
            log.warning("**** NOT ENOUGH RAM!");
            log.warning("To start the server with more ram, launch it as \"java -Xmx1024M -Xms1024M -jar minecraft_server.jar\"");
        }

        log.info("Loading properties");
        this.propertyManager = new PropertyManager(this.options); // CraftBukkit - CLI argument support
        String s = this.propertyManager.getString("server-ip", "");

        this.onlineMode = this.propertyManager.getBoolean("online-mode", true);
        this.spawnAnimals = this.propertyManager.getBoolean("spawn-animals", true);
        this.pvpMode = this.propertyManager.getBoolean("pvp", true);
        this.allowFlight = this.propertyManager.getBoolean("allow-flight", false);
        this.voiceChatEnabled = this.propertyManager.getBoolean("voice-chat", true);
        this.voiceChatPort = this.propertyManager.getInt("voice-chat-port", DEFAULT_VOICE_CHAT_PORT);
        this.voiceRateMaxPacketsPerSec = Math.max(1, this.propertyManager.getInt("voice-rate-max-packets-per-sec", DEFAULT_VOICE_RATE_MAX_PACKETS_PER_SEC));
        this.voiceRateMaxBytesPerSec = Math.max(1, this.propertyManager.getInt("voice-rate-max-bytes-per-sec", DEFAULT_VOICE_RATE_MAX_BYTES_PER_SEC));
        this.voiceRateBurstSeconds = Math.max(1, this.propertyManager.getInt("voice-rate-burst-seconds", DEFAULT_VOICE_RATE_BURST_SECONDS));
        this.voiceRequireUdpBind = this.propertyManager.getBoolean("voice-require-udp-bind", false);
        if (this.voiceChatEnabled) {
            log.info("Voice chat broadcasting enabled (UDP port: " + this.voiceChatPort + ")");
            log.info("Voice TCP fallback limiter configured (packets/sec=" + this.voiceRateMaxPacketsPerSec
                + ", bytes/sec=" + this.voiceRateMaxBytesPerSec
                + ", burst-seconds=" + this.voiceRateBurstSeconds + ")");
            if (this.voiceRequireUdpBind) {
                log.info("Voice UDP bind is required for startup (voice-require-udp-bind=true).");
            }
        } else {
            this.voiceUdpState = "disabled";
        }
        this.configuredLevelType = this.propertyManager.getString("level-type", "DEFAULT").toUpperCase(); // Added
        
        // Parse default gamemode from server.properties (survival, creative, or hardcore)
        String gamemodeStr = this.propertyManager.getString("gamemode", "survival").toLowerCase();
        if (gamemodeStr.equals("creative") || gamemodeStr.equals("c") || gamemodeStr.equals("1")) {
            this.defaultGameMode = 1;
            log.info("Default game mode: Creative");
        } else if (gamemodeStr.equals("hardcore") || gamemodeStr.equals("h") || gamemodeStr.equals("2")) {
            this.defaultGameMode = 2;
            log.info("Default game mode: HARDCORE - Death is permanent!");
        } else {
            this.defaultGameMode = 0;
            if (!gamemodeStr.equals("survival") && !gamemodeStr.equals("s") && !gamemodeStr.equals("0")) {
                log.warning("Unknown gamemode '" + gamemodeStr + "' in server.properties. Defaulting to survival.");
            } else {
                log.info("Default game mode: Survival");
            }
        }
        
        String preflightWorldName = this.propertyManager.getString("level-name", "world");
        try {
            WorldLoaderServer preflightLoader = new WorldLoaderServer(new File("."));
            boolean needsLegacyConversion = preflightLoader.isConvertable(preflightWorldName)
                || preflightLoader.hasLegacyChunkData(preflightWorldName);
            if (needsLegacyConversion) {
                log.info("Converting map!");
                if (!preflightLoader.convert(preflightWorldName, new ConvertProgressUpdater(this))) {
                    throw new RuntimeException("Legacy world conversion did not complete for '" + preflightWorldName + "'");
                }
            }
            RegionCoreWorldUpgrader.upgradeWorldToRegionCore(new File(preflightWorldName), log);
            if (this.propertyManager.getBoolean("allow-nether", true)) {
                String preflightNetherName = preflightWorldName + "_" + Environment.getEnvironment(-1).toString().toLowerCase();
                RegionCoreWorldUpgrader.upgradeWorldToRegionCore(new File(preflightNetherName), log);
            }
        } catch (RuntimeException conversionFailure) {
            log.log(Level.SEVERE, "[RegionCore] Failed to upgrade world data during preflight startup.", conversionFailure);
            this.logStartupFailureContext("World preflight conversion failed", conversionFailure);
            return false;
        }

        InetAddress inetaddress = null;

        if (s.length() > 0) {
            inetaddress = InetAddress.getByName(s);
        }

        int i = this.propertyManager.getInt("server-port", 25565);

        log.info("Starting Minecraft server on " + (s.length() == 0 ? "*" : s) + ":" + i);

        try {
            this.networkListenThread = new NetworkListenThread(this, inetaddress, i);
        } catch (Throwable ioexception) { // CraftBukkit - IOException -> Throwable
            log.warning("**** FAILED TO BIND TO PORT!");
            log.log(Level.WARNING, "The exception was: " + ioexception.toString());
            log.warning("Perhaps a server is already running on that port?");
            this.logStartupFailureContext("Network bind failed", ioexception);
            return false;
        }

        if (!this.onlineMode) {
            log.warning("**** SERVER IS RUNNING IN OFFLINE/INSECURE MODE!");
            log.warning("The server will make no attempt to authenticate usernames. Beware.");
            log.warning("While this makes the game possible to play without internet access, it also opens up the ability for hackers to connect with any username they choose.");
            log.warning("To change this, set \"online-mode\" to \"true\" in the server.settings file.");
        }

        this.serverConfigurationManager = new ServerConfigurationManager(this);
        // CraftBukkit - removed trackers
        long j = System.nanoTime();
        String s1 = this.propertyManager.getString("level-name", "world");
        String s2 = this.propertyManager.getString("level-seed", "");
        long k = (new Random()).nextLong();

        if (s2.length() > 0) {
            try {
                k = Long.parseLong(s2);
            } catch (NumberFormatException numberformatexception) {
                k = (long) s2.hashCode();
            }
        }

        log.info("Preparing level \"" + s1 + "\"");
        this.a(new WorldLoaderServer(new File(".")), s1, k);
        // Bootstrap registries in deterministic order.
        try {
            net.minecraft.server.registry.RegistryBootstrap.initialize();
        } catch (Throwable ignored) {}

        try {
            ModLoader.initialize(new File("."));
        } catch (Throwable t) {
            log.warning("[ModLoader] Failed during startup: " + t.getMessage());
        }

        //Project Poseidon Start
        Poseidon.getServer().initializeServer();
        //Project Poseidon End

        // CraftBukkit start
        long elapsed = System.nanoTime() - j;
        String time = String.format("%.3fs", elapsed / 1000000000.0D);
        
        // UberBukkit - Initialize server-wide statistics tracking
        ServerStatistics.getInstance();
        log.info("[ServerStats] Server-wide statistics tracking initialized");
        
        // Start voice chat UDP server
        this.startVoiceChatServer();
        this.startCommunicationDispatcher();

        this.setStartupReadinessStatus(StartupReadinessStatus.READY);
        log.info("Done (" + time + ")! For help, type \"help\" or \"?\"");

        // log rotator process start.
        if ((boolean) PoseidonConfig.getInstance().getConfigOption("settings.per-day-log-file.enabled") && (boolean) PoseidonConfig.getInstance().getConfigOption("settings.per-day-log-file.latest-log.enabled")) {
            String latestLogFileName = "latest";
            ServerLogRotator serverLogRotator = new ServerLogRotator(latestLogFileName);
            serverLogRotator.start();
        }

        if (this.propertyManager.properties.containsKey("spawn-protection")) {
            log.info("'spawn-protection' in server.properties has been moved to 'settings.spawn-radius' in bukkit.yml. I will move your config for you.");
            this.server.setSpawnRadius(this.propertyManager.getInt("spawn-protection", 16));
            this.propertyManager.properties.remove("spawn-protection");
            this.propertyManager.savePropertiesFile();
        }
        return true;
    }

    private void loadRuntimeTuningConfig() {
        this.tickCatchupEnabled = PoseidonConfig.getInstance().getConfigBoolean("settings.tick-catchup.enabled", true);
        this.maxTickCatchupBacklogMs = Math.max(50L, (long) getPoseidonConfigInt("settings.tick-catchup.max-backlog-ms", 200));
        int warnIntervalSeconds = Math.max(1, getPoseidonConfigInt("settings.tick-catchup.warn-interval-seconds", 30));
        this.tickCatchupWarnIntervalMs = warnIntervalSeconds * 1000L;
    }

    private int getPoseidonConfigInt(String key, int defaultValue) {
        try {
            Object value = PoseidonConfig.getInstance().getConfigOption(key, Integer.valueOf(defaultValue));
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(String.valueOf(value));
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    public boolean isModloaderPresent() {
        try {
            Class.forName("net.minecraft.server.ModLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void a(Convertable convertable, String s, long i) {
        // World storage has already been preflight-upgraded to RegionCore before bind.

        // CraftBukkit start
        for (int j = 0; j < (this.propertyManager.getBoolean("allow-nether", true) ? 2 : 1); ++j) {
            WorldServer world;
            int dimension = j == 0 ? 0 : -1;
            String worldType = Environment.getEnvironment(dimension).toString().toLowerCase();
            String name = (dimension == 0) ? s : s + "_" + worldType;

            ChunkGenerator gen = this.server.getGenerator(name);

            if (j == 0) {
                System.out.println("[MINECRAFT_SERVER_DEBUG] Preparing Overworld. configuredLevelType: " + this.configuredLevelType + ", level-name: " + s + ", seed: " + i);
                IDataManager dataManager = new ServerNBTManager(new File("."), s, true);
                WorldData worldData = dataManager.c();
                long seedToUse = i;

                // Determine integer typeId from configuredLevelType string via registry first, then fall back to legacy names
                int typeId = 0; // Default to 0 (NORMAL/DEFAULT)
                try {
                    String lt = this.configuredLevelType == null ? "default" : this.configuredLevelType.trim();
                    String normalizedKey = net.minecraft.server.registry.WorldTypeRegistryApi.normalizeInputIdentifier(lt);
                    Integer rid = net.minecraft.server.registry.WorldTypeRegistryApi.getByIdentifier(lt);
                    if (rid != null) {
                        typeId = rid.intValue();
                        log.info("[MinecraftServer] level-type resolved via registry '" + normalizedKey + "' => ID " + typeId);
                    } else {
                        // Legacy synonyms fallback
                        if (lt.equalsIgnoreCase("ALPHA")) { typeId = 1; log.info("[MinecraftServer] Configured level-type ALPHA maps to ID 1."); }
                        else if (lt.equalsIgnoreCase("FLAT")) { typeId = 2; log.info("[MinecraftServer] Configured level-type FLAT maps to ID 2."); }
                        else if (lt.equalsIgnoreCase("SKY")) { typeId = 3; log.info("[MinecraftServer] Configured level-type SKY maps to ID 3."); }
                        else if (lt.equalsIgnoreCase("ALPHA_SNOW") || lt.equalsIgnoreCase("ALPHA-SNOW") || lt.equalsIgnoreCase("ALPHASNOW")) { typeId = 5; log.info("[MinecraftServer] Configured level-type ALPHA_SNOW maps to ID 5."); }
                        else if (lt.equalsIgnoreCase("CLASSIC")) { typeId = 6; log.info("[MinecraftServer] Configured level-type CLASSIC maps to ID 6."); }
                        else if (lt.equalsIgnoreCase("INFDEV")) { typeId = 7; log.info("[MinecraftServer] Configured level-type INFDEV maps to ID 7."); }
                        else if (!lt.equalsIgnoreCase("DEFAULT") && !lt.equalsIgnoreCase("NORMAL")) {
                            log.warning("[MinecraftServer] Unknown level-type in server.properties: '" + lt + "'. Defaulting to type ID 0.");
                        }
                    }
                } catch (Throwable ignored) {}

                if (worldData == null) { // New world
                    log.info("[MinecraftServer] No existing world data for '" + s + "'. Creating new with seed: " + seedToUse + ", type ID: " + typeId);
                    worldData = new WorldData(seedToUse, s); // Creates with terrainType 0 initially
                    worldData.setTerrainType(typeId);      // Set the correct type
                    
                    // Set Alpha Snow flag when requested
                    if (typeId == 5) {
                        worldData.setSnowWorld(true);
                        log.info("[MinecraftServer] Enabled AlphaSnow flag for ALPHA_SNOW terrain type.");
                    }
                    
                    // Set appropriate default spawn for sky/alpha_snow worlds
                    if (typeId == 3) { // SKY terrain type
                        worldData.setSpawn(0, 90, 0); // Set a higher default Y for sky worlds
                        log.info("[MinecraftServer] Set initial spawn for SKY world to (0, 90, 0)");
                    } else if (typeId == 5) { // ALPHA_SNOW defaults to normal spawn; no special Y needed
                        log.info("[MinecraftServer] Creating ALPHA_SNOW world; spawn will be determined by world logic.");
                    }
                    
                    dataManager.a(worldData); // Save new WorldData (creates/updates level.dat)
                    log.info("[MinecraftServer] Saved new WorldData for '" + s + "' with TerrainType ID: " + worldData.getTerrainType());
                } else { // Existing world
                    seedToUse = worldData.getSeed(); // Use seed from loaded world data
                    log.info("[MinecraftServer] Loaded existing WorldData for '" + s + "'. Original TerrainType ID: " + worldData.getTerrainType() + ", Seed: " + seedToUse);
                    boolean changed = false;
                    if (worldData.getTerrainType() != typeId) {
                        log.info("[MinecraftServer] Overriding TerrainType ID for world '" + s + "' from " + worldData.getTerrainType() + " to " + typeId + " (from server.properties).");
                        worldData.setTerrainType(typeId);
                        changed = true;
                    }
                    // Ensure AlphaSnow flag aligns with ALPHA_SNOW type
                    if (typeId == 5 && !worldData.isSnowWorld()) {
                        log.info("[MinecraftServer] Enabling AlphaSnow flag on existing world '" + s + "' for ALPHA_SNOW terrain type.");
                        worldData.setSnowWorld(true);
                        changed = true;
                    }
                    if (changed) dataManager.a(worldData); // Save modified WorldData
                    log.info("[MinecraftServer] Using TerrainType ID: " + worldData.getTerrainType() + " for world '" + s + "'");
                }
                
                // WorldServer will use dataManager to load this worldData with the correct type and seed.
                world = new WorldServer(this, dataManager, s, dimension, seedToUse, org.bukkit.World.Environment.getEnvironment(dimension), gen);

            } else {
                String dim = "DIM-1";

                File newWorld = new File(new File(name), dim);
                File oldWorld = new File(new File(s), dim);

                if ((!newWorld.isDirectory()) && (oldWorld.isDirectory())) {
                    log.info("---- Migration of old " + worldType + " folder required ----");
                    log.info("Unfortunately due to the way that Minecraft implemented multiworld support in 1.6, Bukkit requires that you move your " + worldType + " folder to a new location in order to operate correctly.");
                    log.info("We will move this folder for you, but it will mean that you need to move it back should you wish to stop using Bukkit in the future.");
                    log.info("Attempting to move " + oldWorld + " to " + newWorld + "...");

                    if (newWorld.exists()) {
                        log.severe("A file or folder already exists at " + newWorld + "!");
                        log.info("---- Migration of old " + worldType + " folder failed ----");
                    } else if (newWorld.getParentFile().mkdirs()) {
                        if (oldWorld.renameTo(newWorld)) {
                            log.info("Success! To restore the nether in the future, simply move " + newWorld + " to " + oldWorld);
                            log.info("---- Migration of old " + worldType + " folder complete ----");
                        } else {
                            log.severe("Could not move folder " + oldWorld + " to " + newWorld + "!");
                            log.info("---- Migration of old " + worldType + " folder failed ----");
                        }
                    } else {
                        log.severe("Could not create path for " + newWorld + "!");
                        log.info("---- Migration of old " + worldType + " folder failed ----");
                    }
                }

                log.info("[MinecraftServer] Preparing Nether world '" + name + "' with seed from overworld: " + i);
                // Ensure Nether world data mirrors overworld terrain type for generator selection
                ServerNBTManager dataManagerNether = new ServerNBTManager(new File("."), name, true);
                WorldData dataNether = dataManagerNether.c();
                if (dataNether == null) {
                    dataNether = new WorldData(i, name);
                }
                try {
                    WorldServer overworld = this.worlds.get(0);
                    if (overworld != null && overworld.worldData != null) {
                        int ot = overworld.worldData.getTerrainType();
                        dataNether.setTerrainType(ot);
                    } else if (this.configuredLevelType != null && this.configuredLevelType.equalsIgnoreCase("CLASSIC")) {
                        dataNether.setTerrainType(6);
                    } else if (this.configuredLevelType != null && this.configuredLevelType.equalsIgnoreCase("INFDEV")) {
                        dataNether.setTerrainType(7);
                    }
                } catch (Throwable ignore) {}
                dataManagerNether.a(dataNether);
                world = new SecondaryWorldServer(this, dataManagerNether, name, dimension, i, this.worlds.get(0), org.bukkit.World.Environment.getEnvironment(dimension), gen);
            }

            if (gen != null) {
                world.getWorld().getPopulators().addAll(gen.getDefaultPopulators(world.getWorld()));
            }

            this.server.getPluginManager().callEvent(new WorldInitEvent(world.getWorld()));

            world.tracker = new EntityTracker(this, dimension);
            world.addIWorldAccess(new WorldManager(this, world));
            world.spawnMonsters = this.propertyManager.getBoolean("spawn-monsters", true) ? 1 : 0;
            world.setSpawnFlags(this.propertyManager.getBoolean("spawn-monsters", true), this.spawnAnimals);
            this.worlds.add(world);
            EventBus.global().publish(new net.minecraft.server.event.events.WorldLoadEvent(world));
            this.serverConfigurationManager.setPlayerFileData(this.worlds.toArray(new WorldServer[0]));
        }
        // CraftBukkit end

        short short1 = 196;
        long k = System.currentTimeMillis();

        // CraftBukkit start
        for (int l = 0; l < this.worlds.size(); ++l) {
            // if (l == 0 || this.propertyManager.getBoolean("allow-nether", true)) {
            WorldServer worldserver = this.worlds.get(l);
            log.info("Preparing start region for level " + l + " (Seed: " + worldserver.getSeed() + ")");
            if (worldserver.getWorld().getKeepSpawnInMemory()) {
                // CraftBukkit end
                ChunkCoordinates chunkcoordinates = worldserver.getSpawn();

                for (int i1 = -short1; i1 <= short1 && this.isRunning; i1 += 16) {
                    for (int j1 = -short1; j1 <= short1 && this.isRunning; j1 += 16) {
                        long k1 = System.currentTimeMillis();

                        if (k1 < k) {
                            k = k1;
                        }

                        if (k1 > k + 1000L) {
                            int l1 = (short1 * 2 + 1) * (short1 * 2 + 1);
                            int i2 = (i1 + short1) * (short1 * 2 + 1) + j1 + 1;

                            this.a("Preparing spawn area", i2 * 100 / l1);
                            k = k1;
                        }

                        worldserver.chunkProviderServer.getChunkAt(chunkcoordinates.x + i1 >> 4, chunkcoordinates.z + j1 >> 4);

                        while (worldserver.doLighting() && this.isRunning) {
                            ;
                        }
                    }
                }
            } // CraftBukkit
        }

        // CraftBukkit start
        for (World world : this.worlds) {
            this.server.getPluginManager().callEvent(new WorldLoadEvent(world.getWorld()));
        }
        // CraftBukkit end
        
        // Initialize async threading systems
        ThreadingManager.getInstance().initialize(this);

        this.setStartupReadinessStatus(StartupReadinessStatus.WARMING_UP);
        StartupWarmupCoordinator warmupCoordinator = new StartupWarmupCoordinator(this);
        warmupCoordinator.warmupWorlds(this.worlds);

        this.e();
    }

    private void a(String s, int i) {
        this.i = s;
        this.j = i;
        log.info(s + ": " + i + "%");
    }

    private void e() {
        this.i = null;
        this.j = 0;

        this.server.enablePlugins(PluginLoadOrder.POSTWORLD); // CraftBukkit
    }

    void saveChunks() { // CraftBukkit - private -> default
        log.info("Saving chunks");

        // CraftBukkit start
        int worldsSaved = 0;
        for (int i = 0; i < this.worlds.size(); ++i) {
            WorldServer worldserver = this.worlds.get(i);

            try {
                // MCOSE: Force-enable saving for each world during shutdown save,
                // just like save-all does. If save-off was active, the world would
                // silently skip saving all chunks (including signs, tile entities, etc).
                boolean wasSaveDisabled = worldserver.canSave;
                worldserver.canSave = false; // false = saving enabled

                worldserver.save(true, (IProgressUpdate) null);
                worldserver.saveLevel();

                worldserver.canSave = wasSaveDisabled; // restore original state
                ++worldsSaved;
                log.info("Saved world " + (i + 1) + "/" + this.worlds.size()
                    + " '" + worldserver.worldData.name + "' (tile entities, chunks, WAL flushed)");

                WorldSaveEvent event = new WorldSaveEvent(worldserver.getWorld());
                this.server.getPluginManager().callEvent(event);
            } catch (Exception e) {
                log.severe("[MCOSE] Failed to save world " + (i + 1) + "/"
                    + this.worlds.size() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }

        // Always save players on shutdown regardless of save-off state
        try {
            this.serverConfigurationManager.savePlayers();
        } catch (Exception e) {
            log.severe("[MCOSE] Failed to save players: " + e.getMessage());
            e.printStackTrace();
        }

        log.info("Chunk saving complete (" + worldsSaved + "/" + this.worlds.size() + " worlds saved)");
        // CraftBukkit end
    }

    private volatile boolean hasStopped = false;

    public void stop() { // CraftBukkit - private -> public
        // MCOSE: Guard against double-invocation (shutdown hook + finally block)
        synchronized (this) {
            if (this.hasStopped) {
                return;
            }
            this.hasStopped = true;
        }

        log.info("Stopping server");
        
        // UberBukkit - Save server-wide statistics on shutdown
        try {
            ServerStatistics.getInstance().shutdown();
        } catch (Exception e) {
            log.warning("[ServerStats] Error saving server statistics: " + e.getMessage());
        }
        
        // Close the network socket first to release the port
        if (this.networkListenThread != null) {
            this.networkListenThread.closeSocket();
        }

        //Project Poseidon Start

        // This is done before disablePlugins() to ensure the watchdog doesn't detect plugins disabling as a server hang
        try {
            Poseidon.getServer().shutdownServer();
        } catch (Exception e) {
            log.warning("[MCOSE] Poseidon shutdown error (non-fatal): " + e.getMessage());
        }

        //Project Poseidon End

        // CraftBukkit start
        if (this.server != null) {
            this.server.disablePlugins();
        }
        // CraftBukkit end

        if (this.serverConfigurationManager != null) {
            this.serverConfigurationManager.savePlayers();
        }

        for (int wi = 0; wi < this.worlds.size(); ++wi) {
            WorldServer world = this.worlds.get(wi);
            EventBus.global().publish(new net.minecraft.server.event.events.WorldUnloadEvent(world));
        }

        // Shutdown chunk compression workers
        org.bukkit.craftbukkit.ChunkCompressionThread.stopThread();
        this.stopCommunicationDispatcher();
        this.stopVoiceChatServer();

        // Shutdown async threading systems
        ThreadingManager.getInstance().shutdown();

        // CraftBukkit start - multiworld is handled in saveChunks() already.
        if (!this.worlds.isEmpty() && this.worlds.get(0) != null) {
            this.saveChunks();
        }
        // CraftBukkit end

        // MCOSE: Final safety-net flush — ensure all region files are closed and
        // WAL entries are synced to disk, even if saveLevel() failed or was skipped
        // for any reason during saveChunks().
        try {
            RegionFileCache.a();
            log.info("Region file cache flushed successfully");
        } catch (Exception e) {
            log.severe("[MCOSE] Failed to flush region file cache: " + e.getMessage());
            e.printStackTrace();
        }

        // Poseidon Start
        // UberBukkit: Performance statistics not available; stubbed to empty
        Map<String, Object> listenerStatistics = new java.util.HashMap<String, Object>();

        // Only get the Listener Statistics if the Poseidon Server is not null. Prevents null pointer exceptions.
        // if (Poseidon.getServer() != null && Poseidon.getServer().getConfig().getConfigBoolean("settings.performance-monitoring.listener-reporting.print-statistics-on-shutdown.enabled")) {
        //     listenerStatistics = Poseidon.getServer().getSortedListenerPerformance();
        // }

        // Check if the statistics map is not empty
        if (listenerStatistics != null && !listenerStatistics.isEmpty()) {
            log.info("[Poseidon] Listener statistics from this session:");

            // Iterate over each listener and log their statistics
            for (Map.Entry<String, Object> entry : listenerStatistics.entrySet()) {
                String listener = entry.getKey();
                Object stats = entry.getValue();

                /* if (stats.getMaxExecutionTime() == 0) {
                    continue;
                } */
            }
        }


        // Check if the statistics map is not empty

        Map<String, Object> taskStatistics = new java.util.HashMap<String, Object>();

        // Only get the Task Statistics if the Poseidon Server is not null. Prevents null pointer exceptions.
        // if (Poseidon.getServer() != null && Poseidon.getServer().getConfig().getConfigBoolean("settings.performance-monitoring.listener-reporting.print-statistics-on-shutdown.enabled")) {
        //     taskStatistics = Poseidon.getServer().getSortedTaskPerformance();
        // }

        if (taskStatistics != null && !taskStatistics.isEmpty()) {
            log.info("[Poseidon] Synchronous task statistics from this session:");

            // Iterate over each task and log their statistics
            for (Map.Entry<String, Object> entry : taskStatistics.entrySet()) {
                String task = entry.getKey();
                Object stats = entry.getValue();

                /* if (stats.getMaxExecutionTime() == 0) {
                    continue;
                } */
            }
        }
        // Poseidon End
        
        // Reset singletons to allow restart in GUI mode
        if (guiMode) {
            org.bukkit.Bukkit.resetServer();
            com.legacyminecraft.poseidon.Poseidon.resetServer();
        }
    }

    public void a() {
        this.isRunning = false;
    }

    public void run() {
        try {
            if (this.init()) {
                long i = System.currentTimeMillis();

                for (long j = 0L; this.isRunning; Thread.sleep(1L)) {
                    if (modLoaderSupport) {
                        try {
                            Class<?> ml = Class.forName("net.minecraft.server.ModLoader");
                            java.lang.reflect.Method m = ml.getMethod("OnTick", MinecraftServer.class);
                            m.invoke(null, this);
                        } catch (Throwable ignore) {}
                    }

                    long k = System.currentTimeMillis();
                    long l = k - i;

                    if (l > 2000L) {
                        log.warning("Can\'t keep up! Did the system time change, or is the server overloaded?");
                        l = 2000L;
                    }

                    if (l < 0L) {
                        log.warning("Time ran backwards! Did the system time change?");
                        l = 0L;
                    }

                    j += l;
                    i = k;
                    if (this.tickCatchupEnabled && j > this.maxTickCatchupBacklogMs) {
                        long droppedMs = j - this.maxTickCatchupBacklogMs;
                        j = this.maxTickCatchupBacklogMs;
                        long nowMs = System.currentTimeMillis();
                        if (nowMs - this.lastTickCatchupDropWarningMs >= this.tickCatchupWarnIntervalMs) {
                            log.warning("Tick catch-up backlog exceeded " + this.maxTickCatchupBacklogMs + "ms; dropped " + droppedMs + "ms to preserve stable simulation timing.");
                            this.lastTickCatchupDropWarningMs = nowMs;
                        }
                    }
                    if (this.worlds.get(0).everyoneDeeplySleeping()) { // CraftBukkit
                        this.h();
                        j = 0L;
                    } else {
                        long tickStepMs = this.tickIntervalMs;
                        if (tickStepMs < 1L) {
                            tickStepMs = 1L;
                        }
                        while (j > tickStepMs) {
                            MinecraftServer.currentTick = (int) (System.currentTimeMillis() / tickStepMs); // CraftBukkit
                            getWatchdog().tickUpdate(); // Project Poseidon
                            j -= tickStepMs;
                            this.h();
                            tickStepMs = Math.max(1L, this.tickIntervalMs);
                        }
                    }
                }
            } else {
                this.logStartupFailureContext("Initialization returned false", null);
                this.isRunning = false;
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            log.log(Level.SEVERE, "Unexpected exception", throwable);
            this.logStartupFailureContext("Unexpected exception in server main loop", throwable);

            while (this.isRunning) {
                this.b();

                try {
                    Thread.sleep(10L);
                } catch (InterruptedException interruptedexception1) {
                    interruptedexception1.printStackTrace();
                }
            }
        } finally {
            try {
                this.stop();
                this.isStopped = true;
            } catch (Throwable throwable1) {
                throwable1.printStackTrace();
            } finally {
                // Don't exit if running in GUI mode - let the GUI stay open
                if (!guiMode) {
                    System.exit(0);
                }
            }
        }
    }

    //Project Poseidon Start - Tick Update
    private final LinkedList<Double> tpsRecords = new LinkedList<>();
    private long lastTick = System.currentTimeMillis();
    private int tickCount = 0;

    public LinkedList<Double> getTpsRecords() {
        return tpsRecords;
    }
    //Project Poseidon End - Tick Update

    private void h() {
        long tickStartNanos = System.nanoTime();
        ServerProfiler profiler = ServerProfiler.getInstance();
        profiler.startSection("tick");

        ArrayList arraylist = new ArrayList();
        Iterator iterator = trackerList.keySet().iterator();

        while (iterator.hasNext()) {
            String s = (String) iterator.next();
            int i = ((Integer) trackerList.get(s)).intValue();

            if (i > 0) {
                trackerList.put(s, Integer.valueOf(i - 1));
            } else {
                arraylist.add(s);
            }
        }

        int j;

        for (j = 0; j < arraylist.size(); ++j) {
            trackerList.remove(arraylist.get(j));
        }

        AxisAlignedBB.a();
        Vec3D.a();
        ++this.ticks;

        profiler.startSection("schedulerHeartbeat");
        CraftScheduler scheduler = (CraftScheduler) this.server.getScheduler();
        scheduler.mainThreadHeartbeat(this.ticks); // CraftBukkit
        profiler.recordSchedulerSample(
            scheduler.getLastHeartbeatMovedToSynced(),
            scheduler.getLastHeartbeatExecuted(),
            scheduler.getLastHeartbeatLeftover(),
            scheduler.getLastHeartbeatRuntimeNanos() / 1_000_000.0D
        );
        profiler.endSection();

        //Project Poseidon Start - Tick Update
        long currentTime = System.currentTimeMillis();
        tickCount++;

        //Check if a second has passed
        if (currentTime - lastTick >= 1000) {
            double tps = tickCount / ((currentTime - lastTick) / 1000.0);
            tpsRecords.addFirst(tps);
            if (tpsRecords.size() > 900) { //Don't keep more than 15 minutes of data
                tpsRecords.removeLast();
            }

            tickCount = 0;
            lastTick = currentTime;
        }

        //Project Poseidon End - Tick Update

        profiler.startSection("networkListenTick/preWorld");
        this.networkListenThread.a();
        profiler.endSection();

        for (j = 0; j < this.worlds.size(); ++j) { // CraftBukkit
            // if (j == 0 || this.propertyManager.getBoolean("allow-nether", true)) { // CraftBukkit
            WorldServer worldserver = this.worlds.get(j); // CraftBukkit

            if (this.ticks % 20 == 0) {
                // CraftBukkit start - only send timeupdates to the people in that world
                for (int i = 0; i < worldserver.players.size(); ++i) { // Project Poseidon: serverConfigurationManager -> worldserver.players
                    EntityPlayer entityPlayer = (EntityPlayer) worldserver.players.get(i);
                    if (entityPlayer != null) {
                        entityPlayer.netServerHandler.sendPacket(new Packet4UpdateTime(entityPlayer.getPlayerTime())); // Add support for per player time

                    }
                }
                // CraftBukkit end
            }

            profiler.startSection("world" + j + "/doTick");
            worldserver.doTick();
            profiler.endSection();

            profiler.startSection("world" + j + "/lighting");
            while (worldserver.doLighting()) {
                ;
            }
            profiler.endSection();

            profiler.startSection("world" + j + "/cleanUp");
            worldserver.cleanUp();
            profiler.endSection();
        }
        // } // CraftBukkit

        profiler.startSection("networkListenTick/postWorld");
        this.networkListenThread.a();
        profiler.endSection();

        profiler.startSection("playerManagerFlush");
        this.serverConfigurationManager.b();
        profiler.endSection();

        // CraftBukkit start
        profiler.startSection("entityTracking");
        String aggregateTrackingState = EntityTracker.TrackingPressureState.NORMAL.name();
        int entityTrackingSkippedNear = 0;
        int entityTrackingSkippedMid = 0;
        int entityTrackingSkippedFar = 0;
        long entityTrackingLastTransitionMillis = 0L;
        for (j = 0; j < this.worlds.size(); ++j) {
            WorldServer world = (WorldServer) this.worlds.get(j);
            if (world == null || world.tracker == null) {
                continue;
            }

            world.tracker.updatePlayers();
            EntityTracker.TrackingPressureState worldTrackingState = world.tracker.getTrackingPressureState();
            if (worldTrackingState == EntityTracker.TrackingPressureState.PRESSURE) {
                aggregateTrackingState = EntityTracker.TrackingPressureState.PRESSURE.name();
            } else if (worldTrackingState == EntityTracker.TrackingPressureState.RECOVERY
                && EntityTracker.TrackingPressureState.NORMAL.name().equals(aggregateTrackingState)) {
                aggregateTrackingState = EntityTracker.TrackingPressureState.RECOVERY.name();
            }

            entityTrackingSkippedNear += world.tracker.consumeSkippedNearCount();
            entityTrackingSkippedMid += world.tracker.consumeSkippedMidCount();
            entityTrackingSkippedFar += world.tracker.consumeSkippedFarCount();
            long worldTransitionMillis = world.tracker.getTrackingStateLastTransitionMillis();
            if (worldTransitionMillis > entityTrackingLastTransitionMillis) {
                entityTrackingLastTransitionMillis = worldTransitionMillis;
            }
        }
        profiler.endSection();
        profiler.recordEntityTrackingSample(
            aggregateTrackingState,
            entityTrackingSkippedNear,
            entityTrackingSkippedMid,
            entityTrackingSkippedFar,
            entityTrackingLastTransitionMillis
        );
        // CraftBukkit end

        for (j = 0; j < this.r.size(); ++j) {
            ((IUpdatePlayerListBox) this.r.get(j)).a();
        }

        profiler.startSection("consoleCommandDispatch");
        try {
            this.b();
        } catch (Exception exception) {
            log.log(Level.WARNING, "Unexpected exception while parsing console command", exception);
        } finally {
            profiler.endSection();
        }

        // Process async threading results
        profiler.startSection("threadingProcessTick");
        ThreadingManager.getInstance().processTick();
        profiler.endSection();

        int commandQueueDepth = this.s.size();
        int inboundQueueTotal = 0;
        int outboundHighQueueTotal = 0;
        int outboundLowQueueTotal = 0;
        int outboundQueuedBytesTotal = 0;
        int pendingLogins = this.networkListenThread != null ? this.networkListenThread.getPendingLoginCount() : 0;
        int activeHandlers = this.networkListenThread != null ? this.networkListenThread.getActiveHandlerCount() : 0;

        if (this.serverConfigurationManager != null) {
            for (int i = 0; i < this.serverConfigurationManager.players.size(); i++) {
                EntityPlayer player = (EntityPlayer) this.serverConfigurationManager.players.get(i);
                if (player == null || player.netServerHandler == null || player.netServerHandler.networkManager == null) {
                    continue;
                }
                NetworkManager networkManager = player.netServerHandler.networkManager;
                int inboundDepth = networkManager.getInboundQueueSize();
                int outboundHigh = networkManager.getHighPriorityQueueSize();
                int outboundLow = networkManager.getLowPriorityQueueSize();
                int queuedBytes = networkManager.getQueuedBytes();

                inboundQueueTotal += inboundDepth;
                outboundHighQueueTotal += outboundHigh;
                outboundLowQueueTotal += outboundLow;
                outboundQueuedBytesTotal += queuedBytes;

                profiler.recordPlayerQueueSample(
                    player.name,
                    player.netServerHandler.getQueuedPacketCount(),
                    inboundDepth,
                    ChunkCompressionThread.getPlayerQueueSize(player),
                    player.netServerHandler.b()
                );
            }
        }

        profiler.recordQueueSample(
            commandQueueDepth,
            inboundQueueTotal,
            outboundHighQueueTotal,
            outboundLowQueueTotal,
            outboundQueuedBytesTotal,
            pendingLogins,
            activeHandlers,
            ChunkCompressionThread.getTotalQueueSize(),
            ChunkCompressionThread.getTotalQueueCapacity(),
            ChunkCompressionThread.getNetChunkZstdPacketsTotal(),
            ChunkCompressionThread.getNetChunkZlibPacketsTotal(),
            ChunkCompressionThread.getNetChunkZstdFallbackTotal(),
            ChunkCompressionThread.getNetChunkCompressionFailuresTotal(),
            ChunkCompressionThread.getNetChunkZstdCompressNanosTotal(),
            ChunkCompressionThread.getNetChunkZlibCompressNanosTotal(),
            ChunkBuffer.getRegionWriteZstdTotal(),
            ChunkBuffer.getRegionWriteZlibTotal(),
            ChunkBuffer.getRegionWriteZstdFallbackTotal(),
            ChunkBuffer.getRegionWriteZstdNanosTotal(),
            ChunkBuffer.getRegionWriteZlibNanosTotal(),
            this.communicationDispatcher != null ? this.communicationDispatcher.getCurrentChatQueueDepth() : 0,
            this.communicationDispatcher != null ? this.communicationDispatcher.getCurrentChatQueueCapacity() : 0,
            this.communicationDispatcher != null ? this.communicationDispatcher.getParallelChatQueuedTotal() : 0L,
            this.communicationDispatcher != null ? this.communicationDispatcher.getParallelChatSentTotal() : 0L,
            this.communicationDispatcher != null ? this.communicationDispatcher.getParallelChatDroppedOverflowTotal() : 0L,
            this.communicationDispatcher != null ? this.communicationDispatcher.getParallelChatDroppedRateTotal() : 0L,
            this.communicationDispatcher != null ? this.communicationDispatcher.getParallelChatQueueWaitNanosTotal() : 0L,
            this.communicationDispatcher != null ? this.communicationDispatcher.getParallelChatQueueWaitSamplesTotal() : 0L,
            this.communicationDispatcher != null ? this.communicationDispatcher.consumeParallelChatQueueWaitMaxNanos() : 0L,
            NetServerHandler.getParallelVoiceConsumedTotal(),
            NetServerHandler.getParallelVoiceDroppedRateTotal(),
            NetServerHandler.getParallelVoiceDroppedInvalidTotal(),
            NetServerHandler.getParallelVoiceDroppedOverflowTotal(),
            NetServerHandler.getParallelVoiceQueueWaitNanosTotal(),
            NetServerHandler.getParallelVoiceQueueWaitSamplesTotal(),
            NetServerHandler.consumeParallelVoiceQueueWaitMaxNanos()
        );

        int playerCount = this.serverConfigurationManager != null ? this.serverConfigurationManager.players.size() : 0;
        double tickDurationMs = (System.nanoTime() - tickStartNanos) / 1_000_000.0D;
        profiler.recordTickSample(tickDurationMs, commandQueueDepth, this.worlds.size(), playerCount);
        maybeLogVoiceDebugSummary(System.currentTimeMillis());

        profiler.endSection(); // End "tick" section
    }

    public void issueCommand(String s, ICommandListener icommandlistener) {
        this.s.add(new ServerCommand(s, icommandlistener));
    }

    public void b() {
        while (true) {
            ServerCommand servercommand;
            synchronized (this.s) {
                if (this.s.size() == 0) {
                    break;
                }
                servercommand = (ServerCommand) this.s.remove(0);
            }
            long queueWaitMs = Math.max(0L, System.currentTimeMillis() - servercommand.enqueueTimeMillis);

            // CraftBukkit start - ServerCommand for preprocessing
            ServerCommandEvent event = new ServerCommandEvent(this.console, servercommand.command);
            this.server.getPluginManager().callEvent(event);
            servercommand = new ServerCommand(event.getCommand(), servercommand.b, servercommand.enqueueTimeMillis);
            // CraftBukkit end

            // this.consoleCommandHandler.handle(servercommand); // CraftBukkit - Removed its now called in server.dispatchCommand
            long commandStart = System.nanoTime();
            this.server.dispatchCommand(this.console, servercommand); // CraftBukkit
            double commandExecMs = (System.nanoTime() - commandStart) / 1_000_000.0D;
            int remainingCommands;
            synchronized (this.s) {
                remainingCommands = this.s.size();
            }
            ServerProfiler.getInstance().recordCommandLatency(
                extractCommandRoot(servercommand.command),
                queueWaitMs,
                commandExecMs,
                remainingCommands
            );
        }
    }

    private String extractCommandRoot(String command) {
        if (command == null) {
            return "unknown";
        }
        String trimmed = command.trim();
        if (trimmed.length() == 0) {
            return "unknown";
        }
        if (trimmed.charAt(0) == '/') {
            trimmed = trimmed.substring(1);
        }
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx > 0) {
            return trimmed.substring(0, spaceIdx).toLowerCase();
        }
        return trimmed.toLowerCase();
    }

    public void a(IUpdatePlayerListBox iupdateplayerlistbox) {
        this.r.add(iupdateplayerlistbox);
    }

    public static void main(final OptionSet options) { // CraftBukkit - replaces main(String args[])
        StatisticList.a();

        try {
            MinecraftServer minecraftserver = new MinecraftServer(options); // CraftBukkit - pass in the options

            // CraftBukkit - remove gui

            (new ThreadServerApplication("Server thread", minecraftserver)).start();
        } catch (Exception exception) {
            log.log(Level.SEVERE, "Failed to start the minecraft server", exception);
            logStaticStartupFailureContext("Failed to construct MinecraftServer instance", exception, options);
        }
    }

    public File a(String s) {
        return new File(s);
    }

    public void sendMessage(String s) {
        log.info(s);
    }

    public void c(String s) {
        log.warning(s);
    }

    public String getName() {
        return "CONSOLE";
    }

    public WorldServer getWorldServer(int i) {
        // CraftBukkit start
        for (WorldServer world : this.worlds) {
            if (world.dimension == i) {
                return world;
            }
        }

        return this.worlds.get(0);
        // CraftBukkit end
    }

    public EntityTracker getTracker(int i) {
        return this.getWorldServer(i).tracker; // CraftBukkit
    }

    public boolean isStartupReady() {
        return this.startupReadinessStatus == StartupReadinessStatus.READY;
    }

    public StartupReadinessStatus getStartupReadinessStatus() {
        return this.startupReadinessStatus;
    }

    private void setStartupReadinessStatus(StartupReadinessStatus status) {
        if (status != null) {
            this.startupReadinessStatus = status;
        }
    }

    public static boolean isRunning(MinecraftServer minecraftserver) {
        return minecraftserver.isRunning;
    }

    public WatchDogThread getWatchdog() {
        return Poseidon.getServer().getWatchDogThread();
    }

    public float getDebugTickRateTps() {
        return this.debugTickRateTps;
    }

    public long getTickIntervalMs() {
        return this.tickIntervalMs;
    }

    public synchronized float setDebugTickRateTps(float tickRateTps) {
        float clampedTickRate = Math.max(1.0F, Math.min(1000.0F, tickRateTps));
        long interval = Math.round(1000.0D / clampedTickRate);
        if (interval < 1L) {
            interval = 1L;
        }
        this.tickIntervalMs = interval;
        this.debugTickRateTps = 1000.0F / (float) interval;
        return this.debugTickRateTps;
    }

    public synchronized void resetDebugTickRate() {
        this.tickIntervalMs = 50L;
        this.debugTickRateTps = 20.0F;
    }

    public boolean isVoiceChatEnabled() {
        return this.voiceChatEnabled;
    }

    public double getVoiceChatBroadcastRadius() {
        return this.voiceChatBroadcastRadius;
    }
    
    public int getVoiceRateMaxPacketsPerSec() {
        return this.voiceRateMaxPacketsPerSec;
    }

    public int getVoiceRateMaxBytesPerSec() {
        return this.voiceRateMaxBytesPerSec;
    }

    public int getVoiceRateBurstSeconds() {
        return this.voiceRateBurstSeconds;
    }

    public boolean isVoiceUdpHealthy() {
        return this.voiceUdpHealthy;
    }

    public String getVoiceUdpState() {
        return this.voiceUdpState;
    }

    public int getVoiceChatPort() {
        return this.voiceChatPort;
    }
    
    public VoiceChatUDPServer getVoiceChatUDPServer() {
        return this.voiceChatUDPServer;
    }

    public CommunicationDispatcher getCommunicationDispatcher() {
        return this.communicationDispatcher;
    }
    
    public void startVoiceChatServer() {
        if (!this.voiceChatEnabled) {
            this.voiceUdpHealthy = false;
            this.voiceUdpState = "disabled";
            return;
        }
        if (this.voiceChatUDPServer != null) {
            return;
        }
        try {
            this.voiceChatUDPServer = new VoiceChatUDPServer(this, this.voiceChatPort);
            this.voiceChatUDPServer.start();
            this.voiceUdpHealthy = true;
            this.voiceUdpState = "bound:" + this.voiceChatPort;
            log.info("Voice chat UDP server started on port " + this.voiceChatPort);
        } catch (Exception e) {
            this.voiceChatUDPServer = null;
            this.voiceUdpHealthy = false;
            this.voiceUdpState = "bind-failed:" + e.getClass().getSimpleName();
            log.log(Level.SEVERE,
                "[VoiceChat] UDP bind failed on port " + this.voiceChatPort
                    + ". Voice is degraded to TCP failover-only transport until UDP recovers.",
                e
            );
            if (this.voiceRequireUdpBind) {
                throw new IllegalStateException(
                    "voice-require-udp-bind=true and UDP bind failed on port " + this.voiceChatPort,
                    e
                );
            }
        }
    }
    
    public void stopVoiceChatServer() {
        if (this.voiceChatUDPServer != null) {
            this.voiceChatUDPServer.stop();
            this.voiceChatUDPServer = null;
        }
        this.voiceUdpHealthy = false;
        this.voiceUdpState = this.voiceChatEnabled ? "stopped" : "disabled";
    }

    public void startCommunicationDispatcher() {
        if (this.communicationDispatcher == null) {
            this.communicationDispatcher = new CommunicationDispatcher(this);
        }
        this.communicationDispatcher.start();
    }

    public void stopCommunicationDispatcher() {
        if (this.communicationDispatcher != null) {
            this.communicationDispatcher.stop();
        }
    }

    private boolean isVoiceDebugEnabled() {
        return log.isLoggable(Level.FINE) || (this.options != null && this.options.has("debug-config"));
    }

    private void maybeLogVoiceDebugSummary(long nowMs) {
        if (!isVoiceDebugEnabled()) {
            return;
        }
        if (nowMs - this.lastVoiceDebugSummaryAt < VOICE_DEBUG_SUMMARY_INTERVAL_MS) {
            return;
        }
        this.lastVoiceDebugSummaryAt = nowMs;

        NetServerHandler.VoiceTcpWindowStats tcpStats = NetServerHandler.consumeVoiceTcpWindowStats();
        VoiceChatUDPServer.VoiceUdpWindowStats udpStats = this.voiceChatUDPServer != null
            ? this.voiceChatUDPServer.consumeWindowStats()
            : VoiceChatUDPServer.VoiceUdpWindowStats.empty();
        boolean degradedTransport = !this.voiceUdpHealthy;
        boolean hasDropSignals = udpStats.getTotalDrops() > 0L
            || tcpStats.getTotalDrops() > 0L
            || udpStats.queueOverflowDrops > 0L
            || tcpStats.queueOverflowDrops > 0L;
        if (!degradedTransport && !hasDropSignals) {
            return;
        }
        int activeUdpClients = this.voiceChatUDPServer != null ? this.voiceChatUDPServer.getValidatedClientCount() : 0;
        String udpTransportState = this.voiceUdpHealthy ? "up" : "degraded(" + this.voiceUdpState + ")";
        String tcpDropReasonSummary = formatVoiceDropReasons(tcpStats.dropReasons);
        String udpDropReasonSummary = formatVoiceDropReasons(udpStats.dropReasons);
        String summaryBody = "udpState=" + udpTransportState
            + " activeUdpClients=" + activeUdpClients
            + " tcpFallbackUsage=" + tcpStats.attempts
            + " attempts(udp/tcp)=" + udpStats.attempts + "/" + tcpStats.attempts
            + " drops(udp/tcp)=" + udpStats.getTotalDrops() + "/" + tcpStats.getTotalDrops()
            + " queueOverflow(udp/tcp)=" + udpStats.queueOverflowDrops + "/" + tcpStats.queueOverflowDrops
            + " tcpQueueWaitP95Ms=" + formatOneDecimal(tcpStats.queueWaitP95Ms)
            + " dropsByReason udp{" + udpDropReasonSummary + "} tcp{" + tcpDropReasonSummary + "}";

        if (summaryBody.equals(this.lastVoiceDebugSummarySignature)) {
            return;
        }
        this.lastVoiceDebugSummarySignature = summaryBody;

        log.info("[VoiceChat][Summary] " + summaryBody);
    }

    private static String formatVoiceDropReasons(Map<String, Long> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "none";
        }
        List<Map.Entry<String, Long>> entries = new ArrayList<Map.Entry<String, Long>>(reasons.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Long>>() {
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                long aValue = a != null && a.getValue() != null ? a.getValue().longValue() : 0L;
                long bValue = b != null && b.getValue() != null ? b.getValue().longValue() : 0L;
                if (aValue == bValue) {
                    String aKey = a != null && a.getKey() != null ? a.getKey() : "";
                    String bKey = b != null && b.getKey() != null ? b.getKey() : "";
                    return aKey.compareTo(bKey);
                }
                return aValue < bValue ? 1 : -1;
            }
        });
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); ++i) {
            Map.Entry<String, Long> entry = entries.get(i);
            if (entry == null || entry.getValue() == null || entry.getValue().longValue() <= 0L) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue().longValue());
        }
        return sb.length() == 0 ? "none" : sb.toString();
    }

    private static String formatOneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private void logStartupFailureContext(String reason, Throwable cause) {
        StringBuilder context = new StringBuilder();
        context.append("[StartupFailure] reason=").append(reason == null ? "unknown" : reason);
        context.append(" | cwd=").append(new File(".").getAbsolutePath());
        context.append(" | java=").append(System.getProperty("java.version")).append(" (")
            .append(System.getProperty("java.vendor")).append(")");
        context.append(" | os=").append(System.getProperty("os.name")).append(" ")
            .append(System.getProperty("os.arch"));
        context.append(" | guiMode=").append(guiMode);
        context.append(" | tick=").append(this.ticks);

        if (this.propertyManager != null) {
            context.append(" | server-ip=").append(this.propertyManager.getString("server-ip", ""));
            context.append(" | server-port=").append(this.propertyManager.getInt("server-port", 25565));
            context.append(" | online-mode=").append(this.propertyManager.getBoolean("online-mode", true));
            context.append(" | level-name=").append(this.propertyManager.getString("level-name", "world"));
            context.append(" | level-type=").append(this.propertyManager.getString("level-type", "DEFAULT"));
            context.append(" | allow-nether=").append(this.propertyManager.getBoolean("allow-nether", true));
            context.append(" | voice-chat=").append(this.propertyManager.getBoolean("voice-chat", true));
            context.append(" | voice-port=").append(this.propertyManager.getInt("voice-chat-port", DEFAULT_VOICE_CHAT_PORT));
            context.append(" | voice-require-udp-bind=").append(this.propertyManager.getBoolean("voice-require-udp-bind", false));
        }

        log.severe(context.toString());
        if (cause != null) {
            Throwable root = cause;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            if (root != cause) {
                log.log(Level.SEVERE, "[StartupFailure] Root cause: " + root.toString(), root);
            }
        }
    }

    private static void logStaticStartupFailureContext(String reason, Throwable cause, OptionSet options) {
        StringBuilder context = new StringBuilder();
        context.append("[StartupFailure] reason=").append(reason == null ? "unknown" : reason);
        context.append(" | cwd=").append(new File(".").getAbsolutePath());
        context.append(" | java=").append(System.getProperty("java.version")).append(" (")
            .append(System.getProperty("java.vendor")).append(")");
        context.append(" | os=").append(System.getProperty("os.name")).append(" ")
            .append(System.getProperty("os.arch"));
        context.append(" | guiMode=").append(guiMode);

        if (options != null) {
            appendOption(context, options, "config");
            appendOption(context, options, "server-ip");
            appendOption(context, options, "server-port");
            appendOption(context, options, "level-name");
            appendOption(context, options, "online-mode");
            appendOption(context, options, "max-players");
        }

        log.severe(context.toString());
        if (cause != null) {
            log.log(Level.SEVERE, "[StartupFailure] Exception detail", cause);
        }
    }

    private static void appendOption(StringBuilder context, OptionSet options, String key) {
        if (context == null || options == null || key == null) {
            return;
        }
        try {
            if (!options.has(key)) {
                return;
            }
            Object value = options.valueOf(key);
            if (value != null) {
                context.append(" | ").append(key).append("=").append(String.valueOf(value));
            }
        } catch (Throwable ignored) {
        }
    }
}

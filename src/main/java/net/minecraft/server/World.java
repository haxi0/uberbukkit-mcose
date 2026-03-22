package net.minecraft.server;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.generator.ChunkGenerator;

import java.util.*;

import net.minecraft.server.event.EventBus;
import net.minecraft.server.event.events.EntitySpawnEvent;
import net.minecraft.server.registry.BlockCapabilityRegistryApi;
import uk.betacraft.uberbukkit.packet.Packet62Sound;

// CraftBukkit start
// CraftBukkit end

public class World implements IBlockAccess {

    public boolean a = false;
    private List C = new ArrayList();
    public List entityList = new ArrayList();
    private List D = new ArrayList();
    private TreeSet E = new TreeSet();
    private Set F = new HashSet();
    private Map scheduledTickChunkIndex = new HashMap();
    public List c = new ArrayList();
    private List G = new ArrayList();
    public List players = new ArrayList();
    public List e = new ArrayList();
    private long H = 16777215L;
    public int f = 0;
    protected int g = (new Random()).nextInt();
    protected final int h = 1013904223;
    protected float i;
    protected float j;
    protected float k;
    protected float l;
    protected int m = 0;
    public int n = 0;
    public boolean suppressPhysics = false;
    private long I = System.currentTimeMillis();
    protected int p = 40;
    public int spawnMonsters;
    public Random random = new Random();
    public boolean s = false;
    public WorldProvider worldProvider; // CraftBukkit - remove final
    protected List u = new ArrayList();
    public IChunkProvider chunkProvider; // CraftBukkit - protected -> public
    protected final IDataManager w;
    public WorldData worldData; // CraftBukkit - protected -> public
    public boolean isLoading;
    private boolean J;
    public WorldMapCollection worldMaps;
    private ArrayList K = new ArrayList();
    private boolean L;
    private int M = 0;
    public boolean allowMonsters = true; // CraftBukkit - private -> public
    public boolean allowAnimals = true; // CraftBukkit - private -> public
    static int A = 0;
    private Set P = new HashSet();
    private int Q;
    private List R;
    public boolean isStatic;
    public final Map<Explosion.CacheKey, Float> explosionDensityCache = new HashMap<>(); // Paper - Optimize explosions
    private int saveTickCounter = 0; // Decouple periodic saves from time-of-day gamerule
    private long blockTickTime = 0L; // Monotonic scheduled-tick clock independent of day/night time

    public WorldChunkManager getWorldChunkManager() {
        return this.worldProvider.b;
    }

    // CraftBukkit start
    private final CraftWorld world;
    public boolean pvpMode;
    public boolean keepSpawnInMemory = true;
    public ChunkGenerator generator;
    Chunk lastChunkAccessed;
    int lastXAccessed = Integer.MIN_VALUE;
    int lastZAccessed = Integer.MIN_VALUE;
    final Object chunkLock = new Object();
    private List<TileEntity> tileEntitiesToUnload;

    private boolean canSpawn(int x, int z) {
        if (this.generator != null) {
            return this.generator.canSpawn(this.getWorld(), x, z);
        } else {
            return this.worldProvider.canSpawn(x, z);
        }
    }

    public CraftWorld getWorld() {
        return this.world;
    }

    public CraftServer getServer() {
        return (CraftServer) Bukkit.getServer();
    }

    public void markForRemoval(TileEntity tileentity) {
        tileEntitiesToUnload.add(tileentity);
    }

    // CraftBukkit - changed signature
    public World(IDataManager idatamanager, String s, long i, WorldProvider worldprovider, ChunkGenerator gen, org.bukkit.World.Environment env) {
        this.generator = gen;
        this.world = new CraftWorld((WorldServer) this, gen, env);
        tileEntitiesToUnload = new ArrayList<TileEntity>();
        // CraftBukkit end

        this.Q = this.random.nextInt(12000);
        this.R = new ArrayList();
        this.isStatic = false;
        this.w = idatamanager;
        this.worldMaps = new WorldMapCollection(idatamanager);
        this.worldData = idatamanager.c(); // c() is loadWorldInfo()
        // Debug world constructor log removed

        this.s = this.worldData == null; // s is isNewWorld internally (field name)
        if (this.worldData == null) {
             this.worldData = new WorldData(i, s);
             try { this.worldMaps.resetIdCounts(); } catch (Throwable ignored) {} // Reset map idcounts for brand new worlds
        }

        if (worldprovider != null) {
            this.worldProvider = worldprovider;
        } else if (this.worldData != null && this.worldData.h() == -1) {
            this.worldProvider = WorldProvider.byDimension(-1);
        } else if (this.worldData != null && this.worldData.getTerrainType() == 3) {
            // Use WorldProviderSky for sky terrain type, even in overworld
            this.worldProvider = new WorldProviderSky();
            // Override the dimension to stay as overworld
            this.worldProvider.dimension = 0;
        } else {
            this.worldProvider = WorldProvider.byDimension(0);
        }

        boolean flag = this.s;

        if (this.worldData == null) {
            this.worldData = new WorldData(i, s);
            flag = true;
        } else {
            this.worldData.a(s);
        }
        this.blockTickTime = this.worldData.f();

        this.worldProvider.a(this);
        this.chunkProvider = this.b();
        if (flag) {
            try { this.worldMaps.resetIdCounts(); } catch (Throwable ignored) {} // New world via constructor flag
            this.c();
        } else if (this.worldData != null && this.worldData.getTerrainType() == 3 && 
                   this.worldData.c() == 0 && this.worldData.d() == 90 && this.worldData.e() == 0) {
            // If it's a sky world with the default spawn we just set in MinecraftServer, run spawn finding
            this.c();
        } else if (this.worldData != null && this.worldData.getTerrainType() == 6 &&
                this.worldData.c() == 0 && this.worldData.e() == 0 &&
                this.worldData.d() <= 1 && this.worldData.f() == 0L &&
                this.worldProvider != null && this.worldProvider.dimension == 0) {
            // Newly-created CLASSIC worlds can arrive here with prewritten WorldData and a default 0,0,0 spawn.
            // Force one-time classic spawn initialization so spawn is chosen near the center island.
            this.c();
        }

        this.g();
        this.x();

        this.getServer().addWorld(this.world); // CraftBukkit
    }

    protected IChunkProvider b() {
        IChunkLoader ichunkloader = this.w.a(this.worldProvider);

        return new ChunkProviderLoadOrGenerate(this, ichunkloader, this.worldProvider.getChunkProvider());
    }

    protected void c() { 
        // Debug world.c enter log removed
        this.isLoading = true;
        
        // Check if this is a sky world - either by provider type OR terrain type
        boolean isSkyWorld = (this.worldProvider instanceof WorldProviderSky) || 
                            (this.worldData != null && this.worldData.getTerrainType() == 3);
        
        if (isSkyWorld) {
            // Prefer center islands first, then expand within the server's pre-generated spawn radius.
            final int centerX = 0;
            final int centerZ = 0;
            final int minY = 48;
            final int maxY = 126;
            final int maxRadius = 196;
            final int step = 2;
            final int desiredIslandSupport = 9;

            int bestX = 0;
            int bestZ = 0;
            int bestY = -1;
            int bestSupport = -1;
            int bestDist = Integer.MAX_VALUE;

            for (int r = 0; r <= maxRadius; r += step) {
                int ringBestX = 0;
                int ringBestZ = 0;
                int ringBestY = -1;
                int ringBestSupport = -1;
                int ringBestDist = Integer.MAX_VALUE;

                for (int x = centerX - r; x <= centerX + r; x += step) {
                    int zTop = centerZ + r;
                    int zBottom = centerZ - r;

                    int yTop = this.findSkySpawnY(x, zTop, minY, maxY);
                    if (yTop > 0) {
                        int support = this.computeSkyIslandSupportScore(x, zTop, minY, maxY);
                        int dist = Math.abs(x - centerX) + Math.abs(zTop - centerZ);
                        if (support > ringBestSupport || (support == ringBestSupport && dist < ringBestDist)) {
                            ringBestX = x;
                            ringBestY = yTop;
                            ringBestZ = zTop;
                            ringBestSupport = support;
                            ringBestDist = dist;
                        }
                    }

                    if (zBottom != zTop) {
                        int yBottom = this.findSkySpawnY(x, zBottom, minY, maxY);
                        if (yBottom > 0) {
                            int support = this.computeSkyIslandSupportScore(x, zBottom, minY, maxY);
                            int dist = Math.abs(x - centerX) + Math.abs(zBottom - centerZ);
                            if (support > ringBestSupport || (support == ringBestSupport && dist < ringBestDist)) {
                                ringBestX = x;
                                ringBestY = yBottom;
                                ringBestZ = zBottom;
                                ringBestSupport = support;
                                ringBestDist = dist;
                            }
                        }
                    }
                }

                for (int z = centerZ - r + step; z <= centerZ + r - step; z += step) {
                    int xRight = centerX + r;
                    int xLeft = centerX - r;

                    int yRight = this.findSkySpawnY(xRight, z, minY, maxY);
                    if (yRight > 0) {
                        int support = this.computeSkyIslandSupportScore(xRight, z, minY, maxY);
                        int dist = Math.abs(xRight - centerX) + Math.abs(z - centerZ);
                        if (support > ringBestSupport || (support == ringBestSupport && dist < ringBestDist)) {
                            ringBestX = xRight;
                            ringBestY = yRight;
                            ringBestZ = z;
                            ringBestSupport = support;
                            ringBestDist = dist;
                        }
                    }

                    if (xLeft != xRight) {
                        int yLeft = this.findSkySpawnY(xLeft, z, minY, maxY);
                        if (yLeft > 0) {
                            int support = this.computeSkyIslandSupportScore(xLeft, z, minY, maxY);
                            int dist = Math.abs(xLeft - centerX) + Math.abs(z - centerZ);
                            if (support > ringBestSupport || (support == ringBestSupport && dist < ringBestDist)) {
                                ringBestX = xLeft;
                                ringBestY = yLeft;
                                ringBestZ = z;
                                ringBestSupport = support;
                                ringBestDist = dist;
                            }
                        }
                    }
                }

                if (ringBestY > 0) {
                    if (ringBestSupport > bestSupport || (ringBestSupport == bestSupport && ringBestDist < bestDist)) {
                        bestX = ringBestX;
                        bestY = ringBestY;
                        bestZ = ringBestZ;
                        bestSupport = ringBestSupport;
                        bestDist = ringBestDist;
                    }
                    if (ringBestSupport >= desiredIslandSupport) {
                        break;
                    }
                }
            }

            if (bestY <= 0) {
                // Emergency pass: broaden search so we still land on a real island before any air fallback.
                final int emergencyMaxRadius = 1024;
                final int emergencyStep = 8;
                for (int r = maxRadius + emergencyStep; r <= emergencyMaxRadius && bestY <= 0; r += emergencyStep) {
                    for (int x = centerX - r; x <= centerX + r && bestY <= 0; x += emergencyStep) {
                        int zTop = centerZ + r;
                        int zBottom = centerZ - r;

                        int yTop = this.findSkySpawnY(x, zTop, minY, maxY);
                        if (yTop > 0) {
                            bestX = x;
                            bestY = yTop;
                            bestZ = zTop;
                            bestSupport = this.computeSkyIslandSupportScore(x, zTop, minY, maxY);
                            break;
                        }

                        int yBottom = this.findSkySpawnY(x, zBottom, minY, maxY);
                        if (yBottom > 0) {
                            bestX = x;
                            bestY = yBottom;
                            bestZ = zBottom;
                            bestSupport = this.computeSkyIslandSupportScore(x, zBottom, minY, maxY);
                            break;
                        }
                    }
                }
            }

            if (bestY <= 0) {
                System.err.println("[ProjectPoseidon World.c] Sky World: no safe island spawn found. Falling back to 0,90,0.");
                bestX = 0;
                bestZ = 0;
                bestY = 90;
            }

            ChunkCoordinates safeSkySpawn = this.findSafeSpawnNear(bestX, bestZ, 96, false);
            this.worldData.setSpawn(safeSkySpawn.x, safeSkySpawn.y, safeSkySpawn.z);
            System.out.println("[ProjectPoseidon World.c] Sky world initial spawn finalized to: " + safeSkySpawn.x + "," + safeSkySpawn.y + "," + safeSkySpawn.z + " (support=" + bestSupport + ")");
            this.isLoading = false;
            return;
        }
        
        // Classic world: fixed-size 256x256 map with spawn biased to center and inland land quality.
        if (this.worldData != null && this.worldData.getTerrainType() == 6 && this.worldProvider.dimension == 0) {
            final int centerX = 128;
            final int centerZ = 128;
            final int seaLevel = 32;
            final int centerWindow = 5;       // roughly 10x10 around center
            final int targetInlandScore = 30; // 0..49 (higher = more inland)
            int bestX = centerX;
            int bestZ = centerZ;
            int bestY = -1;
            int bestScore = -1;
            int bestDist = Integer.MAX_VALUE;

            // 1) Evaluate all candidates in the center window and pick the most inland.
            for (int sx = centerX - centerWindow; sx <= centerX + centerWindow; ++sx) {
                for (int sz = centerZ - centerWindow; sz <= centerZ + centerWindow; ++sz) {
                    int sy = this.findClassicSpawnY(sx, sz, seaLevel);
                    if (sy <= 0) {
                        continue;
                    }

                    int score = this.computeClassicInlandScore(sx, sz, seaLevel);
                    int dist = Math.abs(sx - centerX) + Math.abs(sz - centerZ);
                    if (score > bestScore || (score == bestScore && dist < bestDist)) {
                        bestX = sx;
                        bestY = sy;
                        bestZ = sz;
                        bestScore = score;
                        bestDist = dist;
                    }
                }
            }

            // 2) If center is still shoreline/ocean edge, expand outward until we find a better inland point.
            if (bestScore < targetInlandScore) {
                for (int r = centerWindow + 1; r <= 96; ++r) {
                    int ringBestX = 0;
                    int ringBestZ = 0;
                    int ringBestY = -1;
                    int ringBestScore = -1;
                    int ringBestDist = Integer.MAX_VALUE;

                    for (int dx = -r; dx <= r; ++dx) {
                        for (int dz = -r; dz <= r; ++dz) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                                continue;
                            }

                            int sx = centerX + dx;
                            int sz = centerZ + dz;
                            int sy = this.findClassicSpawnY(sx, sz, seaLevel);
                            if (sy <= 0) {
                                continue;
                            }

                            int score = this.computeClassicInlandScore(sx, sz, seaLevel);
                            int dist = Math.abs(dx) + Math.abs(dz);
                            if (score > ringBestScore || (score == ringBestScore && dist < ringBestDist)) {
                                ringBestX = sx;
                                ringBestY = sy;
                                ringBestZ = sz;
                                ringBestScore = score;
                                ringBestDist = dist;
                            }
                        }
                    }

                    if (ringBestY > 0 && (ringBestScore > bestScore || (ringBestScore == bestScore && ringBestDist < bestDist))) {
                        bestX = ringBestX;
                        bestY = ringBestY;
                        bestZ = ringBestZ;
                        bestScore = ringBestScore;
                        bestDist = ringBestDist;
                    }

                    if (bestScore >= targetInlandScore) {
                        break;
                    }
                }
            }

            if (bestY <= 0) {
                int centerY = this.findClassicSpawnY(centerX, centerZ, seaLevel);
                bestY = centerY > 0 ? centerY : 36;
            }

            if (bestY < 1) {
                bestY = 1;
            } else if (bestY > 125) {
                bestY = 125;
            }

            ChunkCoordinates safeClassicSpawn = this.findSafeSpawnNear(bestX, bestZ, 48, false);
            this.worldData.setSpawn(safeClassicSpawn.x, safeClassicSpawn.y, safeClassicSpawn.z);
            this.isLoading = false;
            return;
        }
        
        // Safe spawn resolution for all remaining world types.
        int terrainType = this.worldData != null ? this.worldData.getTerrainType() : 0;
        boolean preferShorelineSpawn = terrainType == 0 || terrainType == 1; // DEFAULT/ALPHA washed-ashore style
        int preferredX = 0;
        int preferredZ = 0;
        int searchRadius = 256;

        if (this.generator != null) {
            Random randForGenerator = new Random(this.getSeed()); 
            Location spawn = this.generator.getFixedSpawnLocation(this.getWorld(), randForGenerator);

            if (spawn != null) {
                if (spawn.getWorld() != this.getWorld()) {
                    throw new IllegalStateException("Cannot set spawn point for " + this.worldData.name + " to be in another world (" + spawn.getWorld().getName() + ")");
                } else {
                    preferredX = spawn.getBlockX();
                    preferredZ = spawn.getBlockZ();
                    searchRadius = 128;
                }
            }
        }
        
        ChunkCoordinates safeSpawn = this.findSafeSpawnNear(preferredX, preferredZ, searchRadius, preferShorelineSpawn);
        this.worldData.setSpawn(safeSpawn.x, safeSpawn.y, safeSpawn.z);
        System.out.println("[ProjectPoseidon World.c] Initial spawn finalized to: " + safeSpawn.x + "," + safeSpawn.y + "," + safeSpawn.z);
        this.isLoading = false;
    }

    private int findClassicSpawnY(int x, int z, int seaLevel) {
        if (x < 0 || x > 255 || z < 0 || z > 255) {
            return -1;
        }

        int y = this.f(x, z); // top solid block + 1
        if (y <= seaLevel || y >= 126) {
            return -1;
        }

        int groundId = this.getTypeId(x, y - 1, z);
        if (groundId <= 0 || groundId >= Block.byId.length) {
            return -1;
        }

        Block ground = Block.byId[groundId];
        if (ground == null || !ground.material.isSolid() || ground.material.isLiquid()) {
            return -1;
        }

        // Spawn location must be dry and have headroom.
        if (this.getTypeId(x, y, z) != 0 || this.getTypeId(x, y + 1, z) != 0) {
            return -1;
        }

        return y;
    }

    private int findSkySpawnY(int x, int z, int minY, int maxY) {
        int y = this.e(x, z); // air block above top solid block
        if (y < minY || y > maxY) {
            return -1;
        }

        int groundId = this.getTypeId(x, y - 1, z);
        if (groundId <= 0 || groundId >= Block.byId.length) {
            return -1;
        }

        Block ground = Block.byId[groundId];
        if (ground == null || !ground.material.isSolid() || ground.material.isLiquid()) {
            return -1;
        }

        if (this.getTypeId(x, y, z) != 0 || this.getTypeId(x, y + 1, z) != 0) {
            return -1;
        }

        return y;
    }

    private int computeSkyIslandSupportScore(int x, int z, int minY, int maxY) {
        int radius = 2;
        int score = 0;

        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                if (this.findSkySpawnY(x + dx, z + dz, minY, maxY) > 0) {
                    ++score;
                }
            }
        }

        return score;
    }

    private int computeClassicInlandScore(int x, int z, int seaLevel) {
        int radius = 3;
        int score = 0;

        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                if (this.findClassicSpawnY(x + dx, z + dz, seaLevel) > 0) {
                    ++score;
                }
            }
        }

        return score;
    }

    private boolean isSafeSpawnFloorBlock(int blockId) {
        if (blockId <= 0 || blockId >= Block.byId.length) {
            return false;
        }

        if (blockId == Block.FIRE.id || blockId == Block.CACTUS.id || blockId == Block.LAVA.id || blockId == Block.STATIONARY_LAVA.id) {
            return false;
        }

        Block block = Block.byId[blockId];
        if (block == null || !block.material.isSolid() || block.material.isLiquid()) {
            return false;
        }

        return Block.o[blockId] || blockId == Block.SOUL_SAND.id;
    }

    private boolean isSafeSpawnSpaceBlock(int blockId) {
        if (blockId == 0) {
            return true;
        }

        if (blockId < 0 || blockId >= Block.byId.length) {
            return false;
        }

        if (blockId == Block.FIRE.id || blockId == Block.CACTUS.id || blockId == Block.WEB.id || blockId == Block.LAVA.id || blockId == Block.STATIONARY_LAVA.id) {
            return false;
        }

        Block block = Block.byId[blockId];
        if (block == null) {
            return true;
        }

        return !block.material.isSolid() && !block.material.isLiquid();
    }

    public boolean isSafePlayerSpawnAt(int x, int y, int z) {
        if (y <= 1 || y >= 126) {
            return false;
        }

        if (!this.isSafeSpawnFloorBlock(this.getTypeId(x, y - 1, z))) {
            return false;
        }

        if (!this.isSafeSpawnSpaceBlock(this.getTypeId(x, y, z))) {
            return false;
        }

        return this.isSafeSpawnSpaceBlock(this.getTypeId(x, y + 1, z));
    }

    private int findSafeSpawnYAtColumn(int x, int z) {
        int y = this.f(x, z);
        if (y <= 1 || y >= 126) {
            y = this.e(x, z);
        }

        if (y <= 1 || y >= 126) {
            return -1;
        }

        return this.isSafePlayerSpawnAt(x, y, z) ? y : -1;
    }

    private int computeSpawnSupportScore(int x, int z) {
        int score = 0;
        int radius = 2;

        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dz = -radius; dz <= radius; ++dz) {
                if (this.findSafeSpawnYAtColumn(x + dx, z + dz) > 0) {
                    ++score;
                }
            }
        }

        return score;
    }

    private int computeSpawnColumnScore(int x, int y, int z, boolean preferShorelineSpawn) {
        int score = 0;
        int groundId = this.getTypeId(x, y - 1, z);

        if (preferShorelineSpawn) {
            if (groundId == Block.SAND.id) {
                score += 700;
            } else if (groundId == Block.GRAVEL.id) {
                score += 220;
            } else {
                score -= 50;
            }
        }

        if (groundId == Block.GRASS.id) {
            score += 160;
        } else if (groundId == Block.DIRT.id) {
            score += 120;
        } else if (groundId == Block.STONE.id || groundId == Block.SANDSTONE.id) {
            score += 100;
        } else if (groundId == Block.NETHERRACK.id) {
            score += 80;
        } else if (groundId == Block.SNOW_BLOCK.id || groundId == Block.ICE.id) {
            score += 60;
        }

        score += this.computeSpawnSupportScore(x, z) * 25;

        int preferredY = this.worldProvider instanceof WorldProviderHell ? 64 : 70;
        score -= Math.abs(y - preferredY);
        return score;
    }

    private ChunkCoordinates buildEmergencySpawnPlatform(int centerX, int centerZ, boolean preferShorelineSpawn) {
        int terrainType = this.worldData != null ? this.worldData.getTerrainType() : 0;
        int x = centerX;
        int z = centerZ;

        if (terrainType == 6) {
            x = Math.max(1, Math.min(254, x));
            z = Math.max(1, Math.min(254, z));
        }

        int y = this.findSafeSpawnYAtColumn(x, z);
        if (y <= 1) {
            y = this.f(x, z);
            if (y <= 1 || y >= 126) {
                y = this.e(x, z);
            }
        }

        if (y <= 1 || y >= 126) {
            y = this.worldProvider instanceof WorldProviderHell ? 64 : 70;
        }

        if (y < 2) {
            y = 2;
        } else if (y > 125) {
            y = 125;
        }

        int foundationY = y - 1;
        int foundationId;
        if (this.worldProvider instanceof WorldProviderHell) {
            foundationId = Block.NETHERRACK.id;
        } else if (this.worldData != null && this.worldData.getTerrainType() == 3) {
            foundationId = Block.STONE.id;
        } else if (preferShorelineSpawn) {
            foundationId = Block.SAND.id;
        } else {
            foundationId = Block.GRASS.id;
        }

        for (int dx = -1; dx <= 1; ++dx) {
            for (int dz = -1; dz <= 1; ++dz) {
                int px = x + dx;
                int pz = z + dz;

                if (terrainType == 6 && (px < 0 || px > 255 || pz < 0 || pz > 255)) {
                    continue;
                }

                if (!this.isSafeSpawnFloorBlock(this.getTypeId(px, foundationY, pz))) {
                    this.setRawTypeId(px, foundationY, pz, foundationId);
                }

                this.setRawTypeId(px, y, pz, 0);
                this.setRawTypeId(px, y + 1, pz, 0);
            }
        }

        return new ChunkCoordinates(x, y, z);
    }

    public ChunkCoordinates findSafeSpawnNear(int centerX, int centerZ, int maxRadius, boolean preferShorelineSpawn) {
        int terrainType = this.worldData != null ? this.worldData.getTerrainType() : 0;
        int cx = centerX;
        int cz = centerZ;

        if (terrainType == 6) {
            cx = Math.max(1, Math.min(254, cx));
            cz = Math.max(1, Math.min(254, cz));
        }

        int radiusLimit = Math.max(0, maxRadius);
        int step = 2;
        int bestX = cx;
        int bestY = -1;
        int bestZ = cz;
        int bestScore = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;
        int targetScore = preferShorelineSpawn ? 900 : 500;

        for (int r = 0; r <= radiusLimit; r += step) {
            if (r == 0) {
                int y = this.findSafeSpawnYAtColumn(cx, cz);
                if (y > 0) {
                    int score = this.computeSpawnColumnScore(cx, y, cz, preferShorelineSpawn);
                    bestX = cx;
                    bestY = y;
                    bestZ = cz;
                    bestScore = score;
                    bestDist = 0;
                }
            } else {
                for (int x = cx - r; x <= cx + r; x += step) {
                    int zTop = cz + r;
                    int zBottom = cz - r;

                    if (terrainType != 6 || (x >= 0 && x <= 255 && zTop >= 0 && zTop <= 255)) {
                        int yTop = this.findSafeSpawnYAtColumn(x, zTop);
                        if (yTop > 0) {
                            int score = this.computeSpawnColumnScore(x, yTop, zTop, preferShorelineSpawn);
                            int dist = Math.abs(x - cx) + Math.abs(zTop - cz);
                            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                                bestX = x;
                                bestY = yTop;
                                bestZ = zTop;
                                bestScore = score;
                                bestDist = dist;
                            }
                        }
                    }

                    if (zBottom != zTop && (terrainType != 6 || (x >= 0 && x <= 255 && zBottom >= 0 && zBottom <= 255))) {
                        int yBottom = this.findSafeSpawnYAtColumn(x, zBottom);
                        if (yBottom > 0) {
                            int score = this.computeSpawnColumnScore(x, yBottom, zBottom, preferShorelineSpawn);
                            int dist = Math.abs(x - cx) + Math.abs(zBottom - cz);
                            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                                bestX = x;
                                bestY = yBottom;
                                bestZ = zBottom;
                                bestScore = score;
                                bestDist = dist;
                            }
                        }
                    }
                }

                for (int z = cz - r + step; z <= cz + r - step; z += step) {
                    int xRight = cx + r;
                    int xLeft = cx - r;

                    if (terrainType != 6 || (xRight >= 0 && xRight <= 255 && z >= 0 && z <= 255)) {
                        int yRight = this.findSafeSpawnYAtColumn(xRight, z);
                        if (yRight > 0) {
                            int score = this.computeSpawnColumnScore(xRight, yRight, z, preferShorelineSpawn);
                            int dist = Math.abs(xRight - cx) + Math.abs(z - cz);
                            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                                bestX = xRight;
                                bestY = yRight;
                                bestZ = z;
                                bestScore = score;
                                bestDist = dist;
                            }
                        }
                    }

                    if (xLeft != xRight && (terrainType != 6 || (xLeft >= 0 && xLeft <= 255 && z >= 0 && z <= 255))) {
                        int yLeft = this.findSafeSpawnYAtColumn(xLeft, z);
                        if (yLeft > 0) {
                            int score = this.computeSpawnColumnScore(xLeft, yLeft, z, preferShorelineSpawn);
                            int dist = Math.abs(xLeft - cx) + Math.abs(z - cz);
                            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                                bestX = xLeft;
                                bestY = yLeft;
                                bestZ = z;
                                bestScore = score;
                                bestDist = dist;
                            }
                        }
                    }
                }
            }

            if (bestY > 0 && bestScore >= targetScore && r >= 32) {
                break;
            }
        }

        if (bestY > 0) {
            return new ChunkCoordinates(bestX, bestY, bestZ);
        }

        return this.buildEmergencySpawnPlatform(cx, cz, preferShorelineSpawn);
    }

    public int a(int i, int j) {
        int k;

        for (k = 63; !this.isEmpty(i, k + 1, j); ++k) {
            ;
        }

        return this.getTypeId(i, k, j);
    }

    public void save(boolean flag, IProgressUpdate iprogressupdate) {
        if (this.chunkProvider.canSave()) {
            if (iprogressupdate != null) {
                iprogressupdate.a("Saving level");
            }

            this.w();
            if (iprogressupdate != null) {
                iprogressupdate.b("Saving chunks");
            }

            this.chunkProvider.saveChunks(flag, iprogressupdate);
        }
    }

    private void w() {
        this.k();
        this.w.a(this.worldData, this.players);
        this.worldMaps.a();
    }

    public int getTypeId(int i, int j, int k) {
        return i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000 ? (j < 0 ? 0 : (j >= 128 ? 0 : this.getChunkAt(i >> 4, k >> 4).getTypeId(i & 15, j, k & 15))) : 0;
    }

    public boolean isEmpty(int i, int j, int k) {
        return this.getTypeId(i, j, k) == 0;
    }

    public boolean isLoaded(int i, int j, int k) {
        return j >= 0 && j < 128 ? this.isChunkLoaded(i >> 4, k >> 4) : false;
    }

    public boolean areChunksLoaded(int i, int j, int k, int l) {
        return this.a(i - l, j - l, k - l, i + l, j + l, k + l);
    }

    public boolean a(int i, int j, int k, int l, int i1, int j1) {
        if (i1 >= 0 && j < 128) {
            i >>= 4;
            j >>= 4;
            k >>= 4;
            l >>= 4;
            i1 >>= 4;
            j1 >>= 4;

            for (int k1 = i; k1 <= l; ++k1) {
                for (int l1 = k; l1 <= j1; ++l1) {
                    if (!this.isChunkLoaded(k1, l1)) {
                        return false;
                    }
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private boolean isChunkLoaded(int i, int j) {
        return this.chunkProvider.isChunkLoaded(i, j);
    }

    public Chunk getChunkAtWorldCoords(int i, int j) {
        return this.getChunkAt(i >> 4, j >> 4);
    }

    // CraftBukkit start
    public Chunk getChunkAt(int i, int j) {
        Chunk result = null;
        synchronized (this.chunkLock) {
            if (this.lastChunkAccessed == null || this.lastXAccessed != i || this.lastZAccessed != j) {
                this.lastXAccessed = i;
                this.lastZAccessed = j;
                this.lastChunkAccessed = this.chunkProvider.getOrCreateChunk(i, j);
            }
            result = this.lastChunkAccessed;
        }
        return result;
    }
    // CraftBukkit end

    public boolean setRawTypeIdAndData(int i, int j, int k, int l, int i1) {
        // Prevent block placement/modification outside 256x256 in CLASSIC worlds
        if (this.worldData != null && this.worldData.getTerrainType() == 6) {
            if (i < 0 || i > 255 || k < 0 || k > 255) {
                return false;
            }
        }
        if (i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000) {
            if (j < 0) {
                return false;
            } else if (j >= 128) {
                return false;
            } else {
                Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

                return chunk.a(i & 15, j, k & 15, l, i1);
            }
        } else {
            return false;
        }
    }

    public boolean setRawTypeId(int i, int j, int k, int l) {
        // Prevent block placement/modification outside 256x256 in CLASSIC worlds
        if (this.worldData != null && this.worldData.getTerrainType() == 6) {
            if (i < 0 || i > 255 || k < 0 || k > 255) {
                return false;
            }
        }
        if (i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000) {
            if (j < 0) {
                return false;
            } else if (j >= 128) {
                return false;
            } else {
                Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

                return chunk.a(i & 15, j, k & 15, l);
            }
        } else {
            return false;
        }
    }

    public Material getMaterial(int i, int j, int k) {
        int l = this.getTypeId(i, j, k);

        return l == 0 ? Material.AIR : Block.byId[l].material;
    }

    public int getData(int i, int j, int k) {
        if (i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000) {
            if (j < 0) {
                return 0;
            } else if (j >= 128) {
                return 0;
            } else {
                Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

                i &= 15;
                k &= 15;
                return chunk.getData(i, j, k);
            }
        } else {
            return 0;
        }
    }

    public BlockStateKey getBlockStateKey(int i, int j, int k) {
        return BlockStateBridge.fromLegacy(this.getTypeId(i, j, k), this.getData(i, j, k));
    }

    public void setData(int i, int j, int k, int l) {
        if (this.setRawData(i, j, k, l)) {
            int i1 = this.getTypeId(i, j, k);

            if (Block.t[i1 & 255]) { // Block.t seems to be Block.requiresSelfNotify
                this.update(i, j, k, i1); // World.update (notifyBlockChange)
            } else {
                this.applyPhysics(i, j, k, i1); // World.applyPhysics (notifyBlocksOfNeighborChange)
            }
        }
    }

    public boolean setRawData(int i, int j, int k, int l) {
        if (i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000) {
            if (j < 0) {
                return false;
            } else if (j >= 128) {
                return false;
            } else {
                Chunk chunk = this.getChunkAt(i >> 4, k >> 4);
                int blockId = chunk.getTypeId(i & 15, j, k & 15);
                boolean isFenceGate = (blockId == 188);

                if (isFenceGate) {
                    System.out.println("[ServerWorld SET_RAW_DATA] For FENCE_GATE at (" + i + "," + j + "," + k + ") to meta: " + l + ". Current Block ID: " + blockId);
                }

                // CraftBukkit start
                // org.bukkit.block.Block bblock = world.getBlockAt(i,j,k); // world is not defined here, should be this.world or this.getWorld()
                org.bukkit.block.Block bblock = this.getWorld().getBlockAt(i,j,k); // Corrected world reference
                BlockCanBuildEvent event = new BlockCanBuildEvent(bblock, bblock.getTypeId(), true);
                // if(chunk.isEmpty(i & 15,j,k & 15)) event.setBuildable(true); // isEmpty is not a direct Chunk method
                if((chunk.getTypeId(i & 15, j, k & 15) == 0)) event.setBuildable(true); // Corrected isEmpty check
                this.getServer().getPluginManager().callEvent(event);
                if (!event.isBuildable()) return false;
                // CraftBukkit end

                // Attempting to use 'b' as the obfuscated call to set metadata, as suggested by previous diffs.
                // This assumes chunk.b(x,y,z,meta) is the server's way of calling what might be net.minecraft.src.Chunk.setBlockMetadata
                // or an equivalent method.
                chunk.b(i & 15, j, k & 15, l);

                // this.g(i, j, k, l); // markBlocksDirtyVertical - this seems to be for lighting/rendering, not essential for state
                return true;
            }
        } else {
            return false;
        }
    }

    public boolean setTypeId(int i, int j, int k, int l) {
        // CraftBukkit start
        int old = this.getTypeId(i, j, k);
        if (this.setRawTypeId(i, j, k, l)) {
            this.update(i, j, k, l == 0 ? old : l);
            return true;
        } else {
            return false;
        }
        // CraftBukkit end
    }

    public boolean setTypeIdAndData(int i, int j, int k, int l, int i1) {
        // CraftBukkit start
        int old = this.getTypeId(i, j, k);
        if (this.setRawTypeIdAndData(i, j, k, l, i1)) {
            this.update(i, j, k, l == 0 ? old : l);
            return true;
        } else {
            return false;
        }
        // CraftBukkit end
    }

    public boolean setBlockState(int i, int j, int k, BlockStateKey stateKey) {
        BlockStateBridge.LegacyBlockData legacy = BlockStateBridge.toLegacy(stateKey);
        if (legacy.fallbackUsed) {
            System.err.println("[BlockState] nearest legacy fallback projection at " + i + "," + j + "," + k + " for " + stateKey);
        }
        return this.setRawTypeIdAndData(i, j, k, legacy.blockId, legacy.metadata);
    }

    public boolean setBlockStateAndData(int i, int j, int k, BlockStateKey stateKey) {
        BlockStateBridge.LegacyBlockData legacy = BlockStateBridge.toLegacy(stateKey);
        if (legacy.fallbackUsed) {
            System.err.println("[BlockState] nearest legacy fallback projection at " + i + "," + j + "," + k + " for " + stateKey);
        }
        return this.setTypeIdAndData(i, j, k, legacy.blockId, legacy.metadata);
    }

    public void notify(int i, int j, int k) {
        for (int l = 0; l < this.u.size(); ++l) {
            ((IWorldAccess) this.u.get(l)).a(i, j, k);
        }
    }

    protected void update(int i, int j, int k, int l) {
        this.notify(i, j, k);
        this.applyPhysics(i, j, k, l);
    }

    public void g(int i, int j, int k, int l) {
        if (k > l) {
            int i1 = l;

            l = k;
            k = i1;
        }

        this.b(i, k, j, i, l, j);
    }

    public void i(int i, int j, int k) {
        for (int l = 0; l < this.u.size(); ++l) {
            ((IWorldAccess) this.u.get(l)).a(i, j, k, i, j, k);
        }
    }

    public void b(int i, int j, int k, int l, int i1, int j1) {
        for (int k1 = 0; k1 < this.u.size(); ++k1) {
            ((IWorldAccess) this.u.get(k1)).a(i, j, k, l, i1, j1);
        }
    }

    public void applyPhysics(int i, int j, int k, int l) {
        this.k(i - 1, j, k, l);
        this.k(i + 1, j, k, l);
        this.k(i, j - 1, k, l);
        this.k(i, j + 1, k, l);
        this.k(i, j, k - 1, l);
        this.k(i, j, k + 1, l);
    }

    private void k(int i, int j, int k, int l) {
        if (!this.suppressPhysics && !this.isStatic) {
            Block block = Block.byId[this.getTypeId(i, j, k)];

            if (block != null) {
                // CraftBukkit start
                CraftWorld world = ((WorldServer) this).getWorld();
                if (world != null) {
                    BlockPhysicsEvent event = new BlockPhysicsEvent(world.getBlockAt(i, j, k), l);
                    this.getServer().getPluginManager().callEvent(event);

                    if (event.isCancelled()) {
                        return;
                    }
                }
                // CraftBukkit end

                block.doPhysics(this, i, j, k, l);
            }
        }
    }

    public boolean isChunkLoaded(int i, int j, int k) {
        return this.getChunkAt(i >> 4, k >> 4).c(i & 15, j, k & 15);
    }

    public int k(int i, int j, int k) {
        if (j < 0) {
            return 0;
        } else {
            if (j >= 128) {
                j = 127;
            }

            return this.getChunkAt(i >> 4, k >> 4).c(i & 15, j, k & 15, 0);
        }
    }

    public int getLightLevel(int i, int j, int k) {
        return this.a(i, j, k, true);
    }

    public int a(int i, int j, int k, boolean flag) {
        if (i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000) {
            if (flag) {
                int l = this.getTypeId(i, j, k);

                if (l == Block.STEP.id || l == Block.SOIL.id || l == Block.COBBLESTONE_STAIRS.id || l == Block.WOOD_STAIRS.id) {
                    int i1 = this.a(i, j + 1, k, false);
                    int j1 = this.a(i + 1, j, k, false);
                    int k1 = this.a(i - 1, j, k, false);
                    int l1 = this.a(i, j, k + 1, false);
                    int i2 = this.a(i, j, k - 1, false);

                    if (j1 > i1) {
                        i1 = j1;
                    }

                    if (k1 > i1) {
                        i1 = k1;
                    }

                    if (l1 > i1) {
                        i1 = l1;
                    }

                    if (i2 > i1) {
                        i1 = i2;
                    }

                    return i1;
                }
            }

            if (j < 0) {
                return 0;
            } else {
                if (j >= 128) {
                    j = 127;
                }

                Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

                i &= 15;
                k &= 15;
                return chunk.c(i, j, k, this.f);
            }
        } else {
            return 15;
        }
    }

    public boolean m(int i, int j, int k) {
        if (i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000) {
            if (j < 0) {
                return false;
            } else if (j >= 128) {
                return true;
            } else if (!this.isChunkLoaded(i >> 4, k >> 4)) {
                return false;
            } else {
                Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

                i &= 15;
                k &= 15;
                return chunk.c(i, j, k);
            }
        } else {
            return false;
        }
    }

    public int getHighestBlockYAt(int i, int j) {
        if (i >= -32000000 && j >= -32000000 && i < 32000000 && j <= 32000000) {
            if (!this.isChunkLoaded(i >> 4, j >> 4)) {
                return 0;
            } else {
                Chunk chunk = this.getChunkAt(i >> 4, j >> 4);

                return chunk.b(i & 15, j & 15);
            }
        } else {
            return 0;
        }
    }

    public void a(EnumSkyBlock enumskyblock, int i, int j, int k, int l) {
        if (!this.worldProvider.e || enumskyblock != EnumSkyBlock.SKY) {
            if (this.isLoaded(i, j, k)) {
                if (enumskyblock == EnumSkyBlock.SKY) {
                    if (this.m(i, j, k)) {
                        l = 15;
                    }
                } else if (enumskyblock == EnumSkyBlock.BLOCK) {
                    int i1 = this.getTypeId(i, j, k);

                    if (Block.s[i1] > l) {
                        l = Block.s[i1];
                    }
                }

                if (this.a(enumskyblock, i, j, k) != l) {
                    this.a(enumskyblock, i, j, k, i, j, k);
                }
            }
        }
    }

    public int a(EnumSkyBlock enumskyblock, int i, int j, int k) {
        if (j < 0) {
            j = 0;
        }

        if (j >= 128) {
            j = 127;
        }

        if (j >= 0 && j < 128 && i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000) {
            int l = i >> 4;
            int i1 = k >> 4;

            if (!this.isChunkLoaded(l, i1)) {
                return 0;
            } else {
                Chunk chunk = this.getChunkAt(l, i1);

                return chunk.a(enumskyblock, i & 15, j, k & 15);
            }
        } else {
            return enumskyblock.c;
        }
    }

    public void b(EnumSkyBlock enumskyblock, int i, int j, int k, int l) {
        if (i >= -32000000 && k >= -32000000 && i < 32000000 && k <= 32000000) {
            if (j >= 0) {
                if (j < 128) {
                    if (this.isChunkLoaded(i >> 4, k >> 4)) {
                        Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

                        chunk.a(enumskyblock, i & 15, j, k & 15, l);

                        for (int i1 = 0; i1 < this.u.size(); ++i1) {
                            ((IWorldAccess) this.u.get(i1)).a(i, j, k);
                        }
                    }
                }
            }
        }
    }

    public float n(int i, int j, int k) {
        return this.worldProvider.f[this.getLightLevel(i, j, k)];
    }

    public boolean d() {
        return this.f < 4;
    }

    public MovingObjectPosition a(Vec3D vec3d, Vec3D vec3d1) {
        return this.rayTrace(vec3d, vec3d1, false, false);
    }

    public MovingObjectPosition rayTrace(Vec3D vec3d, Vec3D vec3d1, boolean flag) {
        return this.rayTrace(vec3d, vec3d1, flag, false);
    }

    public MovingObjectPosition rayTrace(Vec3D vec3d, Vec3D vec3d1, boolean flag, boolean flag1) {
        if (!Double.isNaN(vec3d.a) && !Double.isNaN(vec3d.b) && !Double.isNaN(vec3d.c)) {
            if (!Double.isNaN(vec3d1.a) && !Double.isNaN(vec3d1.b) && !Double.isNaN(vec3d1.c)) {
                int i = MathHelper.floor(vec3d1.a);
                int j = MathHelper.floor(vec3d1.b);
                int k = MathHelper.floor(vec3d1.c);
                int l = MathHelper.floor(vec3d.a);
                int i1 = MathHelper.floor(vec3d.b);
                int j1 = MathHelper.floor(vec3d.c);
                int k1 = this.getTypeId(l, i1, j1);
                int l1 = this.getData(l, i1, j1);
                Block block = Block.byId[k1];

                if ((!flag1 || block == null || block.e(this, l, i1, j1) != null) && k1 > 0 && block.a(l1, flag)) {
                    MovingObjectPosition movingobjectposition = block.a(this, l, i1, j1, vec3d, vec3d1);

                    if (movingobjectposition != null) {
                        return movingobjectposition;
                    }
                }

                k1 = 200;

                while (k1-- >= 0) {
                    if (Double.isNaN(vec3d.a) || Double.isNaN(vec3d.b) || Double.isNaN(vec3d.c)) {
                        return null;
                    }

                    if (l == i && i1 == j && j1 == k) {
                        return null;
                    }

                    boolean flag2 = true;
                    boolean flag3 = true;
                    boolean flag4 = true;
                    double d0 = 999.0D;
                    double d1 = 999.0D;
                    double d2 = 999.0D;

                    if (i > l) {
                        d0 = (double) l + 1.0D;
                    } else if (i < l) {
                        d0 = (double) l + 0.0D;
                    } else {
                        flag2 = false;
                    }

                    if (j > i1) {
                        d1 = (double) i1 + 1.0D;
                    } else if (j < i1) {
                        d1 = (double) i1 + 0.0D;
                    } else {
                        flag3 = false;
                    }

                    if (k > j1) {
                        d2 = (double) j1 + 1.0D;
                    } else if (k < j1) {
                        d2 = (double) j1 + 0.0D;
                    } else {
                        flag4 = false;
                    }

                    double d3 = 999.0D;
                    double d4 = 999.0D;
                    double d5 = 999.0D;
                    double d6 = vec3d1.a - vec3d.a;
                    double d7 = vec3d1.b - vec3d.b;
                    double d8 = vec3d1.c - vec3d.c;

                    if (flag2) {
                        d3 = (d0 - vec3d.a) / d6;
                    }

                    if (flag3) {
                        d4 = (d1 - vec3d.b) / d7;
                    }

                    if (flag4) {
                        d5 = (d2 - vec3d.c) / d8;
                    }

                    boolean flag5 = false;
                    byte b0;

                    if (d3 < d4 && d3 < d5) {
                        if (i > l) {
                            b0 = 4;
                        } else {
                            b0 = 5;
                        }

                        vec3d.a = d0;
                        vec3d.b += d7 * d3;
                        vec3d.c += d8 * d3;
                    } else if (d4 < d5) {
                        if (j > i1) {
                            b0 = 0;
                        } else {
                            b0 = 1;
                        }

                        vec3d.a += d6 * d4;
                        vec3d.b = d1;
                        vec3d.c += d8 * d4;
                    } else {
                        if (k > j1) {
                            b0 = 2;
                        } else {
                            b0 = 3;
                        }

                        vec3d.a += d6 * d5;
                        vec3d.b += d7 * d5;
                        vec3d.c = d2;
                    }

                    Vec3D vec3d2 = Vec3D.create(vec3d.a, vec3d.b, vec3d.c);

                    l = (int) (vec3d2.a = (double) MathHelper.floor(vec3d.a));
                    if (b0 == 5) {
                        --l;
                        ++vec3d2.a;
                    }

                    i1 = (int) (vec3d2.b = (double) MathHelper.floor(vec3d.b));
                    if (b0 == 1) {
                        --i1;
                        ++vec3d2.b;
                    }

                    j1 = (int) (vec3d2.c = (double) MathHelper.floor(vec3d.c));
                    if (b0 == 3) {
                        --j1;
                        ++vec3d2.c;
                    }

                    int i2 = this.getTypeId(l, i1, j1);
                    int j2 = this.getData(l, i1, j1);
                    Block block1 = Block.byId[i2];

                    if ((!flag1 || block1 == null || block1.e(this, l, i1, j1) != null) && i2 > 0 && block1.a(j2, flag)) {
                        MovingObjectPosition movingobjectposition1 = block1.a(this, l, i1, j1, vec3d, vec3d1);

                        if (movingobjectposition1 != null) {
                            return movingobjectposition1;
                        }
                    }
                }

                return null;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    public void makeSound(Entity entity, String s, float f, float f1) {
        if (!this.isStatic && entity instanceof EntityHuman) {
            this.makeSound((EntityHuman) entity, entity.locX, entity.locY - (double) entity.height, entity.locZ, s, f, f1);
            return;
        }

        String resolved = net.minecraft.server.registry.SoundEventResolver.resolve(s);
        for (int i = 0; i < this.u.size(); ++i) {
            ((IWorldAccess) this.u.get(i)).a(resolved, entity.locX, entity.locY - (double) entity.height, entity.locZ, f, f1);
        }
    }

    public void makeSound(EntityHuman source, double d0, double d1, double d2, String s, float f, float f1) {
        String resolved = net.minecraft.server.registry.SoundEventResolver.resolve(s);

        if (!this.isStatic && this instanceof WorldServer && source != null) {
            float range = 16.0F;
            if (f > 1.0F) {
                range *= f;
            }

            WorldServer worldServer = (WorldServer) this;
            worldServer.server.serverConfigurationManager.sendPacketNearby(source, d0, d1, d2, range, worldServer.dimension, new Packet62Sound(resolved, d0, d1, d2, f, f1));
            return;
        }

        for (int i = 0; i < this.u.size(); ++i) {
            ((IWorldAccess) this.u.get(i)).a(resolved, d0, d1, d2, f, f1);
        }
    }

    public void makeSound(double d0, double d1, double d2, String s, float f, float f1) {
        String resolved = net.minecraft.server.registry.SoundEventResolver.resolve(s);
        for (int i = 0; i < this.u.size(); ++i) {
            ((IWorldAccess) this.u.get(i)).a(resolved, d0, d1, d2, f, f1);
        }
    }

    public void a(String s, int i, int j, int k) {
        String resolved = net.minecraft.server.registry.SoundEventResolver.resolve(s);
        for (int l = 0; l < this.u.size(); ++l) {
            ((IWorldAccess) this.u.get(l)).a(resolved, i, j, k);
        }
    }

    public void a(String s, double d0, double d1, double d2, double d3, double d4, double d5) {
        String resolved = net.minecraft.server.registry.ParticleTypeRegistryApi.resolveLegacyKey(s);
        for (int i = 0; i < this.u.size(); ++i) {
            ((IWorldAccess) this.u.get(i)).a(resolved, d0, d1, d2, d3, d4, d5);
        }
    }

    public boolean strikeLightning(Entity entity) {
        this.e.add(entity);
        return true;
    }

    // CraftBukkit start - used for entities other than creatures
    public boolean addEntity(Entity entity) {
        return this.addEntity(entity, SpawnReason.CUSTOM); // Set reason as Custom by default
    }


    public boolean addEntity(Entity entity, SpawnReason spawnReason) { // Changed signature, added SpawnReason
    // CraftBukkit end
        int i = MathHelper.floor(entity.locX / 16.0D);
        int j = MathHelper.floor(entity.locZ / 16.0D);
        boolean flag = false;

        if (entity instanceof EntityHuman) {
            flag = true;
        }

        // CraftBukkit start
        if (entity instanceof EntityLiving && !(entity instanceof EntityPlayer)) {
            CreatureSpawnEvent event = CraftEventFactory.callCreatureSpawnEvent((EntityLiving) entity, spawnReason);

            if (event.isCancelled()) {
                return false;
            }
        } else if (entity instanceof EntityItem) {
            ItemSpawnEvent event = CraftEventFactory.callItemSpawnEvent((EntityItem) entity);
            if (event.isCancelled()) {
                return false;
            }
        }
        // CraftBukkit end

        EntitySpawnEvent nmsSpawnEvent = new EntitySpawnEvent(entity, this, spawnReason == null ? "UNKNOWN" : spawnReason.name());
        EventBus.global().publish(nmsSpawnEvent);
        if (nmsSpawnEvent.isCancelled()) {
            return false;
        }

        if (!flag && !this.isChunkLoaded(i, j)) {
            return false;
        } else {
            if (entity instanceof EntityHuman) {
                EntityHuman entityhuman = (EntityHuman) entity;

                this.players.add(entityhuman);
                this.everyoneSleeping();
            }

            this.getChunkAt(i, j).a(entity);
            this.entityList.add(entity);
            this.c(entity);
            return true;
        }
    }

    protected void c(Entity entity) {
        for (int i = 0; i < this.u.size(); ++i) {
            ((IWorldAccess) this.u.get(i)).a(entity);
        }
    }

    protected void d(Entity entity) {
        for (int i = 0; i < this.u.size(); ++i) {
            ((IWorldAccess) this.u.get(i)).b(entity);
        }
    }

    public void kill(Entity entity) {
        if (entity.passenger != null) {
            entity.passenger.mount((Entity) null);
        }

        if (entity.vehicle != null) {
            entity.mount((Entity) null);
        }

        entity.die();
        if (entity instanceof EntityHuman) {
            this.players.remove((EntityHuman) entity);
            this.everyoneSleeping();
        }
    }

    public void removeEntity(Entity entity) {
        entity.die();
        if (entity instanceof EntityHuman) {
            this.players.remove((EntityHuman) entity);
            this.everyoneSleeping();
        }

        int i = entity.bH;
        int j = entity.bJ;

        if (entity.bG && this.isChunkLoaded(i, j)) {
            this.getChunkAt(i, j).b(entity);
        }

        this.entityList.remove(entity);
        this.d(entity);
    }

    public void addIWorldAccess(IWorldAccess iworldaccess) {
        this.u.add(iworldaccess);
    }

    public List getEntities(Entity entity, AxisAlignedBB axisalignedbb) {
        this.K.clear();
        int i = MathHelper.floor(axisalignedbb.a);
        int j = MathHelper.floor(axisalignedbb.d + 1.0D);
        int k = MathHelper.floor(axisalignedbb.b);
        int l = MathHelper.floor(axisalignedbb.e + 1.0D);
        int i1 = MathHelper.floor(axisalignedbb.c);
        int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = i1; l1 < j1; ++l1) {
                if (this.isLoaded(k1, 64, l1)) {
                    for (int i2 = k - 1; i2 < l; ++i2) {
                        Block block = Block.byId[this.getTypeId(k1, i2, l1)];

                        if (block != null) {
                            block.a(this, k1, i2, l1, axisalignedbb, this.K);
                        }
                    }
                }
            }
        }

        double d0 = 0.25D;
        List list = this.b(entity, axisalignedbb.b(d0, d0, d0));

        for (int j2 = 0; j2 < list.size(); ++j2) {
            AxisAlignedBB axisalignedbb1 = ((Entity) list.get(j2)).e_();

            if (axisalignedbb1 != null && axisalignedbb1.a(axisalignedbb)) {
                this.K.add(axisalignedbb1);
            }

            axisalignedbb1 = entity.a_((Entity) list.get(j2));
            if (axisalignedbb1 != null && axisalignedbb1.a(axisalignedbb)) {
                this.K.add(axisalignedbb1);
            }
        }

        return this.K;
    }

    public int a(float f) {
        float f1 = this.b(f);
        float f2 = 1.0F - (MathHelper.cos(f1 * 3.1415927F * 2.0F) * 2.0F + 0.5F);

        if (f2 < 0.0F) {
            f2 = 0.0F;
        }

        if (f2 > 1.0F) {
            f2 = 1.0F;
        }

        f2 = 1.0F - f2;
        f2 = (float) ((double) f2 * (1.0D - (double) (this.d(f) * 5.0F) / 16.0D));
        f2 = (float) ((double) f2 * (1.0D - (double) (this.c(f) * 5.0F) / 16.0D));
        f2 = 1.0F - f2;
        return (int) (f2 * 11.0F);
    }

    public float b(float f) {
        return this.worldProvider.a(this.worldData.f(), f);
    }

    public int e(int i, int j) {
        Chunk chunk = this.getChunkAtWorldCoords(i, j);
        int k = 127;

        i &= 15;

        for (j &= 15; k > 0; --k) {
            int l = chunk.getTypeId(i, k, j);
            Material material = l == 0 ? Material.AIR : Block.byId[l].material;

            if (material.isSolid() || material.isLiquid()) {
                return k + 1;
            }
        }

        return -1;
    }

    public int f(int i, int j) {
        Chunk chunk = this.getChunkAtWorldCoords(i, j);
        int k = 127;

        i &= 15;

        for (j &= 15; k > 0; --k) {
            int l = chunk.getTypeId(i, k, j);

            if (l != 0 && Block.byId[l].material.isSolid()) {
                return k + 1;
            }
        }

        return -1;
    }
    private static long chunkKeyFromChunkCoords(int chunkX, int chunkZ) {
        return ((long) chunkX & 4294967295L) | (((long) chunkZ & 4294967295L) << 32);
    }

    private void indexScheduledTick(NextTickListEntry nextticklistentry) {
        Long key = Long.valueOf(chunkKeyFromChunkCoords(nextticklistentry.a >> 4, nextticklistentry.c >> 4));
        Set chunkTicks = (Set) this.scheduledTickChunkIndex.get(key);

        if (chunkTicks == null) {
            chunkTicks = new HashSet();
            this.scheduledTickChunkIndex.put(key, chunkTicks);
        }

        chunkTicks.add(nextticklistentry);
    }

    private void unindexScheduledTick(NextTickListEntry nextticklistentry) {
        Long key = Long.valueOf(chunkKeyFromChunkCoords(nextticklistentry.a >> 4, nextticklistentry.c >> 4));
        Set chunkTicks = (Set) this.scheduledTickChunkIndex.get(key);

        if (chunkTicks != null) {
            chunkTicks.remove(nextticklistentry);
            if (chunkTicks.isEmpty()) {
                this.scheduledTickChunkIndex.remove(key);
            }
        }
    }

    public void c(int i, int j, int k, int l, int i1) {
        NextTickListEntry nextticklistentry = new NextTickListEntry(i, j, k, l);
        byte b0 = 8;

        if (this.a) {
            if (this.a(nextticklistentry.a - b0, nextticklistentry.b - b0, nextticklistentry.c - b0, nextticklistentry.a + b0, nextticklistentry.b + b0, nextticklistentry.c + b0)) {
                int j1 = this.getTypeId(nextticklistentry.a, nextticklistentry.b, nextticklistentry.c);

                if (j1 == nextticklistentry.d && j1 > 0) {
                    Block.byId[j1].a(this, nextticklistentry.a, nextticklistentry.b, nextticklistentry.c, this.random);
                }
            }
        } else {
            if (this.a(i - b0, j - b0, k - b0, i + b0, j + b0, k + b0)) {
                if (l > 0) {
                    nextticklistentry.a((long) i1 + this.blockTickTime);
                }

                if (!this.F.contains(nextticklistentry)) {
                    this.F.add(nextticklistentry);
                    this.E.add(nextticklistentry);
                    this.indexScheduledTick(nextticklistentry);
                }
            }
        }
    }

    public List getPendingBlockTicksForChunk(int chunkX, int chunkZ) {
        Set chunkTicks = (Set) this.scheduledTickChunkIndex.get(Long.valueOf(chunkKeyFromChunkCoords(chunkX, chunkZ)));

        if (chunkTicks == null || chunkTicks.isEmpty()) {
            return null;
        }

        ArrayList copiedTicks = new ArrayList(chunkTicks.size());
        Iterator iterator = chunkTicks.iterator();

        while (iterator.hasNext()) {
            NextTickListEntry nextticklistentry = (NextTickListEntry) iterator.next();
            NextTickListEntry copy = new NextTickListEntry(nextticklistentry.a, nextticklistentry.b, nextticklistentry.c, nextticklistentry.d);

            copy.a(nextticklistentry.e);
            copiedTicks.add(copy);
        }

        return copiedTicks;
    }

    public void scheduleBlockUpdateFromLoad(int i, int j, int k, int l, int i1) {
        if (l <= 0 || l >= Block.byId.length || Block.byId[l] == null) {
            return;
        }

        NextTickListEntry nextticklistentry = new NextTickListEntry(i, j, k, l);
        long delay = i1 < 0 ? 0L : (long) i1;

        nextticklistentry.a(this.blockTickTime + delay);
        if (!this.F.contains(nextticklistentry)) {
                    this.F.add(nextticklistentry);
                    this.E.add(nextticklistentry);
                    this.indexScheduledTick(nextticklistentry);
                }
    }

    public long getBlockTickTime() {
        return this.blockTickTime;
    }

    public void cleanUp() {
        int i;
        Entity entity;

        for (i = 0; i < this.e.size(); ++i) {
            entity = (Entity) this.e.get(i);
            // CraftBukkit start - fixed an NPE
            if (entity == null) {
                continue;
            }
            // CraftBukkit end
            entity.m_();
            if (entity.dead) {
                this.e.remove(i--);
            }
        }

        this.entityList.removeAll(this.D);

        int j;
        int k;

        for (i = 0; i < this.D.size(); ++i) {
            entity = (Entity) this.D.get(i);
            j = entity.bH;
            k = entity.bJ;
            if (entity.bG && this.isChunkLoaded(j, k)) {
                this.getChunkAt(j, k).b(entity);
            }
        }

        for (i = 0; i < this.D.size(); ++i) {
            this.d((Entity) this.D.get(i));
        }

        this.D.clear();

        for (i = 0; i < this.entityList.size(); ++i) {
            entity = (Entity) this.entityList.get(i);
            if (entity.vehicle != null) {
                if (!entity.vehicle.dead && entity.vehicle.passenger == entity) {
                    continue;
                }

                entity.vehicle.passenger = null;
                entity.vehicle = null;
            }

            if (!entity.dead) {
                this.playerJoinedWorld(entity);
            }

            if (entity.dead) {
                j = entity.bH;
                k = entity.bJ;
                if (entity.bG && this.isChunkLoaded(j, k)) {
                    this.getChunkAt(j, k).b(entity);
                }

                this.entityList.remove(i--);
                this.d(entity);
            }
        }

        this.L = true;
        Iterator iterator = this.c.iterator();

        while (iterator.hasNext()) {
            TileEntity tileentity = (TileEntity) iterator.next();

            if (!tileentity.g()) {
                tileentity.g_();
            }

            if (tileentity.g()) {
                iterator.remove();
                Chunk chunk = this.getChunkAt(tileentity.x >> 4, tileentity.z >> 4);

                if (chunk != null) {
                    chunk.e(tileentity.x & 15, tileentity.y, tileentity.z & 15);
                }
            }
        }

        this.L = false;

        // Craftbukkit start
        if (!tileEntitiesToUnload.isEmpty()) {
            this.c.removeAll(tileEntitiesToUnload);
            this.tileEntitiesToUnload.clear();
        }
        // Craftbukkit end

        if (!this.G.isEmpty()) {
            Iterator iterator1 = this.G.iterator();

            while (iterator1.hasNext()) {
                TileEntity tileentity1 = (TileEntity) iterator1.next();

                if (!tileentity1.g()) {
                    // CraftBukkit - order matters, moved down
                    /* if (!this.c.contains(tileentity1)) {
                        this.c.add(tileentity1);
                    } */

                    Chunk chunk1 = this.getChunkAt(tileentity1.x >> 4, tileentity1.z >> 4);

                    if (chunk1 != null) {
                        chunk1.placeTileEntity(tileentity1.x & 15, tileentity1.y, tileentity1.z & 15, tileentity1);
                        // CraftBukkit start - moved in from above
                        if (!this.c.contains(tileentity1)) {
                            this.c.add(tileentity1);
                        }
                        // CraftBukkit end
                    }

                    this.notify(tileentity1.x, tileentity1.y, tileentity1.z);
                }
            }

            this.G.clear();
        }
    }

    public void a(Collection collection) {
        if (this.L) {
            this.G.addAll(collection);
        } else {
            this.c.addAll(collection);
        }
    }

    public void playerJoinedWorld(Entity entity) {
        this.entityJoinedWorld(entity, true);
    }

    public void entityJoinedWorld(Entity entity, boolean flag) {
        int i = MathHelper.floor(entity.locX);
        int j = MathHelper.floor(entity.locZ);
        byte b0 = 32;

        if (!flag || this.a(i - b0, 0, j - b0, i + b0, 128, j + b0)) {
            entity.bo = entity.locX;
            entity.bp = entity.locY;
            entity.bq = entity.locZ;
            entity.lastYaw = entity.yaw;
            entity.lastPitch = entity.pitch;
            if (flag && entity.bG) {
                if (entity.vehicle != null) {
                    entity.E();
                } else {
                    entity.m_();
                }
            }

            if (Double.isNaN(entity.locX) || Double.isInfinite(entity.locX)) {
                entity.locX = entity.bo;
            }

            if (Double.isNaN(entity.locY) || Double.isInfinite(entity.locY)) {
                entity.locY = entity.bp;
            }

            if (Double.isNaN(entity.locZ) || Double.isInfinite(entity.locZ)) {
                entity.locZ = entity.bq;
            }

            if (Double.isNaN((double) entity.pitch) || Double.isInfinite((double) entity.pitch)) {
                entity.pitch = entity.lastPitch;
            }

            if (Double.isNaN((double) entity.yaw) || Double.isInfinite((double) entity.yaw)) {
                entity.yaw = entity.lastYaw;
            }

            int k = MathHelper.floor(entity.locX / 16.0D);
            int l = MathHelper.floor(entity.locY / 16.0D);
            int i1 = MathHelper.floor(entity.locZ / 16.0D);

            if (!entity.bG || entity.bH != k || entity.bI != l || entity.bJ != i1) {
                if (entity.bG && this.isChunkLoaded(entity.bH, entity.bJ)) {
                    this.getChunkAt(entity.bH, entity.bJ).a(entity, entity.bI);
                }

                if (this.isChunkLoaded(k, i1)) {
                    entity.bG = true;
                    this.getChunkAt(k, i1).a(entity);
                } else {
                    entity.bG = false;
                }
            }

            if (flag && entity.bG && entity.passenger != null) {
                if (!entity.passenger.dead && entity.passenger.vehicle == entity) {
                    this.playerJoinedWorld(entity.passenger);
                } else {
                    entity.passenger.vehicle = null;
                    entity.passenger = null;
                }
            }
        }
    }

    public boolean containsEntity(AxisAlignedBB axisalignedbb) {
        List list = this.b((Entity) null, axisalignedbb);

        for (int i = 0; i < list.size(); ++i) {
            Entity entity = (Entity) list.get(i);

            if (!entity.dead && entity.aI) {
                return false;
            }
        }

        return true;
    }

    public boolean b(AxisAlignedBB axisalignedbb) {
        int i = MathHelper.floor(axisalignedbb.a);
        int j = MathHelper.floor(axisalignedbb.d + 1.0D);
        int k = MathHelper.floor(axisalignedbb.b);
        int l = MathHelper.floor(axisalignedbb.e + 1.0D);
        int i1 = MathHelper.floor(axisalignedbb.c);
        int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

        if (axisalignedbb.a < 0.0D) {
            --i;
        }

        if (axisalignedbb.b < 0.0D) {
            --k;
        }

        if (axisalignedbb.c < 0.0D) {
            --i1;
        }

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    Block block = Block.byId[this.getTypeId(k1, l1, i2)];

                    if (block != null) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean c(AxisAlignedBB axisalignedbb) {
        int i = MathHelper.floor(axisalignedbb.a);
        int j = MathHelper.floor(axisalignedbb.d + 1.0D);
        int k = MathHelper.floor(axisalignedbb.b);
        int l = MathHelper.floor(axisalignedbb.e + 1.0D);
        int i1 = MathHelper.floor(axisalignedbb.c);
        int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

        if (axisalignedbb.a < 0.0D) {
            --i;
        }

        if (axisalignedbb.b < 0.0D) {
            --k;
        }

        if (axisalignedbb.c < 0.0D) {
            --i1;
        }

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    Block block = Block.byId[this.getTypeId(k1, l1, i2)];

                    if (block != null && block.material.isLiquid()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean d(AxisAlignedBB axisalignedbb) {
        int i = MathHelper.floor(axisalignedbb.a);
        int j = MathHelper.floor(axisalignedbb.d + 1.0D);
        int k = MathHelper.floor(axisalignedbb.b);
        int l = MathHelper.floor(axisalignedbb.e + 1.0D);
        int i1 = MathHelper.floor(axisalignedbb.c);
        int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

        if (this.a(i, k, i1, j, l, j1)) {
            for (int k1 = i; k1 < j; ++k1) {
                for (int l1 = k; l1 < l; ++l1) {
                    for (int i2 = i1; i2 < j1; ++i2) {
                        int j2 = this.getTypeId(k1, l1, i2);

                        if (j2 == Block.FIRE.id || j2 == Block.LAVA.id || j2 == Block.STATIONARY_LAVA.id) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public boolean a(AxisAlignedBB axisalignedbb, Material material, Entity entity) {
        int i = MathHelper.floor(axisalignedbb.a);
        int j = MathHelper.floor(axisalignedbb.d + 1.0D);
        int k = MathHelper.floor(axisalignedbb.b);
        int l = MathHelper.floor(axisalignedbb.e + 1.0D);
        int i1 = MathHelper.floor(axisalignedbb.c);
        int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

        if (!this.a(i, k, i1, j, l, j1)) {
            return false;
        } else {
            boolean flag = false;
            Vec3D vec3d = Vec3D.create(0.0D, 0.0D, 0.0D);

            for (int k1 = i; k1 < j; ++k1) {
                for (int l1 = k; l1 < l; ++l1) {
                    for (int i2 = i1; i2 < j1; ++i2) {
                        Block block = Block.byId[this.getTypeId(k1, l1, i2)];

                        if (block != null && block.material == material) {
                            double d0 = (double) ((float) (l1 + 1) - BlockFluids.c(this.getData(k1, l1, i2)));

                            if ((double) l >= d0) {
                                flag = true;
                                block.a(this, k1, l1, i2, entity, vec3d);
                            }
                        }
                    }
                }
            }

            // Bypass water push for Creative players while flying (server-side approximation)
            boolean bypassWaterPush = false;
            if (material == Material.WATER && entity instanceof EntityHuman) {
                EntityHuman ph = (EntityHuman) entity;
                if (ph.gameMode == 1 && !entity.onGround) {
                    bypassWaterPush = true;
                }
            }

            if (vec3d.c() > 0.0D && !bypassWaterPush) {
                vec3d = vec3d.b();
                double d1 = 0.014D;

                entity.motX += vec3d.a * d1;
                entity.motY += vec3d.b * d1;
                entity.motZ += vec3d.c * d1;
            }

            return flag;
        }
    }

    public boolean a(AxisAlignedBB axisalignedbb, Material material) {
        int i = MathHelper.floor(axisalignedbb.a);
        int j = MathHelper.floor(axisalignedbb.d + 1.0D);
        int k = MathHelper.floor(axisalignedbb.b);
        int l = MathHelper.floor(axisalignedbb.e + 1.0D);
        int i1 = MathHelper.floor(axisalignedbb.c);
        int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    Block block = Block.byId[this.getTypeId(k1, l1, i2)];

                    if (block != null && block.material == material) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean b(AxisAlignedBB axisalignedbb, Material material) {
        int i = MathHelper.floor(axisalignedbb.a);
        int j = MathHelper.floor(axisalignedbb.d + 1.0D);
        int k = MathHelper.floor(axisalignedbb.b);
        int l = MathHelper.floor(axisalignedbb.e + 1.0D);
        int i1 = MathHelper.floor(axisalignedbb.c);
        int j1 = MathHelper.floor(axisalignedbb.f + 1.0D);

        for (int k1 = i; k1 < j; ++k1) {
            for (int l1 = k; l1 < l; ++l1) {
                for (int i2 = i1; i2 < j1; ++i2) {
                    Block block = Block.byId[this.getTypeId(k1, l1, i2)];

                    if (block != null && block.material == material) {
                        int j2 = this.getData(k1, l1, i2);
                        double d0 = (double) (l1 + 1);

                        if (j2 < 8) {
                            d0 = (double) (l1 + 1) - (double) j2 / 8.0D;
                        }

                        if (d0 >= axisalignedbb.b) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    public Explosion a(Entity entity, double d0, double d1, double d2, float f) {
        return this.createExplosion(entity, d0, d1, d2, f, false);
    }

    //Project Poseidon Start
    public Explosion createExplosion(Entity entity, double d0, double d1, double d2, float f, boolean flag, EntityDamageEvent.DamageCause customDamageCause) {
        Explosion explosion = new Explosion(this, entity, d0, d1, d2, f);
        explosion.customDamageCause = customDamageCause;

        explosion.setFire = flag;
        explosion.a();
        explosion.a(true);
        return explosion;
    }
    //Project Poseidon End

    public Explosion createExplosion(Entity entity, double d0, double d1, double d2, float f, boolean flag) {
        // Determine whether this explosion should damage blocks based on gamerules
        boolean allowBlockDamage = true;
        if (this.worldData != null) {
            // TNT explosions obey "tntexplodes" gamerule
            if (entity instanceof EntityTNTPrimed) {
                allowBlockDamage = this.worldData.getTntexplodes();
            }
            // Explosions caused by mobs (including fireballs from ghasts) obey "mobGriefing" gamerule
            else if (entity instanceof EntityLiving || entity instanceof EntityFireball) {
                allowBlockDamage = this.worldData.getMobGriefing();
            }
        }

        Explosion explosion = new Explosion(this, entity, d0, d1, d2, f);
        // If block damage is disabled (e.g., mobGriefing=false for ghast fireballs), do not place fire either
        explosion.setFire = flag && allowBlockDamage;
        explosion.a(); // Calculates damage to entities & collects affected blocks
        
        if (allowBlockDamage) {
            explosion.a(true); // Remove blocks + spawn particles
        } else {
            // If block damage is disabled, skip removing blocks but still play the sound/particles
            this.makeSound(d0, d1, d2, "random.explode", 4.0F, (1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.2F) * 0.7F);
        }
        return explosion;
    }

    public float a(Vec3D vec3d, AxisAlignedBB axisalignedbb) {
        double d0 = 1.0D / ((axisalignedbb.d - axisalignedbb.a) * 2.0D + 1.0D);
        double d1 = 1.0D / ((axisalignedbb.e - axisalignedbb.b) * 2.0D + 1.0D);
        double d2 = 1.0D / ((axisalignedbb.f - axisalignedbb.c) * 2.0D + 1.0D);
        int i = 0;
        int j = 0;

        for (float f = 0.0F; f <= 1.0F; f = (float) ((double) f + d0)) {
            for (float f1 = 0.0F; f1 <= 1.0F; f1 = (float) ((double) f1 + d1)) {
                for (float f2 = 0.0F; f2 <= 1.0F; f2 = (float) ((double) f2 + d2)) {
                    double d3 = axisalignedbb.a + (axisalignedbb.d - axisalignedbb.a) * (double) f;
                    double d4 = axisalignedbb.b + (axisalignedbb.e - axisalignedbb.b) * (double) f1;
                    double d5 = axisalignedbb.c + (axisalignedbb.f - axisalignedbb.c) * (double) f2;

                    if (this.a(Vec3D.create(d3, d4, d5), vec3d) == null) {
                        ++i;
                    }

                    ++j;
                }
            }
        }

        return (float) i / (float) j;
    }

    public void douseFire(EntityHuman entityhuman, int i, int j, int k, int l) {
        if (l == 0) {
            --j;
        }

        if (l == 1) {
            ++j;
        }

        if (l == 2) {
            --k;
        }

        if (l == 3) {
            ++k;
        }

        if (l == 4) {
            --i;
        }

        if (l == 5) {
            ++i;
        }

        if (this.getTypeId(i, j, k) == Block.FIRE.id) {
            this.a(entityhuman, 1004, i, j, k, 0);
            this.setTypeId(i, j, k, 0);
        }
    }

    public TileEntity getTileEntity(int i, int j, int k) {
        Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

        return chunk != null ? chunk.d(i & 15, j, k & 15) : null;
    }

    public void setTileEntity(int i, int j, int k, TileEntity tileentity) {
        if (!tileentity.g()) {
            if (this.L) {
                tileentity.x = i;
                tileentity.y = j;
                tileentity.z = k;
                this.G.add(tileentity);
            } else {
                // CraftBukkit - order matters, moved down
                // this.c.add(tileentity);
                Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

                if (chunk != null) {
                    chunk.placeTileEntity(i & 15, j, k & 15, tileentity);
                    this.c.add(tileentity); // CraftBukkit - moved in from above
                }
            }
        }
    }

    public void o(int i, int j, int k) {
        TileEntity tileentity = this.getTileEntity(i, j, k);

        if (tileentity != null && this.L) {
            tileentity.h();
        } else {
            if (tileentity != null) {
                this.c.remove(tileentity);
            }

            Chunk chunk = this.getChunkAt(i >> 4, k >> 4);

            if (chunk != null) {
                chunk.e(i & 15, j, k & 15);
            }
        }
    }

    public boolean p(int i, int j, int k) {
        Block block = Block.byId[this.getTypeId(i, j, k)];

        return block == null ? false : block.a();
    }

    public boolean e(int i, int j, int k) {
        Block block = Block.byId[this.getTypeId(i, j, k)];

        return block == null ? false : block.material.h() && block.b();
    }

    public boolean doLighting() {
        if (this.M >= 50) {
            return false;
        } else {
            ++this.M;

            boolean flag;

            try {
                int i = 500;

                while (this.C.size() > 0) {
                    --i;
                    if (i <= 0) {
                        flag = true;
                        return flag;
                    }

                    ((MetadataChunkBlock) this.C.remove(this.C.size() - 1)).a(this);
                }

                flag = false;
            } finally {
                --this.M;
            }

            return flag;
        }
    }

    public void a(EnumSkyBlock enumskyblock, int i, int j, int k, int l, int i1, int j1) {
        this.a(enumskyblock, i, j, k, l, i1, j1, true);
    }

    public void a(EnumSkyBlock enumskyblock, int i, int j, int k, int l, int i1, int j1, boolean flag) {
        if (!this.worldProvider.e || enumskyblock != EnumSkyBlock.SKY) {
            ++A;

            try {
                if (A == 50) {
                    return;
                }

                int k1 = (l + i) / 2;
                int l1 = (j1 + k) / 2;

                if (this.isLoaded(k1, 64, l1)) {
                    if (this.getChunkAtWorldCoords(k1, l1).isEmpty()) {
                        return;
                    }

                    int i2 = this.C.size();
                    int j2;

                    if (flag) {
                        j2 = 5;
                        if (j2 > i2) {
                            j2 = i2;
                        }

                        for (int k2 = 0; k2 < j2; ++k2) {
                            MetadataChunkBlock metadatachunkblock = (MetadataChunkBlock) this.C.get(this.C.size() - k2 - 1);

                            if (metadatachunkblock.a == enumskyblock && metadatachunkblock.a(i, j, k, l, i1, j1)) {
                                return;
                            }
                        }
                    }

                    this.C.add(new MetadataChunkBlock(enumskyblock, i, j, k, l, i1, j1));
                    j2 = 1000000;
                    if (this.C.size() > 1000000) {
                        System.out.println("More than " + j2 + " updates, aborting lighting updates");
                        this.C.clear();
                    }

                    return;
                }
            } finally {
                --A;
            }
        }
    }

    public void g() {
        int i = this.a(1.0F);

        if (i != this.f) {
            this.f = i;
        }
    }

    public void setSpawnFlags(boolean flag, boolean flag1) {
        this.allowMonsters = flag;
        this.allowAnimals = flag1;
    }

    public void doTick() {
		this.i(); // Update weather each tick (ensure precipitation logic runs)
        
        // Update Herobrine event manager
        HerobrineEventManager.getInstance(this).update();

        // Sleeping logic (advances time to morning if all players deeply sleeping)
        if (this.everyoneDeeplySleeping()) {
            boolean flag = false;
            if (this.allowMonsters && this.spawnMonsters >= 1) {
                flag = SpawnerCreature.a(this, this.players); // Original call
            }
            if (!flag) {
                long timeToAdvance = this.worldData.f() + 24000L;
                this.worldData.a(timeToAdvance - timeToAdvance % 24000L); // Set time to morning
                this.s(); // Wake players
            }
        }

        // CraftBukkit - Creature Spawning
        if ((this.allowMonsters || this.allowAnimals) && (this instanceof WorldServer && this.getServer().getHandle().players.size() > 0)) {
            SpawnerCreature.spawnEntities(this, this.allowMonsters, this.allowAnimals);
        }

        // Unload chunks
        this.chunkProvider.unloadChunks(); // Corrected from b() to unloadChunks()

        // Update light levels
        int j_light = this.a(1.0F); // skylightSubtracted - renamed to avoid conflict with loop var
        if (j_light != this.f) {    // currentSkyLightSubtracted
            this.f = j_light;
            for (int k_loop = 0; k_loop < this.u.size(); ++k_loop) { // u is IWorldAccess list
                ((IWorldAccess) this.u.get(k_loop)).a(); // Obfuscated: updateAllRenderers
            }
        }

        // Conditional Time Advancement & Periodic Save
        long currentTickTime = this.worldData.f(); // getTime()
        long nextTickTime = currentTickTime; 

        if (this.worldData.getDoDayNightCycle()) { // Check gamerule
            nextTickTime = currentTickTime + 1L; 
        }

        // Periodic save check decoupled from time-of-day so saves still occur when time is frozen
        this.saveTickCounter++;
        if (this.saveTickCounter >= this.p) { // p is worldTickFrequency
            this.save(false, (IProgressUpdate) null);
            this.saveTickCounter = 0;
        }

        this.blockTickTime++;
        this.worldData.a(nextTickTime); // setTime() using the (conditionally) incremented value

        this.a(false); // updateEntities (original call)
        this.j();      // tickBlocksAndAmbiance (original call)
    }

    private void x() {
        int terrainType = this.worldData != null ? this.worldData.getTerrainType() : 0;
        boolean isOverworld = (this.worldProvider != null && this.worldProvider.dimension == 0);
        boolean forceInfdevClear = isOverworld && terrainType == 7;
        boolean forceAlphaSnow = isOverworld && (terrainType == 5 || (terrainType == 1 && this.worldData != null && this.worldData.isSnowWorld()));
        if (forceInfdevClear) {
            this.worldData.setStorm(false);
            this.worldData.setWeatherDuration(0);
            this.worldData.setThundering(false);
            this.worldData.setThunderDuration(0);
            this.i = 0.0F;
            this.j = 0.0F;
            this.k = 0.0F;
            this.l = 0.0F;
            return;
        }

        if (forceAlphaSnow) {
            this.worldData.setStorm(true);
            this.worldData.setWeatherDuration(Integer.MAX_VALUE / 2);
            this.worldData.setThundering(false);
            this.worldData.setThunderDuration(0);
            this.i = 1.0F;
            this.j = 1.0F;
            this.k = 0.0F;
            this.l = 0.0F;
            return;
        }

        if (this.worldData.hasStorm()) {
            this.j = 1.0F;
            if (this.worldData.isThundering()) {
                this.l = 1.0F;
            }
        }
    }

    protected void i() {
		if (!this.worldProvider.e) {
			// Alpha/Alpha Snow parity: enforce perpetual weather states to match client
			int terrainType = this.worldData != null ? this.worldData.getTerrainType() : 0;
			boolean isOverworld = (this.worldProvider != null && this.worldProvider.dimension == 0);
			boolean isInfdevWorld = isOverworld && terrainType == 7;
			boolean isAlphaSnowWorld = isOverworld && (terrainType == 5 || (terrainType == 1 && this.worldData != null && this.worldData.isSnowWorld()));
			boolean isAlphaNormalWorld = isOverworld && (terrainType == 1 && (this.worldData == null || !this.worldData.isSnowWorld()));

			if (isInfdevWorld) {
				// INFDEV is strict clear-weather mode.
				this.worldData.setStorm(false);
				this.worldData.setWeatherDuration(0);
				this.worldData.setThundering(false);
				this.worldData.setThunderDuration(0);
				this.i = this.j;
				this.j = (float) Math.max(0.0D, (double) this.j - 0.01D);
				this.k = this.l;
				this.l = (float) Math.max(0.0D, (double) this.l - 0.01D);
				return;
			}

			if (isAlphaSnowWorld) {
				// Force endless snowfall (no thunder) so ALPHA_SNOW has zero chance of clear weather.
				this.worldData.setStorm(true);
				this.worldData.setWeatherDuration(Integer.MAX_VALUE / 2);
				this.worldData.setThundering(false);
				this.worldData.setThunderDuration(0);
				// Keep rain fully on every tick so this.v() always stays true (no transient clear state).
				this.i = this.j;
				this.j = 1.0F;
				this.k = this.l;
				this.l = 0.0F;
				return; // Skip vanilla toggling logic
			}

			if (isAlphaNormalWorld) {
				// Force clear weather in ALPHA (non-snow) worlds for parity with client visuals
				this.worldData.setStorm(false);
				this.worldData.setWeatherDuration(0);
				this.worldData.setThundering(false);
				this.worldData.setThunderDuration(0);
				// Smoothly ramp rain/thunder off
				this.i = this.j;
				this.j = (float) Math.max(0.0D, (double) this.j - 0.01D);
				this.k = this.l;
				this.l = (float) Math.max(0.0D, (double) this.l - 0.01D);
				return; // Skip vanilla toggling logic
			}
            if (this.m > 0) {
                --this.m;
            }

            int i = this.worldData.getThunderDuration();

            if (this.worldData.getDoWeatherCycle()) {
                if (i <= 0) {
                    if (this.worldData.isThundering()) {
                        this.worldData.setThunderDuration(this.random.nextInt(12000) + 3600);
                    } else {
                        this.worldData.setThunderDuration(this.random.nextInt(168000) + 12000);
                    }
                } else {
                    --i;
                    this.worldData.setThunderDuration(i);
                    if (i <= 0) {
                        // CraftBukkit start
                        ThunderChangeEvent thunder = new ThunderChangeEvent(this.getWorld(), !this.worldData.isThundering());
                        this.getServer().getPluginManager().callEvent(thunder);
                        if (!thunder.isCancelled()) {
                            this.worldData.setThundering(!this.worldData.isThundering());
                        }
                        // CraftBukkit end
                    }
                }
            }

            int j = this.worldData.getWeatherDuration();

            if (this.worldData.getDoWeatherCycle()) {
                if (j <= 0) {
                    if (this.worldData.hasStorm()) {
                        this.worldData.setWeatherDuration(this.random.nextInt(12000) + 12000);
                    } else {
                        this.worldData.setWeatherDuration(this.random.nextInt(168000) + 12000);
                    }
                } else {
                    --j;
                    this.worldData.setWeatherDuration(j);
                    if (j <= 0) {
                        // CraftBukkit start
                        WeatherChangeEvent weather = new WeatherChangeEvent(this.getWorld(), !this.worldData.hasStorm());
                        this.getServer().getPluginManager().callEvent(weather);

                        if (!weather.isCancelled()) {
                            this.worldData.setStorm(!this.worldData.hasStorm());
                        }
                        // CraftBukkit end
                    }
                }
            }

            this.i = this.j;
            if (this.worldData.hasStorm()) {
                this.j = (float) ((double) this.j + 0.01D);
            } else {
                this.j = (float) ((double) this.j - 0.01D);
            }

            if (this.j < 0.0F) {
                this.j = 0.0F;
            }

            if (this.j > 1.0F) {
                this.j = 1.0F;
            }

            this.k = this.l;
            if (this.worldData.isThundering()) {
                this.l = (float) ((double) this.l + 0.01D);
            } else {
                this.l = (float) ((double) this.l - 0.01D);
            }

            if (this.l < 0.0F) {
                this.l = 0.0F;
            }

            if (this.l > 1.0F) {
                this.l = 1.0F;
            }
        }
    }

    private void y() {
        int terrainType = this.worldData != null ? this.worldData.getTerrainType() : 0;
        boolean isOverworld = (this.worldProvider != null && this.worldProvider.dimension == 0);
        boolean forceInfdevClear = isOverworld && terrainType == 7;
        boolean forceAlphaSnow = isOverworld && (terrainType == 5 || (terrainType == 1 && this.worldData != null && this.worldData.isSnowWorld()));
        if (forceInfdevClear) {
            this.worldData.setWeatherDuration(0);
            this.worldData.setStorm(false);
            this.worldData.setThunderDuration(0);
            this.worldData.setThundering(false);
            this.i = 0.0F;
            this.j = 0.0F;
            this.k = 0.0F;
            this.l = 0.0F;
            return;
        }

        if (forceAlphaSnow) {
            // Sleeping should not clear weather in ALPHA_SNOW worlds.
            this.worldData.setWeatherDuration(Integer.MAX_VALUE / 2);
            this.worldData.setStorm(true);
            this.worldData.setThunderDuration(0);
            this.worldData.setThundering(false);
            this.i = 1.0F;
            this.j = 1.0F;
            this.k = 0.0F;
            this.l = 0.0F;
            return;
        }

        // CraftBukkit start
        WeatherChangeEvent weather = new WeatherChangeEvent(this.getWorld(), false);
        this.getServer().getPluginManager().callEvent(weather);

        ThunderChangeEvent thunder = new ThunderChangeEvent(this.getWorld(), false);
        this.getServer().getPluginManager().callEvent(thunder);
        if (!weather.isCancelled()) {
            this.worldData.setWeatherDuration(0);
            this.worldData.setStorm(false);
        }
        if (!thunder.isCancelled()) {
            this.worldData.setThunderDuration(0);
            this.worldData.setThundering(false);
        }
        // CraftBukkit end
    }

    protected void j() {
        this.P.clear();

        int i;
        int j;
        int k;
        int l;

        for (int i1 = 0; i1 < this.players.size(); ++i1) {
            EntityHuman entityhuman = (EntityHuman) this.players.get(i1);

            i = MathHelper.floor(entityhuman.locX / 16.0D);
            j = MathHelper.floor(entityhuman.locZ / 16.0D);
            byte b0 = 9;

            for (k = -b0; k <= b0; ++k) {
                for (l = -b0; l <= b0; ++l) {
                    this.P.add(new ChunkCoordIntPair(k + i, l + j));
                }
            }
        }

        if (this.Q > 0) {
            --this.Q;
        }

        Iterator iterator = this.P.iterator();

        while (iterator.hasNext()) {
            ChunkCoordIntPair chunkcoordintpair = (ChunkCoordIntPair) iterator.next();

            i = chunkcoordintpair.x * 16;
            j = chunkcoordintpair.z * 16;
            Chunk chunk = this.getChunkAt(chunkcoordintpair.x, chunkcoordintpair.z);
            int j1;
            int k1;
            int l1;

            if (this.Q == 0) {
                this.g = this.g * 3 + 1013904223;
                k = this.g >> 2;
                l = k & 15;
                j1 = k >> 8 & 15;
                k1 = k >> 16 & 127;
                l1 = chunk.getTypeId(l, k1, j1);
                l += i;
                j1 += j;
                if (l1 == 0 && this.k(l, k1, j1) <= this.random.nextInt(8) && this.a(EnumSkyBlock.SKY, l, k1, j1) <= 0) {
                    EntityHuman entityhuman1 = this.a((double) l + 0.5D, (double) k1 + 0.5D, (double) j1 + 0.5D, 8.0D);

                    if (entityhuman1 != null && entityhuman1.e((double) l + 0.5D, (double) k1 + 0.5D, (double) j1 + 0.5D) > 4.0D) {
                        this.makeSound((double) l + 0.5D, (double) k1 + 0.5D, (double) j1 + 0.5D, "ambient.cave.cave", 0.7F, 0.8F + this.random.nextFloat() * 0.2F);
                        this.Q = this.random.nextInt(12000) + 6000;
                    }
                }
            }

            if (this.random.nextInt(100000) == 0 && this.v() && this.u()) {
                this.g = this.g * 3 + 1013904223;
                k = this.g >> 2;
                l = i + (k & 15);
                j1 = j + (k >> 8 & 15);
                k1 = this.e(l, j1);
                if (this.s(l, k1, j1)) {
                    this.strikeLightning(new EntityWeatherStorm(this, (double) l, (double) k1, (double) j1));
                    this.m = 2;
                }
            }

            int i2;

            if (this.random.nextInt(16) == 0) {
                this.g = this.g * 3 + 1013904223;
                k = this.g >> 2;
                l = k & 15;
                j1 = k >> 8 & 15;
                k1 = this.e(l + i, j1 + j);
                // Treat ALPHA_SNOW worlds as permanently cold for precipitation effects
                boolean isColdOrAlphaSnow = this.getWorldChunkManager().getBiome(l + i, j1 + j).c() || (this.worldData != null && this.worldData.isSnowWorld());
                if (isColdOrAlphaSnow && k1 >= 0 && k1 < 128 && chunk.a(EnumSkyBlock.BLOCK, l, k1, j1) < 10) {
                    l1 = chunk.getTypeId(l, k1 - 1, j1);
                    i2 = chunk.getTypeId(l, k1, j1);
                    if (this.v() && i2 == 0 && Block.SNOW.canPlace(this, l + i, k1, j1 + j) && l1 != 0 && l1 != Block.ICE.id && Block.byId[l1].material.isSolid()) {
                        // CraftBukkit start
                        BlockState blockState = this.getWorld().getBlockAt(l + i, k1, j1 + j).getState();
                        blockState.setTypeId(Block.SNOW.id);

                        BlockFormEvent snow = new BlockFormEvent(blockState.getBlock(), blockState);
                        this.getServer().getPluginManager().callEvent(snow);
                        if (!snow.isCancelled()) {
                            blockState.update(true);
                        }
                        // CraftBukkit end
                    }

                    // CraftBukkit start
                        if (l1 == Block.STATIONARY_WATER.id && chunk.getData(l, k1 - 1, j1) == 0) {
                            BlockState blockState = this.getWorld().getBlockAt(l + i, k1 - 1, j1 + j).getState();
                            blockState.setTypeId(Block.ICE.id);

                            BlockFormEvent iceBlockForm = new BlockFormEvent(blockState.getBlock(), blockState);
                            this.getServer().getPluginManager().callEvent(iceBlockForm);
                            if (!iceBlockForm.isCancelled()) {
                                blockState.update(true);
                            }
                    }
                    // CraftBukkit end
                }
            }

            for (k = 0; k < 80; ++k) {
                this.g = this.g * 3 + 1013904223;
                l = this.g >> 2;
                j1 = l & 15;
                k1 = l >> 8 & 15;
                l1 = l >> 16 & 127;
                i2 = chunk.b[j1 << 11 | k1 << 7 | l1] & 255;
                if (Block.n[i2]) {
                    Block.byId[i2].a(this, j1 + i, l1, k1 + j, this.random);
                }
            }
        }
    }

    public boolean a(boolean flag) {
        int i = this.E.size();

        if (i != this.F.size()) {
            throw new IllegalStateException("TickNextTick list out of synch");
        } else {
            if (i > 1000) {
                i = 1000;
            }

            for (int j = 0; j < i; ++j) {
                NextTickListEntry nextticklistentry = (NextTickListEntry) this.E.first();

                if (!flag && nextticklistentry.e > this.blockTickTime) {
                    break;
                }

                this.E.remove(nextticklistentry);
                this.F.remove(nextticklistentry);
                this.unindexScheduledTick(nextticklistentry);
                byte b0 = 8;

                if (this.a(nextticklistentry.a - b0, nextticklistentry.b - b0, nextticklistentry.c - b0, nextticklistentry.a + b0, nextticklistentry.b + b0, nextticklistentry.c + b0)) {
                    int k = this.getTypeId(nextticklistentry.a, nextticklistentry.b, nextticklistentry.c);

                    if (k == nextticklistentry.d && k > 0) {
                        Block.byId[k].a(this, nextticklistentry.a, nextticklistentry.b, nextticklistentry.c, this.random);
                    }
                }
            }

            return this.E.size() != 0;
        }
    }

    public List b(Entity entity, AxisAlignedBB axisalignedbb) {
        this.R.clear();
        int i = MathHelper.floor((axisalignedbb.a - 2.0D) / 16.0D);
        int j = MathHelper.floor((axisalignedbb.d + 2.0D) / 16.0D);
        int k = MathHelper.floor((axisalignedbb.c - 2.0D) / 16.0D);
        int l = MathHelper.floor((axisalignedbb.f + 2.0D) / 16.0D);

        for (int i1 = i; i1 <= j; ++i1) {
            for (int j1 = k; j1 <= l; ++j1) {
                if (this.isChunkLoaded(i1, j1)) {
                    this.getChunkAt(i1, j1).a(entity, axisalignedbb, this.R);
                }
            }
        }

        return this.R;
    }

    public List a(Class oclass, AxisAlignedBB axisalignedbb) {
        int i = MathHelper.floor((axisalignedbb.a - 2.0D) / 16.0D);
        int j = MathHelper.floor((axisalignedbb.d + 2.0D) / 16.0D);
        int k = MathHelper.floor((axisalignedbb.c - 2.0D) / 16.0D);
        int l = MathHelper.floor((axisalignedbb.f + 2.0D) / 16.0D);
        ArrayList arraylist = new ArrayList();

        for (int i1 = i; i1 <= j; ++i1) {
            for (int j1 = k; j1 <= l; ++j1) {
                if (this.isChunkLoaded(i1, j1)) {
                    this.getChunkAt(i1, j1).a(oclass, axisalignedbb, arraylist);
                }
            }
        }

        return arraylist;
    }

    public void b(int i, int j, int k, TileEntity tileentity) {
        if (this.isLoaded(i, j, k)) {
            this.getChunkAtWorldCoords(i, k).f();
        }

        for (int l = 0; l < this.u.size(); ++l) {
            ((IWorldAccess) this.u.get(l)).a(i, j, k, tileentity);
        }
    }

    public int a(Class oclass) {
        int i = 0;

        for (int j = 0; j < this.entityList.size(); ++j) {
            Entity entity = (Entity) this.entityList.get(j);

            if (oclass.isAssignableFrom(entity.getClass())) {
                ++i;
            }
        }

        return i;
    }

    public void a(List list) {
        // CraftBukkit start
        Entity entity = null;
        for (int i = 0; i < list.size(); ++i) {
            entity = (Entity) list.get(i);
            // CraftBukkit start - fixed an NPE
            if (entity == null) {
                continue;
            }
            // CraftBukkit end
            this.entityList.add(entity);
            // CraftBukkit end
            this.c((Entity) list.get(i));
        }
    }

    public void b(List list) {
        this.D.addAll(list);
    }

    public boolean a(int i, int j, int k, int l, boolean flag, int i1) {
        int j1 = this.getTypeId(j, k, l);
        Block block = Block.byId[j1];
        Block block1 = Block.byId[i];
        AxisAlignedBB axisalignedbb = block1.e(this, j, k, l);

        if (flag) {
            axisalignedbb = null;
        }

        boolean defaultReturn; // CraftBukkit - store the default action

        if (axisalignedbb != null && !this.containsEntity(axisalignedbb)) {
            defaultReturn = false; // CraftBukkit
        } else {
            if (block == Block.WATER || block == Block.STATIONARY_WATER || block == Block.LAVA || block == Block.STATIONARY_LAVA || block == Block.FIRE || block == Block.SNOW) {
                block = null;
            }

            defaultReturn = i > 0 && block == null && block1.canPlace(this, j, k, l, i1); // CraftBukkit
        }

        // CraftBukkit start
        BlockCanBuildEvent event = new BlockCanBuildEvent(this.getWorld().getBlockAt(j, k, l), i, defaultReturn);
        this.getServer().getPluginManager().callEvent(event);

        return event.isBuildable();
        // CraftBukkit end
    }

    public PathEntity findPath(Entity entity, Entity entity1, float f) {
        if (entity == null || entity1 == null) {
            return null;
        }
        int i = MathHelper.floor(entity.locX);
        int j = MathHelper.floor(entity.locY);
        int k = MathHelper.floor(entity.locZ);
        int l = (int) (f + 16.0F);
        int i1 = i - l;
        int j1 = j - l;
        int k1 = k - l;
        int l1 = i + l;
        int i2 = j + l;
        int j2 = k + l;
        ChunkCache chunkcache = new ChunkCache(this, i1, j1, k1, l1, i2, j2);

        return (new Pathfinder(chunkcache)).a(entity, entity1, f);
    }

    public PathEntity a(Entity entity, int i, int j, int k, float f) {
        int l = MathHelper.floor(entity.locX);
        int i1 = MathHelper.floor(entity.locY);
        int j1 = MathHelper.floor(entity.locZ);
        int k1 = (int) (f + 8.0F);
        int l1 = l - k1;
        int i2 = i1 - k1;
        int j2 = j1 - k1;
        int k2 = l + k1;
        int l2 = i1 + k1;
        int i3 = j1 + k1;
        ChunkCache chunkcache = new ChunkCache(this, l1, i2, j2, k2, l2, i3);

        return (new Pathfinder(chunkcache)).a(entity, i, j, k, f);
    }

    private int getBlockFacePowerLevelTo(int i, int j, int k, int l) {
        int blockId = this.getTypeId(i, j, k);
        Block block = blockId > 0 && blockId < Block.byId.length ? Block.byId[blockId] : null;

        if (block == null) {
            return 0;
        }

        // Preserve vanilla wire semantics while wire is recalculating.
        if (block == Block.REDSTONE_WIRE) {
            return block.d(this, i, j, k, l) ? this.getData(i, j, k) : 0;
        }

        return BlockCapabilityRegistryApi.getDirectRedstonePower(this, i, j, k, l);
    }

    private int getMaxDirectPowerAt(int i, int j, int k) {
        int power = this.getBlockFacePowerLevelTo(i, j - 1, k, 0);
        if (power >= 15) {
            return 15;
        }

        int check = this.getBlockFacePowerLevelTo(i, j + 1, k, 1);
        if (check > power) {
            power = check;
        }
        if (power >= 15) {
            return 15;
        }

        check = this.getBlockFacePowerLevelTo(i, j, k - 1, 2);
        if (check > power) {
            power = check;
        }
        if (power >= 15) {
            return 15;
        }

        check = this.getBlockFacePowerLevelTo(i, j, k + 1, 3);
        if (check > power) {
            power = check;
        }
        if (power >= 15) {
            return 15;
        }

        check = this.getBlockFacePowerLevelTo(i - 1, j, k, 4);
        if (check > power) {
            power = check;
        }
        if (power >= 15) {
            return 15;
        }

        check = this.getBlockFacePowerLevelTo(i + 1, j, k, 5);
        if (check > power) {
            power = check;
        }

        return power;
    }

    private int getBlockFaceIndirectPowerLevel(int i, int j, int k, int l) {
        int blockId = this.getTypeId(i, j, k);
        Block block = blockId > 0 && blockId < Block.byId.length ? Block.byId[blockId] : null;

        if (block == null) {
            return 0;
        }

        // Preserve vanilla wire semantics while wire is recalculating.
        if (block == Block.REDSTONE_WIRE) {
            return block.a(this, i, j, k, l) ? this.getData(i, j, k) : 0;
        }

        // Capability-driven full-cube sources (e.g. redstone block) must emit even when opaque.
        // Keep non-opaque sources (levers/buttons/torches) on the indirect-power path.
        if (BlockCapabilityRegistryApi.isRedstonePowerSource(block) && this.e(i, j, k)) {
            return BlockCapabilityRegistryApi.getDirectRedstonePower(this, i, j, k, l);
        }

        if (this.e(i, j, k)) {
            return this.getMaxDirectPowerAt(i, j, k);
        }

        return BlockCapabilityRegistryApi.getIndirectRedstonePower(this, i, j, k, l);
    }

    public int getMaxIndirectPowerAt(int i, int j, int k) {
        int power = this.getBlockFaceIndirectPowerLevel(i, j - 1, k, 0);
        if (power >= 15) {
            return 15;
        }

        int check = this.getBlockFaceIndirectPowerLevel(i, j + 1, k, 1);
        if (check > power) {
            power = check;
        }
        if (power >= 15) {
            return 15;
        }

        check = this.getBlockFaceIndirectPowerLevel(i, j, k - 1, 2);
        if (check > power) {
            power = check;
        }
        if (power >= 15) {
            return 15;
        }

        check = this.getBlockFaceIndirectPowerLevel(i, j, k + 1, 3);
        if (check > power) {
            power = check;
        }
        if (power >= 15) {
            return 15;
        }

        check = this.getBlockFaceIndirectPowerLevel(i - 1, j, k, 4);
        if (check > power) {
            power = check;
        }
        if (power >= 15) {
            return 15;
        }

        check = this.getBlockFaceIndirectPowerLevel(i + 1, j, k, 5);
        if (check > power) {
            power = check;
        }

        return power;
    }

    public boolean isBlockFacePowered(int i, int j, int k, int l) {
        return this.getBlockFacePowerLevelTo(i, j, k, l) > 0;
    }

    public boolean isBlockPowered(int i, int j, int k) {
        return this.getMaxDirectPowerAt(i, j, k) > 0;
    }

    public boolean isBlockFaceIndirectlyPowered(int i, int j, int k, int l) {
        return this.getBlockFaceIndirectPowerLevel(i, j, k, l) > 0;
    }

    public boolean isBlockIndirectlyPowered(int i, int j, int k) {
        return this.getMaxIndirectPowerAt(i, j, k) > 0;
    }

    public EntityHuman findNearbyPlayer(Entity entity, double d0) {
        return this.a(entity.locX, entity.locY, entity.locZ, d0);
    }

    public EntityHuman a(double d0, double d1, double d2, double d3) {
        double d4 = -1.0D;
        EntityHuman entityhuman = null;

        for (int i = 0; i < this.players.size(); ++i) {
            EntityHuman entityhuman1 = (EntityHuman) this.players.get(i);
            // CraftBukkit start - fixed an NPE
            if (entityhuman1 == null || entityhuman1.dead) {
                continue;
            }
            // CraftBukkit end
            double d5 = entityhuman1.e(d0, d1, d2);

            if ((d3 < 0.0D || d5 < d3 * d3) && (d4 == -1.0D || d5 < d4)) {
                d4 = d5;
                entityhuman = entityhuman1;
            }
        }

        return entityhuman;
    }

    public EntityHuman a(String s) {
        for (int i = 0; i < this.players.size(); ++i) {
            if (s.equals(((EntityHuman) this.players.get(i)).name)) {
                return (EntityHuman) this.players.get(i);
            }
        }

        return null;
    }

    public byte[] getMultiChunkData(int i, int j, int k, int l, int i1, int j1) {
        byte[] abyte = new byte[l * i1 * j1 * 5 / 2];
        int k1 = i >> 4;
        int l1 = k >> 4;
        int i2 = i + l - 1 >> 4;
        int j2 = k + j1 - 1 >> 4;
        int k2 = 0;
        int l2 = j;
        int i3 = j + i1;

        if (j < 0) {
            l2 = 0;
        }

        if (i3 > 128) {
            i3 = 128;
        }

        for (int j3 = k1; j3 <= i2; ++j3) {
            int k3 = i - j3 * 16;
            int l3 = i + l - j3 * 16;

            if (k3 < 0) {
                k3 = 0;
            }

            if (l3 > 16) {
                l3 = 16;
            }

            for (int i4 = l1; i4 <= j2; ++i4) {
                int j4 = k - i4 * 16;
                int k4 = k + j1 - i4 * 16;

                if (j4 < 0) {
                    j4 = 0;
                }

                if (k4 > 16) {
                    k4 = 16;
                }

                k2 = this.getChunkAt(j3, i4).getData(abyte, k3, l2, j4, l3, i3, k4, k2);
            }
        }

        return abyte;
    }

    public void k() {
        this.w.b();
    }

    public void setTime(long i) {
        this.worldData.a(i);
        this.notifyWallClockTimeChange();
    }

    private void notifyWallClockTimeChange() {
        if (this.isStatic || Block.WALL_CLOCK == null || !(this.chunkProvider instanceof ChunkProviderServer)) {
            return;
        }

        ChunkProviderServer chunkproviderserver = (ChunkProviderServer) this.chunkProvider;
        for (int chunkIndex = 0; chunkIndex < chunkproviderserver.chunkList.size(); ++chunkIndex) {
            Chunk chunk = (Chunk) chunkproviderserver.chunkList.get(chunkIndex);
            if (chunk == null || chunk == chunkproviderserver.emptyChunk) {
                continue;
            }

            int chunkBaseX = chunk.x << 4;
            int chunkBaseZ = chunk.z << 4;
            for (int localX = 0; localX < 16; ++localX) {
                for (int localZ = 0; localZ < 16; ++localZ) {
                    for (int y = 0; y < 128; ++y) {
                        if (chunk.getTypeId(localX, y, localZ) != Block.WALL_CLOCK.id) {
                            continue;
                        }

                        int worldX = chunkBaseX + localX;
                        int worldZ = chunkBaseZ + localZ;
                        this.applyPhysics(worldX, y, worldZ, Block.WALL_CLOCK.id);
                    }
                }
            }
        }
    }

    public void setTimeAndFixTicklists(long i) {
        this.setTime(i);
    }

    public long getSeed() {
        return this.worldData.getSeed();
    }

    public long getTime() {
        return this.worldData.f();
    }

    public ChunkCoordinates getSpawn() {
        return new ChunkCoordinates(this.worldData.c(), this.worldData.d(), this.worldData.e());
    }

    public boolean a(EntityHuman entityhuman, int i, int j, int k) {
        return true;
    }

    public void a(Entity entity, byte b0) {}

    public IChunkProvider o() {
        return this.chunkProvider;
    }

    public void playNote(int i, int j, int k, int l, int i1) {
        int j1 = this.getTypeId(i, j, k);

        if (j1 > 0) {
            Block.byId[j1].a(this, i, j, k, l, i1);
        }
    }

    public IDataManager p() {
        return this.w;
    }

    public WorldData q() {
        return this.worldData;
    }

    public void everyoneSleeping() {
        this.J = !this.players.isEmpty();
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            EntityHuman entityhuman = (EntityHuman) iterator.next();

            // CraftBukkit
            if (!entityhuman.isSleeping() && !entityhuman.fauxSleeping) {
                this.J = false;
                break;
            }
        }
    }

    // CraftBukkit start
    // Calls the method that checks to see if players are sleeping
    // Called by CraftPlayer.setPermanentSleeping()
    public void checkSleepStatus() {
        if (!this.isStatic) {
            this.everyoneSleeping();
        }
    }
    // CraftBukkit end

    protected void s() {
        this.J = false;
        Iterator iterator = this.players.iterator();

        while (iterator.hasNext()) {
            EntityHuman entityhuman = (EntityHuman) iterator.next();

            if (entityhuman.isSleeping()) {
                entityhuman.a(false, false, true);
            }
        }

        this.y();
    }

    public boolean everyoneDeeplySleeping() {
        if (this.J && !this.isStatic) {
            Iterator iterator = this.players.iterator();

            // CraftBukkit - This allows us to assume that some people are in bed but not really, allowing time to pass in spite of AFKers
            boolean foundActualSleepers = false;

            EntityHuman entityhuman;

            do {
                if (!iterator.hasNext()) {
                    // CraftBukkit
                    return foundActualSleepers;
                }

                entityhuman = (EntityHuman) iterator.next();
                // CraftBukkit start
                if (entityhuman.isDeeplySleeping()) {
                    foundActualSleepers = true;
                }
            } while (entityhuman.isDeeplySleeping() || entityhuman.fauxSleeping);
            // CraftBukkit end

            return false;
        } else {
            return false;
        }
    }

    public float c(float f) {
        return (this.k + (this.l - this.k) * f) * this.d(f);
    }

    public float d(float f) {
        return this.i + (this.j - this.i) * f;
    }

    private boolean isInfdevTerrainWorld() {
        return this.worldData != null && this.worldData.getTerrainType() == 7;
    }

    public boolean u() {
        if (this.isInfdevTerrainWorld()) {
            return false;
        }
        return (double) this.c(1.0F) > 0.9D;
    }

    public boolean v() {
        if (this.isInfdevTerrainWorld()) {
            return false;
        }
        return (double) this.d(1.0F) > 0.2D;
    }

    public boolean s(int i, int j, int k) {
        if (!this.v()) {
            return false;
        } else if (!this.isChunkLoaded(i, j, k)) {
            return false;
        } else if (this.e(i, k) > j) {
            return false;
        } else {
            BiomeBase biomebase = this.getWorldChunkManager().getBiome(i, k);

            return biomebase.c() ? false : biomebase.d();
        }
    }

    public void a(String s, WorldMapBase worldmapbase) {
        this.worldMaps.a(s, worldmapbase);
    }

    public WorldMapBase a(Class oclass, String s) {
        return this.worldMaps.a(oclass, s);
    }

    public int b(String s) {
        return this.worldMaps.a(s);
    }

    public void e(int i, int j, int k, int l, int i1) {
        this.a((EntityHuman) null, i, j, k, l, i1);
    }

    public void a(EntityHuman entityhuman, int i, int j, int k, int l, int i1) {
        for (int j1 = 0; j1 < this.u.size(); ++j1) {
            ((IWorldAccess) this.u.get(j1)).a(entityhuman, i, j, k, l, i1);
        }
    }

    // CraftBukkit start
    public UUID getUUID() {
        return this.w.getUUID();
    }
    // CraftBukkit end
}

package net.minecraft.server.threading;

import net.minecraft.server.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Truly asynchronous chunk generation system.
 * Uses thread-safe generators that don't access shared world state.
 * 
 * Flow:
 * 1. Main thread requests chunk generation
 * 2. Worker thread generates raw terrain data (blocks, heightmap) 
 * 3. Main thread receives data and creates actual Chunk object
 * 4. Main thread handles population (trees, ores, entities)
 */
public class AsyncChunkGenerator {
    
    private static final Logger log = Logger.getLogger("Minecraft");
    
    private final WorldServer world;
    private final long worldSeed;
    private final IThreadSafeGenerator generator;
    private final ExecutorService executor;
    private final ConcurrentHashMap<Long, CompletableFuture<ChunkGenerationData>> pendingChunks;
    private final ConcurrentHashMap<Long, ChunkGenerationData> completedChunks;
    private final AtomicInteger activeGenerations;
    
    private volatile boolean shutdown = false;
    private final int maxConcurrentGenerations;
    
    // Statistics
    private final AtomicInteger chunksGenerated = new AtomicInteger(0);
    private final AtomicInteger chunksQueued = new AtomicInteger(0);
    
    public AsyncChunkGenerator(WorldServer world, int threadCount) {
        this.world = world;
        this.worldSeed = world.getSeed();
        this.maxConcurrentGenerations = threadCount * 4;
        this.pendingChunks = new ConcurrentHashMap<>();
        this.completedChunks = new ConcurrentHashMap<>();
        this.activeGenerations = new AtomicInteger(0);
        
        // Select thread-safe generator based on world type
        this.generator = selectGenerator(world);
        
        // Create thread pool with named threads
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger threadNum = new AtomicInteger(0);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "ChunkGen-" + world.worldData.name + "-" + threadNum.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        };
        
        this.executor = Executors.newFixedThreadPool(threadCount, factory);
        log.info("[AsyncChunk] Started " + threadCount + " chunk generation threads for world '" + 
                 world.worldData.name + "' using " + generator.getGeneratorName());
    }

    public static boolean isTerrainTypeSupported(int terrainType) {
        return terrainType == 0 || terrainType == 2;
    }

    public static String terrainTypeName(int terrainType) {
        if (terrainType == 0) {
            return "DEFAULT";
        }

        if (terrainType == 1) {
            return "ALPHA";
        }

        if (terrainType == 2) {
            return "FLAT";
        }

        if (terrainType == 3) {
            return "SKY";
        }

        if (terrainType == 5) {
            return "ALPHA_SNOW";
        }

        if (terrainType == 6) {
            return "CLASSIC";
        }

        if (terrainType == 7) {
            return "INFDEV";
        }

        return "UNKNOWN";
    }
    
    /**
     * Select the appropriate thread-safe generator for this world.
     */
    private IThreadSafeGenerator selectGenerator(WorldServer world) {
        int terrainType = world.worldData.getTerrainType();
        
        if (terrainType == 0) { // DEFAULT
            return new ThreadSafeDefaultGenerator();
        } else if (terrainType == 2) { // FLAT
            return new ThreadSafeFlatGenerator();
        }

        throw new IllegalArgumentException("Unsupported async terrain type: " + terrainType +
                " (" + terrainTypeName(terrainType) + ")");
    }
    
    /**
     * Request async generation of a chunk.
     * Returns a future that completes with the raw chunk data.
     */
    public CompletableFuture<ChunkGenerationData> requestChunkAsync(int x, int z) {
        if (shutdown) {
            return CompletableFuture.completedFuture(null);
        }
        
        long key = chunkKey(x, z);
        
        // Check if already completed
        ChunkGenerationData ready = completedChunks.get(key);
        if (ready != null) {
            return CompletableFuture.completedFuture(ready);
        }
        
        // Check if already pending
        CompletableFuture<ChunkGenerationData> existing = pendingChunks.get(key);
        if (existing != null) {
            return existing;
        }
        
        // Check capacity
        if (activeGenerations.get() >= maxConcurrentGenerations) {
            return null; // Caller should generate synchronously
        }
        
        // Create and submit task
        CompletableFuture<ChunkGenerationData> future = new CompletableFuture<>();
        CompletableFuture<ChunkGenerationData> previous = pendingChunks.putIfAbsent(key, future);
        if (previous != null) {
            return previous;
        }
        
        activeGenerations.incrementAndGet();
        chunksQueued.incrementAndGet();
        
        final int chunkX = x;
        final int chunkZ = z;
        
        executor.submit(() -> {
            try {
                if (shutdown) {
                    future.complete(null);
                    return;
                }
                
                // Generate chunk data using thread-safe generator
                ChunkGenerationData data = generator.generateChunkData(worldSeed, chunkX, chunkZ);
                
                if (data != null) {
                    completedChunks.put(key, data);
                    chunksGenerated.incrementAndGet();
                }
                
                future.complete(data);
            } catch (Exception e) {
                log.log(Level.WARNING, "[AsyncChunk] Error generating chunk at " + chunkX + "," + chunkZ, e);
                future.completeExceptionally(e);
            } finally {
                activeGenerations.decrementAndGet();
                pendingChunks.remove(key);
            }
        });
        
        return future;
    }
    
    /**
     * Poll for a completed chunk. Call from main thread.
     * Returns null if chunk is not ready.
     */
    public ChunkGenerationData pollCompletedChunk(int x, int z) {
        long key = chunkKey(x, z);
        return completedChunks.remove(key);
    }
    
    /**
     * Check if a chunk is currently being generated.
     */
    public boolean isChunkPending(int x, int z) {
        long key = chunkKey(x, z);
        return pendingChunks.containsKey(key);
    }
    
    /**
     * Check if a chunk has completed generation.
     */
    public boolean isChunkReady(int x, int z) {
        long key = chunkKey(x, z);
        return completedChunks.containsKey(key);
    }
    
    /**
     * Apply generated data to a Chunk object.
     * MUST be called on main thread.
     */
    public void applyDataToChunk(Chunk chunk, ChunkGenerationData data) {
        if (data == null) return;
        
        // Copy block data
        System.arraycopy(data.blocks, 0, chunk.b, 0, Math.min(data.blocks.length, chunk.b.length));
        
        // Copy metadata (nibble array) - chunk.e is the data/metadata array
        if (chunk.e != null && data.metadata != null) {
            System.arraycopy(data.metadata, 0, chunk.e.a, 0, Math.min(data.metadata.length, chunk.e.a.length));
        }
        
        // Copy heightmap
        if (chunk.heightMap != null && data.heightMap != null) {
            System.arraycopy(data.heightMap, 0, chunk.heightMap, 0, Math.min(data.heightMap.length, chunk.heightMap.length));
        }
        
        // Mark chunk for lighting calculation
        chunk.initLighting();
    }
    
    // Helper method to create chunk key
    private static long chunkKey(int x, int z) {
        return ((long) x << 32) + z - Integer.MIN_VALUE;
    }
    
    public int getActiveGenerations() {
        return activeGenerations.get();
    }
    
    public int getReadyChunks() {
        return completedChunks.size();
    }
    
    public int getTotalChunksGenerated() {
        return chunksGenerated.get();
    }
    
    public void shutdown() {
        shutdown = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
        
        pendingChunks.clear();
        completedChunks.clear();
        log.info("[AsyncChunk] Shutdown chunk generator for world '" + world.worldData.name + 
                 "'. Generated " + chunksGenerated.get() + " chunks total.");
    }
}

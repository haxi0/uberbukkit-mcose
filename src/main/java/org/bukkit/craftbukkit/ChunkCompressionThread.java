package org.bukkit.craftbukkit;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Packet;
import net.minecraft.server.Packet202MapChunkZstd;
import net.minecraft.server.Packet51MapChunk;
import net.minecraft.server.ZstdRuntime;
import net.minecraft.server.threading.ThreadingConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.Deflater;

public final class ChunkCompressionThread {

    private static final ChunkCompressionThread instance = new ChunkCompressionThread();
    private static final int CHUNK_SIZE = 16 * 128 * 16 * 5 / 2;
    private static final int REDUCED_DEFLATE_THRESHOLD = CHUNK_SIZE / 4;
    private static final int DEFLATE_LEVEL_CHUNKS = 6;
    private static final int DEFLATE_LEVEL_PARTS = 1;
    private static final int ZSTD_LEVEL = 3;
    private static final int METRIC_SAMPLE_RING_SIZE = 512;

    private final Object lifecycleLock = new Object();
    private final HashMap<EntityPlayer, Integer> queueSizePerPlayer = new HashMap<EntityPlayer, Integer>();
    private final AtomicInteger totalQueuedPackets = new AtomicInteger(0);
    private final AtomicLong zstdChunkPacketsSent = new AtomicLong(0L);
    private final AtomicLong zlibChunkPacketsSent = new AtomicLong(0L);
    private final AtomicLong zstdFallbackCount = new AtomicLong(0L);
    private final AtomicLong compressionFailureCount = new AtomicLong(0L);
    private final AtomicLong netChunkZstdPacketsTotal = new AtomicLong(0L);
    private final AtomicLong netChunkZlibPacketsTotal = new AtomicLong(0L);
    private final AtomicLong netChunkZstdFallbackTotal = new AtomicLong(0L);
    private final AtomicLong netChunkCompressionFailuresTotal = new AtomicLong(0L);
    private final AtomicLong netChunkZstdCompressNanosTotal = new AtomicLong(0L);
    private final AtomicLong netChunkZlibCompressNanosTotal = new AtomicLong(0L);
    private final Object metricsLock = new Object();
    private final long[] compressionSampleNanos = new long[METRIC_SAMPLE_RING_SIZE];
    private int compressionSampleWriteIndex = 0;
    private int compressionSampleCount = 0;

    private volatile boolean running = false;
    private Worker[] workers = new Worker[0];
    private int workerCount = 1;
    private int queueCapacityPerWorker = 1024;
    private int totalQueueCapacity = 1024;
    private int highWatermarkSize = 870;
    private int lowWatermarkSize = 512;

    public static void startThread() {
        instance.startWorkers();
    }

    public static void stopThread() {
        instance.stopWorkers();
    }

    private void startWorkers() {
        synchronized (this.lifecycleLock) {
            if (this.running) {
                return;
            }

            ThreadingConfig config = ThreadingConfig.getInstance();
            this.workerCount = Math.max(1, config.getChunkCompressionThreads());
            int configuredCapacity = Math.max(128, config.getChunkCompressionQueueCapacity());
            this.queueCapacityPerWorker = Math.max(16, configuredCapacity / this.workerCount);
            this.totalQueueCapacity = this.queueCapacityPerWorker * this.workerCount;

            int lowPercent = clamp(config.getChunkCompressionLowWatermarkPercent(), 1, 100);
            int highPercent = clamp(config.getChunkCompressionHighWatermarkPercent(), lowPercent, 100);
            this.lowWatermarkSize = Math.max(1, (this.totalQueueCapacity * lowPercent) / 100);
            this.highWatermarkSize = Math.max(this.lowWatermarkSize, (this.totalQueueCapacity * highPercent) / 100);

            this.workers = new Worker[this.workerCount];
            this.running = true;

            for (int i = 0; i < this.workerCount; i++) {
                Worker worker = new Worker("ChunkCompression-" + (i + 1), this.queueCapacityPerWorker);
                this.workers[i] = worker;
                worker.thread.start();
            }
        }
    }

    private void stopWorkers() {
        Worker[] workersToStop;
        synchronized (this.lifecycleLock) {
            if (!this.running) {
                return;
            }

            this.running = false;
            workersToStop = this.workers;
            this.workers = new Worker[0];
        }

        for (int i = 0; i < workersToStop.length; i++) {
            workersToStop[i].shutdown();
        }

        synchronized (this.queueSizePerPlayer) {
            this.queueSizePerPlayer.clear();
        }
        this.totalQueuedPackets.set(0);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return value > max ? max : value;
    }

    private void handleQueuedPacket(QueuedPacket queuedPacket, Worker worker) {
        try {
            Packet outbound = prepareMapChunkPacket((Packet51MapChunk) queuedPacket.packet, queuedPacket.player, worker);
            sendToNetworkQueue(queuedPacket.player, outbound);
        } finally {
            addToPlayerQueueSize(queuedPacket.player, -1);
            this.totalQueuedPackets.decrementAndGet();
        }
    }

    private Packet prepareMapChunkPacket(Packet51MapChunk source, EntityPlayer player, Worker worker) {
        if (source == null) {
            this.compressionFailureCount.incrementAndGet();
            this.netChunkCompressionFailuresTotal.incrementAndGet();
            return null;
        }

        long start = System.nanoTime();
        try {
            boolean supportsZstd = player != null
                    && player.netServerHandler != null
                    && player.netServerHandler.supportsChunkZstd();
            if (supportsZstd && ZstdRuntime.isAvailable()) {
                long zstdStart = System.nanoTime();
                Packet202MapChunkZstd zstdPacket = compressMapChunkZstd(source);
                if (zstdPacket != null) {
                    long zstdDuration = System.nanoTime() - zstdStart;
                    if (zstdDuration > 0L) {
                        this.netChunkZstdCompressNanosTotal.addAndGet(zstdDuration);
                    }
                    this.zstdChunkPacketsSent.incrementAndGet();
                    this.netChunkZstdPacketsTotal.incrementAndGet();
                    return zstdPacket;
                }
                this.zstdFallbackCount.incrementAndGet();
                this.netChunkZstdFallbackTotal.incrementAndGet();
            }

            long zlibStart = System.nanoTime();
            try {
                compressMapChunkDeflate(source, worker);
            } finally {
                long zlibDuration = System.nanoTime() - zlibStart;
                if (zlibDuration > 0L) {
                    this.netChunkZlibCompressNanosTotal.addAndGet(zlibDuration);
                }
            }
            this.zlibChunkPacketsSent.incrementAndGet();
            this.netChunkZlibPacketsTotal.incrementAndGet();
            return source;
        } catch (Throwable t) {
            this.compressionFailureCount.incrementAndGet();
            this.netChunkCompressionFailuresTotal.incrementAndGet();
            return source;
        } finally {
            recordCompressionSample(System.nanoTime() - start);
        }
    }

    private Packet202MapChunkZstd compressMapChunkZstd(Packet51MapChunk source) {
        if (source.rawData == null) {
            return null;
        }

        byte[] compressed = ZstdRuntime.compressZstd(source.rawData, 0, source.rawData.length, ZSTD_LEVEL);
        if (compressed == null || compressed.length <= 0) {
            return null;
        }

        Packet202MapChunkZstd zstd = new Packet202MapChunkZstd();
        zstd.a = source.a;
        zstd.b = source.b;
        zstd.c = source.c;
        zstd.d = source.d;
        zstd.e = source.e;
        zstd.f = source.f;
        zstd.g = compressed;
        zstd.h = compressed.length;
        zstd.k = true;
        zstd.rawData = source.rawData;
        return zstd;
    }

    private void compressMapChunkDeflate(Packet51MapChunk packet, Worker worker) {
        if (packet.g != null) {
            return;
        }
        if (packet.rawData == null) {
            throw new IllegalStateException("Chunk packet missing rawData for zlib compression");
        }

        int dataSize = packet.rawData.length;
        if (worker.deflateBuffer.length < dataSize + 100) {
            worker.deflateBuffer = new byte[dataSize + 100];
        }

        Deflater deflater = worker.deflater.get();
        deflater.reset();
        deflater.setLevel(dataSize < REDUCED_DEFLATE_THRESHOLD ? DEFLATE_LEVEL_PARTS : DEFLATE_LEVEL_CHUNKS);
        deflater.setInput(packet.rawData);
        deflater.finish();
        int size = deflater.deflate(worker.deflateBuffer);
        if (size == 0) {
            size = deflater.deflate(worker.deflateBuffer);
        }

        packet.g = new byte[size];
        packet.h = size;
        System.arraycopy(worker.deflateBuffer, 0, packet.g, 0, size);
    }

    private void sendToNetworkQueue(EntityPlayer player, Packet packet) {
        if (player == null || player.netServerHandler == null || player.netServerHandler.networkManager == null || packet == null) {
            return;
        }
        player.netServerHandler.networkManager.queue(packet);
    }

    private void recordCompressionSample(long durationNanos) {
        if (durationNanos < 0L) {
            return;
        }

        synchronized (this.metricsLock) {
            this.compressionSampleNanos[this.compressionSampleWriteIndex] = durationNanos;
            this.compressionSampleWriteIndex = (this.compressionSampleWriteIndex + 1) % this.compressionSampleNanos.length;
            if (this.compressionSampleCount < this.compressionSampleNanos.length) {
                ++this.compressionSampleCount;
            }
        }
    }

    public static long getZstdChunkPacketsSent() {
        return instance.zstdChunkPacketsSent.get();
    }

    public static long getZlibChunkPacketsSent() {
        return instance.zlibChunkPacketsSent.get();
    }

    public static long getZstdFallbackCount() {
        return instance.zstdFallbackCount.get();
    }

    public static long getCompressionFailureCount() {
        return instance.compressionFailureCount.get();
    }

    public static long getCompressionAvgMicros() {
        return instance.computeCompressionAvgMicros();
    }

    public static long getCompressionP95Micros() {
        return instance.computeCompressionP95Micros();
    }

    public static long getNetChunkZstdPacketsTotal() {
        return instance.netChunkZstdPacketsTotal.get();
    }

    public static long getNetChunkZlibPacketsTotal() {
        return instance.netChunkZlibPacketsTotal.get();
    }

    public static long getNetChunkZstdFallbackTotal() {
        return instance.netChunkZstdFallbackTotal.get();
    }

    public static long getNetChunkCompressionFailuresTotal() {
        return instance.netChunkCompressionFailuresTotal.get();
    }

    public static long getNetChunkZstdCompressNanosTotal() {
        return instance.netChunkZstdCompressNanosTotal.get();
    }

    public static long getNetChunkZlibCompressNanosTotal() {
        return instance.netChunkZlibCompressNanosTotal.get();
    }

    private long computeCompressionAvgMicros() {
        synchronized (this.metricsLock) {
            if (this.compressionSampleCount <= 0) {
                return 0L;
            }

            long total = 0L;
            for (int i = 0; i < this.compressionSampleCount; ++i) {
                total += this.compressionSampleNanos[i];
            }
            return (total / (long) this.compressionSampleCount) / 1000L;
        }
    }

    private long computeCompressionP95Micros() {
        synchronized (this.metricsLock) {
            if (this.compressionSampleCount <= 0) {
                return 0L;
            }

            long[] copy = Arrays.copyOf(this.compressionSampleNanos, this.compressionSampleCount);
            Arrays.sort(copy);
            int index = (int) Math.ceil((double) copy.length * 0.95D) - 1;
            if (index < 0) {
                index = 0;
            } else if (index >= copy.length) {
                index = copy.length - 1;
            }
            return copy[index] / 1000L;
        }
    }

    public static boolean sendPacket(EntityPlayer player, Packet packet) {
        return instance.enqueuePacket(player, packet);
    }

    private boolean enqueuePacket(EntityPlayer player, Packet packet) {
        if (player == null || packet == null || player.netServerHandler == null || player.netServerHandler.networkManager == null) {
            return false;
        }

        if (!(packet instanceof Packet51MapChunk)) {
            player.netServerHandler.networkManager.queue(packet);
            return true;
        }

        if (!this.running) {
            this.startWorkers();
        }
        if (!this.running || this.workers.length == 0) {
            return false;
        }

        Worker worker = this.workers[selectWorkerIndex(player)];
        Packet packetForQueue = packet.clone();
        if (!(packetForQueue instanceof Packet51MapChunk)) {
            return false;
        }

        QueuedPacket task = new QueuedPacket(player, packetForQueue);
        if (!worker.offer(task)) {
            return false;
        }

        addToPlayerQueueSize(player, +1);
        this.totalQueuedPackets.incrementAndGet();
        return true;
    }

    private int selectWorkerIndex(EntityPlayer player) {
        int id = player == null ? 0 : player.id;
        return (id & Integer.MAX_VALUE) % this.workerCount;
    }

    private void addToPlayerQueueSize(EntityPlayer player, int amount) {
        synchronized (queueSizePerPlayer) {
            Integer count = queueSizePerPlayer.get(player);
            amount += (count == null) ? 0 : count;
            if (amount == 0) {
                queueSizePerPlayer.remove(player);
            } else {
                queueSizePerPlayer.put(player, amount);
            }
        }
    }

    public static int getPlayerQueueSize(EntityPlayer player) {
        synchronized (instance.queueSizePerPlayer) {
            Integer count = instance.queueSizePerPlayer.get(player);
            return count == null ? 0 : count;
        }
    }

    public static int getTotalQueueSize() {
        return instance.totalQueuedPackets.get();
    }

    public static int getTotalQueueCapacity() {
        return instance.totalQueueCapacity;
    }

    public static boolean isAboveHighWatermark() {
        return getTotalQueueSize() >= instance.highWatermarkSize;
    }

    public static boolean isAboveLowWatermark() {
        return getTotalQueueSize() >= instance.lowWatermarkSize;
    }

    public static boolean canAcceptChunk(EntityPlayer player) {
        if (!instance.running) {
            return true;
        }

        if (getTotalQueueSize() >= instance.totalQueueCapacity) {
            return false;
        }

        int playerQueue = getPlayerQueueSize(player);
        int playerSoftLimit = Math.max(8, instance.queueCapacityPerWorker / 2);
        if (playerQueue >= playerSoftLimit) {
            return false;
        }

        if (isAboveHighWatermark() && playerQueue > 2) {
            return false;
        }

        return true;
    }

    private static class QueuedPacket {
        final EntityPlayer player;
        final Packet packet;

        QueuedPacket(EntityPlayer player, Packet packet) {
            this.player = player;
            this.packet = packet;
        }
    }

    private final class Worker implements Runnable {
        private final ArrayBlockingQueue<QueuedPacket> queue;
        private final Thread thread;
        private final ThreadLocal<Deflater> deflater = new ThreadLocal<Deflater>() {
            @Override
            protected Deflater initialValue() {
                return new Deflater();
            }
        };
        private volatile boolean alive = true;
        private byte[] deflateBuffer = new byte[CHUNK_SIZE + 100];

        private Worker(String name, int queueCapacity) {
            this.queue = new ArrayBlockingQueue<QueuedPacket>(queueCapacity);
            this.thread = new Thread(this, name);
            this.thread.setDaemon(true);
        }

        private boolean offer(QueuedPacket packet) {
            return this.queue.offer(packet);
        }

        private void shutdown() {
            this.alive = false;
            this.thread.interrupt();
        }

        public void run() {
            try {
                while (this.alive || !this.queue.isEmpty()) {
                    QueuedPacket packet = this.queue.poll(250L, TimeUnit.MILLISECONDS);
                    if (packet == null) {
                        continue;
                    }

                    try {
                        handleQueuedPacket(packet, this);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (InterruptedException ignored) {
            } finally {
                Deflater d = this.deflater.get();
                d.end();
            }
        }
    }
}

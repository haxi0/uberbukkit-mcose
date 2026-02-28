package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;
import net.minecraft.server.threading.ThreadingManager;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Profiler 2.0:
 * - Always-on low-overhead telemetry (ring buffer)
 * - Optional deep profiling sessions (/profile start)
 */
public class ServerProfiler {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final ServerProfiler INSTANCE = new ServerProfiler();

    // Deep profiling state
    private volatile boolean enabled = false;
    private long profilingStartTime = 0L;
    private long profilingEndTime = 0L;

    // Deep section thresholds
    private static final double SLOW_THRESHOLD_MS = 10.0D;
    private static final double VERY_SLOW_THRESHOLD_MS = 50.0D;
    private static final double CRITICAL_THRESHOLD_MS = 100.0D;

    // Deep section data
    private final Map<String, ProfileData> profileData = new ConcurrentHashMap<String, ProfileData>();
    private final List<SlowCall> slowCalls = Collections.synchronizedList(new ArrayList<SlowCall>());
    private static final int MAX_SLOW_CALLS = 1000;

    private final ThreadLocal<Deque<CallFrame>> callStack = new ThreadLocal<Deque<CallFrame>>() {
        @Override
        protected Deque<CallFrame> initialValue() {
            return new ArrayDeque<CallFrame>();
        }
    };

    // Telemetry configuration
    private volatile boolean alwaysOnEnabled = true;
    private volatile int ringBufferSeconds = 300;
    private volatile boolean autoSnapshotEnabled = true;
    private volatile int autoSnapshotTickThresholdMs = 75;
    private volatile int autoSnapshotCooldownSeconds = 30;
    private volatile int topOffendersCount = 10;
    private volatile boolean includeJsonReports = true;
    private volatile boolean includeTextReports = true;
    private volatile boolean deepPacketBreakdownEnabled = true;
    private volatile boolean deepPerPlayerEnabled = true;
    private volatile long lastConfigRefreshMillis = 0L;

    // Ring buffer telemetry
    private final Object telemetryLock = new Object();
    private RingBufferSample[] ringBuffer = new RingBufferSample[300];
    private int ringBufferWriteIndex = 0;
    private int ringBufferSize = 0;
    private long currentSampleSecond = -1L;
    private RingBufferSample currentSample = null;

    // Rolling telemetry aggregates
    private final Map<String, PacketStatsBucket> packetStats = new HashMap<String, PacketStatsBucket>();
    private final Map<String, CommandStats> commandStats = new HashMap<String, CommandStats>();
    private final Map<String, ChunkOperationStats> chunkOperationStats = new HashMap<String, ChunkOperationStats>();
    private final Map<String, OffenderAccumulator> offenderStats = new HashMap<String, OffenderAccumulator>();

    // Memory tracking
    private final List<MemorySample> memorySamples = new ArrayList<MemorySample>();
    private static final int MAX_MEMORY_SAMPLES = 600;
    private long lastMemorySampleTime = 0L;
    private static final long MEMORY_SAMPLE_INTERVAL_MS = 1000L;

    // Tick timing and command latency history
    private final ArrayDeque<Double> recentTickTimesMs = new ArrayDeque<Double>();
    private static final int MAX_TICK_SAMPLES = 12000;
    private long tickCount = 0L;
    private double totalTickTimeMs = 0D;
    private double maxTickTimeMs = 0D;
    private long lastTickTimeNanos = 0L;

    // Chunk counters
    private long chunksGenerated = 0L;
    private long chunksLoaded = 0L;
    private long chunksUnloaded = 0L;
    private double totalChunkGenTimeMs = 0D;
    private double maxChunkGenTimeMs = 0D;

    // Network movement and entity-tracking telemetry
    private long movementCoalescedPackets = 0L;
    private long movementDroppedPackets = 0L;
    private long entityTrackingSkippedNear = 0L;
    private long entityTrackingSkippedMid = 0L;
    private long entityTrackingSkippedFar = 0L;
    private long trackingStateNormalTicks = 0L;
    private long trackingStatePressureTicks = 0L;
    private long trackingStateRecoveryTicks = 0L;
    private String currentTrackingState = "NORMAL";
    private long trackingLastTransitionMillis = 0L;
    private long lastNetChunkZstdPacketsTotal = 0L;
    private long lastNetChunkZlibPacketsTotal = 0L;
    private long lastNetChunkZstdFallbackTotal = 0L;
    private long lastNetChunkCompressionFailuresTotal = 0L;
    private long lastNetChunkZstdCompressNanosTotal = 0L;
    private long lastNetChunkZlibCompressNanosTotal = 0L;
    private long lastRegionWriteZstdTotal = 0L;
    private long lastRegionWriteZlibTotal = 0L;
    private long lastRegionWriteZstdFallbackTotal = 0L;
    private long lastRegionWriteZstdNanosTotal = 0L;
    private long lastRegionWriteZlibNanosTotal = 0L;

    // Auto snapshots
    private final Queue<ProfileSnapshot> recentAutoSnapshots = new ArrayDeque<ProfileSnapshot>();
    private static final int MAX_AUTO_SNAPSHOTS = 8;
    private long lastAutoSnapshotTime = 0L;

    private ServerProfiler() {
        refreshConfig();
    }

    public static ServerProfiler getInstance() {
        return INSTANCE;
    }

    public void start() {
        clear();
        enabled = true;
        profilingStartTime = System.currentTimeMillis();
        profilingEndTime = 0L;
        log.info("[ServerProfiler] Started profiling session");
    }

    public void stop() {
        if (!enabled) {
            return;
        }
        enabled = false;
        profilingEndTime = System.currentTimeMillis();
        double durationSec = (profilingEndTime - profilingStartTime) / 1000.0D;
        log.info("[ServerProfiler] Stopped profiling session. Duration: " + String.format("%.2f", durationSec) + " seconds");
    }

    public void clear() {
        profileData.clear();
        slowCalls.clear();

        synchronized (telemetryLock) {
            this.ringBuffer = new RingBufferSample[Math.max(30, ringBufferSeconds)];
            this.ringBufferWriteIndex = 0;
            this.ringBufferSize = 0;
            this.currentSampleSecond = -1L;
            this.currentSample = null;
            this.packetStats.clear();
            this.commandStats.clear();
            this.chunkOperationStats.clear();
            this.offenderStats.clear();
            this.memorySamples.clear();
            this.recentTickTimesMs.clear();
            this.recentAutoSnapshots.clear();
            this.lastMemorySampleTime = 0L;
            this.tickCount = 0L;
            this.totalTickTimeMs = 0D;
            this.maxTickTimeMs = 0D;
            this.lastTickTimeNanos = 0L;
            this.lastAutoSnapshotTime = 0L;
            this.chunksGenerated = 0L;
            this.chunksLoaded = 0L;
            this.chunksUnloaded = 0L;
            this.totalChunkGenTimeMs = 0D;
            this.maxChunkGenTimeMs = 0D;
            this.movementCoalescedPackets = 0L;
            this.movementDroppedPackets = 0L;
            this.entityTrackingSkippedNear = 0L;
            this.entityTrackingSkippedMid = 0L;
            this.entityTrackingSkippedFar = 0L;
            this.trackingStateNormalTicks = 0L;
            this.trackingStatePressureTicks = 0L;
            this.trackingStateRecoveryTicks = 0L;
            this.currentTrackingState = "NORMAL";
            this.trackingLastTransitionMillis = 0L;
            this.lastNetChunkZstdPacketsTotal = 0L;
            this.lastNetChunkZlibPacketsTotal = 0L;
            this.lastNetChunkZstdFallbackTotal = 0L;
            this.lastNetChunkCompressionFailuresTotal = 0L;
            this.lastNetChunkZstdCompressNanosTotal = 0L;
            this.lastNetChunkZlibCompressNanosTotal = 0L;
            this.lastRegionWriteZstdTotal = 0L;
            this.lastRegionWriteZlibTotal = 0L;
            this.lastRegionWriteZstdFallbackTotal = 0L;
            this.lastRegionWriteZstdNanosTotal = 0L;
            this.lastRegionWriteZlibNanosTotal = 0L;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    // ----------------------------
    // Deep section profiling
    // ----------------------------

    public void startSection(String sectionName) {
        if (!enabled) {
            return;
        }

        Deque<CallFrame> stack = callStack.get();
        String fullPath = stack.isEmpty() ? sectionName : stack.peek().path + "/" + sectionName;
        stack.push(new CallFrame(fullPath, System.nanoTime()));
    }

    public void endSection() {
        if (!enabled) {
            return;
        }

        Deque<CallFrame> stack = callStack.get();
        if (stack.isEmpty()) {
            return;
        }

        long endNanos = System.nanoTime();
        CallFrame frame = stack.pop();
        double durationMs = (endNanos - frame.startTime) / 1_000_000.0D;

        ProfileData data = profileData.get(frame.path);
        if (data == null) {
            ProfileData created = new ProfileData(frame.path);
            ProfileData previous = profileData.put(frame.path, created);
            data = previous != null ? previous : created;
        }
        data.recordCall(durationMs);

        if (durationMs >= SLOW_THRESHOLD_MS && slowCalls.size() < MAX_SLOW_CALLS) {
            SlowCall.Severity severity = SlowCall.Severity.SLOW;
            if (durationMs >= CRITICAL_THRESHOLD_MS) {
                severity = SlowCall.Severity.CRITICAL;
            } else if (durationMs >= VERY_SLOW_THRESHOLD_MS) {
                severity = SlowCall.Severity.VERY_SLOW;
            }
            slowCalls.add(new SlowCall(frame.path, durationMs, severity, System.currentTimeMillis()));
        }
    }

    public void endStartSection(String sectionName) {
        endSection();
        startSection(sectionName);
    }

    // ----------------------------
    // Always-on telemetry APIs
    // ----------------------------

    /**
     * Backward-compatible tick recorder from previous profiler. Uses delta since previous call.
     */
    public void recordTickTime() {
        long now = System.nanoTime();
        long previous;
        synchronized (telemetryLock) {
            previous = this.lastTickTimeNanos;
            this.lastTickTimeNanos = now;
        }
        if (previous > 0L) {
            double tickMs = (now - previous) / 1_000_000.0D;
            recordTickSample(tickMs, 0, 0, 0);
        }
    }

    public void recordTickSample(double tickMs, int commandQueueDepth, int worldCount, int playerCount) {
        refreshConfigMaybe();

        long now = System.currentTimeMillis();
        boolean triggerAutoSnapshot = false;
        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(now);

            sample.tickCount++;
            sample.tickTotalMs += tickMs;
            if (tickMs > sample.tickMaxMs) {
                sample.tickMaxMs = tickMs;
            }
            if (tickMs >= 50.0D) {
                sample.ticksOver50Ms++;
            }
            if (tickMs >= 75.0D) {
                sample.ticksOver75Ms++;
            }
            if (commandQueueDepth > sample.commandQueueDepthMax) {
                sample.commandQueueDepthMax = commandQueueDepth;
            }
            sample.commandQueueDepthTotal += Math.max(0, commandQueueDepth);
            if (worldCount > sample.worldCountMax) {
                sample.worldCountMax = worldCount;
            }
            if (playerCount > sample.playerCountMax) {
                sample.playerCountMax = playerCount;
            }

            tickCount++;
            totalTickTimeMs += tickMs;
            if (tickMs > maxTickTimeMs) {
                maxTickTimeMs = tickMs;
            }
            recentTickTimesMs.addLast(tickMs);
            while (recentTickTimesMs.size() > MAX_TICK_SAMPLES) {
                recentTickTimesMs.removeFirst();
            }

            if (now - lastMemorySampleTime >= MEMORY_SAMPLE_INTERVAL_MS) {
                recordMemorySampleLocked(now);
                lastMemorySampleTime = now;
            }

            if (alwaysOnEnabled && autoSnapshotEnabled && tickMs >= autoSnapshotTickThresholdMs) {
                long cooldownMs = autoSnapshotCooldownSeconds * 1000L;
                if (now - lastAutoSnapshotTime >= cooldownMs) {
                    lastAutoSnapshotTime = now;
                    triggerAutoSnapshot = true;
                }
            }
        }

        if (triggerAutoSnapshot) {
            ProfileSnapshot snap = captureSnapshot(false);
            snap.autoCaptured = true;
            synchronized (telemetryLock) {
                recentAutoSnapshots.add(snap);
                while (recentAutoSnapshots.size() > MAX_AUTO_SNAPSHOTS) {
                    recentAutoSnapshots.poll();
                }
            }
        }
    }

    public void recordQueueSample(int commandQueueDepth,
                                  int inboundQueueDepth,
                                  int outboundHighQueueDepth,
                                  int outboundLowQueueDepth,
                                  int outboundQueuedBytes,
                                  int pendingLoginCount,
                                  int activeHandlerCount,
                                  int chunkCompressionQueueSize,
                                  int chunkCompressionQueueCapacity,
                                  long netChunkZstdPacketsTotal,
                                  long netChunkZlibPacketsTotal,
                                  long netChunkZstdFallbackTotal,
                                  long netChunkCompressionFailuresTotal,
                                  long netChunkZstdCompressNanosTotal,
                                  long netChunkZlibCompressNanosTotal,
                                  long regionWriteZstdTotal,
                                  long regionWriteZlibTotal,
                                  long regionWriteZstdFallbackTotal,
                                  long regionWriteZstdNanosTotal,
                                  long regionWriteZlibNanosTotal) {
        long now = System.currentTimeMillis();
        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(now);
            sample.commandQueueDepthTotal += Math.max(0, commandQueueDepth);
            if (commandQueueDepth > sample.commandQueueDepthMax) {
                sample.commandQueueDepthMax = commandQueueDepth;
            }
            sample.inboundQueueDepthTotal += Math.max(0, inboundQueueDepth);
            sample.outboundHighQueueDepthTotal += Math.max(0, outboundHighQueueDepth);
            sample.outboundLowQueueDepthTotal += Math.max(0, outboundLowQueueDepth);
            sample.outboundQueuedBytesTotal += Math.max(0, outboundQueuedBytes);
            if (inboundQueueDepth > sample.inboundQueueDepthMax) {
                sample.inboundQueueDepthMax = inboundQueueDepth;
            }
            if (outboundHighQueueDepth > sample.outboundHighQueueDepthMax) {
                sample.outboundHighQueueDepthMax = outboundHighQueueDepth;
            }
            if (outboundLowQueueDepth > sample.outboundLowQueueDepthMax) {
                sample.outboundLowQueueDepthMax = outboundLowQueueDepth;
            }
            if (outboundQueuedBytes > sample.outboundQueuedBytesMax) {
                sample.outboundQueuedBytesMax = outboundQueuedBytes;
            }
            if (pendingLoginCount > sample.pendingLoginsMax) {
                sample.pendingLoginsMax = pendingLoginCount;
            }
            if (activeHandlerCount > sample.activeHandlersMax) {
                sample.activeHandlersMax = activeHandlerCount;
            }
            if (chunkCompressionQueueSize > sample.chunkCompressionQueueMax) {
                sample.chunkCompressionQueueMax = chunkCompressionQueueSize;
            }
            sample.chunkCompressionQueueCapacity = chunkCompressionQueueCapacity;

            long netChunkZstdPacketsDelta = computeNonNegativeDelta(netChunkZstdPacketsTotal, this.lastNetChunkZstdPacketsTotal);
            long netChunkZlibPacketsDelta = computeNonNegativeDelta(netChunkZlibPacketsTotal, this.lastNetChunkZlibPacketsTotal);
            long netChunkZstdFallbackDelta = computeNonNegativeDelta(netChunkZstdFallbackTotal, this.lastNetChunkZstdFallbackTotal);
            long netChunkCompressionFailuresDelta = computeNonNegativeDelta(netChunkCompressionFailuresTotal, this.lastNetChunkCompressionFailuresTotal);
            long netChunkZstdNanosDelta = computeNonNegativeDelta(netChunkZstdCompressNanosTotal, this.lastNetChunkZstdCompressNanosTotal);
            long netChunkZlibNanosDelta = computeNonNegativeDelta(netChunkZlibCompressNanosTotal, this.lastNetChunkZlibCompressNanosTotal);
            long regionWriteZstdDelta = computeNonNegativeDelta(regionWriteZstdTotal, this.lastRegionWriteZstdTotal);
            long regionWriteZlibDelta = computeNonNegativeDelta(regionWriteZlibTotal, this.lastRegionWriteZlibTotal);
            long regionWriteZstdFallbackDelta = computeNonNegativeDelta(regionWriteZstdFallbackTotal, this.lastRegionWriteZstdFallbackTotal);
            long regionWriteZstdNanosDelta = computeNonNegativeDelta(regionWriteZstdNanosTotal, this.lastRegionWriteZstdNanosTotal);
            long regionWriteZlibNanosDelta = computeNonNegativeDelta(regionWriteZlibNanosTotal, this.lastRegionWriteZlibNanosTotal);

            this.lastNetChunkZstdPacketsTotal = netChunkZstdPacketsTotal;
            this.lastNetChunkZlibPacketsTotal = netChunkZlibPacketsTotal;
            this.lastNetChunkZstdFallbackTotal = netChunkZstdFallbackTotal;
            this.lastNetChunkCompressionFailuresTotal = netChunkCompressionFailuresTotal;
            this.lastNetChunkZstdCompressNanosTotal = netChunkZstdCompressNanosTotal;
            this.lastNetChunkZlibCompressNanosTotal = netChunkZlibCompressNanosTotal;
            this.lastRegionWriteZstdTotal = regionWriteZstdTotal;
            this.lastRegionWriteZlibTotal = regionWriteZlibTotal;
            this.lastRegionWriteZstdFallbackTotal = regionWriteZstdFallbackTotal;
            this.lastRegionWriteZstdNanosTotal = regionWriteZstdNanosTotal;
            this.lastRegionWriteZlibNanosTotal = regionWriteZlibNanosTotal;

            sample.netChunkZstdPackets += netChunkZstdPacketsDelta;
            sample.netChunkZlibPackets += netChunkZlibPacketsDelta;
            sample.netChunkZstdFallbacks += netChunkZstdFallbackDelta;
            sample.netChunkCompressionFailures += netChunkCompressionFailuresDelta;
            sample.netChunkZstdCompressNanos += netChunkZstdNanosDelta;
            sample.netChunkZlibCompressNanos += netChunkZlibNanosDelta;
            sample.regionWriteZstd += regionWriteZstdDelta;
            sample.regionWriteZlib += regionWriteZlibDelta;
            sample.regionWriteZstdFallbacks += regionWriteZstdFallbackDelta;
            sample.regionWriteZstdNanos += regionWriteZstdNanosDelta;
            sample.regionWriteZlibNanos += regionWriteZlibNanosDelta;
            sample.queueSampleCount++;
        }
    }

    public void recordPacketIngress(int packetId, String packetClass, int inboundQueueDepth) {
        long now = System.currentTimeMillis();
        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(now);
            sample.packetIngressCount++;
            if (inboundQueueDepth > sample.inboundQueueDepthMax) {
                sample.inboundQueueDepthMax = inboundQueueDepth;
            }

            PacketStatsBucket bucket = getOrCreatePacketBucketLocked(packetId, packetClass);
            bucket.inboundCount++;
            if (inboundQueueDepth > bucket.inboundQueueDepthMax) {
                bucket.inboundQueueDepthMax = inboundQueueDepth;
            }
        }
    }

    public void recordPacketEgress(int packetId,
                                   String packetClass,
                                   double queueWaitMs,
                                   boolean highPriority,
                                   int packetBytes,
                                   String playerName) {
        long now = System.currentTimeMillis();
        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(now);
            sample.packetEgressCount++;
            sample.packetEgressQueueWaitTotalMs += Math.max(0D, queueWaitMs);
            if (queueWaitMs > sample.packetEgressQueueWaitMaxMs) {
                sample.packetEgressQueueWaitMaxMs = queueWaitMs;
            }

            PacketStatsBucket bucket = getOrCreatePacketBucketLocked(packetId, packetClass);
            bucket.outboundCount++;
            bucket.outboundQueueWaitTotalMs += Math.max(0D, queueWaitMs);
            if (queueWaitMs > bucket.outboundQueueWaitMaxMs) {
                bucket.outboundQueueWaitMaxMs = queueWaitMs;
            }
            bucket.outboundBytes += Math.max(0, packetBytes);
            if (highPriority) {
                bucket.highPriorityCount++;
            } else {
                bucket.lowPriorityCount++;
            }

            if (playerName != null && playerName.length() > 0) {
                OffenderAccumulator offender = getOrCreateOffenderLocked(playerName);
                offender.packetSamples++;
                offender.totalEgressQueueWaitMs += Math.max(0D, queueWaitMs);
                if (queueWaitMs > offender.maxQueueWaitMs) {
                    offender.maxQueueWaitMs = queueWaitMs;
                }
            }
        }
    }

    public void recordPacketProcess(int packetId,
                                    String packetClass,
                                    double queueWaitMs,
                                    double processMs,
                                    String playerName) {
        long now = System.currentTimeMillis();
        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(now);
            sample.packetProcessCount++;
            sample.packetIngressQueueWaitTotalMs += Math.max(0D, queueWaitMs);
            if (queueWaitMs > sample.packetIngressQueueWaitMaxMs) {
                sample.packetIngressQueueWaitMaxMs = queueWaitMs;
            }
            sample.packetProcessTotalMs += Math.max(0D, processMs);
            if (processMs > sample.packetProcessMaxMs) {
                sample.packetProcessMaxMs = processMs;
            }

            PacketStatsBucket bucket = getOrCreatePacketBucketLocked(packetId, packetClass);
            bucket.processCount++;
            bucket.inboundQueueWaitTotalMs += Math.max(0D, queueWaitMs);
            if (queueWaitMs > bucket.inboundQueueWaitMaxMs) {
                bucket.inboundQueueWaitMaxMs = queueWaitMs;
            }
            bucket.processTotalMs += Math.max(0D, processMs);
            if (processMs > bucket.processMaxMs) {
                bucket.processMaxMs = processMs;
            }

            if (playerName != null && playerName.length() > 0) {
                OffenderAccumulator offender = getOrCreateOffenderLocked(playerName);
                offender.packetSamples++;
                offender.totalIngressQueueWaitMs += Math.max(0D, queueWaitMs);
                offender.totalProcessMs += Math.max(0D, processMs);
                if (queueWaitMs > offender.maxQueueWaitMs) {
                    offender.maxQueueWaitMs = queueWaitMs;
                }
                if (processMs > offender.maxProcessMs) {
                    offender.maxProcessMs = processMs;
                }
            }
        }
    }

    public void recordCommandLatency(String commandRoot, double queueWaitMs, double executionMs, int commandQueueDepth) {
        long now = System.currentTimeMillis();
        if (commandRoot == null || commandRoot.length() == 0) {
            commandRoot = "unknown";
        }

        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(now);
            sample.commandSamples++;
            sample.commandQueueWaitTotalMs += Math.max(0D, queueWaitMs);
            if (queueWaitMs > sample.commandQueueWaitMaxMs) {
                sample.commandQueueWaitMaxMs = queueWaitMs;
            }
            sample.commandExecutionTotalMs += Math.max(0D, executionMs);
            if (executionMs > sample.commandExecutionMaxMs) {
                sample.commandExecutionMaxMs = executionMs;
            }
            if (commandQueueDepth > sample.commandQueueDepthMax) {
                sample.commandQueueDepthMax = commandQueueDepth;
            }

            CommandStats stats = commandStats.get(commandRoot);
            if (stats == null) {
                stats = new CommandStats(commandRoot);
                commandStats.put(commandRoot, stats);
            }
            stats.count++;
            stats.queueWaitTotalMs += Math.max(0D, queueWaitMs);
            stats.execTotalMs += Math.max(0D, executionMs);
            if (queueWaitMs > stats.queueWaitMaxMs) {
                stats.queueWaitMaxMs = queueWaitMs;
            }
            if (executionMs > stats.execMaxMs) {
                stats.execMaxMs = executionMs;
            }
        }
    }

    public void recordSchedulerSample(int movedToSynced, int executedTasks, int leftoverSyncedTasks, double heartbeatMs) {
        long now = System.currentTimeMillis();
        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(now);
            sample.schedulerSamples++;
            sample.schedulerMovedToSyncedTotal += Math.max(0, movedToSynced);
            sample.schedulerExecutedTotal += Math.max(0, executedTasks);
            sample.schedulerLeftoverTotal += Math.max(0, leftoverSyncedTasks);
            if (leftoverSyncedTasks > sample.schedulerLeftoverMax) {
                sample.schedulerLeftoverMax = leftoverSyncedTasks;
            }
            sample.schedulerHeartbeatTotalMs += Math.max(0D, heartbeatMs);
            if (heartbeatMs > sample.schedulerHeartbeatMaxMs) {
                sample.schedulerHeartbeatMaxMs = heartbeatMs;
            }
        }
    }

    public void recordChunkIo(String operation, double durationMs, String worldName) {
        long now = System.currentTimeMillis();
        String op = operation == null ? "unknown" : operation;

        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(now);
            sample.chunkIoCount++;
            sample.chunkIoTotalMs += Math.max(0D, durationMs);
            if (durationMs > sample.chunkIoMaxMs) {
                sample.chunkIoMaxMs = durationMs;
            }

            String key = worldName == null ? op : op + "@" + worldName;
            ChunkOperationStats stats = chunkOperationStats.get(key);
            if (stats == null) {
                stats = new ChunkOperationStats(op, worldName);
                chunkOperationStats.put(key, stats);
            }
            stats.count++;
            stats.totalMs += Math.max(0D, durationMs);
            if (durationMs > stats.maxMs) {
                stats.maxMs = durationMs;
            }
        }
    }

    public void recordPlayerQueueSample(String playerName,
                                        int outboundQueuedPackets,
                                        int inboundQueueDepth,
                                        int chunkCompressionQueuedPackets,
                                        int pingMs) {
        if (playerName == null || playerName.length() == 0) {
            return;
        }

        synchronized (telemetryLock) {
            OffenderAccumulator offender = getOrCreateOffenderLocked(playerName);
            offender.samples++;
            offender.outboundQueueTotal += Math.max(0, outboundQueuedPackets);
            offender.inboundQueueTotal += Math.max(0, inboundQueueDepth);
            offender.chunkCompressionQueueTotal += Math.max(0, chunkCompressionQueuedPackets);
            offender.pingTotal += Math.max(0, pingMs);

            if (outboundQueuedPackets > offender.outboundQueueMax) {
                offender.outboundQueueMax = outboundQueuedPackets;
            }
            if (inboundQueueDepth > offender.inboundQueueMax) {
                offender.inboundQueueMax = inboundQueueDepth;
            }
            if (chunkCompressionQueuedPackets > offender.chunkCompressionQueueMax) {
                offender.chunkCompressionQueueMax = chunkCompressionQueuedPackets;
            }
            if (pingMs > offender.pingMax) {
                offender.pingMax = pingMs;
            }
        }
    }

    // Backward-compatible chunk counters
    public void recordChunkGenerated(double generationTimeMs) {
        synchronized (telemetryLock) {
            chunksGenerated++;
            totalChunkGenTimeMs += Math.max(0D, generationTimeMs);
            if (generationTimeMs > maxChunkGenTimeMs) {
                maxChunkGenTimeMs = generationTimeMs;
            }
        }
        recordChunkIo("generate", generationTimeMs, null);
    }

    public void recordChunkLoaded() {
        synchronized (telemetryLock) {
            chunksLoaded++;
        }
    }

    public void recordChunkUnloaded() {
        synchronized (telemetryLock) {
            chunksUnloaded++;
        }
    }

    public void recordMovementPacketCoalesced(int count) {
        if (count <= 0) {
            return;
        }
        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(System.currentTimeMillis());
            sample.movementCoalescedPackets += count;
            movementCoalescedPackets += count;
        }
    }

    public void recordMovementPacketDropped(int count) {
        if (count <= 0) {
            return;
        }
        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(System.currentTimeMillis());
            sample.movementDroppedPackets += count;
            movementDroppedPackets += count;
        }
    }

    public void recordEntityTrackingSample(String stateName,
                                           int skippedNear,
                                           int skippedMid,
                                           int skippedFar,
                                           long transitionMillis) {
        String normalized = "NORMAL";
        if (stateName != null) {
            String upper = stateName.toUpperCase(Locale.US);
            if ("PRESSURE".equals(upper) || "RECOVERY".equals(upper)) {
                normalized = upper;
            }
        }

        synchronized (telemetryLock) {
            RingBufferSample sample = getOrCreateCurrentSampleLocked(System.currentTimeMillis());
            sample.entityTrackingState = normalized;
            if ("PRESSURE".equals(normalized)) {
                sample.entityTrackingPressureTicks++;
                trackingStatePressureTicks++;
            } else if ("RECOVERY".equals(normalized)) {
                sample.entityTrackingRecoveryTicks++;
                trackingStateRecoveryTicks++;
            } else {
                sample.entityTrackingNormalTicks++;
                trackingStateNormalTicks++;
            }

            int near = Math.max(0, skippedNear);
            int mid = Math.max(0, skippedMid);
            int far = Math.max(0, skippedFar);
            sample.entityTrackingSkippedNear += near;
            sample.entityTrackingSkippedMid += mid;
            sample.entityTrackingSkippedFar += far;
            entityTrackingSkippedNear += near;
            entityTrackingSkippedMid += mid;
            entityTrackingSkippedFar += far;

            if (transitionMillis > sample.entityTrackingLastTransitionMillis) {
                sample.entityTrackingLastTransitionMillis = transitionMillis;
            }
            if (transitionMillis > trackingLastTransitionMillis) {
                trackingLastTransitionMillis = transitionMillis;
            }
            currentTrackingState = normalized;
        }
    }

    // ----------------------------
    // Snapshot and reporting
    // ----------------------------

    public ProfileSnapshot captureSnapshot(boolean deep) {
        refreshConfigMaybe();
        ProfileSnapshot snapshot = new ProfileSnapshot();
        snapshot.version = 1;
        snapshot.timestampMillis = System.currentTimeMillis();
        snapshot.deep = deep;
        snapshot.alwaysOnEnabled = alwaysOnEnabled;
        snapshot.sessionStartMillis = profilingStartTime;
        snapshot.sessionEndMillis = profilingEndTime;
        snapshot.ringBufferRetentionSeconds = ringBufferSeconds;
        snapshot.lastAutoSnapshotMillis = lastAutoSnapshotTime;

        synchronized (telemetryLock) {
            snapshot.tickCount = tickCount;
            snapshot.avgTickMs = tickCount > 0 ? totalTickTimeMs / tickCount : 0D;
            snapshot.maxTickMs = maxTickTimeMs;
            snapshot.recentTickPercentiles = calculateTickPercentilesLocked();

            snapshot.queueStats = buildQueueStatsLocked();
            snapshot.schedulerStats = buildSchedulerStatsLocked();
            snapshot.chunkIoStats = buildChunkIoStatsLocked();
            snapshot.packetStats = topPacketBucketsLocked(30);
            snapshot.commandStats = topCommandStatsLocked(20);
            snapshot.offenders = topOffendersLocked(topOffendersCount);
            snapshot.autoSnapshotCount = recentAutoSnapshots.size();

            snapshot.ringBufferSamples = copyRingBufferLocked();
            snapshot.memoryAnalysis = analyzeMemoryLocked();

            snapshot.chunksGenerated = chunksGenerated;
            snapshot.chunksLoaded = chunksLoaded;
            snapshot.chunksUnloaded = chunksUnloaded;
            snapshot.avgChunkGenMs = chunksGenerated > 0 ? totalChunkGenTimeMs / chunksGenerated : 0D;
            snapshot.maxChunkGenMs = maxChunkGenTimeMs;
            snapshot.movementCoalescedPackets = movementCoalescedPackets;
            snapshot.movementDroppedPackets = movementDroppedPackets;
            snapshot.entityTrackingSkippedNear = entityTrackingSkippedNear;
            snapshot.entityTrackingSkippedMid = entityTrackingSkippedMid;
            snapshot.entityTrackingSkippedFar = entityTrackingSkippedFar;
            snapshot.entityTrackingState = currentTrackingState;
            snapshot.entityTrackingNormalTicks = trackingStateNormalTicks;
            snapshot.entityTrackingPressureTicks = trackingStatePressureTicks;
            snapshot.entityTrackingRecoveryTicks = trackingStateRecoveryTicks;
            snapshot.entityTrackingLastTransitionMillis = trackingLastTransitionMillis;

            if (deep) {
                snapshot.profileData = copyProfileData();
                snapshot.slowCalls = copySlowCalls();
            } else {
                snapshot.profileData = new ArrayList<ProfileData>();
                snapshot.slowCalls = new ArrayList<SlowCall>();
            }
        }

        try {
            snapshot.threadingStats = ThreadingManager.getInstance().captureStats();
        } catch (Throwable ignored) {
        }

        snapshot.findings = buildFindings(snapshot);
        snapshot.healthScore = calculateHealthScore(snapshot);
        return snapshot;
    }

    public String generateReportText(ProfileSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        sb.append(repeatString("=", 96)).append('\n');
        sb.append("MCOSE SERVER PROFILER 2.0 REPORT").append('\n');
        sb.append(repeatString("=", 96)).append('\n');
        sb.append("Generated: ").append(sdf.format(new Date(snapshot.timestampMillis))).append('\n');
        sb.append("Mode: ").append(snapshot.deep ? "deep" : "always-on snapshot").append('\n');
        sb.append("Health Score: ").append(snapshot.healthScore).append("/100").append('\n');
        sb.append("Tick Avg/P95/P99: ")
            .append(formatDouble(snapshot.avgTickMs)).append("ms / ")
            .append(formatDouble(snapshot.recentTickPercentiles.p95)).append("ms / ")
            .append(formatDouble(snapshot.recentTickPercentiles.p99)).append("ms").append('\n');
        sb.append("Last Auto Snapshot: ").append(snapshot.lastAutoSnapshotMillis > 0 ? sdf.format(new Date(snapshot.lastAutoSnapshotMillis)) : "none").append('\n');
        sb.append('\n');

        sb.append("Top Bottlenecks:\n");
        if (snapshot.findings.isEmpty()) {
            sb.append("  - none detected\n");
        } else {
            int i = 0;
            for (BottleneckFinding finding : snapshot.findings) {
                if (i++ >= 5) {
                    break;
                }
                sb.append("  - [").append(finding.severity).append("] ").append(finding.title)
                    .append(" | ").append(finding.details).append('\n');
            }
        }
        sb.append('\n');

        sb.append("Queue Pressure:\n");
        sb.append("  Command queue avg/max: ").append(formatDouble(snapshot.queueStats.commandQueueAvg)).append(" / ").append(snapshot.queueStats.commandQueueMax).append('\n');
        sb.append("  Inbound queue avg/max: ").append(formatDouble(snapshot.queueStats.inboundQueueAvg)).append(" / ").append(snapshot.queueStats.inboundQueueMax).append('\n');
        sb.append("  Outbound high avg/max: ").append(formatDouble(snapshot.queueStats.outboundHighAvg)).append(" / ").append(snapshot.queueStats.outboundHighMax).append('\n');
        sb.append("  Outbound low avg/max: ").append(formatDouble(snapshot.queueStats.outboundLowAvg)).append(" / ").append(snapshot.queueStats.outboundLowMax).append('\n');
        sb.append("  Outbound bytes avg/max: ").append(formatDouble(snapshot.queueStats.outboundBytesAvg)).append(" / ").append(snapshot.queueStats.outboundBytesMax).append('\n');
        sb.append("  Chunk compression max/cap: ").append(snapshot.queueStats.chunkCompressionMax).append(" / ").append(snapshot.queueStats.chunkCompressionCapacity).append('\n');
        sb.append("  Chunk Codec (window) zstd/zlib/fallback/fail: ")
            .append(snapshot.queueStats.netChunkZstdPackets).append(" / ")
            .append(snapshot.queueStats.netChunkZlibPackets).append(" / ")
            .append(snapshot.queueStats.netChunkZstdFallbacks).append(" / ")
            .append(snapshot.queueStats.netChunkCompressionFailures)
            .append(" | zstdAvgUs=").append(formatDouble(snapshot.queueStats.netChunkZstdAvgMicros))
            .append(" zlibAvgUs=").append(formatDouble(snapshot.queueStats.netChunkZlibAvgMicros))
            .append('\n');
        sb.append("  Region Codec Writes (window) zstd/zlib/fallback: ")
            .append(snapshot.queueStats.regionWriteZstd).append(" / ")
            .append(snapshot.queueStats.regionWriteZlib).append(" / ")
            .append(snapshot.queueStats.regionWriteZstdFallbacks)
            .append(" | zstdAvgUs=").append(formatDouble(snapshot.queueStats.regionWriteZstdAvgMicros))
            .append(" zlibAvgUs=").append(formatDouble(snapshot.queueStats.regionWriteZlibAvgMicros))
            .append('\n');
        sb.append("  Movement coalesced/dropped: ").append(snapshot.queueStats.movementCoalescedPackets).append(" / ").append(snapshot.queueStats.movementDroppedPackets).append('\n');
        sb.append("  Tracking skips near/mid/far: ")
            .append(snapshot.queueStats.entityTrackingSkippedNear).append(" / ")
            .append(snapshot.queueStats.entityTrackingSkippedMid).append(" / ")
            .append(snapshot.queueStats.entityTrackingSkippedFar).append('\n');
        sb.append('\n');

        sb.append("Adaptive Tracking:\n");
        sb.append("  State: ").append(snapshot.entityTrackingState).append('\n');
        sb.append("  State ticks normal/pressure/recovery: ")
            .append(snapshot.entityTrackingNormalTicks).append(" / ")
            .append(snapshot.entityTrackingPressureTicks).append(" / ")
            .append(snapshot.entityTrackingRecoveryTicks).append('\n');
        sb.append("  Last transition: ")
            .append(snapshot.entityTrackingLastTransitionMillis > 0 ? sdf.format(new Date(snapshot.entityTrackingLastTransitionMillis)) : "none")
            .append('\n');
        sb.append('\n');

        sb.append("Scheduler:\n");
        sb.append("  Heartbeats sampled: ").append(snapshot.schedulerStats.samples).append('\n');
        sb.append("  Moved->sync avg: ").append(formatDouble(snapshot.schedulerStats.movedAvg)).append('\n');
        sb.append("  Executed avg: ").append(formatDouble(snapshot.schedulerStats.executedAvg)).append('\n');
        sb.append("  Leftover avg/max: ").append(formatDouble(snapshot.schedulerStats.leftoverAvg)).append(" / ").append(snapshot.schedulerStats.leftoverMax).append('\n');
        sb.append("  Heartbeat ms avg/max: ").append(formatDouble(snapshot.schedulerStats.heartbeatAvgMs)).append(" / ").append(formatDouble(snapshot.schedulerStats.heartbeatMaxMs)).append('\n');
        sb.append('\n');

        sb.append("Packet Path (Top 10 by processing time):\n");
        int packetShown = 0;
        for (PacketStatsBucket bucket : snapshot.packetStats) {
            if (packetShown++ >= 10) {
                break;
            }
            sb.append("  - ").append(bucket.packetClass).append(" (#").append(bucket.packetId).append(")")
                .append(" in=").append(bucket.inboundCount)
                .append(" out=").append(bucket.outboundCount)
                .append(" procAvg=").append(formatDouble(bucket.getProcessAvgMs())).append("ms")
                .append(" procP=").append(formatDouble(bucket.processMaxMs)).append("ms")
                .append(" inQAvg=").append(formatDouble(bucket.getInboundQueueWaitAvgMs())).append("ms")
                .append(" outQAvg=").append(formatDouble(bucket.getOutboundQueueWaitAvgMs())).append("ms")
                .append('\n');
        }
        if (packetShown == 0) {
            sb.append("  - no packet data captured\n");
        }
        sb.append('\n');

        sb.append("Command Latency (Top 10):\n");
        int cmdShown = 0;
        for (CommandLatencyStats cmd : snapshot.commandStats) {
            if (cmdShown++ >= 10) {
                break;
            }
            sb.append("  - /").append(cmd.commandRoot)
                .append(" count=").append(cmd.count)
                .append(" queueAvg=").append(formatDouble(cmd.queueWaitAvgMs)).append("ms")
                .append(" execAvg=").append(formatDouble(cmd.execAvgMs)).append("ms")
                .append(" queueMax=").append(formatDouble(cmd.queueWaitMaxMs)).append("ms")
                .append(" execMax=").append(formatDouble(cmd.execMaxMs)).append("ms")
                .append('\n');
        }
        if (cmdShown == 0) {
            sb.append("  - no command latency data captured\n");
        }
        sb.append('\n');

        sb.append("Chunk Pipeline:\n");
        sb.append("  Chunks generated/loaded/unloaded: ")
            .append(snapshot.chunksGenerated).append(" / ")
            .append(snapshot.chunksLoaded).append(" / ")
            .append(snapshot.chunksUnloaded).append('\n');
        sb.append("  Chunk generation avg/max: ")
            .append(formatDouble(snapshot.avgChunkGenMs)).append("ms / ")
            .append(formatDouble(snapshot.maxChunkGenMs)).append("ms").append('\n');
        for (ChunkIoStats.OperationStats op : snapshot.chunkIoStats.operations) {
            sb.append("  - ").append(op.operation);
            if (op.worldName != null) {
                sb.append("@").append(op.worldName);
            }
            sb.append(": count=").append(op.count)
              .append(" avg=").append(formatDouble(op.avgMs)).append("ms")
              .append(" max=").append(formatDouble(op.maxMs)).append("ms")
              .append('\n');
        }
        sb.append('\n');

        sb.append("Per-player offenders (Top ").append(snapshot.offenders.size()).append("):\n");
        if (snapshot.offenders.isEmpty()) {
            sb.append("  - none\n");
        } else {
            for (OffenderStats offender : snapshot.offenders) {
                sb.append("  - ").append(offender.playerName)
                    .append(" score=").append(formatDouble(offender.score))
                    .append(" outQMax=").append(offender.outboundQueueMax)
                    .append(" inQMax=").append(offender.inboundQueueMax)
                    .append(" compQMax=").append(offender.chunkCompressionQueueMax)
                    .append(" qWaitAvg=").append(formatDouble(offender.queueWaitAvgMs)).append("ms")
                    .append(" procAvg=").append(formatDouble(offender.processAvgMs)).append("ms")
                    .append(" pingAvg=").append(formatDouble(offender.pingAvgMs)).append("ms")
                    .append('\n');
            }
        }
        sb.append('\n');

        sb.append("Threading Subsystem:\n");
        if (snapshot.threadingStats == null) {
            sb.append("  - unavailable\n");
        } else {
            sb.append("  Pending main-thread async tasks: ").append(snapshot.threadingStats.pendingMainThreadTasks).append('\n');
            for (Map.Entry<String, ThreadingManager.WorldAsyncStats> entry : snapshot.threadingStats.worlds.entrySet()) {
                ThreadingManager.WorldAsyncStats stats = entry.getValue();
                sb.append("  - ").append(entry.getKey())
                    .append(" | chunk(active/ready/total)=")
                    .append(stats.chunkGenActive).append('/').append(stats.chunkGenReady).append('/').append(stats.chunkGenTotal)
                    .append(" | lighting(active/pending/total)=")
                    .append(stats.lightingActive).append('/').append(stats.lightingPending).append('/').append(stats.lightingTotal)
                    .append(" | entity(active/pending/total)=")
                    .append(stats.entityActive).append('/').append(stats.entityPending).append('/').append(stats.entityTotal)
                    .append('\n');
            }
        }
        sb.append('\n');

        sb.append("Memory:\n");
        sb.append("  Samples: ").append(snapshot.memoryAnalysis.sampleCount).append('\n');
        sb.append("  Peak/Avg: ")
            .append(snapshot.memoryAnalysis.peakMemory / 1024 / 1024).append("MB / ")
            .append(snapshot.memoryAnalysis.avgMemory / 1024 / 1024).append("MB").append('\n');
        sb.append("  Growth: ").append(formatDouble(snapshot.memoryAnalysis.growthRateBytesPerSec / 1024D)).append(" KB/s").append('\n');
        sb.append("  Status: ").append(snapshot.memoryAnalysis.message).append('\n');
        sb.append('\n');

        if (snapshot.deep) {
            sb.append("Deep Sections (Top 20 by avg):\n");
            int shown = 0;
            for (ProfileData data : snapshot.profileData) {
                if (shown++ >= 20) {
                    break;
                }
                sb.append("  - ").append(data.path)
                    .append(" avg=").append(formatDouble(data.getAverageTime())).append("ms")
                    .append(" max=").append(formatDouble(data.maxTime)).append("ms")
                    .append(" calls=").append(data.callCount)
                    .append('\n');
            }
            sb.append('\n');
        }

        sb.append("Recommendations:\n");
        if (snapshot.findings.isEmpty()) {
            sb.append("  - No high-confidence bottlenecks detected in this window.\n");
        } else {
            int i = 0;
            for (BottleneckFinding finding : snapshot.findings) {
                if (i++ >= 8) {
                    break;
                }
                if (finding.recommendation != null && finding.recommendation.length() > 0) {
                    sb.append("  - ").append(finding.recommendation).append('\n');
                }
            }
        }
        sb.append('\n');

        sb.append(repeatString("=", 96)).append('\n');
        return sb.toString();
    }

    public String generateReportJson(ProfileSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        appendJsonField(sb, "version", snapshot.version, true, 2);
        appendJsonField(sb, "timestampMillis", snapshot.timestampMillis, true, 2);
        appendJsonField(sb, "deep", snapshot.deep, true, 2);
        appendJsonField(sb, "healthScore", snapshot.healthScore, true, 2);
        appendJsonField(sb, "tickCount", snapshot.tickCount, true, 2);
        appendJsonField(sb, "avgTickMs", snapshot.avgTickMs, true, 2);
        appendJsonField(sb, "maxTickMs", snapshot.maxTickMs, true, 2);

        indent(sb, 2).append("\"tickPercentiles\": {\n");
        appendJsonField(sb, "p50", snapshot.recentTickPercentiles.p50, true, 4);
        appendJsonField(sb, "p95", snapshot.recentTickPercentiles.p95, true, 4);
        appendJsonField(sb, "p99", snapshot.recentTickPercentiles.p99, false, 4);
        indent(sb, 2).append("},\n");

        indent(sb, 2).append("\"queue\": {\n");
        appendJsonField(sb, "commandQueueAvg", snapshot.queueStats.commandQueueAvg, true, 4);
        appendJsonField(sb, "commandQueueMax", snapshot.queueStats.commandQueueMax, true, 4);
        appendJsonField(sb, "inboundQueueAvg", snapshot.queueStats.inboundQueueAvg, true, 4);
        appendJsonField(sb, "inboundQueueMax", snapshot.queueStats.inboundQueueMax, true, 4);
        appendJsonField(sb, "outboundHighAvg", snapshot.queueStats.outboundHighAvg, true, 4);
        appendJsonField(sb, "outboundHighMax", snapshot.queueStats.outboundHighMax, true, 4);
        appendJsonField(sb, "outboundLowAvg", snapshot.queueStats.outboundLowAvg, true, 4);
        appendJsonField(sb, "outboundLowMax", snapshot.queueStats.outboundLowMax, true, 4);
        appendJsonField(sb, "outboundBytesAvg", snapshot.queueStats.outboundBytesAvg, true, 4);
        appendJsonField(sb, "outboundBytesMax", snapshot.queueStats.outboundBytesMax, true, 4);
        appendJsonField(sb, "chunkCompressionMax", snapshot.queueStats.chunkCompressionMax, true, 4);
        appendJsonField(sb, "chunkCompressionCapacity", snapshot.queueStats.chunkCompressionCapacity, true, 4);
        appendJsonField(sb, "netChunkZstdPackets", snapshot.queueStats.netChunkZstdPackets, true, 4);
        appendJsonField(sb, "netChunkZlibPackets", snapshot.queueStats.netChunkZlibPackets, true, 4);
        appendJsonField(sb, "netChunkZstdFallbacks", snapshot.queueStats.netChunkZstdFallbacks, true, 4);
        appendJsonField(sb, "netChunkCompressionFailures", snapshot.queueStats.netChunkCompressionFailures, true, 4);
        appendJsonField(sb, "netChunkZstdAvgMicros", snapshot.queueStats.netChunkZstdAvgMicros, true, 4);
        appendJsonField(sb, "netChunkZlibAvgMicros", snapshot.queueStats.netChunkZlibAvgMicros, true, 4);
        appendJsonField(sb, "regionWriteZstd", snapshot.queueStats.regionWriteZstd, true, 4);
        appendJsonField(sb, "regionWriteZlib", snapshot.queueStats.regionWriteZlib, true, 4);
        appendJsonField(sb, "regionWriteZstdFallbacks", snapshot.queueStats.regionWriteZstdFallbacks, true, 4);
        appendJsonField(sb, "regionWriteZstdAvgMicros", snapshot.queueStats.regionWriteZstdAvgMicros, true, 4);
        appendJsonField(sb, "regionWriteZlibAvgMicros", snapshot.queueStats.regionWriteZlibAvgMicros, true, 4);
        appendJsonField(sb, "movementCoalescedPackets", snapshot.queueStats.movementCoalescedPackets, true, 4);
        appendJsonField(sb, "movementDroppedPackets", snapshot.queueStats.movementDroppedPackets, true, 4);
        appendJsonField(sb, "entityTrackingSkippedNear", snapshot.queueStats.entityTrackingSkippedNear, true, 4);
        appendJsonField(sb, "entityTrackingSkippedMid", snapshot.queueStats.entityTrackingSkippedMid, true, 4);
        appendJsonField(sb, "entityTrackingSkippedFar", snapshot.queueStats.entityTrackingSkippedFar, false, 4);
        indent(sb, 2).append("},\n");

        indent(sb, 2).append("\"scheduler\": {\n");
        appendJsonField(sb, "samples", snapshot.schedulerStats.samples, true, 4);
        appendJsonField(sb, "movedAvg", snapshot.schedulerStats.movedAvg, true, 4);
        appendJsonField(sb, "executedAvg", snapshot.schedulerStats.executedAvg, true, 4);
        appendJsonField(sb, "leftoverAvg", snapshot.schedulerStats.leftoverAvg, true, 4);
        appendJsonField(sb, "leftoverMax", snapshot.schedulerStats.leftoverMax, true, 4);
        appendJsonField(sb, "heartbeatAvgMs", snapshot.schedulerStats.heartbeatAvgMs, true, 4);
        appendJsonField(sb, "heartbeatMaxMs", snapshot.schedulerStats.heartbeatMaxMs, false, 4);
        indent(sb, 2).append("},\n");

        indent(sb, 2).append("\"chunk\": {\n");
        appendJsonField(sb, "generated", snapshot.chunksGenerated, true, 4);
        appendJsonField(sb, "loaded", snapshot.chunksLoaded, true, 4);
        appendJsonField(sb, "unloaded", snapshot.chunksUnloaded, true, 4);
        appendJsonField(sb, "avgGenerationMs", snapshot.avgChunkGenMs, true, 4);
        appendJsonField(sb, "maxGenerationMs", snapshot.maxChunkGenMs, false, 4);
        indent(sb, 2).append("},\n");

        indent(sb, 2).append("\"adaptiveTracking\": {\n");
        appendJsonField(sb, "state", snapshot.entityTrackingState, true, 4);
        appendJsonField(sb, "normalTicks", snapshot.entityTrackingNormalTicks, true, 4);
        appendJsonField(sb, "pressureTicks", snapshot.entityTrackingPressureTicks, true, 4);
        appendJsonField(sb, "recoveryTicks", snapshot.entityTrackingRecoveryTicks, true, 4);
        appendJsonField(sb, "skippedNear", snapshot.entityTrackingSkippedNear, true, 4);
        appendJsonField(sb, "skippedMid", snapshot.entityTrackingSkippedMid, true, 4);
        appendJsonField(sb, "skippedFar", snapshot.entityTrackingSkippedFar, true, 4);
        appendJsonField(sb, "lastTransitionMillis", snapshot.entityTrackingLastTransitionMillis, false, 4);
        indent(sb, 2).append("},\n");

        indent(sb, 2).append("\"findings\": [\n");
        for (int i = 0; i < snapshot.findings.size(); i++) {
            BottleneckFinding finding = snapshot.findings.get(i);
            indent(sb, 4).append("{\n");
            appendJsonField(sb, "severity", finding.severity, true, 6);
            appendJsonField(sb, "title", finding.title, true, 6);
            appendJsonField(sb, "details", finding.details, true, 6);
            appendJsonField(sb, "recommendation", finding.recommendation, true, 6);
            appendJsonField(sb, "score", finding.score, false, 6);
            indent(sb, 4).append("}");
            if (i + 1 < snapshot.findings.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, 2).append("],\n");

        indent(sb, 2).append("\"topPackets\": [\n");
        for (int i = 0; i < snapshot.packetStats.size(); i++) {
            PacketStatsBucket bucket = snapshot.packetStats.get(i);
            indent(sb, 4).append("{\n");
            appendJsonField(sb, "packetId", bucket.packetId, true, 6);
            appendJsonField(sb, "packetClass", bucket.packetClass, true, 6);
            appendJsonField(sb, "inboundCount", bucket.inboundCount, true, 6);
            appendJsonField(sb, "outboundCount", bucket.outboundCount, true, 6);
            appendJsonField(sb, "processCount", bucket.processCount, true, 6);
            appendJsonField(sb, "inboundQueueWaitAvgMs", bucket.getInboundQueueWaitAvgMs(), true, 6);
            appendJsonField(sb, "outboundQueueWaitAvgMs", bucket.getOutboundQueueWaitAvgMs(), true, 6);
            appendJsonField(sb, "processAvgMs", bucket.getProcessAvgMs(), false, 6);
            indent(sb, 4).append("}");
            if (i + 1 < snapshot.packetStats.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, 2).append("],\n");

        indent(sb, 2).append("\"topOffenders\": [\n");
        for (int i = 0; i < snapshot.offenders.size(); i++) {
            OffenderStats offender = snapshot.offenders.get(i);
            indent(sb, 4).append("{\n");
            appendJsonField(sb, "player", offender.playerName, true, 6);
            appendJsonField(sb, "score", offender.score, true, 6);
            appendJsonField(sb, "outboundQueueMax", offender.outboundQueueMax, true, 6);
            appendJsonField(sb, "inboundQueueMax", offender.inboundQueueMax, true, 6);
            appendJsonField(sb, "chunkCompressionQueueMax", offender.chunkCompressionQueueMax, true, 6);
            appendJsonField(sb, "queueWaitAvgMs", offender.queueWaitAvgMs, true, 6);
            appendJsonField(sb, "processAvgMs", offender.processAvgMs, true, 6);
            appendJsonField(sb, "pingAvgMs", offender.pingAvgMs, false, 6);
            indent(sb, 4).append("}");
            if (i + 1 < snapshot.offenders.size()) {
                sb.append(',');
            }
            sb.append('\n');
        }
        indent(sb, 2).append("],\n");

        indent(sb, 2).append("\"threading\": {\n");
        if (snapshot.threadingStats != null) {
            appendJsonField(sb, "pendingMainThreadTasks", snapshot.threadingStats.pendingMainThreadTasks, true, 4);
            indent(sb, 4).append("\"worlds\": [\n");
            int worldIndex = 0;
            for (Map.Entry<String, ThreadingManager.WorldAsyncStats> entry : snapshot.threadingStats.worlds.entrySet()) {
                ThreadingManager.WorldAsyncStats worldStats = entry.getValue();
                indent(sb, 6).append("{\n");
                appendJsonField(sb, "world", entry.getKey(), true, 8);
                appendJsonField(sb, "chunkGenActive", worldStats.chunkGenActive, true, 8);
                appendJsonField(sb, "chunkGenReady", worldStats.chunkGenReady, true, 8);
                appendJsonField(sb, "chunkGenTotal", worldStats.chunkGenTotal, true, 8);
                appendJsonField(sb, "lightingActive", worldStats.lightingActive, true, 8);
                appendJsonField(sb, "lightingPending", worldStats.lightingPending, true, 8);
                appendJsonField(sb, "lightingTotal", worldStats.lightingTotal, true, 8);
                appendJsonField(sb, "entityActive", worldStats.entityActive, true, 8);
                appendJsonField(sb, "entityPending", worldStats.entityPending, true, 8);
                appendJsonField(sb, "entityTotal", worldStats.entityTotal, false, 8);
                indent(sb, 6).append("}");
                if (++worldIndex < snapshot.threadingStats.worlds.size()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, 4).append("]\n");
        } else {
            appendJsonField(sb, "pendingMainThreadTasks", 0, false, 4);
        }
        indent(sb, 2).append("},\n");

        indent(sb, 2).append("\"memory\": {\n");
        appendJsonField(sb, "sampleCount", snapshot.memoryAnalysis.sampleCount, true, 4);
        appendJsonField(sb, "peakMemoryBytes", snapshot.memoryAnalysis.peakMemory, true, 4);
        appendJsonField(sb, "avgMemoryBytes", snapshot.memoryAnalysis.avgMemory, true, 4);
        appendJsonField(sb, "growthRateBytesPerSec", snapshot.memoryAnalysis.growthRateBytesPerSec, true, 4);
        appendJsonField(sb, "message", snapshot.memoryAnalysis.message, false, 4);
        indent(sb, 2).append("}\n");

        sb.append("}\n");
        return sb.toString();
    }

    public ReportBundle saveReportBundle() {
        ProfileSnapshot snapshot = captureSnapshot(enabled);
        return saveReportBundle(snapshot);
    }

    public ReportBundle saveReportBundle(ProfileSnapshot snapshot) {
        try {
            File profilesDir = new File("profiles");
            if (!profilesDir.exists()) {
                profilesDir.mkdirs();
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String baseName = "server_profile_" + sdf.format(new Date(snapshot.timestampMillis));
            String textPath = null;
            String jsonPath = null;

            if (includeTextReports) {
                File reportFile = new File(profilesDir, baseName + ".txt");
                PrintWriter pw = new PrintWriter(new FileWriter(reportFile));
                pw.print(generateReportText(snapshot));
                pw.close();
                textPath = reportFile.getAbsolutePath();
            }

            if (includeJsonReports) {
                File jsonFile = new File(profilesDir, baseName + ".json");
                PrintWriter pw = new PrintWriter(new FileWriter(jsonFile));
                pw.print(generateReportJson(snapshot));
                pw.close();
                jsonPath = jsonFile.getAbsolutePath();
            }

            log.info("[ServerProfiler] Report bundle saved. txt=" + textPath + ", json=" + jsonPath);
            return new ReportBundle(textPath, jsonPath);
        } catch (Exception e) {
            log.warning("[ServerProfiler] Failed to save report bundle: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // Backward compatibility with old command paths
    public String generateReport() {
        return generateReportText(captureSnapshot(enabled));
    }

    public String saveReport() {
        ReportBundle bundle = saveReportBundle();
        if (bundle == null) {
            return null;
        }
        return bundle.textPath != null ? bundle.textPath : bundle.jsonPath;
    }

    public String getStatusSummary() {
        refreshConfigMaybe();

        StringBuilder sb = new StringBuilder();
        if (enabled) {
            long duration = profilingStartTime > 0 ? (System.currentTimeMillis() - profilingStartTime) / 1000L : 0L;
            sb.append("Profiler deep mode active ").append(duration).append("s");
        } else if (profilingStartTime > 0L) {
            sb.append("Profiler deep mode stopped");
        } else {
            sb.append("Profiler deep mode idle");
        }

        synchronized (telemetryLock) {
            sb.append(" | Ring: ").append(ringBufferSize).append('/').append(ringBuffer.length);
            sb.append(" | Packet buckets: ").append(packetStats.size());
            sb.append(" | Cmd buckets: ").append(commandStats.size());

            if (tickCount > 0) {
                double avgTick = totalTickTimeMs / tickCount;
                double tps = avgTick > 0D ? 1000.0D / avgTick : 0D;
                sb.append(" | AvgTPS: ").append(formatDouble(tps));
            }

            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024L / 1024L;
            sb.append(" | Mem: ").append(usedMb).append("MB");

            QueueStats statusQueueStats = buildQueueStatsLocked();
            sb.append(" | NetCodec(zs/zl/fb/fail)=")
                .append(statusQueueStats.netChunkZstdPackets).append('/')
                .append(statusQueueStats.netChunkZlibPackets).append('/')
                .append(statusQueueStats.netChunkZstdFallbacks).append('/')
                .append(statusQueueStats.netChunkCompressionFailures)
                .append(" zUs=").append(formatDouble(statusQueueStats.netChunkZstdAvgMicros));
            sb.append(" | RegionCodec(zs/zl/fb)=")
                .append(statusQueueStats.regionWriteZstd).append('/')
                .append(statusQueueStats.regionWriteZlib).append('/')
                .append(statusQueueStats.regionWriteZstdFallbacks)
                .append(" zUs=").append(formatDouble(statusQueueStats.regionWriteZstdAvgMicros));
        }

        sb.append(" | AutoSnap: ").append(autoSnapshotEnabled ? "on" : "off");
        if (lastAutoSnapshotTime > 0L) {
            long ageSec = (System.currentTimeMillis() - lastAutoSnapshotTime) / 1000L;
            sb.append(" (").append(ageSec).append("s ago)");
        }
        return sb.toString();
    }

    // ----------------------------
    // Helper methods
    // ----------------------------

    private void refreshConfigMaybe() {
        long now = System.currentTimeMillis();
        if (now - lastConfigRefreshMillis < 5000L) {
            return;
        }
        synchronized (telemetryLock) {
            if (now - lastConfigRefreshMillis < 5000L) {
                return;
            }
            lastConfigRefreshMillis = now;
        }
        refreshConfig();
    }

    private void refreshConfig() {
        try {
            PoseidonConfig config = PoseidonConfig.getInstance();
            boolean alwaysOn = config.getBoolean("settings.profiler.always-on.enabled", true);
            int newRingSeconds = Math.max(30, config.getInt("settings.profiler.ring-buffer.seconds", 300));
            boolean autoSnap = config.getBoolean("settings.profiler.auto-snapshot.enabled", true);
            int newAutoTick = Math.max(20, config.getInt("settings.profiler.auto-snapshot.tick-threshold-ms", 75));
            int newCooldown = Math.max(1, config.getInt("settings.profiler.auto-snapshot.cooldown-seconds", 30));
            int newTopOffenders = Math.max(1, config.getInt("settings.profiler.top-offenders.count", 10));
            boolean includeJson = config.getBoolean("settings.profiler.report.include-json", true);
            boolean includeText = config.getBoolean("settings.profiler.report.include-text", true);
            boolean packetBreakdown = config.getBoolean("settings.profiler.deep.packet-breakdown.enabled", true);
            boolean perPlayer = config.getBoolean("settings.profiler.deep.per-player.enabled", true);

            boolean resizeRing = false;
            synchronized (telemetryLock) {
                this.alwaysOnEnabled = alwaysOn;
                this.autoSnapshotEnabled = autoSnap;
                this.autoSnapshotTickThresholdMs = newAutoTick;
                this.autoSnapshotCooldownSeconds = newCooldown;
                this.topOffendersCount = newTopOffenders;
                this.includeJsonReports = includeJson;
                this.includeTextReports = includeText;
                this.deepPacketBreakdownEnabled = packetBreakdown;
                this.deepPerPlayerEnabled = perPlayer;

                if (this.ringBufferSeconds != newRingSeconds || this.ringBuffer == null) {
                    this.ringBufferSeconds = newRingSeconds;
                    resizeRing = true;
                }
            }

            if (resizeRing) {
                resizeRingBuffer(newRingSeconds);
            }
        } catch (Throwable ignored) {
            // Keep defaults if config is not ready yet.
        }
    }

    private void resizeRingBuffer(int seconds) {
        synchronized (telemetryLock) {
            RingBufferSample[] newBuffer = new RingBufferSample[Math.max(30, seconds)];
            List<RingBufferSample> history = copyRingBufferLocked();
            int start = Math.max(0, history.size() - newBuffer.length);
            int idx = 0;
            for (int i = start; i < history.size(); i++) {
                newBuffer[idx++] = history.get(i);
            }
            this.ringBuffer = newBuffer;
            this.ringBufferSize = idx;
            this.ringBufferWriteIndex = idx % newBuffer.length;
        }
    }

    private RingBufferSample getOrCreateCurrentSampleLocked(long nowMillis) {
        long epochSecond = nowMillis / 1000L;
        if (currentSample == null || currentSampleSecond != epochSecond) {
            if (currentSample != null) {
                ringBuffer[ringBufferWriteIndex] = new RingBufferSample(currentSample);
                ringBufferWriteIndex = (ringBufferWriteIndex + 1) % ringBuffer.length;
                if (ringBufferSize < ringBuffer.length) {
                    ringBufferSize++;
                }
            }
            currentSampleSecond = epochSecond;
            currentSample = new RingBufferSample(epochSecond);
        }
        return currentSample;
    }

    private List<RingBufferSample> copyRingBufferLocked() {
        List<RingBufferSample> out = new ArrayList<RingBufferSample>(ringBufferSize + 1);
        if (ringBufferSize > 0) {
            int startIndex = (ringBufferWriteIndex - ringBufferSize + ringBuffer.length) % ringBuffer.length;
            for (int i = 0; i < ringBufferSize; i++) {
                RingBufferSample sample = ringBuffer[(startIndex + i) % ringBuffer.length];
                if (sample != null) {
                    out.add(new RingBufferSample(sample));
                }
            }
        }
        if (currentSample != null) {
            out.add(new RingBufferSample(currentSample));
        }
        return out;
    }

    private TickPercentiles calculateTickPercentilesLocked() {
        TickPercentiles percentiles = new TickPercentiles();
        if (recentTickTimesMs.isEmpty()) {
            return percentiles;
        }

        List<Double> sorted = new ArrayList<Double>(recentTickTimesMs);
        Collections.sort(sorted);
        percentiles.p50 = percentile(sorted, 0.50D);
        percentiles.p95 = percentile(sorted, 0.95D);
        percentiles.p99 = percentile(sorted, 0.99D);
        return percentiles;
    }

    private double percentile(List<Double> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0D;
        }
        int idx = (int) Math.floor((sorted.size() - 1) * p);
        if (idx < 0) {
            idx = 0;
        } else if (idx >= sorted.size()) {
            idx = sorted.size() - 1;
        }
        return sorted.get(idx);
    }

    private static long computeNonNegativeDelta(long current, long previous) {
        if (current >= previous) {
            return current - previous;
        }
        return Math.max(0L, current);
    }

    private static double nanosPerOperationMicros(long totalNanos, long operationCount) {
        if (totalNanos <= 0L || operationCount <= 0L) {
            return 0D;
        }
        return (totalNanos / (double) operationCount) / 1000D;
    }

    private QueueStats buildQueueStatsLocked() {
        QueueStats stats = new QueueStats();
        List<RingBufferSample> samples = copyRingBufferLocked();
        if (samples.isEmpty()) {
            return stats;
        }

        double cmdTotal = 0D;
        double inTotal = 0D;
        double highTotal = 0D;
        double lowTotal = 0D;
        double outBytesTotal = 0D;
        long sampleCount = 0L;
        long netChunkZstdNanosTotal = 0L;
        long netChunkZlibNanosTotal = 0L;
        long regionWriteZstdNanosTotal = 0L;
        long regionWriteZlibNanosTotal = 0L;

        for (RingBufferSample sample : samples) {
            long divisor = Math.max(1L, sample.queueSampleCount);
            cmdTotal += sample.commandQueueDepthTotal / (double) divisor;
            inTotal += sample.inboundQueueDepthTotal / (double) divisor;
            highTotal += sample.outboundHighQueueDepthTotal / (double) divisor;
            lowTotal += sample.outboundLowQueueDepthTotal / (double) divisor;
            outBytesTotal += sample.outboundQueuedBytesTotal / (double) divisor;
            sampleCount++;

            if (sample.commandQueueDepthMax > stats.commandQueueMax) {
                stats.commandQueueMax = sample.commandQueueDepthMax;
            }
            if (sample.inboundQueueDepthMax > stats.inboundQueueMax) {
                stats.inboundQueueMax = sample.inboundQueueDepthMax;
            }
            if (sample.outboundHighQueueDepthMax > stats.outboundHighMax) {
                stats.outboundHighMax = sample.outboundHighQueueDepthMax;
            }
            if (sample.outboundLowQueueDepthMax > stats.outboundLowMax) {
                stats.outboundLowMax = sample.outboundLowQueueDepthMax;
            }
            if (sample.outboundQueuedBytesMax > stats.outboundBytesMax) {
                stats.outboundBytesMax = sample.outboundQueuedBytesMax;
            }
            if (sample.chunkCompressionQueueMax > stats.chunkCompressionMax) {
                stats.chunkCompressionMax = sample.chunkCompressionQueueMax;
            }
            if (sample.chunkCompressionQueueCapacity > stats.chunkCompressionCapacity) {
                stats.chunkCompressionCapacity = sample.chunkCompressionQueueCapacity;
            }
            if (sample.pendingLoginsMax > stats.pendingLoginsMax) {
                stats.pendingLoginsMax = sample.pendingLoginsMax;
            }
            if (sample.activeHandlersMax > stats.activeHandlersMax) {
                stats.activeHandlersMax = sample.activeHandlersMax;
            }
            stats.movementCoalescedPackets += sample.movementCoalescedPackets;
            stats.movementDroppedPackets += sample.movementDroppedPackets;
            stats.entityTrackingSkippedNear += sample.entityTrackingSkippedNear;
            stats.entityTrackingSkippedMid += sample.entityTrackingSkippedMid;
            stats.entityTrackingSkippedFar += sample.entityTrackingSkippedFar;
            stats.netChunkZstdPackets += sample.netChunkZstdPackets;
            stats.netChunkZlibPackets += sample.netChunkZlibPackets;
            stats.netChunkZstdFallbacks += sample.netChunkZstdFallbacks;
            stats.netChunkCompressionFailures += sample.netChunkCompressionFailures;
            stats.regionWriteZstd += sample.regionWriteZstd;
            stats.regionWriteZlib += sample.regionWriteZlib;
            stats.regionWriteZstdFallbacks += sample.regionWriteZstdFallbacks;
            netChunkZstdNanosTotal += sample.netChunkZstdCompressNanos;
            netChunkZlibNanosTotal += sample.netChunkZlibCompressNanos;
            regionWriteZstdNanosTotal += sample.regionWriteZstdNanos;
            regionWriteZlibNanosTotal += sample.regionWriteZlibNanos;
        }

        if (sampleCount > 0L) {
            stats.commandQueueAvg = cmdTotal / sampleCount;
            stats.inboundQueueAvg = inTotal / sampleCount;
            stats.outboundHighAvg = highTotal / sampleCount;
            stats.outboundLowAvg = lowTotal / sampleCount;
            stats.outboundBytesAvg = outBytesTotal / sampleCount;
        }

        stats.netChunkZstdAvgMicros = nanosPerOperationMicros(netChunkZstdNanosTotal, stats.netChunkZstdPackets);
        stats.netChunkZlibAvgMicros = nanosPerOperationMicros(netChunkZlibNanosTotal, stats.netChunkZlibPackets);
        stats.regionWriteZstdAvgMicros = nanosPerOperationMicros(regionWriteZstdNanosTotal, stats.regionWriteZstd);
        stats.regionWriteZlibAvgMicros = nanosPerOperationMicros(regionWriteZlibNanosTotal, stats.regionWriteZlib);

        return stats;
    }

    private SchedulerStats buildSchedulerStatsLocked() {
        SchedulerStats stats = new SchedulerStats();
        List<RingBufferSample> samples = copyRingBufferLocked();
        for (RingBufferSample sample : samples) {
            if (sample.schedulerSamples <= 0) {
                continue;
            }
            stats.samples += sample.schedulerSamples;
            stats.movedTotal += sample.schedulerMovedToSyncedTotal;
            stats.executedTotal += sample.schedulerExecutedTotal;
            stats.leftoverTotal += sample.schedulerLeftoverTotal;
            stats.heartbeatTotalMs += sample.schedulerHeartbeatTotalMs;
            if (sample.schedulerLeftoverMax > stats.leftoverMax) {
                stats.leftoverMax = sample.schedulerLeftoverMax;
            }
            if (sample.schedulerHeartbeatMaxMs > stats.heartbeatMaxMs) {
                stats.heartbeatMaxMs = sample.schedulerHeartbeatMaxMs;
            }
        }

        if (stats.samples > 0) {
            stats.movedAvg = stats.movedTotal / (double) stats.samples;
            stats.executedAvg = stats.executedTotal / (double) stats.samples;
            stats.leftoverAvg = stats.leftoverTotal / (double) stats.samples;
            stats.heartbeatAvgMs = stats.heartbeatTotalMs / (double) stats.samples;
        }

        return stats;
    }

    private ChunkIoStats buildChunkIoStatsLocked() {
        ChunkIoStats stats = new ChunkIoStats();
        List<ChunkOperationStats> operations = new ArrayList<ChunkOperationStats>(chunkOperationStats.values());
        Collections.sort(operations, new Comparator<ChunkOperationStats>() {
            @Override
            public int compare(ChunkOperationStats a, ChunkOperationStats b) {
                return Double.compare(b.totalMs, a.totalMs);
            }
        });

        for (ChunkOperationStats op : operations) {
            ChunkIoStats.OperationStats converted = new ChunkIoStats.OperationStats();
            converted.operation = op.operation;
            converted.worldName = op.worldName;
            converted.count = op.count;
            converted.totalMs = op.totalMs;
            converted.maxMs = op.maxMs;
            converted.avgMs = op.count > 0 ? op.totalMs / op.count : 0D;
            stats.operations.add(converted);
            stats.totalCount += op.count;
            stats.totalMs += op.totalMs;
            if (op.maxMs > stats.maxMs) {
                stats.maxMs = op.maxMs;
            }
        }
        stats.avgMs = stats.totalCount > 0 ? stats.totalMs / stats.totalCount : 0D;
        return stats;
    }

    private List<PacketStatsBucket> topPacketBucketsLocked(int limit) {
        List<PacketStatsBucket> buckets = new ArrayList<PacketStatsBucket>();
        if (!deepPacketBreakdownEnabled) {
            return buckets;
        }

        for (PacketStatsBucket bucket : packetStats.values()) {
            buckets.add(new PacketStatsBucket(bucket));
        }
        Collections.sort(buckets, new Comparator<PacketStatsBucket>() {
            @Override
            public int compare(PacketStatsBucket a, PacketStatsBucket b) {
                double as = a.processTotalMs + a.inboundQueueWaitTotalMs + a.outboundQueueWaitTotalMs;
                double bs = b.processTotalMs + b.inboundQueueWaitTotalMs + b.outboundQueueWaitTotalMs;
                return Double.compare(bs, as);
            }
        });
        if (buckets.size() > limit) {
            return new ArrayList<PacketStatsBucket>(buckets.subList(0, limit));
        }
        return buckets;
    }

    private List<CommandLatencyStats> topCommandStatsLocked(int limit) {
        List<CommandLatencyStats> stats = new ArrayList<CommandLatencyStats>();
        for (CommandStats cmd : commandStats.values()) {
            CommandLatencyStats out = new CommandLatencyStats();
            out.commandRoot = cmd.commandRoot;
            out.count = cmd.count;
            out.queueWaitAvgMs = cmd.count > 0 ? cmd.queueWaitTotalMs / cmd.count : 0D;
            out.execAvgMs = cmd.count > 0 ? cmd.execTotalMs / cmd.count : 0D;
            out.queueWaitMaxMs = cmd.queueWaitMaxMs;
            out.execMaxMs = cmd.execMaxMs;
            stats.add(out);
        }

        Collections.sort(stats, new Comparator<CommandLatencyStats>() {
            @Override
            public int compare(CommandLatencyStats a, CommandLatencyStats b) {
                double as = a.queueWaitAvgMs + a.execAvgMs;
                double bs = b.queueWaitAvgMs + b.execAvgMs;
                return Double.compare(bs, as);
            }
        });

        if (stats.size() > limit) {
            return new ArrayList<CommandLatencyStats>(stats.subList(0, limit));
        }
        return stats;
    }

    private List<OffenderStats> topOffendersLocked(int limit) {
        List<OffenderStats> list = new ArrayList<OffenderStats>();
        if (!deepPerPlayerEnabled) {
            return list;
        }

        for (OffenderAccumulator acc : offenderStats.values()) {
            OffenderStats out = new OffenderStats();
            out.playerName = acc.playerName;
            out.outboundQueueMax = acc.outboundQueueMax;
            out.inboundQueueMax = acc.inboundQueueMax;
            out.chunkCompressionQueueMax = acc.chunkCompressionQueueMax;
            out.queueWaitAvgMs = acc.packetSamples > 0 ? (acc.totalIngressQueueWaitMs + acc.totalEgressQueueWaitMs) / acc.packetSamples : 0D;
            out.processAvgMs = acc.packetSamples > 0 ? acc.totalProcessMs / acc.packetSamples : 0D;
            out.pingAvgMs = acc.samples > 0 ? acc.pingTotal / (double) acc.samples : 0D;
            out.score = (out.outboundQueueMax * 0.8D)
                + (out.inboundQueueMax * 0.6D)
                + (out.chunkCompressionQueueMax * 0.9D)
                + (out.queueWaitAvgMs * 1.2D)
                + (out.processAvgMs * 1.0D);
            list.add(out);
        }

        Collections.sort(list, new Comparator<OffenderStats>() {
            @Override
            public int compare(OffenderStats a, OffenderStats b) {
                return Double.compare(b.score, a.score);
            }
        });

        if (list.size() > limit) {
            return new ArrayList<OffenderStats>(list.subList(0, limit));
        }
        return list;
    }

    private MemoryAnalysis analyzeMemoryLocked() {
        if (memorySamples.size() < 2) {
            MemoryAnalysis analysis = new MemoryAnalysis(false, 0D, 0L, 0L, "Insufficient samples", memorySamples.size());
            return analysis;
        }

        long startUsed = memorySamples.get(0).usedMemory;
        long endUsed = memorySamples.get(memorySamples.size() - 1).usedMemory;
        long startTime = memorySamples.get(0).timestamp;
        long endTime = memorySamples.get(memorySamples.size() - 1).timestamp;

        long peak = 0L;
        long sum = 0L;
        for (MemorySample sample : memorySamples) {
            sum += sample.usedMemory;
            if (sample.usedMemory > peak) {
                peak = sample.usedMemory;
            }
        }

        long durationMs = Math.max(1L, endTime - startTime);
        double growth = ((endUsed - startUsed) * 1000.0D) / durationMs;
        long avg = sum / Math.max(1, memorySamples.size());
        long absoluteGrowth = endUsed - startUsed;
        boolean hasLongEnoughWindow = durationMs >= 300_000L; // 5 minutes
        boolean potentialLeak = hasLongEnoughWindow
            && growth > 15_000D
            && absoluteGrowth > (64L * 1024L * 1024L);
        String message;
        if (potentialLeak) {
            message = "Potential leak: sustained memory growth";
        } else if (growth > 3_000D) {
            message = hasLongEnoughWindow ? "Memory rising" : "Memory rising (warmup/terrain generation likely)";
        } else if (growth < -3_000D) {
            message = "Memory falling (GC active)";
        } else {
            message = "Memory stable";
        }
        return new MemoryAnalysis(potentialLeak, growth, peak, avg, message, memorySamples.size());
    }

    private List<ProfileData> copyProfileData() {
        List<ProfileData> copied = new ArrayList<ProfileData>(profileData.values().size());
        for (ProfileData data : profileData.values()) {
            copied.add(new ProfileData(data));
        }
        Collections.sort(copied, new Comparator<ProfileData>() {
            @Override
            public int compare(ProfileData a, ProfileData b) {
                return Double.compare(b.getAverageTime(), a.getAverageTime());
            }
        });
        return copied;
    }

    private List<SlowCall> copySlowCalls() {
        List<SlowCall> copied;
        synchronized (slowCalls) {
            copied = new ArrayList<SlowCall>(slowCalls);
        }
        Collections.sort(copied, new Comparator<SlowCall>() {
            @Override
            public int compare(SlowCall a, SlowCall b) {
                return Double.compare(b.durationMs, a.durationMs);
            }
        });
        return copied;
    }

    private List<BottleneckFinding> buildFindings(ProfileSnapshot snapshot) {
        List<BottleneckFinding> findings = new ArrayList<BottleneckFinding>();

        if (snapshot.recentTickPercentiles.p95 >= 55D) {
            findings.add(new BottleneckFinding("critical",
                "Tick overruns",
                "p95 tick is " + formatDouble(snapshot.recentTickPercentiles.p95) + "ms",
                "Reduce main-thread work per tick (chunk generation/compression/scheduler burst) and inspect top packet + chunk sections.",
                90D));
        } else if (snapshot.recentTickPercentiles.p95 >= 45D) {
            findings.add(new BottleneckFinding("high",
                "Tick pressure",
                "p95 tick is " + formatDouble(snapshot.recentTickPercentiles.p95) + "ms",
                "Watch scheduler backlog and packet queue waits; run deep profile during the spike window.",
                75D));
        }

        if (snapshot.queueStats.commandQueueMax > 20 || snapshot.queueStats.commandQueueAvg > 5D) {
            findings.add(new BottleneckFinding("high",
                "Command queue delay",
                "command queue avg/max " + formatDouble(snapshot.queueStats.commandQueueAvg) + "/" + snapshot.queueStats.commandQueueMax,
                "Investigate command dispatch and plugin command handlers for long sync work.",
                70D));
        }

        if (snapshot.queueStats.inboundQueueMax > 80 || snapshot.queueStats.outboundHighMax > 80 || snapshot.queueStats.outboundLowMax > 120) {
            findings.add(new BottleneckFinding("high",
                "Network queue saturation",
                "in/out queues peaked at " + snapshot.queueStats.inboundQueueMax + "/" + snapshot.queueStats.outboundHighMax + "/" + snapshot.queueStats.outboundLowMax,
                "Inspect packet table for high queue waits; tune chunk send rate and compression worker count/capacity.",
                78D));
        }

        if (snapshot.queueStats.movementDroppedPackets > 0) {
            findings.add(new BottleneckFinding("medium",
                "Movement packet shedding",
                "dropped superseded movement packets " + snapshot.queueStats.movementDroppedPackets,
                "If jank appears, lower pressure by reducing far-entity tracking load or increasing packet throughput capacity.",
                58D));
        }

        if (snapshot.queueStats.chunkCompressionCapacity > 0 &&
            snapshot.queueStats.chunkCompressionMax >= (snapshot.queueStats.chunkCompressionCapacity * 8) / 10) {
            findings.add(new BottleneckFinding("high",
                "Chunk compression backlog",
                "compression queue reached " + snapshot.queueStats.chunkCompressionMax + "/" + snapshot.queueStats.chunkCompressionCapacity,
                "Increase chunk compression threads/capacity or reduce burst chunk production.",
                74D));
        }

        if (snapshot.schedulerStats.leftoverMax > 0 || snapshot.schedulerStats.heartbeatAvgMs > 10D) {
            findings.add(new BottleneckFinding("medium",
                "Scheduler saturation",
                "leftover max " + snapshot.schedulerStats.leftoverMax + ", heartbeat avg " + formatDouble(snapshot.schedulerStats.heartbeatAvgMs) + "ms",
                "Trim heavy synchronous tasks and split them over multiple ticks.",
                62D));
        }

        if (snapshot.chunkIoStats.maxMs > 30D || snapshot.avgChunkGenMs > 25D) {
            findings.add(new BottleneckFinding("medium",
                "Chunk/IO stall risk",
                "chunk op max " + formatDouble(snapshot.chunkIoStats.maxMs) + "ms, gen avg " + formatDouble(snapshot.avgChunkGenMs) + "ms",
                "Profile chunk load/save/generate operations and validate async readiness path capacity.",
                60D));
        }

        if (snapshot.memoryAnalysis.potentialLeak) {
            findings.add(new BottleneckFinding("medium",
                "Memory growth",
                snapshot.memoryAnalysis.message,
                "Check recent allocations/plugins and capture a longer run to confirm sustained growth.",
                55D));
        }

        Collections.sort(findings, new Comparator<BottleneckFinding>() {
            @Override
            public int compare(BottleneckFinding a, BottleneckFinding b) {
                return Double.compare(b.score, a.score);
            }
        });
        return findings;
    }

    private int calculateHealthScore(ProfileSnapshot snapshot) {
        int score = 100;

        score -= (int) Math.min(35D, Math.max(0D, snapshot.recentTickPercentiles.p95 - 40D));
        score -= (int) Math.min(20D, Math.max(0D, snapshot.recentTickPercentiles.p99 - 60D) * 0.5D);
        score -= (int) Math.min(12D, snapshot.queueStats.commandQueueAvg);
        score -= (int) Math.min(12D, snapshot.queueStats.inboundQueueAvg * 0.25D);
        score -= (int) Math.min(12D, snapshot.queueStats.outboundHighAvg * 0.25D);
        if (snapshot.schedulerStats.leftoverMax > 0) {
            score -= Math.min(8, snapshot.schedulerStats.leftoverMax);
        }
        if (snapshot.memoryAnalysis.potentialLeak) {
            score -= 8;
        }

        if (score < 0) {
            return 0;
        }
        return Math.min(score, 100);
    }

    private PacketStatsBucket getOrCreatePacketBucketLocked(int packetId, String packetClass) {
        String clazz = packetClass == null ? "unknown" : packetClass;
        String key = packetId + ":" + clazz;
        PacketStatsBucket bucket = packetStats.get(key);
        if (bucket == null) {
            bucket = new PacketStatsBucket();
            bucket.packetKey = key;
            bucket.packetId = packetId;
            bucket.packetClass = clazz;
            packetStats.put(key, bucket);
        }
        return bucket;
    }

    private OffenderAccumulator getOrCreateOffenderLocked(String playerName) {
        OffenderAccumulator offender = offenderStats.get(playerName);
        if (offender == null) {
            if (offenderStats.size() > 2048) {
                String firstKey = offenderStats.keySet().iterator().next();
                offenderStats.remove(firstKey);
            }
            offender = new OffenderAccumulator(playerName);
            offenderStats.put(playerName, offender);
        }
        return offender;
    }

    private void recordMemorySampleLocked(long now) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long total = rt.totalMemory();
        long max = rt.maxMemory();

        memorySamples.add(new MemorySample(now, used, total, max));
        while (memorySamples.size() > MAX_MEMORY_SAMPLES) {
            memorySamples.remove(0);
        }
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String repeatString(String str, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 32) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int pad = hex.length(); pad < 4; pad++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static StringBuilder indent(StringBuilder sb, int spaces) {
        for (int i = 0; i < spaces; i++) {
            sb.append(' ');
        }
        return sb;
    }

    private static void appendJsonField(StringBuilder sb, String key, String value, boolean comma, int indent) {
        indent(sb, indent).append('"').append(jsonEscape(key)).append("\": \"").append(jsonEscape(value)).append('"');
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendJsonField(StringBuilder sb, String key, long value, boolean comma, int indent) {
        indent(sb, indent).append('"').append(jsonEscape(key)).append("\": ").append(value);
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendJsonField(StringBuilder sb, String key, int value, boolean comma, int indent) {
        appendJsonField(sb, key, (long) value, comma, indent);
    }

    private static void appendJsonField(StringBuilder sb, String key, boolean value, boolean comma, int indent) {
        indent(sb, indent).append('"').append(jsonEscape(key)).append("\": ").append(value);
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    private static void appendJsonField(StringBuilder sb, String key, double value, boolean comma, int indent) {
        double v = value;
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            v = 0D;
        }
        indent(sb, indent).append('"').append(jsonEscape(key)).append("\": ").append(formatDouble(v));
        if (comma) {
            sb.append(',');
        }
        sb.append('\n');
    }

    // ----------------------------
    // Public data model types
    // ----------------------------

    public static class ProfileSnapshot {
        public int version;
        public long timestampMillis;
        public boolean deep;
        public boolean autoCaptured;
        public boolean alwaysOnEnabled;
        public long sessionStartMillis;
        public long sessionEndMillis;
        public int ringBufferRetentionSeconds;
        public long lastAutoSnapshotMillis;
        public int autoSnapshotCount;
        public int healthScore;

        public long tickCount;
        public double avgTickMs;
        public double maxTickMs;
        public TickPercentiles recentTickPercentiles = new TickPercentiles();

        public QueueStats queueStats = new QueueStats();
        public SchedulerStats schedulerStats = new SchedulerStats();
        public ChunkIoStats chunkIoStats = new ChunkIoStats();
        public MemoryAnalysis memoryAnalysis = new MemoryAnalysis(false, 0D, 0L, 0L, "Insufficient samples", 0);

        public long chunksGenerated;
        public long chunksLoaded;
        public long chunksUnloaded;
        public double avgChunkGenMs;
        public double maxChunkGenMs;
        public long movementCoalescedPackets;
        public long movementDroppedPackets;
        public long entityTrackingSkippedNear;
        public long entityTrackingSkippedMid;
        public long entityTrackingSkippedFar;
        public String entityTrackingState = "NORMAL";
        public long entityTrackingNormalTicks;
        public long entityTrackingPressureTicks;
        public long entityTrackingRecoveryTicks;
        public long entityTrackingLastTransitionMillis;

        public List<RingBufferSample> ringBufferSamples = new ArrayList<RingBufferSample>();
        public List<PacketStatsBucket> packetStats = new ArrayList<PacketStatsBucket>();
        public List<CommandLatencyStats> commandStats = new ArrayList<CommandLatencyStats>();
        public List<OffenderStats> offenders = new ArrayList<OffenderStats>();
        public List<BottleneckFinding> findings = new ArrayList<BottleneckFinding>();

        public List<ProfileData> profileData = new ArrayList<ProfileData>();
        public List<SlowCall> slowCalls = new ArrayList<SlowCall>();

        public ThreadingManager.ThreadingStatsSnapshot threadingStats;
    }

    public static class RingBufferSample {
        public long epochSecond;
        public int tickCount;
        public double tickTotalMs;
        public double tickMaxMs;
        public int ticksOver50Ms;
        public int ticksOver75Ms;
        public long commandQueueDepthTotal;
        public int commandQueueDepthMax;
        public int worldCountMax;
        public int playerCountMax;

        public long inboundQueueDepthTotal;
        public long outboundHighQueueDepthTotal;
        public long outboundLowQueueDepthTotal;
        public long outboundQueuedBytesTotal;
        public int inboundQueueDepthMax;
        public int outboundHighQueueDepthMax;
        public int outboundLowQueueDepthMax;
        public int outboundQueuedBytesMax;
        public int pendingLoginsMax;
        public int activeHandlersMax;
        public int chunkCompressionQueueMax;
        public int chunkCompressionQueueCapacity;
        public long netChunkZstdPackets;
        public long netChunkZlibPackets;
        public long netChunkZstdFallbacks;
        public long netChunkCompressionFailures;
        public long netChunkZstdCompressNanos;
        public long netChunkZlibCompressNanos;
        public long regionWriteZstd;
        public long regionWriteZlib;
        public long regionWriteZstdFallbacks;
        public long regionWriteZstdNanos;
        public long regionWriteZlibNanos;
        public int queueSampleCount;

        public int schedulerSamples;
        public long schedulerMovedToSyncedTotal;
        public long schedulerExecutedTotal;
        public long schedulerLeftoverTotal;
        public int schedulerLeftoverMax;
        public double schedulerHeartbeatTotalMs;
        public double schedulerHeartbeatMaxMs;

        public long packetIngressCount;
        public long packetEgressCount;
        public long packetProcessCount;
        public double packetIngressQueueWaitTotalMs;
        public double packetIngressQueueWaitMaxMs;
        public double packetEgressQueueWaitTotalMs;
        public double packetEgressQueueWaitMaxMs;
        public double packetProcessTotalMs;
        public double packetProcessMaxMs;

        public int commandSamples;
        public double commandQueueWaitTotalMs;
        public double commandQueueWaitMaxMs;
        public double commandExecutionTotalMs;
        public double commandExecutionMaxMs;

        public long chunkIoCount;
        public double chunkIoTotalMs;
        public double chunkIoMaxMs;
        public long movementCoalescedPackets;
        public long movementDroppedPackets;
        public long entityTrackingSkippedNear;
        public long entityTrackingSkippedMid;
        public long entityTrackingSkippedFar;
        public long entityTrackingNormalTicks;
        public long entityTrackingPressureTicks;
        public long entityTrackingRecoveryTicks;
        public String entityTrackingState = "NORMAL";
        public long entityTrackingLastTransitionMillis;

        public RingBufferSample(long epochSecond) {
            this.epochSecond = epochSecond;
        }

        public RingBufferSample(RingBufferSample other) {
            this.epochSecond = other.epochSecond;
            this.tickCount = other.tickCount;
            this.tickTotalMs = other.tickTotalMs;
            this.tickMaxMs = other.tickMaxMs;
            this.ticksOver50Ms = other.ticksOver50Ms;
            this.ticksOver75Ms = other.ticksOver75Ms;
            this.commandQueueDepthTotal = other.commandQueueDepthTotal;
            this.commandQueueDepthMax = other.commandQueueDepthMax;
            this.worldCountMax = other.worldCountMax;
            this.playerCountMax = other.playerCountMax;
            this.inboundQueueDepthTotal = other.inboundQueueDepthTotal;
            this.outboundHighQueueDepthTotal = other.outboundHighQueueDepthTotal;
            this.outboundLowQueueDepthTotal = other.outboundLowQueueDepthTotal;
            this.outboundQueuedBytesTotal = other.outboundQueuedBytesTotal;
            this.inboundQueueDepthMax = other.inboundQueueDepthMax;
            this.outboundHighQueueDepthMax = other.outboundHighQueueDepthMax;
            this.outboundLowQueueDepthMax = other.outboundLowQueueDepthMax;
            this.outboundQueuedBytesMax = other.outboundQueuedBytesMax;
            this.pendingLoginsMax = other.pendingLoginsMax;
            this.activeHandlersMax = other.activeHandlersMax;
            this.chunkCompressionQueueMax = other.chunkCompressionQueueMax;
            this.chunkCompressionQueueCapacity = other.chunkCompressionQueueCapacity;
            this.netChunkZstdPackets = other.netChunkZstdPackets;
            this.netChunkZlibPackets = other.netChunkZlibPackets;
            this.netChunkZstdFallbacks = other.netChunkZstdFallbacks;
            this.netChunkCompressionFailures = other.netChunkCompressionFailures;
            this.netChunkZstdCompressNanos = other.netChunkZstdCompressNanos;
            this.netChunkZlibCompressNanos = other.netChunkZlibCompressNanos;
            this.regionWriteZstd = other.regionWriteZstd;
            this.regionWriteZlib = other.regionWriteZlib;
            this.regionWriteZstdFallbacks = other.regionWriteZstdFallbacks;
            this.regionWriteZstdNanos = other.regionWriteZstdNanos;
            this.regionWriteZlibNanos = other.regionWriteZlibNanos;
            this.queueSampleCount = other.queueSampleCount;
            this.schedulerSamples = other.schedulerSamples;
            this.schedulerMovedToSyncedTotal = other.schedulerMovedToSyncedTotal;
            this.schedulerExecutedTotal = other.schedulerExecutedTotal;
            this.schedulerLeftoverTotal = other.schedulerLeftoverTotal;
            this.schedulerLeftoverMax = other.schedulerLeftoverMax;
            this.schedulerHeartbeatTotalMs = other.schedulerHeartbeatTotalMs;
            this.schedulerHeartbeatMaxMs = other.schedulerHeartbeatMaxMs;
            this.packetIngressCount = other.packetIngressCount;
            this.packetEgressCount = other.packetEgressCount;
            this.packetProcessCount = other.packetProcessCount;
            this.packetIngressQueueWaitTotalMs = other.packetIngressQueueWaitTotalMs;
            this.packetIngressQueueWaitMaxMs = other.packetIngressQueueWaitMaxMs;
            this.packetEgressQueueWaitTotalMs = other.packetEgressQueueWaitTotalMs;
            this.packetEgressQueueWaitMaxMs = other.packetEgressQueueWaitMaxMs;
            this.packetProcessTotalMs = other.packetProcessTotalMs;
            this.packetProcessMaxMs = other.packetProcessMaxMs;
            this.commandSamples = other.commandSamples;
            this.commandQueueWaitTotalMs = other.commandQueueWaitTotalMs;
            this.commandQueueWaitMaxMs = other.commandQueueWaitMaxMs;
            this.commandExecutionTotalMs = other.commandExecutionTotalMs;
            this.commandExecutionMaxMs = other.commandExecutionMaxMs;
            this.chunkIoCount = other.chunkIoCount;
            this.chunkIoTotalMs = other.chunkIoTotalMs;
            this.chunkIoMaxMs = other.chunkIoMaxMs;
            this.movementCoalescedPackets = other.movementCoalescedPackets;
            this.movementDroppedPackets = other.movementDroppedPackets;
            this.entityTrackingSkippedNear = other.entityTrackingSkippedNear;
            this.entityTrackingSkippedMid = other.entityTrackingSkippedMid;
            this.entityTrackingSkippedFar = other.entityTrackingSkippedFar;
            this.entityTrackingNormalTicks = other.entityTrackingNormalTicks;
            this.entityTrackingPressureTicks = other.entityTrackingPressureTicks;
            this.entityTrackingRecoveryTicks = other.entityTrackingRecoveryTicks;
            this.entityTrackingState = other.entityTrackingState;
            this.entityTrackingLastTransitionMillis = other.entityTrackingLastTransitionMillis;
        }
    }

    public static class PacketStatsBucket {
        public String packetKey;
        public int packetId;
        public String packetClass;
        public long inboundCount;
        public long outboundCount;
        public long processCount;
        public long outboundBytes;
        public long highPriorityCount;
        public long lowPriorityCount;
        public int inboundQueueDepthMax;
        public double inboundQueueWaitTotalMs;
        public double inboundQueueWaitMaxMs;
        public double outboundQueueWaitTotalMs;
        public double outboundQueueWaitMaxMs;
        public double processTotalMs;
        public double processMaxMs;

        public PacketStatsBucket() {
        }

        public PacketStatsBucket(PacketStatsBucket other) {
            this.packetKey = other.packetKey;
            this.packetId = other.packetId;
            this.packetClass = other.packetClass;
            this.inboundCount = other.inboundCount;
            this.outboundCount = other.outboundCount;
            this.processCount = other.processCount;
            this.outboundBytes = other.outboundBytes;
            this.highPriorityCount = other.highPriorityCount;
            this.lowPriorityCount = other.lowPriorityCount;
            this.inboundQueueDepthMax = other.inboundQueueDepthMax;
            this.inboundQueueWaitTotalMs = other.inboundQueueWaitTotalMs;
            this.inboundQueueWaitMaxMs = other.inboundQueueWaitMaxMs;
            this.outboundQueueWaitTotalMs = other.outboundQueueWaitTotalMs;
            this.outboundQueueWaitMaxMs = other.outboundQueueWaitMaxMs;
            this.processTotalMs = other.processTotalMs;
            this.processMaxMs = other.processMaxMs;
        }

        public double getInboundQueueWaitAvgMs() {
            return processCount > 0 ? inboundQueueWaitTotalMs / processCount : 0D;
        }

        public double getOutboundQueueWaitAvgMs() {
            return outboundCount > 0 ? outboundQueueWaitTotalMs / outboundCount : 0D;
        }

        public double getProcessAvgMs() {
            return processCount > 0 ? processTotalMs / processCount : 0D;
        }
    }

    public static class QueueStats {
        public double commandQueueAvg;
        public int commandQueueMax;
        public double inboundQueueAvg;
        public int inboundQueueMax;
        public double outboundHighAvg;
        public int outboundHighMax;
        public double outboundLowAvg;
        public int outboundLowMax;
        public double outboundBytesAvg;
        public int outboundBytesMax;
        public int pendingLoginsMax;
        public int activeHandlersMax;
        public int chunkCompressionMax;
        public int chunkCompressionCapacity;
        public long netChunkZstdPackets;
        public long netChunkZlibPackets;
        public long netChunkZstdFallbacks;
        public long netChunkCompressionFailures;
        public double netChunkZstdAvgMicros;
        public double netChunkZlibAvgMicros;
        public long regionWriteZstd;
        public long regionWriteZlib;
        public long regionWriteZstdFallbacks;
        public double regionWriteZstdAvgMicros;
        public double regionWriteZlibAvgMicros;
        public long movementCoalescedPackets;
        public long movementDroppedPackets;
        public long entityTrackingSkippedNear;
        public long entityTrackingSkippedMid;
        public long entityTrackingSkippedFar;
    }

    public static class SchedulerStats {
        public long samples;
        public long movedTotal;
        public long executedTotal;
        public long leftoverTotal;
        public int leftoverMax;
        public double heartbeatTotalMs;
        public double heartbeatMaxMs;
        public double movedAvg;
        public double executedAvg;
        public double leftoverAvg;
        public double heartbeatAvgMs;
    }

    public static class ChunkIoStats {
        public static class OperationStats {
            public String operation;
            public String worldName;
            public long count;
            public double totalMs;
            public double maxMs;
            public double avgMs;
        }

        public long totalCount;
        public double totalMs;
        public double maxMs;
        public double avgMs;
        public final List<OperationStats> operations = new ArrayList<OperationStats>();
    }

    public static class OffenderStats {
        public String playerName;
        public int outboundQueueMax;
        public int inboundQueueMax;
        public int chunkCompressionQueueMax;
        public double queueWaitAvgMs;
        public double processAvgMs;
        public double pingAvgMs;
        public double score;
    }

    public static class BottleneckFinding {
        public String severity;
        public String title;
        public String details;
        public String recommendation;
        public double score;

        public BottleneckFinding(String severity, String title, String details, String recommendation, double score) {
            this.severity = severity;
            this.title = title;
            this.details = details;
            this.recommendation = recommendation;
            this.score = score;
        }
    }

    public static class ReportBundle {
        public final String textPath;
        public final String jsonPath;

        public ReportBundle(String textPath, String jsonPath) {
            this.textPath = textPath;
            this.jsonPath = jsonPath;
        }
    }

    // ----------------------------
    // Internal helper classes
    // ----------------------------

    public static class TickPercentiles {
        public double p50;
        public double p95;
        public double p99;
    }

    private static class CallFrame {
        final String path;
        final long startTime;

        CallFrame(String path, long startTime) {
            this.path = path;
            this.startTime = startTime;
        }
    }

    public static class ProfileData {
        public final String path;
        public long callCount = 0;
        public double totalTime = 0D;
        public double maxTime = 0D;
        public double minTime = Double.MAX_VALUE;

        ProfileData(String path) {
            this.path = path;
        }

        ProfileData(ProfileData copy) {
            this.path = copy.path;
            this.callCount = copy.callCount;
            this.totalTime = copy.totalTime;
            this.maxTime = copy.maxTime;
            this.minTime = copy.minTime;
        }

        synchronized void recordCall(double durationMs) {
            callCount++;
            totalTime += durationMs;
            if (durationMs > maxTime) {
                maxTime = durationMs;
            }
            if (durationMs < minTime) {
                minTime = durationMs;
            }
        }

        double getAverageTime() {
            return callCount > 0 ? totalTime / callCount : 0D;
        }
    }

    public static class SlowCall {
        public enum Severity { SLOW, VERY_SLOW, CRITICAL }

        public final String path;
        public final double durationMs;
        public final Severity severity;
        public final long timestamp;

        SlowCall(String path, double durationMs, Severity severity, long timestamp) {
            this.path = path;
            this.durationMs = durationMs;
            this.severity = severity;
            this.timestamp = timestamp;
        }
    }

    private static class MemorySample {
        final long timestamp;
        final long usedMemory;
        final long totalMemory;
        final long maxMemory;

        MemorySample(long timestamp, long usedMemory, long totalMemory, long maxMemory) {
            this.timestamp = timestamp;
            this.usedMemory = usedMemory;
            this.totalMemory = totalMemory;
            this.maxMemory = maxMemory;
        }
    }

    public static class MemoryAnalysis {
        public final boolean potentialLeak;
        public final double growthRateBytesPerSec;
        public final long peakMemory;
        public final long avgMemory;
        public final String message;
        public final int sampleCount;

        MemoryAnalysis(boolean potentialLeak, double growthRateBytesPerSec, long peakMemory, long avgMemory, String message, int sampleCount) {
            this.potentialLeak = potentialLeak;
            this.growthRateBytesPerSec = growthRateBytesPerSec;
            this.peakMemory = peakMemory;
            this.avgMemory = avgMemory;
            this.message = message;
            this.sampleCount = sampleCount;
        }
    }

    public static class CommandLatencyStats {
        public String commandRoot;
        public long count;
        public double queueWaitAvgMs;
        public double execAvgMs;
        public double queueWaitMaxMs;
        public double execMaxMs;
    }

    private static class CommandStats {
        final String commandRoot;
        long count;
        double queueWaitTotalMs;
        double execTotalMs;
        double queueWaitMaxMs;
        double execMaxMs;

        CommandStats(String commandRoot) {
            this.commandRoot = commandRoot;
        }
    }

    private static class ChunkOperationStats {
        final String operation;
        final String worldName;
        long count;
        double totalMs;
        double maxMs;

        ChunkOperationStats(String operation, String worldName) {
            this.operation = operation;
            this.worldName = worldName;
        }
    }

    private static class OffenderAccumulator {
        final String playerName;
        long samples;
        long packetSamples;

        long outboundQueueTotal;
        int outboundQueueMax;
        long inboundQueueTotal;
        int inboundQueueMax;
        long chunkCompressionQueueTotal;
        int chunkCompressionQueueMax;

        long pingTotal;
        int pingMax;

        double totalIngressQueueWaitMs;
        double totalEgressQueueWaitMs;
        double totalProcessMs;
        double maxQueueWaitMs;
        double maxProcessMs;

        OffenderAccumulator(String playerName) {
            this.playerName = playerName;
        }
    }
}

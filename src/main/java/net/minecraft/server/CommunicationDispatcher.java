package net.minecraft.server;

import org.bukkit.craftbukkit.TextWrapper;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Dedicated communication lane for raw non-command chat fanout.
 * Keeps chat processing off the main game-tick packet path.
 */
public class CommunicationDispatcher {
    private static final Logger log = Logger.getLogger("Minecraft");
    private static final long OVERFLOW_LOG_INTERVAL_MS = 2000L;
    private static final int CHAT_QUEUE_CAPACITY = 4096;

    private final MinecraftServer server;
    private final Object chatQueueLock = new Object();
    private final ArrayDeque<QueuedChatMessage> chatQueue = new ArrayDeque<QueuedChatMessage>();
    private final Object workerLock = new Object();

    private volatile boolean running = false;
    private volatile Thread workerThread;
    private volatile long lastOverflowLogAt = 0L;

    private final AtomicLong parallelChatQueuedTotal = new AtomicLong();
    private final AtomicLong parallelChatSentTotal = new AtomicLong();
    private final AtomicLong parallelChatDroppedOverflowTotal = new AtomicLong();
    private final AtomicLong parallelChatDroppedRateTotal = new AtomicLong();
    private final AtomicLong parallelChatQueueWaitNanosTotal = new AtomicLong();
    private final AtomicLong parallelChatQueueWaitSamplesTotal = new AtomicLong();
    private final AtomicLong parallelChatQueueWaitMaxNanosSincePoll = new AtomicLong();

    public CommunicationDispatcher(MinecraftServer server) {
        this.server = server;
    }

    public void start() {
        synchronized (workerLock) {
            if (running) {
                return;
            }
            this.running = true;
            Thread worker = new Thread(new Runnable() {
                public void run() {
                    runWorkerLoop();
                }
            }, "Communication-ChatDispatcher");
            worker.setDaemon(true);
            this.workerThread = worker;
            worker.start();
        }
    }

    public void stop() {
        Thread joinThread = null;
        synchronized (workerLock) {
            if (!running) {
                return;
            }
            running = false;
            joinThread = workerThread;
            workerThread = null;
        }

        synchronized (chatQueueLock) {
            chatQueueLock.notifyAll();
            chatQueue.clear();
        }

        if (joinThread != null) {
            joinThread.interrupt();
            if (Thread.currentThread() != joinThread) {
                try {
                    joinThread.join(200L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public boolean enqueueRawChat(EntityPlayer sender, String message) {
        if (!running || sender == null || sender.name == null || message == null) {
            return false;
        }

        long nowMs = System.currentTimeMillis();
        long enqueueNanos = System.nanoTime();

        synchronized (chatQueueLock) {
            if (!running) {
                return false;
            }
            while (chatQueue.size() >= CHAT_QUEUE_CAPACITY) {
                chatQueue.pollFirst();
                parallelChatDroppedOverflowTotal.incrementAndGet();
                maybeLogOverflow(nowMs, sender.name, CHAT_QUEUE_CAPACITY);
            }
            chatQueue.offerLast(new QueuedChatMessage(sender.name, message, enqueueNanos));
            parallelChatQueuedTotal.incrementAndGet();
            chatQueueLock.notifyAll();
        }

        return true;
    }

    public void recordParallelChatRateDrop() {
        parallelChatDroppedRateTotal.incrementAndGet();
    }

    public int getCurrentChatQueueDepth() {
        synchronized (chatQueueLock) {
            return chatQueue.size();
        }
    }

    public int getCurrentChatQueueCapacity() {
        return CHAT_QUEUE_CAPACITY;
    }

    public long getParallelChatQueuedTotal() {
        return parallelChatQueuedTotal.get();
    }

    public long getParallelChatSentTotal() {
        return parallelChatSentTotal.get();
    }

    public long getParallelChatDroppedOverflowTotal() {
        return parallelChatDroppedOverflowTotal.get();
    }

    public long getParallelChatDroppedRateTotal() {
        return parallelChatDroppedRateTotal.get();
    }

    public long getParallelChatQueueWaitNanosTotal() {
        return parallelChatQueueWaitNanosTotal.get();
    }

    public long getParallelChatQueueWaitSamplesTotal() {
        return parallelChatQueueWaitSamplesTotal.get();
    }

    public long consumeParallelChatQueueWaitMaxNanos() {
        return parallelChatQueueWaitMaxNanosSincePoll.getAndSet(0L);
    }

    private void runWorkerLoop() {
        while (running) {
            try {
                QueuedChatMessage queued = null;
                synchronized (chatQueueLock) {
                    if (chatQueue.isEmpty()) {
                        chatQueueLock.wait(25L);
                    }
                    if (!chatQueue.isEmpty()) {
                        queued = chatQueue.pollFirst();
                    }
                }

                if (queued == null) {
                    continue;
                }

                processQueuedChat(queued);
            } catch (InterruptedException ignored) {
                break;
            } catch (Throwable t) {
                log.warning("[CommunicationDispatcher] Chat worker error: " + t.getMessage());
            }
        }
    }

    private void processQueuedChat(QueuedChatMessage queued) {
        if (queued == null) {
            return;
        }

        long waitNanos = Math.max(0L, System.nanoTime() - queued.enqueueNanos);
        parallelChatQueueWaitNanosTotal.addAndGet(waitNanos);
        parallelChatQueueWaitSamplesTotal.incrementAndGet();
        updateMax(parallelChatQueueWaitMaxNanosSincePoll, waitNanos);

        String formatted = "<" + queued.senderName + "> " + queued.message;
        String[] modernWrapped = TextWrapper.wrapText(formatted);
        String[] legacyWrapped = TextWrapper.wrapTextLegacy(formatted);

        if (server == null || server.serverConfigurationManager == null) {
            parallelChatSentTotal.incrementAndGet();
            return;
        }

        // Keep server/GUI chat logging behavior consistent with the legacy path.
        log.info(formatted);

        List<EntityPlayer> recipients = server.serverConfigurationManager.getOnlinePlayersSnapshot();
        for (EntityPlayer recipient : recipients) {
            if (recipient == null || recipient.netServerHandler == null || recipient.netServerHandler.networkManager == null) {
                continue;
            }

            boolean modern = recipient.netServerHandler.networkManager.pvn >= 9;
            String[] wrapped = modern ? modernWrapped : legacyWrapped;
            for (int i = 0; i < wrapped.length; i++) {
                recipient.netServerHandler.networkManager.queue(new Packet3Chat(wrapped[i]));
            }
        }

        parallelChatSentTotal.incrementAndGet();
    }

    private void maybeLogOverflow(long now, String senderName, int capacity) {
        if (now - lastOverflowLogAt < OVERFLOW_LOG_INTERVAL_MS) {
            return;
        }
        lastOverflowLogAt = now;
        log.warning("[CommunicationDispatcher] Chat queue overflow, dropping oldest message (sender="
            + senderName + ", capacity=" + capacity + ")");
    }

    private static void updateMax(AtomicLong target, long candidate) {
        long prev;
        do {
            prev = target.get();
            if (candidate <= prev) {
                return;
            }
        } while (!target.compareAndSet(prev, candidate));
    }

    private static final class QueuedChatMessage {
        private final String senderName;
        private final String message;
        private final long enqueueNanos;

        private QueuedChatMessage(String senderName, String message, long enqueueNanos) {
            this.senderName = senderName;
            this.message = message;
            this.enqueueNanos = enqueueNanos;
        }
    }
}

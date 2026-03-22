package net.minecraft.server;

import com.legacyminecraft.poseidon.Poseidon;
import com.legacyminecraft.poseidon.PoseidonServer;
import com.legacyminecraft.poseidon.event.PlayerSendPacketEvent;
import com.projectposeidon.ConnectionType;
import com.legacyminecraft.poseidon.PoseidonConfig;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.logging.Logger;

import me.devcody.uberbukkit.patch.Patches;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandAutocompleteRegistry;
import org.bukkit.craftbukkit.ChunkCompressionThread;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.TextWrapper;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.StorageMinecart;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.packet.*;
import org.bukkit.event.player.*;

import uk.betacraft.uberbukkit.UberbukkitConfig;
import uk.betacraft.uberbukkit.packet.Packet62Sound;
import uk.betacraft.uberbukkit.packet.Packet63Digging;
import uk.betacraft.uberbukkit.protocol.Protocol;
import net.minecraft.server.network.ModProtocol;
import net.minecraft.server.registry.BlockMiningRegistryApi;
import net.minecraft.server.registry.RegistrySyncSnapshot;
import net.minecraft.server.registry.PlayerCapabilityRegistryApi;

import net.minecraft.server.event.EventBus;
import net.minecraft.server.event.events.AttackEntityEvent;
import net.minecraft.server.event.events.InteractEntityEvent;
import net.minecraft.server.event.events.InventoryShortcutEvent;

public class NetServerHandler extends NetHandler implements ICommandListener {

    private static boolean isStaffExemptFromFlyKick = UberbukkitConfig.getInstance().getBoolean("settings.exempt-staff-from-flight-kick", false);
    public static Logger a = Logger.getLogger("Minecraft");
    public NetworkManager networkManager;
    public boolean disconnected = false;
    private MinecraftServer minecraftServer;
    public EntityPlayer player; // CraftBukkit - private -> public
    private int f;
    private int g;
    private int h;
    private boolean i;
    private double x;
    private double y;
    private double z;
    private boolean checkMovement = true;
    private Map n = new HashMap();
    private boolean usingReleaseToBeta = false; //Project Poseidon - Create Variable
    private ConnectionType connectionType = ConnectionType.NORMAL; //Project Poseidon - Create Variable
    private int rawConnectionType = 0; //Project Poseidon - Create Variable
    private boolean receivedKeepAlive = false;
    private boolean firePacketEvents;
    // Vanilla ordering: no pre-login buffering required
    private boolean loginSent = true;
    private java.util.List preLoginWorldPackets = null;
    // Keep an abuse guard for TCP voice failover while allowing short WAN jitter bursts.
    private static final boolean PARALLEL_CHAT_ENABLED = true;
    private static final long PARALLEL_CHAT_RATE_WINDOW_MS = 2000L;
    private static final int PARALLEL_CHAT_MAX_MESSAGES_PER_WINDOW = 8;
    private static final boolean PARALLEL_VOICE_TCP_ENABLED = true;
    private static final boolean PARALLEL_FAIL_OPEN_TO_LEGACY = true;
    private static final long[] VOICE_TCP_QUEUE_WAIT_BUCKET_MS = new long[] {1L, 2L, 5L, 10L, 20L, 40L, 80L, 120L, 200L, 300L, 500L, 1000L};
    private static final AtomicLong VOICE_TCP_ATTEMPTS_WINDOW = new AtomicLong();
    private static final AtomicLong VOICE_TCP_QUEUE_OVERFLOW_WINDOW = new AtomicLong();
    private static final AtomicLongArray VOICE_TCP_QUEUE_WAIT_BUCKETS_WINDOW = new AtomicLongArray(VOICE_TCP_QUEUE_WAIT_BUCKET_MS.length + 1);
    private static final ConcurrentHashMap<String, AtomicLong> VOICE_TCP_DROP_REASONS_WINDOW = new ConcurrentHashMap<String, AtomicLong>();
    private long voiceTcpLimiterLastRefillAt = 0L;
    private double voiceTcpPacketTokens = 0.0D;
    private double voiceTcpByteTokens = 0.0D;
    private long parallelChatWindowStartAt = 0L;
    private int parallelChatMessagesInWindow = 0;
    private static final long VOICE_TCP_BACKLOG_LOG_INTERVAL_MS = 30000L;
    private static final int VOICE_TCP_INBOUND_QUEUE_MAX_PACKETS = 240;
    private static final int VOICE_TCP_BACKLOG_WARN_QUEUE_DEPTH = 192;
    private static final AtomicLong PARALLEL_VOICE_CONSUMED_TOTAL = new AtomicLong();
    private static final AtomicLong PARALLEL_VOICE_DROPPED_RATE_TOTAL = new AtomicLong();
    private static final AtomicLong PARALLEL_VOICE_DROPPED_INVALID_TOTAL = new AtomicLong();
    private static final AtomicLong PARALLEL_VOICE_DROPPED_OVERFLOW_TOTAL = new AtomicLong();
    private static final AtomicLong PARALLEL_VOICE_QUEUE_WAIT_NANOS_TOTAL = new AtomicLong();
    private static final AtomicLong PARALLEL_VOICE_QUEUE_WAIT_SAMPLES_TOTAL = new AtomicLong();
    private static final AtomicLong PARALLEL_VOICE_QUEUE_WAIT_MAX_NANOS_SINCE_POLL = new AtomicLong();
    private long lastVoiceTcpBacklogLogAt = 0L;
    private long voiceTcpBacklogDropCount = 0L;
    private int voiceTcpBacklogPeakDepth = 0;
    private final Object voiceTcpInboundWorkerLock = new Object();
    private Thread voiceTcpInboundWorkerThread;
    private volatile boolean voiceTcpInboundWorkerRunning = false;
    private final Object voiceTcpInboundQueueLock = new Object();
    private final ArrayDeque<QueuedTcpVoicePacket> voiceTcpInboundQueue = new ArrayDeque<QueuedTcpVoicePacket>();
    
    // MCOSE version checking
    private boolean receivedVersionPacket = false;
    private String clientVersion = null;
    private long connectionStartTime = System.currentTimeMillis();
    private static final long VERSION_CHECK_GRACE_PERIOD_MS = 10000; // 10 seconds to send version
    private boolean modProtocolNegotiated = false;
    private int remoteModProtocolVersion = 0;
    private int negotiatedModFeatures = 0;
    private RegistrySyncSnapshot syncedRegistrySnapshot = null;
    
    private final String msgPlayerLeave;
    private static final long INVENTORY_SHORTCUT_PRIME_MS = 350L;
    private int primedInventorySlot = -1;
    private int primedInventoryWindowId = -1;
    private long primedInventoryClickAt = 0L;

    public boolean isReceivedKeepAlive() {
        return receivedKeepAlive;
    }

    public void setReceivedKeepAlive(boolean receivedKeepAlive) {
        this.receivedKeepAlive = receivedKeepAlive;
    }

    public boolean supportsChunkZstd() {
        return (this.negotiatedModFeatures & ModProtocol.FEATURE_CHUNK_ZSTD) != 0;
    }

    public boolean supportsItemStackV2() {
        return (this.negotiatedModFeatures & ModProtocol.FEATURE_ITEM_STACK_V2) != 0;
    }

    public boolean supportsItemComponents() {
        return (this.negotiatedModFeatures & ModProtocol.FEATURE_ITEM_COMPONENTS) != 0;
    }

    public boolean supportsRegionCoreItems() {
        return (this.negotiatedModFeatures & ModProtocol.FEATURE_REGIONCORE_ITEMS) != 0;
    }

    public boolean supportsEntityWireV2() {
        return (this.negotiatedModFeatures & ModProtocol.FEATURE_ENTITY_WIRE_V2) != 0;
    }

    public boolean supportsEntityDataV2() {
        return (this.negotiatedModFeatures & ModProtocol.FEATURE_ENTITY_DATA_V2) != 0;
    }

    public boolean supportsSkinPartSync() {
        return this.modProtocolNegotiated
            && (this.negotiatedModFeatures & ModProtocol.FEATURE_SKIN_PARTS_SYNC) != 0;
    }

    public static long getParallelVoiceConsumedTotal() {
        return PARALLEL_VOICE_CONSUMED_TOTAL.get();
    }

    public static long getParallelVoiceDroppedRateTotal() {
        return PARALLEL_VOICE_DROPPED_RATE_TOTAL.get();
    }

    public static long getParallelVoiceDroppedInvalidTotal() {
        return PARALLEL_VOICE_DROPPED_INVALID_TOTAL.get();
    }

    public static long getParallelVoiceDroppedOverflowTotal() {
        return PARALLEL_VOICE_DROPPED_OVERFLOW_TOTAL.get();
    }

    public static long getParallelVoiceQueueWaitNanosTotal() {
        return PARALLEL_VOICE_QUEUE_WAIT_NANOS_TOTAL.get();
    }

    public static long getParallelVoiceQueueWaitSamplesTotal() {
        return PARALLEL_VOICE_QUEUE_WAIT_SAMPLES_TOTAL.get();
    }

    public static long consumeParallelVoiceQueueWaitMaxNanos() {
        return PARALLEL_VOICE_QUEUE_WAIT_MAX_NANOS_SINCE_POLL.getAndSet(0L);
    }

    public static VoiceTcpWindowStats consumeVoiceTcpWindowStats() {
        return new VoiceTcpWindowStats(
            VOICE_TCP_ATTEMPTS_WINDOW.getAndSet(0L),
            VOICE_TCP_QUEUE_OVERFLOW_WINDOW.getAndSet(0L),
            consumeVoiceTcpQueueWaitP95Ms(),
            consumeVoiceTcpDropReasonsWindow()
        );
    }

    private static Map<String, Long> consumeVoiceTcpDropReasonsWindow() {
        Map<String, Long> snapshot = new HashMap<String, Long>();
        for (Map.Entry<String, AtomicLong> entry : VOICE_TCP_DROP_REASONS_WINDOW.entrySet()) {
            AtomicLong counter = entry.getValue();
            if (counter == null) {
                continue;
            }
            long count = counter.getAndSet(0L);
            if (count > 0L) {
                snapshot.put(entry.getKey(), Long.valueOf(count));
            }
        }
        return snapshot;
    }

    private static double consumeVoiceTcpQueueWaitP95Ms() {
        long totalSamples = 0L;
        long[] bucketCounts = new long[VOICE_TCP_QUEUE_WAIT_BUCKET_MS.length + 1];
        for (int i = 0; i < bucketCounts.length; ++i) {
            long count = VOICE_TCP_QUEUE_WAIT_BUCKETS_WINDOW.getAndSet(i, 0L);
            bucketCounts[i] = count;
            totalSamples += count;
        }
        if (totalSamples <= 0L) {
            return 0.0D;
        }
        long threshold = (long) Math.ceil((double) totalSamples * 0.95D);
        long cumulative = 0L;
        for (int i = 0; i < bucketCounts.length; ++i) {
            cumulative += bucketCounts[i];
            if (cumulative >= threshold) {
                if (i >= VOICE_TCP_QUEUE_WAIT_BUCKET_MS.length) {
                    return (double) VOICE_TCP_QUEUE_WAIT_BUCKET_MS[VOICE_TCP_QUEUE_WAIT_BUCKET_MS.length - 1];
                }
                return (double) VOICE_TCP_QUEUE_WAIT_BUCKET_MS[i];
            }
        }
        return (double) VOICE_TCP_QUEUE_WAIT_BUCKET_MS[VOICE_TCP_QUEUE_WAIT_BUCKET_MS.length - 1];
    }

    private static void recordVoiceTcpDropReason(String reason) {
        String normalizedReason = normalizeVoiceDropReason(reason);
        AtomicLong counter = VOICE_TCP_DROP_REASONS_WINDOW.get(normalizedReason);
        if (counter == null) {
            AtomicLong created = new AtomicLong();
            AtomicLong existing = VOICE_TCP_DROP_REASONS_WINDOW.putIfAbsent(normalizedReason, created);
            counter = existing != null ? existing : created;
        }
        counter.incrementAndGet();
    }

    private static String normalizeVoiceDropReason(String reason) {
        if (reason == null) {
            return "unknown";
        }
        String trimmed = reason.trim();
        if (trimmed.length() == 0) {
            return "unknown";
        }
        int end = trimmed.length();
        int colon = trimmed.indexOf(':');
        if (colon >= 0 && colon < end) {
            end = colon;
        }
        int space = trimmed.indexOf(' ');
        if (space >= 0 && space < end) {
            end = space;
        }
        return trimmed.substring(0, end);
    }

    private static void recordVoiceTcpQueueWaitSample(long queueWaitNanos) {
        long queueWaitMs = queueWaitNanos <= 0L ? 0L : queueWaitNanos / 1_000_000L;
        int bucket = VOICE_TCP_QUEUE_WAIT_BUCKET_MS.length;
        for (int i = 0; i < VOICE_TCP_QUEUE_WAIT_BUCKET_MS.length; ++i) {
            if (queueWaitMs <= VOICE_TCP_QUEUE_WAIT_BUCKET_MS[i]) {
                bucket = i;
                break;
            }
        }
        VOICE_TCP_QUEUE_WAIT_BUCKETS_WINDOW.incrementAndGet(bucket);
    }

    public static final class VoiceTcpWindowStats {
        public final long attempts;
        public final long queueOverflowDrops;
        public final double queueWaitP95Ms;
        public final Map<String, Long> dropReasons;

        private VoiceTcpWindowStats(long attempts, long queueOverflowDrops, double queueWaitP95Ms, Map<String, Long> dropReasons) {
            this.attempts = attempts;
            this.queueOverflowDrops = queueOverflowDrops;
            this.queueWaitP95Ms = queueWaitP95Ms;
            this.dropReasons = dropReasons == null ? Collections.<String, Long>emptyMap() : dropReasons;
        }

        public long getTotalDrops() {
            long total = 0L;
            for (Long value : this.dropReasons.values()) {
                if (value != null) {
                    total += value.longValue();
                }
            }
            return total;
        }
    }

    // markLoginPacketSent no-op (kept for compatibility)
    public void markLoginPacketSent() { this.loginSent = true; }

    public NetServerHandler(MinecraftServer minecraftserver, NetworkManager networkmanager, EntityPlayer entityplayer) {
        this.minecraftServer = minecraftserver;
        this.networkManager = networkmanager;
        networkmanager.a((NetHandler) this);
        this.player = entityplayer;
        entityplayer.netServerHandler = this;

        // CraftBukkit start
        this.server = minecraftserver.server;
        this.firePacketEvents = PoseidonConfig.getInstance().getBoolean("settings.packet-events.enabled", false); //Poseidon
        this.msgPlayerLeave = PoseidonConfig.getInstance().getConfigString("message.player.leave");
    }

    //Project Poseidon - Start
    public boolean isUsingReleaseToBeta() {
        return usingReleaseToBeta;
    }

    public void setUsingReleaseToBeta(boolean usingReleaseToBeta) {
        this.usingReleaseToBeta = usingReleaseToBeta;
    }

    public ConnectionType getConnectionType() {
        return this.connectionType;
    }

    public void setConnectionType(ConnectionType connectionType) {
        this.connectionType = connectionType;
    }

    public void setRawConnectionType(int rawConnectionType) {
        this.rawConnectionType = rawConnectionType;
    }

    public int getRawConnectionType() {
        return this.rawConnectionType;
    }

    //Project Poseidon - End

    private final CraftServer server;
    private int lastTick = MinecraftServer.currentTick;
    private int lastDropTick = MinecraftServer.currentTick;
    private int dropCount = 0;
    private static final int PLACE_DISTANCE_SQUARED = 6 * 6;
    private static final long RIGHT_CLICK_DUPLICATE_SUPPRESS_MS = 35L;

    // Get position of last block hit for BlockDamageLevel.STOPPED
    private double lastPosX = Double.MAX_VALUE;
    private double lastPosY = Double.MAX_VALUE;
    private double lastPosZ = Double.MAX_VALUE;
    private float lastPitch = Float.MAX_VALUE;
    private float lastYaw = Float.MAX_VALUE;
    private boolean justTeleported = false;

    // For the packet15 hack :(
    Long lastPacket;

    // Store the last block right clicked and what type it was
    private int lastMaterial;

    public CraftPlayer getPlayer() {
        return (this.player == null) ? null : (CraftPlayer) this.player.getBukkitEntity();
    }
    // CraftBukkit end

    // uberbukkit
    public Integer lastDigX = null;
    public Integer lastDigY = null;
    public Integer lastDigZ = null;
    public Integer lastDigFace = null;


    public void a() {
        this.i = false;
        this.networkManager.b();

        if (this.f - this.g > 20) {
            this.sendPacket(new Packet0KeepAlive());
        }

        // Periodically push updated ping to all clients for Tab overlay
        try {
            if ((MinecraftServer.currentTick - this.lastTick) >= 20) { // about once per second
                this.lastTick = MinecraftServer.currentTick;
                int currentPing = this.b();
                if (this.player != null) {
                    Packet201PlayerInfo update = new Packet201PlayerInfo(this.player.name, true, currentPing);
                    this.minecraftServer.serverConfigurationManager.sendAll(update);
                }
            }
        } catch (Throwable ignore) {}

        // uberbukkit - play breaking sound & animation for others
        if (this.mineExpire >= System.currentTimeMillis()) {
            if (this.lastMine + 200L < System.currentTimeMillis()) {
                return;
            }

            // Allow sending to all clients that support extension packets; Protocol layer will filter per-recipient

            delaySound++;
            if (delaySound % 4 != 0) return;

            // prevent overflow (lol)
            delaySound = 0;

            int id = this.player.world.getTypeId(lastDigX, lastDigY, lastDigZ);
            if (id == 0) return;

            Block block = Block.byId[id];

            float vol1 = (block.stepSound.getVolume1() + 1.0F) / 8.0F;
            this.minecraftServer.serverConfigurationManager.sendPacketNearbyToScale(this.player, (double) lastDigX + 0.5D, (double) lastDigY + 0.5D, (double) lastDigZ + 0.5D, vol1, ((WorldServer) this.player.world).dimension, new Packet62Sound(block.stepSound.getName(), (double) lastDigX + 0.5D, (double) lastDigY + 0.5D, (double) lastDigZ + 0.5D, vol1, block.stepSound.getVolume2() * 0.5F));

            if (lastDigFace != null) {
                // Compute progress from elapsed dig ticks and state-aware mining speed.
                long start = this.player.itemInWorldManager.getLastDigStart();
                long now = System.currentTimeMillis();
                double elapsedTicks = Math.max(0D, (now - start) / 50.0D);
                float perTick = BlockMiningRegistryApi.getBreakProgressPerTick(this.player, this.player.world, lastDigX, lastDigY, lastDigZ);
                float progress = (float) (elapsedTicks * perTick);
                if (progress > 1.0F) progress = 1.0F;
                if (progress < 0.0F) progress = 0.0F;

                this.minecraftServer.serverConfigurationManager.sendPacketNearby(player, lastDigX, lastDigY, lastDigZ, 64D, player.dimension, new Packet63Digging(lastDigX, lastDigY, lastDigZ, lastDigFace, (float) elapsedTicks));

                if (progress >= 1.0F) {
                    // Clear overlay immediately at completion
                    this.minecraftServer.serverConfigurationManager.sendPacketNearby(player, lastDigX, lastDigY, lastDigZ, 64D, player.dimension, new Packet63Digging(lastDigX, lastDigY, lastDigZ, lastDigFace, -1.0F));
                }
            }
        }
    }

    public void disconnect(String s) {
        if (disconnected) return; // Poseidon: Kick/Disconnect spam fix
        stopVoiceTcpInboundWorker(true);

        // CraftBukkit start
        String leaveMessage = this.msgPlayerLeave.replace("%player%", this.player.name);

        PlayerKickEvent event = new PlayerKickEvent(this.server.getPlayer(this.player), s, leaveMessage);
        this.server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            // Do not kick the player
            return;
        }
        // Send the possibly modified leave message
        s = event.getReason();
        // CraftBukkit end

        this.player.B();
        this.sendPacket(new Packet255KickDisconnect(s));
        this.networkManager.d();

        // CraftBukkit start
        leaveMessage = event.getLeaveMessage();
        if (leaveMessage != null) {
            this.minecraftServer.serverConfigurationManager.sendAll(new Packet3Chat(leaveMessage));
        }
        // CraftBukkit end

        this.minecraftServer.serverConfigurationManager.disconnect(this.player);
        this.disconnected = true;
    }

    // uberbukkit
    public void a(Packet5EntityEquipment packet5) {
        if (this.networkManager.pvn > 6) return;

        this.player.packet5.process(packet5);
    }

    public void a(Packet27 packet27) {
        // poseidon
        PacketReceivedEvent event = new PacketReceivedEvent(server.getPlayer(player), packet27);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        this.player.a(packet27.c(), packet27.e(), packet27.g(), packet27.h(), packet27.d(), packet27.f());
    }

    public void a(Packet10Flying packet10flying) {
        // poseidon
        PacketReceivedEvent pevent = new PacketReceivedEvent(server.getPlayer(player), packet10flying);
        server.getPluginManager().callEvent(pevent);
        if (pevent.isCancelled()) return;

        WorldServer worldserver = this.minecraftServer.getWorldServer(this.player.dimension);

        this.i = true;
        double d0;

        if (!this.checkMovement) {
            d0 = packet10flying.y - this.y;
            if (packet10flying.x == this.x && d0 * d0 < 0.01D && packet10flying.z == this.z) {
                this.checkMovement = true;
            }
        }

        // CraftBukkit start
        Player player = this.getPlayer();
        Location from = new Location(player.getWorld(), lastPosX, lastPosY, lastPosZ, lastYaw, lastPitch); // Get the Players previous Event location.
        Location to = player.getLocation().clone(); // Start off the To location as the Players current location.

        // If the packet contains movement information then we update the To location with the correct XYZ.
        if (packet10flying.h && !(packet10flying.h && packet10flying.y == -999.0D && packet10flying.stance == -999.0D)) {
            to.setX(packet10flying.x);
            to.setY(packet10flying.y);
            to.setZ(packet10flying.z);
        }

        // If the packet contains look information then we update the To location with the correct Yaw & Pitch.
        if (packet10flying.hasLook && Patches.HEAD_ROTATION.check(this.player, packet10flying.yaw, packet10flying.pitch)) {
            to.setYaw(packet10flying.yaw);
            to.setPitch(packet10flying.pitch);
        }

        // Freeze enforcement: short-circuit movement packets
        if (uk.betacraft.uberbukkit.AdminRegistry.isFrozen(player.getName())) {
            this.player.netServerHandler.sendPacket(new Packet13PlayerLookMove(from.getX(), from.getY() + 1.6200000047683716D, from.getY(), from.getZ(), from.getYaw(), from.getPitch(), false));
            return;
        }

        // Prevent 40 event-calls for less than a single pixel of movement >.>
        double delta = Math.pow(this.lastPosX - to.getX(), 2) + Math.pow(this.lastPosY - to.getY(), 2) + Math.pow(this.lastPosZ - to.getZ(), 2);
        float deltaAngle = Math.abs(this.lastYaw - to.getYaw()) + Math.abs(this.lastPitch - to.getPitch());

        if ((delta > 1f / 256 || deltaAngle > 10f) && (this.checkMovement && !this.player.dead)) {
            this.player.isInWorkbench = false; // uberbukkit - cancel workbench status when player moves
            this.lastPosX = to.getX();
            this.lastPosY = to.getY();
            this.lastPosZ = to.getZ();
            this.lastYaw = to.getYaw();
            this.lastPitch = to.getPitch();

            // Skip the first time we do this
            if (from.getX() != Double.MAX_VALUE) {
                PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
                this.server.getPluginManager().callEvent(event);

                // If the event is cancelled we move the player back to their old location.
                if (event.isCancelled()) {
                    this.player.netServerHandler.sendPacket(new Packet13PlayerLookMove(from.getX(), from.getY() + 1.6200000047683716D, from.getY(), from.getZ(), from.getYaw(), from.getPitch(), false));
                    return;
                }

                /* If a Plugin has changed the To destination then we teleport the Player
                   there to avoid any 'Moved wrongly' or 'Moved too quickly' errors.
                   We only do this if the Event was not cancelled. */
                if (!to.equals(event.getTo())) {
                    this.player.getBukkitEntity().teleport(event.getTo());
                    return;
                }

                /* Check to see if the Players Location has somehow changed during the call of the event.
                   This can happen due to a plugin teleporting the player instead of using .setTo() */
                if (!from.equals(this.getPlayer().getLocation()) && this.justTeleported) {
                    this.justTeleported = false;
                    return;
                }
            }
        }

        if (Double.isNaN(packet10flying.x) || Double.isNaN(packet10flying.y) || Double.isNaN(packet10flying.z) || Double.isNaN(packet10flying.stance) && player.isOnline() && !disconnected) {
            player.teleport(player.getWorld().getSpawnLocation());
            System.err.println(player.getName() + " was caught trying to crash the server with an invalid position.");
            player.kickPlayer("Nope!");
            return;
        }

        if (this.checkMovement && !this.player.dead) {
            // CraftBukkit end
            double d1;
            double d2;
            double d3;
            double d4;

            if (this.player.vehicle != null) {
                float f = this.player.yaw;
                float f1 = this.player.pitch;

                this.player.vehicle.f();
                d1 = this.player.locX;
                d2 = this.player.locY;
                d3 = this.player.locZ;
                double d5 = 0.0D;

                d4 = 0.0D;
                if (packet10flying.hasLook) {
                    f = packet10flying.yaw;
                    f1 = packet10flying.pitch;
                }

                if (packet10flying.h && packet10flying.y == -999.0D && packet10flying.stance == -999.0D) {
                    d5 = packet10flying.x;
                    d4 = packet10flying.z;

                    // Project Poseidon - Start
                    // Boat crash fix ported from UberBukkit

                    double d8 = d5 * d5 + d4 * d4;
                    if (d8 > 100.0D) {
                        a.warning("[Poseidon]" + this.player.name + " tried crashing server on entity " + this.player.vehicle.toString() + ". They have been kicked.");
                        player.kickPlayer("Boat crash attempt detected!");
                        return;
                    }

                    // Project Poseidon - End

                }
                PlayerCapabilityRegistryApi.handleGroundStateUpdate(this.player, packet10flying.g);
                this.player.onGround = packet10flying.g;
                this.player.a(true);
                this.player.move(d5, 0.0D, d4);
                this.player.setLocation(d1, d2, d3, f, f1);
                this.player.motX = d5;
                this.player.motZ = d4;
                if (this.player.vehicle != null) {
                    worldserver.vehicleEnteredWorld(this.player.vehicle, true);
                }

                if (this.player.vehicle != null) {
                    this.player.vehicle.f();
                    this.player.vehicle.airBorne = true;
                }

                this.minecraftServer.serverConfigurationManager.d(this.player);
                this.x = this.player.locX;
                this.y = this.player.locY;
                this.z = this.player.locZ;
                worldserver.playerJoinedWorld(this.player);
                return;
            }

            if (this.player.isSleeping()) {
                this.player.a(true);
                this.player.setLocation(this.x, this.y, this.z, this.player.yaw, this.player.pitch);
                worldserver.playerJoinedWorld(this.player);
                return;
            }

            d0 = this.player.locY;
            this.x = this.player.locX;
            this.y = this.player.locY;
            this.z = this.player.locZ;
            d1 = this.player.locX;
            d2 = this.player.locY;
            d3 = this.player.locZ;
            float f2 = this.player.yaw;
            float f3 = this.player.pitch;

            if (packet10flying.h && packet10flying.y == -999.0D && packet10flying.stance == -999.0D) {
                packet10flying.h = false;
            }

            if (packet10flying.h) {
                d1 = packet10flying.x;
                d2 = packet10flying.y;
                d3 = packet10flying.z;
                d4 = packet10flying.stance - packet10flying.y;
                if (!this.player.isSleeping() && (d4 > 1.65D || d4 < 0.1D)) {
                    this.disconnect("Illegal stance");
                    a.warning(this.player.name + " had an illegal stance: " + d4);
                    return;
                }

                if (Math.abs(packet10flying.x) > 3.2E7D || Math.abs(packet10flying.z) > 3.2E7D) {
                    this.disconnect("Illegal position");
                    return;
                }
            }

            if (packet10flying.hasLook) {
                f2 = packet10flying.yaw;
                f3 = packet10flying.pitch;
            }

            this.player.a(true);
            this.player.br = 0.0F;
            this.player.setLocation(this.x, this.y, this.z, f2, f3);
            if (!this.checkMovement) {
                return;
            }

            d4 = d1 - this.player.locX;
            double d6 = d2 - this.player.locY;
            double d7 = d3 - this.player.locZ;

            // Alpha parity: do not inject forward-based climb boost here; handled by collision logic
            double d14 = this.player.motX * this.player.motX + this.player.motY * this.player.motY + this.player.motZ * this.player.motZ;
            double d8 = d4 * d4 + d6 * d6 + d7 * d7;

            if ((boolean) PoseidonConfig.getInstance().getConfigOption("world.settings.speed-hack-check.enabled", true)) {
                if (d8 - d14 > (double) PoseidonConfig.getInstance().getConfigOption("world.settings.speed-hack-check.distance", 100.0D) && this.checkMovement) { // CraftBukkit - Added this.checkMovement condition to solve this check being triggered by teleports
                    a.warning(this.player.name + " moved too quickly! " + d4 + "," + d6 + "," + d7 + " (" + d4 + ", " + d6 + ", " + d7 + ")");
                    if ((boolean) PoseidonConfig.getInstance().getConfigOption("world.settings.speed-hack-check.teleport", true)) {
                        this.a(this.x, this.y, this.z, this.player.yaw, this.player.pitch);
                    } else {
                        this.disconnect("You moved too quickly :( (Hacking?)");
                    }
                    return;
                }
            }

            float f4 = 0.0625F;
            boolean flag = worldserver.getEntities(this.player, this.player.boundingBox.clone().shrink((double) f4, (double) f4, (double) f4)).size() == 0;

            this.player.move(d4, d6, d7);
            d4 = d1 - this.player.locX;
            d6 = d2 - this.player.locY;
            if (d6 > -0.5D && d6 < 0.5D) {
                d6 = 0.0D;
            }

            d7 = d3 - this.player.locZ;
            d8 = d4 * d4 + d6 * d6 + d7 * d7;
            boolean flag1 = false;

            // MCOSE: Be more lenient with position checks for creative/flying players
            // When landing from flight, there can be position discrepancies due to client/server desync
            boolean isCreativeOrCanFly = this.player.gameMode == 1;
            if (!isCreativeOrCanFly && isStaffExemptFromFlyKick) {
                Player bukkitPlayer = (Player) this.player.getBukkitEntity();
                isCreativeOrCanFly = bukkitPlayer.isOp() || bukkitPlayer.hasPermission("uberbukkit.fly");
            }
            
            // Use a higher tolerance for flying players (1.0 vs 0.0625)
            double movementTolerance = isCreativeOrCanFly ? 1.0D : 0.0625D;
            
            if (d8 > movementTolerance && !this.player.isSleeping()) {
                flag1 = true;
                a.warning(this.player.name + " moved wrongly!");
                System.out.println("Got position " + d1 + ", " + d2 + ", " + d3);
                System.out.println("Expected " + this.player.locX + ", " + this.player.locY + ", " + this.player.locZ);
            }

            // Alpha parity: apply small upward impulse when pushing into ladders (supports ladder gaps)
            double d2Adjusted = d2;
            if (this.player.positionChanged && this.player.p() && d2 <= this.player.locY) {
                d2Adjusted = this.player.locY + 0.2D;
            }
            this.player.setLocation(d1, d2Adjusted, d3, f2, f3);
            boolean flag2 = worldserver.getEntities(this.player, this.player.boundingBox.clone().shrink((double) f4, (double) f4, (double) f4)).size() == 0;

            // MCOSE: Skip teleport-back for creative/flying players to allow smooth landings
            if (flag && (flag1 || !flag2) && !this.player.isSleeping() && !isCreativeOrCanFly) {
                this.a(this.x, this.y, this.z, f2, f3);
                return;
            }

            AxisAlignedBB axisalignedbb = this.player.boundingBox.clone().b((double) f4, (double) f4, (double) f4).a(0.0D, -0.55D, 0.0D);

            // uberbukkit
            boolean bool = false;
            if (isStaffExemptFromFlyKick) {
                Player bukkitPlayer = (Player) this.player.getBukkitEntity();
                bool = bukkitPlayer.isOp() || bukkitPlayer.hasPermission("uberbukkit.fly");
            }

            // Treat ladders (including ladder gaps via EntityLiving.p()) as valid support to avoid false fly checks
            boolean supported = worldserver.b(axisalignedbb) || this.player.p();
            if (!this.minecraftServer.allowFlight && !supported && !bool) {
                boolean creativeBypass = this.player != null && this.player instanceof EntityPlayer && ((EntityPlayer) this.player).gameMode == 1;
                // Consider real downward motion as falling, not hovering/flying.
                // This avoids false positives on long descents in non-LAN conditions.
                boolean falling = d6 < -0.03125D || this.player.motY < -0.08D || this.player.fallDistance > 0.0F;
                if (creativeBypass || falling) {
                    this.h = 0;
                } else {
                    ++this.h;
                    if (this.h > 80) {
                        a.warning(this.player.name + " was kicked for floating too long!");
                        this.disconnect("Flying is not enabled on this server");
                        return;
                    }
                }
            } else {
                this.h = 0;
            }
            PlayerCapabilityRegistryApi.handleGroundStateUpdate(this.player, packet10flying.g);
            this.player.onGround = packet10flying.g;
            this.minecraftServer.serverConfigurationManager.d(this.player);
            this.player.b(this.player.locY - d0, packet10flying.g);
        }
    }

    public void a(double d0, double d1, double d2, float f, float f1) {
        // CraftBukkit start - Delegate to teleport(Location)
        Player player = this.getPlayer();
        Location from = player.getLocation();
        Location to = new Location(this.getPlayer().getWorld(), d0, d1, d2, f, f1);
        PlayerTeleportEvent event = new PlayerTeleportEvent(player, from, to);
        this.server.getPluginManager().callEvent(event);

        from = event.getFrom();
        to = event.isCancelled() ? from : event.getTo();

        this.teleport(to);
    }

    public void teleport(Location dest) {
        double d0, d1, d2;
        float f, f1;

        d0 = dest.getX();
        d1 = dest.getY();
        d2 = dest.getZ();
        f = dest.getYaw();
        f1 = dest.getPitch();

        // TODO: make sure this is the best way to address this.
        if (Float.isNaN(f)) {
            f = 0;
        }

        if (Float.isNaN(f1)) {
            f1 = 0;
        }

        this.lastPosX = d0;
        this.lastPosY = d1;
        this.lastPosZ = d2;
        this.lastYaw = f;
        this.lastPitch = f1;
        this.justTeleported = true;
        // CraftBukkit end

        this.checkMovement = false;
        this.x = d0;
        this.y = d1;
        this.z = d2;
        this.player.setLocation(d0, d1, d2, f, f1);
        this.player.netServerHandler.sendPacket(new Packet13PlayerLookMove(d0, d1 + 1.6200000047683716D, d1, d2, f, f1, false));
    }

    // uberbukkit
    public void a(Packet21PickupSpawn packet21) {
        // copy from craftbukkit
        if (this.lastDropTick != MinecraftServer.currentTick) {
            this.dropCount = 0;
            this.lastDropTick = MinecraftServer.currentTick;
        } else {
            // Else we increment the drop count and check the amount.
            this.dropCount++;
            if (this.dropCount >= 20) {
                a.warning(this.player.name + " dropped their items too quickly!");
                this.disconnect("You dropped your items too quickly (Hacking?)");
            }
        }
        // drop itemstack
        ItemStack hand = this.player.inventory.items[this.player.inventory.itemInHandIndex];
        ItemStack todrop = null;

        if (hand != null && hand.id == packet21.h && hand.count >= packet21.i && packet21.i == 1) {
            todrop = hand.cloneItemStack();
            todrop.count = packet21.i;
            hand.count -= packet21.i;
        } else {
            ArrayList<ItemStack> list = this.player.packet5.queue.getQueue();
            for (ItemStack stack : list) {
                if (stack.id == packet21.h && stack.count >= packet21.i) {
                    todrop = stack.cloneItemStack();
                    todrop.count = packet21.i;
                    this.player.packet5.queue.removeStackFromQueue(todrop);
                    break;
                }
            }
        }
        this.player.a(todrop, false);
        //this.player.F();
    }

    public boolean tryHandleParallelChat(Packet3Chat packet3chat) {
        if (!PARALLEL_CHAT_ENABLED) {
            return false;
        }

        try {
            if (packet3chat == null || packet3chat.message == null) {
                return PARALLEL_FAIL_OPEN_TO_LEGACY ? false : true;
            }

            String s = packet3chat.message;
            if (s.length() > Packet3Chat.MAX_CHAT_LENGTH) {
                this.disconnect("Chat message too long");
                return true;
            }

            s = s.trim();
            if (s.length() == 0) {
                return true;
            }

            for (int i = 0; i < s.length(); ++i) {
                if (!FontAllowedCharacters.isAllowedCharacter(s.charAt(i))) {
                    this.disconnect("Illegal characters in chat");
                    return true;
                }
            }

            if (s.startsWith("/")) {
                return false;
            }

            if (this.player == null || this.player.dead || this.disconnected) {
                return true;
            }

            if (uk.betacraft.uberbukkit.AdminRegistry.isMuted(this.player.name)) {
                this.networkManager.queue(new Packet3Chat("\u00A7cYou are muted."));
                return true;
            }

            if (isParallelChatRateLimited(System.currentTimeMillis())) {
                CommunicationDispatcher dispatcher = this.minecraftServer != null ? this.minecraftServer.getCommunicationDispatcher() : null;
                if (dispatcher != null) {
                    dispatcher.recordParallelChatRateDrop();
                }
                this.networkManager.queue(new Packet3Chat("\u00A7cYou are sending messages too quickly."));
                return true;
            }

            CommunicationDispatcher dispatcher = this.minecraftServer != null ? this.minecraftServer.getCommunicationDispatcher() : null;
            if (dispatcher == null || !dispatcher.isRunning()) {
                return PARALLEL_FAIL_OPEN_TO_LEGACY ? false : true;
            }

            if (!dispatcher.enqueueRawChat(this.player, s)) {
                return PARALLEL_FAIL_OPEN_TO_LEGACY ? false : true;
            }

            return true;
        } catch (Throwable t) {
            String playerName = this.player != null && this.player.name != null ? this.player.name : "<unknown>";
            a.warning("[CommunicationDispatcher] Parallel chat failure player=" + playerName + ", reason=" + t.getMessage());
            return PARALLEL_FAIL_OPEN_TO_LEGACY ? false : true;
        }
    }

    public boolean tryHandleParallelVoice(Packet64Voice packet64voice) {
        if (!PARALLEL_VOICE_TCP_ENABLED) {
            return false;
        }
        try {
            return processVoicePacket(packet64voice, true);
        } catch (Throwable t) {
            recordVoiceTcpDropReason("parallel-failure");
            return PARALLEL_FAIL_OPEN_TO_LEGACY ? false : true;
        }
    }

    public void handle64Voice(Packet64Voice packet64voice) {
        processVoicePacket(packet64voice, false);
    }

    private boolean processVoicePacket(Packet64Voice packet64voice, boolean parallelLane) {
        long now = System.currentTimeMillis();
        int payloadLength = packet64voice != null && packet64voice.audioData != null ? packet64voice.audioData.length : -1;

        if (!this.minecraftServer.isVoiceChatEnabled()) {
            logVoiceTcpDrop(now, payloadLength, "voice-chat-disabled");
            if (parallelLane) {
                PARALLEL_VOICE_DROPPED_INVALID_TOTAL.incrementAndGet();
            }
            return true;
        }

        if (packet64voice == null || packet64voice.audioData == null) {
            logVoiceTcpDrop(now, payloadLength, "empty-payload");
            if (parallelLane) {
                PARALLEL_VOICE_DROPPED_INVALID_TOTAL.incrementAndGet();
            }
            return true;
        }

        boolean stopMarker = packet64voice.audioData.length == 0;

        if (packet64voice.audioData.length > Packet64Voice.MAX_PAYLOAD_SIZE) {
            logVoiceTcpDrop(now, packet64voice.audioData.length, "payload-too-large");
            this.disconnect("Invalid voice payload");
            if (parallelLane) {
                PARALLEL_VOICE_DROPPED_INVALID_TOTAL.incrementAndGet();
            }
            return true;
        }

        if (this.player == null || this.player.dead) {
            logVoiceTcpDrop(now, packet64voice.audioData.length, "player-missing-or-dead");
            if (parallelLane) {
                PARALLEL_VOICE_DROPPED_INVALID_TOTAL.incrementAndGet();
            }
            return true;
        }

        if (!stopMarker && isTcpVoiceRateLimited(now, packet64voice.audioData.length)) {
            logVoiceTcpDrop(now, packet64voice.audioData.length, "rate-limited");
            if (parallelLane) {
                PARALLEL_VOICE_DROPPED_RATE_TOTAL.incrementAndGet();
            }
            return true;
        }

		if(uk.betacraft.uberbukkit.AdminRegistry.isMuted(this.player.name)) {
            logVoiceTcpDrop(now, packet64voice.audioData.length, "player-muted");
            if (parallelLane) {
                PARALLEL_VOICE_DROPPED_INVALID_TOTAL.incrementAndGet();
            }
			return true;
		}

		if(!this.canUseVoiceChat()) {
            logVoiceTcpDrop(now, packet64voice.audioData.length, "permission-denied");
            if (parallelLane) {
                PARALLEL_VOICE_DROPPED_INVALID_TOTAL.incrementAndGet();
            }
			return true;
		}

        boolean routeToRoom = this.minecraftServer.chatRoomManager.shouldRouteVoiceToRoom(this.player);
        String route = routeToRoom ? (stopMarker ? "room-stop" : "room") : (stopMarker ? "proximity-stop" : "proximity");
        logVoiceTcpAttempt(now, packet64voice.audioData.length, route);

        Packet64Voice outbound;
        if (routeToRoom) {
            outbound = packet64voice.cloneForForwarding(this.player.id, 0.0F, this.player.name);
        } else {
            outbound = packet64voice.cloneForForwarding(this.player.id, (float) this.minecraftServer.getVoiceChatBroadcastRadius(), this.player.name);
        }

        if (!enqueueTcpVoiceInbound(now, packet64voice.audioData.length, outbound, routeToRoom, parallelLane)) {
            logVoiceTcpDrop(now, packet64voice.audioData.length, "queue-overflow");
            if (parallelLane) {
                PARALLEL_VOICE_DROPPED_OVERFLOW_TOTAL.incrementAndGet();
            }
            return true;
        }

        if (parallelLane) {
            PARALLEL_VOICE_CONSUMED_TOTAL.incrementAndGet();
        }

        return true;
    }

    private boolean isParallelChatRateLimited(long now) {
        if (this.parallelChatWindowStartAt == 0L || now - this.parallelChatWindowStartAt >= PARALLEL_CHAT_RATE_WINDOW_MS) {
            this.parallelChatWindowStartAt = now;
            this.parallelChatMessagesInWindow = 0;
        }

        if (this.parallelChatMessagesInWindow >= PARALLEL_CHAT_MAX_MESSAGES_PER_WINDOW) {
            return true;
        }

        this.parallelChatMessagesInWindow++;
        return false;
    }

    private void logVoiceTcpAttempt(long now, int payloadLength, String route) {
        VOICE_TCP_ATTEMPTS_WINDOW.incrementAndGet();
    }

    private void logVoiceTcpDrop(long now, int payloadLength, String reason) {
        recordVoiceTcpDropReason(reason);
    }

    private boolean isTcpVoiceRateLimited(long now, int payloadLength) {
        int safePayloadLength = payloadLength;
        if (safePayloadLength < 0) {
            safePayloadLength = 0;
        }

        int packetsPerSecond = this.minecraftServer != null ? Math.max(1, this.minecraftServer.getVoiceRateMaxPacketsPerSec()) : 120;
        int bytesPerSecond = this.minecraftServer != null ? Math.max(1, this.minecraftServer.getVoiceRateMaxBytesPerSec()) : 131072;
        int burstSeconds = this.minecraftServer != null ? Math.max(1, this.minecraftServer.getVoiceRateBurstSeconds()) : 2;
        double packetCapacity = Math.max(1.0D, (double) packetsPerSecond * (double) burstSeconds);
        double byteCapacity = Math.max(1.0D, (double) bytesPerSecond * (double) burstSeconds);

        if (this.voiceTcpLimiterLastRefillAt <= 0L || now < this.voiceTcpLimiterLastRefillAt) {
            this.voiceTcpPacketTokens = packetCapacity;
            this.voiceTcpByteTokens = byteCapacity;
            this.voiceTcpLimiterLastRefillAt = now;
        } else {
            long elapsedMs = now - this.voiceTcpLimiterLastRefillAt;
            if (elapsedMs > 0L) {
                double elapsedSeconds = elapsedMs / 1000.0D;
                this.voiceTcpPacketTokens = Math.min(packetCapacity, this.voiceTcpPacketTokens + elapsedSeconds * (double) packetsPerSecond);
                this.voiceTcpByteTokens = Math.min(byteCapacity, this.voiceTcpByteTokens + elapsedSeconds * (double) bytesPerSecond);
                this.voiceTcpLimiterLastRefillAt = now;
            }
        }

        if (this.voiceTcpPacketTokens < 1.0D) {
            return true;
        }
        if (this.voiceTcpByteTokens < (double) safePayloadLength) {
            return true;
        }

        this.voiceTcpPacketTokens -= 1.0D;
        this.voiceTcpByteTokens -= (double) safePayloadLength;
        return false;
    }

    private boolean enqueueTcpVoiceInbound(long now, int payloadLength, Packet64Voice outbound, boolean routeToRoom, boolean parallelLane) {
        if (outbound == null) {
            return false;
        }
        startVoiceTcpInboundWorker();
        synchronized (this.voiceTcpInboundQueueLock) {
            if (this.voiceTcpInboundQueue.size() >= VOICE_TCP_INBOUND_QUEUE_MAX_PACKETS) {
                recordVoiceTcpBacklogState(now, this.voiceTcpInboundQueue.size(), true);
                this.voiceTcpInboundQueue.pollFirst();
                VOICE_TCP_QUEUE_OVERFLOW_WINDOW.incrementAndGet();
                return false;
            }
            this.voiceTcpInboundQueue.offerLast(new QueuedTcpVoicePacket(outbound, routeToRoom, parallelLane, System.nanoTime()));
            recordVoiceTcpBacklogState(now, this.voiceTcpInboundQueue.size(), false);
            this.voiceTcpInboundQueueLock.notifyAll();
            return true;
        }
    }

    private void recordVoiceTcpBacklogState(long now, int queueDepth, boolean droppedForOverflow) {
        if (queueDepth > this.voiceTcpBacklogPeakDepth) {
            this.voiceTcpBacklogPeakDepth = queueDepth;
        }
        if (droppedForOverflow) {
            this.voiceTcpBacklogDropCount++;
        }
        if (now - this.lastVoiceTcpBacklogLogAt < VOICE_TCP_BACKLOG_LOG_INTERVAL_MS) {
            return;
        }

        if (this.voiceTcpBacklogDropCount <= 0L && this.voiceTcpBacklogPeakDepth < VOICE_TCP_BACKLOG_WARN_QUEUE_DEPTH) {
            return;
        }

        String playerName = this.player != null && this.player.name != null ? this.player.name : "<unknown>";
        a.warning("[VoiceChat][ServerTcpRx] Voice transmissions backed up player=" + playerName
            + ", queueDepthPeak=" + this.voiceTcpBacklogPeakDepth + "/" + VOICE_TCP_INBOUND_QUEUE_MAX_PACKETS
            + ", dropped=" + this.voiceTcpBacklogDropCount);
        this.lastVoiceTcpBacklogLogAt = now;
        this.voiceTcpBacklogDropCount = 0L;
        this.voiceTcpBacklogPeakDepth = 0;
    }

    private void startVoiceTcpInboundWorker() {
        synchronized (this.voiceTcpInboundWorkerLock) {
            if (this.voiceTcpInboundWorkerRunning) {
                return;
            }
            this.voiceTcpInboundWorkerRunning = true;
            String playerName = this.player != null && this.player.name != null ? this.player.name : "unknown";
            Thread worker = new Thread(new Runnable() {
                public void run() {
                    runVoiceTcpInboundWorkerLoop();
                }
            }, "VoiceTcpInbound-" + playerName);
            worker.setDaemon(true);
            this.voiceTcpInboundWorkerThread = worker;
            worker.start();
        }
    }

    private void stopVoiceTcpInboundWorker(boolean clearQueue) {
        Thread workerToJoin = null;
        synchronized (this.voiceTcpInboundWorkerLock) {
            if (this.voiceTcpInboundWorkerRunning) {
                this.voiceTcpInboundWorkerRunning = false;
                workerToJoin = this.voiceTcpInboundWorkerThread;
                this.voiceTcpInboundWorkerThread = null;
            }
        }
        if (workerToJoin != null) {
            workerToJoin.interrupt();
            synchronized (this.voiceTcpInboundQueueLock) {
                this.voiceTcpInboundQueueLock.notifyAll();
            }
            if (Thread.currentThread() != workerToJoin) {
                try {
                    workerToJoin.join(120L);
                } catch (InterruptedException ignored) {}
            }
        }
        if (clearQueue) {
            synchronized (this.voiceTcpInboundQueueLock) {
                this.voiceTcpInboundQueue.clear();
            }
        }
    }

    private void runVoiceTcpInboundWorkerLoop() {
        while (this.voiceTcpInboundWorkerRunning) {
            try {
                QueuedTcpVoicePacket queued = null;
                synchronized (this.voiceTcpInboundQueueLock) {
                    if (this.voiceTcpInboundQueue.isEmpty()) {
                        this.voiceTcpInboundQueueLock.wait(25L);
                    }
                    if (!this.voiceTcpInboundQueue.isEmpty()) {
                        queued = this.voiceTcpInboundQueue.pollFirst();
                    }
                }
                if (queued == null) {
                    continue;
                }
                processQueuedTcpVoicePacket(queued);
            } catch (InterruptedException ignored) {
                break;
            } catch (Throwable t) {
                recordVoiceTcpDropReason("worker-error");
            }
        }
    }

    private void processQueuedTcpVoicePacket(QueuedTcpVoicePacket queued) {
        if (queued == null || queued.packet == null || this.disconnected || this.player == null) {
            return;
        }
        long queueWaitNanos = Math.max(0L, System.nanoTime() - queued.enqueueNanos);
        recordVoiceTcpQueueWaitSample(queueWaitNanos);
        if (queued.parallelLane) {
            PARALLEL_VOICE_QUEUE_WAIT_NANOS_TOTAL.addAndGet(queueWaitNanos);
            PARALLEL_VOICE_QUEUE_WAIT_SAMPLES_TOTAL.incrementAndGet();
            updateAtomicMax(PARALLEL_VOICE_QUEUE_WAIT_MAX_NANOS_SINCE_POLL, queueWaitNanos);
        }
        if (!this.minecraftServer.isVoiceChatEnabled()) {
            return;
        }
        if (queued.routeToRoom) {
            this.minecraftServer.chatRoomManager.broadcastVoice(this.player, queued.packet);
            return;
        }
        this.minecraftServer.serverConfigurationManager.sendPacketNearby(
            this.player,
            this.player.locX,
            this.player.locY,
            this.player.locZ,
            this.minecraftServer.getVoiceChatBroadcastRadius(),
            this.player.dimension,
            queued.packet
        );
    }

    private static void updateAtomicMax(AtomicLong target, long candidate) {
        long prev;
        do {
            prev = target.get();
            if (candidate <= prev) {
                return;
            }
        } while (!target.compareAndSet(prev, candidate));
    }

	private boolean canUseVoiceChat() {
		// Voice chat is enabled by default for all players
		// Admins can disable it for specific players using permission plugins
		// by negating the uberbukkit.voice.chat permission (set to false)
		CraftPlayer craft = this.getPlayer();
		if(craft == null) {
			return true;
		}
		// Check if permission is explicitly set to false (negated)
		// If not set at all, default to true (allowed)
		if(craft.isPermissionSet("uberbukkit.voice.chat")) {
			return craft.hasPermission("uberbukkit.voice.chat");
		}
		return true; // Default: voice chat allowed for everyone
    }

    public void handle66ChatRoomAction(Packet66ChatRoomAction packet66) {
        this.minecraftServer.chatRoomManager.handleAction(this.player, packet66);
    }

    public long mineExpire = 0;
    public long lastMine = 0;
    public int delaySound = 0;

    private boolean isCreativeSwordDig(Packet14BlockDig packet14blockdig) {
        if (packet14blockdig == null || this.player == null || this.player.itemInWorldManager == null) {
            return false;
        }

        if (!this.player.itemInWorldManager.isCreative()) {
            return false;
        }

        if (packet14blockdig.e != 0 && packet14blockdig.e != 1 && packet14blockdig.e != 2 && packet14blockdig.e != 3) {
            return false;
        }

        ItemStack itemInHand = this.player.inventory.getItemInHand();
        return itemInHand != null && itemInHand.getItem() instanceof ItemSword;
    }

    public void a(Packet14BlockDig packet14blockdig) {
        // poseidon
        PacketReceivedEvent event = new PacketReceivedEvent(server.getPlayer(player), packet14blockdig);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (this.player.dead) return; // CraftBukkit
        if (uk.betacraft.uberbukkit.AdminRegistry.isFrozen(this.player.name)) {
            // Cancel digging while frozen
            return;
        }

        if (this.isCreativeSwordDig(packet14blockdig)) {
            if (packet14blockdig.e == 0 || packet14blockdig.e == 1 || packet14blockdig.e == 3) {
                WorldServer worldserver = this.minecraftServer.getWorldServer(this.player.dimension);
                this.sendPacket(new Packet53BlockChange(packet14blockdig.a, packet14blockdig.b, packet14blockdig.c, worldserver));
            }
            this.mineExpire = 0;
            this.lastMine = 0;
            return;
        }

        WorldServer worldserver = this.minecraftServer.getWorldServer(this.player.dimension);

        if (packet14blockdig.e == 4) {
            // CraftBukkit start
            // If the ticks aren't the same then the count starts from 0 and we update the lastDropTick.
            if (this.lastDropTick != MinecraftServer.currentTick) {
                this.dropCount = 0;
                this.lastDropTick = MinecraftServer.currentTick;
            } else {
                // Else we increment the drop count and check the amount.
                this.dropCount++;
                if (this.dropCount >= 20) {
                    a.warning(this.player.name + " dropped their items too quickly!");
                    this.disconnect("You dropped your items too quickly (Hacking?)");
                }
            }
            // CraftBukkit end
            this.player.F();
        } else {
            boolean flag = worldserver.weirdIsOpCache = worldserver.dimension != 0 || this.minecraftServer.serverConfigurationManager.isOp(this.player.name); // CraftBukkit
            boolean flag1 = false;

            // uberbukkit

            // Pre-b1.3 block handling
            // e == 2 is stop digging (holds no block coordinate data)
            // e == 3 is expected block break from client
            // e == 1 is digging
            // e == 0 is start digging, or digging every 5 packets (notch is weird)

            // Post-b1.2_01 block handling
            // e == 2 is stop digging
            // e == 0 is start digging
            Integer i = packet14blockdig.a;
            Integer j = packet14blockdig.b;
            Integer k = packet14blockdig.c;

            if (packet14blockdig.e == 0) {
                flag1 = true;
            }

            if (packet14blockdig.e == 1 && this.networkManager.pvn <= 8) {
                flag1 = true;
            }

            if (packet14blockdig.e == 2 && this.networkManager.pvn >= 9) {
                flag1 = true;
            }

            if (flag1) {
                double d0 = this.player.locX - ((double) i + 0.5D);
                double d1 = this.player.locY - ((double) j + 0.5D);
                double d2 = this.player.locZ - ((double) k + 0.5D);
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;

                if (d3 > 36.0D) {
                    return;
                }
            }

            ChunkCoordinates chunkcoordinates = worldserver.getSpawn();
            int l = (int) MathHelper.abs((float) (i - chunkcoordinates.x));
            int i1 = (int) MathHelper.abs((float) (k - chunkcoordinates.z));

            if (l > i1) {
                i1 = l;
            }

            if (this.networkManager.pvn <= 8) {
                // CraftBukkit start
                CraftPlayer player = getPlayer();
                CraftBlock block = (CraftBlock) player.getWorld().getBlockAt(i, j, k);
                int blockId = block.getTypeId();
                float damage = 0;
                if (Block.byId[blockId] != null) {
                    damage = BlockMiningRegistryApi.getBreakProgressPerTick(player.getHandle(), worldserver, i, j, k); //Get amount of damage going to block
                }
                // CraftBukkit end

                if (packet14blockdig.e == 0) {
                    // CraftBukkit start
                    if (i1 > this.server.getSpawnRadius() || flag) {
                        if (blockId > 0) {
                            BlockDamageEvent breakEvent;
                            // If the amount of damage that the player is going to do to the block
                            // is >= 1, then the block is going to break (eg, flowers, torches)
                            if (damage >= 1.0F) {
                                // if we are destroying either a redstone wire with a current greater than 0 or
                                // a redstone torch that is on, then we should notify plugins that this block has
                                // returned to a current value of 0 (since it will once the redstone is destroyed)
                                if ((blockId == Block.REDSTONE_WIRE.id && block.getData() > 0) || blockId == Block.REDSTONE_TORCH_ON.id) {
                                    server.getPluginManager().callEvent(new BlockRedstoneEvent(block, (blockId == Block.REDSTONE_WIRE.id ? block.getData() : 15), 0));
                                }
                                breakEvent = new BlockDamageEvent(player, block, player.getItemInHand(), true);
                            } else {
                                breakEvent = new BlockDamageEvent(player, block, player.getItemInHand(), false);
                            }
                            server.getPluginManager().callEvent(breakEvent);
                            if (!breakEvent.isCancelled()) {
                                this.player.itemInWorldManager.oldClick(i, j, k, packet14blockdig.face);

                                this.lastDigFace = packet14blockdig.face; // uberbukkit - handle digging
                            }
                        }
                    }
                    // CraftBukkit end
                } else if (packet14blockdig.e == 2) {
                    // CraftBukkit start - Get last block that the player hit
                    // Otherwise the block is a Bedrock @(0,0,0)
                    block = (CraftBlock) player.getWorld().getBlockAt(lastDigX, lastDigY, lastDigZ);
                    BlockDamageEvent breakEvent = new BlockDamageEvent(player, block, player.getItemInHand(), damage >= 1.0F);
                    server.getPluginManager().callEvent(breakEvent);
                    if (!breakEvent.isCancelled()) {
                        this.player.itemInWorldManager.oldHaltBreak();
                    }
                    // CraftBukkit end
                } else if (packet14blockdig.e == 1) {
                    // CraftBukkit start
                    if (i1 > this.server.getSpawnRadius() || flag) {
                        BlockDamageEvent breakEvent;
                        // If the amount of damage going to the block plus the current amount
                        // of damage is greater than 1, the block is going to break.
                        if (this.player.itemInWorldManager.damageDealt + damage >= 1.0F) {
                            // if we are destroying either a redstone wire with a current greater than 0 or
                            // a redstone torch that is on, then we should notify plugins that this block has
                            // returned to a current value of 0 (since it will once the redstone is destroyed)
                            if ((blockId == Block.REDSTONE_WIRE.id && block.getData() > 0) || blockId == Block.REDSTONE_TORCH_ON.id) {
                                server.getPluginManager().callEvent(new BlockRedstoneEvent(block, (blockId == Block.REDSTONE_WIRE.id ? block.getData() : 15), 0));
                            }
                            breakEvent = new BlockDamageEvent(player, block, player.getItemInHand(), damage >= 1.0F);
                        } else {
                            breakEvent = new BlockDamageEvent(player, block, player.getItemInHand(), damage >= 1.0F);
                        }
                        server.getPluginManager().callEvent(breakEvent);
                        if (!breakEvent.isCancelled()) {
                            this.player.itemInWorldManager.oldDig(i, j, k, packet14blockdig.face);

                            this.lastDigFace = packet14blockdig.face; // uberbukkit - handle digging
                        } else {
                            this.player.itemInWorldManager.damageDealt = 0; // Reset the amount of damage if stopping break.
                        }
                    }
                    // CraftBukkit end
                } else if (packet14blockdig.e == 3) {
                    double d5 = this.player.locX - ((double) i + 0.5D);
                    double d6 = this.player.locY - ((double) j + 0.5D);
                    double d7 = this.player.locZ - ((double) k + 0.5D);
                    double d8 = d5 * d5 + d6 * d6 + d7 * d7;

                    if (d8 < 256.0D) {
                        this.player.netServerHandler.sendPacket((Packet) (new Packet53BlockChange(i, j, k, this.player.world))); // Craftbukkit
                    }
                }
            } else {
                if (packet14blockdig.e == 0) {
                    // CraftBukkit
                    if (i1 < this.server.getSpawnRadius() && !flag) {
                        this.player.netServerHandler.sendPacket(new Packet53BlockChange(i, j, k, worldserver));
                    } else {
                        // CraftBukkit - add face argument
                        this.player.itemInWorldManager.dig(i, j, k, packet14blockdig.face);

                        // uberbukkit - handle digging
                        this.mineExpire = this.player.itemInWorldManager.getExpectedDigEnd();
                        this.lastDigFace = packet14blockdig.face;
                    }
                } else if (packet14blockdig.e == 2) {
                    // uberbukkit - swapped i,j,k for lastDigX,lastDigY,lastDigZ
                    this.player.itemInWorldManager.a(lastDigX, lastDigY, lastDigZ);

                    // uberbukkit - handle digging
                    this.mineExpire = 0;
                    this.lastMine = 0;

                    if (worldserver.getTypeId(lastDigX, lastDigY, lastDigZ) != 0) {
                        this.player.netServerHandler.sendPacket(new Packet53BlockChange(lastDigX, lastDigY, lastDigZ, worldserver));
                    }
                } else if (packet14blockdig.e == 3) {
                    double d4 = this.player.locX - ((double) i + 0.5D);
                    double d5 = this.player.locY - ((double) j + 0.5D);
                    double d6 = this.player.locZ - ((double) k + 0.5D);
                    double d7 = d4 * d4 + d5 * d5 + d6 * d6;

                    if (d7 < 256.0D) {
                        this.player.netServerHandler.sendPacket(new Packet53BlockChange(i, j, k, worldserver));
                    }
                }
            }

            // uberbukkit - reset last positions when stops digging
            lastDigX = i;
            lastDigY = j;
            lastDigZ = k;

            worldserver.weirdIsOpCache = false;
        }
    }

    public void a(Packet15Place packet15place) {
//        System.out.println("Packet15 received");
//        System.out.println("a: " + packet15place.a);
//        System.out.println("b: " + packet15place.b);
//        System.out.println("c: " + packet15place.c);
//        System.out.println("face: " + packet15place.face);
//        System.out.println("data: " + packet15place.data);
        // poseidon
        PacketReceivedEvent pevent = new PacketReceivedEvent(server.getPlayer(player), packet15place);
        server.getPluginManager().callEvent(pevent);
        if (pevent.isCancelled()) return;

        WorldServer worldserver = this.minecraftServer.getWorldServer(this.player.dimension);

        // CraftBukkit start
        if (this.player.dead) return;

        // uberbukkit: noptch what the fuck have you done
        if (this.networkManager.pvn == 7) {
            if (packet15place.itemstack != null && packet15place.a != -1 && packet15place.b != 255 && packet15place.c != -1 && (packet15place.itemstack.id == Item.BUCKET.id || packet15place.itemstack.id == Item.WATER_BUCKET.id || packet15place.itemstack.id == Item.LAVA_BUCKET.id || packet15place.itemstack.id == Item.MILK_BUCKET.id)) {
                return;
            }
        }

        // This is a horrible hack needed because the client sends 2 packets on 'right mouse click'
        // aimed at a block. We shouldn't need to get the second packet if the data is handled
        // but we cannot know what the client will do, so we might still get it
        //
        // If the time between packets is small enough, and the 'signature' similar, we discard the
        // second one. This sadly has to remain until Mojang makes their packets saner. :(
        //  -- Grum

        if (packet15place.face == 255) {
            if (packet15place.itemstack != null && packet15place.itemstack.id == this.lastMaterial && this.lastPacket != null && packet15place.timestamp - this.lastPacket < RIGHT_CLICK_DUPLICATE_SUPPRESS_MS) {
                this.lastPacket = null;
                return;
            }
        } else {
            this.lastMaterial = packet15place.itemstack == null ? -1 : packet15place.itemstack.id;
            this.lastPacket = packet15place.timestamp;
        }

        // CraftBukkit - if rightclick decremented the item, always send the update packet.
        // this is not here for CraftBukkit's own functionality; rather it is to fix
        // a notch bug where the item doesn't update correctly.
        boolean always = false;

        // CraftBukkit end

        if (uk.betacraft.uberbukkit.AdminRegistry.isFrozen(this.player.name)) {
            return; // No place/use while frozen
        }
        ItemStack itemstack = this.player.inventory.getItemInHand();
        boolean flag = worldserver.weirdIsOpCache = worldserver.dimension != 0 || this.minecraftServer.serverConfigurationManager.isOp(this.player.name); // CraftBukkit

        if (packet15place.face == 255) {
            if (itemstack == null) {
                return;
            }

            // CraftBukkit start
            int itemstackAmount = itemstack.count;
            PlayerInteractEvent event = CraftEventFactory.callPlayerInteractEvent(this.player, Action.RIGHT_CLICK_AIR, itemstack);
            if (event.useItemInHand() != Event.Result.DENY) {
                this.player.itemInWorldManager.useItem(this.player, this.player.world, itemstack);
            }

            // CraftBukkit - notch decrements the counter by 1 in the above method with food,
            // snowballs and so forth, but he does it in a place that doesn't cause the
            // inventory update packet to get sent
            always = (itemstack.count != itemstackAmount);
            // CraftBukkit end
        } else {
            int i = packet15place.a;
            int j = packet15place.b;
            int k = packet15place.c;
            int l = packet15place.face;
            ChunkCoordinates chunkcoordinates = worldserver.getSpawn();
            int i1 = (int) MathHelper.abs((float) (i - chunkcoordinates.x));
            int j1 = (int) MathHelper.abs((float) (k - chunkcoordinates.z));

            if (i1 > j1) {
                j1 = i1;
            }

            // CraftBukkit start - Check if we can actually do something over this large a distance
            Location eyeLoc = this.getPlayer().getEyeLocation();
            if (Math.pow(eyeLoc.getX() - i, 2) + Math.pow(eyeLoc.getY() - j, 2) + Math.pow(eyeLoc.getZ() - k, 2) > PLACE_DISTANCE_SQUARED) {
                return;
            }
            flag = true; // spawn protection moved to ItemBlock!!!
            // CraftBukkit end

            if (j1 > 16 || flag) {
                this.player.itemInWorldManager.interact(this.player, worldserver, itemstack, i, j, k, l);
            }

            this.player.netServerHandler.sendPacket(new Packet53BlockChange(i, j, k, worldserver));
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

            this.player.netServerHandler.sendPacket(new Packet53BlockChange(i, j, k, worldserver));
        }

        itemstack = this.player.inventory.getItemInHand();
        if (itemstack != null && itemstack.count == 0) {
            this.player.inventory.items[this.player.inventory.itemInHandIndex] = null;
        }

        this.player.h = true;
        this.player.inventory.items[this.player.inventory.itemInHandIndex] = ItemStack.b(this.player.inventory.items[this.player.inventory.itemInHandIndex]);
        Slot slot = this.player.activeContainer.a(this.player.inventory, this.player.inventory.itemInHandIndex);

        this.player.activeContainer.a();
        this.player.h = false;
        // CraftBukkit
        if (!ItemStack.equals(this.player.inventory.getItemInHand(), packet15place.itemstack) || always) {
            if (this.networkManager.pvn <= 6) {
                this.refreshInventory();
            } else {
                this.sendPacket(new Packet103SetSlot(this.player.activeContainer.windowId, slot.a, this.player.inventory.getItemInHand()));
            }
        }

        worldserver.weirdIsOpCache = false;
    }

    // uberbukkit
    public void refreshInventory() {
        this.sendPacket(new Packet5EntityEquipment(-1, this.player.inventory.items));
        this.sendPacket(new Packet5EntityEquipment(-2, this.player.inventory.craft));
        this.sendPacket(new Packet5EntityEquipment(-3, this.player.inventory.armor));
    }

    public void a(String s, Object[] aobject) {
        if (this.disconnected) return; // CraftBukkit - rarely it would send a disconnect line twice
        stopVoiceTcpInboundWorker(true);


        if (!(boolean) PoseidonConfig.getInstance().getConfigOption("settings.remove-join-leave-debug", true) || !s.equals("disconnect.quitting")) {
            a.info(this.player.name + " lost connection: " + s);
        }

        a.info(this.player.name + " has left the game.");
        // CraftBukkit start - we need to handle custom quit messages
        String quitMessage = this.minecraftServer.serverConfigurationManager.disconnect(this.player);
        if (quitMessage != null) {
            this.minecraftServer.serverConfigurationManager.sendAll(new Packet3Chat(quitMessage));
        }
        // CraftBukkit end
        this.disconnected = true;
    }

    public void a(Packet packet) {
        a.warning(this.getClass() + " wasn\'t prepared to deal with a " + packet.getClass());
        this.disconnect("Protocol error, unexpected packet");
    }

    public void sendPacket(Packet packet) {
        //Poseidon Start - Send Packet Event
        if (packet == null) // Why do anything if there's no packet? (fixes Internal server error)
            return;

        if (firePacketEvents) {
            PlayerSendPacketEvent event = new PlayerSendPacketEvent(this.player.name, packet);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            packet = event.getPacket(); //In case a plugin replaces the entire packet
        }
        //Poseidon End

        // UberBukkit
        PacketSentEvent packetSentEvent = new PacketSentEvent(getPlayer(), packet);
        Bukkit.getPluginManager().callEvent(packetSentEvent);
        if (packetSentEvent.isCancelled()) {
            return;
        }
        packet = packetSentEvent.getPacket();

        if (this.supportsEntityWireV2()) {
            packet = this.translateEntityPacketToV2(packet);
            if (packet == null) {
                return;
            }
        }

        Protocol protocol = this.player.protocol;
        boolean supportsPacket = protocol.canReceivePacket(packet.b());
        if (!supportsPacket && packet instanceof Packet62Sound && this.isMcoseClient()) {
            // MCOSE clients can decode Packet62Sound even when logged in as protocol 14.
            supportsPacket = true;
        }

        if (!supportsPacket) {
            Packet fallbackPacket = this.createLegacySoundFallback(packet);
            if (fallbackPacket == null || !protocol.canReceivePacket(fallbackPacket.b())) {
                this.g = this.f;
                return;
            }
            packet = fallbackPacket;
        }

        // uberbukkit - try to disallow for incompatible blocks and packets
        if (packet instanceof Packet5EntityEquipment) {
            Packet5EntityEquipment packet5 = (Packet5EntityEquipment) packet;

            // don't send entity equipment data to pre-b1.0 clients
            // (they use packet 5 for inventory data)
            if (packet5.items == null && this.networkManager.pvn < 7) {
                this.g = this.f;
                return;
            }

            if (packet5.c > 0 && !protocol.canReceiveBlockItem(packet5.c)) {
                this.networkManager.queue(new Packet5EntityEquipment(packet5.a, packet5.b, null));
                packet = null;
            }
        } else if (packet instanceof Packet53BlockChange) {
            Packet53BlockChange packet53 = (Packet53BlockChange) packet;
            if (!protocol.canReceiveBlockItem(packet53.material)) {
                this.networkManager.queue(new Packet53BlockChange(packet53.a, packet53.b, packet53.c, 1, packet53.data));
                packet = null;
            }
        } else if (packet instanceof Packet18ArmAnimation) {
            Packet18ArmAnimation packet18 = (Packet18ArmAnimation) packet;

            // skip riding/burning/sneaking packets for a1.1.2_01
            if (this.networkManager.pvn <= 2 && packet18.b >= 100) {
                this.g = this.f;
                return;
            }
        } else if (packet instanceof Packet8UpdateHealth) {
            Packet8UpdateHealth packet8 = (Packet8UpdateHealth) packet;
            if (this.networkManager.pvn <= 7 && packet8.a < 0) {
                packet8.a = 0;
            }
        }

        // CraftBukkit start
        else if (packet instanceof Packet6SpawnPosition) {
            Packet6SpawnPosition packet6 = (Packet6SpawnPosition) packet;
            this.player.compassTarget = new Location(this.getPlayer().getWorld(), packet6.x, packet6.y, packet6.z);
        } else if (packet instanceof Packet51MapChunk) {
            if (!ChunkCompressionThread.sendPacket(this.player, packet)) {
                // If compression queue is currently saturated, fall back to direct network queue
                // to avoid dropping chunk packets.
                this.networkManager.queue(packet);
            }
            packet = null;
        } else if (packet instanceof Packet3Chat) {
            String message = ((Packet3Chat) packet).message;
            
            // Skip word wrapping for internal protocol messages - they should be sent as-is
            if (message != null && message.startsWith("[[") && message.endsWith("]]")) {
                this.networkManager.queue(packet);
                packet = null;
            } else {
                // uberbukkit - wrap normal chat messages
                String[] wrapped = null;
                if (this.networkManager.pvn >= 9) { // TODO check compatibility
                    wrapped = TextWrapper.wrapText(message);
                } else {
                    wrapped = TextWrapper.wrapTextLegacy(message);
                }

                for (final String line : wrapped) {
                    this.networkManager.queue(new Packet3Chat(line));
                }
                packet = null;
            }
        }
        if (packet != null) this.networkManager.queue(packet);
        // CraftBukkit end

        this.g = this.f;
    }

    private Packet createLegacySoundFallback(Packet packet) {
        if (!(packet instanceof Packet62Sound)) {
            return null;
        }

        Packet62Sound soundPacket = (Packet62Sound) packet;
        String sound = soundPacket.sound;
        if (sound == null) {
            return null;
        }

        // Protocol 14 clients do not accept Packet62Sound. Map lever/button clicks to legacy aux effects.
        if ("random.click".equals(sound) || "ui.button.click".equals(sound)) {
            int effectId = soundPacket.pitch >= 0.55F ? 1001 : 1000;
            int x = MathHelper.floor(soundPacket.locX);
            int y = MathHelper.floor(soundPacket.locY);
            int z = MathHelper.floor(soundPacket.locZ);
            return new Packet61(effectId, x, y, z, 0);
        }

        return null;
    }

    public void a(Packet16BlockItemSwitch packet16blockitemswitch) {
        // poseidon
        PacketReceivedEvent pevent = new PacketReceivedEvent(server.getPlayer(player), packet16blockitemswitch);
        server.getPluginManager().callEvent(pevent);
        if (pevent.isCancelled()) return;

        if (this.player.dead) return; // CraftBukkit

        if (this.networkManager.pvn >= 7) {
            if (packet16blockitemswitch.itemInHandIndex >= 0 && packet16blockitemswitch.itemInHandIndex <= InventoryPlayer.e()) {
                // CraftBukkit start
                PlayerItemHeldEvent event = new PlayerItemHeldEvent(this.getPlayer(), this.player.inventory.itemInHandIndex, packet16blockitemswitch.itemInHandIndex);
                this.server.getPluginManager().callEvent(event);
                // CraftBukkit end

                this.player.inventory.itemInHandIndex = packet16blockitemswitch.itemInHandIndex;
            } else {
                a.warning(this.player.name + " tried to set an invalid carried item");
                this.disconnect("Invalid hotbar selection (Hacking?)");
            }
        } else {

            for (int i = 0; i < 9; i++) {
                ItemStack stack = this.player.inventory.items[i];

                if ((stack != null && stack.id == packet16blockitemswitch.itemId) || (stack == null && packet16blockitemswitch.itemId == 0)) {
                    this.player.inventory.itemInHandIndex = i;
                }
            }
        }
    }

    private Packet translateEntityPacketToV2(Packet packet) {
        if (packet == null) {
            return null;
        }
        if (packet instanceof Packet24MobSpawn) {
            return new Packet204AddEntityV2((Packet24MobSpawn)packet);
        }
        if (packet instanceof Packet40EntityMetadata) {
            return new Packet205SetEntityDataV2((Packet40EntityMetadata)packet);
        }
        if (packet instanceof Packet34EntityTeleport) {
            return Packet206EntityMoveV2.fromTeleport((Packet34EntityTeleport)packet);
        }
        if (packet instanceof Packet30Entity) {
            return Packet206EntityMoveV2.fromLegacy((Packet30Entity)packet);
        }
        if (packet instanceof Packet39AttachEntity) {
            return new Packet207EntityLinkV2((Packet39AttachEntity)packet);
        }
        if (packet instanceof Packet5EntityEquipment) {
            Packet5EntityEquipment legacy = (Packet5EntityEquipment)packet;
            if (legacy.items == null) {
                return new Packet208EntityEquipmentV2(legacy);
            }
        }
        return packet;
    }

    public void a(Packet3Chat packet3chat) {
        // poseidon
        PacketReceivedEvent event = new PacketReceivedEvent(server.getPlayer(player), packet3chat);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        String s = packet3chat.message;

        if (s.length() > Packet3Chat.MAX_CHAT_LENGTH) {
            this.disconnect("Chat message too long");
        } else {
            s = s.trim();

            for (int i = 0; i < s.length(); ++i) {
                if (!FontAllowedCharacters.isAllowedCharacter(s.charAt(i))) {
                    this.disconnect("Illegal characters in chat");
                    return;
                }
            }

            // CraftBukkit start
            this.chat(s);
        }
    }

    public boolean chat(String s) {
        if (!this.player.dead) {
            // UberBukkit - Start
            // Forcefully closes a container when a player sends a chat message
            if (player.activeContainer != player.defaultContainer) {
                player.y();
            }
            // UberBukkit - End

            if (s.startsWith("/")) {
                this.handleCommand(s);
                return true;
            } else {
                if (uk.betacraft.uberbukkit.AdminRegistry.isMuted(this.player.name)) {
                    this.getPlayer().sendMessage("§cYou are muted.");
                    return true; // swallow chat
                }
                Player player = this.getPlayer();
                PlayerChatEvent event = new PlayerChatEvent(player, s);
                this.server.getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return true;
                }

                s = String.format(event.getFormat(), event.getPlayer().getDisplayName(), event.getMessage());
                minecraftServer.console.sendMessage(s);
                for (Player recipient : event.getRecipients()) {
                    recipient.sendMessage(s);
                }
            }
        }

        return false;
        // CraftBukkit end
    }

    private void handleCommand(String s) {
        // CraftBukkit start
        CraftPlayer player = this.getPlayer();

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, s);
        this.server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return;
        }

        s = event.getMessage(); //Poseidon: Override command with new command string.

        try {
            if (this.server.dispatchCommand(player, s.substring(1))) {
                //Project Poseidon Start
                //Hide commands from being logged in console
                String cmdName = s.split(" ")[0].replaceAll("/", "");

                boolean suppress = Poseidon.getServer().isCommandHidden(cmdName)
                        || cmdName.startsWith("openinv")
                        || cmdName.equals("admin");
                if (!suppress) {
                    a.info(player.getName() + " issued server command: " + s);
                }

                //Project Poseidon End
                return;
            }
        } catch (CommandException ex) {
            player.sendMessage(ChatColor.RED + "An internal error occurred while attempting to perform this command");
            Logger.getLogger(NetServerHandler.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            return;
        }
        // CraftBukkit end

        /* CraftBukkit start - No longer neaded av we have already handled it server.dispatchCommand above.
        if (s.toLowerCase().startsWith("/me ")) {
            s = "* " + this.player.name + " " + s.substring(s.indexOf(" ")).trim();
            a.info(s);
            this.minecraftServer.serverConfigurationManager.sendAll(new Packet3Chat(s));
        } else if (s.toLowerCase().startsWith("/kill")) {
            this.player.damageEntity(this.player, 1000); // CraftBukkit - replace null entity with player entity; TODO: decide if we want damage with a null source to fire an event.
        } else if (s.toLowerCase().startsWith("/tell ")) {
            String[] astring = s.split(" ");

            if (astring.length >= 3) {
                s = s.substring(s.indexOf(" ")).trim();
                s = s.substring(s.indexOf(" ")).trim();
                s = "\u00A77" + this.player.name + " whispers " + s;
                a.info(s + " to " + astring[1]);
                if (!this.minecraftServer.serverConfigurationManager.a(astring[1], (Packet) (new Packet3Chat(s)))) {
                    this.sendPacket(new Packet3Chat("\u00A7cThere\'s no player by that name online."));
                }
            }
        } else {
            String s1;

            if (this.minecraftServer.serverConfigurationManager.isOp(this.player.name)) {
                s1 = s.substring(1);
                a.info(this.player.name + " issued server command: " + s1);
                this.minecraftServer.issueCommand(s1, this);
            } else {
                s1 = s.substring(1);
                a.info(this.player.name + " tried command: " + s1);
            }
        }
        // CraftBukkit end */
    }

    public void a(Packet18ArmAnimation packet18armanimation) {
        // poseidon
        PacketReceivedEvent pevent = new PacketReceivedEvent(server.getPlayer(player), packet18armanimation);
        server.getPluginManager().callEvent(pevent);
        if (pevent.isCancelled()) return;

        // CraftBukkit start
        if (this.player.dead) return;

        if (packet18armanimation.b == 104 || packet18armanimation.b == 105) {
            PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getPlayer(), packet18armanimation.b == 104);
            this.server.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end

        if (packet18armanimation.b == 1) {
            // CraftBukkit start - raytrace to look for 'rogue armswings'
            float f = 1.0F;
            float f1 = this.player.lastPitch + (this.player.pitch - this.player.lastPitch) * f;
            float f2 = this.player.lastYaw + (this.player.yaw - this.player.lastYaw) * f;
            double d0 = this.player.lastX + (this.player.locX - this.player.lastX) * (double) f;
            double d1 = this.player.lastY + (this.player.locY - this.player.lastY) * (double) f + 1.62D - (double) this.player.height;
            double d2 = this.player.lastZ + (this.player.locZ - this.player.lastZ) * (double) f;
            Vec3D vec3d = Vec3D.create(d0, d1, d2);

            float f3 = MathHelper.cos(-f2 * 0.017453292F - 3.1415927F);
            float f4 = MathHelper.sin(-f2 * 0.017453292F - 3.1415927F);
            float f5 = -MathHelper.cos(-f1 * 0.017453292F);
            float f6 = MathHelper.sin(-f1 * 0.017453292F);
            float f7 = f4 * f5;
            float f8 = f3 * f5;
            double d3 = 5.0D;
            Vec3D vec3d1 = vec3d.add((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
            MovingObjectPosition movingobjectposition = this.player.world.rayTrace(vec3d, vec3d1, true);

            if (movingobjectposition == null || movingobjectposition.type != EnumMovingObjectType.TILE) {
                CraftEventFactory.callPlayerInteractEvent(this.player, Action.LEFT_CLICK_AIR, this.player.inventory.getItemInHand());
            }

            // Arm swing animation
            PlayerAnimationEvent event = new PlayerAnimationEvent(this.getPlayer());
            this.server.getPluginManager().callEvent(event);

            if (event.isCancelled()) return;
            // CraftBukkit end

            this.player.w();

            // uberbukkit - handle digging
            if (this.mineExpire >= System.currentTimeMillis() && this.networkManager.pvn >= 9) {
                lastMine = System.currentTimeMillis();
            }
        } else if (packet18armanimation.b == 104) {
            this.player.setSneak(true);
        } else if (packet18armanimation.b == 105) {
            this.player.setSneak(false);
        }
    }

    public void a(Packet19EntityAction packet19entityaction) {
        // poseidon
        PacketReceivedEvent pevent = new PacketReceivedEvent(server.getPlayer(player), packet19entityaction);
        server.getPluginManager().callEvent(pevent);
        if (pevent.isCancelled()) return;

        // CraftBukkit start
        if (this.player.dead) return;

        if (packet19entityaction.animation == 1 || packet19entityaction.animation == 2) {
            PlayerToggleSneakEvent event = new PlayerToggleSneakEvent(this.getPlayer(), packet19entityaction.animation == 1);
            this.server.getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return;
            }
        }
        // CraftBukkit end

        if (packet19entityaction.animation == 1) {
            this.player.setSneak(true);
        } else if (packet19entityaction.animation == 2) {
            this.player.setSneak(false);
        } else if (packet19entityaction.animation == 3) {
            this.player.a(false, true, true);
            this.checkMovement = false;
        } else if (packet19entityaction.animation == 4) {
            // Dismount from vehicle (boat/minecart)
            if (this.player.vehicle != null) {
                this.player.mount(null);
            }
        } else if (packet19entityaction.animation == 5) {
            // MCOSE: Player opened inventory - grant "Taking Inventory" achievement
            if (this.player.achievementManager != null && !this.player.achievementManager.hasAchievement(AchievementList.openInventory)) {
                this.player.achievementManager.unlock(AchievementList.openInventory);
            }
        }
    }

    // Creative inventory slot sync (client -> server)
    public void handleCreativeSlot(Packet107CreativeSetSlot packet) {
        if (this.player == null) return;
        // Allow from true creative OR trusted modded clients (pvn >= 12) for pick-block support
        boolean allow = (this.player.gameMode == 1) || (this.networkManager != null && this.networkManager.pvn >= 12);
        if (!allow) return;
        ItemStack stack = sanitizeCreativeStack(packet.itemStack);
        if (packet.slot == -1) {
            if (stack != null) {
                int max = Math.min(64, stack.getMaxStackSize());
                if (stack.count < 1) stack.count = 1;
                if (stack.count > max) stack.count = max;
                this.player.a(stack, true);
            }
            return;
        }
        int slot = packet.slot;
        if (stack != null) {
            int max = Math.min(64, stack.getMaxStackSize());
            if (stack.count < 1) stack.count = 1;
            if (stack.count > max) stack.count = max;
        }
        if (slot >= 0 && slot < 36) {
            this.player.inventory.items[slot] = stack;
        } else if (slot >= 36 && slot < 45) {
            this.player.inventory.items[slot - 36] = stack;
        } else {
            return;
        }
        ItemStack confirm = (slot >= 36 && slot < 45) ? this.player.inventory.items[slot - 36] : this.player.inventory.items[slot];
        this.player.netServerHandler.sendPacket(new Packet103SetSlot(0, slot, confirm));
    }

    private ItemStack sanitizeCreativeStack(ItemStack stack) {
        if (stack == null) {
            return null;
        }

        if (stack.getItem() != null) {
            return stack;
        }

        // Compatibility shim: legacy clients/palettes may still send fence gate as 150.
        if (stack.id == 150 && Block.FENCE_GATE != null && Item.byId[Block.FENCE_GATE.id] != null) {
            stack.id = Block.FENCE_GATE.id;
            if (stack.getItem() != null) {
                return stack;
            }
        }

        return null;
    }

    public void a(Packet0KeepAlive packet0KeepAlive) {
        this.receivedKeepAlive = true;
    }

    public void a(Packet255KickDisconnect packet255kickdisconnect) {
        // poseidon
        PacketReceivedEvent event = new PacketReceivedEvent(server.getPlayer(player), packet255kickdisconnect);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        // uberbukkit - drop item queue on disconnect
        if (this.networkManager.pvn <= 6) {
            ArrayList<ItemStack> queue = this.player.packet5.queue.dropAllQueue();
            Player bukkitEntity = (Player) this.player.getBukkitEntity();
            for (ItemStack item : queue) {
                System.out.println("Drop queue id: " + item.id + ", dmg: " + item.damage + ", cnt: " + item.count);
                HashMap<Integer, org.bukkit.inventory.ItemStack> map = bukkitEntity.getInventory().addItem(new CraftItemStack(item));
                // drop what couldn't fit in the inventory
                for (org.bukkit.inventory.ItemStack stack : map.values()) {
                    bukkitEntity.getWorld().dropItemNaturally(bukkitEntity.getLocation(), stack);
                }
            }
        }
        this.networkManager.a("disconnect.quitting", new Object[0]);
    }

    public int b() {
        return this.networkManager.e();
    }

    public int getQueuedPacketCount() {
        return this.networkManager.getQueuedPacketCount();
    }

    public void sendMessage(String s) {
        this.sendPacket(new Packet3Chat("\u00A77" + s));
    }

    public String getName() {
        return this.player.name;
    }

    public void a(Packet7UseEntity packet7useentity) {
        // poseidon
        PacketReceivedEvent pevent = new PacketReceivedEvent(server.getPlayer(player), packet7useentity);
        server.getPluginManager().callEvent(pevent);
        if (pevent.isCancelled()) return;

        if (this.player.dead) return; // CraftBukkit

        WorldServer worldserver = this.minecraftServer.getWorldServer(this.player.dimension);
        Entity entity = worldserver.getEntity(packet7useentity.target);
        ItemStack itemInHand = this.player.inventory.getItemInHand();

        if (!PlayerCapabilityRegistryApi.canAffectEntities(this.player)) {
            return;
        }

        if (entity != null) {

            // uberbukkit start
            // backported from release 1.2, fixes mobs being unpunchable from certain positions
            boolean flag = this.player.e(entity);
            double maxRange = 36.0D;

            if (!flag) maxRange = 9.0D;

            if (this.player.g(entity) >= maxRange) return;

            // uberbukkit end

            if (packet7useentity.c == 0) {
                InteractEntityEvent interactEntityEvent = new InteractEntityEvent(this.player, entity, this.player.world);
                EventBus.global().publish(interactEntityEvent);
                if (interactEntityEvent.isCancelled()) {
                    return;
                }

                Player player = (Player) this.getPlayer();
                org.bukkit.entity.Entity bukkitEntity = entity.getBukkitEntity();
                // CraftBukkit start
                //Project Poseidon Start - Fixes a Minecart dupe glitch
                if (player.isInsideVehicle() && bukkitEntity instanceof StorageMinecart) {
                    return;
                }
                //Project Poseidon End
                PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, bukkitEntity);
                this.server.getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    return;
                }
                // CraftBukkit end
                this.player.c(entity);
                // CraftBukkit start - update the client if the item is an infinite one
                if (itemInHand != null && itemInHand.count <= -1) {
                    this.player.updateInventory(this.player.activeContainer);
                }
                // CraftBukkit end
            } else if (packet7useentity.c == 1) {
                AttackEntityEvent attackEntityEvent = new AttackEntityEvent(this.player, entity, this.player.world);
                EventBus.global().publish(attackEntityEvent);
                if (attackEntityEvent.isCancelled()) {
                    return;
                }

                this.player.d(entity);
                // CraftBukkit start - update the client if the item is an infinite one
                if (itemInHand != null && itemInHand.count <= -1) {
                    this.player.updateInventory(this.player.activeContainer);
                }
                // CraftBukkit end
            }
        }
    }

    public void a(Packet9Respawn packet9respawn) {
        // poseidon
        PacketReceivedEvent event = new PacketReceivedEvent(server.getPlayer(player), packet9respawn);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        System.out.println("[Respawn] Player " + this.player.name + " requesting respawn. Health=" + this.player.health + 
            ", Hardcore=" + this.player.isHardcoreMode() + ", GameMode=" + this.player.gameMode);

        if (this.player.dead || this.player.health <= 0) {
            // Hardcore mode: check if player is still banned
            if (this.player.isHardcoreMode()) {
                // Check if player is currently banned - if NOT banned, they were unbanned by admin
                boolean isBanned = this.minecraftServer.serverConfigurationManager.banByName.contains(this.player.name.toLowerCase());
                
                System.out.println("[Hardcore Respawn] Player " + this.player.name + " - isBanned=" + isBanned + 
                    ", banList contains: " + this.minecraftServer.serverConfigurationManager.banByName);
                
                if (isBanned) {
                    // Still banned - kick them
                    String kickMessage = PoseidonConfig.getInstance().getConfigString("world-settings.hardcore.death-kick-message");
                    this.disconnect(kickMessage != null ? kickMessage : "You died in hardcore mode!");
                    return;
                }
                
                // Player was unbanned - allow them to respawn but KEEP them in hardcore mode
                // They should still be playing hardcore and will be banned again if they die
                System.out.println("[Hardcore] Player " + this.player.name + " was unbanned - allowing respawn (staying in hardcore mode)");
            }

            try {
                this.player = this.minecraftServer.serverConfigurationManager.moveToWorld(this.player, 0);
                if (this.player != null) {
                    this.player.dead = false;
                    this.player.deathTicks = 0;
                    if (this.player.health <= 0) {
                        this.player.health = 20;
                    }
                }

                CraftPlayer craftPlayer = this.getPlayer();
                if (craftPlayer != null) {
                    craftPlayer.setHandle(this.player); // CraftBukkit
                }
            } catch (Throwable t) {
                a.log(java.util.logging.Level.SEVERE, "[Respawn] Failed to respawn player " + this.player.name + ". Disconnecting stale session.", t);
                this.disconnect("Respawn failed. Please reconnect.");
            }
        }
    }

    public void a(Packet101CloseWindow packet101closewindow) {
        PacketReceivedEvent event = new PacketReceivedEvent(server.getPlayer(player), packet101closewindow);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;


        if (this.player.dead) return; // CraftBukkit

        this.player.A();
    }

    public void a(Packet102WindowClick packet102windowclick) {
        // poseidon
        PacketReceivedEvent event = new PacketReceivedEvent(server.getPlayer(player), packet102windowclick);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (this.player.dead) return; // CraftBukkit

        if (this.player.activeContainer.windowId == packet102windowclick.a && this.player.activeContainer.c(this.player)) {
            if (this.player.activeContainer.isPositioned() && !Patches.CONTAINER_DISTANCE.check(this.player, this.player.activeContainer.getPosition())) {
                return;
            }

            InventoryShortcutEvent shortcutEvent = this.detectInventoryShortcut(packet102windowclick);
            if (shortcutEvent != null) {
                EventBus.global().publish(shortcutEvent);
                if (shortcutEvent.isCancelled()) {
                    this.clearInventoryShortcutPrime();
                    this.player.activeContainer.a();
                    this.player.z();
                    return;
                }
            }
            this.updateInventoryShortcutPrime(packet102windowclick, shortcutEvent != null);

            ItemStack itemstack = this.player.activeContainer.a(packet102windowclick.b, packet102windowclick.c, packet102windowclick.f, this.player);

            if (ItemStack.equals(packet102windowclick.e, itemstack)) {
                this.player.netServerHandler.sendPacket(new Packet106Transaction(packet102windowclick.a, packet102windowclick.d, true));
                this.player.h = true;
                this.player.activeContainer.a();
                this.player.z();
                this.player.h = false;
            } else {
                this.n.put(this.player.activeContainer.windowId, packet102windowclick.d);
                this.player.netServerHandler.sendPacket(new Packet106Transaction(packet102windowclick.a, packet102windowclick.d, false));
                this.player.activeContainer.a(this.player, false);
                ArrayList arraylist = new ArrayList();

                for (int i = 0; i < this.player.activeContainer.e.size(); ++i) {
                    arraylist.add(((Slot) this.player.activeContainer.e.get(i)).getItem());
                }

                this.player.a(this.player.activeContainer, arraylist);
            }
        }
    }

    private InventoryShortcutEvent detectInventoryShortcut(Packet102WindowClick packet102windowclick) {
        if (this.player == null || this.player.activeContainer == null) {
            this.clearInventoryShortcutPrime();
            return null;
        }
        if (this.primedInventorySlot < 0 || this.primedInventoryWindowId != packet102windowclick.a) {
            return null;
        }
        if (System.currentTimeMillis() - this.primedInventoryClickAt > INVENTORY_SHORTCUT_PRIME_MS) {
            this.clearInventoryShortcutPrime();
            return null;
        }

        InventoryShortcutEvent.Action action = null;
        int hotbarIndex = -1;

        if (packet102windowclick.b == -999 && (packet102windowclick.c == 0 || packet102windowclick.c == 1)) {
            action = packet102windowclick.c == 0
                ? InventoryShortcutEvent.Action.DROP_STACK
                : InventoryShortcutEvent.Action.DROP_SINGLE;
        } else if (!packet102windowclick.f && packet102windowclick.c == 0) {
            hotbarIndex = this.resolveHotbarIndex(packet102windowclick.b);
            if (hotbarIndex >= 0) {
                action = InventoryShortcutEvent.Action.HOTBAR_SWAP;
            }
        }

        if (action == null) {
            return null;
        }

        Slot hoveredSlot = null;
        if (this.primedInventorySlot >= 0 && this.primedInventorySlot < this.player.activeContainer.e.size()) {
            hoveredSlot = (Slot) this.player.activeContainer.e.get(this.primedInventorySlot);
        }

        return new InventoryShortcutEvent(
            this.player,
            this.player.activeContainer,
            hoveredSlot,
            action,
            -1,
            hotbarIndex,
            false,
            false
        );
    }

    private int resolveHotbarIndex(int slotNumber) {
        if (this.player == null || this.player.activeContainer == null) {
            return -1;
        }
        int slotCount = this.player.activeContainer.e.size();
        int hotbarStart = slotCount - 9;
        if (hotbarStart < 0) {
            return -1;
        }
        return slotNumber >= hotbarStart && slotNumber < slotCount ? (slotNumber - hotbarStart) : -1;
    }

    private void updateInventoryShortcutPrime(Packet102WindowClick packet102windowclick, boolean shortcutResolved) {
        if (shortcutResolved) {
            this.clearInventoryShortcutPrime();
            return;
        }

        if (packet102windowclick.b >= 0 && packet102windowclick.c == 0 && !packet102windowclick.f) {
            this.primedInventorySlot = packet102windowclick.b;
            this.primedInventoryWindowId = packet102windowclick.a;
            this.primedInventoryClickAt = System.currentTimeMillis();
            return;
        }

        if (packet102windowclick.b == -999 || packet102windowclick.c != 0 || packet102windowclick.f) {
            this.clearInventoryShortcutPrime();
        }
    }

    private void clearInventoryShortcutPrime() {
        this.primedInventorySlot = -1;
        this.primedInventoryWindowId = -1;
        this.primedInventoryClickAt = 0L;
    }

    public void a(Packet106Transaction packet106transaction) {
        // poseidon
        PacketReceivedEvent event = new PacketReceivedEvent(server.getPlayer(player), packet106transaction);
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (this.player.dead) return; // CraftBukkit

        Short oshort = (Short) this.n.get(this.player.activeContainer.windowId);

        if (oshort != null && packet106transaction.b == oshort && this.player.activeContainer.windowId == packet106transaction.a && !this.player.activeContainer.c(this.player)) {
            if (this.player.activeContainer.isPositioned() && !Patches.CONTAINER_DISTANCE.check(this.player, this.player.activeContainer.getPosition())) {
                return;
            }

            this.player.activeContainer.a(this.player, true);
        }
    }

    public void a(Packet130UpdateSign packet130updatesign) {
        // poseidon
        PacketReceivedEvent pevent = new PacketReceivedEvent(server.getPlayer(player), packet130updatesign);
        server.getPluginManager().callEvent(pevent);
        if (pevent.isCancelled()) return;

        if (this.player.dead) return; // CraftBukkit

        WorldServer worldserver = this.minecraftServer.getWorldServer(this.player.dimension);

        if (worldserver.isLoaded(packet130updatesign.x, packet130updatesign.y, packet130updatesign.z)) {
            TileEntity tileentity = worldserver.getTileEntity(packet130updatesign.x, packet130updatesign.y, packet130updatesign.z);

            if (tileentity instanceof TileEntitySign) {
                TileEntitySign tileentitysign = (TileEntitySign) tileentity;

                if (!tileentitysign.a()) {
                    this.minecraftServer.c("Player " + this.player.name + " just tried to change non-editable sign");
                    // CraftBukkit
                    this.sendPacket(new Packet130UpdateSign(packet130updatesign.x, packet130updatesign.y, packet130updatesign.z, tileentitysign.lines));
                    return;
                }
            }

            int i;
            int j;

            for (j = 0; j < 4; ++j) {
                boolean flag = true;

                if (packet130updatesign.lines[j].length() > 15) {
                    flag = false;
                } else {
                    for (i = 0; i < packet130updatesign.lines[j].length(); ++i) {
                        char c = packet130updatesign.lines[j].charAt(i);
                        // Allow the section sign (§) for color codes, and color code characters (0-9, a-f, k-o, r)
                        if (c == '\u00A7') {
                            // Color code prefix - allowed
                            continue;
                        }
                        if (i > 0 && packet130updatesign.lines[j].charAt(i - 1) == '\u00A7') {
                            // This is a color code character following § - allowed
                            continue;
                        }
                        if (!FontAllowedCharacters.isAllowedCharacter(c)) {
                            flag = false;
                        }
                    }
                }

                if (!flag) {
                    packet130updatesign.lines[j] = "!?";
                }
            }

            if (tileentity instanceof TileEntitySign) {
                j = packet130updatesign.x;
                int k = packet130updatesign.y;

                i = packet130updatesign.z;
                TileEntitySign tileentitysign1 = (TileEntitySign) tileentity;

                // CraftBukkit start
                Player player = this.server.getPlayer(this.player);
                SignChangeEvent event = new SignChangeEvent((CraftBlock) player.getWorld().getBlockAt(j, k, i), this.server.getPlayer(this.player), packet130updatesign.lines);
                this.server.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    for (int l = 0; l < 4; ++l) {
                        tileentitysign1.lines[l] = event.getLine(l);
                    }
                    tileentitysign1.a(false);
                }
                // CraftBukkit end

                tileentitysign1.update();
                try {
                    if (worldserver.chunkProvider instanceof ChunkProviderServer) {
                        ChunkProviderServer chunkproviderserver = (ChunkProviderServer) worldserver.chunkProvider;
                        Chunk chunk = worldserver.getChunkAtWorldCoords(j, i);

                        if (chunk != null) {
                            chunk.f();
                            chunkproviderserver.saveChunk(chunk);
                            worldserver.saveLevel();
                        }
                    }
                } catch (Exception exception) {
                    a.warning("Failed to persist sign at " + j + "," + k + "," + i + ": " + exception.getMessage());
                    exception.printStackTrace();
                }
                worldserver.notify(j, k, i);
            }
        }
    }

    public boolean c() {
        return true;
    }

    /**
     * Handle Packet131 map data from client.
     * Type 2 = lock request
     */
    @Override
    public void a(Packet131 packet131) {
        if (packet131.c != null && packet131.c.length >= 1) {
            // Type 2 = lock map request
            if (packet131.c[0] == 2) {
                int mapId = packet131.b; // Now supports int mapId for extended format
                WorldMap worldmap = (WorldMap) this.player.world.a(WorldMap.class, "map_" + mapId);
                if (worldmap != null && !worldmap.locked) {
                    worldmap.locked = true;
                    worldmap.a(); // Mark dirty to save
                }
            }
        }
    }
    
    /**
     * Handle custom payload packets (plugin channels).
     * Includes friends verification for online-mode servers.
     */
    @Override
    public void a(Packet250CustomPayload packet250custompayload) {
        if (packet250custompayload.channel == null) {
            return;
        }
        
        // MCOSE version check - must be received first
        if ("MCOSE_VERSION".equals(packet250custompayload.channel)) {
            handleVersionPacket(packet250custompayload);
            return;
        }

        if (ModProtocol.CHANNEL_HELLO.equals(packet250custompayload.channel)) {
            ModProtocol.HelloInfo helloInfo = ModProtocol.readHelloInfo(packet250custompayload.data);
            this.remoteModProtocolVersion = helloInfo.version;
            this.negotiatedModFeatures = 0;
            this.modProtocolNegotiated = false;

            if (ModProtocol.isSupportedVersion(helloInfo.version)) {
                this.modProtocolNegotiated = true;
                if (!ModProtocol.supportsFeatureBits(helloInfo.version)) {
                    this.disconnect("Protocol mismatch: modern entity wire (v2) is required.");
                    return;
                }

                if (!ModProtocol.hasRequiredEntityFeatures(helloInfo.featureBits)) {
                    this.disconnect("Protocol mismatch: modern entity wire (v2) is required.");
                    return;
                }

                int serverFeatures = ModProtocol.resolveServerSupportedFeatures();
                this.negotiatedModFeatures = helloInfo.featureBits & serverFeatures;
                int ackVersion = helloInfo.version == ModProtocol.PROTOCOL_VERSION_EXPERIMENTAL
                        ? ModProtocol.PROTOCOL_VERSION_EXPERIMENTAL
                        : ModProtocol.PROTOCOL_VERSION;
                this.sendPacket(new Packet250CustomPayload(
                        ModProtocol.CHANNEL_HELLO_ACK,
                        ModProtocol.createHelloAckPayload(ackVersion, this.negotiatedModFeatures)));
                this.sendSkinPartSnapshotToClient();
            }
            return;
        }

        if (ModProtocol.CHANNEL_REGISTRY_REQUEST.equals(packet250custompayload.channel)) {
            if (!this.modProtocolNegotiated) {
                return;
            }
            int requestVersion = ModProtocol.readRegistryRequestVersion(packet250custompayload.data);
            if (requestVersion != this.remoteModProtocolVersion) {
                return;
            }
            this.syncedRegistrySnapshot = RegistrySyncSnapshot.captureLocal();
            this.sendPacket(new Packet250CustomPayload(ModProtocol.CHANNEL_REGISTRY_SYNC, ModProtocol.createRegistrySyncPayload(this.syncedRegistrySnapshot)));
            return;
        }

        if (ModProtocol.CHANNEL_SKIN_PARTS.equals(packet250custompayload.channel)) {
            this.handleSkinPartsPacket(packet250custompayload);
            return;
        }

        if (CommandAutocompleteRegistry.CHANNEL_REQUEST.equals(packet250custompayload.channel)) {
            handleCommandAutocompleteRequest(packet250custompayload);
            return;
        }
        
        // Try friends verification handler first (for MCOSE|F* channels)
        if (packet250custompayload.channel.startsWith("MCOSE|F")) {
            if (minecraftServer.friendsVerificationHandler.handlePacket(this.player, packet250custompayload)) {
                return; // Handled
            }
        }
        
        // Handle book editing and signing
        if ("MC|BEdit".equals(packet250custompayload.channel)) {
            handleBookEdit(packet250custompayload);
            return;
        }
        
        if ("MC|BSign".equals(packet250custompayload.channel)) {
            handleBookSign(packet250custompayload);
            return;
        }
        
        // MCOSE: Handle sign dye coloring
        if ("MC|SignDye".equals(packet250custompayload.channel)) {
            handleSignDye(packet250custompayload);
            return;
        }
        
        // Handle other custom channels here if needed
        // (Voice chat, Herobrine events, etc. are handled by their own systems)
    }

    private void handleSkinPartsPacket(Packet250CustomPayload packet) {
        if (!this.supportsSkinPartSync()) {
            return;
        }
        if (this.player == null) {
            return;
        }

        ModProtocol.SkinPartsInfo skinPartsInfo = ModProtocol.readSkinPartsPayload(packet == null ? null : packet.data);
        int modelPartMask = skinPartsInfo.modelPartMask & 0x7F;
        this.player.setSkinModelPartMask(modelPartMask);
        this.broadcastSkinPartMask(this.player.name, modelPartMask);
    }

    private void sendSkinPartSnapshotToClient() {
        if (!this.supportsSkinPartSync()) {
            return;
        }
        if (this.minecraftServer == null || this.minecraftServer.serverConfigurationManager == null) {
            return;
        }

        java.util.List<EntityPlayer> online = this.minecraftServer.serverConfigurationManager.getOnlinePlayersSnapshot();
        for (int i = 0; i < online.size(); ++i) {
            EntityPlayer onlinePlayer = online.get(i);
            if (onlinePlayer == null) {
                continue;
            }
            this.sendPacket(new Packet250CustomPayload(
                    ModProtocol.CHANNEL_SKIN_PARTS,
                    ModProtocol.createSkinPartsPayload(onlinePlayer.name, onlinePlayer.getSkinModelPartMask())));
        }
    }

    private void broadcastSkinPartMask(String username, int modelPartMask) {
        if (this.minecraftServer == null || this.minecraftServer.serverConfigurationManager == null) {
            return;
        }

        java.util.List<EntityPlayer> online = this.minecraftServer.serverConfigurationManager.getOnlinePlayersSnapshot();
        for (int i = 0; i < online.size(); ++i) {
            EntityPlayer recipient = online.get(i);
            if (recipient == null || recipient.netServerHandler == null || !recipient.netServerHandler.supportsSkinPartSync()) {
                continue;
            }
            recipient.netServerHandler.sendPacket(new Packet250CustomPayload(
                    ModProtocol.CHANNEL_SKIN_PARTS,
                    ModProtocol.createSkinPartsPayload(username, modelPartMask)));
        }
    }
    
    /**
     * Handle MCOSE version packet from client.
     * Kicks players with outdated client versions.
     */
    private void handleVersionPacket(Packet250CustomPayload packet) {
        if (receivedVersionPacket) {
            return; // Already handled
        }
        receivedVersionPacket = true;
        String minimumVersion = UberbukkitConfig.getInstance().getString("client.minimum_version.value", ModVersion.getMajorMinor(ModVersion.VERSION));
        if (minimumVersion == null || minimumVersion.trim().isEmpty()) {
            minimumVersion = ModVersion.getMajorMinor(ModVersion.VERSION);
        } else {
            minimumVersion = minimumVersion.trim();
        }
        
        try {
            if (packet.data == null || packet.data.length == 0) {
                // No version data - treat as outdated
                this.disconnect("Outdated client! Please update to " + ModVersion.getMajorMinor(minimumVersion) + " or newer");
                return;
            }
            
            // Read version string from packet
            java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(packet.data));
            this.clientVersion = dis.readUTF();
            
            a.info("[MCOSE] " + this.player.name + " connected with client version " + this.clientVersion);
            
            // Check version compatibility
            if (!ModVersion.isCompatible(this.clientVersion, minimumVersion)) {
                this.disconnect(ModVersion.getOutdatedMessage(this.clientVersion, minimumVersion));
                return;
            }

            sendCommandAutocompleteTree();
            
        } catch (Exception e) {
            a.warning("[MCOSE] Error reading version packet: " + e.getMessage());
            this.disconnect("Outdated client! Please update to " + ModVersion.getMajorMinor(minimumVersion) + " or newer");
        }
    }

    private void sendCommandAutocompleteTree() {
        try {
            if (!(this.server.getCommandMap() instanceof org.bukkit.command.SimpleCommandMap)) {
                return;
            }

            org.bukkit.command.SimpleCommandMap commandMap = (org.bukkit.command.SimpleCommandMap) this.server.getCommandMap();
            byte[] payload = CommandAutocompleteRegistry.getInstance().buildTreePayload(commandMap, this.getPlayer());
            if (payload == null || payload.length == 0) {
                return;
            }

            this.sendPacket(new Packet250CustomPayload(CommandAutocompleteRegistry.CHANNEL_TREE, payload));
        } catch (Throwable t) {
            a.warning("[CommandAutocomplete] Failed to send command tree: " + t.getMessage());
        }
    }

    public void refreshCommandAutocompleteTree() {
        sendCommandAutocompleteTree();
    }


    private void handleCommandAutocompleteRequest(Packet250CustomPayload packet) {
        if (packet == null || packet.data == null || packet.data.length == 0) {
            return;
        }

        java.io.DataInputStream in = null;
        try {
            in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(packet.data));
            int protocol = in.readInt();
            if (protocol != CommandAutocompleteRegistry.PROTOCOL_VERSION) {
                return;
            }

            int requestId = in.readInt();
            int cursorPos = in.readInt();
            String text = in.readUTF();
            if (text == null) {
                text = "";
            }

            if (!(this.server.getCommandMap() instanceof org.bukkit.command.SimpleCommandMap)) {
                return;
            }

            org.bukkit.command.SimpleCommandMap commandMap = (org.bukkit.command.SimpleCommandMap) this.server.getCommandMap();
            java.util.List<String> suggestions = CommandAutocompleteRegistry.getInstance().suggest(commandMap, this.getPlayer(), text, cursorPos);

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            java.io.DataOutputStream out = new java.io.DataOutputStream(baos);
            out.writeInt(CommandAutocompleteRegistry.PROTOCOL_VERSION);
            out.writeInt(requestId);
            out.writeInt(cursorPos);
            out.writeUTF(text);
            out.writeInt(suggestions.size());
            for (int i = 0; i < suggestions.size(); i++) {
                String value = suggestions.get(i);
                out.writeUTF(value == null ? "" : value);
            }

            this.sendPacket(new Packet250CustomPayload(CommandAutocompleteRegistry.CHANNEL_RESPONSE, baos.toByteArray()));
        } catch (Throwable t) {
            a.warning("[CommandAutocomplete] Failed to handle request: " + t.getMessage());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (java.io.IOException ignored) {}
            }
        }
    }
    
    /**
     * Check if the client has sent version packet within grace period.
     * Called from tick/update loop.
     */
    public void checkVersionTimeout() {
        // Skip if already received or if it's a vanilla client (no MCOSE features)
        if (receivedVersionPacket) {
            return;
        }
        
        long elapsed = System.currentTimeMillis() - connectionStartTime;
        if (elapsed > VERSION_CHECK_GRACE_PERIOD_MS) {
            // Grace period expired - this is likely a vanilla client
            // Mark as received to stop checking, but with null version
            receivedVersionPacket = true;
            this.clientVersion = "vanilla";
            a.info("[MCOSE] " + this.player.name + " did not send version packet - assuming vanilla client");
            // Don't kick vanilla clients - they just won't have MCOSE features
        }
    }
    
    /**
     * Get the client's MCOSE version, or null if unknown/vanilla.
     */
    public String getClientVersion() {
        return this.clientVersion;
    }
    
    /**
     * Check if this client is a confirmed MCOSE client.
     */
    public boolean isMcoseClient() {
        return this.clientVersion != null && !"vanilla".equals(this.clientVersion);
    }
    
    /**
     * Handle book edit packet (MC|BEdit).
     * Updates the pages of the book in the player's hand.
     */
    private void handleBookEdit(Packet250CustomPayload packet) {
        try {
            if (packet.data == null || packet.data.length == 0) {
                return;
            }
            
            ItemStack heldItem = this.player.inventory.getItemInHand();
            if (heldItem == null || heldItem.id != Item.WRITABLE_BOOK.id) {
                return;
            }
            
            // Read NBT data from packet
            java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(packet.data));
            NBTBase nbt = NBTBase.b(dis);
            
            if (nbt instanceof NBTTagCompound) {
                NBTTagCompound bookData = (NBTTagCompound) nbt;
                
                // Validate and apply the book data
                if (bookData.hasKey("pages")) {
                    NBTTagList pages = bookData.l("pages");
                    
                    // Limit page count and content length
                    if (pages.c() <= 50) {
                        // Set or create the tag on the item
                        if (heldItem.tag == null) {
                            heldItem.tag = new NBTTagCompound();
                        }
                        heldItem.tag.a("pages", pages);
                        
                        // Mark inventory as dirty for persistence
                        this.player.inventory.update();
                        
                        // Sync inventory back to client so they see the saved book
                        this.player.updateInventory(this.player.activeContainer);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[NetServerHandler] Error handling book edit: " + e.getMessage());
        }
    }
    
    /**
     * Handle book sign packet (MC|BSign).
     * Converts a writable book to a written book with author and title.
     */
    private void handleBookSign(Packet250CustomPayload packet) {
        try {
            if (packet.data == null || packet.data.length == 0) {
                return;
            }
            
            ItemStack heldItem = this.player.inventory.getItemInHand();
            if (heldItem == null || heldItem.id != Item.WRITABLE_BOOK.id) {
                return;
            }
            
            // Read NBT data from packet
            java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(packet.data));
            NBTBase nbt = NBTBase.b(dis);
            
            if (nbt instanceof NBTTagCompound) {
                NBTTagCompound bookData = (NBTTagCompound) nbt;
                
                // Validate the book data
                if (bookData.hasKey("pages") && bookData.hasKey("title") && bookData.hasKey("author")) {
                    String title = bookData.getString("title");
                    String author = bookData.getString("author");
                    NBTTagList pages = bookData.l("pages");
                    
                    // Validate title length
                    if (title.length() > 16) {
                        title = title.substring(0, 16);
                    }
                    
                    // Validate author (should match player name)
                    if (!author.equals(this.player.name)) {
                        author = this.player.name;
                    }
                    
                    // Limit page count
                    if (pages.c() <= 50) {
                        // Convert to written book
                        heldItem.id = Item.WRITTEN_BOOK.id;
                        
                        // Set or create the tag on the item
                        if (heldItem.tag == null) {
                            heldItem.tag = new NBTTagCompound();
                        }
                        heldItem.tag.a("pages", pages);
                        heldItem.tag.setString("title", title);
                        heldItem.tag.setString("author", author);
                        
                        // Mark inventory as dirty for persistence
                        this.player.inventory.update();
                        
                        // Sync inventory back to client so they see the signed book
                        this.player.updateInventory(this.player.activeContainer);
                        
                        a.info("[MCOSE] Player " + this.player.name + " signed book: \"" + title + "\"");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[NetServerHandler] Error handling book sign: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle sign dye packet (MC|SignDye).
     * Changes the text color of a sign when right-clicked with dye.
     */
    private void handleSignDye(Packet250CustomPayload packet) {
        try {
            if (packet.data == null || packet.data.length < 13) { // 4+4+4+1 bytes minimum
                return;
            }
            
            java.io.DataInputStream dis = new java.io.DataInputStream(
                new java.io.ByteArrayInputStream(packet.data));
            int x = dis.readInt();
            int y = dis.readInt();
            int z = dis.readInt();
            int dyeDamage = dis.readByte() & 0xFF;
            
            // Validate dye damage value
            if (dyeDamage > 15) {
                return;
            }
            
            // Check distance (anti-cheat)
            double dx = x - this.player.locX;
            double dy = y - this.player.locY;
            double dz = z - this.player.locZ;
            if (dx * dx + dy * dy + dz * dz > 64) { // Max 8 blocks
                return;
            }
            
            // Verify player is holding the correct dye
            ItemStack heldItem = this.player.inventory.getItemInHand();
            if (heldItem == null || heldItem.id != Item.INK_SACK.id || heldItem.getData() != dyeDamage) {
                return; // Invalid - player isn't holding the right dye
            }
            
            // Get the sign tile entity
            World world = this.player.world;
            TileEntity te = world.getTileEntity(x, y, z);
            if (te instanceof TileEntitySign) {
                TileEntitySign sign = (TileEntitySign) te;
                sign.setColorFromDye(dyeDamage);
                
                // Consume one dye from the player's hand (server-authoritative)
                if (this.player.gameMode != 1) { // Don't consume in creative mode
                    heldItem.count--;
                    if (heldItem.count <= 0) {
                        this.player.inventory.items[this.player.inventory.itemInHandIndex] = null;
                    }
                    // Sync inventory slot to client
                    this.player.updateInventory(this.player.defaultContainer);
                }
                
                // Broadcast the color update via custom payload to all players in range
                // Format: x (int), y (int), z (int), color (int)
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
                dos.writeInt(x);
                dos.writeInt(y);
                dos.writeInt(z);
                dos.writeInt(sign.textColor);
                Packet250CustomPayload colorPacket = new Packet250CustomPayload("MC|SignCol", baos.toByteArray());
                this.minecraftServer.serverConfigurationManager.sendPacketNearby(
                    x, y, z, 64.0, this.player.dimension, colorPacket);
            }
        } catch (Exception e) {
            System.err.println("[NetServerHandler] Error handling sign dye: " + e.getMessage());
        }
    }
    
    /**
     * Handle tab completion requests from the client.
     * Parses the partial command and returns possible completions.
     */
    @Override
    public void a(Packet203TabComplete packet203tabcomplete) {
        if (packet203tabcomplete.text == null || packet203tabcomplete.text.isEmpty()) {
            return;
        }

        if (!(this.server.getCommandMap() instanceof org.bukkit.command.SimpleCommandMap)) {
            return;
        }

        org.bukkit.command.SimpleCommandMap commandMap = (org.bukkit.command.SimpleCommandMap) this.server.getCommandMap();
        java.util.List<String> completions = CommandAutocompleteRegistry.getInstance()
            .suggest(commandMap, this.getPlayer(), packet203tabcomplete.text, packet203tabcomplete.text.length());
        String[] responseArray = completions.toArray(new String[0]);
        this.sendPacket(new Packet203TabComplete(responseArray));
    }

    private static final class QueuedTcpVoicePacket {
        private final Packet64Voice packet;
        private final boolean routeToRoom;
        private final boolean parallelLane;
        private final long enqueueNanos;

        private QueuedTcpVoicePacket(Packet64Voice packet, boolean routeToRoom, boolean parallelLane, long enqueueNanos) {
            this.packet = packet;
            this.routeToRoom = routeToRoom;
            this.parallelLane = parallelLane;
            this.enqueueNanos = enqueueNanos;
        }
    }
}

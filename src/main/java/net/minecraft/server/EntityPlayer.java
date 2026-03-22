package net.minecraft.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.ChunkCompressionThread;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.inventory.ChestOpenedEvent;

import com.legacyminecraft.poseidon.PoseidonConfig;
import com.legacyminecraft.poseidon.event.PlayerDeathEvent;
import com.projectposeidon.api.PoseidonUUID;

import me.devcody.uberbukkit.math.Vec3i;
import uk.betacraft.uberbukkit.UberbukkitConfig;
import uk.betacraft.uberbukkit.alpha.inventory.ProcessPacket5;
import uk.betacraft.uberbukkit.packet.Packet62Sound;
import net.minecraft.server.registry.PlayerCapabilityRegistryApi;
import uk.betacraft.uberbukkit.protocol.Protocol;

// CraftBukkit start

public class EntityPlayer extends EntityHuman implements ICrafting {
    private static final int BOW_POSE_DURATION_TICKS = 10;

    public NetServerHandler netServerHandler;
    public MinecraftServer b;
    public ItemInWorldManager itemInWorldManager;
    public double d;
    public double e;
    public List chunkCoordIntPairQueue = new LinkedList();
    public Set playerChunkCoordIntPairs = new HashSet();
    public final List removeQueue = new LinkedList(); // poseidon
    private int lastChunkStreamTick = Integer.MIN_VALUE;
    private int bL = -99999999;
    private int bM = 60;
    private ItemStack[] bN = new ItemStack[] { null, null, null, null, null };
    private boolean lastSentBowPose = false;
    private int bowPoseTicks = 0;
    private int bO = 0;
    public boolean h;
    // uberbukkit
    public Protocol protocol;
    public ProcessPacket5 packet5;
    public boolean isInWorkbench = false; // uberbukkit pvn < 7
    private int skinModelPartMask = 0x7F;

    public EntityPlayer(MinecraftServer minecraftserver, World world, String s, ItemInWorldManager iteminworldmanager, int pvn) {
        super(world);
        this.packet5 = new ProcessPacket5(this); // uberbukkit
        this.protocol = Protocol.getProtocolClass(pvn); // uberbukkit
        iteminworldmanager.player = this;
        this.itemInWorldManager = iteminworldmanager;
        ChunkCoordinates chunkcoordinates = world.getSpawn();
        int i = chunkcoordinates.x;
        int j = chunkcoordinates.z;
        int searchRadius = 64;
        boolean preferShorelineSpawn = world.worldData != null &&
            (world.worldData.getTerrainType() == 0 || world.worldData.getTerrainType() == 1);

        if (!world.worldProvider.e) {
            if ((boolean) PoseidonConfig.getInstance().getProperty("world-settings.randomize-spawn")) { //Project Poseidon: Moved randomizing X and Y axis into a config option
                i += this.random.nextInt(20) - 10;
                j += this.random.nextInt(20) - 10;
                searchRadius = 80;
            }
        }

        ChunkCoordinates safeSpawn = world.findSafeSpawnNear(i, j, searchRadius, preferShorelineSpawn);
        this.setPositionRotation((double) safeSpawn.x + 0.5D, (double) safeSpawn.y + 0.01D, (double) safeSpawn.z + 0.5D, 0.0F, 0.0F);
        this.b = minecraftserver;
        this.bs = 0.0F;
        this.name = s;
        this.height = 0.0F;

        // CraftBukkit start
        this.displayName = this.name;
        this.playerUUID = PoseidonUUID.getPlayerGracefulUUID(this.name); //Project Poseidon
        
        // UberBukkit - Initialize server-side statistics and achievements tracking
        this.playerStatistics = new PlayerStatistics(this);
        this.achievementManager = new AchievementManager(this);
    }

    public String displayName;
    public UUID playerUUID; //Project Poseidon
    public org.bukkit.Location compassTarget;

    public int getSkinModelPartMask() {
        return this.skinModelPartMask & 0x7F;
    }

    public void setSkinModelPartMask(int modelPartMask) {
        this.skinModelPartMask = modelPartMask & 0x7F;
    }
    
    /**
     * Get the Mojang UUID for this player.
     * @return The player's UUID
     */
    public UUID getMojangUUID() {
        return this.playerUUID;
    }
    // CraftBukkit end
    
    // UberBukkit - Server-side statistics and achievements tracking
    public PlayerStatistics playerStatistics;
    public AchievementManager achievementManager;
    // Project Poseidon - Update container for creative/survival mode switch
    public void updateContainer() {
        this.defaultContainer = new ContainerPlayer(this.inventory, !this.world.isStatic);
        this.activeContainer = this.defaultContainer;
        if (this.netServerHandler != null) {
            try {
                this.activeContainer.a((ICrafting) this);
            } catch (IllegalArgumentException already) {
                // ignore duplicate listener during edge cases
            }
        }
    }

    public void setGameMode(int gameMode) {
        int previous = this.gameMode;
        this.gameMode = gameMode;
        if (previous != gameMode) {
            PlayerCapabilityRegistryApi.handleGameModeUpdate(this, previous, gameMode);
        }
        this.updateContainer();
    }

    public void spawnIn(World world) {
        super.spawnIn(world);
        // CraftBukkit - world fallback code, either respawn location or global spawn
        if (world == null) {
            this.dead = false;
            ChunkCoordinates position = null;
            if (this.spawnWorld != null && !this.spawnWorld.equals("")) {
                CraftWorld cworld = (CraftWorld) Bukkit.getServer().getWorld(this.spawnWorld);
                if (cworld != null && this.getBed() != null) {
                    world = cworld.getHandle();
                    position = EntityHuman.getBed(cworld.getHandle(), this.getBed());
                }
            }
            if (world == null || position == null) {
                world = ((CraftWorld) Bukkit.getServer().getWorlds().get(0)).getHandle();
                position = world.getSpawn();
            }
            this.world = world;
            this.setPosition(position.x + 0.5, position.y, position.z + 0.5);
        }
        this.dimension = ((WorldServer) this.world).dimension;
        // CraftBukkit end
        this.itemInWorldManager = new ItemInWorldManager((WorldServer) world);
        this.itemInWorldManager.player = this;
    }

    public void syncInventory() {
        if (this.netServerHandler.networkManager.pvn <= 6) {
            this.netServerHandler.refreshInventory();
        }
        try {
            this.activeContainer.a((ICrafting) this);
        } catch (IllegalArgumentException already) {
            // Listener already registered; ignore to avoid duplicate registration during login
        }
    }

    public ItemStack[] getEquipment() {
        return this.bN;
    }

    protected void s() {
        this.height = 0.0F;
    }

    public float t() {
        return 1.62F;
    }

    public void m_() {
        this.itemInWorldManager.a();
        --this.bM;
        if (this.bowPoseTicks > 0) {
            --this.bowPoseTicks;
        }
        this.activeContainer.a();

        for (int i = 0; i < 5; ++i) {
            ItemStack itemstack = this.c_(i);

            if (itemstack != this.bN[i]) {
                this.b.getTracker(this.dimension).a(this, new Packet5EntityEquipment(this.id, i, itemstack));
                this.bN[i] = itemstack;
            }
        }

        if (!this.world.isStatic) {
            boolean bowPose = this.isBowPoseActive();
            if (bowPose != this.lastSentBowPose) {
                this.world.a(this, (byte) (bowPose ? 16 : 17)); // Custom statuses: player bow pose on/off
                this.lastSentBowPose = bowPose;
            }
        }
    }

    public ItemStack c_(int i) {
        return i == 0 ? this.inventory.getItemInHand() : this.inventory.armor[i - 1];
    }

    public boolean isBowPoseActive() {
        return this.bowPoseTicks > 0;
    }

    public void triggerBowPose() {
        this.bowPoseTicks = BOW_POSE_DURATION_TICKS;
    }

    public void die(Entity entity) {
        // Send death status to trigger tilt on all clients before any other side effects
        if (!this.world.isStatic) {
            this.world.a(this, (byte) 3);
        }
        // CraftBukkit start
        java.util.List<org.bukkit.inventory.ItemStack> loot = new java.util.ArrayList<org.bukkit.inventory.ItemStack>();

        for (int i = 0; i < this.inventory.items.length; ++i) {
            if (this.inventory.items[i] != null) {
                loot.add(new CraftItemStack(this.inventory.items[i]));
            }
        }

        for (int i = 0; i < this.inventory.armor.length; ++i) {
            if (this.inventory.armor[i] != null) {
                loot.add(new CraftItemStack(this.inventory.armor[i]));
            }
        }

        // uberbukkit
        ArrayList<ItemStack> queue = this.packet5.queue.dropAllQueue();
        for (ItemStack item : queue) {
            loot.add(new CraftItemStack(item));
        }

        org.bukkit.entity.Entity bukkitEntity = this.getBukkitEntity();
        CraftWorld bworld = this.world.getWorld();

        PlayerDeathEvent event = new PlayerDeathEvent(bukkitEntity, loot);
        
        // UberBukkit - Set keepInventory from gamerule (plugins can still override)
        if (this.world.worldData != null && this.world.worldData.getKeepInventory()) {
            event.setKeepInventory(true);
            event.getDrops().clear(); // Don't drop items if keepInventory is enabled
        }
        
        // UberBukkit - Generate death message like modern Minecraft
        // Check both config option and gamerule - both must be enabled
        boolean configEnabled = PoseidonConfig.getInstance().getConfigBoolean("settings.death-messages.enabled", true);
        boolean gameruleEnabled = this.world.worldData != null ? this.world.worldData.getShowDeathMessages() : true;
        if (configEnabled && gameruleEnabled) {
            String deathMessage = DeathMessageHelper.getDeathMessage(this, entity);
            event.setDeathMessage(deathMessage);
        }
        
        this.world.getServer().getPluginManager().callEvent(event);

        if (event.getDeathMessage() != null && !event.getDeathMessage().trim().isEmpty()) {
            this.b.serverConfigurationManager.sendAll(new Packet3Chat(event.getDeathMessage()));
        }

        // CraftBukkit - we clean the player's inventory after the EntityDeathEvent is called so plugins can get the exact state of the inventory.

        //Poseidon - Only clear inventory if keep inventory is false
        if (!event.getKeepInventory()) {
            for (int i = 0; i < this.inventory.items.length; ++i) {
                this.inventory.items[i] = null;
            }

            for (int i = 0; i < this.inventory.armor.length; ++i) {
                this.inventory.armor[i] = null;
            }
        }

        // Only drop items if keepInventory is false
        // Double-check by clearing drops if keepInventory is true (prevents plugins from adding items back)
        if (event.getKeepInventory()) {
            event.getDrops().clear();
        } else {
            for (org.bukkit.inventory.ItemStack stack : event.getDrops()) {
                bworld.dropItemNaturally(bukkitEntity.getLocation(), stack);
            }
        }

        this.y();

        // Hardcore mode: ban player on death
        if (this.isHardcoreMode()) {
            String banMessage = PoseidonConfig.getInstance().getConfigString("world-settings.hardcore.ban-message");
            String kickMessage = PoseidonConfig.getInstance().getConfigString("world-settings.hardcore.death-kick-message");
            // Ban the player
            this.b.serverConfigurationManager.a(this.name);
            // Kick with the hardcore death message
            if (this.netServerHandler != null) {
                this.netServerHandler.disconnect(kickMessage != null ? kickMessage : "You died in hardcore mode!");
            }
        }
        // CraftBukkit end
    }

    /**
     * Check if hardcore mode is enabled for this player.
     * Player's personal gameMode takes priority - if they're in creative (1) or survival (0),
     * they are NOT in hardcore mode, even if the server default is hardcore.
     * Only gameMode 2 means hardcore for the player.
     */
    public boolean isHardcoreMode() {
        // Player's personal gameMode is the primary check
        // gameMode 2 = hardcore, 1 = creative, 0 = survival
        // If player is in survival or creative, they are NOT hardcore
        // (their mode was explicitly set via /gamemode or from saved data)
        return this.gameMode == 2;
    }

    public boolean damageEntity(Entity entity, int i) {
        if (this.bM > 0) {
            return false;
        } else {
            // CraftBukkit - this.b.pvpMode -> this.world.pvpMode
            if (!this.world.pvpMode) {
                if (entity instanceof EntityHuman) {
                    return false;
                }

                if (entity instanceof EntityArrow) {
                    EntityArrow entityarrow = (EntityArrow) entity;

                    if (entityarrow.shooter instanceof EntityHuman) {
                        return false;
                    }
                }
            }

            return super.damageEntity(entity, i);
        }
    }

    protected boolean j_() {
        return this.b.pvpMode;
    }

    public void b(int i) {
        super.b(i, RegainReason.EATING);
    }

    public WorldServer getWorldServer() {
        return (WorldServer) this.world;
    }

    public void a(boolean flag) {
        super.m_();

        // Poseidon start
        while (!this.removeQueue.isEmpty()) {
            int i = Math.min(this.removeQueue.size(), 127);
            int[] aint = new int[i];
            Iterator iterator = this.removeQueue.iterator();
            int j = 0;

            while (iterator.hasNext() && j < i) {
                aint[j++] = ((Integer) iterator.next()).intValue();
                iterator.remove();
            }

            for (int k = 0; k < aint.length; k++) { // cant use array since not supported in b1.7.3
                this.netServerHandler.sendPacket(new Packet29DestroyEntity(aint[k]));
            }
        }
        // poseidon end

        for (int i = 0; i < this.inventory.getSize(); ++i) {
            ItemStack itemstack = this.inventory.getItem(i);

            if (itemstack != null && Item.byId[itemstack.id].b() && this.netServerHandler.b() <= 2) {
                Packet packet = ((ItemWorldMapBase) Item.byId[itemstack.id]).b(itemstack, this.world, this);

                if (packet != null) {
                    this.netServerHandler.sendPacket(packet);
                }
            }
        }

        // Poseidon start
        if (flag && !this.chunkCoordIntPairQueue.isEmpty() && this.lastChunkStreamTick != MinecraftServer.currentTick) {
            this.lastChunkStreamTick = MinecraftServer.currentTick;
            if (PoseidonConfig.getInstance().getBoolean("settings.faster-packets.enabled", true)) {
                ArrayList arraylist = new ArrayList();
                ArrayList arraylist1 = new ArrayList();
                int pendingChunkPackets = this.netServerHandler.getQueuedPacketCount() + ChunkCompressionThread.getPlayerQueueSize(this);
                int totalCompressionQueue = ChunkCompressionThread.getTotalQueueSize();
                int maxChunksThisTick = 16;
                int serverAgeTicks = this.b != null ? this.b.ticks : Integer.MAX_VALUE;
                boolean startupTrafficGuard = serverAgeTicks >= 0 && serverAgeTicks < 1200;

                // Keep chunk stream moving even under backlog so fast flight does not leave stripe-like gaps.
                if (pendingChunkPackets > 220) {
                    maxChunksThisTick = 3;
                } else if (pendingChunkPackets > 160) {
                    maxChunksThisTick = 5;
                } else if (pendingChunkPackets > 110) {
                    maxChunksThisTick = 7;
                } else if (pendingChunkPackets > 70) {
                    maxChunksThisTick = 10;
                } else if (pendingChunkPackets > 36) {
                    maxChunksThisTick = 13;
                }

                double horizontalSpeedSq = this.motX * this.motX + this.motZ * this.motZ;
                if (horizontalSpeedSq > 0.12D * 0.12D) {
                    maxChunksThisTick += 6;
                } else if (horizontalSpeedSq > 0.06D * 0.06D) {
                    maxChunksThisTick += 3;
                }
                if (maxChunksThisTick > 24) {
                    maxChunksThisTick = 24;
                }

                if (startupTrafficGuard) {
                    // During the first minute after boot, ramp chunk output gradually to keep interaction packets responsive.
                    int startupCap;
                    if (serverAgeTicks < 200) {
                        startupCap = 4;
                    } else if (serverAgeTicks < 400) {
                        startupCap = 6;
                    } else if (serverAgeTicks < 800) {
                        startupCap = 8;
                    } else {
                        startupCap = 10;
                    }

                    if (pendingChunkPackets > 180) {
                        startupCap = Math.min(startupCap, 2);
                    } else if (pendingChunkPackets > 120) {
                        startupCap = Math.min(startupCap, 4);
                    } else if (pendingChunkPackets > 80) {
                        startupCap = Math.min(startupCap, 6);
                    }

                    maxChunksThisTick = Math.min(maxChunksThisTick, startupCap);
                }

                if (ChunkCompressionThread.isAboveHighWatermark()) {
                    maxChunksThisTick = Math.min(maxChunksThisTick, 2);
                } else if (ChunkCompressionThread.isAboveLowWatermark()) {
                    maxChunksThisTick = Math.min(maxChunksThisTick, 6);
                }
                if (totalCompressionQueue > (ChunkCompressionThread.getTotalQueueCapacity() * 9) / 10) {
                    maxChunksThisTick = Math.min(maxChunksThisTick, 1);
                }

                LinkedList queue = this.chunkCoordIntPairQueue instanceof LinkedList ?
                    (LinkedList) this.chunkCoordIntPairQueue :
                    new LinkedList(this.chunkCoordIntPairQueue);
                if (queue != this.chunkCoordIntPairQueue) {
                    this.chunkCoordIntPairQueue = queue;
                }

                int attempts = Math.min(queue.size(), maxChunksThisTick * 4);
                while (attempts-- > 0 && arraylist.size() < maxChunksThisTick && !queue.isEmpty()) {
                    ChunkCoordIntPair chunkcoordintpair = (ChunkCoordIntPair) queue.getFirst();

                    if (chunkcoordintpair == null) {
                        queue.removeFirst();
                        continue;
                    }

                    if (!this.world.isLoaded(chunkcoordintpair.x << 4, 0, chunkcoordintpair.z << 4)) {
                        // If not yet loaded, push back to the end of the queue instead of dropping it
                        queue.removeFirst();
                        queue.addLast(chunkcoordintpair);
                        continue;
                    }

                    if (!ChunkCompressionThread.canAcceptChunk(this)) {
                        // Compression path saturated; defer remaining chunk sends for this tick.
                        break;
                    }

                    queue.removeFirst();

                    // CraftBukkit start - Get tile entities directly from the chunk instead of the world
                    Chunk chunk = this.world.getChunkAt(chunkcoordintpair.x, chunkcoordintpair.z);
                    arraylist.add(chunk);
                    arraylist1.addAll(chunk.tileEntities.values());
                    // CraftBukkit end
                }

                if (!arraylist.isEmpty()) {
                    Iterator iterator2 = arraylist.iterator();

                    while (iterator2.hasNext()) {
                        Chunk chunk = (Chunk) iterator2.next();

                        // PreChunk is already sent by PlayerInstance when the player starts watching this chunk.
                        this.netServerHandler.sendPacket(new Packet51MapChunk(chunk.x * 16, 0, chunk.z * 16, 16, 128, 16, this.getWorldServer()));
                        this.getWorldServer().tracker.a(this, chunk);
                    }

                    iterator2 = arraylist1.iterator();

                    while (iterator2.hasNext()) {
                        TileEntity tileentity = (TileEntity) iterator2.next();

                        this.a(tileentity);
                    }
                }
            } else {
                ChunkCoordIntPair chunkcoordintpair = (ChunkCoordIntPair) this.chunkCoordIntPairQueue.get(0);

                if (chunkcoordintpair != null) {
                    boolean flag1 = false;

                    if (this.netServerHandler.getQueuedPacketCount() + ChunkCompressionThread.getPlayerQueueSize(this) < 6
                        && ChunkCompressionThread.canAcceptChunk(this)) { // CraftBukkit - Add check against Chunk Packets in the ChunkCompressionThread.
                        flag1 = true;
                    }

                    if (flag1) {
                        WorldServer worldserver = this.b.getWorldServer(this.dimension);

                        this.chunkCoordIntPairQueue.remove(chunkcoordintpair);
                        this.netServerHandler.sendPacket(new Packet51MapChunk(chunkcoordintpair.x * 16, 0, chunkcoordintpair.z * 16, 16, 128, 16, worldserver));

                        Chunk chunk = this.world.getChunkAt(chunkcoordintpair.x, chunkcoordintpair.z);
                        this.getWorldServer().tracker.a(this, chunk);

                        List list = worldserver.getTileEntities(chunkcoordintpair.x * 16, 0, chunkcoordintpair.z * 16, chunkcoordintpair.x * 16 + 16, 128, chunkcoordintpair.z * 16 + 16);

                        for (int j = 0; j < list.size(); ++j) {
                            this.a((TileEntity) list.get(j));
                        }
                    }
                }
            }
        }
        // Poseidon end

        if (this.E) {
            //if (this.b.propertyManager.getBoolean("allow-nether", true)) { // CraftBukkit
            if (this.activeContainer != this.defaultContainer) {
                this.y();
            }

            if (this.vehicle != null) {
                this.mount(this.vehicle);
            } else {
                // uberbukkit - play portal sound
                if (this.F == 0.0F) {
                    String legacy = net.minecraft.server.registry.SoundEventResolver.resolve("minecraft:block.portal.trigger");
                    ((WorldServer) this.world).server.serverConfigurationManager.sendPacketNearby(this, this.locX, this.locY, this.locZ, 64D, this.dimension, new Packet62Sound(legacy, this.locX, this.locY, this.locZ, 1.0F, this.random.nextFloat() * 0.4F + 0.8F));
                }

                this.F += 0.0125F;
                if (this.F >= 1.0F) {
                    this.F = 1.0F;
                    this.D = 10;

                    // uberbukkit
                    String legacy2 = net.minecraft.server.registry.SoundEventResolver.resolve("minecraft:block.portal.travel");
                    ((WorldServer) this.world).server.serverConfigurationManager.sendPacketNearby(this, this.locX, this.locY, this.locZ, 64D, this.dimension, new Packet62Sound(legacy2, this.locX, this.locY, this.locZ, 1.0F, this.random.nextFloat() * 0.4F + 0.8F));

                    this.b.serverConfigurationManager.f(this);
                }
            }

            this.E = false;
            //} // CraftBukkit
        } else {
            if (this.F > 0.0F) {
                this.F -= 0.05F;
            }

            if (this.F < 0.0F) {
                this.F = 0.0F;
            }
        }

        if (this.D > 0) {
            --this.D;
        }

        if (this.health != this.bL) {
            this.netServerHandler.sendPacket(new Packet8UpdateHealth(this.health));
            this.bL = this.health;
        }
    }

    private void a(TileEntity tileentity) {
        if (tileentity != null) {
            Packet packet = tileentity.f();

            if (packet != null) {
                this.netServerHandler.sendPacket(packet);
            }
        }
    }

    public void v() {
        super.v();
    }

    public void receive(Entity entity, int i) {
        if (!entity.dead) {
            EntityTracker entitytracker = this.b.getTracker(this.dimension);

            if (entity instanceof EntityItem) {
                entitytracker.a(entity, new Packet22Collect(entity.id, this.id));
            }

            if (entity instanceof EntityArrow) {
                entitytracker.a(entity, new Packet22Collect(entity.id, this.id));
            }
        }

        super.receive(entity, i);
        this.activeContainer.a();
    }

    public void w() {
        if (!this.p) {
            this.q = -1;
            this.p = true;
            EntityTracker entitytracker = this.b.getTracker(this.dimension);

            entitytracker.a(this, new Packet18ArmAnimation(this, 1));
        }
    }

    public void x() {
    }

    public EnumBedError a(int i, int j, int k) {
        EnumBedError enumbederror = super.a(i, j, k);

        if (enumbederror == EnumBedError.OK) {
            EntityTracker entitytracker = this.b.getTracker(this.dimension);
            Packet17 packet17 = new Packet17(this, 0, i, j, k);

            entitytracker.a(this, packet17);

            // uberbukkit - beds (b1.3 - b1.6.4)
            if (!UberbukkitConfig.getInstance().getBoolean("mechanics.beds_pre_b1_6_5", false))
                this.netServerHandler.a(this.locX, this.locY, this.locZ, this.yaw, this.pitch);

            this.netServerHandler.sendPacket(packet17);
        }

        return enumbederror;
    }

    public void a(boolean flag, boolean flag1, boolean flag2) {
        if (this.isSleeping()) {
            EntityTracker entitytracker = this.b.getTracker(this.dimension);

            entitytracker.sendPacketToEntity(this, new Packet18ArmAnimation(this, 3));
        }

        super.a(flag, flag1, flag2);
        if (this.netServerHandler != null) {
            this.netServerHandler.a(this.locX, this.locY, this.locZ, this.yaw, this.pitch);
        }
    }

    public void mount(Entity entity) {
        // CraftBukkit start
        this.setPassengerOf(entity);
    }

    public void setPassengerOf(Entity entity) {
        // mount(null) doesn't really fly for overloaded methods,
        // so this method is needed

        super.setPassengerOf(entity);
        // CraftBukkit end

        this.netServerHandler.sendPacket(new Packet39AttachEntity(this, this.vehicle));
        this.netServerHandler.a(this.locX, this.locY, this.locZ, this.yaw, this.pitch);
    }

    protected void a(double d0, boolean flag) {
    }

    public void b(double d0, boolean flag) {
        super.a(d0, flag);
    }

    private void ai() {
        this.bO = this.bO % 100 + 1;
    }

    public void b(int i, int j, int k) {
        this.ai();
        this.netServerHandler.sendPacket(new Packet100OpenWindow(this.bO, 1, "Crafting", 9));
        this.activeContainer = new ContainerWorkbench(this.inventory, this.world, i, j, k);
        this.activeContainer.setPosition(new Vec3i(i, j, k));
        this.activeContainer.windowId = this.bO;
        this.activeContainer.a((ICrafting) this);
    }

    public void a(IInventory iinventory) {
        this.ai();

        // Poseidon start
        ChestOpenedEvent event = new ChestOpenedEvent((org.bukkit.entity.Player) this.getBukkitEntity(), iinventory.getContents());
        this.world.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        // Poseidon end

        this.netServerHandler.sendPacket(new Packet100OpenWindow(this.bO, 0, iinventory.getName(), iinventory.getSize()));
        this.activeContainer = new ContainerChest(this.inventory, iinventory);
        ((ContainerChest) this.activeContainer).setEntityHuman(this); // Uberbukkit
        this.activeContainer.windowId = this.bO;
        this.activeContainer.a((ICrafting) this);
    }

    public void a(IInventory iinventory, Vec3i position) {
        this.ai();

        this.netServerHandler.sendPacket(new Packet100OpenWindow(this.bO, 0, iinventory.getName(), iinventory.getSize()));
        this.activeContainer = new ContainerChest(this.inventory, iinventory);
        ((ContainerChest) this.activeContainer).setEntityHuman(this); // Uberbukkit
        this.activeContainer.setPosition(position);
        this.activeContainer.windowId = this.bO;
        this.activeContainer.a((ICrafting) this);
    }

    public void a(TileEntityFurnace tileentityfurnace) {
        this.ai();
        this.netServerHandler.sendPacket(new Packet100OpenWindow(this.bO, 2, tileentityfurnace.getName(), tileentityfurnace.getSize()));
        this.activeContainer = new ContainerFurnace(this.inventory, tileentityfurnace);
        this.activeContainer.setPosition(new Vec3i(tileentityfurnace.x, tileentityfurnace.y, tileentityfurnace.z));
        this.activeContainer.windowId = this.bO;
        this.activeContainer.a((ICrafting) this);
    }

    public void a(TileEntityDispenser tileentitydispenser) {
        this.ai();
        this.netServerHandler.sendPacket(new Packet100OpenWindow(this.bO, 3, tileentitydispenser.getName(), tileentitydispenser.getSize()));
        this.activeContainer = new ContainerDispenser(this.inventory, tileentitydispenser);
        this.activeContainer.setPosition(new Vec3i(tileentitydispenser.x, tileentitydispenser.y, tileentitydispenser.z));
        this.activeContainer.windowId = this.bO;
        this.activeContainer.a((ICrafting) this);
    }

    public void a(Container container, int i, ItemStack itemstack) {
        if (!(container.b(i) instanceof SlotResult)) {
            if (!this.h) {
                // uberbukkit
                if (this.netServerHandler.networkManager.pvn <= 6) {
                    this.netServerHandler.refreshInventory();
                    //this.netServerHandler.sendPacket(new Packet5EntityEquipment(-1, this.inventory.items));
                    //this.netServerHandler.sendPacket(new Packet5EntityEquipment(-2, this.inventory.craft));
                    //this.netServerHandler.sendPacket(new Packet5EntityEquipment(-3, this.inventory.armor));
                } else {
                    this.netServerHandler.sendPacket(new Packet103SetSlot(container.windowId, i, itemstack));
                }
            }
        }
    }

    public void updateInventory(Container container) {
        this.a(container, container.b());
    }

    public void a(Container container, List list) {
        // uberbukkit
        if (this.netServerHandler.networkManager.pvn <= 6) {
            this.netServerHandler.refreshInventory();
        } else {
            this.netServerHandler.sendPacket(new Packet104WindowItems(container.windowId, list));
            this.netServerHandler.sendPacket(new Packet103SetSlot(-1, -1, this.inventory.j()));
        }
    }

    public void a(Container container, int i, int j) {
        this.netServerHandler.sendPacket(new Packet105CraftProgressBar(container.windowId, i, j));
    }

    public void a(ItemStack itemstack) {
    }

    public void y() {
        this.netServerHandler.sendPacket(new Packet101CloseWindow(this.activeContainer.windowId));
        this.A();
    }

    public void z() {
        if (!this.h) {
            this.netServerHandler.sendPacket(new Packet103SetSlot(-1, -1, this.inventory.j()));
        }
    }

    public void A() {
        this.activeContainer.a((EntityHuman) this);
        this.activeContainer = this.defaultContainer;
    }

    public void a(float f, float f1, boolean flag, boolean flag1, float f2, float f3) {
        this.az = f;
        this.aA = f1;
        this.aC = flag;
        this.setSneak(flag1);
        this.pitch = f2;
        this.yaw = f3;
    }

    public void a(Statistic statistic, int i) {
        if (statistic == null) {
            return;
        }

        if (statistic instanceof Achievement) {
            Achievement achievement = (Achievement) statistic;
            if (this.achievementManager != null) {
                this.achievementManager.unlock(achievement);
            } else if (this.playerStatistics != null) {
                this.playerStatistics.addStatistic(statistic, i);
            }
            return;
        }

        if (this.playerStatistics != null) {
            this.playerStatistics.recordIncrement(statistic, i);
        }
    }

    public void B() {
        if (this.vehicle != null) {
            this.mount(this.vehicle);
        }

        if (this.passenger != null) {
            this.passenger.mount(this);
        }

        if (this.sleeping) {
            this.a(true, false, false);
        }

        // uberbukkit - drop item queue on disconnect
        if (this.netServerHandler.networkManager.pvn <= 6) {
            ArrayList<ItemStack> queue = this.packet5.queue.dropAllQueue();
            Player bukkitEntity = (Player) this.getBukkitEntity();
            for (ItemStack item : queue) {
                System.out.println("Drop queue id: " + item.id + ", dmg: " + item.damage + ", cnt: " + item.count);
                HashMap<Integer, org.bukkit.inventory.ItemStack> map = bukkitEntity.getInventory().addItem(new CraftItemStack(item));
                // drop what couldn't fit in the inventory
                for (org.bukkit.inventory.ItemStack stack : map.values()) {
                    bukkitEntity.getWorld().dropItemNaturally(bukkitEntity.getLocation(), stack);
                }
            }
        }
    }

    public void C() {
        this.bL = -99999999;
    }

    public void a(String s) {
        // uberbukkit - fix for multiple bed alerts (b1.3 - b1.6.4)
        if (this.netServerHandler.networkManager.pvn <= 11 || UberbukkitConfig.getInstance().getBoolean("mechanics.beds_pre_b1_6_5", false))
            return;

        StatisticStorage statisticstorage = StatisticStorage.a();
        String s1 = statisticstorage.a(s);

        this.netServerHandler.sendPacket(new Packet3Chat(s1));
    }

    // CraftBukkit start
    public long timeOffset = 0;
    public boolean relativeTime = true;

    public long getPlayerTime() {
        if (this.relativeTime) {
            // Adds timeOffset to the current server time.
            return this.world.getTime() + this.timeOffset;
        } else {
            // Adds timeOffset to the beginning of this day.
            return this.world.getTime() - (this.world.getTime() % 24000) + this.timeOffset;
        }
    }

    @Override
    public String toString() {
        return super.toString() + "(" + this.name + " at " + this.locX + "," + this.locY + "," + this.locZ + ")";
    }
    // CraftBukkit end
    
    // UberBukkit - Override NBT load to include statistics and achievements
    @Override
    public void a(NBTTagCompound nbttagcompound) {
        super.a(nbttagcompound);
        
        // Load player statistics (legacy - keeps stats other than achievements)
        if (this.playerStatistics != null && nbttagcompound.hasKey("PlayerStats")) {
            this.playerStatistics.loadFromNBT(nbttagcompound.k("PlayerStats"));
        }
        
        // Load achievements via AchievementManager
        if (this.achievementManager != null && nbttagcompound.hasKey("PlayerAchievements")) {
            this.achievementManager.loadFromNBT(nbttagcompound.k("PlayerAchievements"));
        } else if (this.achievementManager != null && nbttagcompound.hasKey("PlayerStats")) {
            // Migrate from old PlayerStats if PlayerAchievements doesn't exist
            NBTTagCompound statsNbt = nbttagcompound.k("PlayerStats");
            if (statsNbt.hasKey("Achievements")) {
                this.achievementManager.loadFromNBT(statsNbt);
            }
        }
    }
    
    // UberBukkit - Override NBT save to include statistics and achievements
    @Override
    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
        
        // Save player statistics (legacy - keeps stats other than achievements)
        if (this.playerStatistics != null) {
            NBTTagCompound statsNbt = new NBTTagCompound();
            this.playerStatistics.saveToNBT(statsNbt);
            nbttagcompound.a("PlayerStats", statsNbt);
        }
        
        // Save achievements via AchievementManager (separate key for cleaner data)
        if (this.achievementManager != null) {
            NBTTagCompound achievementsNbt = new NBTTagCompound();
            this.achievementManager.saveToNBT(achievementsNbt);
            nbttagcompound.a("PlayerAchievements", achievementsNbt);
        }
    }
}

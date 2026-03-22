package net.minecraft.server;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.bukkit.entity.Player;

public class EntityTrackerEntry {

    private static final double VELOCITY_PACKET_DEADBAND = 0.03D;
    private static final double VELOCITY_PACKET_FORCE_DELTA = 0.09D;
    private static final int VELOCITY_PACKET_MIN_INTERVAL_TICKS = 3;
    private static final int LIVING_RELATIVE_SYNC_MAX_TICKS = 120;

    public Entity tracker;
    // uberbukkit
    public boolean b_ = false;
    public boolean c_ = false;
    public boolean d_ = false;

    public int b;
    public int c;
    public int d;
    public int e;
    public int f;
    public int g;
    public int h;
    public double i;
    public double j;
    public double k;
    public int l = 0;
    private double o;
    private double p;
    private double q;
    private boolean r = false;
    private boolean isMoving;
    private int t = 0;
    private int velocityPacketCooldown = 0;
    public boolean m = false;
    public Set trackedPlayers = new HashSet();

    public EntityTrackerEntry(Entity entity, int i, int j, boolean flag) {
        this.tracker = entity;
        this.b = i;
        this.c = j;
        this.isMoving = flag;
        this.d = MathHelper.floor(entity.locX * 32.0D);
        this.e = MathHelper.floor(entity.locY * 32.0D);
        this.f = MathHelper.floor(entity.locZ * 32.0D);
        this.g = MathHelper.d(entity.yaw * 256.0F / 360.0F);
        this.h = MathHelper.d(entity.pitch * 256.0F / 360.0F);
    }

    public boolean equals(Object object) {
        return object instanceof EntityTrackerEntry ? ((EntityTrackerEntry) object).tracker.id == this.tracker.id : false;
    }

    public int hashCode() {
        return this.tracker.id;
    }

    private EntityTracker resolveEntityTracker() {
        if (this.tracker == null || this.tracker.world == null || !(this.tracker.world instanceof WorldServer)) {
            return null;
        }
        return ((WorldServer) this.tracker.world).tracker;
    }

    private boolean isNearAnyPlayer(List players, int nearChunkRadius) {
        if (players == null || players.isEmpty()) {
            return false;
        }

        int entityChunkX = this.tracker.bH;
        int entityChunkZ = this.tracker.bJ;
        for (int i = 0; i < players.size(); i++) {
            EntityPlayer player = (EntityPlayer) players.get(i);
            if (player == null) {
                continue;
            }

            int distance = Math.max(Math.abs(player.bH - entityChunkX), Math.abs(player.bJ - entityChunkZ));
            if (distance <= nearChunkRadius) {
                return true;
            }
        }
        return false;
    }

    public void track(List list) {
        this.m = false;
        if (!this.r || this.tracker.e(this.o, this.p, this.q) > 16.0D) {
            this.o = this.tracker.locX;
            this.p = this.tracker.locY;
            this.q = this.tracker.locZ;
            this.r = true;
            this.m = true;
            this.scanPlayers(list);
        }

        DataWatcher datawatcher = this.tracker.aa();
        boolean hasMetadataUpdate = datawatcher.a()
                || this.tracker.getSynchedEntityData() != null && this.tracker.getSynchedEntityData().isDirty();
        EntityTracker entityTracker = resolveEntityTracker();
        EntityTracker.TrackingPressureState pressureState = entityTracker != null ? entityTracker.getTrackingPressureState() : EntityTracker.TrackingPressureState.NORMAL;
        boolean nearLivingEntity = entityTracker != null
            && this.tracker instanceof EntityLiving
            && !(this.tracker instanceof EntityPlayer)
            && isNearAnyPlayer(list, entityTracker.getNearChunkRadius());
        if (this.velocityPacketCooldown > 0) {
            this.velocityPacketCooldown--;
        }

        if (++this.l % this.c == 0 || this.tracker.airBorne || hasMetadataUpdate) {
            ++this.t; // Poseidon - moved below

            // encoded means multiplied by 32
            // this is required to send it to the client, as the relative position is sent as the float multiplied by 32
            int newEncodedPosX = MathHelper.floor(this.tracker.locX * 32.0D);
            int newEncodedPosY = MathHelper.floor(this.tracker.locY * 32.0D);
            int newEncodedPosZ = MathHelper.floor(this.tracker.locZ * 32.0D);
            int newEncodedRotationYaw = MathHelper.d(this.tracker.yaw * 256.0F / 360.0F);
            int newEncodedRotationPitch = MathHelper.d(this.tracker.pitch * 256.0F / 360.0F);
            int encodedDiffX = newEncodedPosX - this.d;
            int encodedDiffY = newEncodedPosY - this.e;
            int encodedDiffZ = newEncodedPosZ - this.f;
            int maxRelativeSyncTicks = this.tracker instanceof EntityLiving && !(this.tracker instanceof EntityPlayer) ? LIVING_RELATIVE_SYNC_MAX_TICKS : 400;
            Object packet = null;
            // mob movement fix, credit to Oldmana#7086 from the Modification Station discord server
            // https://discordapp.com/channels/397834523028488203/397839387465089054/684637208199823377
            int movementUpdateTreshold = 1;
            int rotationUpdateTreshold = 1;
            boolean needsPositionUpdate = Math.abs(encodedDiffX) >= movementUpdateTreshold || Math.abs(encodedDiffY) >= movementUpdateTreshold || Math.abs(encodedDiffZ) >= movementUpdateTreshold || tracker instanceof EntityBoat || tracker instanceof EntityMinecart;

            boolean needsRotationUpdate = Math.abs(newEncodedRotationYaw - this.g) >= rotationUpdateTreshold || Math.abs(newEncodedRotationPitch - this.h) >= rotationUpdateTreshold;

            // CraftBukkit start - Code moved from below
            if (needsPositionUpdate) {
                this.d = newEncodedPosX;
                this.e = newEncodedPosY;
                this.f = newEncodedPosZ;
            }

            if (needsRotationUpdate) {
                this.g = newEncodedRotationYaw;
                this.h = newEncodedRotationPitch;
            }
            // CraftBukkit end

            if (encodedDiffX >= -128 && encodedDiffX < 128 && encodedDiffY >= -128 && encodedDiffY < 128 && encodedDiffZ >= -128 && encodedDiffZ < 128 && this.t <= maxRelativeSyncTicks) {
                // entity has moved less than 4 blocks
                if (needsPositionUpdate && needsRotationUpdate) {
                    packet = new Packet33RelEntityMoveLook(this.tracker.id, (byte) encodedDiffX, (byte) encodedDiffY, (byte) encodedDiffZ, (byte) newEncodedRotationYaw, (byte) newEncodedRotationPitch);
                } else if (needsPositionUpdate) {
                    packet = new Packet31RelEntityMove(this.tracker.id, (byte) encodedDiffX, (byte) encodedDiffY, (byte) encodedDiffZ);
                } else if (needsRotationUpdate) {
                    packet = new Packet32EntityLook(this.tracker.id, (byte) newEncodedRotationYaw, (byte) newEncodedRotationPitch);
                }
            } else {
                this.t = 0;
                // minecart clipping fix
                //this.tracker.locX = (double) i / 32.0D;
                //this.tracker.locY = (double) j / 32.0D;
                //this.tracker.locZ = (double) k / 32.0D;
                // entity has moved more than 4 blocks, send teleport

                // CraftBukkit start - Refresh list of who can see a player before sending teleport packet
                if (this.tracker instanceof EntityPlayer) {
                    this.scanPlayers(new java.util.ArrayList(this.trackedPlayers));
                }
                // CraftBukkit end

                packet = new Packet34EntityTeleport(this.tracker.id, newEncodedPosX, newEncodedPosY, newEncodedPosZ, (byte) newEncodedRotationYaw, (byte) newEncodedRotationPitch);
            }

            if (this.isMoving) {
                double d0 = this.tracker.motX - this.i;
                double d1 = this.tracker.motY - this.j;
                double d2 = this.tracker.motZ - this.k;
                double d3 = VELOCITY_PACKET_DEADBAND;
                double d5 = VELOCITY_PACKET_FORCE_DELTA;
                int minVelocityIntervalTicks = VELOCITY_PACKET_MIN_INTERVAL_TICKS;
                if (pressureState != EntityTracker.TrackingPressureState.PRESSURE && nearLivingEntity) {
                    d3 = VELOCITY_PACKET_DEADBAND * 0.5D;
                    d5 = VELOCITY_PACKET_FORCE_DELTA * 0.75D;
                    minVelocityIntervalTicks = 1;
                }
                double d4 = d0 * d0 + d1 * d1 + d2 * d2;

                boolean sendVelocity = d4 > d3 * d3 || d4 > 0.0D && this.tracker.motX == 0.0D && this.tracker.motY == 0.0D && this.tracker.motZ == 0.0D;
                boolean forcedVelocity = d4 > d5 * d5;
                if (sendVelocity && (forcedVelocity || this.velocityPacketCooldown <= 0)) {
                    this.i = this.tracker.motX;
                    this.j = this.tracker.motY;
                    this.k = this.tracker.motZ;
                    this.a((Packet) (new Packet28EntityVelocity(this.tracker.id, this.i, this.j, this.k)));
                    this.velocityPacketCooldown = minVelocityIntervalTicks;
                }
            }

            if (packet != null) {
                this.a((Packet) packet);
            }


            if (hasMetadataUpdate) {
                this.b((Packet) (new Packet40EntityMetadata(this.tracker)));
            }

            // uberbukkit - send both methods. legacy packet 18 gets ignored by clients that support packet 40
            if (this.b_ && this.tracker.vehicle == null) {
                this.b_ = false;
                this.b((Packet) (new Packet18ArmAnimation(this.tracker, 101)));
            } else if (!this.b_ && this.tracker.vehicle != null) {
                this.b_ = true;
                this.b((Packet) (new Packet18ArmAnimation(this.tracker, 100)));
            }

            if (this.tracker instanceof EntityLiving) {
                if (this.d_ && !this.tracker.isSneaking()) {
                    this.d_ = false;
                    this.b((Packet) (new Packet18ArmAnimation(this.tracker, 105)));
                } else if (!this.d_ && this.tracker.isSneaking()) {
                    this.d_ = true;
                    this.b((Packet) (new Packet18ArmAnimation(this.tracker, 104)));
                }
            }

            if (this.c_ && this.tracker.fireTicks <= 0) {
                this.c_ = false;
                this.b((Packet) (new Packet18ArmAnimation(this.tracker, 103)));
            } else if (!this.c_ && this.tracker.fireTicks > 0) {
                this.c_ = true;
                this.b((Packet) (new Packet18ArmAnimation(this.tracker, 102)));
            }

            /* CraftBukkit start - Code moved up
            if (needsPositionUpdate) {
                this.d = newEncodedPosX;
                this.e = newEncodedPosY;
                this.f = newEncodedPosZ;
            }

            if (needsRotationUpdate) {
                this.g = newEncodedRotationYaw;
                this.h = newEncodedRotationPitch;
            }
            // Craftbukkit end */
            this.tracker.airBorne = false;
        }

        if (this.tracker.velocityChanged) {
            // CraftBukkit start - create PlayerVelocity event
            boolean cancelled = false;

            if (this.tracker instanceof EntityPlayer) {
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) this.tracker.getBukkitEntity();
                org.bukkit.util.Vector velocity = player.getVelocity();

                org.bukkit.event.player.PlayerVelocityEvent event = new org.bukkit.event.player.PlayerVelocityEvent(player, velocity);
                this.tracker.world.getServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    cancelled = true;
                } else if (!velocity.equals(event.getVelocity())) {
                    player.setVelocity(velocity);
                }
            }

            if (!cancelled) {
                this.b((Packet) (new Packet28EntityVelocity(this.tracker)));
            }
            // CraftBukkit end
            this.tracker.velocityChanged = false;
        }
    }

    public void a(Packet packet) {
        Iterator iterator = this.trackedPlayers.iterator();

        while (iterator.hasNext()) {
            EntityPlayer entityplayer = (EntityPlayer) iterator.next();

            entityplayer.netServerHandler.sendPacket(packet.clone()); // uberbukkit - .clone()
        }
    }

    public void b(Packet packet) {
        this.a(packet);
        if (this.tracker instanceof EntityPlayer) {
            ((EntityPlayer) this.tracker).netServerHandler.sendPacket(packet.clone()); // uberbukkit - .clone()
        }
    }

    public void a() {
        // Poseidon start
        //this.a((Packet) (new Packet29DestroyEntity(this.tracker.id)));
        Iterator iterator = this.trackedPlayers.iterator();

        while (iterator.hasNext()) {
            EntityPlayer entityplayer = (EntityPlayer) iterator.next();

            entityplayer.removeQueue.add(Integer.valueOf(this.tracker.id));
        }
        // Poseidon end
    }

    public void a(EntityPlayer entityplayer) {
        if (this.trackedPlayers.contains(entityplayer)) {
            entityplayer.removeQueue.add(Integer.valueOf(this.tracker.id)); // Poseidon
            this.trackedPlayers.remove(entityplayer);
        }
    }

    public void b(EntityPlayer entityplayer) {
        if (entityplayer != this.tracker) {
            double d0 = entityplayer.locX - (double) this.d / 32.0D;
            double d1 = entityplayer.locZ - (double) this.f / 32.0D;

            if (d0 >= (double) (-this.b) && d0 <= (double) this.b && d1 >= (double) (-this.b) && d1 <= (double) this.b) {
                if (!this.trackedPlayers.contains(entityplayer) && this.d(entityplayer)) {
                    // CraftBukkit start
                    if (tracker instanceof EntityPlayer) {
                        org.bukkit.entity.Player player = (Player) ((EntityPlayer) tracker).getBukkitEntity();
                        if (!((Player) entityplayer.getBukkitEntity()).canSee(player)) {
                            return;
                        }
                    }

                    entityplayer.removeQueue.remove(Integer.valueOf(this.tracker.id));
                    // CraftBukkit end

                    this.trackedPlayers.add(entityplayer);
                    // Poseidon start
                    Packet packet = this.b(entityplayer.netServerHandler.networkManager.pvn);
                    entityplayer.netServerHandler.sendPacket(packet);

                    // uberbukkit
                    if (!entityplayer.protocol.canReceivePacket(40)) {
                        if (this.d_) {
                            entityplayer.netServerHandler.sendPacket((Packet) (new Packet18ArmAnimation(this.tracker, 104)));
                        }

                        if (this.b_) {
                            entityplayer.netServerHandler.sendPacket((Packet) (new Packet18ArmAnimation(this.tracker, 100)));
                        }

                        if (this.c_) {
                            entityplayer.netServerHandler.sendPacket((Packet) (new Packet18ArmAnimation(this.tracker, 102)));
                        }
                    } else {
                        if (!this.tracker.datawatcher.getD()) {
                            entityplayer.netServerHandler.sendPacket(new Packet40EntityMetadata(this.tracker));
                        }
                    }

                    this.i = this.tracker.motX;
                    this.j = this.tracker.motY;
                    this.k = this.tracker.motZ;
                    if (this.isMoving) {
                        entityplayer.netServerHandler.sendPacket(new Packet28EntityVelocity(this.tracker.id, this.tracker.motX, this.tracker.motY, this.tracker.motZ));
                    }

                    if (this.tracker.vehicle != null) {
                        entityplayer.netServerHandler.sendPacket(new Packet39AttachEntity(this.tracker, this.tracker.vehicle));
                    }
                    // Poseidon end

                    // CraftBukkit start
                    if (this.tracker.passenger != null) {
                        entityplayer.netServerHandler.sendPacket(new Packet39AttachEntity(this.tracker.passenger, this.tracker));
                    }
                    // CraftBukkit end

                    ItemStack[] aitemstack = this.tracker.getEquipment();

                    if (aitemstack != null) {
                        for (int i = 0; i < aitemstack.length; ++i) {
                            entityplayer.netServerHandler.sendPacket(new Packet5EntityEquipment(this.tracker.id, i, aitemstack[i]));
                        }
                    }

                    if (this.tracker instanceof EntitySkeleton) {
                        EntitySkeleton skeleton = (EntitySkeleton) this.tracker;
                        boolean hostile = skeleton.target != null && skeleton.target.T();
                        entityplayer.netServerHandler.sendPacket(new Packet38EntityStatus(this.tracker.id, (byte) (hostile ? 14 : 15)));
                    } else if (this.tracker instanceof EntityPlayer) {
                        EntityPlayer trackedPlayer = (EntityPlayer) this.tracker;
                        entityplayer.netServerHandler.sendPacket(new Packet38EntityStatus(this.tracker.id, (byte) (trackedPlayer.isBowPoseActive() ? 16 : 17)));
                    }

                    if (this.tracker instanceof EntityHuman) {
                        EntityHuman entityhuman = (EntityHuman) this.tracker;

                        // uberbukkit
                        if (entityhuman.isSleeping() && entityplayer.protocol.canReceivePacket(17)) {
                            ChunkCoordinates chunkcoordinates = entityhuman.A;
                            if (chunkcoordinates != null) {
                                entityplayer.netServerHandler.sendPacket(new Packet17(this.tracker, 0, chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z));
                            } else {
                                entityplayer.netServerHandler.sendPacket(new Packet17(this.tracker, 0, MathHelper.floor(this.tracker.locX), MathHelper.floor(this.tracker.locY), MathHelper.floor(this.tracker.locZ)));
                            }
                        }
                    }

                    // MCOSE: When a player enters tracking range of a wall map, send the full
                    // map pixel data so they can see it without having to pick it up first.
                    if (this.tracker instanceof EntityMapHanging) {
                        EntityMapHanging mapHanging = (EntityMapHanging) this.tracker;
                        WorldMap worldmap = (WorldMap) this.tracker.world.a(WorldMap.class, "map_" + mapHanging.mapId);
                        if (worldmap != null) {
                            // Send full map data as column packets (128 columns of 128 pixels each)
                            for (int col = 0; col < 128; ++col) {
                                byte[] columnData = new byte[131]; // 3 header bytes + 128 pixel bytes
                                columnData[0] = 0; // type 0 = column data
                                columnData[1] = (byte) col; // column index
                                columnData[2] = 0; // start row
                                for (int row = 0; row < 128; ++row) {
                                    columnData[row + 3] = worldmap.f[row * 128 + col];
                                }
                                entityplayer.netServerHandler.sendPacket(
                                    new Packet131((short) Item.MAP.id, mapHanging.mapId, columnData));
                            }
                        }
                    }
                }
            } else if (this.trackedPlayers.contains(entityplayer)) {
                this.trackedPlayers.remove(entityplayer);
                entityplayer.removeQueue.add(Integer.valueOf(this.tracker.id)); // Poseidon
                //entityplayer.netServerHandler.sendPacket(new Packet29DestroyEntity(this.tracker.id));
            }
        }
    }

    private boolean d(EntityPlayer entityplayer) {
        return entityplayer.getWorldServer().getPlayerManager().a(entityplayer, this.tracker.bH, this.tracker.bJ);
    }

    public void scanPlayers(List list) {
        for (int i = 0; i < list.size(); ++i) {
            this.b((EntityPlayer) list.get(i));
        }
    }

    private Packet b(int pvn) {
        if (this.tracker.dead) { // Poseidon
            // CraftBukkit start - Remove useless error spam, just return
            // System.out.println("Fetching addPacket for removed entity");
            return null;
            // CraftBukkit end
        }

        if (this.tracker instanceof EntityItem) {
            EntityItem entityitem = (EntityItem) this.tracker;
            Packet21PickupSpawn packet21pickupspawn = new Packet21PickupSpawn(entityitem);

            // There's no reason to set the item's position to the compressed position
            //entityitem.locX = (double) packet21pickupspawn.b / 32.0D;
            //entityitem.locY = (double) packet21pickupspawn.c / 32.0D;
            //entityitem.locZ = (double) packet21pickupspawn.d / 32.0D;
            return packet21pickupspawn;
        } else if (this.tracker instanceof EntityPlayer) {
            // CraftBukkit start - limit name length to 16 characters
            if (((EntityHuman) this.tracker).name.length() > 16) {
                ((EntityHuman) this.tracker).name = ((EntityHuman) this.tracker).name.substring(0, 16);
            }
            // CraftBukkit end
            return new Packet20NamedEntitySpawn((EntityHuman) this.tracker);
        } else {
            if (this.tracker instanceof EntityMinecart) {
                EntityMinecart entityminecart = (EntityMinecart) this.tracker;

                if (entityminecart.type == 0) {
                    return new Packet23VehicleSpawn(this.tracker, 10);
                }

                if (entityminecart.type == 1) {
                    return new Packet23VehicleSpawn(this.tracker, 11);
                }

                if (entityminecart.type == 2) {
                    return new Packet23VehicleSpawn(this.tracker, 12);
                }
            }

            if (this.tracker instanceof EntityBoat) {
                return new Packet23VehicleSpawn(this.tracker, 1);
            } else if (this.tracker instanceof EntitySnowman) {
                return new Packet24MobSpawn((EntityLiving) this.tracker);
            } else if (this.tracker instanceof EntityHerobrine) {
                return new Packet24MobSpawn((EntityLiving) this.tracker);
            } else if (this.tracker instanceof IAnimal) {
                return new Packet24MobSpawn((EntityLiving) this.tracker);
            } else if (this.tracker instanceof EntityFish) {
                return new Packet23VehicleSpawn(this.tracker, 90);
            } else if (this.tracker instanceof EntityArrow) {
                EntityLiving entityliving = ((EntityArrow) this.tracker).shooter;

                return new Packet23VehicleSpawn(this.tracker, 60, entityliving != null ? entityliving.id : this.tracker.id);
                // uberbukkit
            } else if (this.tracker instanceof EntitySnowball || (this.tracker instanceof EntityFireball && pvn < 12)) {
                return new Packet23VehicleSpawn(this.tracker, 61);
            } else if (this.tracker instanceof EntityFireball && pvn >= 12) {
                EntityFireball entityfireball = (EntityFireball) this.tracker;
                // CraftBukkit start - added check for null shooter
                int shooter = ((EntityFireball) this.tracker).shooter != null ? ((EntityFireball) this.tracker).shooter.id : 1;
                Packet23VehicleSpawn packet23vehiclespawn = new Packet23VehicleSpawn(this.tracker, 63, shooter);
                // CraftBukkit end

                packet23vehiclespawn.e = (int) (entityfireball.c * 8000.0D);
                packet23vehiclespawn.f = (int) (entityfireball.d * 8000.0D);
                packet23vehiclespawn.g = (int) (entityfireball.e * 8000.0D);
                return packet23vehiclespawn;
            } else if (this.tracker instanceof EntityEgg) {
                return new Packet23VehicleSpawn(this.tracker, 62);
            } else if (this.tracker instanceof EntityTNTPrimed) {
                return new Packet23VehicleSpawn(this.tracker, 50);
            } else {
                if (this.tracker instanceof EntityFallingSand) {
                    EntityFallingSand entityfallingsand = (EntityFallingSand) this.tracker;

                    if (entityfallingsand.a == Block.SAND.id) {
                        return new Packet23VehicleSpawn(this.tracker, 70);
                    }

                    if (entityfallingsand.a == Block.GRAVEL.id) {
                        return new Packet23VehicleSpawn(this.tracker, 71);
                    }
                }

                if (this.tracker instanceof EntityPainting) {
                    return new Packet25EntityPainting((EntityPainting) this.tracker);
                } else if (this.tracker instanceof EntityMapHanging) {
                    return new Packet26EntityMapHanging((EntityMapHanging) this.tracker);
                } else {
                    throw new IllegalArgumentException("Don\'t know how to add " + this.tracker.getClass() + "!");
                }
            }
        }
    }

    public void c(EntityPlayer entityplayer) {
        if (this.trackedPlayers.contains(entityplayer)) {
            this.trackedPlayers.remove(entityplayer);
            entityplayer.removeQueue.add(Integer.valueOf(this.tracker.id)); // Poseidon
            //entityplayer.netServerHandler.sendPacket(new Packet29DestroyEntity(this.tracker.id));
        }
    }
}

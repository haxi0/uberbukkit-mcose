package net.minecraft.server;

import java.util.Iterator;
import java.util.List;

import org.bukkit.craftbukkit.TrigMath;
import org.bukkit.craftbukkit.entity.CraftItem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import com.legacyminecraft.poseidon.PoseidonConfig;

import me.devcody.uberbukkit.math.Vec3i;
import uk.betacraft.uberbukkit.Uberbukkit;

import net.minecraft.server.event.EventBus;
import net.minecraft.server.event.events.PlayerDamageEvent;
import net.minecraft.server.event.events.PlayerDamageType;
import net.minecraft.server.event.events.PlayerDealDamageEvent;
import net.minecraft.server.event.events.PlayerJumpEvent;
import net.minecraft.server.event.events.PlayerMoveEvent;
import net.minecraft.server.registry.PlayerCapabilityRegistryApi;

public abstract class EntityHuman extends EntityLiving {

    public InventoryPlayer inventory = new InventoryPlayer(this);
    public Container defaultContainer;
    public Container activeContainer;
    public byte l = 0;
    public int m = 0;
    public float n;
    public float o;
    public boolean p = false;
    public int q = 0;
    public String name;
    public int dimension;
    public double t;
    public double u;
    public double v;
    public double w;
    public double x;
    public double y;
    // CraftBukkit start
    public boolean sleeping;
    public boolean fauxSleeping;
    public String spawnWorld = "";
    // CraftBukkit end
    public ChunkCoordinates A;
    public int sleepTicks; // CraftBukkit - private -> public
    public float B;
    public float C;
    public ChunkCoordinates b;
    private ChunkCoordinates c;
    private ChunkCoordinates startBoatRidingCoordinate;
    public int D = 20;
    protected boolean E = false;
    public float F;
    // Project Poseidon compatibility: 0 = survival, 1 = creative
    public int gameMode = 0;
    private int d = 0;
    public EntityFish hookedFish = null;
    
    // MCOSE - Wool colors collected for Rainbow Collection achievement (bitmask)
    public int woolColorsCollected = 0;
    
    // uberbukkit - Name for the tab list (allows keeping the prefix only there)
    public String listName;

    public void setListName(String name) {
        this.listName = name;
    }

    public EntityHuman(World world) {
        super(world);
        this.defaultContainer = new ContainerPlayer(this.inventory, !world.isStatic);
        this.activeContainer = this.defaultContainer;
        this.height = 1.62F;
        ChunkCoordinates chunkcoordinates = world.getSpawn();

        this.setPositionRotation((double) chunkcoordinates.x + 0.5D, (double) (chunkcoordinates.y + 1), (double) chunkcoordinates.z + 0.5D, 0.0F, 0.0F);
        this.health = 20;
        this.U = "humanoid";
        this.T = 180.0F;
        this.maxFireTicks = 20;
        this.texture = "/mob/char.png";
    }

    protected void b() {
        super.b();
        this.datawatcher.a(16, Byte.valueOf((byte) 0));
    }

    public void m_() {
        if (this.isSleeping()) {
            ++this.sleepTicks;
            if (this.sleepTicks > 100) {
                this.sleepTicks = 100;
            }

            if (!this.world.isStatic) {
                if (!this.o()) {
                    this.a(true, true, false);
                } else if (this.world.d()) {
                    this.a(false, true, true);
                }
            }
        } else if (this.sleepTicks > 0) {
            ++this.sleepTicks;
            if (this.sleepTicks >= 110) {
                this.sleepTicks = 0;
            }
        }

        super.m_();
        if (!this.world.isStatic && this.activeContainer != null && !this.activeContainer.b(this)) {
            this.y();
            this.activeContainer = this.defaultContainer;
        }

        this.t = this.w;
        this.u = this.x;
        this.v = this.y;
        double d0 = this.locX - this.w;
        double d1 = this.locY - this.x;
        double d2 = this.locZ - this.y;
        double d3 = 10.0D;

        if (d0 > d3) {
            this.t = this.w = this.locX;
        }

        if (d2 > d3) {
            this.v = this.y = this.locZ;
        }

        if (d1 > d3) {
            this.u = this.x = this.locY;
        }

        if (d0 < -d3) {
            this.t = this.w = this.locX;
        }

        if (d2 < -d3) {
            this.v = this.y = this.locZ;
        }

        if (d1 < -d3) {
            this.u = this.x = this.locY;
        }

        this.w += d0 * 0.25D;
        this.y += d2 * 0.25D;
        this.x += d1 * 0.25D;
        this.a(StatisticList.k, 1);
        if (this.vehicle == null) {
            this.c = null;
            this.startBoatRidingCoordinate = null;
        }
    }

    protected boolean D() {
        return this.health <= 0 || this.isSleeping();
    }

    protected void y() {
        this.activeContainer = this.defaultContainer;
    }

    public void E() {
        double d0 = this.locX;
        double d1 = this.locY;
        double d2 = this.locZ;

        super.E();
        this.n = this.o;
        this.o = 0.0F;
        this.i(this.locX - d0, this.locY - d1, this.locZ - d2);
    }

    protected void c_() {
        if (this.p) {
            ++this.q;
            if (this.q >= 8) {
                this.q = 0;
                this.p = false;
            }
        } else {
            this.q = 0;
        }

        this.aa = (float) this.q / 8.0F;
    }

    public void v() {
        // CraftBukkit - spawnMonsters -> allowMonsters
        if (!this.world.allowMonsters && this.health < 20 && this.ticksLived % 20 * 12 == 0) {
            this.b(1, RegainReason.REGEN);
        }

        this.inventory.f();
        if (this.ticksLived % 20 == 0) {
            this.checkArmorAchievements();
        }
        this.n = this.o;
        super.v();
        float f = MathHelper.a(this.motX * this.motX + this.motZ * this.motZ);
        // CraftBukkit - Math -> TrigMath
        float f1 = (float) TrigMath.atan(-this.motY * 0.20000000298023224D) * 15.0F;

        if (f > 0.1F) {
            f = 0.1F;
        }

        if (!this.onGround || this.health <= 0) {
            f = 0.0F;
        }

        if (this.onGround || this.health <= 0) {
            f1 = 0.0F;
        }

        this.o += (f - this.o) * 0.4F;
        this.aj += (f1 - this.aj) * 0.8F;
        if (this.health > 0) {
            List list = this.world.b((Entity) this, this.boundingBox.b(1.0D, 0.0D, 1.0D));

            if (list != null) {
                for (int i = 0; i < list.size(); ++i) {
                    Entity entity = (Entity) list.get(i);

                    if (!entity.dead) {
                        this.i(entity);
                    }
                }
            }
        }

        // Immediately extinguish Creative players when exiting lava or fire
        if (this.gameMode == 1) {
            boolean inLava = this.ae();
            boolean inFire = this.world.d(this.boundingBox.shrink(0.0010D, 0.0010D, 0.0010D));
            if (!inLava && !inFire && this.fireTicks > 0) {
                this.fireTicks = 0;
            }
        }
    }

    private void i(Entity entity) {
        entity.b(this);
    }

    public void die(Entity entity) {
        super.die(entity);
        this.b(0.2F, 0.2F);
        this.setPosition(this.locX, this.locY, this.locZ);
        this.motY = 0.10000000149011612D;
        if (this.name.equals("Notch")) {
            this.a(new ItemStack(Item.APPLE, 1), true);
        }

        // Only drop inventory items if keepInventory gamerule is false
        boolean keepInventory = this.world.worldData != null && this.world.worldData.getKeepInventory();
        if (!keepInventory) {
            this.inventory.h();
        }
        // Multiplayer parity: send death tilt and smoke puff
        if (!this.world.isStatic && this instanceof EntityPlayer) {
            // status 3 indicates death animation on clients
            this.world.a(this, (byte) 3);
            // spawn death smoke
            ((WorldServer) this.world).server.serverConfigurationManager.sendPacketNearby(this, this.locX, this.locY + 0.1D, this.locZ, 32.0D, ((WorldServer) this.world).dimension,
                new Packet61(2001 /* aux effect id placeholder */, (int) Math.floor(this.locX), (int) Math.floor(this.locY), (int) Math.floor(this.locZ), 0));
        }
        if (entity != null) {
            this.motX = (double) (-MathHelper.cos((this.af + this.yaw) * 3.1415927F / 180.0F) * 0.1F);
            this.motZ = (double) (-MathHelper.sin((this.af + this.yaw) * 3.1415927F / 180.0F) * 0.1F);
        } else {
            this.motX = this.motZ = 0.0D;
        }

        this.height = 0.1F;
        this.a(StatisticList.y, 1);
    }

    public void c(Entity entity, int i) {
        this.m += i;
        if (entity instanceof EntityHuman) {
            this.a(StatisticList.A, 1);
        } else {
            this.a(StatisticList.z, 1);
        }
    }

    public void F() {
        this.a(this.inventory.splitStack(this.inventory.itemInHandIndex, 1), false);
    }

    public void b(ItemStack itemstack) {
        this.a(itemstack, false);
    }

    public void a(ItemStack itemstack, boolean flag) {
        if (itemstack != null) {
            EntityItem entityitem = new EntityItem(this.world, this.locX, this.locY - 0.30000001192092896D + (double) this.t(), this.locZ, itemstack);

            entityitem.pickupDelay = 40;
            float f = 0.1F;
            float f1;

            if (flag) {
                f1 = this.random.nextFloat() * 0.5F;
                float f2 = this.random.nextFloat() * 3.1415927F * 2.0F;

                entityitem.motX = (double) (-MathHelper.sin(f2) * f1);
                entityitem.motZ = (double) (MathHelper.cos(f2) * f1);
                entityitem.motY = 0.20000000298023224D;
            } else {
                f = 0.3F;
                entityitem.motX = (double) (-MathHelper.sin(this.yaw / 180.0F * 3.1415927F) * MathHelper.cos(this.pitch / 180.0F * 3.1415927F) * f);
                entityitem.motZ = (double) (MathHelper.cos(this.yaw / 180.0F * 3.1415927F) * MathHelper.cos(this.pitch / 180.0F * 3.1415927F) * f);
                entityitem.motY = (double) (-MathHelper.sin(this.pitch / 180.0F * 3.1415927F) * f + 0.1F);
                f = 0.02F;
                f1 = this.random.nextFloat() * 3.1415927F * 2.0F;
                f *= this.random.nextFloat();
                entityitem.motX += Math.cos((double) f1) * (double) f;
                entityitem.motY += (double) ((this.random.nextFloat() - this.random.nextFloat()) * 0.1F);
                entityitem.motZ += Math.sin((double) f1) * (double) f;
            }

            // CraftBukkit start
            Player player = (Player) this.getBukkitEntity();
            CraftItem drop = new CraftItem(this.world.getServer(), entityitem);

            PlayerDropItemEvent event = new PlayerDropItemEvent(player, drop);
            this.world.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                player.getInventory().addItem(drop.getItemStack());
                return;
            }
            // CraftBukkit end

            this.a(entityitem);
            this.a(StatisticList.v, 1);
        }
    }

    protected void a(EntityItem entityitem) {
        this.world.addEntity(entityitem);
    }

    public float a(Block block) {
        float f = this.inventory.a(block);

        if (this.a(Material.WATER)) {
            f /= 5.0F;
        }

        if (!this.onGround) {
            f /= 5.0F;
        }

        return f;
    }

    public boolean b(Block block) {
        return this.inventory.b(block);
    }

    public void a(NBTTagCompound nbttagcompound) {
        super.a(nbttagcompound);
        NBTTagList nbttaglist = nbttagcompound.l("Inventory");

        this.inventory.b(nbttaglist);
        this.dimension = nbttagcompound.e("Dimension");
        this.sleeping = nbttagcompound.m("Sleeping");
        this.sleepTicks = nbttagcompound.d("SleepTimer");
        if (nbttagcompound.hasKey("PortalCooldown")) {
            this.D = nbttagcompound.e("PortalCooldown");
        }
        if (this.sleeping) {
            this.A = new ChunkCoordinates(MathHelper.floor(this.locX), MathHelper.floor(this.locY), MathHelper.floor(this.locZ));
            this.a(true, true, false);
        }

        // Project Poseidon - Load gamemode from NBT
        if (nbttagcompound.hasKey("GameType")) {
            this.gameMode = nbttagcompound.e("GameType");
        }

        // CraftBukkit start
        this.spawnWorld = nbttagcompound.getString("SpawnWorld");
        if (this.spawnWorld == "") {
            this.spawnWorld = this.world.getServer().getWorlds().get(0).getName();
        }
        // CraftBukkit end

        if (nbttagcompound.hasKey("SpawnX") && nbttagcompound.hasKey("SpawnY") && nbttagcompound.hasKey("SpawnZ")) {
            this.b = new ChunkCoordinates(nbttagcompound.e("SpawnX"), nbttagcompound.e("SpawnY"), nbttagcompound.e("SpawnZ"));
        }
    }

    public void b(NBTTagCompound nbttagcompound) {
        super.b(nbttagcompound);
        nbttagcompound.a("Inventory", (NBTBase) this.inventory.a(new NBTTagList()));
        nbttagcompound.a("Dimension", this.dimension);
        nbttagcompound.a("Sleeping", this.sleeping);
        nbttagcompound.a("SleepTimer", (short) this.sleepTicks);
        nbttagcompound.a("PortalCooldown", this.D);
        nbttagcompound.a("GameType", this.gameMode); // Project Poseidon - Save gamemode
        if (this.b != null) {
            nbttagcompound.a("SpawnX", this.b.x);
            nbttagcompound.a("SpawnY", this.b.y);
            nbttagcompound.a("SpawnZ", this.b.z);
            nbttagcompound.setString("SpawnWorld", this.spawnWorld); // CraftBukkit
        }
    }

    public void a(IInventory iinventory) {
    }

    public void a(IInventory iinventory, Vec3i position) {
    }

    public void b(int i, int j, int k) {
    }

    public void receive(Entity entity, int i) {
    }

    public float t() {
        return 0.12F;
    }

    protected void s() {
        this.height = 1.62F;
    }

    public boolean damageEntity(Entity entity, int i) {
        this.ay = 0;
        if (this.health <= 0) {
            return false;
        } else {
            // Project Poseidon - No damage in creative mode
            if (this.gameMode == 1) {
                return false;
            }

            if (this instanceof EntityPlayer && PlayerCapabilityRegistryApi.isInvulnerable((EntityPlayer) this)) {
                return false;
            }

            if (this.isSleeping() && !this.world.isStatic) {
                this.a(true, true, false);
            }

            if (entity instanceof EntityMonster || entity instanceof EntityArrow) {
                if (this.world.spawnMonsters == 0) {
                    i = 0;
                }

                if (this.world.spawnMonsters == 1) {
                    i = i / 3 + 1;
                }

                if (this.world.spawnMonsters == 3) {
                    i = i * 3 / 2;
                }
            }

            if (i == 0) {
                return false;
            } else {
                Object object = entity;

                if (entity instanceof EntityArrow && ((EntityArrow) entity).shooter != null) {
                    object = ((EntityArrow) entity).shooter;
                }

                if (!(object instanceof EntityLiving) && this instanceof EntityPlayer) {
                    Entity resolved = object instanceof Entity ? (Entity) object : null;
                    PlayerDamageEvent damageEvent = new PlayerDamageEvent(
                            (EntityPlayer) this,
                            this.world,
                            entity,
                            resolved,
                            this.resolveDamageType(entity, resolved),
                            i
                    );
                    EventBus.global().publish(damageEvent);
                    if (damageEvent.isCancelled()) {
                        return false;
                    }
                    i = damageEvent.getAmount();
                    if (i <= 0) {
                        return false;
                    }
                }

                if (object instanceof EntityLiving) {
                    // CraftBukkit start - this is here instead of EntityMonster because EntityLiving(s) that aren't monsters
                    // also damage the player in this way. For example, EntitySlime.

                    if (this instanceof EntityPlayer) {
                        Entity resolved = object instanceof Entity ? (Entity) object : null;
                        PlayerDamageEvent damageEvent = new PlayerDamageEvent(
                                (EntityPlayer) this,
                                this.world,
                                entity,
                                resolved,
                                this.resolveDamageType(entity, resolved),
                                i
                        );
                        EventBus.global().publish(damageEvent);
                        if (damageEvent.isCancelled()) {
                            return false;
                        }
                        i = damageEvent.getAmount();
                        if (i <= 0) {
                            return false;
                        }
                    }

                    // We handle projectiles in their individual classes!
                    if (!(entity.getBukkitEntity() instanceof Projectile)) {
                        org.bukkit.entity.Entity damager = ((Entity) object).getBukkitEntity();
                        org.bukkit.entity.Entity damagee = this.getBukkitEntity();

                        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, damagee, EntityDamageEvent.DamageCause.ENTITY_ATTACK, i);
                        this.world.getServer().getPluginManager().callEvent(event);

                        if (event.isCancelled() || event.getDamage() == 0) {
                            return false;
                        }

                        i = event.getDamage();
                    }
                    // CraftBukkit end

                    this.a((EntityLiving) object, false);
                }

                this.a(StatisticList.x, i);
                return super.damageEntity(entity, i);
            }
        }
    }

    protected boolean j_() {
        return false;
    }

    protected void a(EntityLiving entityliving, boolean flag) {
        if (!(entityliving instanceof EntityCreeper) && !(entityliving instanceof EntityGhast)) {
            if (entityliving instanceof EntityWolf) {
                EntityWolf entitywolf = (EntityWolf) entityliving;

                if (entitywolf.isTamed() && this.name.equals(entitywolf.getOwnerName())) {
                    return;
                }
            }

            if (!(entityliving instanceof EntityHuman) || this.j_()) {
                List list = this.world.a(EntityWolf.class, AxisAlignedBB.b(this.locX, this.locY, this.locZ, this.locX + 1.0D, this.locY + 1.0D, this.locZ + 1.0D).b(16.0D, 4.0D, 16.0D));
                Iterator iterator = list.iterator();

                while (iterator.hasNext()) {
                    Entity entity = (Entity) iterator.next();
                    EntityWolf entitywolf1 = (EntityWolf) entity;

                    if (entitywolf1.isTamed() && entitywolf1.F() == null && this.name.equals(entitywolf1.getOwnerName()) && (!flag || !entitywolf1.isSitting())) {
                        // CraftBukkit start
                        org.bukkit.entity.Entity bukkitTarget = entity == null ? null : entityliving.getBukkitEntity();

                        EntityTargetEvent event;
                        if (flag) {
                            event = new EntityTargetEvent(entitywolf1.getBukkitEntity(), bukkitTarget, EntityTargetEvent.TargetReason.OWNER_ATTACKED_TARGET);
                        } else {
                            event = new EntityTargetEvent(entitywolf1.getBukkitEntity(), bukkitTarget, EntityTargetEvent.TargetReason.TARGET_ATTACKED_OWNER);
                        }
                        this.world.getServer().getPluginManager().callEvent(event);

                        if (event.isCancelled()) {
                            continue;
                        }
                        // CraftBukkit end

                        entitywolf1.setSitting(false);
                        entitywolf1.setTarget(entityliving);
                    }
                }
            }
        }
    }

    protected void c(int i) {
        int j = 25 - this.inventory.g();
        int k = i * j + this.d;

        this.inventory.c(i);
        i = k / 25;
        this.d = k % 25;
        super.c(i);
    }

    public void a(TileEntityFurnace tileentityfurnace) {
    }

    public void a(TileEntityDispenser tileentitydispenser) {
    }

    public void a(TileEntitySign tileentitysign) {
    }

    public void c(Entity entity) {
        if (!entity.a(this)) {
            ItemStack itemstack = this.G();

            if (itemstack != null && entity instanceof EntityLiving) {
                itemstack.a((EntityLiving) entity);
                // CraftBukkit - bypass infinite items; <= 0 -> == 0
                if (itemstack.count == 0) {
                    itemstack.a(this);
                    this.H();
                }
            }
        }
    }

    public ItemStack G() {
        return this.inventory.getItemInHand();
    }

    public void H() {
        this.inventory.setItem(this.inventory.itemInHandIndex, (ItemStack) null);
    }

    public double I() {
        return (double) (this.height - 0.5F);
    }

    public void w() {
        this.q = -1;
        this.p = true;
    }

    public void d(Entity entity) {
        if (this instanceof EntityPlayer && !PlayerCapabilityRegistryApi.canAffectEntities((EntityPlayer) this)) {
            return;
        }

        int i = this.inventory.a(entity);

        if (i > 0) {
            if (Uberbukkit.getTargetPVN() >= 11) {
                if (this.motY < 0.0D) {
                    ++i;
                }
            }
            // CraftBukkit start - Don't call the event when the entity is human since it will be called with damageEntity
            if (entity instanceof EntityLiving && !(entity instanceof EntityHuman)) {
                org.bukkit.entity.Entity damager = this.getBukkitEntity();
                org.bukkit.entity.Entity damagee = (entity == null) ? null : entity.getBukkitEntity();

                EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(damager, damagee, EntityDamageEvent.DamageCause.ENTITY_ATTACK, i);
                this.world.getServer().getPluginManager().callEvent(event);

                if (event.isCancelled() || event.getDamage() == 0) {
                    return;
                }

                i = event.getDamage();
            }
            // CraftBukkit end

            if (this instanceof EntityPlayer) {
                ItemStack held = this.G();
                PlayerDealDamageEvent damageEvent = new PlayerDealDamageEvent(
                        (EntityPlayer) this,
                        this.world,
                        entity,
                        held,
                        PlayerDamageType.MELEE,
                        i
                );
                EventBus.global().publish(damageEvent);
                if (damageEvent.isCancelled()) {
                    return;
                }
                i = damageEvent.getAmount();
                if (i <= 0) {
                    return;
                }
            }

            // CraftBukkit start - Return when the damage fails so that the item will not lose durability
            double d0 = entity.motX;
            double d1 = entity.motY;
            double d2 = entity.motZ;

            if (!entity.damageEntity(this, i)) {
                return;
            }

            if (entity instanceof EntityPlayer && entity.velocityChanged && PoseidonConfig.getInstance().getBoolean("settings.player-knockback-fix.enabled", true)) {
                boolean cancelled = false;
                org.bukkit.entity.Player player = (org.bukkit.entity.Player) entity.getBukkitEntity();
                org.bukkit.util.Vector velocity = new org.bukkit.util.Vector(d0, d1, d2);

                org.bukkit.event.player.PlayerVelocityEvent event = new org.bukkit.event.player.PlayerVelocityEvent(player, velocity.clone());
                this.world.getServer().getPluginManager().callEvent(event);

                if (event.isCancelled()) {
                    cancelled = true;
                } else if (!velocity.equals(event.getVelocity())) {
                    player.setVelocity(velocity);
                }

                if (!cancelled) {
                    ((EntityPlayer) entity).netServerHandler.sendPacket(new Packet28EntityVelocity(entity));
                    entity.velocityChanged = false;
                    entity.motX = d0;
                    entity.motY = d1;
                    entity.motZ = d2;
                }
            }

            // CraftBukkit end

            ItemStack itemstack = this.G();

            if (itemstack != null && entity instanceof EntityLiving) {
                itemstack.a((EntityLiving) entity, this);
                // CraftBukkit - bypass infinite items; <= 0 -> == 0
                if (itemstack.count == 0) {
                    itemstack.a(this);
                    this.H();
                }
            }

            if (entity instanceof EntityLiving) {
                if (entity.T()) {
                    this.a((EntityLiving) entity, true);
                }

                this.a(StatisticList.w, i);
            }
        }
    }

    public void a(ItemStack itemstack) {
    }

    public void die() {
        super.die();
        this.defaultContainer.a(this);
        if (this.activeContainer != null) {
            this.activeContainer.a(this);
        }
    }

    public boolean K() {
        if (this.sleeping) {
            return false;
        }

        // Uberbukkit - different conditions for suffocation pre-b1.6
        if (this instanceof EntityPlayer && ((EntityPlayer) this).netServerHandler.networkManager.pvn < 12) {
            int i = MathHelper.floor(this.locX);
            int j = MathHelper.floor(this.locY + (double) this.t());
            int k = MathHelper.floor(this.locZ);

            return this.world.e(i, j, k);
        }

        return super.K();
    }

    public EnumBedError a(int i, int j, int k) {
        if (!this.world.isStatic) {
            if (this.isSleeping() || !this.T()) {
                return EnumBedError.OTHER_PROBLEM;
            }

            if (this.world.worldProvider.c) {
                return EnumBedError.NOT_POSSIBLE_HERE;
            }

            if (this.world.d()) {
                return EnumBedError.NOT_POSSIBLE_NOW;
            }

            if (Math.abs(this.locX - (double) i) > 3.0D || Math.abs(this.locY - (double) j) > 2.0D || Math.abs(this.locZ - (double) k) > 3.0D) {
                return EnumBedError.TOO_FAR_AWAY;
            }
        }

        // CraftBukkit start
        if (this.getBukkitEntity() instanceof Player) {
            Player player = (Player) this.getBukkitEntity();
            org.bukkit.block.Block bed = this.world.getWorld().getBlockAt(i, j, k);

            PlayerBedEnterEvent event = new PlayerBedEnterEvent(player, bed);
            this.world.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) {
                return EnumBedError.OTHER_PROBLEM;
            }
        }
        // CraftBukkit end

        this.b(0.2F, 0.2F);
        this.height = 0.2F;
        if (this.world.isLoaded(i, j, k)) {
            int l = this.world.getData(i, j, k);
            int i1 = BlockBed.c(l);
            float f = 0.5F;
            float f1 = 0.5F;

            switch (i1) {
                case 0:
                    f1 = 0.9F;
                    break;

                case 1:
                    f = 0.1F;
                    break;

                case 2:
                    f1 = 0.1F;
                    break;

                case 3:
                    f = 0.9F;
            }

            this.e(i1);
            this.setPosition((double) ((float) i + f), (double) ((float) j + 0.9375F), (double) ((float) k + f1));
        } else {
            this.setPosition((double) ((float) i + 0.5F), (double) ((float) j + 0.9375F), (double) ((float) k + 0.5F));
        }

        this.sleeping = true;
        this.sleepTicks = 0;
        this.A = new ChunkCoordinates(i, j, k);
        this.motX = this.motZ = this.motY = 0.0D;
        if (!this.world.isStatic) {
            this.world.everyoneSleeping();
        }
        
        // MCOSE: Sweet Dreams achievement for sleeping in a bed
        this.a(AchievementList.sleepInBed, 1);

        return EnumBedError.OK;
    }

    private void e(int i) {
        this.B = 0.0F;
        this.C = 0.0F;
        switch (i) {
            case 0:
                this.C = -1.8F;
                break;

            case 1:
                this.B = 1.8F;
                break;

            case 2:
                this.C = 1.8F;
                break;

            case 3:
                this.B = -1.8F;
        }
    }

    public void a(boolean flag, boolean flag1, boolean flag2) {
        this.b(0.6F, 1.8F);
        this.s();
        ChunkCoordinates chunkcoordinates = this.A;
        ChunkCoordinates chunkcoordinates1 = this.A;

        if (chunkcoordinates != null && this.world.getTypeId(chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z) == Block.BED.id) {
            BlockBed.a(this.world, chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z, false);
            chunkcoordinates1 = BlockBed.f(this.world, chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z, 0);
            if (chunkcoordinates1 == null) {
                chunkcoordinates1 = new ChunkCoordinates(chunkcoordinates.x, chunkcoordinates.y + 1, chunkcoordinates.z);
            }

            this.setPosition((double) ((float) chunkcoordinates1.x + 0.5F), (double) ((float) chunkcoordinates1.y + this.height + 0.1F), (double) ((float) chunkcoordinates1.z + 0.5F));
        }

        this.sleeping = false;
        if (!this.world.isStatic && flag1) {
            this.world.everyoneSleeping();
        }

        // CraftBukkit start
        if (this.getBukkitEntity() instanceof Player) {
            Player player = (Player) this.getBukkitEntity();

            org.bukkit.block.Block bed;
            if (chunkcoordinates != null) {
                bed = this.world.getWorld().getBlockAt(chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z);
            } else {
                bed = this.world.getWorld().getBlockAt(player.getLocation());
            }

            PlayerBedLeaveEvent event = new PlayerBedLeaveEvent(player, bed);
            this.world.getServer().getPluginManager().callEvent(event);
        }
        // CraftBukkit end

        if (flag) {
            this.sleepTicks = 0;
        } else {
            this.sleepTicks = 100;
        }

        if (flag2 && PoseidonConfig.getInstance().getBoolean("version.mechanics.beds_set_spawnpoint", true)) {
            this.a(this.A);
        }
    }

    private boolean o() {
        return this.world.getTypeId(this.A.x, this.A.y, this.A.z) == Block.BED.id;
    }

    public static ChunkCoordinates getBed(World world, ChunkCoordinates chunkcoordinates) {
        IChunkProvider ichunkprovider = world.o();

        ichunkprovider.getChunkAt(chunkcoordinates.x - 3 >> 4, chunkcoordinates.z - 3 >> 4);
        ichunkprovider.getChunkAt(chunkcoordinates.x + 3 >> 4, chunkcoordinates.z - 3 >> 4);
        ichunkprovider.getChunkAt(chunkcoordinates.x - 3 >> 4, chunkcoordinates.z + 3 >> 4);
        ichunkprovider.getChunkAt(chunkcoordinates.x + 3 >> 4, chunkcoordinates.z + 3 >> 4);
        if (world.getTypeId(chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z) != Block.BED.id) {
            return null;
        } else {
            ChunkCoordinates chunkcoordinates1 = BlockBed.f(world, chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z, 0);

            return chunkcoordinates1;
        }
    }

    public boolean isSleeping() {
        return this.sleeping;
    }

    public boolean isDeeplySleeping() {
        return this.sleeping && this.sleepTicks >= 100;
    }

    public void a(String s) {
    }

    public ChunkCoordinates getBed() {
        return this.b;
    }

    public void a(ChunkCoordinates chunkcoordinates) {
        if (chunkcoordinates != null) {
            this.b = new ChunkCoordinates(chunkcoordinates);
            this.spawnWorld = this.world.worldData.name; // CraftBukkit
        } else {
            this.b = null;
        }
    }

    public void a(Statistic statistic) {
        this.a(statistic, 1);
    }

    public void a(Statistic statistic, int i) {
    }

    protected void O() {
        if (this instanceof EntityPlayer) {
            PlayerJumpEvent jumpEvent = new PlayerJumpEvent((EntityPlayer) this, this.world, 0.42D);
            EventBus.global().publish(jumpEvent);
            if (jumpEvent.isCancelled()) {
                return;
            }

            super.O();
            this.motY = jumpEvent.getJumpVelocity();
            this.a(StatisticList.u, 1);
            return;
        }

        super.O();
        this.a(StatisticList.u, 1);
    }

    public void a(float f, float f1) {
        if (this instanceof EntityPlayer) {
            PlayerMoveEvent moveEvent = new PlayerMoveEvent(
                    (EntityPlayer) this,
                    this.world,
                    this.locX,
                    this.locY,
                    this.locZ,
                    f,
                    f1,
                    PlayerCapabilityRegistryApi.isFlying((EntityPlayer) this)
            );
            EventBus.global().publish(moveEvent);
            if (moveEvent.isCancelled()) {
                return;
            }
            f = moveEvent.getStrafe();
            f1 = moveEvent.getForward();
        }

        double d0 = this.locX;
        double d1 = this.locY;
        double d2 = this.locZ;

        super.a(f, f1);
        this.h(this.locX - d0, this.locY - d1, this.locZ - d2);
    }

    private void h(double d0, double d1, double d2) {
        if (this.vehicle == null) {
            int i;

            if (this.a(Material.WATER)) {
                i = Math.round(MathHelper.a(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);
                if (i > 0) {
                    this.a(StatisticList.q, i);
                }
            } else if (this.ad()) {
                i = Math.round(MathHelper.a(d0 * d0 + d2 * d2) * 100.0F);
                if (i > 0) {
                    this.a(StatisticList.m, i);
                }
            } else if (this.p()) {
                if (d1 > 0.0D) {
                    this.a(StatisticList.o, (int) Math.round(d1 * 100.0D));
                }
            } else if (this.onGround) {
                i = Math.round(MathHelper.a(d0 * d0 + d2 * d2) * 100.0F);
                if (i > 0) {
                    this.a(StatisticList.l, i);
                }
            } else {
                i = Math.round(MathHelper.a(d0 * d0 + d2 * d2) * 100.0F);
                if (i > 25) {
                    this.a(StatisticList.p, i);
                }
            }
        }
    }

    private void i(double d0, double d1, double d2) {
        if (this.vehicle != null) {
            int i = Math.round(MathHelper.a(d0 * d0 + d1 * d1 + d2 * d2) * 100.0F);

            if (i > 0) {
                if (this.vehicle instanceof EntityMinecart) {
                    this.a(StatisticList.r, i);
                    if (this.c == null) {
                        this.c = new ChunkCoordinates(MathHelper.floor(this.locX), MathHelper.floor(this.locY), MathHelper.floor(this.locZ));
                    } else if (this.c.a(MathHelper.floor(this.locX), MathHelper.floor(this.locY), MathHelper.floor(this.locZ)) >= 1000.0D) {
                        this.a(AchievementList.q, 1);
                    }
                } else if (this.vehicle instanceof EntityBoat) {
                    this.a(StatisticList.s, i);
                    if (this.startBoatRidingCoordinate == null) {
                        this.startBoatRidingCoordinate = new ChunkCoordinates(MathHelper.floor(this.locX), MathHelper.floor(this.locY), MathHelper.floor(this.locZ));
                    } else if (this.startBoatRidingCoordinate.a(MathHelper.floor(this.locX), MathHelper.floor(this.locY), MathHelper.floor(this.locZ)) >= 1000.0D) {
                        this.a(AchievementList.boatTravel, 1);
                    }
                } else if (this.vehicle instanceof EntityPig) {
                    this.a(StatisticList.t, i);
                }
            }
        }
    }

    protected void a(float f) {
        if (f >= 2.0F) {
            this.a(StatisticList.n, (int) Math.round((double) f * 100.0D));
        }

        if (f > 0.75F && !this.world.isStatic) {
            int i = MathHelper.floor(this.locX);
            int j = MathHelper.floor(this.locY - 0.20000000298023224D - (double) this.height);
            int k = MathHelper.floor(this.locZ);

            if (this.world.getTypeId(i, j, k) == Block.SOIL.id || this.world.getTypeId(i, j, k) == Block.CROPS.id) {
                if (this.world.getTypeId(i, j, k) == Block.CROPS.id) {
                    --j;
                    if (this.world.getTypeId(i, j, k) != Block.SOIL.id) {
                        ++j;
                    }
                }

                if (this.world.getTypeId(i, j, k) == Block.SOIL.id && uk.betacraft.uberbukkit.UberbukkitConfig.getInstance().getBoolean("mechanics.farmland_trampling", true) && !this.isSneaking()) {
                    boolean modern = uk.betacraft.uberbukkit.UberbukkitConfig.getInstance().getBoolean("mechanics.modern_farmland", true);
                    // MCOSE - Don't trample unless jump/fall distance > 0.5 in modern mode
                    if (!modern || f > 0.5F) {
                        // CraftBukkit start - Interact Soil
                        org.bukkit.event.Cancellable cancellable = org.bukkit.craftbukkit.event.CraftEventFactory.callPlayerInteractEvent(this, org.bukkit.event.block.Action.PHYSICAL, i, j, k, -1, null);

                        if (!cancellable.isCancelled()) {
                            this.world.setTypeId(i, j, k, Block.DIRT.id);
                        }
                        // CraftBukkit end
                    } else if (modern) {
                        // Resync client to prevent visual prediction glitch
                        if (this instanceof EntityPlayer) {
                            ((EntityPlayer) this).netServerHandler.sendPacket(new net.minecraft.server.Packet53BlockChange(i, j, k, this.world));
                        }
                    }
                }
            }
        }

        super.a(f);
    }

    public void a(EntityLiving entityliving) {
        if (entityliving instanceof EntityMonster) {
            this.a((Statistic) AchievementList.s);
        }
    }

    public void P() {
        if (this.D > 0) {
            this.D = 10;
        } else {
            this.E = true;
        }
    }

    private PlayerDamageType resolveDamageType(Entity directSource, Entity resolvedSource) {
        if (resolvedSource instanceof EntityArrow || directSource instanceof EntityArrow) {
            return PlayerDamageType.PROJECTILE;
        }

        if (resolvedSource instanceof EntityLiving) {
            return PlayerDamageType.MELEE;
        }

        if (this.fallDistance > 0.0F && directSource == null) {
            return PlayerDamageType.FALL;
        }

        if (this.locY < -64.0D) {
            return PlayerDamageType.VOID;
        }

        if (this.ae()) {
            return PlayerDamageType.LAVA;
        }

        if (this.fireTicks > 0) {
            return PlayerDamageType.FIRE;
        }

        if (this.airTicks <= 0) {
            return PlayerDamageType.DROWNING;
        }

        return PlayerDamageType.GENERIC;
    }

    /**
     * MCOSE: Override void kill to apply void damage instead of instant death.
     * Like modern Minecraft, creative players still take void damage so they
     * don't fall infinitely. 4 damage per tick until death.
     */
    @Override
    protected void Y() {
        // Apply 4 damage per tick (bypasses creative invulnerability)
        // This matches modern Minecraft behavior where even creative players die in void
        this.health -= 4;
        if (this.health <= 0) {
            this.die();
        }
    }

    private void checkArmorAchievements() {
        ItemStack[] armor = this.inventory.armor;
        if (armor[0] == null || armor[1] == null || armor[2] == null || armor[3] == null) {
            return;
        }

        boolean fullIron = this.isArmorSet(armor, Item.IRON_BOOTS, Item.IRON_LEGGINGS, Item.IRON_CHESTPLATE, Item.IRON_HELMET);
        if (fullIron) {
            this.a(AchievementList.fullIron, 1);
        }

        boolean fullDiamond = this.isArmorSet(armor, Item.DIAMOND_BOOTS, Item.DIAMOND_LEGGINGS, Item.DIAMOND_CHESTPLATE, Item.DIAMOND_HELMET);
        if (fullDiamond) {
            this.a(AchievementList.fullDiamond, 1);
        }
    }

    private boolean isArmorSet(ItemStack[] armor, Item boots, Item leggings, Item chest, Item helmet) {
        return armor[0].getItem() == boots
                && armor[1].getItem() == leggings
                && armor[2].getItem() == chest
                && armor[3].getItem() == helmet;
    }
    
    /**
     * MCOSE: Track wool colors collected for Rainbow Collection achievement.
     * @param color The wool color metadata (0-15)
     */
    public void trackWoolColor(int color) {
        if (color >= 0 && color < 16) {
            this.woolColorsCollected |= (1 << color);
            // Check if all 16 colors collected (bitmask = 0xFFFF = 65535)
            if (this.woolColorsCollected == 0xFFFF) {
                this.a(AchievementList.rainbowWool, 1);
            }
        }
    }
}

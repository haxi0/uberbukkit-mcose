package net.minecraft.server;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import uk.betacraft.uberbukkit.UberbukkitConfig;

public class BlockTNT extends Block {

    private static final long TNT_OWNER_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final Map<String, TNTPlacementOwner> placedOwners = new HashMap<String, TNTPlacementOwner>();

    public BlockTNT(int i, int j) {
        super(i, j, Material.TNT);
    }

    public int a(int i) {
        return i == 0 ? this.textureId + 2 : (i == 1 ? this.textureId + 1 : this.textureId);
    }

    public void c(World world, int i, int j, int k) {
        super.c(world, i, j, k);
        if (world.isBlockIndirectlyPowered(i, j, k)) {
            this.postBreak(world, i, j, k, 1);
            world.setTypeId(i, j, k, 0);
        }
    }

    public void doPhysics(World world, int i, int j, int k, int l) {
        if (l > 0 && Block.byId[l].isPowerSource() && world.isBlockIndirectlyPowered(i, j, k)) {
            this.postBreak(world, i, j, k, 1);
            world.setTypeId(i, j, k, 0);
        }
    }

    public int a(Random random) {
        return 0;
    }

    public void d(World world, int i, int j, int k) {
        if (!world.isStatic) {
            EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double) ((float) i + 0.5F), (double) ((float) j + 0.5F), (double) ((float) k + 0.5F));
            entitytntprimed.sourceName = consumePlacedBy(world, i, j, k);

            entitytntprimed.fuseTicks = world.random.nextInt(entitytntprimed.fuseTicks / 4) + entitytntprimed.fuseTicks / 8;
            world.addEntity(entitytntprimed);
        }
    }

    public void postBreak(World world, int i, int j, int k, int l) {
        postBreak(world, i, j, k, l, null);
    }

    // UberBukkit - Overload to track who ignited the TNT
    public void postBreak(World world, int i, int j, int k, int l, EntityLiving igniter) {
        postBreak(world, i, j, k, l, igniter, sourceNameFromLiving(igniter));
    }

    // UberBukkit - Overload to track igniter name when no live entity source is available
    public void postBreak(World world, int i, int j, int k, int l, EntityLiving igniter, String igniterName) {
        if (!world.isStatic) {
            if ((l & 1) == 0) {
                clearPlacedBy(world, i, j, k);
                this.a(world, i, j, k, new ItemStack(Block.TNT.id, 1, 0));
            } else {
                EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double) ((float) i + 0.5F), (double) ((float) j + 0.5F), (double) ((float) k + 0.5F));
                entitytntprimed.source = igniter; // UberBukkit - Track the igniter
                String placedBy = consumePlacedBy(world, i, j, k);
                if (igniterName != null) {
                    entitytntprimed.sourceName = igniterName;
                } else {
                    entitytntprimed.sourceName = placedBy;
                }
                world.addEntity(entitytntprimed);
                world.makeSound(entitytntprimed, "random.fuse", 1.0F, 1.0F);
            }
        }
    }

    public void b(World world, int i, int j, int k, EntityHuman entityhuman) {
        boolean shouldIgnite = false;
        
        // uberbukkit
        if (!UberbukkitConfig.getInstance().getBoolean("mechanics.tnt_require_lighter", true)) {
            shouldIgnite = true;
        }

        if ((entityhuman.G() != null && entityhuman.G().id == Item.FLINT_AND_STEEL.id)) {
            shouldIgnite = true;
        }

        if (shouldIgnite) {
            // UberBukkit - Directly spawn TNT with the igniter tracked
            world.setTypeId(i, j, k, 0); // Remove the TNT block
            EntityTNTPrimed entitytntprimed = new EntityTNTPrimed(world, (double) ((float) i + 0.5F), (double) ((float) j + 0.5F), (double) ((float) k + 0.5F));
            entitytntprimed.source = entityhuman; // Track who lit the TNT
            String sourceName = sourceNameFromLiving(entityhuman);
            if (sourceName == null) {
                sourceName = consumePlacedBy(world, i, j, k);
            } else {
                clearPlacedBy(world, i, j, k);
            }
            entitytntprimed.sourceName = sourceName;
            world.addEntity(entitytntprimed);
            world.makeSound(entitytntprimed, "random.fuse", 1.0F, 1.0F);
            // MCOSE: KABOOM! achievement for igniting TNT
            entityhuman.a(AchievementList.explosion, 1);
            return; // Don't call super - we've handled it
        }

        super.b(world, i, j, k, entityhuman);
    }

    public boolean interact(World world, int i, int j, int k, EntityHuman entityhuman) {
        return super.interact(world, i, j, k, entityhuman);
    }

    public static synchronized void recordPlacedBy(World world, int x, int y, int z, EntityHuman player) {
        if (world == null || player == null || player.name == null) {
            return;
        }
        pruneOldOwners();
        placedOwners.put(key(world, x, y, z), new TNTPlacementOwner(player.name));
    }

    public static synchronized void clearPlacedBy(World world, int x, int y, int z) {
        if (world == null) {
            return;
        }
        placedOwners.remove(key(world, x, y, z));
    }

    public static synchronized String consumePlacedBy(World world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        pruneOldOwners();
        TNTPlacementOwner owner = placedOwners.remove(key(world, x, y, z));
        if (owner == null) {
            return null;
        }
        return owner.name;
    }

    public static String sourceNameFromLiving(EntityLiving living) {
        if (living instanceof EntityHuman) {
            return ((EntityHuman) living).name;
        }
        return null;
    }

    private static String key(World world, int x, int y, int z) {
        String worldName;
        try {
            worldName = world.getWorld() == null ? "unknown" : world.getWorld().getName();
        } catch (Throwable ignored) {
            worldName = "unknown";
        }
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    private static void pruneOldOwners() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, TNTPlacementOwner>> iterator = placedOwners.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TNTPlacementOwner> entry = iterator.next();
            if ((now - entry.getValue().createdAt) > TNT_OWNER_MAX_AGE_MS) {
                iterator.remove();
            }
        }
    }

    private static final class TNTPlacementOwner {
        private final String name;
        private final long createdAt;

        private TNTPlacementOwner(String name) {
            this.name = name;
            this.createdAt = System.currentTimeMillis();
        }
    }
}

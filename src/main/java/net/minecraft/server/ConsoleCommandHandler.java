package net.minecraft.server;

import org.bukkit.craftbukkit.command.ServerCommandListener;
import org.bukkit.craftbukkit.entity.CraftPlayer;

import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

// CraftBukkit start
// CraftBukkit end

public class ConsoleCommandHandler {

    private static Logger a = Logger.getLogger("Minecraft");
    private static final int RELIGHT_MAX_RADIUS = 8;
    private static final int RELIGHT_MAX_LIGHTING_PASSES = 4096;
    private MinecraftServer server;
    private ICommandListener listener; // CraftBukkit

    public ConsoleCommandHandler(MinecraftServer minecraftserver) {
        this.server = minecraftserver;
    }

    // Craftbukkit start
    private boolean hasPermission(ICommandListener listener, String perm) {
        if (listener instanceof ServerCommandListener) {
            ServerCommandListener serv = (ServerCommandListener) listener;
            return serv.getSender().hasPermission(perm);
        } else if (listener instanceof NetServerHandler) {
            NetServerHandler net = (NetServerHandler) listener;
            return net.getPlayer().hasPermission(perm);
        } else if ((listener instanceof ServerGUI) || (listener instanceof MinecraftServer)) {
            return server.console.hasPermission(perm);
        }

        return false;
    }

    private boolean checkPermission(ICommandListener listener, String command) {
        if (hasPermission(listener, "bukkit.command." + command)) {
            return true;
        } else {
            listener.sendMessage("I'm sorry, Dave, but I cannot let you do that.");
            return false;
        }
    }
    // Craftbukkit end

    public boolean handle(ServerCommand servercommand) { // CraftBukkit - returns boolean
        String s = servercommand.command;
        ICommandListener icommandlistener = servercommand.b;
        String s1 = icommandlistener.getName();
        this.listener = icommandlistener; // CraftBukkit
        ServerConfigurationManager serverconfigurationmanager = this.server.serverConfigurationManager;

        if (!s.toLowerCase().startsWith("help") && !s.toLowerCase().startsWith("?")) {
            if (s.toLowerCase().startsWith("list")) {
                if (!checkPermission(listener, "list")) return true; // Craftbukkit
                icommandlistener.sendMessage("Connected players: " + serverconfigurationmanager.c());
            } else if (s.toLowerCase().startsWith("stop")) {
                if (!checkPermission(listener, "stop")) return true; // Craftbukkit
                this.print(s1, "Stopping the server..");
                this.server.a();
            } else {
                int i;
                WorldServer worldserver;

                if (s.toLowerCase().startsWith("save-all")) {
                    if (!checkPermission(listener, "save.perform")) return true; // Craftbukkit
                    this.print(s1, "Forcing save..");
                    if (serverconfigurationmanager != null) {
                        serverconfigurationmanager.savePlayers();
                    }

                    // CraftBukkit start
                    for (i = 0; i < this.server.worlds.size(); ++i) {
                        worldserver = this.server.worlds.get(i);
                        boolean save = worldserver.canSave;
                        worldserver.canSave = false;
                        worldserver.save(true, (IProgressUpdate) null);
                        worldserver.canSave = save;
                    }
                    // CraftBukkit end

                    this.print(s1, "Save complete.");
                } else if (s.toLowerCase().startsWith("save-off")) {
                    if (!checkPermission(listener, "save.disable")) return true; // Craftbukkit
                    this.print(s1, "Disabling level saving..");

                    for (i = 0; i < this.server.worlds.size(); ++i) { // CraftBukkit
                        worldserver = this.server.worlds.get(i); // CraftBukkit
                        worldserver.canSave = true;
                    }
                } else if (s.toLowerCase().startsWith("save-on")) {
                    if (!checkPermission(listener, "save.enable")) return true; // Craftbukkit
                    this.print(s1, "Enabling level saving..");

                    for (i = 0; i < this.server.worlds.size(); ++i) { // CraftBukkit
                        worldserver = this.server.worlds.get(i); // CraftBukkit
                        worldserver.canSave = false;
                    }
                } else {
                    String s2;

                    if (s.toLowerCase().startsWith("op ")) {
                        if (!checkPermission(listener, "op.give")) return true; // Craftbukkit
                        s2 = s.substring(s.indexOf(" ")).trim();
                        serverconfigurationmanager.e(s2);
                        this.print(s1, "Opping " + s2);
                        serverconfigurationmanager.a(s2, "\u00A7eYou are now op!");
                    } else if (s.toLowerCase().startsWith("deop ")) {
                        if (!checkPermission(listener, "op.take")) return true; // Craftbukkit
                        s2 = s.substring(s.indexOf(" ")).trim();
                        serverconfigurationmanager.f(s2);
                        serverconfigurationmanager.a(s2, "\u00A7eYou are no longer op!");
                        this.print(s1, "De-opping " + s2);
                    } else if (s.toLowerCase().startsWith("ban-ip ")) {
                        if (!checkPermission(listener, "ban.ip")) return true; // Craftbukkit
                        s2 = s.substring(s.indexOf(" ")).trim();
                        serverconfigurationmanager.c(s2);
                        this.print(s1, "Banning ip " + s2);
                    } else if (s.toLowerCase().startsWith("pardon-ip ")) {
                        if (!checkPermission(listener, "unban.ip")) return true; // Craftbukkit
                        s2 = s.substring(s.indexOf(" ")).trim();
                        serverconfigurationmanager.d(s2);
                        this.print(s1, "Pardoning ip " + s2);
                    } else {
                        EntityPlayer entityplayer;

                        if (s.toLowerCase().startsWith("ban ")) {
                            if (!checkPermission(listener, "ban.player")) return true; // Craftbukkit
                            s2 = s.substring(s.indexOf(" ")).trim();
                            serverconfigurationmanager.a(s2);
                            this.print(s1, "Banning " + s2);
                            entityplayer = serverconfigurationmanager.i(s2);
                            if (entityplayer != null) {
                                entityplayer.netServerHandler.disconnect("Banned by admin");
                            }
                        } else if (s.toLowerCase().startsWith("pardon ")) {
                            if (!checkPermission(listener, "unban.player")) return true; // Craftbukkit
                            s2 = s.substring(s.indexOf(" ")).trim();
                            serverconfigurationmanager.b(s2);
                            this.print(s1, "Pardoning " + s2);
                        } else {
                            int j;

                            if (s.toLowerCase().startsWith("kick ")) {
                                if (!checkPermission(listener, "kick")) return true; // Craftbukkit
                                // CraftBukkit start - Add kick message compatibility
                                String[] parts = s.split(" ");
                                s2 = parts.length >= 2 ? parts[1] : "";
                                // CraftBukkit end
                                entityplayer = null;

                                for (j = 0; j < serverconfigurationmanager.players.size(); ++j) {
                                    EntityPlayer entityplayer1 = (EntityPlayer) serverconfigurationmanager.players.get(j);

                                    if (entityplayer1.name.equalsIgnoreCase(s2)) {
                                        entityplayer = entityplayer1;
                                    }
                                }

                                if (entityplayer != null) {
                                    entityplayer.netServerHandler.disconnect("Kicked by admin");
                                    this.print(s1, "Kicking " + entityplayer.name);
                                } else {
                                    icommandlistener.sendMessage("Can\'t find user " + s2 + ". No kick.");
                                }
                            } else {
                                EntityPlayer entityplayer2;
                                String[] astring;

                                if (s.toLowerCase().startsWith("tp ")) {
                                    if (!checkPermission(listener, "teleport")) return true; // Craftbukkit
                                    astring = s.split(" ");
                                    if (astring.length == 3) {
                                        entityplayer = serverconfigurationmanager.i(astring[1]);
                                        entityplayer2 = serverconfigurationmanager.i(astring[2]);
                                        if (entityplayer == null) {
                                            icommandlistener.sendMessage("Can\'t find user " + astring[1] + ". No tp.");
                                        } else if (entityplayer2 == null) {
                                            icommandlistener.sendMessage("Can\'t find user " + astring[2] + ". No tp.");
                                        } else if (entityplayer.dimension != entityplayer2.dimension) {
                                            icommandlistener.sendMessage("User " + astring[1] + " and " + astring[2] + " are in different dimensions. No tp.");
                                        } else {
                                            entityplayer.netServerHandler.a(entityplayer2.locX, entityplayer2.locY, entityplayer2.locZ, entityplayer2.yaw, entityplayer2.pitch);
                                            this.print(s1, "Teleporting " + astring[1] + " to " + astring[2] + ".");
                                        }
                                    } else {
                                        icommandlistener.sendMessage("Syntax error, please provice a source and a target.");
                                    }
                                } else {
                                    String s3;
                                    int k;

                                    if (s.toLowerCase().startsWith("give ")) {
                                        if (!checkPermission(listener, "give")) return true; // Craftbukkit
                                        astring = s.split(" ");
                                        if (astring.length != 3 && astring.length != 4) {
                                            return true; // CraftBukkit
                                        }

                                        s3 = astring[1];
                                        entityplayer2 = serverconfigurationmanager.i(s3);
                                        if (entityplayer2 != null) {
                                            String itemArg = astring[2];
                                            if (net.minecraft.server.registry.RegistryKeyPolicy.looksNumeric(itemArg)) {
                                                icommandlistener.sendMessage("Numeric item IDs are disabled. Use item keys.");
                                                return true;
                                            }
                                            String normalized = net.minecraft.server.registry.ItemRegistry.normalizeInputIdentifier(itemArg);
                                            if (normalized == null) {
                                                icommandlistener.sendMessage("Unknown item key: " + itemArg);
                                                return true;
                                            }
                                            Item giveItem = net.minecraft.server.registry.ItemRegistry.get(new net.minecraft.server.util.ResourceLocation(normalized));
                                            if (giveItem == null) {
                                                icommandlistener.sendMessage("Unknown item key: " + itemArg);
                                                return true;
                                            }
                                            k = giveItem.id;
                                            this.print(s1, "Giving " + entityplayer2.name + " some " + normalized);
                                            int l = 1;

                                            if (astring.length > 3) {
                                                l = this.a(astring[3], 1);
                                            }

                                            if (l < 1) {
                                                l = 1;
                                            }

                                            if (l > 64) {
                                                l = 64;
                                            }

                                            int dmg = net.minecraft.server.registry.ItemRegistry.getDefaultDamage(normalized);
                                            if (dmg < 0) dmg = 0;
                                            entityplayer2.b(new ItemStack(k, l, dmg));
                                        } else {
                                            icommandlistener.sendMessage("Can\'t find user " + s3);
                                        }
                                    } else if (s.toLowerCase().startsWith("time ")) {
                                        astring = s.split(" ");
                                        if (astring.length != 3) {
                                            return true; // CraftBukkit
                                        }

                                        s3 = astring[1];

                                        try {
                                            j = Integer.parseInt(astring[2]);
                                            WorldServer worldserver1;

                                            if ("add".equalsIgnoreCase(s3)) {
                                                if (!checkPermission(listener, "time.add")) return true; // Craftbukkit
                                                for (k = 0; k < this.server.worlds.size(); ++k) { // CraftBukkit
                                                    worldserver1 = this.server.worlds.get(k); // CraftBukkit
                                                    worldserver1.setTimeAndFixTicklists(worldserver1.getTime() + (long) j);
                                                }

                                                this.print(s1, "Added " + j + " to time");
                                            } else if ("set".equalsIgnoreCase(s3)) {
                                                if (!checkPermission(listener, "time.set")) return true; // Craftbukkit
                                                for (k = 0; k < this.server.worlds.size(); ++k) { // CraftBukkit
                                                    worldserver1 = this.server.worlds.get(k); // CraftBukkit
                                                    worldserver1.setTimeAndFixTicklists((long) j);
                                                }

                                                this.print(s1, "Set time to " + j);
                                            } else {
                                                icommandlistener.sendMessage("Unknown method, use either \"add\" or \"set\"");
                                            }
                                        } catch (NumberFormatException numberformatexception1) {
                                            icommandlistener.sendMessage("Unable to convert time value, " + astring[2]);
                                        }
                    } else if (s.toLowerCase().startsWith("profile")) {
                        if (!checkPermission(listener, "profile")) return true;
                        handleProfileCommand(s, icommandlistener, s1);
                    } else if (s.toLowerCase().startsWith("relight")) {
                        if (!checkPermission(listener, "relight")) return true;
                        handleRelightCommand(s, icommandlistener, s1, serverconfigurationmanager);
                    } else if (s.toLowerCase().startsWith("vanish")) {
                        // Toggle vanish state for the executor if it is a player; otherwise require a target
                        org.bukkit.command.CommandSender sender = null;
                        if (this.listener instanceof ServerCommandListener) {
                            sender = ((ServerCommandListener) this.listener).getSender();
                        }
                        if (sender instanceof org.bukkit.entity.Player) {
                            org.bukkit.entity.Player bp = (org.bukkit.entity.Player) sender;
                            boolean vanished = VanishAPI.toggle(bp);
                            sender.sendMessage(vanished ? "You have vanished." : "You are now visible.");
                        } else {
                            String[] parts = s.split(" ");
                            if (parts.length < 2) {
                                icommandlistener.sendMessage("Usage: vanish <player>");
                            } else {
                                EntityPlayer ep = serverconfigurationmanager.i(parts[1]);
                                if (ep == null) {
                                    icommandlistener.sendMessage("Can't find user " + parts[1]);
                                } else {
                                    org.bukkit.entity.Player bp = (org.bukkit.entity.Player) ep.getBukkitEntity();
                                    boolean vanished = VanishAPI.toggle(bp);
                                    this.print(s1, (vanished ? "Vanished " : "Revealed ") + bp.getName());
                                }
                            }
                        }
                    } else if (s.toLowerCase().startsWith("say ")) {
                                        if (!checkPermission(listener, "say")) return true; // Craftbukkit
                                        s = s.substring(s.indexOf(" ")).trim();
                                        a.info("[" + s1 + "] " + s);
                                        serverconfigurationmanager.sendAll(new Packet3Chat("\u00A7d[Server] " + s));
                                    } else if (s.toLowerCase().startsWith("tell ")) {
                                        if (!checkPermission(listener, "tell")) return true; // Craftbukkit
                                        astring = s.split(" ");
                                        if (astring.length >= 3) {
                                            s = s.substring(s.indexOf(" ")).trim();
                                            s = s.substring(s.indexOf(" ")).trim();
                                            a.info("[" + s1 + "->" + astring[1] + "] " + s);
                                            s = "\u00A77" + s1 + " whispers " + s;
                                            a.info(s);
                                            if (!serverconfigurationmanager.a(astring[1], (Packet) (new Packet3Chat(s)))) {
                                                icommandlistener.sendMessage("There\'s no player by that name online.");
                                            }
                                        }
                                    } else if (s.toLowerCase().startsWith("whitelist ")) {
                                        this.a(s1, s, icommandlistener);
                                    } else {
                                        icommandlistener.sendMessage("Unknown console command. Type \"help\" for help."); // CraftBukkit
                                        return false; // CraftBukkit
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (!checkPermission(listener, "help")) return true; // Craftbukkit
            this.a(icommandlistener);
        }

        return true; // CraftBukkit
    }

    private void a(String s, String s1, ICommandListener icommandlistener) {
        String[] astring = s1.split(" ");
        this.listener = icommandlistener; // CraftBukkit

        if (astring.length >= 2) {
            String s2 = astring[1].toLowerCase();

            if ("on".equals(s2)) {
                if (!checkPermission(listener, "whitelist.enable")) return; // Craftbukkit
                this.print(s, "Turned on white-listing");
                this.server.propertyManager.b("white-list", true);
            } else if ("off".equals(s2)) {
                if (!checkPermission(listener, "whitelist.disable")) return; // Craftbukkit
                this.print(s, "Turned off white-listing");
                this.server.propertyManager.b("white-list", false);
            } else if ("list".equals(s2)) {
                if (!checkPermission(listener, "whitelist.list")) return; // Craftbukkit
                Set set = this.server.serverConfigurationManager.e();
                String s3 = "";

                String s4;

                for (Iterator iterator = set.iterator(); iterator.hasNext(); s3 = s3 + s4 + " ") {
                    s4 = (String) iterator.next();
                }

                icommandlistener.sendMessage("White-listed players: " + s3);
            } else {
                String s5;

                if ("add".equals(s2) && astring.length == 3) {
                    if (!checkPermission(listener, "whitelist.add")) return; // Craftbukkit
                    s5 = astring[2].toLowerCase();
                    this.server.serverConfigurationManager.k(s5);
                    this.print(s, "Added " + s5 + " to white-list");
                } else if ("remove".equals(s2) && astring.length == 3) {
                    if (!checkPermission(listener, "whitelist.remove")) return; // Craftbukkit
                    s5 = astring[2].toLowerCase();
                    this.server.serverConfigurationManager.l(s5);
                    this.print(s, "Removed " + s5 + " from white-list");
                } else if ("reload".equals(s2)) {
                    if (!checkPermission(listener, "whitelist.reload")) return; // Craftbukkit
                    this.server.serverConfigurationManager.f();
                    this.print(s, "Reloaded white-list from file");
                }
            }
        }
    }

    private void a(ICommandListener icommandlistener) {
        icommandlistener.sendMessage("To run the server without a gui, start it like this:");
        icommandlistener.sendMessage("   java -Xmx1024M -Xms1024M -jar minecraft_server.jar nogui");
        icommandlistener.sendMessage("Console commands:");
        icommandlistener.sendMessage("   help  or  ?               shows this message");
        icommandlistener.sendMessage("   kick <player>             removes a player from the server");
        icommandlistener.sendMessage("   ban <player>              bans a player from the server");
        icommandlistener.sendMessage("   pardon <player>           pardons a banned player so that they can connect again");
        icommandlistener.sendMessage("   ban-ip <ip>               bans an IP address from the server");
        icommandlistener.sendMessage("   pardon-ip <ip>            pardons a banned IP address so that they can connect again");
        icommandlistener.sendMessage("   op <player>               turns a player into an op");
        icommandlistener.sendMessage("   deop <player>             removes op status from a player");
        icommandlistener.sendMessage("   tp <player1> <player2>    moves one player to the same location as another player");
        icommandlistener.sendMessage("   give <player> <item> [num]  gives a player a resource");
        icommandlistener.sendMessage("   tell <player> <message>   sends a private message to a player");
        icommandlistener.sendMessage("   stop                      gracefully stops the server");
        icommandlistener.sendMessage("   save-all                  forces a server-wide level save");
        icommandlistener.sendMessage("   save-off                  disables terrain saving (useful for backup scripts)");
        icommandlistener.sendMessage("   save-on                   re-enables terrain saving");
        icommandlistener.sendMessage("   list                      lists all currently connected players");
        icommandlistener.sendMessage("   say <message>             broadcasts a message to all players");
        icommandlistener.sendMessage("   time <add|set> <amount>   adds to or sets the world time (0-24000)");
        icommandlistener.sendMessage("   profile <start|stop|report|save|snapshot|clear|status>  performance profiler commands");
        icommandlistener.sendMessage("   relight [player|chunkX chunkZ] [radius] [dimension]      relight loaded chunks to fix stale light");
    }

    private void print(String s, String s1) {
        String s2 = s + ": " + s1;

        // CraftBukkit start
        this.listener.sendMessage(s1);
        this.informOps("\u00A77(" + s2 + ")");
        if (this.listener instanceof MinecraftServer) {
            return; // Already logged so don't call a.info()
        }
        // CraftBukkit end
        a.info(s2);
    }

    // CraftBukkit start
    private void informOps(String msg) {
        Packet3Chat packet3chat = new Packet3Chat(msg);
        EntityPlayer sender = null;
        if (this.listener instanceof ServerCommandListener) {
            org.bukkit.command.CommandSender commandSender = ((ServerCommandListener) this.listener).getSender();
            if (commandSender instanceof CraftPlayer) {
                sender = ((CraftPlayer) commandSender).getHandle();
            }
        }
        java.util.List<EntityPlayer> players = this.server.serverConfigurationManager.players;
        for (int i = 0; i < players.size(); ++i) {
            EntityPlayer entityPlayer = (EntityPlayer) players.get(i);
            if (sender != entityPlayer && this.server.serverConfigurationManager.isOp(entityPlayer.name)) {
                entityPlayer.netServerHandler.sendPacket(packet3chat);
            }
        }
    }
    // CraftBukkit end

    private int a(String s, int i) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException numberformatexception) {
            return i;
        }
    }

    private boolean isInteger(String input) {
        if (input == null || input.length() == 0) {
            return false;
        }

        int start = (input.charAt(0) == '-' || input.charAt(0) == '+') ? 1 : 0;
        if (start == input.length()) {
            return false;
        }

        for (int i = start; i < input.length(); ++i) {
            if (!Character.isDigit(input.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private int clampRelightRadius(int radius) {
        if (radius < 0) {
            return 0;
        }

        if (radius > RELIGHT_MAX_RADIUS) {
            return RELIGHT_MAX_RADIUS;
        }

        return radius;
    }

    private void sendRelightUsage(ICommandListener commandListener) {
        commandListener.sendMessage("Usage: relight");
        commandListener.sendMessage("   relight [radius]                     (player only)");
        commandListener.sendMessage("   relight <player> [radius]");
        commandListener.sendMessage("   relight <chunkX> <chunkZ> [radius] [dimension]");
        commandListener.sendMessage("Notes: only loaded chunks are touched; max radius is " + RELIGHT_MAX_RADIUS + " chunks.");
    }

    private WorldServer getWorldByDimensionExact(int dimension) {
        for (int i = 0; i < this.server.worlds.size(); ++i) {
            WorldServer world = this.server.worlds.get(i);
            if (world.dimension == dimension) {
                return world;
            }
        }

        return null;
    }

    private void queueRelightForChunk(WorldServer world, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        world.a(EnumSkyBlock.SKY, minX, 0, minZ, maxX, 127, maxZ);
        world.a(EnumSkyBlock.BLOCK, minX, 0, minZ, maxX, 127, maxZ);
        world.b(minX, 0, minZ, maxX, 127, maxZ);
    }

    private void runRelight(WorldServer world, int centerChunkX, int centerChunkZ, int radius, ICommandListener commandListener, String senderName, String originLabel) {
        long startedAt = System.currentTimeMillis();
        int relitChunks = 0;
        int skippedChunks = 0;

        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; ++chunkX) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; ++chunkZ) {
                int blockX = (chunkX << 4) + 8;
                int blockZ = (chunkZ << 4) + 8;
                if (!world.isLoaded(blockX, 64, blockZ)) {
                    ++skippedChunks;
                    continue;
                }

                this.queueRelightForChunk(world, chunkX, chunkZ);
                ++relitChunks;
            }
        }

        int lightingPasses = 0;
        boolean hasMoreWork = false;

        if (relitChunks > 0) {
            do {
                hasMoreWork = world.doLighting();
                ++lightingPasses;
            } while (hasMoreWork && lightingPasses < RELIGHT_MAX_LIGHTING_PASSES);
        }

        String worldName = world.getWorld() != null ? world.getWorld().getName() : ("dim=" + world.dimension);
        long elapsedMs = System.currentTimeMillis() - startedAt;
        this.print(senderName, "Relight complete in " + worldName + " around " + originLabel + " (radius " + radius + "): relit " + relitChunks + " loaded chunks, skipped " + skippedChunks + " unloaded chunks, lighting passes " + lightingPasses + ", " + elapsedMs + " ms.");

        if (hasMoreWork) {
            commandListener.sendMessage("Relight queue still has pending work; remaining light updates will continue over subsequent ticks.");
        }
    }

    private void handleRelightCommand(String command, ICommandListener commandListener, String senderName, ServerConfigurationManager serverconfigurationmanager) {
        String[] parts = command.trim().split("\\s+");
        NetServerHandler senderHandler = commandListener instanceof NetServerHandler ? (NetServerHandler) commandListener : null;
        EntityPlayer senderPlayer = senderHandler != null ? senderHandler.player : null;
        int senderChunkX = senderPlayer != null ? MathHelper.floor(senderPlayer.locX) >> 4 : 0;
        int senderChunkZ = senderPlayer != null ? MathHelper.floor(senderPlayer.locZ) >> 4 : 0;
        WorldServer senderWorld = senderPlayer != null ? senderPlayer.getWorldServer() : this.server.getWorldServer(0);
        int radius = 0;
        int centerChunkX;
        int centerChunkZ;
        WorldServer world;
        String originLabel;

        if (parts.length == 1) {
            if (senderPlayer == null) {
                this.sendRelightUsage(commandListener);
                return;
            }

            centerChunkX = senderChunkX;
            centerChunkZ = senderChunkZ;
            world = senderWorld;
            originLabel = "player " + senderPlayer.name;
        } else if (parts.length == 2) {
            if (this.isInteger(parts[1])) {
                if (senderPlayer == null) {
                    this.sendRelightUsage(commandListener);
                    return;
                }

                radius = this.a(parts[1], 0);
                centerChunkX = senderChunkX;
                centerChunkZ = senderChunkZ;
                world = senderWorld;
                originLabel = "player " + senderPlayer.name;
            } else {
                EntityPlayer target = serverconfigurationmanager.i(parts[1]);
                if (target == null) {
                    commandListener.sendMessage("Can't find user " + parts[1] + ". No relight performed.");
                    return;
                }

                centerChunkX = MathHelper.floor(target.locX) >> 4;
                centerChunkZ = MathHelper.floor(target.locZ) >> 4;
                world = target.getWorldServer();
                originLabel = "player " + target.name;
            }
        } else if (parts.length == 3 && this.isInteger(parts[1]) && this.isInteger(parts[2])) {
            centerChunkX = this.a(parts[1], 0);
            centerChunkZ = this.a(parts[2], 0);
            world = senderWorld;
            originLabel = "chunk " + centerChunkX + "," + centerChunkZ;
        } else if (parts.length == 3) {
            EntityPlayer target = serverconfigurationmanager.i(parts[1]);
            if (target == null) {
                commandListener.sendMessage("Can't find user " + parts[1] + ". No relight performed.");
                return;
            }

            if (!this.isInteger(parts[2])) {
                this.sendRelightUsage(commandListener);
                return;
            }

            radius = this.a(parts[2], 0);
            centerChunkX = MathHelper.floor(target.locX) >> 4;
            centerChunkZ = MathHelper.floor(target.locZ) >> 4;
            world = target.getWorldServer();
            originLabel = "player " + target.name;
        } else if (this.isInteger(parts[1]) && this.isInteger(parts[2])) {
            centerChunkX = this.a(parts[1], 0);
            centerChunkZ = this.a(parts[2], 0);
            if (this.isInteger(parts[3])) {
                radius = this.a(parts[3], 0);
            } else {
                this.sendRelightUsage(commandListener);
                return;
            }

            int dimension = senderWorld.dimension;
            if (parts.length >= 5) {
                if (!this.isInteger(parts[4])) {
                    this.sendRelightUsage(commandListener);
                    return;
                }

                dimension = this.a(parts[4], dimension);
            }

            WorldServer explicitWorld = this.getWorldByDimensionExact(dimension);
            if (explicitWorld == null) {
                commandListener.sendMessage("No world loaded for dimension " + dimension + ".");
                return;
            }

            world = explicitWorld;
            originLabel = "chunk " + centerChunkX + "," + centerChunkZ;
        } else {
            this.sendRelightUsage(commandListener);
            return;
        }

        int clampedRadius = this.clampRelightRadius(radius);
        if (clampedRadius != radius) {
            commandListener.sendMessage("Radius " + radius + " is out of bounds; using " + clampedRadius + " instead.");
        }

        this.runRelight(world, centerChunkX, centerChunkZ, clampedRadius, commandListener, senderName, originLabel);
    }
    
    /**
     * Handle the /profile command for server performance profiling.
     */
    private void handleProfileCommand(String command, ICommandListener listener, String senderName) {
        String[] parts = command.split(" ");
        ServerProfiler profiler = ServerProfiler.getInstance();
        
        if (parts.length < 2) {
            listener.sendMessage("Usage: profile <start|stop|report|save|snapshot|clear|status>");
            listener.sendMessage("  start  - Start profiling");
            listener.sendMessage("  stop   - Stop profiling");
            listener.sendMessage("  report - Show compact summary and key findings");
            listener.sendMessage("  save   - Save report bundle (.txt + .json)");
            listener.sendMessage("  snapshot - Capture immediate always-on snapshot");
            listener.sendMessage("  clear  - Clear profiling data");
            listener.sendMessage("  status - Show current profiler status");
            return;
        }
        
        String subcommand = parts[1].toLowerCase();
        
        if ("start".equals(subcommand)) {
            profiler.start();
            this.print(senderName, "Profiler started. Run 'profile stop' to stop, then 'profile report' or 'profile save'.");
        } else if ("stop".equals(subcommand)) {
            profiler.stop();
            this.print(senderName, "Profiler stopped. Use 'profile report' to view or 'profile save' to save.");
        } else if ("report".equals(subcommand)) {
            ServerProfiler.ProfileSnapshot snapshot = profiler.captureSnapshot(profiler.isEnabled());
            String report = profiler.generateReportText(snapshot);
            String[] lines = report.split("\n");
            int maxLines = Math.min(lines.length, 80);
            for (int i = 0; i < maxLines; i++) {
                listener.sendMessage(lines[i]);
            }
            if (lines.length > maxLines) {
                listener.sendMessage("... (" + (lines.length - maxLines) + " additional lines omitted)");
                ServerProfiler.ReportBundle bundle = profiler.saveReportBundle(snapshot);
                if (bundle != null) {
                    this.print(senderName, "Detailed report bundle saved: txt=" + bundle.textPath + ", json=" + bundle.jsonPath);
                }
            }
        } else if ("snapshot".equals(subcommand)) {
            ServerProfiler.ProfileSnapshot snapshot = profiler.captureSnapshot(false);
            String report = profiler.generateReportText(snapshot);
            int linesSent = 0;
            for (String line : report.split("\n")) {
                if (linesSent++ >= 40) {
                    listener.sendMessage("... snapshot truncated, use 'profile save' for full report bundle.");
                    break;
                }
                listener.sendMessage(line);
            }
        } else if ("save".equals(subcommand)) {
            ServerProfiler.ReportBundle bundle = profiler.saveReportBundle();
            if (bundle != null) {
                this.print(senderName, "Report bundle saved: txt=" + bundle.textPath + ", json=" + bundle.jsonPath);
            } else {
                listener.sendMessage("Failed to save report. Check console for errors.");
            }
        } else if ("clear".equals(subcommand)) {
            profiler.clear();
            this.print(senderName, "Profiler data cleared.");
        } else if ("status".equals(subcommand)) {
            listener.sendMessage(profiler.getStatusSummary());
        } else {
            listener.sendMessage("Unknown profile subcommand: " + subcommand);
            listener.sendMessage("Use: profile <start|stop|report|save|snapshot|clear|status>");
        }
    }
}

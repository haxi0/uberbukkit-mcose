package net.minecraft.server;

import com.projectposeidon.ConnectionType;
import com.legacyminecraft.poseidon.PoseidonConfig;
import com.legacyminecraft.poseidon.util.CrackedAllowlist;
import com.legacyminecraft.poseidon.util.CryptoHelper;
import com.projectposeidon.johnymuffin.LoginProcessHandler;

import uk.betacraft.uberbukkit.Uberbukkit;
import uk.betacraft.uberbukkit.protocol.Protocol;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.CraftServer;

import java.net.InetSocketAddress;
import java.net.Socket;
import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.Random;
import java.util.logging.Logger;

import static com.legacyminecraft.poseidon.util.Release2Beta.deserializeAddress;

public class NetLoginHandler extends NetHandler {

    public static Logger a = Logger.getLogger("Minecraft");
    private static Random d = new Random();
    public NetworkManager networkManager;
    public boolean c = false;
    private MinecraftServer server;
    private int f = 0;
    private String g = null;
    private Packet1Login h = null;
    private String serverId = "";
    private ConnectionType connectionType;
    private boolean usingReleaseToBeta = false; //Poseidon -> Release2Beta support
    private boolean receivedLoginPacket = false;
    private int rawConnectionType;
    private boolean receivedKeepAlive = false;

    // Modern authentication fields
    private boolean modernAuthEnabled = false;
    private byte[] verifyToken;
    private SecretKey sharedSecret;
    private boolean modernAuthWithoutEncryption = true; // Beta 1.7.3 doesn't support encryption

    private final String msgKickShutdown;

    public NetLoginHandler(MinecraftServer minecraftserver, Socket socket, String s) {
        this.server = minecraftserver;
        this.networkManager = new NetworkManager(socket, s, this);
        this.networkManager.f = 0;

        this.msgKickShutdown = PoseidonConfig.getInstance().getConfigString("message.kick.shutdown");
    }

    // CraftBukkit start
    public Socket getSocket() {
        return this.networkManager.socket;
    }

    public MinecraftServer getMinecraftServer() {
        return this.server;
    }
    // CraftBukkit end

    public void a() {
        if (this.h != null) {
            this.b(this.h);
            this.h = null;
        }

        if (this.f++ == 600) {
            this.disconnect("Took too long to log in");
        } else {
            this.networkManager.b();
        }
    }

    public void disconnect(String s) {
        try {
            a.info("Disconnecting " + this.b() + ": " + s);
            this.networkManager.queue(new Packet255KickDisconnect(s));
            this.networkManager.d();
            this.c = true;
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void a(Packet2Handshake packet2handshake) {
        // Online mode uses modern Mojang authentication
        if (this.server.onlineMode) {
            if (!CrackedAllowlist.get().contains(packet2handshake.a)) {
                // Use modern authentication flow
                this.modernAuthEnabled = true;
                a.info("[AUTH] Using modern Mojang authentication for " + packet2handshake.a);

                this.verifyToken = new byte[4];
                new SecureRandom().nextBytes(this.verifyToken);

                KeyPair keyPair = CryptoHelper.getServerKeyPair();
                this.serverId = ""; // Empty string for modern auth
                this.networkManager.queue(new Packet253EncryptionRequest(this.serverId, keyPair.getPublic(), this.verifyToken));
                return;
            }
            // Cracked allowlist bypasses auth
            a.info("[AUTH] Cracked allowlist user '" + packet2handshake.a + "' bypassing authentication");
            this.networkManager.queue(new Packet2Handshake("-", packet2handshake.pvn11));
            return;
        }

        // Offline mode - no authentication
        this.networkManager.queue(new Packet2Handshake("-", packet2handshake.pvn11));
    }

    public void a(Packet0KeepAlive packet0KeepAlive) {
        receivedKeepAlive = true;
    }

    // Handler for modern authentication response
    public void a(Packet252SharedKey packet252SharedKey) {
        if (!this.modernAuthEnabled || !this.server.onlineMode) {
            this.disconnect("Protocol error");
            return;
        }

        try {
            KeyPair keyPair = CryptoHelper.getServerKeyPair();
            byte[] decryptedSecret = CryptoHelper.decryptRSA(packet252SharedKey.sharedSecret, keyPair.getPrivate());
            byte[] decryptedToken = CryptoHelper.decryptRSA(packet252SharedKey.verifyToken, keyPair.getPrivate());

            if (!java.util.Arrays.equals(this.verifyToken, decryptedToken)) {
                this.disconnect("Invalid verify token");
                return;
            }

            this.sharedSecret = CryptoHelper.createSecretKey(decryptedSecret);
            this.serverId = CryptoHelper.generateServerId("", keyPair.getPublic(), this.sharedSecret);

            // No link-layer encryption is enabled for b1.7.3; continue waiting for login
            if (this.modernAuthWithoutEncryption) {
                this.f = 0; // reset timeout
                this.receivedLoginPacket = false;
            }
        } catch (Exception e) {
            a.warning("Error handling encryption response: " + e.getMessage());
            this.disconnect("Encryption error");
        }
    }

    public void a(Packet1Login packet1login) {
        if (receivedLoginPacket) {
            this.disconnect("Multiple login packets received.");
            return;
        }
        receivedLoginPacket = true;
        this.g = packet1login.name;

        this.networkManager.pvn = packet1login.pvn; // uberbukkit

        // uberbukkit - account for b1.1_02's protocol version. assume b1.1_02
        if (Uberbukkit.getTargetPVN() == 7 && this.networkManager.pvn == 8) this.networkManager.pvn = 7;

        this.networkManager.protocol = Protocol.getProtocolClass(this.networkManager.pvn);

        if (!Uberbukkit.getAllowedPVNs().contains(this.networkManager.pvn)) {
            this.disconnect("Client version not allowed!");
        } else {
            //Project Poseidon - Start (Release2Beta)
            if (packet1login.d == (byte) -999 || packet1login.d == (byte) 25) {
                connectionType = ConnectionType.RELEASE2BETA_OFFLINE_MODE_IP_FORWARDING;
            } else if (packet1login.d == (byte) 26) {
                connectionType = ConnectionType.RELEASE2BETA_ONLINE_MODE_IP_FORWARDING;
            } else if (packet1login.d == (byte) 1) {
                connectionType = ConnectionType.RELEASE2BETA;
            } else if (packet1login.d == (byte) 2) {
                connectionType = ConnectionType.BUNGEECORD_OFFLINE_MODE_IP_FORWARDING;
            } else {
                connectionType = ConnectionType.NORMAL;
            }
            rawConnectionType = packet1login.d;
            //TODO: We need to find a better and cleaner way to support these different Beta proxies, Maybe a handler class???
            if ((Boolean) PoseidonConfig.getInstance().getConfigOption("settings.bungeecord.bungee-mode.enable") && !connectionType.equals(ConnectionType.BUNGEECORD_OFFLINE_MODE_IP_FORWARDING) && !connectionType.equals(ConnectionType.BUNGEECORD_ONLINE_MODE_IP_FORWARDING)) {
                a.info(packet1login.name + " is not using BungeeCord, kicking the player.");
                this.disconnect((String) PoseidonConfig.getInstance().getConfigOption("settings.bungeecord.bungee-mode.kick-message"));
                return;
            }

            if (connectionType.equals(ConnectionType.RELEASE2BETA_OFFLINE_MODE_IP_FORWARDING) || connectionType.equals(ConnectionType.RELEASE2BETA_ONLINE_MODE_IP_FORWARDING) || connectionType.equals(ConnectionType.BUNGEECORD_OFFLINE_MODE_IP_FORWARDING) || connectionType.equals(ConnectionType.BUNGEECORD_ONLINE_MODE_IP_FORWARDING)) {
                //Proxy has IP Forwarding enabled
                if ((Boolean) PoseidonConfig.getInstance().getConfigOption("settings.release2beta.enable-ip-pass-through")) {
                    //IP Forwarding is enabled server side
                    if (this.getSocket().getInetAddress().getHostAddress().equalsIgnoreCase(String.valueOf(PoseidonConfig.getInstance().getConfigOption("settings.release2beta.proxy-ip", "127.0.0.1")))) {
                        //Release2Beta server is authorized - Override IP address
                        InetSocketAddress address = deserializeAddress(packet1login.c);
                        a.info(packet1login.name + " has been detected using Release2Beta, using the IP passed through: " + address.getAddress().getHostAddress());
                        this.networkManager.setSocketAddress(address);
                        this.usingReleaseToBeta = true;
                    } else {
                        //Release2Beta server isn't authorized
                        a.info(packet1login.name + " is attempting to use a unauthorized Release2Beta server, kicking the player.");
                        this.disconnect(ChatColor.RED + "The Release2Beta server you are connecting through is unauthorized.");
                        return;
                    }
                } else {
                    //Poseidon doesn't support IP Forwarding
                    a.info(packet1login.name + " is trying to connect through R2B with IP Forwarding enabled, however, it is disabled in Poseidon. Kicking player!");
                    this.disconnect(ChatColor.RED + "IP Forwarding is disabled in Poseidon. Please disable in Release2Beta.");
                    return;
                }
            }
            //Project Poseidon - End (Release2Beta

            if (((CraftServer) Bukkit.getServer()).isShuttingdown()) {
                this.disconnect(this.msgKickShutdown);
                return;
            }


            LoginProcessHandler loginHandler = new LoginProcessHandler(this, packet1login, this.server.server, this.server.onlineMode);
            // Inform login handler whether modern auth was used
            try {
                loginHandler.setUsingModernAuth(this.modernAuthEnabled);
            } catch (Throwable ignore) {}
            // (new ThreadLoginVerifier(this, packet1login, this.server.server)).start(); // CraftBukkit
            //            }
        }
    }

    public void b(Packet1Login packet1login) {
        EntityPlayer entityplayer = this.server.serverConfigurationManager.a(this, packet1login.name);

        if (entityplayer != null) {
            this.server.serverConfigurationManager.b(entityplayer);
            // entityplayer.a((World) this.server.a(entityplayer.dimension)); // CraftBukkit - set by Entity
            // CraftBukkit - add world and location to 'logged in' message.
            a.info(this.b() + " logged in with entity id " + entityplayer.id + " at ([" + entityplayer.world.worldData.name + "] " + entityplayer.locX + ", " + entityplayer.locY + ", " + entityplayer.locZ + ")");
            WorldServer worldserver = (WorldServer) entityplayer.world; // CraftBukkit
            ChunkCoordinates chunkcoordinates = worldserver.getSpawn();
            NetServerHandler netserverhandler = new NetServerHandler(this.server, this.networkManager, entityplayer);
            //Poseidon Start
            netserverhandler.setUsingReleaseToBeta(usingReleaseToBeta);
            netserverhandler.setConnectionType(connectionType);
            netserverhandler.setRawConnectionType(rawConnectionType);
            netserverhandler.setReceivedKeepAlive(receivedKeepAlive);
            //Poseidon End
            // uberbukkit
            byte dim = this.getClientDimension(worldserver);
            if (this.networkManager.pvn < 12 && dim != 1) {
                dim = 0;
            }

            netserverhandler.sendPacket(new Packet1Login("", entityplayer.id, worldserver.getSeed(), dim));
            netserverhandler.sendPacket(new Packet6SpawnPosition(chunkcoordinates.x, chunkcoordinates.y, chunkcoordinates.z));

            // MCOSE: Send Uberbukkit NBT flag IMMEDIATELY after login, BEFORE any inventory packets
            // This MUST be the first packet after login so client knows to expect NBT in all subsequent ItemStack packets
            netserverhandler.sendPacket(new Packet70Bed(13));
            
            // MCOSE: Signal modded server - enables modern fence collision and other compatibility flags
            netserverhandler.sendPacket(new Packet70Bed(19));

            // Poseidon parity: signal client to enable special visuals on overworld attach.
            try {
                int actualTerrainType = (worldserver.worldData != null ? worldserver.worldData.getTerrainType() : 0);
                if (worldserver.worldProvider.dimension == 0) {
                    if (isAlphaVisualTerrain(actualTerrainType)) {
                        // ALPHA / ALPHA_SNOW visuals
                        netserverhandler.sendPacket(new Packet70Bed(5));
                        if (actualTerrainType == 5) {
                            // Explicitly signal ALPHA_SNOW variant so client picks snow biome visuals
                            netserverhandler.sendPacket(new Packet70Bed(10));
                        }
                    } else if (actualTerrainType == 7) {
                        // INFDEV visuals (parallel to deferred alpha flow)
                        netserverhandler.sendPacket(new Packet70Bed(20));
                    } else if (actualTerrainType == 6) {
                        // CLASSIC visuals use the INFDEV renderer path.
                        netserverhandler.sendPacket(new Packet70Bed(21));
                    } else if (actualTerrainType == 3) {
                        // SKY visuals
                        netserverhandler.sendPacket(new Packet70Bed(6));
                    }
                }
            } catch (Throwable ignore) {}
            this.server.serverConfigurationManager.a(entityplayer, worldserver);
            // this.server.serverConfigurationManager.sendAll(new Packet3Chat("\u00A7e" + entityplayer.name + " joined the game."));  // CraftBukkit - message moved to join event
            this.server.serverConfigurationManager.c(entityplayer);
            netserverhandler.a(entityplayer.locX, entityplayer.locY, entityplayer.locZ, entityplayer.yaw, entityplayer.pitch);
            this.server.networkListenThread.a(netserverhandler);
            netserverhandler.sendPacket(new Packet4UpdateTime(entityplayer.getPlayerTime())); // CraftBukkit - add support for player specific time

            // Poseidon parity: apply saved gamemode visuals and containers on login
            try {
                // Note: Packet70Bed(13) for Uberbukkit NBT flag is now sent immediately after login
                // before serverConfigurationManager.a() which calls updateContainer()
                
                entityplayer.syncInventory();
                entityplayer.updateContainer();
                if (entityplayer.gameMode == 1) {
                    netserverhandler.sendPacket(new Packet70Bed(3)); // creative HUD/flight enable
                } else {
                    netserverhandler.sendPacket(new Packet70Bed(4)); // survival HUD
                }
                // If SKY terrain, also send the SKY overlay refresh as Poseidon does in /gamemode
                if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                    netserverhandler.sendPacket(new Packet70Bed(2));
                }
                // uberbukkit: signal client to enable ladder-gap mechanics on modded clients
                netserverhandler.sendPacket(new Packet70Bed(7));
                // Send terrain visual hint on login based on overworld terrain type
                try {
                    WorldServer ws = this.server.getWorldServer(0);
                    if (ws != null && ws.worldData != null) {
                        int terrainType = ws.worldData.getTerrainType();
                        if (isAlphaVisualTerrain(terrainType) || isInfdevVisualTerrain(terrainType)) {
                            netserverhandler.sendPacket(new Packet70Bed(8)); // Terrain override on
                            if (terrainType == 5) {
                                // Signal ALPHA_SNOW variant explicitly
                                netserverhandler.sendPacket(new Packet70Bed(10));
                            }
                        } else {
                            netserverhandler.sendPacket(new Packet70Bed(9)); // Terrain override off
                        }
                    }
                } catch (Throwable ignore) {}
                if (this.server.isVoiceChatEnabled()) {
                    VoiceChatUDPServer voiceServer = this.server.getVoiceChatUDPServer();
                    if (voiceServer != null && entityplayer.getMojangUUID() != null) {
                        java.util.UUID secret = voiceServer.generateSecret(entityplayer.getMojangUUID());
                        if (secret != null) {
                            netserverhandler.sendPacket(new Packet70Bed(11));
                            // Send voice server connection info as a special packet
                            // Format: [[VOICEINFO:port:secretMSB:secretLSB]]
                            String voiceInfo = "[[VOICEINFO:" + this.server.getVoiceChatPort() + ":" +
                                              secret.getMostSignificantBits() + ":" +
                                              secret.getLeastSignificantBits() + "]]";
                            netserverhandler.sendPacket(new Packet3Chat(voiceInfo));
                        } else {
                            netserverhandler.sendPacket(new Packet70Bed(12));
                        }
                    } else {
                        netserverhandler.sendPacket(new Packet70Bed(12));
                    }
                } else {
                    netserverhandler.sendPacket(new Packet70Bed(12));
                }
                this.server.chatRoomManager.sendSnapshot(entityplayer);
                
                // Send hardcore mode indicator to client for heart display
                if (entityplayer.isHardcoreMode()) {
                    netserverhandler.sendPacket(new Packet70Bed(17)); // Hardcore mode enabled
                } else {
                    netserverhandler.sendPacket(new Packet70Bed(18)); // Hardcore mode disabled
                }
            } catch (Throwable ignore) {}
        }

        this.c = true;
    }

    public void a(String s, Object[] aobject) {
        a.info(this.b() + " lost connection");
        this.c = true;
    }

    public void a(Packet packet) {
        // Allow legacy server list pings during login phase
        if (packet instanceof Packet254ServerPing) {
            this.a((Packet254ServerPing) packet);
            return;
        }
        this.disconnect("Protocol error");
    }

    // Legacy server list ping (0xFE and 0xFE 0x01)
    public void a(Packet254ServerPing ping) {
        try {
            int online = this.server.serverConfigurationManager.players.size();
            int max = this.server.serverConfigurationManager.maxPlayers;
            String motd = this.server.propertyManager.getString("motd", "A Minecraft Server");
            if (motd == null || motd.trim().length() == 0) {
                motd = "A Minecraft Server";
                try {
                    // Persist a default if empty to avoid blank MOTD
                    this.server.propertyManager.properties.setProperty("motd", motd);
                    this.server.propertyManager.savePropertiesFile();
                } catch (Throwable ignored) {}
            }

            String response;
            if (ping.extended) {
                String protocol = "14";
                String version = "b1.7.3";
                response = "\u00a71\u0000" + protocol + "\u0000" + version + "\u0000" + motd + "\u0000" + online + "\u0000" + max;
            } else {
                response = motd + "\u00a7" + online + "\u00a7" + max;
            }

            this.networkManager.queue(new Packet255KickDisconnect(response));
            this.networkManager.d();
            this.c = true;
        } catch (Throwable t) {
            a.warning("Error handling legacy ping: " + t.getMessage());
            try { this.networkManager.d(); } catch (Throwable ignore) {}
            this.c = true;
        }
    }

    public String b() {
        return this.g != null ? this.g + " [" + this.networkManager.getSocketAddress().toString() + "]" : this.networkManager.getSocketAddress().toString();
    }

    //This can and will return null for multiple packets.
    public String getUsername() {
        return this.g;
    }

    public boolean c() {
        return true;
    }

    /**
     * @author moderator_man
     * @returns the session id for this player
     */
    public String getServerID() {
        return serverId;
    }

    static String a(NetLoginHandler netloginhandler) {
        return netloginhandler.serverId;
    }

    public static Packet1Login a(NetLoginHandler netloginhandler, Packet1Login packet1login) {
        return netloginhandler.h = packet1login;
    }

    private static boolean isAlphaVisualTerrain(int terrainType) {
        return terrainType == 1 || terrainType == 5;
    }

    private static boolean isInfdevVisualTerrain(int terrainType) {
        return terrainType == 6 || terrainType == 7;
    }

    private byte getClientDimension(WorldServer worldserver) {
        if (worldserver == null || worldserver.worldProvider == null) {
            return 0;
        }

        if (worldserver.worldProvider instanceof WorldProviderHell) {
            return -1;
        }

        try {
            if (worldserver.worldData != null && worldserver.worldData.getTerrainType() == 3) {
                // SKY terrain worlds should render with the Sky provider on clients.
                return 1;
            }
        } catch (Throwable ignore) {}

        return (byte) worldserver.worldProvider.dimension;
    }
}

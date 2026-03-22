package net.minecraft.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NetworkListenThread {

    public static Logger a = Logger.getLogger("Minecraft");
    private ServerSocket d;
    private Thread e;
    public volatile boolean b = false;
    private int f = 0;
    private ArrayList g = new ArrayList();
    private ArrayList h = new ArrayList();
    public MinecraftServer c;

    public NetworkListenThread(MinecraftServer minecraftserver, InetAddress inetaddress, int i) throws IOException {
        this.c = minecraftserver;
        // Create unbound socket first so we can set options before binding
        this.d = new ServerSocket();
        // Allow immediate rebind after server restart (avoids "Address already in use")
        this.d.setReuseAddress(true);
        this.d.setPerformancePreferences(0, 2, 1);
        // Now bind to the address and port
        this.d.bind(new InetSocketAddress(inetaddress, i), 0);
        this.b = true;
        this.e = new NetworkAcceptThread(this, "Listen thread", minecraftserver);
        this.e.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread thread, Throwable throwable) {
                a.log(Level.SEVERE, "Network accept thread crashed unexpectedly", throwable);
            }
        });
        this.e.start();
    }

    public void a(NetServerHandler netserverhandler) {
        this.h.add(netserverhandler);
    }

    private void a(NetLoginHandler netloginhandler) {
        if (netloginhandler == null) {
            throw new IllegalArgumentException("Got null pendingconnection!");
        } else {
            this.g.add(netloginhandler);
        }
    }

    public void a() {
        int i;

        for (i = 0; i < this.g.size(); ++i) {
            NetLoginHandler netloginhandler = (NetLoginHandler) this.g.get(i);

            try {
                netloginhandler.a();
            } catch (Exception exception) {
                if (netloginhandler == null) {
                    a.log(Level.WARNING, "Looks like someone tried to crash the server, stopped their attempt.");
                    this.g.remove(i);
                    return;
                } else {
                    netloginhandler.disconnect("Internal server error");
                    a.log(Level.WARNING, "Failed to handle packet: " + exception, exception);
                }
            }

            if (netloginhandler.c) {
                this.g.remove(i--);
            }

            netloginhandler.networkManager.a();
        }

        for (i = 0; i < this.h.size(); ++i) {
            NetServerHandler netserverhandler = (NetServerHandler) this.h.get(i);

            try {
                netserverhandler.a();
            } catch (Exception exception1) {
                a.log(Level.WARNING, "Failed to handle packet: " + exception1, exception1);
                netserverhandler.disconnect("Internal server error");
            }

            if (netserverhandler.disconnected) {
                this.h.remove(i--);
            }

            netserverhandler.networkManager.a();
        }
    }

    public int getPendingLoginCount() {
        return this.g.size();
    }

    public int getActiveHandlerCount() {
        return this.h.size();
    }

    public String getBoundAddressForLog() {
        if (this.d == null) {
            return "<unbound>";
        }

        InetAddress address = this.d.getInetAddress();
        if (address == null || address.isAnyLocalAddress()) {
            return "*";
        }

        return address.getHostAddress();
    }

    public int getBoundPort() {
        return this.d == null ? -1 : this.d.getLocalPort();
    }

    public boolean isAcceptThreadAlive() {
        return this.e != null && this.e.isAlive();
    }

    static ServerSocket a(NetworkListenThread networklistenthread) {
        return networklistenthread.d;
    }

    static int b(NetworkListenThread networklistenthread) {
        return networklistenthread.f++;
    }

    static void a(NetworkListenThread networklistenthread, NetLoginHandler netloginhandler) {
        networklistenthread.a(netloginhandler);
    }
    
    /**
     * Closes the server socket and stops accepting new connections.
     * This should be called during server shutdown to release the port.
     */
    public void closeSocket() {
        this.b = false; // Stop accepting new connections
        try {
            if (this.d != null && !this.d.isClosed()) {
                this.d.close();
                a.info("Server socket closed");
            }
        } catch (IOException e) {
            a.log(Level.WARNING, "Error closing server socket", e);
        }
    }
}

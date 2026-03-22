package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.HashMap;

class NetworkAcceptThread extends Thread {

    final MinecraftServer a;

    final NetworkListenThread b;
    
    // Connection rate limiting - configurable to allow clients that ping then connect.
    // Disabled by default; can be enabled to mitigate spam without breaking normal joins.
    private final long connectionThrottleMs;
    private final int connectionThrottleBurst;

    NetworkAcceptThread(NetworkListenThread networklistenthread, String s, MinecraftServer minecraftserver) {
        super(s);
        this.b = networklistenthread;
        this.a = minecraftserver;
        // Keep throttle disabled by default unless explicitly enabled in config.
        this.connectionThrottleMs = (long) PoseidonConfig.getInstance().getInt("settings.connection-throttle-ms.value", 0);
        // Allow a small burst within the throttle window so multiple players behind one NAT
        // can still join together without tripping the limiter.
        this.connectionThrottleBurst = Math.max(1, PoseidonConfig.getInstance().getInt("settings.connection-throttle-ms.burst", 4));
    }

    public void run() {
        HashMap<InetAddress, ThrottleWindow> throttleByAddress = new HashMap<InetAddress, ThrottleWindow>();

        while (this.b.b) {
            try {
                Socket socket = NetworkListenThread.a(this.b).accept();

                if (socket != null) {
                    InetAddress inetaddress = socket.getInetAddress();
                    long now = System.currentTimeMillis();
                    String hostAddress = inetaddress.getHostAddress();
                    boolean localhost = "127.0.0.1".equals(hostAddress) || "::1".equals(hostAddress) || "0:0:0:0:0:0:0:1".equals(hostAddress);
                    boolean throttleTriggered = false;

                    // Rate limit connections per IP (except localhost)
                    // This prevents connection spam but allows normal client behavior
                    // (clients ping the server list, then connect shortly after)
                    if (this.connectionThrottleMs > 0L && !localhost) {
                        ThrottleWindow throttleWindow = throttleByAddress.get(inetaddress);
                        if (throttleWindow == null || now - throttleWindow.windowStartMs >= this.connectionThrottleMs) {
                            throttleWindow = new ThrottleWindow(now);
                            throttleByAddress.put(inetaddress, throttleWindow);
                        } else {
                            ++throttleWindow.attempts;
                        }

                        if (throttleWindow.attempts > this.connectionThrottleBurst) {
                            throttleTriggered = true;
                        }
                    }

                    if (throttleTriggered) {
                        socket.close();
                    } else {
                        NetLoginHandler netloginhandler = new NetLoginHandler(this.a, socket, "Connection #" + NetworkListenThread.b(this.b));
                        NetworkListenThread.a(this.b, netloginhandler);
                    }
                }
            } catch (IOException ioexception) {
                // Only log if we're still supposed to be running (not shutdown)
                if (this.b.b) {
                    ioexception.printStackTrace();
                }
                // If b.b is false, socket was closed for shutdown - exit gracefully
            }
        }
    }

    private static final class ThrottleWindow {
        final long windowStartMs;
        int attempts;

        ThrottleWindow(long windowStartMs) {
            this.windowStartMs = windowStartMs;
            this.attempts = 1;
        }
    }
}

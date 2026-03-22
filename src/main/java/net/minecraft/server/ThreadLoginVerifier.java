package net.minecraft.server;

import com.legacyminecraft.poseidon.PoseidonConfig;
import com.projectposeidon.johnymuffin.LoginProcessHandler;
import com.legacyminecraft.poseidon.util.SessionAPI;
import org.bukkit.craftbukkit.CraftServer;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

// CraftBukkit start
// CraftBukkit end

public class ThreadLoginVerifier extends Thread {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int DEFAULT_RETRY_DELAY_MS = 250;
    private static final int DEFAULT_MAX_RETRY_DELAY_MS = 1000;
    private static final boolean DEFAULT_PARALLEL_NO_IP_FALLBACK = true;

    // Shared bounded pool so many logins can verify in parallel without unbounded thread growth.
    private static final ExecutorService SESSION_LOOKUP_POOL = Executors.newFixedThreadPool(
            Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2)),
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);

                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "SessionLookup-" + this.threadNumber.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            }
    );

    final Packet1Login loginPacket;

    final NetLoginHandler netLoginHandler;

    final LoginProcessHandler loginProcessHandler;  //Project Poseidon

    // CraftBukkit start
    CraftServer server;

    public ThreadLoginVerifier(LoginProcessHandler loginProcessHandler, NetLoginHandler netloginhandler, Packet1Login packet1login, CraftServer server) {
        this.server = server;
        // CraftBukkit end
        this.loginProcessHandler = loginProcessHandler;  //Project Poseidon

        this.netLoginHandler = netloginhandler;
        this.loginPacket = packet1login;
    }

    private String getIP() {
        return ((InetSocketAddress) netLoginHandler.networkManager.getSocketAddress()).getAddress().getHostAddress();
    }

    public void run() {
        try {
            String serverId = netLoginHandler.getServerID();
            String playerName = loginPacket.name;
            String clientIP = getIP();

            String verificationFailure = verifySessionWithRetry(playerName, serverId, clientIP);
            if (verificationFailure == null) {
                loginProcessHandler.userMojangSessionVerified();
                return;
            }

            loginProcessHandler.cancelLoginProcess(verificationFailure);
        } catch (Exception exception) {
            this.loginProcessHandler.cancelLoginProcess("Failed to verify username! [internal error " + exception + "]");
            exception.printStackTrace();
        }
    }

    private String verifySessionWithRetry(String playerName, String serverId, String clientIP) {
        boolean isLocalhost = "127.0.0.1".equals(clientIP) || "localhost".equals(clientIP);
        int maxAttempts = Math.max(1, getConfigInt("settings.authentication.session.max-attempts", DEFAULT_MAX_ATTEMPTS));
        int retryDelayMs = Math.max(0, getConfigInt("settings.authentication.session.retry-delay-ms", DEFAULT_RETRY_DELAY_MS));
        int maxRetryDelayMs = Math.max(retryDelayMs, getConfigInt("settings.authentication.session.max-retry-delay-ms", DEFAULT_MAX_RETRY_DELAY_MS));
        boolean parallelNoIpFallback = getConfigBoolean("settings.authentication.session.parallel-no-ip-fallback", DEFAULT_PARALLEL_NO_IP_FALLBACK);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            List<SessionAPI.ModernSessionResponse> responses = queryModernSessionWithFallback(playerName, serverId, clientIP, !isLocalhost && parallelNoIpFallback);

            boolean sawRetryableError = false;
            boolean sawNoContent = false;

            for (SessionAPI.ModernSessionResponse response : responses) {
                if (response == null) {
                    sawRetryableError = true;
                    continue;
                }

                int responseCode = response.getResponseCode();
                if (responseCode == 204) {
                    sawNoContent = true;
                    continue;
                }

                if (responseCode == 200) {
                    // Username match is case-insensitive per Mojang protocol docs.
                    if (!playerName.equalsIgnoreCase(response.getUsername())) {
                        return "Failed to verify username!";
                    }

                    // For non-localhost, verify IP if Mojang returned one.
                    String mojangIP = response.getIp();
                    if (!isLocalhost && mojangIP != null && !mojangIP.isEmpty() && !"noip".equalsIgnoreCase(mojangIP)) {
                        if (!clientIP.equals(mojangIP)) {
                            return "Failed to verify username! (IP mismatch)";
                        }
                    }

                    return null;
                }

                if (SessionAPI.isRetryableStatusCode(responseCode)) {
                    sawRetryableError = true;
                }
            }

            if (sawNoContent) {
                if (SessionAPI.hasJoined(playerName, serverId)) {
                    return null;
                }
            }

            if (!sawRetryableError || attempt >= maxAttempts) {
                break;
            }

            int delayMs = calculateBackoffDelay(attempt, retryDelayMs, maxRetryDelayMs);
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return "Failed to verify username!";
    }

    private List<SessionAPI.ModernSessionResponse> queryModernSessionWithFallback(String playerName, String serverId, String clientIP, boolean includeNoIpFallback) {
        List<SessionAPI.ModernSessionResponse> responses = new ArrayList<SessionAPI.ModernSessionResponse>(2);
        SessionAPI.ModernSessionResponse primary = lookupModernSession(playerName, serverId, clientIP);
        responses.add(primary);

        // Avoid doubling every request by default. Only do no-IP fallback when the primary
        // result was inconclusive/transient, which significantly reduces Mojang auth rate-limit hits.
        if (includeNoIpFallback && shouldTryNoIpFallback(primary)) {
            responses.add(lookupModernSession(playerName, serverId, "127.0.0.1"));
        }

        return responses;
    }

    private SessionAPI.ModernSessionResponse lookupModernSession(String playerName, String serverId, String clientIP) {
        Future<SessionAPI.ModernSessionResponse> future = SESSION_LOOKUP_POOL.submit(() -> SessionAPI.hasJoinedModern(playerName, serverId, clientIP));
        try {
            return future.get();
        } catch (Throwable throwable) {
            return new SessionAPI.ModernSessionResponse(-1, "", "", "");
        }
    }

    private boolean shouldTryNoIpFallback(SessionAPI.ModernSessionResponse response) {
        if (response == null) {
            return true;
        }

        int responseCode = response.getResponseCode();
        return responseCode == 204 || SessionAPI.isRetryableStatusCode(responseCode);
    }

    private int calculateBackoffDelay(int attempt, int retryDelayMs, int maxRetryDelayMs) {
        long baseDelay = (long) retryDelayMs;
        long exponentialDelay = baseDelay << Math.max(0, attempt - 1);
        return (int) Math.min((long) maxRetryDelayMs, exponentialDelay);
    }

    private int getConfigInt(String key, int defaultValue) {
        try {
            return PoseidonConfig.getInstance().getInt(key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }

    private boolean getConfigBoolean(String key, boolean defaultValue) {
        try {
            return PoseidonConfig.getInstance().getConfigBoolean(key, defaultValue);
        } catch (Throwable ignored) {
            return defaultValue;
        }
    }
}

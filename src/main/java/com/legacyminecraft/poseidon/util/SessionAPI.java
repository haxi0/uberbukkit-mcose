package com.legacyminecraft.poseidon.util;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * A wrapper class for the Minecraft session API
 * <p>
 * TODO maybe make the HTTP requests asynchronous? idk if it really matters
 *
 * @author moderator_man
 */
public class SessionAPI {
    public static final String SESSION_BASE = "http://session.minecraft.net/game/";
    public static final String MODERN_SESSION_BASE = "https://sessionserver.mojang.com/session/minecraft/";
    private static final int HTTP_TIMEOUT_MS = 3000;

    public static class ModernSessionResponse {
        private final int responseCode;
        private final String username;
        private final String uuid;
        private final String ip;

        public ModernSessionResponse(int responseCode, String username, String uuid, String ip) {
            this.responseCode = responseCode;
            this.username = username;
            this.uuid = uuid;
            this.ip = ip;
        }

        public int getResponseCode() {
            return this.responseCode;
        }

        public String getUsername() {
            return this.username;
        }

        public String getUuid() {
            return this.uuid;
        }

        public String getIp() {
            return this.ip;
        }
    }

    public static boolean hasJoined(String username, String serverId) {
        HTTPResponse response = httpGetRequest(SESSION_BASE + String.format("checkserver.jsp?user=%s&serverId=%s", username, serverId));
        if (!"YES".equals(response.getResponse())) return false;
        return true;
    }

    public static void hasJoined(String username, String serverId, String ip, SessionRequestRunnable callback) {
        ModernSessionResponse result = hasJoinedModern(username, serverId, ip);
        callback.callback(result.getResponseCode(), result.getUsername(), result.getUuid(), result.getIp());
    }

    public static ModernSessionResponse hasJoinedModern(String username, String serverId, String ip) {
        try {
            boolean checkIP = !"127.0.0.1".equals(ip) && !"localhost".equals(ip);
            StringBuilder sb = new StringBuilder();
            sb.append(MODERN_SESSION_BASE + "hasJoined");
            sb.append("?username=" + username);
            sb.append("&serverId=" + serverId);
            if (checkIP) sb.append("&ip=" + ip);
            String requestUrl = sb.toString();

            HTTPResponse response = httpGetRequest(requestUrl);

            // Handle 204 No Content
            if (response.getResponseCode() == 204) {
                return new ModernSessionResponse(204, "", "", "");
            }

            if (response.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return new ModernSessionResponse(response.getResponseCode(), "", "", "");
            }

            if (response.getResponse() == null || response.getResponse().isEmpty()) {
                return new ModernSessionResponse(204, "", "", "");
            }

            JSONObject obj = (JSONObject) new JSONParser().parse(response.getResponse());
            String res_username = (obj.containsKey("name") ? (String) obj.get("name") : "nousername");
            String res_uuid = (obj.containsKey("id") ? (String) obj.get("id") : "nouuid");
            String res_ip = (obj.containsKey("ip") ? (String) obj.get("ip") : "noip");
            System.out.println("[AUTH] Mojang response for " + username + ": code=" + response.getResponseCode() + ", uuid=" + res_uuid);
            return new ModernSessionResponse(response.getResponseCode(), res_username, res_uuid, res_ip);
        } catch (Exception ex) {
            System.out.println(String.format("[AUTH] Failed to authenticate session for '%s': %s", username, ex.getMessage()));
            return new ModernSessionResponse(-1, "", "", "");
        }
    }

    public static boolean isRetryableStatusCode(int responseCode) {
        return responseCode == -1
                || responseCode == 408
                || responseCode == 429
                || responseCode == 500
                || responseCode == 502
                || responseCode == 503
                || responseCode == 504;
    }

    private static HTTPResponse httpGetRequest(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (url.startsWith("https") ? (HttpsURLConnection) obj.openConnection() : (HttpURLConnection) obj.openConnection());
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Project-Poseidon/1.0");
            con.setConnectTimeout(HTTP_TIMEOUT_MS);
            con.setReadTimeout(HTTP_TIMEOUT_MS);

            int responseCode = con.getResponseCode();

            if (responseCode == 204) {
                return new HTTPResponse("", responseCode);
            }

            InputStream stream = responseCode >= 400 ? con.getErrorStream() : con.getInputStream();
            if (stream == null) {
                return new HTTPResponse("", responseCode);
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(stream));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) { response.append(inputLine); }
            in.close();
            return new HTTPResponse(response.toString(), responseCode);
        } catch (Throwable ex) {
            return new HTTPResponse("", -1);
        }
    }
}

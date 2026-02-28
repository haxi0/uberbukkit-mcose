package net.minecraft.server.network;

import net.minecraft.server.registry.RegistrySyncSnapshot;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * Versioned mod handshake and registry sync payload helpers.
 */
public final class ModProtocol {
    public static final int PROTOCOL_VERSION = 2;
    public static final int PROTOCOL_VERSION_LEGACY = 1;
    public static final int FEATURE_CHUNK_ZSTD = 1 << 0;
    public static final String CHANNEL_HELLO = "MCOSE|MOD_HELLO";
    public static final String CHANNEL_HELLO_ACK = "MCOSE|MOD_HELLO_ACK";
    public static final String CHANNEL_REGISTRY_SYNC = "MCOSE|REG_SYNC";
    public static final String CHANNEL_REGISTRY_REQUEST = "MCOSE|REG_REQ";

    private ModProtocol() {}

    public static byte[] createHelloAckPayload(int protocolVersion, int negotiatedFeatures) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(protocolVersion);
            if (protocolVersion >= PROTOCOL_VERSION) {
                out.writeInt(negotiatedFeatures);
            }
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static int readHelloVersion(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return -1;
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int version = in.readInt();
            in.close();
            return version;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    public static HelloInfo readHelloInfo(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return new HelloInfo(-1, 0);
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int version = in.readInt();
            int featureBits = 0;
            if (version >= PROTOCOL_VERSION && in.available() >= 4) {
                featureBits = in.readInt();
            }
            in.close();
            return new HelloInfo(version, featureBits);
        } catch (Throwable ignored) {
            return new HelloInfo(-1, 0);
        }
    }

    public static int readRegistryRequestVersion(byte[] payload) {
        return readHelloVersion(payload);
    }

    public static int resolveServerSupportedFeatures() {
        int features = 0;
        if (net.minecraft.server.ZstdRuntime.isAvailable()) {
            features |= FEATURE_CHUNK_ZSTD;
        }
        return features;
    }

    public static byte[] createRegistrySyncPayload(RegistrySyncSnapshot snapshot) {
        if (snapshot == null) {
            return new byte[0];
        }
        return snapshot.toBytes();
    }

    public static final class HelloInfo {
        public final int version;
        public final int featureBits;

        public HelloInfo(int version, int featureBits) {
            this.version = version;
            this.featureBits = featureBits;
        }
    }
}

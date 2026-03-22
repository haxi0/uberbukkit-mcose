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
    public static final int PROTOCOL_VERSION = 4;
    public static final int PROTOCOL_VERSION_LEGACY = 1;
    public static final int PROTOCOL_VERSION_ITEMS = 2;
    public static final int PROTOCOL_VERSION_EXPERIMENTAL = 5;
    private static final int FEATURE_BITS_INTRODUCED_IN = 2;

    public static final int FEATURE_CHUNK_ZSTD = 1 << 0;
    public static final int FEATURE_ITEM_STACK_V2 = 1 << 1;
    public static final int FEATURE_ITEM_COMPONENTS = 1 << 2;
    public static final int FEATURE_REGIONCORE_ITEMS = 1 << 3;
    public static final int FEATURE_ENTITY_WIRE_V2 = 1 << 4;
    public static final int FEATURE_ENTITY_DATA_V2 = 1 << 5;
    public static final int FEATURE_REGIONCORE_ENTITIES = 1 << 6;
    public static final int FEATURE_SKIN_PARTS_SYNC = 1 << 7;

    public static final String CHANNEL_HELLO = "MCOSE|MOD_HELLO";
    public static final String CHANNEL_HELLO_ACK = "MCOSE|MOD_HELLO_ACK";
    public static final String CHANNEL_REGISTRY_SYNC = "MCOSE|REG_SYNC";
    public static final String CHANNEL_REGISTRY_REQUEST = "MCOSE|REG_REQ";
    public static final String CHANNEL_SKIN_PARTS = "MCOSE|SKINPARTS";

    private ModProtocol() {}

    public static byte[] createHelloAckPayload(int protocolVersion, int negotiatedFeatures) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeInt(protocolVersion);
            if (supportsFeatureBits(protocolVersion)) {
                out.writeInt(negotiatedFeatures);
            }
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static int readHelloVersion(byte[] payload) {
        return readHelloInfo(payload).version;
    }

    public static HelloInfo readHelloInfo(byte[] payload) {
        if (payload == null || payload.length < 4) {
            return new HelloInfo(-1, 0);
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            int version = in.readInt();
            int featureBits = 0;
            if (supportsFeatureBits(version) && in.available() >= 4) {
                featureBits = in.readInt();
            }
            in.close();
            return new HelloInfo(version, featureBits);
        } catch (Throwable ignored) {
            return new HelloInfo(-1, 0);
        }
    }

    public static boolean supportsFeatureBits(int version) {
        return version >= FEATURE_BITS_INTRODUCED_IN;
    }

    public static boolean isSupportedVersion(int version) {
        return version == PROTOCOL_VERSION
                || version == PROTOCOL_VERSION_LEGACY
                || version == PROTOCOL_VERSION_ITEMS
                || version == 3
                || version == PROTOCOL_VERSION_EXPERIMENTAL;
    }

    public static int readRegistryRequestVersion(byte[] payload) {
        return readHelloVersion(payload);
    }

    public static int resolveServerSupportedFeatures() {
        int features = FEATURE_ITEM_STACK_V2
                | FEATURE_ITEM_COMPONENTS
                | FEATURE_REGIONCORE_ITEMS
                | FEATURE_ENTITY_WIRE_V2
                | FEATURE_ENTITY_DATA_V2
                | FEATURE_REGIONCORE_ENTITIES
                | FEATURE_SKIN_PARTS_SYNC;
        if (net.minecraft.server.ZstdRuntime.isAvailable()) {
            features |= FEATURE_CHUNK_ZSTD;
        }
        return features;
    }

    public static boolean hasRequiredEntityFeatures(int featureBits) {
        int required = FEATURE_ENTITY_WIRE_V2 | FEATURE_ENTITY_DATA_V2;
        return (featureBits & required) == required;
    }

    public static byte[] createRegistrySyncPayload(RegistrySyncSnapshot snapshot) {
        if (snapshot == null) {
            return new byte[0];
        }
        return snapshot.toBytes();
    }

    public static byte[] createSkinPartsPayload(String username, int modelPartMask) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(baos);
            out.writeUTF(username == null ? "" : username);
            out.writeByte(modelPartMask & 0x7F);
            out.flush();
            return baos.toByteArray();
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    public static SkinPartsInfo readSkinPartsPayload(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return new SkinPartsInfo("", 0x7F);
        }

        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
            String username = in.readUTF();
            int modelPartMask = in.readByte() & 0x7F;
            in.close();
            return new SkinPartsInfo(username, modelPartMask);
        } catch (Throwable ignored) {
            return new SkinPartsInfo("", 0x7F);
        }
    }

    public static final class HelloInfo {
        public final int version;
        public final int featureBits;

        public HelloInfo(int version, int featureBits) {
            this.version = version;
            this.featureBits = featureBits;
        }
    }

    public static final class SkinPartsInfo {
        public final String username;
        public final int modelPartMask;

        public SkinPartsInfo(String username, int modelPartMask) {
            this.username = username == null ? "" : username;
            this.modelPartMask = modelPartMask & 0x7F;
        }
    }
}

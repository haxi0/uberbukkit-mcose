package net.minecraft.server;

import com.github.luben.zstd.Zstd;

import java.util.Arrays;

public final class ZstdRuntime {

    private static final int DEFAULT_LEVEL = 3;
    private static boolean initialized;
    private static boolean available;
    private static boolean unavailableLogged;

    private ZstdRuntime() {
    }

    public static synchronized boolean isAvailable() {
        ensureInitialized();
        return available;
    }

    public static byte[] compressZstd(byte[] input, int length) {
        return compressZstd(input, 0, length, DEFAULT_LEVEL);
    }

    public static byte[] compressZstd(byte[] input, int offset, int length, int level) {
        if (input == null || offset < 0 || length < 0 || offset + length > input.length) {
            return null;
        }
        if (!isAvailable()) {
            return null;
        }

        try {
            long bound = Zstd.compressBound(length);
            if (bound <= 0L || bound > Integer.MAX_VALUE) {
                return null;
            }

            byte[] out = new byte[(int) bound];
            long written = Zstd.compressByteArray(out, 0, out.length, input, offset, length, level);
            if (Zstd.isError(written) || written < 0L || written > (long) out.length) {
                return null;
            }

            return Arrays.copyOf(out, (int) written);
        } catch (Throwable failure) {
            disableAfterFailure(failure);
            return null;
        }
    }

    public static byte[] decompressZstd(byte[] compressed, int expectedSize) {
        if (compressed == null || expectedSize < 0) {
            return null;
        }
        if (!isAvailable()) {
            return null;
        }

        try {
            byte[] out = new byte[expectedSize];
            long read = Zstd.decompressByteArray(out, 0, out.length, compressed, 0, compressed.length);
            if (Zstd.isError(read) || read != (long) expectedSize) {
                return null;
            }
            return out;
        } catch (Throwable failure) {
            disableAfterFailure(failure);
            return null;
        }
    }

    private static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }

        try {
            int defaultLevel = Zstd.defaultCompressionLevel();
            available = defaultLevel != 0;
            if (!available) {
                logUnavailable("zstd-jni returned invalid default level");
            }
        } catch (Throwable failure) {
            available = false;
            logUnavailable(failure.toString());
        }

        initialized = true;
    }

    private static synchronized void disableAfterFailure(Throwable failure) {
        available = false;
        initialized = true;
        logUnavailable(failure.toString());
    }

    private static void logUnavailable(String reason) {
        if (unavailableLogged) {
            return;
        }
        unavailableLogged = true;
        System.out.println("[Compression] zstd disabled, falling back to zlib: " + reason);
    }
}

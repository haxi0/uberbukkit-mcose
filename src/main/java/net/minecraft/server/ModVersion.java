package net.minecraft.server;

/**
 * Central version information for Minecraft Oldschool Edition server.
 * This MUST match the client's ModVersion to prevent version mismatches.
 */
public class ModVersion {
    /** The current mod version number (e.g., "1.4", "1.5", "2.0") */
    public static final String VERSION = "1.8";
    
    /** The full mod name */
    public static final String MOD_NAME = "Minecraft Oldschool Edition";
    
    /** Full display string combining name and version */
    public static final String FULL_NAME = MOD_NAME + " " + VERSION;
    
    /**
     * Parse the major version number from a version string.
     * Supports suffixes such as "1.8 Pre-Release 1".
     * @param version Version string like "1.7", "1.7.2", or "1.8 Pre-Release 1"
     * @return Major version (e.g., 1)
     */
    public static int getMajor(String version) {
        return getVersionComponent(version, 0);
    }
    
    /**
     * Parse the minor version number from a version string.
     * Supports suffixes such as "1.8 Pre-Release 1".
     * @param version Version string like "1.7", "1.7.2", or "1.8 Pre-Release 1"
     * @return Minor version (e.g., 7)
     */
    public static int getMinor(String version) {
        return getVersionComponent(version, 1);
    }

    /**
     * Returns a normalized "major.minor" string.
     * Examples:
     * - "1.8 Pre-Release 1" -> "1.8"
     * - "1.7.3" -> "1.7"
     */
    public static String getMajorMinor(String version) {
        return getMajor(version) + "." + getMinor(version);
    }

    /**
     * Compare two version strings using major.minor only.
     * Returns:
     * - negative when left < right
     * - zero when equal
     * - positive when left > right
     */
    public static int compareMajorMinor(String leftVersion, String rightVersion) {
        int leftMajor = getMajor(leftVersion);
        int rightMajor = getMajor(rightVersion);
        if (leftMajor != rightMajor) {
            return leftMajor - rightMajor;
        }

        int leftMinor = getMinor(leftVersion);
        int rightMinor = getMinor(rightVersion);
        return leftMinor - rightMinor;
    }

    /**
     * Extract Nth numeric component from a version string.
     * Example: "1.8 Pre-Release 1" -> [1, 8, 1]
     */
    private static int getVersionComponent(String version, int index) {
        if (version == null || version.isEmpty() || index < 0) {
            return 0;
        }

        int componentIndex = 0;
        int value = 0;
        boolean inNumber = false;

        for (int i = 0; i < version.length(); i++) {
            char ch = version.charAt(i);
            if (ch >= '0' && ch <= '9') {
                if (!inNumber) {
                    inNumber = true;
                    value = ch - '0';
                } else {
                    value = (value * 10) + (ch - '0');
                }
            } else if (inNumber) {
                if (componentIndex == index) {
                    return value;
                }
                componentIndex++;
                inNumber = false;
                value = 0;
            }
        }

        if (inNumber && componentIndex == index) {
            return value;
        }

        return 0;
    }
    
    /**
     * Check if the client version is compatible with the server version.
     * Client must be at least the same major.minor version as server.
     * @param clientVersion The client's version string
     * @return true if compatible, false if outdated
     */
    public static boolean isCompatible(String clientVersion) {
        return isCompatible(clientVersion, VERSION);
    }

    /**
     * Check if the client version is compatible with a configured minimum.
     * Comparison uses major.minor only and ignores patch/prerelease suffixes.
     * @param clientVersion The client's version string
     * @param minimumVersion Minimum required version string
     * @return true if client >= minimum major.minor
     */
    public static boolean isCompatible(String clientVersion, String minimumVersion) {
        return compareMajorMinor(clientVersion, minimumVersion) >= 0;
    }
    
    /**
     * Get a user-friendly message for version mismatch.
     */
    public static String getOutdatedMessage(String clientVersion) {
        return getOutdatedMessage(clientVersion, VERSION);
    }

    /**
     * Get a user-friendly message for version mismatch against a minimum version.
     */
    public static String getOutdatedMessage(String clientVersion, String minimumVersion) {
        return "Outdated client! You have " + clientVersion + ", minimum required is " + getMajorMinor(minimumVersion) + " or newer";
    }
}

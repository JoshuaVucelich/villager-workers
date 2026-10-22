package com.vucelich.villagerworkers.common;

/**
 * Create's upload cap is the server config {@code schematics.maxTotalSchematicSize}.
 * The config comment says the unit is KiloBytes. ServerSchematicLoader rejects an upload
 * when the byte count is greater than that setting times 1000. The default setting is 256,
 * which is 256000 bytes. Changing the config is enough, so this mod does not mixin the check.
 */
public final class SchematicUploadLimit {
    public static final String CONFIG_KEY = "schematics.maxTotalSchematicSize";
    public static final int DEFAULT_KILOBYTES = 256;

    private SchematicUploadLimit() {}

    public static long maxBytes(int kiloBytes) {
        return Math.max(0, kiloBytes) * 1000L;
    }

    public static boolean fits(long bytes, int kiloBytes) {
        return bytes >= 0 && bytes <= maxBytes(kiloBytes);
    }
}

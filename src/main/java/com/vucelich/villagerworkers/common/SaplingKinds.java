package com.vucelich.villagerworkers.common;

/**
 * Which sapling replaces a log. Only vanilla ids are mapped; unknown woods are not replanted.
 */
public final class SaplingKinds {
    private SaplingKinds() {}

    public static String saplingForLog(String blockId) {
        if (blockId == null || !blockId.startsWith("minecraft:")) {
            return null;
        }
        String path = blockId.substring("minecraft:".length());
        String base = path;
        boolean stripped = false;
        for (String suffix : new String[] {"_log", "_wood", "_stem", "_hyphae"}) {
            if (path.endsWith(suffix)) {
                base = path.substring(0, path.length() - suffix.length());
                stripped = true;
                break;
            }
        }
        if (!stripped) {
            return null;
        }
        if (base.startsWith("stripped_")) {
            base = base.substring("stripped_".length());
        }
        return switch (base) {
            case "mangrove" -> "minecraft:mangrove_propagule";
            case "crimson" -> "minecraft:crimson_fungus";
            case "warped" -> "minecraft:warped_fungus";
            case "bamboo" -> null;
            default -> "minecraft:" + base + "_sapling";
        };
    }
}

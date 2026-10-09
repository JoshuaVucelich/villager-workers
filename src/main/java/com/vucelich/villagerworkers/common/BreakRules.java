package com.vucelich.villagerworkers.common;

import java.util.Set;

/**
 * The only break decision. Job code may destroy a block only when this returns true.
 * A whitelist entry skips the block-entity and protected-namespace rules and nothing else.
 */
public final class BreakRules {
    public static final Set<String> GLOBAL_DENY = Set.of(
            "bedrock",
            "barrier",
            "command_block",
            "chain_command_block",
            "repeating_command_block",
            "structure_block",
            "structure_void",
            "jigsaw",
            "end_portal",
            "end_portal_frame",
            "nether_portal",
            "reinforced_deepslate");

    /**
     * Create Aeronautics ships as several mods. Simulated is the assembly core, Sable is the physics
     * engine, Aeronautics is the flying parts, and Offroad is the land-vehicle parts. All of them add
     * world blocks, so they share the Create protection rule.
     */
    public static final Set<String> PROTECTED_NAMESPACES = Set.of(
            "create",
            "aeronautics",
            "simulated",
            "sable",
            "offroad");

    private BreakRules() {}

    public static boolean allow(
            boolean workingLumberOrMiner,
            boolean insideVolume,
            boolean withinReach,
            boolean sameDimension,
            float destroySpeed,
            String blockId,
            boolean blockEntity,
            boolean reservedPosition,
            boolean whitelisted,
            boolean lumberBlockOk) {
        if (!workingLumberOrMiner || !insideVolume || !withinReach || !sameDimension) {
            return false;
        }
        if (destroySpeed < 0.0F || reservedPosition) {
            return false;
        }
        String path = path(blockId);
        if (path == null || GLOBAL_DENY.contains(path)) {
            return false;
        }
        if (blockEntity && !whitelisted) {
            return false;
        }
        String namespace = namespace(blockId);
        if (namespace != null && PROTECTED_NAMESPACES.contains(namespace) && !whitelisted) {
            return false;
        }
        return lumberBlockOk;
    }

    public static String namespace(String blockId) {
        if (blockId == null) {
            return null;
        }
        int colon = blockId.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        return blockId.substring(0, colon);
    }

    public static String path(String blockId) {
        if (blockId == null) {
            return null;
        }
        int colon = blockId.indexOf(':');
        if (colon < 0 || colon >= blockId.length() - 1) {
            return null;
        }
        return blockId.substring(colon + 1);
    }
}

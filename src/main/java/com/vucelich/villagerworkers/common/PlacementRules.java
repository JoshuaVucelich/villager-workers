package com.vucelich.villagerworkers.common;

/**
 * Where a builder may place a schematic cell. It never breaks a block.
 * Air in the schematic does not clear a replaceable block, and a solid mismatch is left in place.
 */
public final class PlacementRules {
    public enum Choice {
        SKIP,
        PLACE,
        BLOCKED,
        MISSING
    }

    private PlacementRules() {}

    public static Choice choose(boolean alreadyMatches, boolean schematicAir, boolean replaceable, boolean hasMaterials) {
        if (alreadyMatches) {
            return Choice.SKIP;
        }
        if (schematicAir) {
            return replaceable ? Choice.SKIP : Choice.BLOCKED;
        }
        if (!replaceable) {
            return Choice.BLOCKED;
        }
        if (!hasMaterials) {
            return Choice.MISSING;
        }
        return Choice.PLACE;
    }
}

package com.vucelich.villagerworkers.common;

/**
 * Where a broken block's items go. The inventory is tried first. Storage is the fallback.
 * The ground is only used when both are full.
 */
public final class DropRouting {
    public enum Place {
        INVENTORY,
        STORAGE,
        GROUND
    }

    private DropRouting() {}

    public static Place choose(boolean inventoryHasRoom, boolean storageHasRoom) {
        if (inventoryHasRoom) {
            return Place.INVENTORY;
        }
        if (storageHasRoom) {
            return Place.STORAGE;
        }
        return Place.GROUND;
    }
}

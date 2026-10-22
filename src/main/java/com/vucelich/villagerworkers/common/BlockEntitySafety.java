package com.vucelich.villagerworkers.common;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;

/**
 * Block entity data copied from a structure.
 * Containers, loot, books, and records are left empty so a schematic cannot smuggle items.
 */
public final class BlockEntitySafety {
    private static final Set<String> UNSAFE = Set.of("Items", "Item", "item", "RecordItem", "Book", "LootTable");

    private BlockEntitySafety() {}

    public static boolean safe(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return true;
        }
        for (String key : UNSAFE) {
            if (tag.contains(key)) {
                return false;
            }
        }
        return true;
    }

    public static CompoundTag copyIfSafe(CompoundTag tag) {
        if (tag == null || tag.isEmpty() || !safe(tag)) {
            return null;
        }
        return tag.copy();
    }
}

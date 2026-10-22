package com.vucelich.villagerworkers.common;

import java.util.List;

/**
 * Props that are drawn on a foreman. Nothing in this list is inserted into an inventory.
 */
public final class CosmeticGear {
    public static final String CLIPBOARD = "clipboard";
    public static final String BLUEPRINT = "blueprint";

    private CosmeticGear() {}

    public static List<String> renderedProps(boolean foreman) {
        if (!foreman) {
            return List.of();
        }
        return List.of(CLIPBOARD, BLUEPRINT);
    }

    /** Hiring, promoting, and rendering add this many real items. */
    public static int inventoryItemsAdded() {
        return 0;
    }
}

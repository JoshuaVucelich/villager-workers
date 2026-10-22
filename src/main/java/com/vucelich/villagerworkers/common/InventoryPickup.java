package com.vucelich.villagerworkers.common;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * Broken blocks go into the villager inventory first. The assigned chest is next.
 * Anything still left is dropped, and the owner is told.
 */
public final class InventoryPickup {
    public record Outcome(int inventory, int chest, int dropped, boolean toldOwner) {}

    private InventoryPickup() {}

    public static Outcome plan(int count, int inventoryRoom, int chestRoom, boolean chestAssigned) {
        if (count <= 0) {
            return new Outcome(0, 0, 0, false);
        }
        int inventory = Math.min(count, Math.max(0, inventoryRoom));
        int rest = count - inventory;
        int chest = 0;
        if (chestAssigned) {
            chest = Math.min(rest, Math.max(0, chestRoom));
            rest -= chest;
        }
        return new Outcome(inventory, chest, rest, rest > 0);
    }

    /** How many of this stack the container can still accept. */
    public static int room(Container container, ItemStack stack) {
        if (container == null || stack == null || stack.isEmpty()) {
            return 0;
        }
        int room = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                if (container.canPlaceItem(slot, stack)) {
                    room += stack.getMaxStackSize();
                }
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                room += Math.max(0, existing.getMaxStackSize() - existing.getCount());
            }
        }
        return room;
    }
}

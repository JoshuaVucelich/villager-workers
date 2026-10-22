package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InventoryPickupTest {
    @Test
    void fullInventoryTakesTheWholeStack() {
        InventoryPickup.Outcome outcome = InventoryPickup.plan(64, 64, 0, false);
        assertEquals(64, outcome.inventory());
        assertEquals(0, outcome.chest());
        assertEquals(0, outcome.dropped());
        assertFalse(outcome.toldOwner());
    }

    @Test
    void overflowGoesToTheAssignedChest() {
        InventoryPickup.Outcome outcome = InventoryPickup.plan(64, 10, 54, true);
        assertEquals(10, outcome.inventory());
        assertEquals(54, outcome.chest());
        assertEquals(0, outcome.dropped());
        assertFalse(outcome.toldOwner());
    }

    @Test
    void missingChestDropsAndTellsTheOwner() {
        InventoryPickup.Outcome outcome = InventoryPickup.plan(64, 0, 0, false);
        assertEquals(0, outcome.inventory());
        assertEquals(0, outcome.chest());
        assertEquals(64, outcome.dropped());
        assertTrue(outcome.toldOwner());
    }

    @Test
    void fullChestDropsAndTellsTheOwner() {
        InventoryPickup.Outcome outcome = InventoryPickup.plan(64, 0, 0, true);
        assertEquals(64, outcome.dropped());
        assertTrue(outcome.toldOwner());
    }

    @Test
    void partialRoomsSplitThreeWays() {
        InventoryPickup.Outcome outcome = InventoryPickup.plan(20, 8, 8, true);
        assertEquals(8, outcome.inventory());
        assertEquals(8, outcome.chest());
        assertEquals(4, outcome.dropped());
        assertTrue(outcome.toldOwner());
    }

    @Test
    void emptyStackDoesNothing() {
        InventoryPickup.Outcome outcome = InventoryPickup.plan(0, 64, 64, true);
        assertEquals(0, outcome.inventory());
        assertEquals(0, outcome.dropped());
        assertFalse(outcome.toldOwner());
    }
}

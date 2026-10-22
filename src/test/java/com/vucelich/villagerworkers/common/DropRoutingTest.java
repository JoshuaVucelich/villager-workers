package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DropRoutingTest {
    @Test
    void inventoryComesBeforeStorageAndTheGround() {
        assertEquals(DropRouting.Place.INVENTORY, DropRouting.choose(true, true));
        assertEquals(DropRouting.Place.INVENTORY, DropRouting.choose(true, false));
        assertEquals(DropRouting.Place.STORAGE, DropRouting.choose(false, true));
        assertEquals(DropRouting.Place.GROUND, DropRouting.choose(false, false));
    }
}

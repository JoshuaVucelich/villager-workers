package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import org.junit.jupiter.api.Test;

class ForemanTest {
    private final UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void promoteAndDemoteOnlyChangeTheFlag() {
        WorkerData data = WorkerData.hire(owner);
        assertFalse(data.foreman());
        assertEquals(0, data.crewSize());
        WorkerJob job = data.job();
        int owners = data.ownerCount();
        data.setForeman(true);
        data.setCrewSize(4);
        assertTrue(data.foreman());
        assertEquals(4, data.crewSize());
        assertEquals(job, data.job());
        assertEquals(owners, data.ownerCount());
        data.setForeman(false);
        assertFalse(data.foreman());
        assertEquals(owners, data.ownerCount());
    }

    @Test
    void foremanFlagRoundTripsAndLegacySaveIsNotAForeman() {
        WorkerData data = WorkerData.hire(owner);
        data.setForeman(true);
        data.setCrewSize(2);
        var saved = WorkerData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        WorkerData loaded = WorkerData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        assertTrue(loaded.foreman());
        assertEquals(2, loaded.crewSize());

        CompoundTag legacy = new CompoundTag();
        legacy.putString("owner", owner.toString());
        WorkerData migrated = WorkerData.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow();
        assertFalse(migrated.foreman());
        assertEquals(0, migrated.crewSize());
    }

    @Test
    void hatColorsDifferAndPropsAreNotItems() {
        assertNotEquals(HatStyle.WORKER, HatStyle.FOREMAN);
        assertEquals(HatStyle.WORKER, HatStyle.color(false));
        assertEquals(HatStyle.FOREMAN, HatStyle.color(true));
        assertEquals(0, CosmeticGear.renderedProps(false).size());
        assertEquals(2, CosmeticGear.renderedProps(true).size());
        assertEquals(CosmeticGear.CLIPBOARD, CosmeticGear.renderedProps(true).get(0));
        assertEquals(CosmeticGear.BLUEPRINT, CosmeticGear.renderedProps(true).get(1));
        assertEquals(0, CosmeticGear.inventoryItemsAdded());
    }

    @Test
    void labelFollowsTheConfig() {
        assertTrue(ForemanLabel.DEFAULT_ENABLED);
        assertEquals("Foreman Birch (4 crew)", ForemanLabel.text("Birch", 4, true));
        assertEquals("", ForemanLabel.text("Birch", 4, false));
        assertEquals("Foreman worker (0 crew)", ForemanLabel.text("  ", -3, true));
    }
}

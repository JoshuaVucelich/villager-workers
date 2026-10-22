package com.vucelich.villagerworkers.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkerSettingsTest {
    @Test
    void holdWhileMenuOpenDefaultsOn() {
        assertTrue(WorkerSettings.HOLD_WHILE_MENU_OPEN.getDefault());
    }

    @Test
    void structureBuildDefaultsStayCheap() {
        assertEquals(1, WorkerSettings.BUILD_PLACEMENTS_PER_TICK.getDefault());
        assertEquals(64, WorkerSettings.BUILD_SCAN_BUDGET.getDefault());
        assertEquals(-1, WorkerSettings.BUILD_INTERVAL_TICKS.getDefault());
        assertEquals(256, WorkerSettings.CREATE_SCHEMATIC_UPLOAD_KB.getDefault());
    }
}

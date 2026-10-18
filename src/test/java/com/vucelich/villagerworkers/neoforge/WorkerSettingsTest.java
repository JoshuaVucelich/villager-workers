package com.vucelich.villagerworkers.neoforge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkerSettingsTest {
    @Test
    void holdWhileMenuOpenDefaultsOn() {
        assertTrue(WorkerSettings.HOLD_WHILE_MENU_OPEN.getDefault());
    }
}

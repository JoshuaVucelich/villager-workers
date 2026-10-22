package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SchematicUploadLimitTest {
    @Test
    void defaultCapIs256000Bytes() {
        assertEquals("schematics.maxTotalSchematicSize", SchematicUploadLimit.CONFIG_KEY);
        assertEquals(256000L, SchematicUploadLimit.maxBytes(SchematicUploadLimit.DEFAULT_KILOBYTES));
        assertTrue(SchematicUploadLimit.fits(256000L, 256));
        assertFalse(SchematicUploadLimit.fits(256001L, 256));
    }

    @Test
    void raisingTheSettingRaisesTheCap() {
        assertEquals(512000L, SchematicUploadLimit.maxBytes(512));
        assertTrue(SchematicUploadLimit.fits(300000L, 512));
        assertFalse(SchematicUploadLimit.fits(512001L, 512));
    }
}

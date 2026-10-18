package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SaveFormatTest {
    @Test
    void version046SaveLoadsWithoutHoldState() {
        UUID owner = UUID.fromString("6f1e0c2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b");
        String json = "{\"owner\":\"" + owner + "\",\"job\":\"miner\",\"activity\":\"stopped\",\"rested\":true}";
        WorkerData data = WorkerData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(owner, data.owner());
        assertEquals(WorkerJob.MINER, data.job());
        assertEquals(WorkerActivity.STOPPED, data.activity());
        assertTrue(data.rested());
        assertEquals(ProblemCode.NONE, data.problem().code());
        assertTrue(data.breakWhitelist().isEmpty());
        assertTrue(data.miner().useValuables());
        assertTrue(data.carryIsEmpty());
    }

    @Test
    void valuablesToggleRoundTrips() {
        UUID owner = UUID.fromString("6f1e0c2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b");
        String json = "{\"owner\":\"" + owner + "\",\"miner\":{\"use_valuables\":false,\"valuables\":[\"minecraft:diamond\"]}}";
        WorkerData data = WorkerData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(List.of(ResourceLocation.parse("minecraft:diamond")), data.miner().valuables());
        assertFalse(data.miner().useValuables());
        WorkerData again = WorkerData.CODEC.parse(JsonOps.INSTANCE, WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assertEquals(List.of(ResourceLocation.parse("minecraft:diamond")), again.miner().valuables());
        assertFalse(again.miner().useValuables());
    }
}

package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RankTest {
    private final UUID owner = UUID.fromString("6f1e0c2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b");

    @Test
    void hireStartsAsAWorker() {
        assertEquals(WorkerRank.WORKER, WorkerData.hire(owner).rank());
    }

    @Test
    void oldSaveHasNoForemanHat() {
        String json = "{\"owner\":\"" + owner + "\",\"job\":\"miner\",\"activity\":\"stopped\"}";
        WorkerData data = WorkerData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(WorkerRank.WORKER, data.rank());
    }

    @Test
    void foremanRankRoundTrips() {
        WorkerData data = WorkerData.hire(owner);
        data.setRank(WorkerRank.FOREMAN);
        WorkerData again = WorkerData.CODEC.parse(JsonOps.INSTANCE, WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assertEquals(WorkerRank.FOREMAN, again.rank());
        again.setRank(WorkerRank.WORKER);
        WorkerData demoted = WorkerData.CODEC.parse(JsonOps.INSTANCE, WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, again).getOrThrow()).getOrThrow();
        assertEquals(WorkerRank.WORKER, demoted.rank());
    }
}

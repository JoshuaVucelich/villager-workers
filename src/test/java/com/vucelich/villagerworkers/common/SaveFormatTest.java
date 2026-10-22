package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
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

    @Test
    void version049JobsAndActionsRemain() {
        assertEquals(
                Set.of(WorkerJob.NONE, WorkerJob.LUMBERJACK, WorkerJob.MINER, WorkerJob.BUILDER),
                Set.of(WorkerJob.values()));
        assertEquals(7, WorkerAction.values().length);
        assertEquals(
                Set.of(
                        WorkerAction.OPEN_SCREEN,
                        WorkerAction.SET_ACTIVITY,
                        WorkerAction.EDIT_SETUP,
                        WorkerAction.CHAT,
                        WorkerAction.EDIT_TRUSTED,
                        WorkerAction.TRANSFER_OWNER,
                        WorkerAction.VIEW_PERMISSIONS),
                Set.of(WorkerAction.values()));
    }

    /**
     * A 0.4.9 worker document. Every field that version saved is present, and fields added later stay at their defaults.
     */
    @Test
    void version049SaveKeepsEveryStoredField() {
        UUID owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID friend = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID come = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String json = """
                {
                  "owner": "%s",
                  "trusted": [{"id": "%s", "name": "Friend"}],
                  "job": "builder",
                  "activity": "coming",
                  "rested": true,
                  "slept_last_night": true,
                  "last_dawn_day": 12,
                  "home_bed": {"dimension": "minecraft:overworld", "pos": [1, 2, 3]},
                  "lumberjack": {
                    "area": {"dimension": "minecraft:overworld", "min": [0, 1, 0], "max": [3, 4, 5]},
                    "clear_leaves": true,
                    "replant_saplings": true,
                    "log_chest": [2, 3, 4],
                    "sapling_chest": [5, 6, 7],
                    "tool_chest": [8, 9, 10]
                  },
                  "miner": {
                    "mode": "tunnel",
                    "area": {"dimension": "minecraft:overworld", "min": [1, 1, 1], "max": [2, 2, 2]},
                    "origin": {"dimension": "minecraft:overworld", "pos": [4, 5, 6], "facing": "north"},
                    "width": 5,
                    "height": 4,
                    "length": 20,
                    "wall_block": "minecraft:cobblestone",
                    "branch_spacing": 6,
                    "branch_length": 9,
                    "shaft_width": 3,
                    "shaft_depth": 12,
                    "shaft_up": true,
                    "valuables": ["minecraft:diamond_ore"],
                    "valuables_chest": [1, 2, 3],
                    "regular_chest": [4, 5, 6],
                    "tool_chest": [7, 8, 9]
                  },
                  "builder": {
                    "schematic_anchor": [10, 11, 12],
                    "material_chests": [[13, 14, 15], [16, 17, 18]]
                  },
                  "break_whitelist": ["minecraft:oak_log", "create:andesite_casing"],
                  "problem": {"code": "stuck", "subject": "oak"},
                  "come_target": "%s",
                  "alerts": {
                    "hungry_notified_at": 100,
                    "hurt_notified_at": 200,
                    "hungry_pending": true,
                    "hurt_pending": false,
                    "notices": [{"code": "stuck", "notified_at": 50, "pending": true, "subject": "tree"}]
                  }
                }
                """.formatted(owner, friend, come);
        WorkerData data = WorkerData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assert049(data, owner, friend, come);
        WorkerData again = WorkerData.CODEC.parse(JsonOps.INSTANCE, WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow()).getOrThrow();
        assert049(again, owner, friend, come);
    }

    private static void assert049(WorkerData data, UUID owner, UUID friend, UUID come) {
        assertEquals(owner, data.owner());
        assertEquals(List.of(owner), data.allOwners());
        assertEquals(owner, data.originalOwner());
        assertEquals(1, data.trusted().size());
        assertEquals(friend, data.trusted().get(0).id());
        assertEquals("Friend", data.trusted().get(0).name());
        assertEquals(WorkerJob.BUILDER, data.job());
        assertEquals(WorkerActivity.COMING, data.activity());
        assertTrue(data.rested());
        assertTrue(data.sleptLastNight());
        assertEquals(12L, data.lastDawnDay());
        assertEquals(new BlockPos(1, 2, 3), data.homeBed().pos());
        assertEquals("minecraft:overworld", data.homeBed().dimension().location().toString());
        assertTrue(data.lumberjack().clearLeaves());
        assertTrue(data.lumberjack().replantSaplings());
        assertEquals(new BlockPos(0, 1, 0), data.lumberjack().area().orElseThrow().min());
        assertEquals(new BlockPos(3, 4, 5), data.lumberjack().area().orElseThrow().max());
        assertEquals(new BlockPos(2, 3, 4), data.lumberjack().logChest().orElseThrow());
        assertEquals(new BlockPos(5, 6, 7), data.lumberjack().saplingChest().orElseThrow());
        assertEquals(new BlockPos(8, 9, 10), data.lumberjack().toolChest().orElseThrow());
        assertEquals(MinerMode.TUNNEL, data.miner().mode());
        assertEquals(new BlockPos(4, 5, 6), data.miner().origin().orElseThrow().pos());
        assertEquals("north", data.miner().origin().orElseThrow().facing());
        assertEquals(5, data.miner().width());
        assertEquals(4, data.miner().height());
        assertEquals(20, data.miner().length());
        assertEquals("minecraft:cobblestone", data.miner().wallBlock());
        assertEquals(6, data.miner().branchSpacing());
        assertEquals(9, data.miner().branchLength());
        assertEquals(3, data.miner().shaftWidth());
        assertEquals(12, data.miner().shaftDepth());
        assertTrue(data.miner().shaftUp());
        assertEquals(List.of(ResourceLocation.parse("minecraft:diamond_ore")), data.miner().valuables());
        assertTrue(data.miner().useValuables());
        assertEquals(new BlockPos(1, 2, 3), data.miner().valuablesChest().orElseThrow());
        assertEquals(new BlockPos(4, 5, 6), data.miner().regularChest().orElseThrow());
        assertEquals(new BlockPos(7, 8, 9), data.miner().toolChest().orElseThrow());
        assertEquals(new BlockPos(10, 11, 12), data.builder().schematicAnchor().orElseThrow());
        assertEquals(List.of(new BlockPos(13, 14, 15), new BlockPos(16, 17, 18)), data.builder().materialChests());
        assertEquals(
                List.of(ResourceLocation.parse("minecraft:oak_log"), ResourceLocation.parse("create:andesite_casing")),
                data.breakWhitelist());
        assertEquals(ProblemCode.STUCK, data.problem().code());
        assertEquals("oak", data.problem().subject());
        assertEquals(come, data.comeTarget());
        assertEquals(100L, data.hungryNotifiedAt());
        assertEquals(200L, data.hurtNotifiedAt());
        assertTrue(data.hungryPending());
        assertFalse(data.hurtPending());
        assertEquals(1, data.notices().size());
        assertEquals("stuck", data.notices().get(0).code());
        assertEquals(50L, data.notices().get(0).notifiedAt());
        assertTrue(data.notices().get(0).pending());
        assertEquals("tree", data.notices().get(0).subject());
        assertEquals(WorkerRank.WORKER, data.rank());
        assertEquals(null, data.foreman());
        assertFalse(data.rulesOverride());
        assertFalse(data.plainBlocks());
        assertTrue(data.scaffoldPosts().isEmpty());
        assertTrue(data.carryIsEmpty());
    }
}

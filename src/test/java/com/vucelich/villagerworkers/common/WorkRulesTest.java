package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class WorkRulesTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test
    void breakRulesRejectUnsafeBlocksEvenWhenWhitelisted() {
        assertFalse(allow(false, true, true, true, 1, "minecraft:stone", false, false, false, true));
        assertFalse(allow(true, false, true, true, 1, "minecraft:stone", false, false, false, true));
        assertFalse(allow(true, true, false, true, 1, "minecraft:stone", false, false, false, true));
        assertFalse(allow(true, true, true, false, 1, "minecraft:stone", false, false, false, true));
        assertFalse(allow(true, true, true, true, -1, "minecraft:stone", false, false, true, true));
        for (String path : BreakRules.GLOBAL_DENY) {
            assertFalse(allow(true, true, true, true, 1, "minecraft:" + path, false, false, true, true), path);
        }
        assertFalse(allow(true, true, true, true, 1, "minecraft:chest", true, false, false, true));
        assertTrue(allow(true, true, true, true, 1, "minecraft:chest", true, false, true, true));
        for (String namespace : BreakRules.PROTECTED_NAMESPACES) {
            assertFalse(allow(true, true, true, true, 1, namespace + ":cog", false, false, false, true), namespace);
            assertTrue(allow(true, true, true, true, 1, namespace + ":cog", false, false, true, true), namespace);
        }
        assertFalse(allow(true, true, true, true, 1, "minecraft:chest", false, true, true, true));
        assertFalse(allow(true, true, true, true, 1, "minecraft:oak_log", false, false, false, false));
        assertTrue(allow(true, true, true, true, 1, "minecraft:stone", false, false, false, true));
        assertTrue(BreakRules.PROTECTED_NAMESPACES.contains("simulated"));
        assertTrue(BreakRules.PROTECTED_NAMESPACES.contains("sable"));
        assertTrue(BreakRules.PROTECTED_NAMESPACES.contains("offroad"));
        assertTrue(BreakRules.PROTECTED_NAMESPACES.contains("createbigcannons"));
        assertTrue(BreakRules.PROTECTED_NAMESPACES.contains("railways"));
        assertTrue(BreakRules.PROTECTED_NAMESPACES.contains("numismatics"));
        for (String path : BreakRules.PROTECTED_PATHS) {
            assertFalse(allow(true, true, true, true, 1, "minecraft:" + path, false, false, false, true), path);
            assertTrue(allow(true, true, true, true, 1, "minecraft:" + path, false, false, true, true), path);
        }
    }

    @Test
    void tunnelBranchAndShaftVolumesMatchTheMarkedShape() {
        BlockPos origin = new BlockPos(0, 64, 0);
        WorkerData tunnel = miner(MinerMode.TUNNEL, origin, "east", 3, 2, 2, "minecraft:cobblestone", 4, 2, 2, 2, false);
        assertEquals(12, WorkVolumes.digCount(tunnel));
        assertEquals(14, WorkVolumes.wallCount(tunnel));
        assertTrue(WorkVolumes.isDig(tunnel, OVERWORLD, origin));
        assertTrue(WorkVolumes.isDig(tunnel, OVERWORLD, new BlockPos(1, 65, 1)));
        assertTrue(WorkVolumes.isDig(tunnel, OVERWORLD, new BlockPos(1, 64, -1)));
        assertFalse(WorkVolumes.isDig(tunnel, OVERWORLD, new BlockPos(2, 64, 0)));
        assertFalse(WorkVolumes.isDig(tunnel, OVERWORLD, new BlockPos(0, 66, 0)));
        assertFalse(WorkVolumes.isWall(tunnel, OVERWORLD, origin));
        assertTrue(WorkVolumes.isWall(tunnel, OVERWORLD, new BlockPos(0, 64, 2)));
        assertTrue(WorkVolumes.isWall(tunnel, OVERWORLD, new BlockPos(0, 66, 0)));
        assertFalse(WorkVolumes.isWall(tunnel, OVERWORLD, new BlockPos(0, 63, 0)));
        assertCells(tunnel);

        WorkerData open = miner(MinerMode.TUNNEL, origin, "east", 3, 2, 2, "", 4, 2, 2, 2, false);
        assertEquals(0, WorkVolumes.wallCount(open));
        assertFalse(WorkVolumes.isWall(open, OVERWORLD, new BlockPos(0, 66, 0)));

        WorkerData branch = miner(MinerMode.BRANCH, origin, "east", 1, 2, 8, "minecraft:cobblestone", 4, 2, 2, 2, false);
        assertTrue(WorkVolumes.isDig(branch, OVERWORLD, origin));
        assertFalse(WorkVolumes.isDig(branch, OVERWORLD, new BlockPos(0, 64, 1)));
        assertTrue(WorkVolumes.isDig(branch, OVERWORLD, new BlockPos(4, 64, 1)));
        assertTrue(WorkVolumes.isDig(branch, OVERWORLD, new BlockPos(4, 65, -2)));
        assertFalse(WorkVolumes.isDig(branch, OVERWORLD, new BlockPos(4, 64, 3)));
        assertFalse(WorkVolumes.isDig(branch, OVERWORLD, new BlockPos(2, 64, 1)));
        assertCells(branch);

        BlockPos shaftOrigin = new BlockPos(0, 10, 0);
        WorkerData down = miner(MinerMode.SHAFT, shaftOrigin, "north", 3, 2, 2, "", 4, 2, 2, 2, false);
        assertTrue(WorkVolumes.isDig(down, OVERWORLD, shaftOrigin));
        assertTrue(WorkVolumes.isDig(down, OVERWORLD, new BlockPos(1, 9, 1)));
        assertFalse(WorkVolumes.isDig(down, OVERWORLD, new BlockPos(2, 10, 0)));
        assertFalse(WorkVolumes.isDig(down, OVERWORLD, new BlockPos(0, 8, 0)));
        assertFalse(WorkVolumes.isWall(down, OVERWORLD, shaftOrigin));
        assertCells(down);

        WorkerData up = miner(MinerMode.SHAFT, shaftOrigin, "north", 3, 2, 2, "", 4, 2, 2, 2, true);
        assertTrue(WorkVolumes.isDig(up, OVERWORLD, new BlockPos(1, 11, 1)));
        assertFalse(WorkVolumes.isDig(up, OVERWORLD, new BlockPos(0, 9, 0)));
        assertCells(up);
    }

    @Test
    void clearBoxIsInclusiveAndIndexedFromTheTop() {
        WorkerData data = WorkerData.hire(UUID.randomUUID());
        data.setJob(WorkerJob.LUMBERJACK);
        data.setLumberjack(data.lumberjack().withArea(Optional.of(
                new WorkArea(OVERWORLD, new BlockPos(1, 2, 0), new BlockPos(0, 0, 0)))));
        assertEquals(6, WorkVolumes.digCount(data));
        assertEquals(new BlockPos(0, 2, 0), WorkVolumes.digAt(data, 0));
        assertTrue(WorkVolumes.isDig(data, OVERWORLD, new BlockPos(1, 0, 0)));
        assertFalse(WorkVolumes.isDig(data, OVERWORLD, new BlockPos(2, 0, 0)));
        assertFalse(WorkVolumes.isDig(data, ResourceLocation.parse("minecraft:the_nether"), new BlockPos(0, 0, 0)));
        assertCells(data);
    }

    @Test
    void saplingsFollowTheVanillaWood() {
        assertEquals("minecraft:oak_sapling", SaplingKinds.saplingForLog("minecraft:oak_log"));
        assertEquals("minecraft:oak_sapling", SaplingKinds.saplingForLog("minecraft:stripped_oak_log"));
        assertEquals("minecraft:oak_sapling", SaplingKinds.saplingForLog("minecraft:oak_wood"));
        assertEquals("minecraft:mangrove_propagule", SaplingKinds.saplingForLog("minecraft:mangrove_log"));
        assertEquals("minecraft:crimson_fungus", SaplingKinds.saplingForLog("minecraft:crimson_stem"));
        assertEquals("minecraft:warped_fungus", SaplingKinds.saplingForLog("minecraft:warped_hyphae"));
        assertEquals("minecraft:cherry_sapling", SaplingKinds.saplingForLog("minecraft:cherry_log"));
        assertEquals("minecraft:pale_oak_sapling", SaplingKinds.saplingForLog("minecraft:pale_oak_log"));
        assertNull(SaplingKinds.saplingForLog("minecraft:bamboo_block"));
        assertNull(SaplingKinds.saplingForLog("minecraft:stone"));
        assertNull(SaplingKinds.saplingForLog("create:oak_log"));
    }

    @Test
    void restedWorkersMoveAndBreakFaster() {
        assertEquals(10, ActionPace.ticksBetweenActions(true));
        assertEquals(20, ActionPace.ticksBetweenActions(false));
        assertEquals(1.25F, ActionPace.breakMultiplier(true));
        assertEquals(0.75F, ActionPace.breakMultiplier(false));
    }

    @Test
    void rescanWaitStaysUnderATenthOfTheCycle() {
        assertEquals(ActionPace.RESCAN_TICKS, ActionPace.rescanDelay(0));
        assertEquals(ActionPace.RESCAN_TICKS, ActionPace.rescanDelay(80));
        assertEquals(810L, ActionPace.rescanDelay(81));
        long delay = ActionPace.rescanDelay(500);
        assertEquals(5000L, delay);
        assertTrue(500.0 / (delay + 500) < 0.10);
        UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        int jitter = ActionPace.rescanJitter(id);
        assertTrue(jitter >= 0 && jitter <= ActionPace.RESCAN_JITTER);
        assertEquals(jitter, ActionPace.rescanJitter(id));
        assertEquals(0, ActionPace.rescanJitter(null));
    }

    @Test
    void workProblemsStayOnTheStatusLine() {
        WorkerProblem full = new WorkerProblem(ProblemCode.CHEST_FULL, "");
        assertEquals("chest full", StatusText.problem(false, false, full));
        assertEquals("hungry. chest full", StatusText.problem(true, false, full));
        assertEquals(
                "[Birch] lumberjack, working, rested, 20/20 health. chest full",
                StatusText.line("Birch", WorkerJob.LUMBERJACK, WorkerActivity.WORKING, true, 20, 20, false, false, full));
        assertEquals("[Birch] is out of minecraft:oak_sapling.", StatusText.ownerAlert("Birch", ProblemCode.OUT_OF_MATERIALS, "minecraft:oak_sapling"));
        WorkerProblem locked = new WorkerProblem(ProblemCode.NO_ACCESS, "");
        assertEquals("cannot open a container", StatusText.problem(false, false, locked));
        assertEquals("[Birch] cannot open that container.", StatusText.ownerAlert("Birch", ProblemCode.NO_ACCESS, ""));
        assertEquals("[Birch] will not dig because the ship check failed.", StatusText.ownerAlert("Birch", ProblemCode.SHIP_GUARD, ""));
    }

    @Test
    void waterloggedBreakSucceedsWhenTheBlockIsGone() {
        assertTrue(BreakOutcome.succeeded(true, false));
        assertTrue(BreakOutcome.succeeded(false, false));
        assertFalse(BreakOutcome.succeeded(false, true));
        assertFalse(BreakOutcome.shouldRestore(false));
        assertTrue(BreakOutcome.shouldRestore(true));
    }

    @Test
    void builderPlacesOnlyIntoReplaceableCells() {
        assertEquals(PlacementRules.Choice.SKIP, PlacementRules.choose(true, false, true, true));
        assertEquals(PlacementRules.Choice.PLACE, PlacementRules.choose(false, false, true, true));
        assertEquals(PlacementRules.Choice.MISSING, PlacementRules.choose(false, false, true, false));
        assertEquals(PlacementRules.Choice.BLOCKED, PlacementRules.choose(false, false, false, true));
        assertEquals(PlacementRules.Choice.SKIP, PlacementRules.choose(false, true, true, false));
        assertEquals(PlacementRules.Choice.BLOCKED, PlacementRules.choose(false, true, false, true));
    }

    @Test
    void comeTargetIsClearedWhenTheWorkerStops() {
        WorkerData data = WorkerData.hire(UUID.randomUUID());
        UUID player = UUID.randomUUID();
        data.comeTo(player);
        assertEquals(WorkerActivity.COMING, data.activity());
        assertEquals(player, data.comeTarget());
        data.setActivity(WorkerActivity.STOPPED);
        assertNull(data.comeTarget());
    }

    private static void assertCells(WorkerData data) {
        Set<BlockPos> dig = new HashSet<>();
        for (int i = 0; i < WorkVolumes.digCount(data); i++) {
            BlockPos pos = WorkVolumes.digAt(data, i);
            assertTrue(WorkVolumes.isDig(data, OVERWORLD, pos), pos.toString());
            assertFalse(WorkVolumes.isWall(data, OVERWORLD, pos), pos.toString());
            assertTrue(dig.add(pos), pos.toString());
        }
        Set<BlockPos> walls = new HashSet<>();
        for (int i = 0; i < WorkVolumes.wallCount(data); i++) {
            BlockPos pos = WorkVolumes.wallAt(data, i);
            assertTrue(WorkVolumes.isWall(data, OVERWORLD, pos), pos + " index " + i);
            assertFalse(WorkVolumes.isDig(data, OVERWORLD, pos), pos.toString());
            assertTrue(walls.add(pos), pos.toString());
        }
    }

    private static WorkerData miner(
            MinerMode mode,
            BlockPos origin,
            String facing,
            int width,
            int height,
            int length,
            String wall,
            int spacing,
            int branchLength,
            int shaftWidth,
            int shaftDepth,
            boolean shaftUp) {
        WorkerData data = WorkerData.hire(UUID.randomUUID());
        data.setJob(WorkerJob.MINER);
        data.setActivity(WorkerActivity.WORKING);
        data.setMiner(new MinerConfig(
                mode,
                Optional.empty(),
                Optional.of(new WorkOrigin(OVERWORLD, origin, facing)),
                width,
                height,
                length,
                wall,
                spacing,
                branchLength,
                shaftWidth,
                shaftDepth,
                shaftUp,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                true));
        return data;
    }

    private static boolean allow(
            boolean working,
            boolean inside,
            boolean reach,
            boolean sameDimension,
            float destroySpeed,
            String blockId,
            boolean blockEntity,
            boolean reserved,
            boolean whitelisted,
            boolean lumberOk) {
        return BreakRules.allow(working, inside, reach, sameDimension, destroySpeed, blockId, blockEntity, reserved, whitelisted, lumberOk);
    }
}

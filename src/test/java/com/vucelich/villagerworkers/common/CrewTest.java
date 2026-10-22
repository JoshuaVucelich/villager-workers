package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class CrewTest {
    private final UUID player = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID foremanId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID otherForeman = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private final UUID one = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private final UUID two = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private final UUID three = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @Test
    void assignThreeThenRejectAForeman() {
        WorkerData foreman = foreman();
        WorkerData a = worker();
        WorkerData b = worker();
        WorkerData c = worker();
        assertTrue(CrewCommands.assign(a, one, foreman, foremanId));
        assertTrue(CrewCommands.assign(b, two, foreman, foremanId));
        assertTrue(CrewCommands.assign(c, three, foreman, foremanId));
        assertEquals(3, foreman.crewSize());
        WorkerData alsoForeman = foreman();
        assertFalse(CrewCommands.assign(alsoForeman, otherForeman, foreman, foremanId));
        assertEquals(3, foreman.crewSize());
    }

    @Test
    void unassignMoveAndGive() {
        WorkerData foreman = foreman();
        WorkerData other = foreman();
        WorkerData member = worker();
        WorkerData stay = worker();
        CrewCommands.assign(member, one, foreman, foremanId);
        CrewCommands.assign(stay, two, foreman, foremanId);
        assertTrue(CrewCommands.unassign(member, one, foreman));
        assertEquals(1, foreman.crewSize());
        assertEquals(null, member.crewForeman());
        CrewCommands.assign(member, one, foreman, foremanId);
        assertTrue(CrewCommands.unassign(member, one, foreman));
        assertTrue(CrewCommands.assign(member, one, other, otherForeman));
        assertEquals(1, foreman.crewSize());
        assertEquals(1, other.crewSize());
        assertEquals(otherForeman, member.crewForeman());
        member.transferSole(new TrustedPlayer(player, "Ada"));
        other.removeCrewMember(one);
        assertEquals(1, member.ownerCount());
        assertEquals(player, member.owner());
        assertEquals(0, other.crewSize());
    }

    @Test
    void commandTargets() {
        List<UUID> crew = List.of(one, two, three);
        assertEquals(1, CrewCommands.select(foremanId, crew, CrewCommands.Target.FOREMAN, List.of()).size());
        assertEquals(4, CrewCommands.select(foremanId, crew, CrewCommands.Target.CREW, List.of()).size());
        List<UUID> ticked = CrewCommands.select(foremanId, crew, CrewCommands.Target.TICKED, List.of(two, UUID.randomUUID()));
        assertEquals(List.of(two), ticked);
    }

    @Test
    void overrideSwitchesAllFiveRuleFields() {
        WorkerData foreman = foreman();
        foreman.setJob(WorkerJob.LUMBERJACK);
        foreman.setLumberjack(area(1));
        foreman.setMiner(foreman.miner().withNumbers(MinerMode.TUNNEL, 3, 3, 8, "", 4, 8, 2, 16, false, List.of()));
        foreman.setBuilder(new BuilderConfig(java.util.Optional.of(new BlockPos(4, 5, 6)), List.of()));
        foreman.setBreakWhitelist(List.of(ResourceLocation.parse("minecraft:stone")));

        WorkerData worker = worker();
        worker.setJob(WorkerJob.MINER);
        worker.setLumberjack(area(9));
        worker.setMiner(worker.miner().withNumbers(MinerMode.SHAFT, 1, 2, 1, "", 4, 8, 2, 4, true, List.of()));
        worker.setBuilder(BuilderConfig.defaults());
        worker.setBreakWhitelist(List.of(ResourceLocation.parse("minecraft:dirt")));
        worker.setCrewForeman(foremanId);

        CrewRules.View fromForeman = CrewRules.effective(worker, foreman);
        assertEquals(5, CrewRules.matchingFields(fromForeman, CrewRules.own(foreman)));
        assertEquals(0, CrewRules.matchingFields(fromForeman, CrewRules.own(worker)));

        worker.setOverrideForeman(true);
        CrewRules.View own = CrewRules.effective(worker, foreman);
        assertEquals(5, CrewRules.matchingFields(own, CrewRules.own(worker)));
    }

    @Test
    void ordersHitTheChosenMembersOnly() {
        WorkerData foreman = foreman();
        foreman.setJob(WorkerJob.MINER);
        WorkerData a = worker();
        a.setJob(WorkerJob.MINER);
        WorkerData b = worker();
        b.setJob(WorkerJob.MINER);
        CrewCommands.assign(a, one, foreman, foremanId);
        CrewCommands.assign(b, two, foreman, foremanId);
        List<UUID> targets = CrewCommands.select(foremanId, foreman.crewMembers(), CrewCommands.Target.CREW, List.of());
        assertEquals(3, targets.size());
        for (WorkerData data : List.of(foreman, a, b)) {
            CrewCommands.apply(data, CrewCommands.Order.WORK, player, true);
        }
        assertEquals(WorkerActivity.WORKING, foreman.activity());
        assertEquals(WorkerActivity.WORKING, a.activity());
        assertEquals(WorkerActivity.WORKING, b.activity());

        a.setActivity(WorkerActivity.IDLE);
        b.setActivity(WorkerActivity.IDLE);
        foreman.setActivity(WorkerActivity.IDLE);
        List<UUID> onlyB = CrewCommands.select(foremanId, foreman.crewMembers(), CrewCommands.Target.TICKED, List.of(two));
        assertEquals(List.of(two), onlyB);
        CrewCommands.apply(b, CrewCommands.Order.FOLLOW, player, true);
        assertEquals(WorkerActivity.COMING, b.activity());
        assertEquals(WorkerActivity.IDLE, a.activity());
        assertEquals(WorkerActivity.IDLE, foreman.activity());
        CrewCommands.apply(foreman, CrewCommands.Order.STAY, player, true);
        assertEquals(WorkerActivity.STOPPED, foreman.activity());
    }

    @Test
    void pushRulesRestoresTheWorkersOwnJob() {
        WorkerData worker = worker();
        worker.setJob(WorkerJob.MINER);
        WorkerData foreman = foreman();
        foreman.setJob(WorkerJob.LUMBERJACK);
        worker.setCrewForeman(foremanId);
        worker.pushRules(CrewRules.effective(worker, foreman));
        assertEquals(WorkerJob.LUMBERJACK, worker.job());
        worker.popRules();
        assertEquals(WorkerJob.MINER, worker.job());
        worker.setOverrideForeman(true);
        worker.pushRules(CrewRules.effective(worker, foreman));
        assertEquals(WorkerJob.MINER, worker.job());
        worker.popRules();
        assertEquals(WorkerJob.MINER, worker.job());
    }

    @Test
    void assignmentRoundTrips() {
        WorkerData foreman = foreman();
        WorkerData worker = worker();
        CrewCommands.assign(worker, one, foreman, foremanId);
        worker.setOverrideForeman(true);
        WorkerData loadedWorker = WorkerData.CODEC.parse(NbtOps.INSTANCE, WorkerData.CODEC.encodeStart(NbtOps.INSTANCE, worker).getOrThrow()).getOrThrow();
        WorkerData loadedForeman = WorkerData.CODEC.parse(NbtOps.INSTANCE, WorkerData.CODEC.encodeStart(NbtOps.INSTANCE, foreman).getOrThrow()).getOrThrow();
        assertEquals(foremanId, loadedWorker.crewForeman());
        assertTrue(loadedWorker.overrideForeman());
        assertEquals(List.of(one), loadedForeman.crewMembers());
        assertEquals(1, loadedForeman.crewSize());
    }

    private WorkerData foreman() {
        WorkerData data = WorkerData.hire(player);
        data.setForeman(true);
        return data;
    }

    private WorkerData worker() {
        return WorkerData.hire(player);
    }

    private static LumberjackConfig area(int x) {
        WorkArea area = new WorkArea(ResourceLocation.parse("minecraft:overworld"), new BlockPos(x, 64, 0), new BlockPos(x + 2, 70, 2));
        return new LumberjackConfig(java.util.Optional.of(area), false, false, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
    }
}

package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrewLogicTest {
    private final UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID foremanId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private final UUID memberA = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private final UUID memberB = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private final UUID stranger = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @Test
    void foremanCannotJoinACrew() {
        assertFalse(CrewLogic.canAssign(WorkerRank.FOREMAN, foremanId, memberA));
        assertFalse(CrewLogic.canAssign(WorkerRank.WORKER, memberA, memberA));
        assertTrue(CrewLogic.canAssign(WorkerRank.WORKER, memberA, foremanId));
    }

    @Test
    void oneRuleChangeReachesTheCrewAndSkipsAnOverride() {
        WorkerData foreman = WorkerData.hire(owner);
        foreman.setRank(WorkerRank.FOREMAN);
        foreman.setScaffoldOrder("spare,vanilla");
        foreman.setPlainBlocks(true);
        WorkerData first = WorkerData.hire(owner);
        WorkerData second = WorkerData.hire(owner);
        WorkerData opted = WorkerData.hire(owner);
        opted.setRulesOverride(true);
        opted.setScaffoldOrder("vanilla");
        opted.setPlainBlocks(false);
        int updated = CrewLogic.applyScaffoldRule(foreman, List.of(first, second, opted));
        assertEquals(2, updated);
        assertEquals("spare,vanilla", first.scaffoldOrder());
        assertTrue(first.plainBlocks());
        assertEquals("spare,vanilla", second.scaffoldOrder());
        assertTrue(second.plainBlocks());
        assertEquals("vanilla", opted.scaffoldOrder());
        assertFalse(opted.plainBlocks());
        assertEquals(List.of(ScaffoldKinds.SPARE), CrewLogic.effectiveScaffold(first, foreman));
        assertEquals(List.of(ScaffoldKinds.VANILLA), CrewLogic.effectiveScaffold(opted, foreman));
    }

    @Test
    void commandTargetsAreForemanCrewOrTickedMembers() {
        List<UUID> crew = List.of(memberA, memberB);
        assertEquals(List.of(foremanId), CrewLogic.targets(CrewLogic.Scope.FOREMAN, foremanId, crew, List.of(memberA)));
        assertEquals(List.of(foremanId, memberA, memberB), CrewLogic.targets(CrewLogic.Scope.CREW, foremanId, crew, List.of()));
        assertEquals(List.of(memberA), CrewLogic.targets(CrewLogic.Scope.TICKED, foremanId, crew, List.of(memberA, stranger)));
        assertEquals(List.of(foremanId, memberB), CrewLogic.targets(CrewLogic.Scope.TICKED, foremanId, crew, List.of(foremanId, memberB)));
    }

    @Test
    void crewFieldsReloadAndOldSavesStayUnassigned() {
        WorkerData data = WorkerData.hire(owner);
        data.setForeman(foremanId);
        data.setRulesOverride(true);
        data.setScaffoldOrder("andesite,spare");
        WorkerData again = WorkerData.CODEC
                .parse(JsonOps.INSTANCE, WorkerData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow())
                .getOrThrow();
        assertEquals(foremanId, again.foreman());
        assertTrue(again.rulesOverride());
        assertEquals("andesite,spare", again.scaffoldOrder());
        String old = "{\"owner\":\"" + owner + "\"}";
        WorkerData legacy = WorkerData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(old)).getOrThrow();
        assertEquals(null, legacy.foreman());
        assertFalse(legacy.rulesOverride());
        assertEquals("", legacy.scaffoldOrder());
    }

    @Test
    void defaultScaffoldOrderIsAndesiteThenVanillaThenSpare() {
        assertEquals(
                List.of(ScaffoldKinds.ANDESITE, ScaffoldKinds.VANILLA, ScaffoldKinds.SPARE),
                ScaffoldKinds.parse("", false));
        assertEquals(List.of(ScaffoldKinds.SPARE), ScaffoldKinds.parse("andesite,vanilla,spare", true));
    }
}

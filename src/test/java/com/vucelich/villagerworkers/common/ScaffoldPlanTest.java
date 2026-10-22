package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ScaffoldPlanTest {
    @Test
    void highTargetBuildsARisingStair() {
        List<BlockPos> stair = ScaffoldPlan.stair(new BlockPos(0, 64, 0), new BlockPos(0, 68, 6));
        assertEquals(4, stair.size());
        assertEquals(65, stair.get(1).getY());
        assertTrue(stair.get(1).getZ() > stair.get(0).getZ());
        assertEquals(67, stair.get(3).getY());
    }

    @Test
    void sameHeightNeedsNoStair() {
        assertEquals(0, ScaffoldPlan.stair(new BlockPos(0, 64, 0), new BlockPos(3, 64, 0)).size());
    }

    @Test
    void preferenceOrder() {
        assertEquals("create:andesite_scaffolding", ScaffoldPlan.choose(ScaffoldPreference.VANILLA, false, true, false));
        assertEquals("", ScaffoldPlan.choose(ScaffoldPreference.VANILLA, false, false, false));
        assertEquals("minecraft:scaffolding", ScaffoldPreference.VANILLA.needed());
        assertEquals("spare", ScaffoldPlan.choose(ScaffoldPreference.PLAIN, true, true, true));
        assertEquals("minecraft:scaffolding", ScaffoldPlan.choose(ScaffoldPreference.ANDESITE, true, false, true));
    }

    @Test
    void teardownStartsAtTheTop() {
        List<BlockPos> order = ScaffoldPlan.topDown(List.of(new BlockPos(0, 1, 0), new BlockPos(0, 3, 0), new BlockPos(0, 2, 0)));
        assertEquals(3, order.get(0).getY());
        assertEquals(2, order.get(1).getY());
        assertEquals(1, order.get(2).getY());
    }

    @Test
    void foremanPreferenceCoversTheCrewUntilOverride() {
        UUID owner = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        WorkerData foreman = WorkerData.hire(owner);
        foreman.setForeman(true);
        foreman.setBuilder(BuilderConfig.defaults().withScaffold(ScaffoldPreference.PLAIN));
        WorkerData member = WorkerData.hire(owner);
        member.setBuilder(BuilderConfig.defaults().withScaffold(ScaffoldPreference.VANILLA));
        member.pushRules(CrewRules.effective(member, foreman));
        assertEquals(ScaffoldPreference.PLAIN, member.builder().scaffold());
        member.popRules();
        assertEquals(ScaffoldPreference.VANILLA, member.builder().scaffold());

        WorkerData own = WorkerData.hire(owner);
        own.setOverrideForeman(true);
        own.setBuilder(BuilderConfig.defaults().withScaffold(ScaffoldPreference.VANILLA));
        assertEquals(ScaffoldPreference.VANILLA, CrewRules.effective(own, foreman).builder().scaffold());
        assertEquals(ScaffoldPreference.VANILLA, own.builder().scaffold());
    }
}

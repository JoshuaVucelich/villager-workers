package com.vucelich.villagerworkers.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vucelich.villagerworkers.common.ActionPace;
import com.vucelich.villagerworkers.common.ProblemCode;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AssemblyGuardsTest {
    private static final Object CONTAINER = new Object();
    private static final Object PLOT = new Object();
    private static final BlockPos POS = new BlockPos(2, 1, 2);

    private enum Mode {
        THROW,
        THROW_SUB,
        NULL_CONTAINER,
        OUT_OF_BOUNDS,
        NO_PLOT,
        OK
    }

    private static Mode mode = Mode.THROW;

    @AfterEach
    void reset() {
        AssemblyGuards.resetShipForTest();
        mode = Mode.THROW;
    }

    @Test
    void shipLookupFailsClosedLogsOnceAndResetsOnStop() throws Exception {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        AssemblyGuards.installShipLookupForTest(
                lookup.findStatic(AssemblyGuardsTest.class, "container", MethodType.methodType(Object.class, ServerLevel.class)),
                lookup.findStatic(
                        AssemblyGuardsTest.class, "inBounds", MethodType.methodType(boolean.class, Object.class, BlockPos.class)),
                lookup.findStatic(
                        AssemblyGuardsTest.class, "plot", MethodType.methodType(Object.class, Object.class, ChunkPos.class)),
                lookup.findStatic(AssemblyGuardsTest.class, "sub", MethodType.methodType(Object.class, Object.class)));

        mode = Mode.THROW;
        assertTrue(AssemblyGuards.probeForTest(0L, POS, true), "failed lookup is occupied");
        assertTrue(AssemblyGuards.shipGuardFailed(), "guard fails closed");
        assertEquals(1, AssemblyGuards.shipFailureLogs, "first failure logs");
        assertTrue(AssemblyGuards.probeForTest(1L, POS, true));
        assertEquals(1, AssemblyGuards.shipFailureLogs, "cooldown does not log again");

        ProblemCode notice = noticeAfter(0L);
        mode = Mode.OUT_OF_BOUNDS;
        assertFalse(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS, POS, true));
        notice = noticeAfter(notice);
        mode = Mode.NULL_CONTAINER;
        assertFalse(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS, POS, true));
        notice = noticeAfter(notice);
        mode = Mode.NO_PLOT;
        assertFalse(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS, POS, true));
        notice = noticeAfter(notice);
        mode = Mode.THROW_SUB;
        assertTrue(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS, POS, true));
        notice = noticeAfter(notice);
        assertEquals(1, AssemblyGuards.shipFailureLogs, "sublevel failure is a quiet retry");

        mode = Mode.OK;
        long afterRetry = ActionPace.SHIP_RETRY_TICKS * 2L;
        AssemblyGuards.pollForTest(afterRetry);
        notice = noticeAfter(notice);
        assertTrue(AssemblyGuards.shipGuardFailed(), "poll does not clear the failure");
        assertEquals(ProblemCode.SHIP_GUARD, notice, "notice stays steady");

        mode = Mode.THROW;
        assertTrue(AssemblyGuards.probeForTest(afterRetry, POS, true));
        assertEquals(1, AssemblyGuards.shipFailureLogs, "another retry stays quiet");
        assertEquals(ProblemCode.SHIP_GUARD, noticeAfter(notice));

        mode = Mode.OK;
        long recoveredAt = ActionPace.SHIP_RETRY_TICKS * 3L;
        assertTrue(AssemblyGuards.probeForTest(recoveredAt, POS, true), "plot and sublevel both returned");
        assertFalse(AssemblyGuards.shipGuardFailed(), "full lookup clears the failure");

        mode = Mode.THROW;
        assertTrue(AssemblyGuards.probeForTest(recoveredAt, POS, true));
        assertEquals(2, AssemblyGuards.shipFailureLogs);
        AssemblyGuards.onServerStopped(null);
        assertFalse(AssemblyGuards.shipGuardFailed(), "server stop clears the failure");
        assertTrue(AssemblyGuards.probeForTest(0L, POS, true));
        assertEquals(3, AssemblyGuards.shipFailureLogs, "the next run can log again");
    }

    private static ProblemCode noticeAfter(long ignored) {
        return noticeAfter(ProblemCode.NONE);
    }

    private static ProblemCode noticeAfter(ProblemCode current) {
        if (AssemblyGuards.shipGuardFailed()) {
            return current == ProblemCode.NONE ? ProblemCode.SHIP_GUARD : current;
        }
        return current == ProblemCode.SHIP_GUARD ? ProblemCode.NONE : current;
    }

    @SuppressWarnings("unused")
    private static Object container(ServerLevel level) {
        return mode == Mode.NULL_CONTAINER ? null : CONTAINER;
    }

    @SuppressWarnings("unused")
    private static boolean inBounds(Object container, BlockPos pos) {
        return mode != Mode.OUT_OF_BOUNDS;
    }

    @SuppressWarnings("unused")
    private static Object plot(Object container, ChunkPos pos) {
        if (mode == Mode.THROW) {
            throw new IllegalStateException("forced ship failure");
        }
        if (mode == Mode.NO_PLOT) {
            return null;
        }
        return PLOT;
    }

    @SuppressWarnings("unused")
    private static Object sub(Object plot) {
        if (mode == Mode.THROW_SUB) {
            throw new IllegalStateException("forced sublevel failure");
        }
        return plot;
    }
}

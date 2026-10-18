package com.vucelich.villagerworkers.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vucelich.villagerworkers.common.ActionPace;
import com.vucelich.villagerworkers.common.ProblemCode;
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
        assertTrue(AssemblyGuards.probeForTest(0L, POS), "failed lookup is occupied");
        assertTrue(AssemblyGuards.shipGuardFailed(), "guard fails closed");
        assertEquals(1, AssemblyGuards.shipFailureLogs, "first failure logs");
        assertTrue(AssemblyGuards.probeForTest(1L, POS));
        assertEquals(1, AssemblyGuards.shipFailureLogs, "cooldown does not log again");
        ProblemCode notice = ProblemCode.SHIP_GUARD;

        mode = Mode.OUT_OF_BOUNDS;
        assertFalse(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS, POS));
        assertFalse(AssemblyGuards.shipGuardFailed(), "a returned lookup clears the digging block");
        assertTrue(AssemblyGuards.plotHandleBroken(), "the plot handle is still the failure");
        notice = noticeAfter(notice);
        assertEquals(ProblemCode.SHIP_GUARD, notice, "the notice stays while the plot handle is broken");
        assertEquals(1, AssemblyGuards.shipFailureLogs, "a partial return keeps the log quiet");

        mode = Mode.THROW;
        assertTrue(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS, POS));
        assertEquals(1, AssemblyGuards.shipFailureLogs, "the next failure stays quiet");
        notice = noticeAfter(notice);
        assertEquals(ProblemCode.SHIP_GUARD, notice, "the notice is not raised a second time");

        mode = Mode.NULL_CONTAINER;
        AssemblyGuards.pollForTest(ActionPace.SHIP_RETRY_TICKS * 2L);
        assertFalse(AssemblyGuards.shipGuardFailed(), "poll clears a lookup that returns");
        assertTrue(AssemblyGuards.plotHandleBroken(), "poll at the origin never called the plot");
        notice = noticeAfter(notice);
        assertEquals(ProblemCode.SHIP_GUARD, notice, "poll does not clear a broken plot notice");
        assertEquals(1, AssemblyGuards.shipFailureLogs);

        mode = Mode.THROW;
        assertTrue(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS * 2L, POS));
        assertEquals(1, AssemblyGuards.shipFailureLogs);

        mode = Mode.NO_PLOT;
        assertFalse(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS * 3L, POS));
        assertFalse(AssemblyGuards.shipGuardFailed());
        assertFalse(AssemblyGuards.plotHandleBroken(), "a plot return clears the broken handle");
        notice = noticeAfter(notice);
        assertEquals(ProblemCode.NONE, notice, "the notice clears once the plot handle returns");

        mode = Mode.THROW;
        assertTrue(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS * 3L, POS));
        assertEquals(1, AssemblyGuards.shipFailureLogs);

        mode = Mode.THROW_SUB;
        assertTrue(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS * 4L, POS));
        assertTrue(AssemblyGuards.shipGuardFailed());
        assertEquals(1, AssemblyGuards.shipFailureLogs, "a thrown sublevel is a quiet retry");

        mode = Mode.OK;
        AssemblyGuards.pollForTest(ActionPace.SHIP_RETRY_TICKS * 5L);
        assertFalse(AssemblyGuards.shipGuardFailed(), "a full poll clears the digging block");

        mode = Mode.THROW;
        assertTrue(AssemblyGuards.probeForTest(ActionPace.SHIP_RETRY_TICKS * 5L, POS));
        assertEquals(2, AssemblyGuards.shipFailureLogs, "a full success allows another log");
        AssemblyGuards.onServerStopped(null);
        assertFalse(AssemblyGuards.shipGuardFailed(), "server stop clears the failure");
        assertTrue(AssemblyGuards.probeForTest(0L, POS));
        assertEquals(3, AssemblyGuards.shipFailureLogs, "server stop allows another log");
    }

    private static ProblemCode noticeAfter(ProblemCode current) {
        if (AssemblyGuards.shipGuardFailed() || AssemblyGuards.plotHandleBroken()) {
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

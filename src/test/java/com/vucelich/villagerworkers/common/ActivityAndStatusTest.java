package com.vucelich.villagerworkers.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActivityAndStatusTest {
    @Test
    void startAndResumeFollowTheJob() {
        assertEquals(WorkerActivity.IDLE, ActivityRules.afterStart(WorkerJob.NONE, false).orElseThrow());
        assertEquals(WorkerActivity.WORKING, ActivityRules.afterStart(WorkerJob.LUMBERJACK, false).orElseThrow());
        assertEquals(WorkerActivity.WORKING, ActivityRules.afterResume(WorkerJob.MINER, false).orElseThrow());
        assertTrue(ActivityRules.afterStart(WorkerJob.BUILDER, false).isEmpty());
        assertTrue(ActivityRules.afterResume(WorkerJob.BUILDER, false).isEmpty());
        assertEquals(WorkerActivity.WORKING, ActivityRules.afterStart(WorkerJob.BUILDER, true).orElseThrow());
        assertEquals(WorkerActivity.STOPPED, ActivityRules.afterStop());
        assertEquals(WorkerActivity.COMING, ActivityRules.afterCome());
    }

    @Test
    void statusLineMatchesTheAgreedShape() {
        assertEquals(
                "[Birch] lumberjack, working, rested, 20/20 health. No problems.",
                StatusText.line("Birch", WorkerJob.LUMBERJACK, WorkerActivity.WORKING, true, 20, 20, false, false));
        assertEquals("hungry, hurt", StatusText.problem(true, true));
    }

    @Test
    void minerNumbersStayInsideTheDesignLimits() {
        assertEquals(3, SetupLimits.tunnelWidth(4));
        assertEquals(7, SetupLimits.tunnelWidth(99));
        assertEquals(1, SetupLimits.tunnelWidth(0));
        assertEquals(2, SetupLimits.tunnelHeight(1));
        assertEquals(5, SetupLimits.tunnelHeight(9));
        assertEquals(128, SetupLimits.tunnelLength(500));
        assertEquals(2, SetupLimits.branchSpacing(1));
        assertEquals(64, SetupLimits.branchLength(1000));
        assertEquals(5, SetupLimits.shaftWidth(8));
        assertEquals(1, SetupLimits.shaftDepth(0));
        assertEquals(64, SetupLimits.shaftDepth(999));
    }
}

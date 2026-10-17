package com.vucelich.villagerworkers.common;

import java.util.UUID;

/** How fast a working villager breaks and how long it waits between actions. */
public final class ActionPace {
    public static final int RESTED_INTERVAL = 10;
    public static final int UNRESTED_INTERVAL = 20;
    public static final float RESTED_BREAK = 1.25F;
    public static final float UNRESTED_BREAK = 0.75F;
    public static final double REACH = 4.5;
    public static final double REACH_SQR = REACH * REACH;
    public static final double COME_DISTANCE = 2.0;
    public static final double COME_DISTANCE_SQR = COME_DISTANCE * COME_DISTANCE;
    public static final int STUCK_TICKS = 200;
    /** Shortest wait before a finished lumberjack or miner volume is scanned again (40 seconds). */
    public static final int RESCAN_TICKS = 800;
    /** Extra wait, stable for one worker, so a group does not rescan on the same tick. */
    public static final int RESCAN_JITTER = 200;
    /**
     * The wait is at least this many times the empty-scan length, so that scan stays under a tenth
     * of the cycle (scan / (wait + scan) is at most 1/11).
     */
    public static final int RESCAN_SCAN_FACTOR = 10;
    /** Try a failed Sable ship lookup again after this many ticks (30 seconds). */
    public static final int SHIP_RETRY_TICKS = 600;

    private ActionPace() {}

    public static int ticksBetweenActions(boolean rested) {
        return rested ? RESTED_INTERVAL : UNRESTED_INTERVAL;
    }

    public static float breakMultiplier(boolean rested) {
        return rested ? RESTED_BREAK : UNRESTED_BREAK;
    }

    /** Ticks to wait after a finished pass. Never shorter than {@link #RESCAN_TICKS}. */
    public static long rescanDelay(int emptyScanTicks) {
        long scan = Math.max(0, emptyScanTicks);
        return Math.max(RESCAN_TICKS, scan * RESCAN_SCAN_FACTOR);
    }

    /** Inclusive range {@code 0} through {@link #RESCAN_JITTER}, stable for one id. */
    public static int rescanJitter(UUID id) {
        if (id == null) {
            return 0;
        }
        return Math.floorMod(id.hashCode(), RESCAN_JITTER + 1);
    }
}

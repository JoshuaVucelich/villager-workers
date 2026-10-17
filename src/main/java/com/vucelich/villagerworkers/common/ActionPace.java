package com.vucelich.villagerworkers.common;

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
    /** Finished lumberjack and miner volumes are scanned again after this many ticks (40 seconds). */
    public static final int RESCAN_TICKS = 800;

    private ActionPace() {}

    public static int ticksBetweenActions(boolean rested) {
        return rested ? RESTED_INTERVAL : UNRESTED_INTERVAL;
    }

    public static float breakMultiplier(boolean rested) {
        return rested ? RESTED_BREAK : UNRESTED_BREAK;
    }
}

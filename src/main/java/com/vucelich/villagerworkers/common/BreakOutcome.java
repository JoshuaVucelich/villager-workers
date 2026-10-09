package com.vucelich.villagerworkers.common;

/**
 * A break succeeded when {@code destroyBlock} returned true, or the original block is gone.
 * Water left behind by a waterlogged block is not air, and it is still a successful break.
 * Contents are put back only when that same block is still there.
 */
public final class BreakOutcome {
    private BreakOutcome() {}

    public static boolean succeeded(boolean destroyBlockResult, boolean originalBlockRemains) {
        return destroyBlockResult || !originalBlockRemains;
    }

    public static boolean shouldRestore(boolean originalBlockRemains) {
        return originalBlockRemains;
    }
}

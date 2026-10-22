package com.vucelich.villagerworkers.common;

/**
 * Hard-hat colors drawn by the client. They are not item ids.
 */
public final class HatStyle {
    /** Yellow hard hat for a worker. */
    public static final int WORKER = 0xF5C518;
    /** White hard hat for a foreman. Different from {@link #WORKER}. */
    public static final int FOREMAN = 0xF7F7F7;
    public static final int BRIM = 0x2A2A2A;

    private HatStyle() {}

    public static int color(boolean foreman) {
        return foreman ? FOREMAN : WORKER;
    }
}

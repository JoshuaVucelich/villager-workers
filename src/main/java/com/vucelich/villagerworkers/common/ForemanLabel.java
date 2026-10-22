package com.vucelich.villagerworkers.common;

/**
 * Optional name label. The client config decides whether it is drawn.
 * Drawing it does not run on ship or contraption ticks.
 */
public final class ForemanLabel {
    public static final boolean DEFAULT_ENABLED = true;

    private ForemanLabel() {}

    public static String text(String name, int crew, boolean enabled) {
        if (!enabled) {
            return "";
        }
        String shown = name == null || name.isBlank() ? "worker" : name;
        int count = Math.max(0, crew);
        return "Foreman " + shown + " (" + count + " crew)";
    }
}

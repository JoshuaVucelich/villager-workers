package com.vucelich.villagerworkers.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config. Every gameplay switch lives here so a pack can turn it off. */
public final class WorkerSettings {
    public static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue HOLD_WHILE_MENU_OPEN;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        HOLD_WHILE_MENU_OPEN = builder
                .comment("While a player has this worker's menu open, the worker stands still and its job waits.")
                .define("holdWhileMenuOpen", true);
        SPEC = builder.build();
    }

    private WorkerSettings() {}

    public static boolean holdWhileMenuOpen() {
        return HOLD_WHILE_MENU_OPEN.get();
    }
}

package com.vucelich.villagerworkers.neoforge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common config. Every gameplay switch lives here so a pack can turn it off. */
public final class WorkerSettings {
    public static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue HOLD_WHILE_MENU_OPEN;
    static final ModConfigSpec.IntValue BUILD_PLACEMENTS_PER_TICK;
    static final ModConfigSpec.IntValue BUILD_SCAN_BUDGET;
    static final ModConfigSpec.IntValue BUILD_INTERVAL_TICKS;
    static final ModConfigSpec.IntValue CREATE_SCHEMATIC_UPLOAD_KB;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        HOLD_WHILE_MENU_OPEN = builder
                .comment("While a player has this worker's menu open, the worker stands still and its job waits.")
                .define("holdWhileMenuOpen", true);
        builder.comment("How much structure building a single worker may do. Defaults keep a build to one block every few ticks.");
        BUILD_PLACEMENTS_PER_TICK = builder
                .comment("Blocks a builder may place in one tick. 1 is the default.")
                .defineInRange("buildPlacementsPerTick", 1, 1, 16);
        BUILD_SCAN_BUDGET = builder
                .comment("Schematic cells a builder may inspect in one tick while looking for the next block to place.")
                .defineInRange("buildScanBudget", 64, 8, 512);
        BUILD_INTERVAL_TICKS = builder
                .comment("Ticks to wait after a structure placement. -1 uses the rested or unrested work pace.")
                .defineInRange("buildIntervalTicks", -1, -1, 200);
        CREATE_SCHEMATIC_UPLOAD_KB = builder
                .comment("Create schematic upload limit in KB. 256 leaves Create's maxTotalSchematicSize alone. Higher raises it, and the packet cap up to 32767 bytes, when Create is installed.")
                .defineInRange("createSchematicUploadKb", 256, 256, 8192);
        SPEC = builder.build();
    }

    private WorkerSettings() {}

    public static boolean holdWhileMenuOpen() {
        return HOLD_WHILE_MENU_OPEN.get();
    }

    public static int buildPlacementsPerTick() {
        return BUILD_PLACEMENTS_PER_TICK.get();
    }

    public static int buildScanBudget() {
        return BUILD_SCAN_BUDGET.get();
    }

    public static int buildIntervalTicks() {
        return BUILD_INTERVAL_TICKS.get();
    }

    public static int createSchematicUploadKb() {
        return CREATE_SCHEMATIC_UPLOAD_KB.get();
    }
}

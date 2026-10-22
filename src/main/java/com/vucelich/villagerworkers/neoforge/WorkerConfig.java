package com.vucelich.villagerworkers.neoforge;

import com.vucelich.villagerworkers.common.ForemanLabel;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client switches for drawing workers. These do not run during ship physics.
 */
public final class WorkerConfig {
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue SHOW_FOREMAN_LABELS;
    public static final ModConfigSpec.BooleanValue RENDER_WORKER_GEAR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Drawing workers. Turning these off skips extra client rendering. It does not change ship tick time.");
        builder.push("render");
        SHOW_FOREMAN_LABELS = builder
                .comment("Draw 'Foreman <name> (N crew)' above a foreman.")
                .define("showForemanLabels", ForemanLabel.DEFAULT_ENABLED);
        RENDER_WORKER_GEAR = builder
                .comment("Draw the hard hat, and the foreman's clipboard and blueprint.")
                .define("renderWorkerGear", true);
        builder.pop();
        CLIENT_SPEC = builder.build();
    }

    private WorkerConfig() {}

    public static boolean showForemanLabels() {
        return SHOW_FOREMAN_LABELS.get();
    }

    public static boolean renderWorkerGear() {
        return RENDER_WORKER_GEAR.get();
    }
}
